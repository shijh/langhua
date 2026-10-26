/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * The record of "who did what, when and from where" (Step 125).
 *
 * <p>OFBiz keeps its own login history, but it has no page and it stores the password that was typed
 * by mistake; the business tables only carry {@code createdStamp}, so nothing said who pressed what.
 * This class writes one short row per interesting action - a login, a refused login, somebody asking
 * for a password hint, a changed password - and drops rows older than a year.
 *
 * <p>Recording never fails the thing it is recording: every write is guarded, because a shop that
 * cannot log in because the audit table hiccuped would be worse than a missing row.
 */
public final class MfgAudit {

    private static final String MODULE = MfgAudit.class.getName();

    /** A login that worked. */
    public static final String LOGIN_OK = "LOGIN_OK";
    /** A login that was refused (wrong password, unknown account, locked account). */
    public static final String LOGIN_FAIL = "LOGIN_FAIL";
    /** Somebody asked for the password hint of an account on the "forgot password" page. */
    public static final String FORGOT_HINT = "FORGOT_HINT";
    /** An account changed its own password. */
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    /** A document was written: a work order, a receipt, a stock movement, an order ... */
    public static final String DOC_CHANGE = "DOC_CHANGE";
    /** A document write that the service refused (no permission, bad numbers, ...). */
    public static final String DOC_FAIL = "DOC_FAIL";

    /** How long a row is kept, in days: the shop asked for one year. */
    public static final int KEEP_DAYS = 365;

    /** Where the day of the last clean up is remembered, so it runs about once a day. */
    private static final String LAST_PURGE = "mfg.audit.lastPurge";

    /** The resource the settings live under (see the system settings page). */
    private static final String RESOURCE = "mfg";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");

    /** The actions with the words the page shows, in the order the dropdown lists them. */
    private static final Map<String, String> ACTIONS = new LinkedHashMap<>();

    static {
        ACTIONS.put(LOGIN_OK, "登录成功");
        ACTIONS.put(LOGIN_FAIL, "登录失败");
        ACTIONS.put(FORGOT_HINT, "查看密码提示");
        ACTIONS.put(PASSWORD_CHANGE, "修改密码");
        ACTIONS.put(DOC_CHANGE, "单据操作");
        ACTIONS.put(DOC_FAIL, "操作被拒");
    }

    /**
     * The requests that change a document, with the words the audit page shows for them. Everything
     * else (reading a page, a JSON lookup, the login itself) leaves no row, or has one of its own.
     */
    private static final Map<String, String> DOCUMENTS = new LinkedHashMap<>();

