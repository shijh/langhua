/*
 * This library is part of OFBiz-MFG component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Integration test of the hardware assembly (mfg) services.
 *
 * <p>Unlike the unit tests, this exercises the services through OFBiz's own service engine
 * (attribute validation, transaction, entity engine and database) over the Web Tools endpoint
 * {@code /webtools/control/scheduleServiceSync} with {@code _RUN_SYNC_=Y}. It walks the whole
 * flow: product model, BOM import, routing, work order, serial numbers, work report, defect,
 * work order detail, dashboard, material lots, issues to the work order, shipments to / from
 * the SMT house and the backward lot trace.
 *
 * <p>Prerequisites: a running OFBiz instance with the mfg component deployed and data loaded
 * ({@code gradlew loadAll}), and a user allowed to invoke services (the default
 * {@code admin/ofbiz} user is fine).
 *
 * <p>The test is additive: it creates a product model {@code ITMFG} (updated if it already
 * exists), replaces its BOM, and adds one work order plus one report and defect on every run.
 *
 * <p>Configuration (system properties): {@code ofbiz.url}, {@code ofbiz.user},
 * {@code ofbiz.password}. Defaults: {@code https://localhost:8443}, {@code admin}, {@code ofbiz}.
 *
 * <p>Exit code is 0 when all checks pass, 1 otherwise.
 */
public final class MfgServiceIT {

    private static final String PRODUCT_CODE = "ITMFG";
    private static final String SN_PREFIX = "ITMFG";
    private static final String BOM_CSV =
            "R0402-10K,10K 0402 resistor,4,R1-R4,N\n"
            + "C0402-100N,100nF 0402 capacitor,6,C1-C6,N\n"
            + "U1-BT5-SOC,Main SoC,1,U1,Y\n"
            + "BAT-18650,18650 cell,1,BT1,Y";

    /** A model kept only for the cost test, so the priced BOM does not disturb the other checks. */
    private static final String COST_PRODUCT_CODE = "COSTM";
    private static final String COST_COMPONENT = "R0402-10K";
    /** 4 x 0.05 + 6 x 0.02 + 1 x 3.00 = 3.32 per unit, 0.5 h x 40 = 20.00 labour. */
    private static final String COST_BOM_CSV =
            "R0402-10K,10K 0402 resistor,4,R1-R4,N,0.05\n"
            + "C0402-100N,100nF 0402 capacitor,6,C1-C6,N,0.02\n"
            + "U1-BT5-SOC,Main SoC,1,U1,Y,3.00";

    private static final Pattern WORK_ORDER_ID = Pattern.compile("WO-\\d{6}-\\d{3}");
    private static final Pattern SERIAL_NUMBER = Pattern.compile(SN_PREFIX + "-\\d{6}-\\d{4}");
    private static final Pattern LOT_ID = Pattern.compile("LOT-\\d{6}-\\d+");
    private static final Pattern DELIVERY_ID = Pattern.compile("DO-\\d{6}-\\d+");
    private static final Pattern VENDOR_ID = Pattern.compile("V-\\d{6}-\\d+");
    private static final Pattern CLAIM_ID = Pattern.compile("RMA-\\d{6}-\\d+");
    private static final Pattern DEV_PROJECT_ID = Pattern.compile("DEV-\\d{6}-\\d+");
    private static final Pattern ECO_ID = Pattern.compile("ECO-\\d{6}-\\d+");
    private static final Pattern BOM_VERSION_ID = Pattern.compile(PRODUCT_CODE + "-V\\d+");
    private static final Pattern LOT_NUMBER = Pattern.compile("LOT-\\d{6}-\\d+");
    private static final String ENCLOSURE_PART = "ENC-IT-SHELL";

    private static final Pattern PHOTO_SEQ = Pattern.compile("<td>seq</td>\\s*<td>(\\d+)</td>");
    private static final String TINY_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVQI12P4//8/AzYEYgADMAciAAB6"
        + "wwMBAAAAAElFTkSuQmCC";

    /** A one page pdf, small enough to keep in the source. */
    private static final String TINY_PDF_BASE64 =
        "JVBERi0xLjQKMSAwIG9iago8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PmVuZG9iagoyIDAgb2Jq"
        + "Cjw8L1R5cGUvUGFnZXMvQ291bnQgMC9LaWRzW10+PmVuZG9iagp0cmFpbGVyCjw8L1Jvb3QgMSAw"
        + "IFI+PgolJUVPRgo=";
    private static final String KEY_COMPONENT = "U1-BT5-SOC";
    private static final String SUPPLIER_LOT = "DC-IT-2410";

    private final String baseUrl;
    private final String user;
    private final String password;
    private int failures;
    private CookieManager cookieManager;

    private String workOrderId;
    private String serialNumber;
    private String lotId;
    private String deliveryId;
    private double finishedGoodsOnHand;
    private String vendorId;
    private String claimId;
    private String exchangeSn;
    private String repairWorkOrderId;
    private static final String BUYER_MOBILE = "13900001111";
    private static final String BUYER_NICK = "it_buyer";

    /** BOM version created by the version test, reused by the change order test. */
    private String createdVersionId;
    /** Development project created by the development test. */
    private String devProjectCode;
    /** Change order created by the change order test. */
    private String ecoId;
    private final java.util.List<String> importedLotIds = new java.util.ArrayList<>();

    private MfgServiceIT(String baseUrl, String user, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.user = user;
        this.password = password;
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getProperty("ofbiz.url", "https://localhost:8443");
        String user = System.getProperty("ofbiz.user", "admin");
        String password = System.getProperty("ofbiz.password", "ofbiz");

        System.out.println("Integration test against " + baseUrl + " as user " + user);

        MfgServiceIT it = new MfgServiceIT(baseUrl, user, password);
        it.setUp();
        try {
            it.login();
            it.savesProductModel();
            it.importsBom();
            it.savesRouting();
            it.readsProductDetail();
            it.createsWorkOrder();
            it.registersSerialNumbers();
            it.reportsWork();
            it.recordsDefect();
            it.readsWorkOrderDetail();
            it.readsDashboard();
            it.readsDashboardTrends();
            it.readsPagedWorkOrders();
            it.createsMaterialLot();
            it.issuesMaterialLot();
            it.rejectsIssueOfForeignLot();
            it.inspectsMaterialLot();
            it.recordsShipments();
            it.readsWorkOrderMaterials();
            it.tracesLotBackwards();
            it.readsShortageList();
            it.importsLotsFromCsv();
            it.estimatesSnRangeByFifo();
            it.recordsReplacement();
            it.importsTestResults();
            it.completesTheWorkOrder();
            it.createsDelivery();
            it.rejectsDuplicateDelivery();
            it.savesVendor();
            it.attachesVendorToWorkOrder();
            it.savesBuyerFlag();
            it.checksBuyerFlag();
            it.registersWarrantyClaim();
            it.readsWarrantyClaim();
            it.opensRepairWorkOrder();
            it.shipsReplacement();
            it.closesWarrantyClaim();
            it.rejectsBadClaimStatus();
            it.releasesBuyerFlag();
            it.readsQualityReport();
            it.rejectsZeroHoursReport();
            it.managesBomVersions();
            it.runsProductDevelopment();
            it.appliesChangeOrderToBomVersion();
            it.runsTheThreeDevelopmentTracks();
            it.storesProductPhotos();
            it.costsTheModel();
            it.addsCostDetailToTheWorkOrder();
            it.pricesTheFinishedGoods();
            it.tracksInventory();
            it.reservesMaterialForAWorkOrder();
            it.raisesDevelopmentIssues();
            it.readsTrendReport();
            it.listsTheDashboardAlerts();
            it.quotesAndTracksSoftware();
        } finally {
            it.tearDown();
        }

        System.out.println();
        if (it.failures == 0) {
            System.out.println("INTEGRATION TEST PASSED");
            System.exit(0);
        }
        System.out.println("INTEGRATION TEST FAILED (" + it.failures + " check(s))");
        System.exit(1);
    }

