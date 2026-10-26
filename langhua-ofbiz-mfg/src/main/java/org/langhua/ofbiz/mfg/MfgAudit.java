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
    }

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
