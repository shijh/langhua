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

    private static final Pattern WORK_ORDER_ID = Pattern.compile("WO-\\d{6}-\\d{3}");
    private static final Pattern SERIAL_NUMBER = Pattern.compile(SN_PREFIX + "-\\d{6}-\\d{4}");
    private static final Pattern LOT_ID = Pattern.compile("LOT-\\d{6}-\\d{2}");
    private static final Pattern DELIVERY_ID = Pattern.compile("DO-\\d{6}-\\d{2}");
    private static final Pattern VENDOR_ID = Pattern.compile("V-\\d{6}-\\d{2}");
    private static final Pattern CLAIM_ID = Pattern.compile("RMA-\\d{6}-\\d{2}");
    private static final Pattern DEV_PROJECT_ID = Pattern.compile("DEV-\\d{6}-\\d{2}");
    private static final Pattern ECO_ID = Pattern.compile("ECO-\\d{6}-\\d{2}");
    private static final Pattern BOM_VERSION_ID = Pattern.compile(PRODUCT_CODE + "-V\\d+");
    private static final Pattern LOT_NUMBER = Pattern.compile("LOT-\\d{6}-\\d{2}");
    private static final String ENCLOSURE_PART = "ENC-IT-SHELL";

    private static final Pattern PHOTO_SEQ = Pattern.compile("<td>seq</td>\\s*<td>(\\d+)</td>");
    private static final String TINY_PNG_BASE64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFklEQVQI12P4//8/AzYEYgADMAciAAB6"
        + "wwMBAAAAAElFTkSuQmCC";
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
            it.recordsShipments();
            it.readsWorkOrderMaterials();
            it.tracesLotBackwards();
            it.readsShortageList();
            it.importsLotsFromCsv();
            it.estimatesSnRangeByFifo();
            it.recordsReplacement();
            it.importsTestResults();
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
        form.put("productCode", PRODUCT_CODE);
        form.put("fileName", "C:\\Users\\it\\Pictures\\正面 照片.png");
        form.put("contentType", "image/png");
        form.put("base64Data", TINY_PNG_BASE64);
        form.put("note", "IT 照片");
        Response saved = invoke("mfgProductImageSave", form);
        check("photo: save returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        String seq = firstGroup(PHOTO_SEQ, saved.body);
        check("photo: a sequence number is returned", seq != null, "no seq in response");
        check("photo: the file name is cleaned", saved.body.contains("png") && !saved.body.contains("Users"),
                "file name still carries a path");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("productCode", PRODUCT_CODE);
        Response list = invoke("mfgProductImageList", listForm);
        check("photo: list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("photo: the photo is listed", list.body.contains("image/png"), "content type missing from the list");

        Map<String, String> detailForm = new LinkedHashMap<>();
        detailForm.put("productCode", PRODUCT_CODE);
        Response detail = invoke("mfgProductImageList", detailForm);
        check("photo: the model reports one photo", detail.body.contains("imageCount"),
                "no imageCount in the response");

        // the photo is served through the controller; the page script checks the bytes with a real
        // browser session, here only the service side is covered
        Map<String, String> rejectForm = new LinkedHashMap<>();
        rejectForm.put("productCode", PRODUCT_CODE);
        rejectForm.put("fileName", "manual.pdf");
        rejectForm.put("contentType", "application/pdf");
        rejectForm.put("base64Data", TINY_PNG_BASE64);
        Response rejected = invoke("mfgProductImageSave", rejectForm);
        check("photo: a pdf is rejected", rejected.body.contains("Only JPG, PNG and GIF photos are supported"),
                "a pdf was stored");

        Map<String, String> deleteForm = new LinkedHashMap<>();
        deleteForm.put("productCode", PRODUCT_CODE);
        deleteForm.put("seq", seq == null ? "1" : seq);
        Response deleted = invoke("mfgProductImageDelete", deleteForm);
        check("photo: delete returns HTTP 200", deleted.status == 200
                && !deleted.body.contains("does not exist"), "HTTP " + deleted.status);

        Map<String, String> emptyListForm = new LinkedHashMap<>();
        emptyListForm.put("productCode", PRODUCT_CODE);
        Response emptyList = invoke("mfgProductImageList", emptyListForm);
        check("photo: the list is empty after the delete", emptyList.body.contains("imageCount</td>")
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