    static {
        DOCUMENTS.put("workOrderCreate", "新建工单");
        DOCUMENTS.put("reportCreate", "报工");
        DOCUMENTS.put("issueSave", "发料");
        DOCUMENTS.put("shipmentSave", "外协收发");
        DOCUMENTS.put("workOrderComplete", "完工入库");
        DOCUMENTS.put("workOrderReserve", "预留物料");
        DOCUMENTS.put("workOrderRelease", "释放预留");
        DOCUMENTS.put("workOrderVendor", "设置外协厂家");
        DOCUMENTS.put("workOrderOutsource", "外协加工费");
        DOCUMENTS.put("outsourceFeeSave", "加外协明细");
        DOCUMENTS.put("outsourceFeeDelete", "删外协明细");
        DOCUMENTS.put("productSave", "保存型号");
        DOCUMENTS.put("bomImport", "导入 BOM");
        DOCUMENTS.put("bomItemSave", "改 BOM 行");
        DOCUMENTS.put("bomItemSaveJson", "改 BOM 行");
        DOCUMENTS.put("bomVersionCreate", "新建 BOM 版本");
        DOCUMENTS.put("bomVersionActivate", "启用 BOM 版本");
        DOCUMENTS.put("routingSave", "保存工序");
        DOCUMENTS.put("lotSave", "保存批次");
        DOCUMENTS.put("lotImportCsv", "导入批次");
        DOCUMENTS.put("lotInspect", "来料检验");
        DOCUMENTS.put("snRegister", "登记 SN");
        DOCUMENTS.put("defectCreate", "登记不良");
        DOCUMENTS.put("replacementSave", "记录换料");
        DOCUMENTS.put("testImportCsv", "导入测试结果");
        DOCUMENTS.put("partCodeRename", "料号重命名");
        DOCUMENTS.put("inventoryMove", "库存流水");
        DOCUMENTS.put("stockCountSave", "盘点");
        DOCUMENTS.put("stockTransfer", "移库");
        DOCUMENTS.put("stockSettingSave", "安全库存");
        DOCUMENTS.put("locationSave", "保存库位");
        DOCUMENTS.put("receiptSave", "保存到货单");
        DOCUMENTS.put("receiptSaveJson", "保存到货单");
        DOCUMENTS.put("receiptLineSave", "到货单行");
        DOCUMENTS.put("receiptLineJson", "改到货单行");
        DOCUMENTS.put("receiptVoid", "作废到货单");
        DOCUMENTS.put("receiptRestore", "恢复到货单");
        DOCUMENTS.put("receiptFromOrder", "照订单入库");
        DOCUMENTS.put("receiptFromOrdersJson", "照订单入库");
        DOCUMENTS.put("orderExcel", "导入订单 Excel");
        DOCUMENTS.put("refundedOrderSave", "登记退款单");
        DOCUMENTS.put("refundedOrderDelete", "撤销退款单");
        DOCUMENTS.put("salesOrderSave", "保存销售订单");
        DOCUMENTS.put("salesOrderReserve", "订单预留");
        DOCUMENTS.put("salesOrderPlan", "排产");
        DOCUMENTS.put("salesOrderClose", "关闭订单");
        DOCUMENTS.put("salesOrderLineJson", "改订单行数量");
        DOCUMENTS.put("deliverySave", "出货");
        DOCUMENTS.put("devProjectSave", "保存开发项目");
        DOCUMENTS.put("devStageSave", "开发阶段");
        DOCUMENTS.put("devStepSave", "开发步骤");
        DOCUMENTS.put("devStepStatus", "步骤状态");
        DOCUMENTS.put("devIssueSave", "登记问题");
        DOCUMENTS.put("devIssueStatus", "问题状态");
        DOCUMENTS.put("ecoSave", "保存设计变更");
        DOCUMENTS.put("ecoApprove", "批准设计变更");
        DOCUMENTS.put("ecoApply", "执行设计变更");
        DOCUMENTS.put("ecoCancel", "取消设计变更");
        DOCUMENTS.put("claimCreate", "建售后单");
        DOCUMENTS.put("claimUpdate", "更新售后单");
        DOCUMENTS.put("claimExchange", "换货");
        DOCUMENTS.put("claimRepair", "返修单");
        DOCUMENTS.put("buyerFlagSave", "买家名单");
        DOCUMENTS.put("vendorSave", "保存外协厂家");
        DOCUMENTS.put("softwareProjectSave", "软件项目");
        DOCUMENTS.put("userSave", "账号");
        DOCUMENTS.put("userResetPassword", "重置密码");
        DOCUMENTS.put("settingsSave", "系统设置");
        DOCUMENTS.put("attachmentUpload", "上传附件");
        DOCUMENTS.put("attachmentDelete", "删除附件");
        DOCUMENTS.put("productImageUpload", "上传附件");
        DOCUMENTS.put("productImageDelete", "删除附件");
        DOCUMENTS.put("auditPurge", "清理操作记录");
    }

    /**
     * The words the audit page shows for a request that changes a document.
     *
     * @param requestName the last segment of the request, e.g. {@code workOrderCreate}
     * @return the words, {@code null} when that request writes no document (a list page, a lookup)
     */
    static String documentLabel(String requestName) {
        return requestName == null ? null : DOCUMENTS.get(requestName);
    }