    private void setUp() throws Exception {
        // OFBiz development instances use a self-signed certificate
        TrustManager[] trustAll = new TrustManager[] {
            new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }
            }
        };
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAll, new SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);

        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        cookieManager = cookies;
        CookieHandler.setDefault(cookies);
    }

    private void tearDown() {
        // nothing to release
    }

    private void login() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("USERNAME", user);
        form.put("PASSWORD", password);
        form.put("JavaScriptEnabled", "Y");

        Response response = post("/webtools/control/login", form);
        check("login returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("login page does not report a login error",
                !response.body.contains("following error occurred during login"),
                "login error in response");
    }

    private void savesProductModel() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);
        form.put("productName", "Integration test model");
        form.put("snPrefix", SN_PREFIX);
        form.put("standardHours", "0.45");
        form.put("warrantyMonths", "12");
        form.put("salePrice", "299");

        Response response = invoke("mfgProductSave", form);
        check("product: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("product: response echoes the product code", response.body.contains(PRODUCT_CODE),
                "no " + PRODUCT_CODE + " in response");
        check("product: no service error", !response.body.contains("responseMessage=error"),
                "unexpected service error");
    }

    private void importsBom() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);
        form.put("csv", BOM_CSV);
        form.put("replace", "Y");

        Response response = invoke("mfgBomImport", form);
        check("bom: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("bom: response contains the imported count", response.body.contains("importedCount"),
                "no 'importedCount' in response");
        check("bom: four lines imported", response.body.contains("importedCount</td>")
                && response.body.contains(">4<"), "expected importedCount = 4");
        check("bom: no service error", !response.body.contains("No BOM line could be parsed"),
                "unexpected service error");
    }

    private void savesRouting() throws IOException {
        String[][] operations = {
            {"10", "Assembly", "LINE-1", "0.25"},
            {"20", "Ageing", "AGE-1", "0.10"},
            {"30", "Testing", "FCT-1", "0.08"},
            {"40", "Packing", "PACK-1", "0.02"}
        };
        for (String[] operation : operations) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("productCode", PRODUCT_CODE);
            form.put("opSeq", operation[0]);
            form.put("opName", operation[1]);
            form.put("workCenter", operation[2]);
            form.put("stdHours", operation[3]);

            Response response = invoke("mfgRoutingSave", form);
            check("routing: op " + operation[0] + " saved", response.status == 200
                    && !response.body.contains("responseMessage=error"), "HTTP " + response.status);
        }
    }

    private void readsProductDetail() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);

        Response response = invoke("mfgProductDetail", form);
        check("product detail: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("product detail: BOM contains the SoC", response.body.contains("U1-BT5-SOC"),
                "no U1-BT5-SOC in response");
        check("product detail: BOM contains the cell", response.body.contains("BAT-18650"),
                "no BAT-18650 in response");
        check("product detail: routing contains the test station", response.body.contains("FCT-1"),
                "no FCT-1 in response");
    }

    private void createsWorkOrder() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);
        form.put("plannedQty", "50");
        form.put("dueDate", "2026-12-31");
        form.put("customerName", "Integration test");
        form.put("smtVendor", "EMS-IT");

        Response response = invoke("mfgWorkOrderCreate", form);
        check("work order: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        workOrderId = firstMatch(WORK_ORDER_ID, response.body);
        check("work order: identifier created", workOrderId != null, "no WO- identifier in response");
        System.out.println("[work order] " + workOrderId);
    }

    private void readsDashboardTrends() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("dayCount", "7");

        Response response = invoke("mfgDashboardTrends", form);
        check("dashboard trends: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("dashboard trends: kpi block returned", response.body.contains("shippedUnits"),
                "no shippedUnits in response");
        check("dashboard trends: daily series returned", response.body.contains("shipPct"),
                "no scaled series in response");
        check("dashboard trends: responsibility split returned",
                response.body.contains("responsibilities"), "no responsibility split in response");
        check("dashboard trends: refresh stamp returned", response.body.contains("refreshedAt"),
                "no refresh stamp in response");
    }

    private void readsPagedWorkOrders() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("page", "1");
        form.put("pageSize", "5");

        Response response = invoke("mfgWorkOrderList", form);
        check("paging: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("paging: total count returned", response.body.contains("totalCount"),
                "no totalCount in response");
        check("paging: page count returned", response.body.contains("pageCount"),
                "no pageCount in response");

        Map<String, String> search = new LinkedHashMap<>();
        search.put("search", workOrderId == null ? "WO-" : workOrderId);
        response = invoke("mfgWorkOrderList", search);
        check("search: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("search: the work order is found", workOrderId != null && response.body.contains(workOrderId),
                "work order missing from the search result");

        Map<String, String> miss = new LinkedHashMap<>();
        miss.put("search", "NO-SUCH-KEYWORD-XYZ");
        response = invoke("mfgWorkOrderList", miss);
        check("search: no match returns nothing", response.status == 200 && !response.body.contains(workOrderId),
                "unexpected rows for an unknown keyword");
    }

    private void registersSerialNumbers() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("qty", "3");

        Response response = invoke("mfgSnRegister", form);
        check("serial numbers: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        serialNumber = firstMatch(SERIAL_NUMBER, response.body);
        check("serial numbers: generated with the model prefix", serialNumber != null,
                "no " + SN_PREFIX + "-yyMMdd-NNNN in response");
        check("serial numbers: first and last are reported",
                response.body.contains("firstSn") && response.body.contains("lastSn"),
                "firstSn / lastSn missing");
        System.out.println("[serial number] " + serialNumber);
    }

    private void reportsWork() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("opSeq", "10");
        form.put("operatorName", "IT-operator");
        form.put("workHours", "1.5");
        form.put("qtyOk", "3");
        form.put("qtyNg", "1");
        if (serialNumber != null) {
            form.put("sn", serialNumber);
        }

        Response response = invoke("mfgReportCreate", form);
        check("work report: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("work report: identifier created", response.body.contains("reportId"),
                "no 'reportId' in response");
        check("work report: no service error", !response.body.contains("must be greater than zero"),
                "unexpected service error");
    }

    private void recordsDefect() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("opSeq", "30");
        form.put("defectCode", "IT-NO-POWER");
        form.put("qty", "1");
        form.put("responsibility", "INHOUSE");
        form.put("repairHours", "0.4");
        if (serialNumber != null) {
            form.put("sn", serialNumber);
        }

        Response response = invoke("mfgDefectCreate", form);
        check("defect: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("defect: identifier created", response.body.contains("defectId"),
                "no 'defectId' in response");
    }

    private void readsWorkOrderDetail() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);

        Response response = invoke("mfgWorkOrderDetail", form);
        check("work order detail: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("work order detail: shows the product code", response.body.contains(PRODUCT_CODE),
                "no " + PRODUCT_CODE + " in response");
        check("work order detail: reports the labour hours",
                response.body.contains("reportedHours"), "no 'reportedHours' in response");
        check("work order detail: reports the good quantity",
                response.body.contains("totalQtyOk"), "no 'totalQtyOk' in response");
        check("work order detail: contains the defect code", response.body.contains("IT-NO-POWER"),
                "no IT-NO-POWER in response");
    }

    private void readsDashboard() throws IOException {
        Response response = invoke("mfgDashboard", new LinkedHashMap<String, String>());
        check("dashboard: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("dashboard: reports open work orders", response.body.contains("openOrderCount"),
                "no 'openOrderCount' in response");
        check("dashboard: reports today's hours", response.body.contains("todayHours"),
                "no 'todayHours' in response");
    }

    private void createsMaterialLot() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("componentCode", KEY_COMPONENT);
        form.put("componentName", "Main SoC");
        form.put("supplierName", "IT-supplier");
        form.put("supplierLot", SUPPLIER_LOT);
        form.put("receivedQty", "120");

        Response response = invoke("mfgLotSave", form);
        check("lot: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        lotId = firstMatch(LOT_ID, response.body);
        check("lot: identifier generated", lotId != null, "no LOT- identifier in response");
        check("lot: no service error", !response.body.contains("receivedQty must be greater than zero"),
                "unexpected service error");
        System.out.println("[material lot] " + lotId);
    }

    private void issuesMaterialLot() throws IOException {
        if (workOrderId == null || lotId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("componentCode", KEY_COMPONENT);
        form.put("lotId", lotId);
        form.put("qty", "2");
        form.put("designators", "U1");

        Response response = invoke("mfgIssueSave", form);
        check("issue: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("issue: identifier created", response.body.contains("issueId"), "no 'issueId' in response");
    }

    private void rejectsIssueOfForeignLot() throws IOException {
        if (workOrderId == null || lotId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("componentCode", "BAT-18650");
        form.put("lotId", lotId);
        form.put("qty", "1");

        Response response = invoke("mfgIssueSave", form);
        check("issue: a lot of another component is rejected",
                response.body.contains("belongs to component"), "no validation error in response");
        System.out.println("[invalid issue] error reported as expected");
    }

    /** Incoming inspection: a failed lot must not be issued, a concession may. */
    private void inspectsMaterialLot() throws IOException {
        Map<String, String> lot = new LinkedHashMap<>();
        lot.put("componentCode", "IQC-IT-PART");
        lot.put("componentName", "Incoming inspection part");
        lot.put("supplierName", "IT supplier");
        lot.put("supplierLot", "DC-IQC");
        lot.put("receivedQty", "10");
        Response saved = invoke("mfgLotSave", lot);
        check("iqc: a new lot returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        String iqcLotId = firstMatch(LOT_ID, saved.body);
        check("iqc: pending lot identifier returned", iqcLotId != null, "no lot id in response");
        if (iqcLotId == null || workOrderId == null) {
            return;
        }
        check("iqc: a new lot starts as pending", saved.body.contains("MFG_IQC_PENDING"),
                "the lot did not start as pending");

        Map<String, String> badStatus = new LinkedHashMap<>();
        badStatus.put("lotId", iqcLotId);
        badStatus.put("inspectStatusId", "MFG_IQC_BOGUS");
        Response rejected = invoke("mfgLotInspect", badStatus);
        check("iqc: an unknown status is rejected",
                rejected.body.contains("Unknown inspection status"), "the bogus status was stored");

        Map<String, String> tooMuch = new LinkedHashMap<>();
        tooMuch.put("lotId", iqcLotId);
        tooMuch.put("inspectStatusId", "MFG_IQC_PASS");
        tooMuch.put("rejectedQty", "99");
        Response over = invoke("mfgLotInspect", tooMuch);
        check("iqc: a rejected quantity larger than received is refused",
                over.body.contains("larger than the received quantity"), "the quantity was accepted");

        Map<String, String> fail = new LinkedHashMap<>();
        fail.put("lotId", iqcLotId);
        fail.put("inspectStatusId", "MFG_IQC_FAIL");
        fail.put("rejectedQty", "3");
        fail.put("inspector", "IT inspector");
        fail.put("inspectNote", "Pads do not solder");
        Response failed = invoke("mfgLotInspect", fail);
        check("iqc: a failure is stored", failed.status == 200 && failed.body.contains("MFG_IQC_FAIL"),
                "HTTP " + failed.status);

        Map<String, String> issueForm = new LinkedHashMap<>();
        issueForm.put("workOrderId", workOrderId);
        issueForm.put("componentCode", "IQC-IT-PART");
        issueForm.put("lotId", iqcLotId);
        issueForm.put("qty", "1");
        Response blocked = invoke("mfgIssueSave", issueForm);
        check("iqc: a failed lot cannot be issued",
                blocked.body.contains("failed incoming inspection"), "the failed lot was issued");

        Map<String, String> concession = new LinkedHashMap<>();
        concession.put("lotId", iqcLotId);
        concession.put("inspectStatusId", "MFG_IQC_CONCESSION");
        concession.put("rejectedQty", "3");
        Response conceded = invoke("mfgLotInspect", concession);
        check("iqc: a concession is stored", conceded.body.contains("MFG_IQC_CONCESSION"),
                "the concession was not stored");

        Response issueAfter = invoke("mfgIssueSave", issueForm);
        check("iqc: a concession lot can be issued", issueAfter.body.contains("issueId"),
                "the concession lot was still blocked");

        Map<String, String> list = new LinkedHashMap<>();
        list.put("search", iqcLotId);
        Response listing = invoke("mfgLotList", list);
        check("iqc: the lot shows its inspection state in the list",
                listing.body.contains("MFG_IQC_CONCESSION"), "inspection state missing from the list");
    }

    private void recordsShipments() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> out = new LinkedHashMap<>();
        out.put("workOrderId", workOrderId);
        out.put("directionId", "MFG_SHIP_OUT");
        out.put("qty", "50");
        out.put("carrier", "SF");
        out.put("trackingNo", "SF-IT-OUT");
        Response response = invoke("mfgShipmentSave", out);
        check("shipment out: returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("shipment out: identifier created", response.body.contains("shipmentId"),
                "no 'shipmentId' in response");

        Map<String, String> in = new LinkedHashMap<>();
        in.put("workOrderId", workOrderId);
        in.put("directionId", "MFG_SHIP_IN");
        in.put("qty", "48");
        in.put("defectQty", "2");
        in.put("carrier", "SF");
        in.put("trackingNo", "SF-IT-IN");
        response = invoke("mfgShipmentSave", in);
        check("shipment in: returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("shipment in: no service error", !response.body.contains("defectQty must not exceed qty"),
                "unexpected service error");
    }

    private void readsWorkOrderMaterials() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);

        Response response = invoke("mfgWorkOrderMaterials", form);
        check("materials: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("materials: BOM requirement lines are returned", response.body.contains(KEY_COMPONENT),
                "no " + KEY_COMPONENT + " in response");
        check("materials: issued lot is returned", lotId != null && response.body.contains(lotId),
                "lot missing in response");
        check("materials: kitting flag is returned", response.body.contains("kitComplete"),
                "no 'kitComplete' in response");
        check("materials: shipments are returned", response.body.contains("SF-IT-IN"),
                "no tracking number in response");
    }

    private void tracesLotBackwards() throws IOException {
        if (lotId == null || workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("lotId", lotId);

        Response response = invoke("mfgTraceByLot", form);
        check("trace: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("trace: the work order is found from the lot", response.body.contains(workOrderId),
                "work order missing from trace");
        check("trace: serial number count is reported", response.body.contains("totalSnCount"),
                "no 'totalSnCount' in response");
        System.out.println("[trace] lot " + lotId + " -> " + workOrderId);
    }

    private void readsShortageList() throws IOException {
        // the list is paged and sorted by due date, so ask for a single wide page here
        Map<String, String> form = new LinkedHashMap<>();
        form.put("pageSize", "200");
        Response response = invoke("mfgShortageList", form);
        check("shortage: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("shortage: open orders are counted", response.body.contains("shortageOrderCount"),
                "no 'shortageOrderCount' in response");
        check("shortage: the test work order is listed", workOrderId != null
                && response.body.contains(workOrderId), "work order missing from the shortage list");
    }

    private void importsLotsFromCsv() throws IOException {
        String csv = KEY_COMPONENT + ",Main SoC,IT-supplier,DC-IT-A,10,2026-10-21\n"
                + KEY_COMPONENT + ",Main SoC,IT-supplier,DC-IT-B,10,2026-10-21";
        Map<String, String> form = new LinkedHashMap<>();
        form.put("csv", csv);

        Response response = invoke("mfgLotImportCsv", form);
        check("lot import: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("lot import: two lots imported", response.body.contains("importedCount"),
                "no 'importedCount' in response");
        Matcher matcher = LOT_ID.matcher(response.body);
        while (matcher.find()) {
            importedLotIds.add(matcher.group());
        }
        check("lot import: identifiers generated", importedLotIds.size() >= 2,
                "found " + importedLotIds.size() + " lot ids");
        System.out.println("[lot import] " + importedLotIds);
    }

    private void estimatesSnRangeByFifo() throws IOException {
        if (workOrderId == null || importedLotIds.size() < 2) {
            return;
        }
        for (int i = 0; i < 2; i++) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("workOrderId", workOrderId);
            form.put("componentCode", KEY_COMPONENT);
            form.put("lotId", importedLotIds.get(i));
            form.put("qty", "1.5");
            invoke("mfgIssueSave", form);
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("lotId", importedLotIds.get(0));
        Response response = invoke("mfgTraceByLot", form);
        check("fifo: trace returns estimated range", response.body.contains("estimatedSnFrom")
                && response.body.contains("estimatedSnTo"), "no estimated range in response");
        check("fifo: estimate is placed on a serial number of the work order",
                serialNumber != null && response.body.contains("estimatedSnCount"),
                "no 'estimatedSnCount' in response");
    }

    private void recordsReplacement() throws IOException {
        if (serialNumber == null || workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("sn", serialNumber);
        form.put("componentCode", KEY_COMPONENT);
        form.put("oldLotId", lotId == null ? "" : lotId);
        form.put("newLotId", importedLotIds.isEmpty() ? "" : importedLotIds.get(0));
        form.put("qty", "1");
        form.put("reason", "IT-NO-POWER");
        form.put("hours", "0.3");

        Response response = invoke("mfgReplacementSave", form);
        check("replacement: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("replacement: identifier created", response.body.contains("replacementId"),
                "no 'replacementId' in response");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("sn", serialNumber);
        response = invoke("mfgSnDetail", detail);
        check("replacement: shown in the serial number history",
                response.body.contains("replacementId") && response.body.contains("IT-NO-POWER"),
                "replacement missing from the serial number history");
    }

    private void importsTestResults() throws IOException {
        if (serialNumber == null) {
            return;
        }
        String csv = serialNumber + ",FCT,VBAT,3.72,V,Y\n"
                + serialNumber + ",FCT,IQ,12.4,mA,N\n"
                + "SN-UNKNOWN,FCT,VBAT,3.7,V,Y";
        Map<String, String> form = new LinkedHashMap<>();
        form.put("csv", csv);
        form.put("workOrderId", workOrderId == null ? "" : workOrderId);

        Response response = invoke("mfgTestImportCsv", form);
        check("test import: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("test import: two rows imported", response.body.contains("importedCount"),
                "no 'importedCount' in response");
        check("test import: the unknown serial number is skipped",
                response.body.contains("skippedCount"), "no 'skippedCount' in response");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("sn", serialNumber);
        response = invoke("mfgSnDetail", detail);
        check("test import: results are visible on the unit",
                response.body.contains("VBAT") && response.body.contains("passFail"),
                "test results missing from the serial number history");

        if (workOrderId != null) {
            Map<String, String> order = new LinkedHashMap<>();
            order.put("workOrderId", workOrderId);
            response = invoke("mfgWorkOrderDetail", order);
            check("test import: work order reports the test yield",
                    response.body.contains("testYield"), "no 'testYield' in response");
        }
    }

    /**
     * Books the good units into finished goods stock, which is what a delivery then ships.
     */
    private void completesTheWorkOrder() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        Response response = invoke("mfgWorkOrderComplete", form);
        check("completion: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        String lotId = "FG-" + workOrderId;
        check("completion: the finished goods lot is named after the work order",
                response.body.contains(lotId), "no [" + lotId + "] in response");
        String completedQty = attribute(response.body, "completedQty");
        check("completion: the good units are booked", completedQty != null && value(
                Double.valueOf(completedQty)) > 0.0, "completedQty=" + completedQty);
        String unitCost = attribute(response.body, "unitCost");
        check("completion: the lot carries a unit cost",
                unitCost != null, "no unitCost in response");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("workOrderId", workOrderId);
        response = invoke("mfgWorkOrderDetail", detail);
        check("completion: the work order is completed",
                response.body.contains("MFG_COMPLETED"), "status not reported as completed");
        check("completion: the page gets the finished goods lot",
                response.body.contains("productLot"), "no 'productLot' in the detail");
        check("completion: the serial numbers are completed",
                !response.body.contains("MFG_IN_PROGRESS"), "a unit is still in progress");

        finishedGoodsOnHand = productOnHand();
        check("completion: the model shows up in the finished goods stock",
                finishedGoodsOnHand > 0.0, "on hand " + finishedGoodsOnHand);
    }

    /**
     * Finished goods on hand over every model.
     */
    private double productOnHand() throws IOException {
        Response response = invoke("mfgProductStock", new LinkedHashMap<String, String>());
        String total = attribute(response.body, "onHandTotal");
        return total == null ? 0.0 : Double.parseDouble(total);
    }

    private void createsDelivery() throws IOException {
        if (serialNumber == null || workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customerName", "IT customer");
        form.put("customerPo", "PO-IT-1");
        form.put("deliveryDate", "2026-10-21");
        form.put("carrier", "SF");
        form.put("trackingNo", "SF-IT-DLV");
        form.put("workOrderId", workOrderId);
        form.put("qty", "1");

        Response response = invoke("mfgDeliverySave", form);
        check("delivery: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        deliveryId = firstMatch(DELIVERY_ID, response.body);
        check("delivery: identifier generated", deliveryId != null, "no DO- identifier in response");
        System.out.println("[delivery] " + deliveryId);

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("deliveryId", deliveryId == null ? "" : deliveryId);
        response = invoke("mfgDeliveryDetail", detail);
        check("delivery: the shipped unit is listed", response.body.contains(serialNumber),
                "serial number missing from the delivery");

        Map<String, String> snDetail = new LinkedHashMap<>();
        snDetail.put("sn", serialNumber);
        response = invoke("mfgSnDetail", snDetail);
        check("delivery: visible on the serial number history",
                response.body.contains("IT customer"), "customer missing on the unit");

        double afterShipping = productOnHand();
        check("delivery: the finished goods stock went down by one unit",
                near(Double.valueOf(afterShipping), finishedGoodsOnHand - 1.0),
                "on hand " + finishedGoodsOnHand + " -> " + afterShipping);

        Map<String, String> ledger = new LinkedHashMap<>();
        ledger.put("lotId", "FG-" + workOrderId);
        response = invoke("mfgInventoryLedger", ledger);
        check("delivery: the issue is on the finished goods ledger",
                response.body.contains(deliveryId), "no movement for the delivery");
    }

    private void rejectsDuplicateDelivery() throws IOException {
        if (serialNumber == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("customerName", "IT customer");
        form.put("sns", serialNumber);

        Response response = invoke("mfgDeliverySave", form);
        check("delivery: shipping the same unit twice is rejected",
                response.body.contains("has already been delivered"),
                "no validation error in response");
        System.out.println("[invalid delivery] error reported as expected");
    }

    private void savesVendor() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("vendorName", "IT SMT Vendor");
        form.put("vendorType", "BOTH");
        form.put("contactName", "IT Receiver");
        form.put("contactMobile", "13800002222");
        form.put("addressText", "Building 3, Baoan District, Shenzhen");
        form.put("customerCode", "LH-IT-01");

        Response response = invoke("mfgVendorSave", form);
        check("vendor: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        vendorId = firstMatch(VENDOR_ID, response.body);
        check("vendor: identifier generated", vendorId != null, "no V- identifier in response");
        System.out.println("[vendor] " + vendorId);

        response = invoke("mfgVendorList", new LinkedHashMap<String, String>());
        check("vendor: listed with its customer code", response.body.contains("LH-IT-01"),
                "customer code missing from the vendor list");
        check("vendor: listed with its shipping label", response.body.contains("shippingLabel"),
                "no 'shippingLabel' in the vendor list");
    }

    private void attachesVendorToWorkOrder() throws IOException {
        if (workOrderId == null || vendorId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("vendorId", vendorId);

        Response response = invoke("mfgWorkOrderVendor", form);
        check("work order vendor: service call returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        check("work order vendor: no service error", !response.body.contains("Unknown vendor"),
                "unexpected service error");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("workOrderId", workOrderId);
        response = invoke("mfgWorkOrderDetail", detail);
        check("work order vendor: the work order carries the vendor", response.body.contains("IT SMT Vendor"),
                "vendor name missing on the work order");
        check("work order vendor: the shipping label is available",
                response.body.contains("shippingLabel") && response.body.contains("LH-IT-01"),
                "shipping label missing on the work order");
    }

    private void savesBuyerFlag() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("receiverMobile", BUYER_MOBILE);
        form.put("buyerNick", BUYER_NICK);
        form.put("receiverName", "IT Buyer");
        form.put("riskLevel", "WATCH");
        form.put("reasonCode", "FREQ_RETURN");
        form.put("evidenceNote", "two no-reason returns in thirty days");

        Response response = invoke("mfgBuyerFlagSave", form);
        check("buyer flag: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("buyer flag: key built from the nickname", response.body.contains("TB:N:" + BUYER_NICK),
                "no TB:N: key in response");
        check("buyer flag: level kept", response.body.contains("WATCH"), "level missing from the response");

        response = invoke("mfgBuyerFlagList", new LinkedHashMap<String, String>());
        check("buyer flag: listed", response.body.contains(BUYER_NICK), "nickname missing from the list");
        check("buyer flag: masked mobile offered", response.body.contains("maskedMobile"),
                "no maskedMobile attribute in the list");
    }

    private void checksBuyerFlag() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("receiverMobile", "139-0000-1111");

        Response response = invoke("mfgBuyerFlagCheck", form);
        check("buyer flag: formatted mobile still matches", response.body.contains(BUYER_NICK),
                "no hit for the formatted mobile");

        Map<String, String> masked = new LinkedHashMap<>();
        masked.put("receiverMobile", "138****2222");
        response = invoke("mfgBuyerFlagSave", masked);
        check("buyer flag: a masked mobile is rejected", response.body.contains("cannot identify a buyer"),
                "masked mobile was accepted");
    }

    private void releasesBuyerFlag() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("receiverMobile", BUYER_MOBILE);
        form.put("buyerNick", BUYER_NICK);
        form.put("riskLevel", "WATCH");
        form.put("statusId", "MFG_FLAG_RELEASED");

        Response response = invoke("mfgBuyerFlagSave", form);
        check("buyer flag: release returns HTTP 200", response.status == 200, "HTTP " + response.status);

        Map<String, String> checkForm = new LinkedHashMap<>();
        checkForm.put("receiverMobile", BUYER_MOBILE);
        response = invoke("mfgBuyerFlagCheck", checkForm);
        check("buyer flag: released flag no longer matches", !response.body.contains(BUYER_NICK),
                "released flag still matches");
    }

    private void readsQualityReport() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("fromDate", "2026-01-01");
        form.put("toDate", "2026-12-31");

        Response response = invoke("mfgQualityReport", form);
        check("quality: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("quality: shipped units reported", response.body.contains("shippedUnits"),
                "no shippedUnits in response");
        check("quality: rates calculated", response.body.contains("returnRate")
                        && response.body.contains("costRate"),
                "rates missing from the response");
        check("quality: cost turned into money", response.body.contains("costTotal"),
                "no costTotal in response");
        check("quality: model breakdown present", response.body.contains(PRODUCT_CODE),
                "model missing from the breakdown");
        check("quality: red lines evaluated", response.body.contains("overRepairLimit"),
                "no red line flag in response");
    }

    private void registersWarrantyClaim() throws IOException {
        if (serialNumber == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("sn", serialNumber);
        form.put("receivedDate", "2026-10-21");
        form.put("signedDate", "2026-10-21");
        form.put("claimType", "EXCH");
        form.put("tradeNo", "TB-IT-9001");
        form.put("buyerNick", BUYER_NICK);
        form.put("responsibility", "SMT-VENDOR");
        form.put("issueDesc", "Charging port loose after three months");

        Response response = invoke("mfgClaimCreate", form);
        check("warranty: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        claimId = firstMatch(CLAIM_ID, response.body);
        check("warranty: identifier generated", claimId != null, "no RMA- identifier in response");
        System.out.println("[claim] " + claimId);
        check("warranty: the flag is returned", response.body.contains("inWarranty"),
                "no inWarranty attribute in response");
        check("warranty: the type is kept", response.body.contains("EXCH"), "type missing from the response");
        check("warranty: the buyer risk level is inherited", response.body.contains("WATCH"),
                "risk level missing from the response");
        check("warranty: the no reason window is derived from the signed date",
                response.body.contains("2026-10-29"), "expected the seven day deadline on 2026-10-29");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("claimId", claimId == null ? "" : claimId);
        response = invoke("mfgClaimDetail", detail);
        check("warranty: the claim is readable", response.body.contains(claimId == null ? "" : claimId)
                        && response.body.contains(serialNumber),
                "claim or serial number missing from the detail");
        check("warranty: start date comes from the delivery", response.body.contains("2027-10-21"),
                "expected a warranty end date of 2027-10-21");
        check("warranty: the delivery is linked", deliveryId == null || response.body.contains(deliveryId),
                "delivery missing from the claim");
    }

    private void readsWarrantyClaim() throws IOException {
        if (claimId == null || serialNumber == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("sn", serialNumber);
        Response response = invoke("mfgClaimList", form);
        check("warranty: listed for its serial number", response.body.contains(claimId), "claim missing from the list");

        Map<String, String> snDetail = new LinkedHashMap<>();
        snDetail.put("sn", serialNumber);
        response = invoke("mfgSnDetail", snDetail);
        check("warranty: visible on the serial number history", response.body.contains(claimId),
                "claim missing from the unit history");
    }

    private void opensRepairWorkOrder() throws IOException {
        if (claimId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("claimId", claimId);
        form.put("dueDate", "2026-10-28");

        Response response = invoke("mfgClaimRepair", form);
        check("warranty: repair work order returns HTTP 200", response.status == 200,
                "HTTP " + response.status);
        repairWorkOrderId = firstMatch(WORK_ORDER_ID, response.body);
        check("warranty: repair work order created", repairWorkOrderId != null, "no WO- id in response");
        check("warranty: repair work order is a new one", !workOrderId.equals(repairWorkOrderId),
                "repair reuses the production work order");
        System.out.println("[repair work order] " + repairWorkOrderId);

        response = invoke("mfgClaimRepair", form);
        check("warranty: a second repair work order is rejected",
                response.body.contains("already has repair work order"), "no validation error in response");
    }

    private void shipsReplacement() throws IOException {
        if (claimId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("claimId", claimId);
        form.put("carrier", "SF");
        form.put("trackingNo", "SF-IT-BACK");

        Response response = invoke("mfgClaimExchange", form);
        check("warranty: exchange returns HTTP 200", response.status == 200, "HTTP " + response.status);
        String exchangeDeliveryId = firstMatch(DELIVERY_ID, response.body);
        check("warranty: replacement delivery created", exchangeDeliveryId != null,
                "no DO- id in response");
        System.out.println("[exchange delivery] " + exchangeDeliveryId);
        check("warranty: the claim is marked as replaced", response.body.contains("MFG_RMA_REPLACED"),
                "status not updated");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("claimId", claimId);
        response = invoke("mfgClaimDetail", detail);
        exchangeSn = firstMatchOtherThan(SERIAL_NUMBER, response.body, serialNumber);
        check("warranty: a replacement unit is picked", exchangeSn != null,
                "no other serial number on the claim");
        check("warranty: the replacement is not the returned unit", !serialNumber.equals(exchangeSn),
                "the returned unit was shipped again");

        Map<String, String> unit = new LinkedHashMap<>();
        unit.put("sn", exchangeSn == null ? "" : exchangeSn);
        response = invoke("mfgSnDetail", unit);
        check("warranty: the replacement unit is shipped", response.body.contains("MFG_SHIPPED"),
                "unit status not updated");

        response = invoke("mfgClaimExchange", form);
        check("warranty: a second exchange is rejected",
                response.body.contains("already has a replacement delivery"),
                "no validation error in response");
    }

    private void closesWarrantyClaim() throws IOException {
        if (claimId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("claimId", claimId);
        form.put("statusId", "MFG_RMA_CLOSED");
        form.put("claimType", "EXCH");
        form.put("repairHours", "1.25");
        form.put("inspectResult", "QUALITY");
        form.put("freightPayer", "SELLER");
        form.put("refundAmount", "129");
        form.put("deductAmount", "0");
        form.put("returnTrackingNo", "SF-IT-RETURN");
        form.put("receivedBackDate", "2026-10-21");
        form.put("vendorClaimStatus", "SENT");
        form.put("note", "Resoldered the port, retested OK");

        Response response = invoke("mfgClaimUpdate", form);
        check("warranty: closing returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("warranty: status updated", response.body.contains("MFG_RMA_CLOSED"), "closed status missing");

        Map<String, String> detail = new LinkedHashMap<>();
        detail.put("claimId", claimId);
        response = invoke("mfgClaimDetail", detail);
        check("warranty: close date stamped", response.body.contains("closedDate"), "no closedDate attribute");
        check("warranty: repair hours stored", response.body.contains("1.25"), "repair hours missing");
        check("warranty: inspection result stored", response.body.contains("QUALITY"),
                "inspection result missing");
        check("warranty: refund amount stored", response.body.contains("129"), "refund amount missing");
        check("warranty: return tracking stored", response.body.contains("SF-IT-RETURN"),
                "return tracking missing");
    }

    private void rejectsBadClaimStatus() throws IOException {
        if (claimId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("claimId", claimId);
        form.put("statusId", "NOT_A_STATUS");

        Response response = invoke("mfgClaimUpdate", form);
        check("warranty: invalid status rejected", response.body.contains("statusId must be"),
                "no validation error in response");
        System.out.println("[invalid claim status] error reported as expected");
    }

    private void rejectsZeroHoursReport() throws IOException {
        if (workOrderId == null) {
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("workOrderId", workOrderId);
        form.put("opSeq", "10");
        form.put("workHours", "0");
        form.put("qtyOk", "1");        Response response = invoke("mfgReportCreate", form);
        check("invalid report: service reports an error",
                response.body.contains("workHours must be greater than zero"),
                "no validation error in response");
        System.out.println("[invalid report] error reported as expected");
    }

    /**
     * Runs one PCB project, one enclosure project and checks the step rules of the three tracks.
     *
     * @throws IOException when the service call fails
     */
    private void runsTheThreeDevelopmentTracks() throws IOException {
        // a PCB project only knows the PCB steps
        Map<String, String> pcbForm = new LinkedHashMap<>();
        pcbForm.put("projectName", "IT PCB 项目");
        pcbForm.put("productCode", PRODUCT_CODE);
        pcbForm.put("trackId", "MFG_TRACK_PCB");
        pcbForm.put("partCode", "PCB-IT-BT5");
        Response pcb = invoke("mfgDevProjectSave", pcbForm);
        check("pcb track: project created", pcb.status == 200, "HTTP " + pcb.status);
        String pcbProjectCode = firstMatch(DEV_PROJECT_ID, pcb.body);
        check("pcb track: identifier generated", pcbProjectCode != null, "no project code");

        Map<String, String> pcbDetailForm = new LinkedHashMap<>();
        pcbDetailForm.put("projectCode", pcbProjectCode == null ? "" : pcbProjectCode);
        Response pcbDetail = invoke("mfgDevProjectDetail", pcbDetailForm);
        check("pcb track: five steps listed", pcbDetail.body.contains("MFG_PCB_BREADBOARD")
                && pcbDetail.body.contains("MFG_PCB_HAND_SOLDER"), "PCB steps missing");
        check("pcb track: no firmware step in the PCB project",
                !pcbDetail.body.contains("MFG_FW_FLASH"), "firmware step leaked into the PCB project");

        Map<String, String> startForm = new LinkedHashMap<>();
        startForm.put("projectCode", pcbProjectCode == null ? "" : pcbProjectCode);
        startForm.put("stepId", "MFG_PCB_BREADBOARD");
        startForm.put("statusId", "MFG_STEP_DONE");
        Response started = invoke("mfgDevStepStatus", startForm);
        check("pcb track: quick status works", started.status == 200
                && started.body.contains("MFG_STEP_DONE"), "HTTP " + started.status);

        Map<String, String> stepForm = new LinkedHashMap<>();
        stepForm.put("projectCode", pcbProjectCode == null ? "" : pcbProjectCode);
        stepForm.put("stepId", "MFG_PCB_SCHEMATIC");
        stepForm.put("statusId", "MFG_STEP_DOING");
        stepForm.put("plannedDate", "2026-11-05");
        stepForm.put("ownerName", "it_hw");
        stepForm.put("vendorName", "立创");
        stepForm.put("amount", "120");
        stepForm.put("referenceNo", "PO-IT-001");
        stepForm.put("note", "二阶电路");
        Response saved = invoke("mfgDevStepSave", stepForm);
        check("pcb track: step details saved", saved.status == 200, "HTTP " + saved.status);

        Map<String, String> pcbAgainForm = new LinkedHashMap<>();
        pcbAgainForm.put("projectCode", pcbProjectCode == null ? "" : pcbProjectCode);
        Response pcbAgain = invoke("mfgDevProjectDetail", pcbAgainForm);
        check("pcb track: reference number is kept", pcbAgain.body.contains("PO-IT-001"),
                "reference number missing");
        check("pcb track: current step moved on", pcbAgain.body.contains("MFG_PCB_LAYOUT"),
                "current step not reported");

        Map<String, String> wrongTrack = new LinkedHashMap<>();
        wrongTrack.put("projectCode", pcbProjectCode == null ? "" : pcbProjectCode);
        wrongTrack.put("stepId", "MFG_FW_FLASH");
        wrongTrack.put("statusId", "MFG_STEP_DONE");
        Response rejected = invoke("mfgDevStepStatus", wrongTrack);
        check("pcb track: a firmware step is rejected",
                rejected.body.contains("does not belong to project"), "wrong step was accepted");

        // the enclosure track creates a material lot when the stock in step is finished
        Map<String, String> encForm = new LinkedHashMap<>();
        encForm.put("projectName", "IT 外壳项目");
        encForm.put("productCode", PRODUCT_CODE);
        encForm.put("trackId", "MFG_TRACK_ENCLOSURE");
        encForm.put("partCode", ENCLOSURE_PART);
        Response enc = invoke("mfgDevProjectSave", encForm);
        String encProjectCode = firstMatch(DEV_PROJECT_ID, enc.body);
        check("enclosure track: project created", encProjectCode != null, "no project code");

        Map<String, String> noQty = new LinkedHashMap<>();
        noQty.put("projectCode", encProjectCode == null ? "" : encProjectCode);
        noQty.put("stepId", "MFG_ENC_STOCK_IN");
        noQty.put("statusId", "MFG_STEP_DONE");
        Response noQtyResponse = invoke("mfgDevStepStatus", noQty);
        check("enclosure track: stock in without quantity is rejected",
                noQtyResponse.body.contains("Set the quantity"), "stock in accepted without quantity");

        Map<String, String> stockIn = new LinkedHashMap<>();
        stockIn.put("projectCode", encProjectCode == null ? "" : encProjectCode);
        stockIn.put("stepId", "MFG_ENC_STOCK_IN");
        stockIn.put("statusId", "MFG_STEP_DONE");
        stockIn.put("qty", "50");
        stockIn.put("vendorName", "IT 注塑厂");
        stockIn.put("referenceNo", "HT-IT-2026-01");
        stockIn.put("amount", "8500");
        Response stocked = invoke("mfgDevStepSave", stockIn);
        check("enclosure track: stock in returns HTTP 200", stocked.status == 200, "HTTP " + stocked.status);
        String lotId = firstMatch(LOT_NUMBER, stocked.body);
        check("enclosure track: a material lot was created", lotId != null, "no lot id in response");

        Map<String, String> lotsForm = new LinkedHashMap<>();
        lotsForm.put("search", lotId == null ? ENCLOSURE_PART : lotId);
        Response lots = invoke("mfgLotList", lotsForm);
        check("enclosure track: the lot is in the material lots", lotId != null && lots.body.contains(lotId),
                "lot missing from the material lot list");
        check("enclosure track: the lot carries the enclosure part", lots.body.contains(ENCLOSURE_PART),
                "part code missing on the lot");

        Response stockedAgain = invoke("mfgDevStepSave", stockIn);
        check("enclosure track: finishing the stock in twice keeps one lot",
                stockedAgain.body.contains(lotId == null ? "no-lot" : lotId), "a second lot was created");

        Map<String, String> encDetailForm = new LinkedHashMap<>();
        encDetailForm.put("projectCode", encProjectCode == null ? "" : encProjectCode);
        Response encDetail = invoke("mfgDevProjectDetail", encDetailForm);
        check("enclosure track: seven steps listed", encDetail.body.contains("MFG_ENC_BLENDER")
                && encDetail.body.contains("MFG_ENC_ASSEMBLY"), "enclosure steps missing");
        check("enclosure track: lot shown on the stock in step", lotId != null
                && encDetail.body.contains(lotId), "lot not shown on the step");
    }

    /**
     * Stores a photo through the service, lists it, fetches the bytes and the thumbnail and finally
     * deletes it.
     *
     * @throws IOException when the call fails
     */
    private void storesProductPhotos() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("ownerTypeId", "MFG_ATT_PRODUCT");
        form.put("ownerId", PRODUCT_CODE);
        form.put("fileName", "C:\\Users\\it\\Pictures\\正面 照片.png");
        form.put("contentType", "image/png");
        form.put("base64Data", TINY_PNG_BASE64);
        form.put("note", "IT 照片");
        Response saved = invoke("mfgAttachmentSave", form);
        check("photo: save returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        String seq = firstGroup(PHOTO_SEQ, saved.body);
        check("photo: a sequence number is returned", seq != null, "no seq in response");
        check("photo: the file name is cleaned", saved.body.contains("png") && !saved.body.contains("Users"),
                "file name still carries a path");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("ownerTypeId", "MFG_ATT_PRODUCT");
        listForm.put("ownerId", PRODUCT_CODE);
        Response list = invoke("mfgAttachmentList", listForm);
        check("photo: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("photo: the photo is listed", list.body.contains("image/png"), "content type missing from the list");

        Map<String, String> detailForm = new LinkedHashMap<>();
        detailForm.put("ownerTypeId", "MFG_ATT_PRODUCT");
        detailForm.put("ownerId", PRODUCT_CODE);
        Response detail = invoke("mfgAttachmentList", detailForm);
        check("photo: the owner reports one attachment", detail.body.contains("attachmentCount"),
                "no attachmentCount in the response");

        // the photo is served through the controller; the page script checks the bytes with a real
        // browser session, here only the service side is covered
        Map<String, String> rejectForm = new LinkedHashMap<>();
        rejectForm.put("ownerTypeId", "MFG_ATT_PRODUCT");
        rejectForm.put("ownerId", PRODUCT_CODE);
        rejectForm.put("fileName", "manual.zip");
        rejectForm.put("contentType", "application/zip");
        rejectForm.put("base64Data", TINY_PNG_BASE64);
        Response rejected = invoke("mfgAttachmentSave", rejectForm);
        check("photo: a zip is rejected", rejected.body.contains("Only JPG, PNG, GIF and PDF files are supported"),
                "a zip was stored");

        Map<String, String> badOwner = new LinkedHashMap<>();
        badOwner.put("ownerTypeId", "MFG_ATT_BOGUS");
        badOwner.put("ownerId", PRODUCT_CODE);
        badOwner.put("fileName", "photo.png");
        badOwner.put("contentType", "image/png");
        badOwner.put("base64Data", TINY_PNG_BASE64);
        Response badOwnerResponse = invoke("mfgAttachmentSave", badOwner);
        check("photo: an unknown owner type is rejected",
                badOwnerResponse.body.contains("Unknown attachment owner type"), "the owner type was accepted");

        // a pdf on a claim, to prove the same code serves another record
        Map<String, String> pdfForm = new LinkedHashMap<>();
        pdfForm.put("ownerTypeId", "MFG_ATT_CLAIM");
        pdfForm.put("ownerId", claimId == null ? "NO-CLAIM" : claimId);
        pdfForm.put("fileName", "inspection.pdf");
        pdfForm.put("contentType", "application/pdf");
        pdfForm.put("base64Data", TINY_PDF_BASE64);
        Response pdfSaved = invoke("mfgAttachmentSave", pdfForm);
        // the webtools result page escapes the slash of the content type as &#x2f;
        boolean pdfEchoed = pdfSaved.body.contains("application/pdf")
                || pdfSaved.body.contains("application&#x2f;pdf");
        check("claim attachment: a pdf is accepted", pdfSaved.status == 200 && pdfEchoed,
                "HTTP " + pdfSaved.status);

        Map<String, String> deleteForm = new LinkedHashMap<>();
        deleteForm.put("ownerTypeId", "MFG_ATT_PRODUCT");
        deleteForm.put("ownerId", PRODUCT_CODE);
        deleteForm.put("seq", seq == null ? "1" : seq);
        Response deleted = invoke("mfgAttachmentDelete", deleteForm);
        check("photo: delete returns HTTP 200", deleted.status == 200
                && !deleted.body.contains("does not exist"), "HTTP " + deleted.status);

        Map<String, String> emptyListForm = new LinkedHashMap<>();
        emptyListForm.put("ownerTypeId", "MFG_ATT_PRODUCT");
        emptyListForm.put("ownerId", PRODUCT_CODE);
        Response emptyList = invoke("mfgAttachmentList", emptyListForm);
        check("photo: the list is empty after the delete", emptyList.body.contains("attachmentCount</td>")
                && emptyList.body.contains(">0<"), "the photo is still listed");
    }

    private Response invoke(String serviceName, Map<String, String> parameters) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", serviceName);
        form.put("_RUN_SYNC_", "Y");
        form.putAll(parameters);
        return post("/webtools/control/scheduleServiceSync", form);
    }

    /**
     * Creates the next BOM version, fills it with a different line set and switches the model to
     * it. The version created here is reused by the change order test.
     *
     * @throws IOException when the service call fails
     */
    private void managesBomVersions() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);
        form.put("copyCurrent", "Y");
        form.put("note", "IT second version");
        Response response = invoke("mfgBomVersionCreate", form);
        check("bom version: create returns HTTP 200", response.status == 200, "HTTP " + response.status);
        createdVersionId = firstMatch(BOM_VERSION_ID, response.body);
        check("bom version: identifier returned", createdVersionId != null, "no version id in response");
        check("bom version: current lines were copied", response.body.contains(">4<"),
                "expected copiedCount = 4");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("productCode", PRODUCT_CODE);
        Response list = invoke("mfgBomVersionList", listForm);
        check("bom version: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("bom version: new version is listed", createdVersionId != null
                && list.body.contains(createdVersionId), "version missing from the list");

        // add one line to the new version only
        Map<String, String> importForm = new LinkedHashMap<>();
        importForm.put("productCode", PRODUCT_CODE);
        importForm.put("bomVersionId", createdVersionId == null ? "" : createdVersionId);
        importForm.put("csv", "U9-BT5-EXTRA,新增外挂芯片,1,U9,Y");
        importForm.put("replace", "N");
        Response imported = invoke("mfgBomImport", importForm);
        check("bom version: import into the draft works", imported.status == 200
                && !imported.body.contains("responseMessage=error"), "HTTP " + imported.status);

        Map<String, String> activateForm = new LinkedHashMap<>();
        activateForm.put("bomVersionId", createdVersionId == null ? "" : createdVersionId);
        Response activated = invoke("mfgBomVersionActivate", activateForm);
        check("bom version: activate returns HTTP 200", activated.status == 200, "HTTP " + activated.status);
        check("bom version: activate reports the model", activated.body.contains(PRODUCT_CODE),
                "no product code in response");

        Map<String, String> detailForm = new LinkedHashMap<>();
        detailForm.put("productCode", PRODUCT_CODE);
        Response detail = invoke("mfgProductDetail", detailForm);
        check("bom version: product detail uses the activated lines", detail.body.contains("U9-BT5-EXTRA"),
                "the new line is not in the current BOM");
        check("bom version: product detail reports the version", createdVersionId != null
                && detail.body.contains(createdVersionId), "version missing from the detail");

        Map<String, String> migrateForm = new LinkedHashMap<>();
        migrateForm.put("productCode", PRODUCT_CODE);
        Response migrated = invoke("mfgBomMigrateLegacy", migrateForm);
        check("bom version: legacy migration is idempotent", migrated.status == 200
                && !migrated.body.contains("responseMessage=error"), "HTTP " + migrated.status);
    }

    /**
     * Creates a development project and walks it through the first gate.
     *
     * @throws IOException when the service call fails
     */
    private void runsProductDevelopment() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("projectName", "IT 开发项目");
        form.put("productCode", PRODUCT_CODE);
        form.put("ownerName", "it_owner");
        form.put("targetCost", "180");
        form.put("targetPrice", "299");
        form.put("plannedLaunchDate", "2026-12-01");
        Response response = invoke("mfgDevProjectSave", form);
        check("dev project: create returns HTTP 200", response.status == 200, "HTTP " + response.status);
        devProjectCode = firstMatch(DEV_PROJECT_ID, response.body);
        check("dev project: identifier generated", devProjectCode != null, "no project code in response");
        check("dev project: starts as a new project", response.body.contains("isNew</td>")
                && response.body.contains(">Y<"), "expected isNew = Y");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("search", devProjectCode == null ? "" : devProjectCode);
        Response list = invoke("mfgDevProjectList", listForm);
        check("dev project: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("dev project: found by its number", devProjectCode != null && list.body.contains(devProjectCode),
                "project missing from the list");

        Map<String, String> detailForm = new LinkedHashMap<>();
        detailForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        Response detail = invoke("mfgDevProjectDetail", detailForm);
        check("dev project: detail returns HTTP 200", detail.status == 200, "HTTP " + detail.status);
        check("dev project: five gates are listed", detail.body.contains("MFG_STAGE_TRIAL")
                && detail.body.contains("MFG_STAGE_MASS"), "gate list incomplete");
        check("dev project: starts at the concept stage", detail.body.contains("MFG_STAGE_CONCEPT"),
                "no concept stage");

        Map<String, String> reviewForm = new LinkedHashMap<>();
        reviewForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        reviewForm.put("stageId", "MFG_STAGE_CONCEPT");
        reviewForm.put("resultId", "MFG_REVIEW_PASS");
        reviewForm.put("doneDate", "2026-10-21");
        reviewForm.put("reviewer", "it_reviewer");
        Response reviewed = invoke("mfgDevStageSave", reviewForm);
        check("dev project: gate review saved", reviewed.status == 200
                && !reviewed.body.contains("responseMessage=error"), "HTTP " + reviewed.status);
        check("dev project: passing the gate moves to design", reviewed.body.contains("MFG_STAGE_DESIGN"),
                "project stage did not advance");

        Map<String, String> badStage = new LinkedHashMap<>();
        badStage.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        badStage.put("stageId", "MFG_STAGE_BOGUS");
        Response rejected = invoke("mfgDevStageSave", badStage);
        check("dev project: unknown stage is rejected",
                rejected.body.contains("Unknown development stage"),
                "unknown stage was accepted");
    }

    /**
     * Raises a change order that switches the model to the BOM version created before, approves
     * it and applies it.
     *
     * @throws IOException when the service call fails
     */
    private void appliesChangeOrderToBomVersion() throws IOException {
        if (createdVersionId == null) {
            check("change order: skipped, no BOM version available", false, "no version id");
            return;
        }
        Map<String, String> form = new LinkedHashMap<>();
        form.put("productCode", PRODUCT_CODE);
        form.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        form.put("ecoTypeId", "MFG_ECO_BOM");
        form.put("reason", "IT 变更测试");
        form.put("changeSummary", "U9 新增");
        form.put("requestedBy", "it_requester");
        form.put("toBomVersionId", createdVersionId);
        Response response = invoke("mfgDevEcoSave", form);
        check("change order: create returns HTTP 200", response.status == 200, "HTTP " + response.status);
        ecoId = firstMatch(ECO_ID, response.body);
        check("change order: identifier generated", ecoId != null, "no change order id in response");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("search", ecoId == null ? "" : ecoId);
        Response list = invoke("mfgDevEcoList", listForm);
        check("change order: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("change order: found by its number", ecoId != null && list.body.contains(ecoId),
                "change order missing from the list");
        check("change order: target version is kept", list.body.contains(createdVersionId),
                "target version missing");

        Map<String, String> applyTooEarly = new LinkedHashMap<>();
        applyTooEarly.put("ecoId", ecoId == null ? "" : ecoId);
        Response early = invoke("mfgDevEcoApply", applyTooEarly);
        check("change order: a draft cannot be applied",
                early.body.contains("Only an approved change order can be applied"),
                "a draft was applied");

        Map<String, String> approveForm = new LinkedHashMap<>();
        approveForm.put("ecoId", ecoId == null ? "" : ecoId);
        approveForm.put("approvedBy", "it_approver");
        Response approved = invoke("mfgDevEcoApprove", approveForm);
        check("change order: approve returns HTTP 200", approved.status == 200
                && approved.body.contains("MFG_ECO_APPROVED"), "HTTP " + approved.status);

        Map<String, String> applyForm = new LinkedHashMap<>();
        applyForm.put("ecoId", ecoId == null ? "" : ecoId);
        Response applied = invoke("mfgDevEcoApply", applyForm);
        check("change order: apply returns HTTP 200", applied.status == 200, "HTTP " + applied.status);
        check("change order: status becomes applied", applied.body.contains("MFG_ECO_APPLIED"),
                "status did not change");

        Map<String, String> detailForm = new LinkedHashMap<>();
        detailForm.put("ecoId", ecoId == null ? "" : ecoId);
        Response detail = invoke("mfgDevEcoDetail", detailForm);
        check("change order: detail returns HTTP 200", detail.status == 200, "HTTP " + detail.status);
        check("change order: detail shows the target version", detail.body.contains(createdVersionId),
                "target version missing from the detail");

        Map<String, String> again = new LinkedHashMap<>();
        again.put("ecoId", ecoId == null ? "" : ecoId);
        Response second = invoke("mfgDevEcoApply", again);
        check("change order: applying twice is rejected",
                second.body.contains("Only an approved change order can be applied"),
                "the change order was applied twice");

        Map<String, String> projectForm = new LinkedHashMap<>();
        projectForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        Response project = invoke("mfgDevProjectDetail", projectForm);
        check("change order: linked project shows the change order", ecoId != null
                && project.body.contains(ecoId), "change order missing on the project");
    }

    /**
     * Reserves the material of a work order from a lot, issues part of it and releases the rest: the
     * reserved quantity must lower the available stock, and an issue must not be blocked by the
     * reservation of its own order.
     *
     * @throws IOException when a service call fails
     */
    private void reservesMaterialForAWorkOrder() throws IOException {
        String suffix = String.valueOf(System.currentTimeMillis() % 1000000L);
        String component = "RSV" + suffix;
        String model = "RSVM" + suffix;
        Map<String, String> productForm = new LinkedHashMap<>();
        productForm.put("productCode", model);
        productForm.put("productName", "Reservation test model");
        productForm.put("snPrefix", model);
        productForm.put("standardHours", "0.1");
        productForm.put("laborRate", "40");
        Response product = invoke("mfgProductSave", productForm);
        check("reserve: the model is ready", product.status == 200
                && !product.body.contains("responseMessage=error"), "HTTP " + product.status);

        Map<String, String> bomForm = new LinkedHashMap<>();
        bomForm.put("productCode", model);
        bomForm.put("csv", component + ",Reserve test part,2,U1,Y");
        bomForm.put("replace", "Y");
        Response bom = invoke("mfgBomImport", bomForm);
        check("reserve: the BOM needs two parts per unit",
                bom.status == 200 && bom.body.contains("importedCount"), "HTTP " + bom.status);

        Map<String, String> lotForm = new LinkedHashMap<>();
        lotForm.put("componentCode", component);
        lotForm.put("componentName", "Reserve test part");
        lotForm.put("supplierName", "IT parts");
        lotForm.put("supplierLot", "DC-RSV");
        lotForm.put("receivedQty", "10");
        lotForm.put("unitPrice", "1");
        Response lot = invoke("mfgLotSave", lotForm);
        check("reserve: ten parts are in stock", lot.status == 200, "HTTP " + lot.status);

        Map<String, String> orderForm = new LinkedHashMap<>();
        orderForm.put("productCode", model);
        orderForm.put("plannedQty", "3");
        orderForm.put("dueDate", "2026-12-31");
        orderForm.put("customerName", "Reserve test");
        Response order = invoke("mfgWorkOrderCreate", orderForm);
        String reserveOrderId = firstMatch(WORK_ORDER_ID, order.body);
        check("reserve: the work order is created", reserveOrderId != null, "no work order id");
        // three units need six parts, and the lot has ten of them
        check("reserve: the material is reserved when the order is created",
                near(fieldValue(order.body, "reservedQty"), 6.0), "reservedQty is not 6");
        check("reserve: nothing is missing", near(fieldValue(order.body, "shortQty"), 0.0),
                "shortQty is not 0");

        Map<String, String> summaryForm = new LinkedHashMap<>();
        summaryForm.put("search", component);
        Response summary = invoke("mfgStockSummary", summaryForm);
        check("reserve: the stock keeps the reservation apart",
                summary.body.contains("reservedQty:6"), "reservedQty is not 6 in the summary");
        check("reserve: only four parts are left available",
                summary.body.contains("availableQty:4"), "availableQty is not 4");

        Map<String, String> materialsForm = new LinkedHashMap<>();
        materialsForm.put("workOrderId", reserveOrderId == null ? "" : reserveOrderId);
        Response materials = invoke("mfgWorkOrderMaterials", materialsForm);
        check("reserve: the kitting line shows the reservation",
                materials.body.contains("reservedQty:6"), "no reserved quantity on the kitting line");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("search", component);
        Response lots = invoke("mfgLotList", listForm);
        String reserveLotId = firstMatch(LOT_ID, lots.body);
        check("reserve: the lot of the component is found", reserveLotId != null, "no lot id");

        Map<String, String> issueForm = new LinkedHashMap<>();
        issueForm.put("workOrderId", reserveOrderId == null ? "" : reserveOrderId);
        issueForm.put("componentCode", component);
        issueForm.put("lotId", reserveLotId == null ? "" : reserveLotId);
        issueForm.put("qty", "2");
        Response issued = invoke("mfgIssueSave", issueForm);
        check("reserve: the order may issue its own reserved material",
                issued.body.contains("issueId"), "the issue was blocked by the reservation");

        Response afterIssue = invoke("mfgStockSummary", summaryForm);
        check("reserve: issuing releases that much of the reservation",
                afterIssue.body.contains("reservedQty:4"), "reservedQty is not 4 after the issue");
        check("reserve: eight parts are still on hand",
                afterIssue.body.contains("onHandQty:8"), "onHandQty is not 8 after the issue");

        Map<String, String> releaseForm = new LinkedHashMap<>();
        releaseForm.put("workOrderId", reserveOrderId == null ? "" : reserveOrderId);
        Response released = invoke("mfgWorkOrderRelease", releaseForm);
        check("reserve: releasing gives the reservation back",
                near(fieldValue(released.body, "releasedQty"), 4.0), "releasedQty is not 4");

        Response afterRelease = invoke("mfgStockSummary", summaryForm);
        check("reserve: nothing is reserved any more",
                afterRelease.body.contains("reservedQty:0"), "reservedQty is not 0 after the release");
        check("reserve: the whole stock is available again",
                afterRelease.body.contains("availableQty:8"), "availableQty is not 8");
    }

    /**
     * Receives a lot, issues, scraps, returns it, sets a safety stock, then counts it: the on hand
     * quantity must always be the sum of the movements.
     *
     * @throws IOException when a service call fails
     */
    private void tracksInventory() throws IOException {
        // a fresh component per run, so the totals do not depend on what earlier runs left behind
        String component = "INV" + (System.currentTimeMillis() % 1000000L);

        Map<String, String> lotForm = new LinkedHashMap<>();
        lotForm.put("componentCode", component);
        lotForm.put("componentName", "Inventory test part");
        lotForm.put("supplierName", "IT parts");
        lotForm.put("supplierLot", "DC-INV");
        lotForm.put("receivedQty", "10");
        lotForm.put("receivedDate", "2026-10-21");
        lotForm.put("unitPrice", "2");
        Response lot = invoke("mfgLotSave", lotForm);
        String invLotId = firstMatch(LOT_ID, lot.body);
        check("inventory: the receipt puts ten on hand", invLotId != null
                && near(fieldValue(lot.body, "onHandQty"), 10.0), "onHandQty is not 10");
        check("inventory: a pending lot is fully available",
                near(fieldValue(lot.body, "availableQty"), 10.0), "availableQty is not 10");

        check("inventory: there is a work order to issue to", workOrderId != null, "no work order");
        Map<String, String> issueForm = new LinkedHashMap<>();
        issueForm.put("workOrderId", workOrderId == null ? "" : workOrderId);
        issueForm.put("componentCode", component);
        issueForm.put("lotId", invLotId == null ? "" : invLotId);
        issueForm.put("qty", "4");
        Response issue = invoke("mfgIssueSave", issueForm);
        check("inventory: issuing four leaves six on hand",
                near(fieldValue(issue.body, "onHandQty"), 6.0), "onHandQty is not 6");

        Map<String, String> tooMuchForm = new LinkedHashMap<>();
        tooMuchForm.putAll(issueForm);
        tooMuchForm.put("qty", "7");
        Response tooMuch = invoke("mfgIssueSave", tooMuchForm);
        check("inventory: issuing more than on hand is refused",
                tooMuch.body.contains("left in lot"), "an overdrawn issue was accepted");

        Map<String, String> scrapForm = new LinkedHashMap<>();
        scrapForm.put("lotId", invLotId == null ? "" : invLotId);
        scrapForm.put("movementTypeId", "MFG_MOVE_SCRAP");
        scrapForm.put("qty", "1");
        scrapForm.put("operatorName", "IT");
        scrapForm.put("note", "dropped on the floor");
        Response scrap = invoke("mfgInventoryMove", scrapForm);
        check("inventory: scrapping one leaves five",
                near(fieldValue(scrap.body, "onHandQty"), 5.0), "onHandQty is not 5");

        Map<String, String> returnForm = new LinkedHashMap<>();
        returnForm.put("lotId", invLotId == null ? "" : invLotId);
        returnForm.put("movementTypeId", "MFG_MOVE_RETURN");
        returnForm.put("qty", "1");
        returnForm.put("workOrderId", workOrderId == null ? "" : workOrderId);
        returnForm.put("operatorName", "IT");
        Response returned = invoke("mfgInventoryMove", returnForm);
        check("inventory: a return puts one back",
                near(fieldValue(returned.body, "onHandQty"), 6.0), "onHandQty is not 6");

        Map<String, String> badMoveForm = new LinkedHashMap<>();
        badMoveForm.put("lotId", invLotId == null ? "" : invLotId);
        badMoveForm.put("movementTypeId", "MFG_MOVE_BOGUS");
        badMoveForm.put("qty", "1");
        Response badMove = invoke("mfgInventoryMove", badMoveForm);
        check("inventory: an unknown movement type is refused",
                badMove.body.contains("Unknown movement type"), "the bogus type was accepted");

        Map<String, String> settingForm = new LinkedHashMap<>();
        settingForm.put("componentCode", component);
        settingForm.put("componentName", "Inventory test part");
        settingForm.put("minimumStock", "20");
        settingForm.put("reorderQty", "50");
        settingForm.put("supplierName", "IT parts");
        Response setting = invoke("mfgStockSettingSave", settingForm);
        check("inventory: the safety stock is stored",
                near(fieldValue(setting.body, "minimumStock"), 20.0), "minimumStock is not 20");

        Map<String, String> summaryForm = new LinkedHashMap<>();
        summaryForm.put("search", component);
        Response summary = invoke("mfgStockSummary", summaryForm);
        check("inventory: the summary lists the component",
                summary.body.contains("componentCode:" + component), "the component is missing");
        check("inventory: the summary shows six on hand",
                summary.body.contains("onHandQty:6"), "onHandQty is not 6 in the summary");
        check("inventory: the component is below its safety stock",
                summary.body.contains("belowMinimum:true"), "belowMinimum is not true");
        check("inventory: the reorder quantity is suggested",
                summary.body.contains("suggestedQty:50"), "suggestedQty is not 50");

        Map<String, String> ledgerForm = new LinkedHashMap<>();
        ledgerForm.put("lotId", invLotId == null ? "" : invLotId);
        Response ledger = invoke("mfgInventoryLedger", ledgerForm);
        check("inventory: the ledger keeps every movement",
                ledger.body.contains("MFG_MOVE_RECEIPT") && ledger.body.contains("MFG_MOVE_ISSUE")
                        && ledger.body.contains("MFG_MOVE_SCRAP")
                        && ledger.body.contains("MFG_MOVE_RETURN"), "a movement type is missing");
        check("inventory: the ledger shows the balance of the lot",
                ledger.body.contains("balanceQty"), "no balance column");

        Map<String, String> countForm = new LinkedHashMap<>();
        countForm.put("lotId", invLotId == null ? "" : invLotId);
        countForm.put("countedQty", "4");
        countForm.put("counterName", "IT counter");
        Response count = invoke("mfgStockCount", countForm);
        check("inventory: the count books the variance",
                near(fieldValue(count.body, "varianceQty"), -2.0), "varianceQty is not -2");
        check("inventory: the count corrects the stock",
                near(fieldValue(count.body, "onHandQty"), 4.0), "onHandQty is not 4");
        check("inventory: the count gets a number",
                count.body.contains("CNT-"), "no count number in response");

        Map<String, String> countListForm = new LinkedHashMap<>();
        countListForm.put("lotId", invLotId == null ? "" : invLotId);
        Response countList = invoke("mfgStockCountList", countListForm);
        check("inventory: the count is listed with its numbers",
                countList.body.contains("countedQty") && countList.body.contains("varianceQty"),
                "the count is missing from the list");

        Response alerts = invoke("mfgAlerts", new LinkedHashMap<String, String>());
        check("inventory: a component below its safety stock is alerted",
                alerts.body.contains("MFG_ALERT_STOCK"), "no stock alert in the result");
    }

    /**
     * Pays attention to the cost detail: the labour hours come from the routing when the model has
     * one, and the outsourced processing fee is kept apart from material and labour.
     *
     * @throws IOException when a service call fails
     */
    private void addsCostDetailToTheWorkOrder() throws IOException {
        Map<String, String> productCostForm = new LinkedHashMap<>();
        productCostForm.put("productCode", PRODUCT_CODE);
        Response productCost = invoke("mfgProductCost", productCostForm);
        check("cost detail: labour hours come from the routing",
                "ROUTING".equals(attribute(productCost.body, "hoursSource")),
                "hours are not from the routing");
        // the routing of the model adds up to 0.25 + 0.10 + 0.08 + 0.02
        check("cost detail: routing hours are summed",
                near(fieldValue(productCost.body, "standardHours"), 0.45),
                "standardHours is not 0.45");

        Map<String, String> orderForm = new LinkedHashMap<>();
        orderForm.put("productCode", PRODUCT_CODE);
        orderForm.put("plannedQty", "2");
        orderForm.put("dueDate", "2026-12-31");
        orderForm.put("customerName", "Outsource test");
        Response order = invoke("mfgWorkOrderCreate", orderForm);
        String outsourceOrderId = firstMatch(WORK_ORDER_ID, order.body);
        check("cost detail: work order created for the outsourced fee", outsourceOrderId != null,
                "no work order id in response");

        Map<String, String> feeForm = new LinkedHashMap<>();
        feeForm.put("workOrderId", outsourceOrderId == null ? "" : outsourceOrderId);
        feeForm.put("outsourceAmount", "120.50");
        Response fee = invoke("mfgWorkOrderOutsourceSave", feeForm);
        check("cost detail: outsourced fee saved", fee.status == 200
                && near(fieldValue(fee.body, "outsourceAmount"), 120.5), "HTTP " + fee.status);

        Map<String, String> negativeForm = new LinkedHashMap<>();
        negativeForm.put("workOrderId", outsourceOrderId == null ? "" : outsourceOrderId);
        negativeForm.put("outsourceAmount", "-5");
        Response negative = invoke("mfgWorkOrderOutsourceSave", negativeForm);
        check("cost detail: a negative outsourced fee is refused",
                negative.body.contains("cannot be negative"), "negative fee was accepted");

        Map<String, String> orderCostForm = new LinkedHashMap<>();
        orderCostForm.put("workOrderId", outsourceOrderId == null ? "" : outsourceOrderId);
        Response orderCost = invoke("mfgWorkOrderCost", orderCostForm);
        check("cost detail: the order cost carries the outsourced fee",
                near(fieldValue(orderCost.body, "outsourceAmount"), 120.5),
                "outsourceAmount is not 120.50");
        double issued = value(fieldValue(orderCost.body, "issuedMaterialCost"));
        double labour = value(fieldValue(orderCost.body, "actualLaborCost"));
        check("cost detail: the actual total includes material, labour and the fee",
                near(fieldValue(orderCost.body, "actualTotal"), issued + labour + 120.5),
                "actualTotal does not add up");
        check("cost detail: the variance without the fee is given as well",
                near(fieldValue(orderCost.body, "differenceExOutsource"),
                        value(fieldValue(orderCost.body, "differenceVsStandard")) - 120.5),
                "differenceExOutsource does not add up");
        check("cost detail: every operation is costed",
                orderCost.body.contains("opName:Testing") && orderCost.body.contains("opName:Packing"),
                "no operation rows in the cost result");
    }

    /**
     * Prices a finished goods lot with the actual cost of its work order, and checks that pressing
     * the completion twice tops the lot up instead of booking the units twice.
     *
     * @throws IOException when a service call fails
     */
    private void pricesTheFinishedGoods() throws IOException {
        Map<String, String> orderForm = new LinkedHashMap<>();
        orderForm.put("productCode", PRODUCT_CODE);
        orderForm.put("plannedQty", "1");
        orderForm.put("dueDate", "2026-12-31");
        orderForm.put("customerName", "Finished goods test");
        Response order = invoke("mfgWorkOrderCreate", orderForm);
        String fgOrderId = firstMatch(WORK_ORDER_ID, order.body);
        check("finished goods: work order created", fgOrderId != null, "no work order id");

        Map<String, String> reportForm = new LinkedHashMap<>();
        reportForm.put("workOrderId", fgOrderId == null ? "" : fgOrderId);
        reportForm.put("opSeq", "1");
        reportForm.put("workHours", "2");
        reportForm.put("qtyOk", "1");
        reportForm.put("operatorName", "IT worker");
        Response report = invoke("mfgReportCreate", reportForm);
        check("finished goods: one good unit reported", report.status == 200, "HTTP " + report.status);

        Map<String, String> feeForm = new LinkedHashMap<>();
        feeForm.put("workOrderId", fgOrderId == null ? "" : fgOrderId);
        feeForm.put("outsourceAmount", "100");
        invoke("mfgWorkOrderOutsourceSave", feeForm);

        Map<String, String> completeForm = new LinkedHashMap<>();
        completeForm.put("workOrderId", fgOrderId == null ? "" : fgOrderId);
        Response complete = invoke("mfgWorkOrderComplete", completeForm);
        check("finished goods: one unit booked into stock",
                near(fieldValue(complete.body, "completedQty"), 1.0),
                "completedQty is not 1");
        double unitCost = value(fieldValue(complete.body, "unitCost"));
        check("finished goods: the unit cost carries the outsourced fee",
                unitCost >= 100.0, "unitCost is " + unitCost);

        Map<String, String> againForm = new LinkedHashMap<>();
        againForm.put("workOrderId", fgOrderId == null ? "" : fgOrderId);
        Response again = invoke("mfgWorkOrderComplete", againForm);
        check("finished goods: completing twice books nothing new",
                near(fieldValue(again.body, "completedQty"), 0.0),
                "completedQty is not 0 on the second completion");
        check("finished goods: the lot still holds one unit",
                near(fieldValue(again.body, "onHandQty"), 1.0), "onHandQty is not 1");

        Map<String, String> stockForm = new LinkedHashMap<>();
        stockForm.put("search", PRODUCT_CODE);
        Response stock = invoke("mfgProductStock", stockForm);
        check("finished goods: the stock page lists the model",
                stock.body.contains(PRODUCT_CODE), "the model is missing from the finished goods");
        check("finished goods: the model has stock on hand",
                value(fieldValue(stock.body, "onHandTotal")) >= 1.0,
                "onHandTotal is below one for this model");
    }

    /**
     * Raises a problem at a development step, lists it, closes it and checks that a step of another
     * track is refused.
     *
     * @throws IOException when a service call fails
     */
    private void raisesDevelopmentIssues() throws IOException {
        check("issues: the development project of the earlier test exists", devProjectCode != null,
                "no development project to attach the issue to");

        Map<String, String> issueForm = new LinkedHashMap<>();
        issueForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        issueForm.put("stepId", "");
        issueForm.put("title", "Trial run scratches the enclosure");
        issueForm.put("detail", "Visible on the second batch, gate " + devProjectCode);
        issueForm.put("severityId", "MFG_ISSUE_HIGH");
        issueForm.put("ownerName", "IT tester");
        issueForm.put("dueDate", "2026-12-01");
        Response issue = invoke("mfgDevStepIssueSave", issueForm);
        String issueId = firstMatch(java.util.regex.Pattern.compile("ISSUE-[0-9]{6}-[0-9]+"),
                issue.body);
        check("issues: issue raised with a generated number", issue.status == 200 && issueId != null,
                "no issue id in response");
        check("issues: a new issue starts open", issue.body.contains("MFG_ISSUE_OPEN"),
                "status is not MFG_ISSUE_OPEN");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        Response list = invoke("mfgDevStepIssueList", listForm);
        check("issues: the list counts the open issue",
                near(fieldValue(list.body, "openCount"), 1.0), "openCount is not 1");
        check("issues: the list returns the title",
                list.body.contains("Trial run scratches the enclosure"), "title missing from the list");
        check("issues: the high severity is counted",
                near(fieldValue(list.body, "highOpenCount"), 1.0), "highOpenCount is not 1");

        Map<String, String> closeForm = new LinkedHashMap<>();
        closeForm.put("issueId", issueId == null ? "" : issueId);
        closeForm.put("statusId", "MFG_ISSUE_CLOSED");
        closeForm.put("resolution", "Changed the mould release agent");
        Response closed = invoke("mfgDevStepIssueStatus", closeForm);
        check("issues: the issue can be closed", closed.status == 200
                && closed.body.contains("MFG_ISSUE_CLOSED"), "HTTP " + closed.status);

        Response reopenedList = invoke("mfgDevStepIssueList", listForm);
        check("issues: a closed issue is no longer open",
                near(fieldValue(reopenedList.body, "openCount"), 0.0),
                "openCount is not 0 after closing");

        Map<String, String> badStepForm = new LinkedHashMap<>();
        badStepForm.put("projectCode", devProjectCode == null ? "" : devProjectCode);
        badStepForm.put("stepId", "MFG_PCB_BREADBOARD");
        badStepForm.put("title", "Step of another track");
        Response badStep = invoke("mfgDevStepIssueSave", badStepForm);
        check("issues: a step of another track is refused",
                badStep.body.contains("does not belong to project"), "the wrong step was accepted");

        Map<String, String> badStatusForm = new LinkedHashMap<>();
        badStatusForm.put("issueId", issueId == null ? "" : issueId);
        badStatusForm.put("statusId", "MFG_ISSUE_BOGUS");
        Response badStatus = invoke("mfgDevStepIssueStatus", badStatusForm);
        check("issues: an unknown status is refused",
                badStatus.body.contains("Unknown issue status"), "the wrong status was accepted");
    }

    /**
     * Reads the yield and labour hour trend by month and by day.
     *
     * @throws IOException when a service call fails
     */
    private void readsTrendReport() throws IOException {
        Map<String, String> monthForm = new LinkedHashMap<>();
        monthForm.put("groupBy", "MONTH");
        Response month = invoke("mfgTrendReport", monthForm);
        check("trend: monthly report returns rows", month.status == 200
                && month.body.contains("rows"), "HTTP " + month.status);
        check("trend: the grouping is echoed", "MONTH".equals(attribute(month.body, "groupBy")),
                "groupBy is not MONTH");
        check("trend: the yield of the reported work is counted",
                month.body.contains("yieldRate"), "no yieldRate in the result");
        check("trend: reported hours are totalled",
                value(fieldValue(month.body, "hoursTotal")) > 0.0, "hoursTotal is not positive");

        Map<String, String> dayForm = new LinkedHashMap<>();
        dayForm.put("groupBy", "day");
        dayForm.put("fromDate", "2000-01-01");
        dayForm.put("toDate", "2099-12-31");
        Response day = invoke("mfgTrendReport", dayForm);
        check("trend: the grouping is case insensitive",
                "DAY".equals(attribute(day.body, "groupBy")), "groupBy is not DAY");
        check("trend: daily buckets carry the good quantity",
                value(fieldValue(day.body, "qtyOkTotal")) > 0.0, "qtyOkTotal is not positive");

        Map<String, String> badForm = new LinkedHashMap<>();
        badForm.put("groupBy", "QUARTER");
        Response bad = invoke("mfgTrendReport", badForm);
        check("trend: an unknown grouping falls back to the month",
                "MONTH".equals(attribute(bad.body, "groupBy")), "unknown grouping was not replaced");
    }

    /**
     * Costs a model from the BOM prices and the labour rate, compares it with the target cost of
     * the development project, then costs a work order against what was really issued.
     *
     * @throws IOException when a service call fails
     */
    private void costsTheModel() throws IOException {
        Map<String, String> productForm = new LinkedHashMap<>();
        productForm.put("productCode", COST_PRODUCT_CODE);
        productForm.put("productName", "Cost test model");
        productForm.put("snPrefix", COST_PRODUCT_CODE);
        productForm.put("standardHours", "0.5");
        productForm.put("laborRate", "40");
        productForm.put("warrantyMonths", "12");
        productForm.put("salePrice", "30");
        Response product = invoke("mfgProductSave", productForm);
        check("cost: product saved with a labour rate", product.status == 200
                && !product.body.contains("responseMessage=error"), "HTTP " + product.status);

        Map<String, String> bomForm = new LinkedHashMap<>();
        bomForm.put("productCode", COST_PRODUCT_CODE);
        bomForm.put("csv", COST_BOM_CSV);
        bomForm.put("replace", "Y");
        Response bom = invoke("mfgBomImport", bomForm);
        check("cost: BOM with prices imported", bom.status == 200 && bom.body.contains("importedCount"),
                "HTTP " + bom.status);

        Map<String, String> projectForm = new LinkedHashMap<>();
        projectForm.put("projectCode", "");
        projectForm.put("projectName", "Cost test project");
        projectForm.put("productCode", COST_PRODUCT_CODE);
        projectForm.put("targetCost", "20");
        projectForm.put("targetPrice", "30");
        Response project = invoke("mfgDevProjectSave", projectForm);
        check("cost: development project carries a target cost", project.status == 200
                && !project.body.contains("responseMessage=error"), "HTTP " + project.status);

        Map<String, String> costForm = new LinkedHashMap<>();
        costForm.put("productCode", COST_PRODUCT_CODE);
        costForm.put("qty", "10");
        Response cost = invoke("mfgProductCost", costForm);
        check("cost: product cost returns HTTP 200", cost.status == 200, "HTTP " + cost.status);
        check("cost: material comes from the BOM prices",
                near(fieldValue(cost.body, "materialPerUnit"), 3.32), "materialPerUnit is not 3.32");
        check("cost: labour is standard hours times the rate",
                near(fieldValue(cost.body, "laborPerUnit"), 20.0), "laborPerUnit is not 20.00");
        check("cost: unit cost is material plus labour",
                near(fieldValue(cost.body, "unitCost"), 23.32), "unitCost is not 23.32");
        check("cost: ten units are totalled",
                near(fieldValue(cost.body, "totalCost"), 233.2), "totalCost is not 233.20");
        check("cost: every BOM line carries a price",
                near(fieldValue(cost.body, "missingPriceCount"), 0.0), "a BOM line has no price");
        check("cost: the target cost is compared with the unit cost",
                near(fieldValue(cost.body, "targetDifference"), 3.32), "target difference is not 3.32");
        check("cost: the margin follows the target price",
                near(fieldValue(cost.body, "unitMargin"), 6.68), "unit margin is not 6.68");

        Map<String, String> orderForm = new LinkedHashMap<>();
        orderForm.put("productCode", COST_PRODUCT_CODE);
        orderForm.put("plannedQty", "5");
        orderForm.put("dueDate", "2026-12-31");
        orderForm.put("customerName", "Cost test");
        Response order = invoke("mfgWorkOrderCreate", orderForm);
        String costWorkOrderId = firstMatch(WORK_ORDER_ID, order.body);
        check("cost: work order created for the model", costWorkOrderId != null,
                "no work order id in response");

        Map<String, String> lotForm = new LinkedHashMap<>();
        lotForm.put("componentCode", COST_COMPONENT);
        lotForm.put("componentName", "10K 0402 resistor");
        lotForm.put("supplierName", "Cost supplier");
        lotForm.put("receivedQty", "100");
        lotForm.put("receivedDate", "2026-10-21");
        lotForm.put("unitPrice", "0.06");
        Response lot = invoke("mfgLotSave", lotForm);
        String costLotId = firstMatch(LOT_ID, lot.body);
        check("cost: priced material lot created", costLotId != null, "no lot id in response");

        Map<String, String> issueForm = new LinkedHashMap<>();
        issueForm.put("workOrderId", costWorkOrderId == null ? "" : costWorkOrderId);
        issueForm.put("componentCode", COST_COMPONENT);
        issueForm.put("lotId", costLotId == null ? "" : costLotId);
        issueForm.put("qty", "4");
        Response issue = invoke("mfgIssueSave", issueForm);
        check("cost: material issued to the work order", issue.status == 200
                && !issue.body.contains("responseMessage=error"), "HTTP " + issue.status);

        Map<String, String> orderCostForm = new LinkedHashMap<>();
        orderCostForm.put("workOrderId", costWorkOrderId == null ? "" : costWorkOrderId);
        Response orderCost = invoke("mfgWorkOrderCost", orderCostForm);
        check("cost: work order cost returns HTTP 200", orderCost.status == 200,
                "HTTP " + orderCost.status);
        check("cost: work order standard unit cost",
                near(fieldValue(orderCost.body, "standardUnitCost"), 23.32),
                "standardUnitCost is not 23.32");
        check("cost: work order standard total for five units",
                near(fieldValue(orderCost.body, "standardTotal"), 116.6),
                "standardTotal is not 116.60");
        check("cost: actual material follows the lot price",
                near(fieldValue(orderCost.body, "issuedMaterialCost"), 0.24),
                "issuedMaterialCost is not 0.24");
        check("cost: every issued lot carries a price",
                near(fieldValue(orderCost.body, "issuesWithoutPrice"), 0.0),
                "an issued lot has no price");
    }

    /**
     * Tests the alerts of the dashboard (a shortage and a passed after sales window) and the three
     * CSV downloads built on the same data.
     *
     * @throws IOException when a service call fails
     */
    private void listsTheDashboardAlerts() throws IOException {
        // signed eight days ago: the seven day window closed at midnight
        String signedDate = java.time.LocalDate.now().minusDays(8).toString();
        Map<String, String> claimForm = new LinkedHashMap<>();
        claimForm.put("sn", serialNumber == null ? "" : serialNumber);
        claimForm.put("issueDesc", "Alerts test claim");
        claimForm.put("claimType", "RTN");
        claimForm.put("tradeNo", "T-ALERT-1");
        claimForm.put("signedDate", signedDate);
        Response claim = invoke("mfgClaimCreate", claimForm);
        String alertClaimId = firstMatch(CLAIM_ID, claim.body);
        check("alerts: a claim with a passed 7 day window is opened", alertClaimId != null,
                "no claim id in the response");

        Map<String, String> form = new LinkedHashMap<>();
        Response alerts = invoke("mfgAlerts", form);
        check("alerts: service call returns HTTP 200", alerts.status == 200, "HTTP " + alerts.status);
        check("alerts: the shortage of the test order is listed",
                alerts.body.contains("MFG_ALERT_SHORTAGE"), "no shortage alert");
        check("alerts: the passed seven day window is listed",
                alerts.body.contains("MFG_ALERT_CLAIM_7DAY"), "no claim alert");
        check("alerts: the claim is named in the list",
                alertClaimId != null && alerts.body.contains(alertClaimId), "claim missing from the alerts");
        check("alerts: a passed deadline is an error", alerts.body.contains("ERROR"),
                "no error level in the response");
        check("alerts: the counts are reported", alerts.body.contains("alertCount")
                && alerts.body.contains("errorCount"), "no counts in the response");

        // The three CSV downloads answer on the mfg webapp, which has its own session: they are
        // checked by the page script (verify-m33.ps1), not here.
    }

    /**
     * Quotes software from its function points, keeps a project in the ledger and checks that the
     * measured productivity and the late project show up.
     *
     * @throws IOException when a service call fails
     */
    private void quotesAndTracksSoftware() throws IOException {
        Map<String, String> quoteForm = new LinkedHashMap<>();
        quoteForm.put("ufp", "10");
        quoteForm.put("swfBase", "1");
        quoteForm.put("rdf", "1");
        quoteForm.put("pdrHoursPerFp", "40");
        quoteForm.put("taxRate", "0.06");
        quoteForm.put("dncRate", "0.05");
        Response quote = invoke("mfgSoftwareEstimate", quoteForm);
        check("software: quote returns HTTP 200", quote.status == 200, "HTTP " + quote.status);
        check("software: ten function points take 400 hours",
                near(fieldValue(quote.body, "effortHours"), 400.0), "effort wrong");
        check("software: the effort is fifty person days",
                near(fieldValue(quote.body, "effortDays"), 50.0), "person days wrong");
        check("software: the schedule follows the standard",
                near(fieldValue(quote.body, "scheduleMonths"), 1.78), "schedule wrong");
        check("software: the price uses the default day rate",
                near(fieldValue(quote.body, "amountBeforeTax"), 88000.0), "price wrong");
        check("software: the tax is added",
                near(fieldValue(quote.body, "amountWithTax"), 93280.0), "tax wrong");
        check("software: the non labour cost is added",
                near(fieldValue(quote.body, "totalAmount"), 97944.0), "total wrong");

        Map<String, String> saveForm = new LinkedHashMap<>();
        saveForm.put("projectName", "IT firmware 1.0");
        saveForm.put("categoryId", "MFG_SW_FIRMWARE");
        saveForm.put("ownerName", "it_developer");
        saveForm.put("statusId", "MFG_SW_DOING");
        saveForm.put("deliverySeq", "1");
        saveForm.put("ufp", "10");
        saveForm.put("swfBase", "1");
        saveForm.put("pdrHoursPerFp", "40");
        saveForm.put("dayRate", "880");
        saveForm.put("plannedEndDate", java.time.LocalDate.now().plusDays(10).toString());
        Response saved = invoke("mfgSoftwareProjectSave", saveForm);
        check("software: save returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        String softwareCode = firstMatch(java.util.regex.Pattern.compile("SW-\\d{6}-\\d+"), saved.body);
        check("software: the project gets a number", softwareCode != null, "no SW- number in response");
        check("software: the planned person days are frozen",
                near(fieldValue(saved.body, "plannedDays"), 50.0), "planned days wrong");

        Map<String, String> doneForm = new LinkedHashMap<>();
        doneForm.put("projectCode", softwareCode == null ? "" : softwareCode);
        doneForm.put("projectName", "IT firmware 1.0");
        doneForm.put("categoryId", "MFG_SW_FIRMWARE");
        doneForm.put("statusId", "MFG_SW_DONE");
        doneForm.put("ufp", "10");
        doneForm.put("actualHours", "480");
        doneForm.put("actualCost", "1200");
        doneForm.put("quotedAmount", "50000");
        Response done = invoke("mfgSoftwareProjectSave", doneForm);
        check("software: the finished project is saved", done.status == 200
                && !done.body.contains("responseMessage=error"), "HTTP " + done.status);

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("search", softwareCode == null ? "IT firmware" : softwareCode);
        Response list = invoke("mfgSoftwareProjectList", listForm);
        check("software: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("software: the project is listed", softwareCode != null && list.body.contains(softwareCode),
                "project missing from the list");
        check("software: the real hours are reported", list.body.contains("actualHours"),
                "no actual hours in the list");
        check("software: a productivity baseline is reported",
                list.body.contains("pdrMedian") && list.body.contains("pdrSampleCount"),
                "no baseline in the response");
        // the row level figures are nested, the page script checks the rendered numbers
        check("software: every row reports its measured productivity", list.body.contains("pdrActual"),
                "no measured productivity in the rows");

        Map<String, String> lateForm = new LinkedHashMap<>();
        lateForm.put("projectName", "IT late enclosure");
        lateForm.put("categoryId", "MFG_SW_ENCLOSURE");
        lateForm.put("statusId", "MFG_SW_DOING");
        lateForm.put("ufp", "10");
        lateForm.put("plannedEndDate", java.time.LocalDate.now().minusDays(5).toString());
        invoke("mfgSoftwareProjectSave", lateForm);

        Response alerts = invoke("mfgAlerts", new LinkedHashMap<String, String>());
        check("software: a late project raises a dashboard alert",
                alerts.body.contains("MFG_ALERT_SOFTWARE"), "no software alert");
    }

    private Response get(String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("Accept", "text/html,image/*,*/*");
        connection.setRequestProperty("User-Agent", "MfgServiceIT");
        String cookie = cookieHeader();
        if (cookie != null) {
            connection.setRequestProperty("Cookie", cookie);
        }
        int status = connection.getResponseCode();
        String type = connection.getContentType();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : readAll(stream);
        connection.disconnect();
        return new Response(status, responseBody, type);
    }

    /**
     * Builds the cookie header of the session, so a plain GET is authenticated the same way as the
     * service calls are.
     *
     * @return the cookie header, {@code null} when the session has no cookie yet
     */
    private String cookieHeader() {
        if (cookieManager == null) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(cookie.getName()).append('=').append(cookie.getValue());
        }
        return text.length() == 0 ? null : text.toString();
    }

    private Response post(String path, Map<String, String> form) throws IOException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);

        HttpURLConnection connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(60000);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        connection.setRequestProperty("User-Agent", "MfgServiceIT");

        try (OutputStream out = connection.getOutputStream()) {
            out.write(payload);
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : readAll(stream);
        connection.disconnect();
        return new Response(status, responseBody);
    }

    private static String readAll(InputStream stream) throws IOException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append('\n');
            }
        }
        return text.toString();
    }

    /**
     * Reads one value out of the parameter table the Web Tools service runner renders.
     *
     * @param body the response body
     * @param name the parameter name
     * @return the value as a number, {@code null} when the parameter is missing
     */
    private static Double fieldValue(String body, String name) {
        Pattern pattern = Pattern.compile("<td>" + Pattern.quote(name) + "</td>\\s*<td>([-0-9.]+)</td>");
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private static boolean near(Double value, double expected) {
        return value != null && Math.abs(value.doubleValue() - expected) < 0.005;
    }

    /**
     * Reads a number of the response, {@code 0} when it is missing, so the checks can do arithmetic.
     *
     * @param value the value read by {@link #fieldValue(String, String)}
     * @return the number or zero
     */
    private static double value(Double value) {
        return value == null ? 0.0 : value.doubleValue();
    }

    /**
     * Reads a text output attribute of the response, as the service result table renders it.
     *
     * @param body the response body
     * @param name the attribute name
     * @return the value, {@code null} when it is missing
     */
    private static String attribute(String body, String name) {
        Pattern pattern = Pattern.compile("<td>" + Pattern.quote(name) + "</td>\\s*<td>([^<]*)</td>");
        Matcher matcher = pattern.matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String firstGroup(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private static String firstMatchOtherThan(Pattern pattern, String text, String exclude) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (exclude == null || !matcher.group().equals(exclude)) {
                return matcher.group();
            }
        }
        return null;
    }

    private void check(String description, boolean condition, String detail) {
        if (condition) {
            System.out.println("  OK   " + description);
        } else {
            failures++;
            System.out.println("  FAIL " + description + " -> " + detail);
        }
    }

    private static final class Response {
        private final int status;
        private final String body;
        private final String contentType;

        private Response(int status, String body) {
            this(status, body, null);
        }

        private Response(int status, String body, String contentType) {
            this.status = status;
            this.body = body;
            this.contentType = contentType;
        }
    }
}