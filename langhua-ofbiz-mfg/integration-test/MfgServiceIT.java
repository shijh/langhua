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
    private static final String KEY_COMPONENT = "U1-BT5-SOC";
    private static final String SUPPLIER_LOT = "DC-IT-2410";

    private final String baseUrl;
    private final String user;
    private final String password;
    private int failures;

    private String workOrderId;
    private String serialNumber;
    private String lotId;
    private String deliveryId;
    private String vendorId;
    private String claimId;
    private String exchangeSn;
    private String repairWorkOrderId;
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
            it.registersWarrantyClaim();
            it.readsWarrantyClaim();
            it.opensRepairWorkOrder();
            it.shipsReplacement();
            it.closesWarrantyClaim();
            it.rejectsBadClaimStatus();
            it.rejectsZeroHoursReport();
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

        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cookieManager);
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
        Response response = invoke("mfgShortageList", new LinkedHashMap<String, String>());
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
        form.put("buyerNick", "it_buyer");
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

    private Response invoke(String serviceName, Map<String, String> parameters) throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", serviceName);
        form.put("_RUN_SYNC_", "Y");
        form.putAll(parameters);
        return post("/webtools/control/scheduleServiceSync", form);
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

        private Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