    /**
     * The name of a request: the last segment of its path. The component is mounted with the
     * {@code /control/*} mapping, so the name is in the path info ({@code /workOrderCreate}) and not
     * in the servlet path ({@code /control}) - asking the wrong one silently recorded nothing.
     *
     * @param path the path info or the servlet path, may be {@code null}
     * @return the last segment, {@code null} when there is none
     */
    static String requestNameOf(String path) {
        if (path == null) {
            return null;
        }
        String trimmed = path.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        return name.isEmpty() ? null : name;
    }

    /** The parameters that name the document a request works on, most specific first. */
    private static final String[] ID_PARAMS = {"workOrderId", "receiptId", "salesOrderId", "deliveryId",
        "claimId", "ecoId", "projectCode", "productCode", "lotId", "vendorId", "locationId",
        "sn", "issueId", "userLoginId"};

    private MfgAudit() {
    }

    /**
     * The Chinese words of one action.
     *
     * @param actionId the action, may be {@code null} or unknown
     * @return the words, or the action itself when it is not one we write
     */
    static String label(String actionId) {
        if (actionId == null) {
            return "";
        }
        String label = ACTIONS.get(actionId);
        return label == null ? actionId : label;
    }

    /**
     * The actions for the filter dropdown.
     *
     * @return one map per action with {@code actionId} and {@code label}
     */
    static List<Map<String, String>> options() {
        List<Map<String, String>> options = new ArrayList<>();
        for (Map.Entry<String, String> action : ACTIONS.entrySet()) {
            Map<String, String> option = new LinkedHashMap<>();
            option.put("actionId", action.getKey());
            option.put("label", action.getValue());
            options.add(option);
        }
        return options;
    }

