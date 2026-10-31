/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * Integration test for the {@code learningCurveEstimate} service.
 *
 * <p>Unlike the unit tests, this exercises the service through OFBiz's own service engine
 * ({@code ModelService} validation + {@code dispatcher.runSync}) over the Web Tools endpoint
 * {@code /webtools/control/scheduleServiceSync} with {@code _RUN_SYNC_=Y}.
 *
 * <p>Prerequisites: a running OFBiz instance with the learningcurve component deployed and
 * data loaded ({@code gradlew loadAll}), and a user allowed to invoke services
 * (the default {@code admin/ofbiz} user is fine).
 *
 * <p>Configuration (system properties): {@code ofbiz.url}, {@code ofbiz.user}, {@code ofbiz.password}.
 * Defaults: {@code https://localhost:8443}, {@code admin}, {@code ofbiz}.
 *
 * <p>Exit code is 0 when all checks pass, 1 otherwise.
 */
public final class LearningCurveServiceIT {

    private static final String SERVICE_NAME = "learningCurveEstimate";
    private static final String PRODUCT_CODE = "IT-MODEL-A";

    private final String baseUrl;
    private final String user;
    private final String password;
    private int failures;

    private LearningCurveServiceIT(String baseUrl, String user, String password) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.user = user;
        this.password = password;
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = System.getProperty("ofbiz.url", "https://localhost:8443");
        String user = System.getProperty("ofbiz.user", "admin");
        String password = System.getProperty("ofbiz.password", "ofbiz");

        System.out.println("Integration test against " + baseUrl + " as user " + user);

        LearningCurveServiceIT it = new LearningCurveServiceIT(baseUrl, user, password);
        it.setUp();
        try {
            it.login();
            it.estimateWright();
            it.estimateCrawford();
            it.rejectsMalformedHistoryData();
            it.savesAndReadsBack();
            it.quotesSoftware();
            it.forecastsTheProductivity();
            it.readsShopFloorPeriods();
            it.appliesTheCurveAsStandardHours();
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
        check("login page does not report a login error", !response.body.contains("following error occurred during login"),
                "login error in response");
    }

    private void estimateWright() throws IOException {
        Response response = invoke("2:200;3:285;5:450", "5", "0.95", "1");

        check("wright: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("wright: response contains the estimate field", response.body.contains("estimate"),
                "no 'estimate' in response");
        check("wright: response contains the expected estimate", response.body.contains("440.18"),
                "expected 440.18 in response");
        check("wright: no service error", !response.body.contains("Invalid historyData entry"),
                "unexpected service error");
        System.out.println("[wright] " + firstLineWith(response.body, "estimate"));
    }

    private void estimateCrawford() throws IOException {
        Response response = invoke("2:200;3:285;5:450", "5", "0.95", "2");

        check("crawford: service call returns HTTP 200", response.status == 200, "HTTP " + response.status);
        check("crawford: response contains the expected estimate", response.body.contains("445.51"),
                "expected 445.51 in response");
        System.out.println("[crawford] " + firstLineWith(response.body, "estimate"));
    }

    private void rejectsMalformedHistoryData() throws IOException {
        Response response = invoke("not-a-number", "5", "0.95", "1");

        check("invalid input: service reports an error", response.body.contains("Invalid historyData entry"),
                "no validation error in response");
        System.out.println("[invalid input] error reported as expected");
    }

    private Response invoke(String historyData, String newUnitNumber, String leastSquare, String methodType)
            throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", SERVICE_NAME);
        form.put("_RUN_SYNC_", "Y");
        form.put("historyData", historyData);
        form.put("newUnitNumber", newUnitNumber);
        form.put("leastSquare", leastSquare);
        form.put("methodType", methodType);
        return post("/webtools/control/scheduleServiceSync", form);
    }

    /**
     * Quotes software through the MES cost model and keeps the quotation: ten function points at the
     * default day rate, then the same quote read back from the list.
     *
     * @throws IOException when the service call fails
     */
    private void quotesSoftware() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", "learningCurveSoftwareQuote");
        form.put("_RUN_SYNC_", "Y");
        form.put("ufp", "10");
        form.put("swfBase", "1");
        form.put("rdf", "1");
        form.put("pdrHoursPerFp", "40");
        form.put("taxRate", "0.06");
        form.put("dncRate", "0.05");
        Response quote = post("/webtools/control/scheduleServiceSync", form);
        check("software quote: service call returns HTTP 200", quote.status == 200, "HTTP " + quote.status);
        check("software quote: the effort is 400 hours", quote.body.contains("effortHours")
                && quote.body.contains("400"), "no 400 hours in response");
        check("software quote: the price uses the default day rate", quote.body.contains("88000"),
                "expected 88000 before tax");
        check("software quote: the total is reported", quote.body.contains("97944"),
                "expected 97944 in total");

        Map<String, String> saveForm = new LinkedHashMap<>();
        saveForm.put("SERVICE_NAME", "learningCurveSaveSoftwareQuote");
        saveForm.put("_RUN_SYNC_", "Y");
        saveForm.put("projectName", "IT software quote");
        saveForm.put("customerName", "IT customer");
        saveForm.put("ilfCount", "4");
        saveForm.put("eifCount", "2");
        saveForm.put("swfBase", "1");
        saveForm.put("rdf", "1");
        saveForm.put("pdrHoursPerFp", "40");
        saveForm.put("dayRate", "880");
        saveForm.put("taxRate", "0.06");
        saveForm.put("dncRate", "0.05");
        Response saved = post("/webtools/control/scheduleServiceSync", saveForm);
        check("software quote: saving returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        check("software quote: a quote id comes back", saved.body.contains("quoteId"),
                "no quoteId in response");
        check("software quote: the size is 170 function points", saved.body.contains("170"),
                "expected 170 function points (35 x 4 + 15 x 2)");

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("SERVICE_NAME", "learningCurveListSoftwareQuotes");
        listForm.put("_RUN_SYNC_", "Y");
        Response list = post("/webtools/control/scheduleServiceSync", listForm);
        check("software quote: the list returns HTTP 200", list.status == 200, "HTTP " + list.status);
        check("software quote: the saved quote is listed", list.body.contains("IT software quote"),
                "quote missing from the list");
        check("software quote: the list counts the quotes", list.body.contains("quoteCount"),
                "no quoteCount in response");

        // the quote becomes a software project of the appliance component, once
        String quoteId = valueOf(saved.body, "quoteId");
        check("software quote: the quote id can be read", quoteId != null, "no quote id in response");
        Map<String, String> projectForm = new LinkedHashMap<>();
        projectForm.put("SERVICE_NAME", "learningCurveQuoteToProject");
        projectForm.put("_RUN_SYNC_", "Y");
        projectForm.put("quoteId", quoteId == null ? "" : quoteId);
        projectForm.put("categoryId", "MFG_SW_FIRMWARE");
        Response project = post("/webtools/control/scheduleServiceSync", projectForm);
        check("software quote: it becomes a project", project.status == 200, "HTTP " + project.status);
        String projectCode = valueOf(project.body, "projectCode");
        check("software quote: the project has a number", projectCode != null
                && projectCode.matches("SW-\\d{6}-\\d+"), "no SW- number: " + projectCode);
        System.out.println("[software quote] project " + projectCode);

        Response again = post("/webtools/control/scheduleServiceSync", projectForm);
        check("software quote: converting twice keeps one project",
                projectCode != null && projectCode.equals(valueOf(again.body, "projectCode")),
                "a second project was created");
    }

    /**
     * Reads one value out of the parameter table the Web Tools service runner renders.
     *
     * @param body the response body
     * @param name the parameter name
     * @return the value, {@code null} when the parameter is missing
     */
    private String valueOf(String body, String name) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("<td>" + java.util.regex.Pattern.quote(name) + "</td>\\s*<td>([^<]*)</td>")
                .matcher(body);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /**
     * Forecasts the productivity of the next delivery from the software projects of MES.
     *
     * @throws IOException when the service call fails
     */
    private void forecastsTheProductivity() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", "learningCurvePdrForecast");
        form.put("_RUN_SYNC_", "Y");
        Response forecast = post("/webtools/control/scheduleServiceSync", form);

        check("pdr forecast: service call returns HTTP 200", forecast.status == 200,
                "HTTP " + forecast.status);
        check("pdr forecast: the samples are reported", forecast.body.contains("sampleCount"),
                "no sampleCount in response");
        // with fewer than two finished projects the service explains itself instead of failing
        check("pdr forecast: it either predicts or explains",
                forecast.body.contains("predictedPdr") || forecast.body.contains("message"),
                "neither a prediction nor a message");
    }

    /**
     * Asks the learning curve for the per-period points of the MES shop floor report. The service reads
     * the work orders of the MES component, so on a machine without shop floor data it has to answer
     * with zero periods instead of an error.
     *
     * @throws IOException when the service call fails
     */
    private void readsShopFloorPeriods() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", "learningCurveMesPeriods");
        form.put("groupBy", "MONTH");
        form.put("_RUN_SYNC_", "Y");
        Response periods = post("/webtools/control/scheduleServiceSync", form);

        check("mes periods: service call returns HTTP 200", periods.status == 200,
                "HTTP " + periods.status);
        check("mes periods: the period count is reported", periods.body.contains("periodCount"),
                "no periodCount in response");
        check("mes periods: the grouping comes back", periods.body.contains("MONTH"),
                "no groupBy in response");
        check("mes periods: no service error", !periods.body.contains("errorMessage"),
                "service reported an error");
    }

    /**
     * Writes the hours of a curve back into the standard hours of a model of the MES component: the
     * preview proposes a value, the apply stores it with an audit row, and a cost estimate has to be
     * refused because it says nothing about hours.
     *
     * @throws IOException when a service call fails
     */
    private void appliesTheCurveAsStandardHours() throws IOException {
        String productCode = "LCIT" + (System.currentTimeMillis() % 1000000L);

        Map<String, String> product = new LinkedHashMap<>();
        product.put("SERVICE_NAME", "mfgProductSave");
        product.put("_RUN_SYNC_", "Y");
        product.put("productCode", productCode);
        product.put("productName", "IT standard hours model");
        product.put("standardHours", "9");
        product.put("laborRate", "120");
        Response created = post("/webtools/control/scheduleServiceSync", product);
        check("hours: the MES model is created",
                created.status == 200 && !created.body.contains("errorMessage"),
                "HTTP " + created.status + " " + valueOf(created.body, "errorMessage"));

        Map<String, String> estimateForm = new LinkedHashMap<>();
        estimateForm.put("SERVICE_NAME", "learningCurveSaveEstimate");
        estimateForm.put("_RUN_SYNC_", "Y");
        estimateForm.put("productCode", productCode);
        estimateForm.put("measureType", "HOURS");
        estimateForm.put("historyData", "2:20;3:28;5:40");
        estimateForm.put("newUnitNumber", "5");
        estimateForm.put("leastSquare", "0.9");
        estimateForm.put("methodType", "1");
        String estimateId = extractEstimateId(post("/webtools/control/scheduleServiceSync", estimateForm).body);
        check("hours: the hours estimate is stored", estimateId != null, "could not parse estimateId");
        if (estimateId == null) {
            return;
        }

        Map<String, String> previewForm = new LinkedHashMap<>();
        previewForm.put("SERVICE_NAME", "learningCurveStandardHours");
        previewForm.put("_RUN_SYNC_", "Y");
        previewForm.put("estimateId", estimateId);
        previewForm.put("baseQty", "5");
        Response preview = post("/webtools/control/scheduleServiceSync", previewForm);

        String proposedText = valueOf(preview.body, "proposedUnitHours");
        check("hours: the preview proposes unit hours", proposedText != null, "no proposedUnitHours");
        check("hours: the model is found in MES", "Y".equals(valueOf(preview.body, "existsInMes")),
                "existsInMes=" + valueOf(preview.body, "existsInMes"));
        check("hours: it can be applied", "Y".equals(valueOf(preview.body, "canApply")),
                "canApply=" + valueOf(preview.body, "canApply"));
        check("hours: the hours in use are read", "9.0".equals(valueOf(preview.body, "currentHours")),
                "currentHours=" + valueOf(preview.body, "currentHours"));

        Map<String, String> applyForm = new LinkedHashMap<>();
        applyForm.put("SERVICE_NAME", "learningCurveApplyStandardHours");
        applyForm.put("_RUN_SYNC_", "Y");
        applyForm.put("estimateId", estimateId);
        applyForm.put("baseQty", "5");
        Response applied = post("/webtools/control/scheduleServiceSync", applyForm);
        check("hours: the apply reports success", "Y".equals(valueOf(applied.body, "applied")),
                "applied=" + valueOf(applied.body, "applied"));
        check("hours: the old value is kept in the audit", "9.0".equals(valueOf(applied.body, "oldHours")),
                "oldHours=" + valueOf(applied.body, "oldHours"));
        check("hours: an audit row is written", valueOf(applied.body, "applyId") != null, "no applyId");

        // reading the proposal again shows what the model carries now
        Response after = post("/webtools/control/scheduleServiceSync", previewForm);
        check("hours: the model now says the hours come from the curve",
                "LEARNING_CURVE".equals(valueOf(after.body, "currentSource")),
                "currentSource=" + valueOf(after.body, "currentSource"));
        double proposed = proposedText == null ? -1.0 : Double.parseDouble(proposedText);
        String storedText = valueOf(after.body, "currentHours");
        double stored = storedText == null ? -2.0 : Double.parseDouble(storedText);
        check("hours: the model holds the value of the curve", Math.abs(stored - proposed) < 1e-6,
                "stored " + storedText + " against " + proposedText);

        Map<String, String> costForm = new LinkedHashMap<>();
        costForm.put("SERVICE_NAME", "learningCurveSaveEstimate");
        costForm.put("_RUN_SYNC_", "Y");
        costForm.put("productCode", productCode);
        costForm.put("measureType", "COST");
        costForm.put("historyData", "2:200;3:285;5:450");
        costForm.put("newUnitNumber", "5");
        String costEstimateId = extractEstimateId(post("/webtools/control/scheduleServiceSync", costForm).body);
        check("hours: a cost estimate is stored for the refusal", costEstimateId != null, "no estimate id");
        if (costEstimateId != null) {
            Map<String, String> costApplyForm = new LinkedHashMap<>();
            costApplyForm.put("SERVICE_NAME", "learningCurveApplyStandardHours");
            costApplyForm.put("_RUN_SYNC_", "Y");
            costApplyForm.put("estimateId", costEstimateId);
            Response refused = post("/webtools/control/scheduleServiceSync", costApplyForm);
            check("hours: a cost estimate cannot become standard hours",
                    refused.body.contains("errorMessage") && !"Y".equals(valueOf(refused.body, "applied")),
                    "the apply did not refuse");
        }
    }

    /**
     * Stores an estimate through the {@code learningCurveSaveEstimate} service and reads it back
     * through {@code learningCurveChartData}, which proves the entity model, the insert and the
     * chart query all work against the database.
     */
    private void savesAndReadsBack() throws IOException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("SERVICE_NAME", "learningCurveSaveEstimate");
        form.put("_RUN_SYNC_", "Y");
        form.put("productCode", PRODUCT_CODE);
        form.put("measureType", "COST");
        form.put("historyData", "2:200;3:285;5:450");
        form.put("newUnitNumber", "5");
        form.put("leastSquare", "0.95");
        form.put("methodType", "1");
        Response saved = post("/webtools/control/scheduleServiceSync", form);

        check("save: service call returns HTTP 200", saved.status == 200, "HTTP " + saved.status);
        check("save: response contains estimateId", saved.body.contains("estimateId"),
                "no 'estimateId' in response");
        check("save: unit cost stored", saved.body.contains("88.03"), "expected 88.03 in response");
        String estimateId = extractEstimateId(saved.body);
        check("save: estimateId parsed", estimateId != null, "could not parse estimateId");
        System.out.println("[save] estimateId = " + estimateId);

        Map<String, String> listForm = new LinkedHashMap<>();
        listForm.put("SERVICE_NAME", "learningCurveListEstimates");
        listForm.put("_RUN_SYNC_", "Y");
        listForm.put("productCode", PRODUCT_CODE);
        Response listed = post("/webtools/control/scheduleServiceSync", listForm);
        check("list: service call returns HTTP 200", listed.status == 200, "HTTP " + listed.status);
        check("list: saved product is returned", listed.body.contains(PRODUCT_CODE),
                "expected " + PRODUCT_CODE + " in response");

        if (estimateId != null) {
            Map<String, String> chartForm = new LinkedHashMap<>();
            chartForm.put("SERVICE_NAME", "learningCurveChartData");
            chartForm.put("_RUN_SYNC_", "Y");
            chartForm.put("estimateId", estimateId);
            Response chart = post("/webtools/control/scheduleServiceSync", chartForm);
            check("chart: service call returns HTTP 200", chart.status == 200, "HTTP " + chart.status);
            check("chart: response contains the chart points", chart.body.contains("points"),
                    "no 'points' in response");
            check("chart: response contains the product code", chart.body.contains(PRODUCT_CODE),
                    "expected " + PRODUCT_CODE + " in response");
        }
    }

    private static String extractEstimateId(String body) {
        Matcher matcher = Pattern.compile("estimateId\\s*</td>\\s*<td>\\s*([0-9]+)").matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        Matcher loose = Pattern.compile("estimateId[^0-9]{0,40}([0-9]+)").matcher(body);
        return loose.find() ? loose.group(1) : null;
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
        connection.setRequestProperty("User-Agent", "LearningCurveServiceIT");

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

    private static String firstLineWith(String text, String needle) {
        for (String line : text.split("\n")) {
            if (line.contains(needle)) {
                return line.trim().replaceAll("\\s+", " ");
            }
        }
        return "(not found)";
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