    /**
     * The key of one audit row: {@code AU-yyMMdd-NN}, the same shape as every other document number
     * of the component.
     *
     * @param day the day the row was written
     * @param sequence the number within that day, 1 based
     * @return the key
     */
    static String auditId(LocalDate day, int sequence) {
        return "AU-" + day.format(DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    /**
     * The instant before which rows are dropped: the requested number of days back from now.
     *
     * @param nowMillis "now" in milliseconds
     * @param keepDays how many days to keep
     * @return the cut off
     */
    static Timestamp keepCutoff(long nowMillis, int keepDays) {
        return new Timestamp(nowMillis - keepDays * 24L * 60L * 60L * 1000L);
    }

    /**
     * Says why a login was refused, in words a shop floor reader understands. The framework answers
     * in the language of its own labels (English here); the same three cases are turned into Chinese
     * on the login page by {@code script/signIn.groovy}, so keep the two in step.
     *
     * @param message what the framework said, may be {@code null}
     * @return the reason for the audit row
     */
    static String failureText(String message) {
        if (message == null || message.trim().isEmpty()) {
            return "";
        }
        String text = message.trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("has been disabled") || lower.contains("is disabled")) {
            return "账号被临时锁住了（连着输错密码）";
        }
        if (lower.contains("password incorrect")) {
            return "密码不对";
        }
        if (lower.contains("user not found")) {
            return "没有这个账号名";
        }
        return text;
    }

    /**
     * The name of the webapp a request came from, for the note of a row: both components write into
     * the same table, so "where" matters when reading it.
     *
     * @param contextPath the context path of the request, e.g. {@code /mfg}
     * @return the name to show
     */
    static String webappName(String contextPath) {
        if (contextPath == null) {
            return "";
        }
        String path = contextPath.startsWith("/") ? contextPath.substring(1) : contextPath;
        if ("mfg".equals(path)) {
            return "MES";
        }
        if ("learningcurve".equals(path)) {
            return "学习曲线";
        }
        return path;
    }

    /**
     * Writes one row for a request that changed a document (Step 125b): who sent which operation and
     * on what. Called from the postprocessor of both webapps, so every document - the ones written
     * today and the ones added next year - is covered without a line in each service.
     *
     * <p>Reading pages, JSON lookups for a page and the login itself leave nothing here: they either
     * change no document or have a row of their own. A refused write is written as
     * {@link #DOC_FAIL} with the reason, so a row never claims something was done that was not.
     *
     * @param request the finished request
     */
    static void recordDocumentRequest(javax.servlet.http.HttpServletRequest request) {
        try {
            if (request == null || !"POST".equalsIgnoreCase(request.getMethod())) {
                return;
            }
            String path = request.getPathInfo();
            if (path == null || path.isEmpty() || "/".equals(path)) {
                path = request.getServletPath();
            }
            String name = requestNameOf(path);
            String what = documentLabel(name);
            if (what == null) {
                return;
            }
            javax.servlet.http.HttpSession session = request.getSession(false);
            Object logged = session == null ? null : session.getAttribute("userLogin");
            String user = logged instanceof GenericValue
                    ? ((GenericValue) logged).getString("userLoginId") : null;
            if (user == null) {
                return;
            }
            String target = null;
            // a document that was just created has no number in the request: the service returns it
            // and the framework copies the answer onto the request, so look there first
            for (String param : ID_PARAMS) {
                Object value = request.getAttribute(param);
                if (value instanceof CharSequence && ((CharSequence) value).toString().trim().length() > 0) {
                    target = value.toString().trim();
                    break;
                }
            }
            for (int i = 0; target == null && i < ID_PARAMS.length; i++) {
                String value = request.getParameter(ID_PARAMS[i]);
                if (value != null && !value.trim().isEmpty()) {
                    target = value.trim();
                }
            }
            String problem = refusalOf(request);
            String action = problem == null ? DOC_CHANGE : DOC_FAIL;
            String note = what + (problem == null ? "" : "：" + problem);
            record(delegatorOf(request), user, action, target, request.getRemoteAddr(), note);
        } catch (Throwable t) {
            Debug.logWarning(t, "Could not write the audit row of a request", MODULE);
        }
    }

    /**
     * Why a request was refused, in the words of the service: a failing service leaves either the
     * single message or the list of messages on the request, and only the first one is read.
     *
     * @param request the finished request
     * @return the reason, {@code null} when nothing was refused
     */
    static String refusalOf(javax.servlet.http.HttpServletRequest request) {
        Object single = request.getAttribute("_ERROR_MESSAGE_");
        if (single != null && !String.valueOf(single).trim().isEmpty()) {
            return String.valueOf(single);
        }
        Object list = request.getAttribute("_ERROR_MESSAGE_LIST_");
        if (list instanceof java.util.Collection) {
            for (Object message : (java.util.Collection<?>) list) {
                if (message != null && !String.valueOf(message).trim().isEmpty()) {
                    String text = String.valueOf(message);
                    return text.length() > 200 ? text.substring(0, 200) + "…" : text;
                }
            }
        }
        return null;
    }

    /**
     * The delegator of a request: the framework puts it on the request, and on the servlet context
     * of the webapp as a fallback.
     *
     * @param request the request
     * @return the delegator, {@code null} when neither carries one
     */
    private static Delegator delegatorOf(javax.servlet.http.HttpServletRequest request) {
        Object fromRequest = request.getAttribute("delegator");
        if (fromRequest instanceof Delegator) {
            return (Delegator) fromRequest;
        }
        javax.servlet.ServletContext webapp = request.getServletContext();
        Object fromContext = webapp == null ? null : webapp.getAttribute("delegator");
        return fromContext instanceof Delegator ? (Delegator) fromContext : null;
    }

    /**
     * Writes one row. Everything is guarded: an audit row that cannot be written must never keep
     * somebody from logging in.
     *
     * @param delegator the delegator, may be {@code null}
     * @param userLoginId who did it, empty when nobody was logged in (the login form, the password hint)
     * @param actionId one of {@link #LOGIN_OK}, {@link #LOGIN_FAIL}, {@link #FORGOT_HINT},
     *        {@link #PASSWORD_CHANGE}
     * @param targetId what it was done to: the account name that was typed, empty when it is the actor
     * @param remoteAddr the address the request came from
     * @param note what else is worth knowing: why a login failed, which webapp it was
     */
    static void record(Delegator delegator, String userLoginId, String actionId, String targetId,
            String remoteAddr, String note) {
        if (delegator == null || actionId == null) {
            return;
        }
        try {
            LocalDate today = LocalDate.now();
            String prefix = "AU-" + today.format(DAY) + "-";
            int max = 0;
            List<GenericValue> todayRows = EntityQuery.use(delegator).from("MfgAuditLog")
                    .where(EntityCondition.makeCondition("auditId", EntityOperator.LIKE, prefix + "%"))
                    .queryList();
            for (GenericValue row : todayRows) {
                String id = row.getString("auditId");
                if (id == null || id.length() <= prefix.length()) {
                    continue;
                }
                try {
                    max = Math.max(max, Integer.parseInt(id.substring(prefix.length())));
                } catch (NumberFormatException ignored) {
                    // a hand written key: it does not take part in the numbering
                }
            }
            Map<String, Object> fields = new HashMap<>();
            fields.put("auditId", auditId(today, max + 1));
            fields.put("userLoginId", cut(userLoginId, 20));
            fields.put("actionId", actionId);
            fields.put("targetId", cut(targetId, 100));
            fields.put("remoteAddr", cut(remoteAddr, 100));
            fields.put("note", cut(note, 255));
            delegator.create("MfgAuditLog", fields);
        } catch (Exception e) {
            Debug.logWarning(e, "Could not write the audit row [" + actionId + "]", MODULE);
        }
        purgeIfDue(delegator);
    }

    /**
     * Drops the rows older than {@link #KEEP_DAYS}.
     *
     * @param delegator the delegator
     * @param cutoff everything written before this instant goes
     * @return how many rows were dropped
     * @throws GenericEntityException when the table cannot be read
     */
    static int purge(Delegator delegator, Timestamp cutoff) throws GenericEntityException {
        return delegator.removeByCondition("MfgAuditLog",
                EntityCondition.makeCondition("createdStamp", EntityOperator.LESS_THAN, cutoff));
    }

    /**
     * Runs the clean up about once a day, on the back of the recording that happens anyway. Nobody
     * has to remember to press a button for the one year promise to hold.
     *
     * @param delegator the delegator
     */
    private static void purgeIfDue(Delegator delegator) {
        try {
            String today = LocalDate.now().toString();
            GenericValue property = EntityQuery.use(delegator).from("SystemProperty")
                    .where("systemResourceId", RESOURCE, "systemPropertyId", LAST_PURGE).queryOne();
            if (property != null && today.equals(property.getString("systemPropertyValue"))) {
                return;
            }
            int removed = purge(delegator, keepCutoff(System.currentTimeMillis(), KEEP_DAYS));
            if (property == null) {
                Map<String, Object> fields = new HashMap<>();
                fields.put("systemResourceId", RESOURCE);
                fields.put("systemPropertyId", LAST_PURGE);
                fields.put("systemPropertyValue", today);
                fields.put("description", "The day the audit log was last trimmed to one year");
                delegator.create("SystemProperty", fields);
            } else {
                property.set("systemPropertyValue", today);
                property.store();
            }
            if (removed > 0) {
                Debug.logInfo("Audit log: dropped " + removed + " rows older than " + KEEP_DAYS + " days", MODULE);
            }
        } catch (Exception e) {
            Debug.logWarning(e, "Could not trim the audit log", MODULE);
        }
    }

    /**
     * Cuts a value to the length its column has.
     *
     * @param value the value, may be {@code null}
     * @param maxLength the column width
     * @return the value, empty when it was {@code null}
     */
    private static String cut(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
