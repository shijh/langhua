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

package org.langhua.ofbiz.mfg;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.common.login.LoginServices;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.condition.EntityCondition;
import org.apache.ofbiz.entity.condition.EntityOperator;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.entity.util.EntityUtil;
import org.apache.ofbiz.entity.util.EntityUtilProperties;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.GenericServiceException;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * The accounts of the shop: the password gate, the account page and the audit log behind it.
 *
 * <p>An account is a {@code UserLogin}, the {@code Person} behind it and exactly one security
 * group. The page never lets the last administrator lock itself out, an account is dropped by
 * disabling the login instead of deleting a row other applications point at, and every change of a
 * password leaves a line in the audit log, so "who changed whose password" has one answer.
 *
 * <p>The permission vocabulary ({@code PERM_*}, {@link MfgSecurity#permissionProblem}) stays in
 * {@code MfgServices}: every area of the component speaks it, and this class only asks it
 * questions. Moved out of {@code MfgServices} in Step 166.
 */
public final class MfgAccountServices {

    private MfgAccountServices() {
    }

    /**
     * Tells the decorator whether the logged in account still uses the password that ships with a
     * fresh OFBiz database, so every page can show the change form instead of its content.
     *
     * <p>The whole gate can be switched off with the system property
     * {@code org.langhua.mfg.requirePasswordChange=N}; development instances start that way because
     * the page check scripts log in with the default account.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgPasswordStatus}
     * @return the service result with {@code mustChange} ({@code Y} / {@code N})
     */
    public static Map<String, Object> passwordStatus(DispatchContext dctx, Map<String, ? extends Object> context) {
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("mustChange", "N");
        result.put("userLoginId", "");
        result.put("message", "");
        if (!"Y".equalsIgnoreCase(System.getProperty(MfgConstants.FORCE_PASSWORD_CHANGE_PROPERTY, "Y"))) {
            return result;
        }
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return result;
        }
        String userLoginId = userLogin.getString("userLoginId");
        result.put("userLoginId", userLoginId == null ? "" : userLoginId);
        if (isDefaultPassword(dctx.getDelegator(), userLogin.getString("currentPassword"))) {
            result.put("mustChange", "Y");
            result.put("message", "当前账号还在用出厂密码，请先改成自己的密码再继续使用");
        }
        return result;
    }

    /**
     * Changes the password of the logged in account. The current password is always checked, even
     * for an administrator (OFBiz itself lets an administrator skip that check).
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgChangePassword}
     * @return the service result with {@code changed} = {@code Y}
     */
    public static Map<String, Object> changePassword(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return ServiceUtil.returnError("请先登录再改密码");
        }
        String userLoginId = userLogin.getString("userLoginId");
        String currentPassword = MfgSupport.toText(context.get("currentPassword"));
        if (currentPassword == null || !matchesStoredPassword(delegator, userLoginId, currentPassword)) {
            return ServiceUtil.returnError("当前密码不对");
        }
        String newPassword = MfgSupport.toText(context.get("newPassword"));
        String newPasswordVerify = MfgSupport.toText(context.get("newPasswordVerify"));
        // checked here as well: OFBiz skips the "must differ" rule for an administrator
        String problem = passwordProblem(currentPassword, newPassword, newPasswordVerify);
        if (problem != null) {
            return ServiceUtil.returnError(problem);
        }
        Map<String, Object> updateContext = new HashMap<>();
        updateContext.put("userLogin", userLogin);
        updateContext.put("userLoginId", userLoginId);
        updateContext.put("currentPassword", currentPassword);
        updateContext.put("newPassword", newPassword);
        updateContext.put("newPasswordVerify", newPasswordVerify);
        updateContext.put("passwordHint", MfgSupport.toText(context.get("passwordHint")));
        try {
            Map<String, Object> updated = dctx.getDispatcher().runSync("updatePassword", updateContext, -1, true);
            if (ServiceUtil.isError(updated)) {
                Object errors = updated.get("errorMessageList");
                if (errors instanceof List) {
                    List<String> messages = new ArrayList<>();
                    for (Object error : (List<?>) errors) {
                        messages.add(String.valueOf(error));
                    }
                    return ServiceUtil.returnError(messages);
                }
                return ServiceUtil.returnError(ServiceUtil.getErrorMessage(updated));
            }
        } catch (GenericServiceException e) {
            return ServiceUtil.returnError("改密码失败：" + e.getMessage());
        }
        Map<String, Object> result = ServiceUtil.returnSuccess("密码已修改");
        result.put("changed", "Y");
        result.put("userLoginId", userLoginId == null ? "" : userLoginId);
        // the audit log keeps a line for it, so "who changed a password when" can be answered
        MfgAudit.record(delegator, userLoginId, MfgAudit.PASSWORD_CHANGE, userLoginId, null, "服务");
        return result;
    }

    /**
     * Applies the rules of a new password: at least five characters, typed twice the same way, and
     * different from the current password.
     *
     * @param currentPassword the password in use, may be {@code null}
     * @param newPassword the new password as typed in
     * @param newPasswordVerify the new password typed in a second time
     * @return the message to show, or {@code null} when the new password is acceptable
     */
    static String passwordProblem(String currentPassword, String newPassword, String newPasswordVerify) {
        if (newPassword == null || newPassword.length() < 5) {
            return "新密码至少 5 个字符";
        }
        if (newPasswordVerify == null || !newPassword.equals(newPasswordVerify)) {
            return "两次输入的新密码不一致";
        }
        if (newPassword.equals(currentPassword)) {
            return "新密码不能和当前密码相同";
        }
        return null;
    }

    /**
     * Checks the password of one account against the value the user typed in.
     *
     * @param delegator the delegator
     * @param userLoginId the account
     * @param password the password as typed in
     * @return {@code true} when it matches
     */
    static boolean matchesStoredPassword(Delegator delegator, String userLoginId, String password) {
        try {
            GenericValue stored = EntityQuery.use(delegator).from("UserLogin")
                    .where("userLoginId", userLoginId).queryOne();
            return stored != null && matchesPassword(delegator, stored.getString("currentPassword"), password);
        } catch (GenericEntityException e) {
            return false;
        }
    }

    /**
     * Says whether a stored password hash is the one of the password that ships with OFBiz.
     *
     * @param delegator the delegator
     * @param storedPassword the hash kept on {@code UserLogin}
     * @return {@code true} when the account still uses the default password
     */
    static boolean isDefaultPassword(Delegator delegator, String storedPassword) {
        return matchesPassword(delegator, storedPassword, MfgConstants.DEFAULT_PASSWORD);
    }

    /**
     * Compares a stored password (hash or plain text, depending on the security settings) with a
     * password as typed in.
     *
     * @param delegator the delegator
     * @param storedPassword the stored value
     * @param password the password as typed in
     * @return {@code true} when they match
     */
    static boolean matchesPassword(Delegator delegator, String storedPassword, String password) {
        if (storedPassword == null || password == null) {
            return false;
        }
        boolean useEncryption = "true".equals(
                EntityUtilProperties.getPropertyValue("security", "password.encrypt", delegator));
        return LoginServices.checkPassword(storedPassword, useEncryption, password);
    }

    /**
     * Lists the accounts with the person behind each one, its security group and whether it still
     * uses the default password.
     *
     * <p>Never returns an error: the page is reachable by every logged in user, so a user without
     * the administrator group just gets {@code canManage=N} and a message instead of a form.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgUserList}
     * @return the service result with {@code rows}, {@code groupOptions}, {@code canManage}
     */
    public static Map<String, Object> userList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", rows);
        result.put("groupOptions", groups);
        result.put("canManage", "N");
        result.put("message", "");
        result.put("totalCount", Long.valueOf(0));
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return result;
        }
        try {
            String caller = userLogin.getString("userLoginId");
            String denied = accountAdminProblem(dctx, userLogin);
            result.put("canManage", denied == null ? "Y" : "N");
            result.put("message", denied == null ? "" : denied);
            result.put("currentUserLoginId", caller == null ? "" : caller);
            if (denied != null) {
                // the account names are not shown to somebody who may not manage them
                return result;
            }
            Set<String> shopGroups = shopGroupIds(delegator);
            for (GenericValue group : EntityQuery.use(delegator).from("SecurityGroup").orderBy("groupId").queryList()) {
                String groupId = group.getString("groupId");
                if (!shopGroups.contains(groupId)) {
                    continue;
                }
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("groupId", groupId);
                String description = MfgSupport.toText(group.getString("description"));
                option.put("description", description == null ? MfgSupport.toText(group.getString("groupName")) : description);
                groups.add(option);
            }
            // the administrator group first: that is what almost every new account needs
            Collections.sort(groups, new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    String leftId = String.valueOf(left.get("groupId"));
                    String rightId = String.valueOf(right.get("groupId"));
                    if (MfgConstants.ACCOUNT_ADMIN_GROUP.equals(leftId)
                            != MfgConstants.ACCOUNT_ADMIN_GROUP.equals(rightId)) {
                        return MfgConstants.ACCOUNT_ADMIN_GROUP.equals(leftId) ? -1 : 1;
                    }
                    return leftId.compareTo(rightId);
                }
            });
            for (GenericValue login : EntityQuery.use(delegator).from("UserLogin").orderBy("userLoginId").queryList()) {
                String userLoginId = login.getString("userLoginId");
                if ("Y".equals(login.getString("isSystem"))) {
                    // accounts the framework runs under are not people and cannot log in
                    continue;
                }
                List<String> groupIds = groupIdsOf(delegator, userLoginId);
                if (!belongsToShop(groupIds, shopGroups)) {
                    // a user of some other application of the installation is none of our business
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("userLoginId", userLoginId);
                row.put("partyId", MfgSupport.toText(login.getString("partyId")));
                row.put("personName", personNameOf(delegator, login.getString("partyId")));
                row.put("enabled", "N".equals(login.getString("enabled")) ? "N" : "Y");
                row.put("groups", groupIds.isEmpty() ? "" : String.join(" / ", groupIds));
                row.put("groupIds", groupIds);
                row.put("defaultPassword", isDefaultPassword(delegator, login.getString("currentPassword")) ? "Y" : "N");
                row.put("self", userLoginId != null && userLoginId.equals(caller) ? "Y" : "N");
                row.put("createdDate", dayText(login.getTimestamp("createdStamp")));
                rows.add(row);
            }
            result.put("totalCount", Long.valueOf(rows.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("读取账号失败：" + e.getMessage());
        }
    }

    /**
     * Creates an account (person + user login + security group) or updates the name, the security
     * group and the enabled flag of an existing one. An existing password is never changed here:
     * that is {@link #userResetPassword(DispatchContext, Map)}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgUserSave}
     * @return the service result with {@code userLoginId} and {@code created} ({@code Y} on insert)
     */
    public static Map<String, Object> userSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return ServiceUtil.returnError("请先登录");
        }
        String caller = userLogin.getString("userLoginId");
        String denied = accountAdminProblem(dctx, userLogin);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String userLoginId = normalizeAccountName(context.get("userLoginId"));
        String problem = accountNameProblem(userLoginId);
        if (problem != null) {
            return ServiceUtil.returnError(problem);
        }
        String personName = MfgSupport.toText(context.get("personName"));
        String groupId = MfgSupport.toText(context.get("groupId"));
        String enabled = "N".equalsIgnoreCase(String.valueOf(context.get("enabled"))) ? "N" : "Y";
        try {
            GenericValue existing = findUserLogin(delegator, userLoginId);
            boolean created = existing == null;
            if (created) {
                String password = MfgSupport.toText(context.get("password"));
                if (password == null) {
                    password = MfgConstants.DEFAULT_PASSWORD;
                }
                if (password.length() < 5) {
                    return ServiceUtil.returnError("初始密码至少 5 个字符");
                }
                if (groupId == null) {
                    groupId = MfgConstants.ACCOUNT_ADMIN_GROUP;
                }
                if (!shopGroupIds(delegator).contains(groupId)) {
                    return ServiceUtil.returnError("安全组不属于本系统：" + groupId);
                }
                Map<String, Object> createContext = new HashMap<>();
                createContext.put("userLogin", userLogin);
                createContext.put("userLoginId", userLoginId);
                createContext.put("firstName", personName == null ? userLoginId : personName);
                createContext.put("lastName", "");
                createContext.put("currentPassword", password);
                createContext.put("currentPasswordVerify", password);
                createContext.put("passwordHint", MfgSupport.toText(context.get("passwordHint")));
                createContext.put("enabled", enabled);
                createContext.put("requirePasswordChange", "N");
                Map<String, Object> createdResult =
                        dctx.getDispatcher().runSync("createPersonAndUserLogin", createContext, -1, true);
                if (ServiceUtil.isError(createdResult)) {
                    return copyErrors(createdResult);
                }
                existing = findUserLogin(delegator, userLoginId);
                if (existing == null) {
                    return ServiceUtil.returnError("账号创建失败，请重试");
                }
                replaceSecurityGroup(delegator, existing.getString("userLoginId"), groupId);
            } else {
                String actualId = existing.getString("userLoginId");
                boolean self = actualId != null && actualId.equals(caller);
                if (self && "N".equals(enabled)) {
                    return ServiceUtil.returnError("不能停用当前登录的账号");
                }
                if (self && groupId != null && !groupIdsOf(delegator, actualId).contains(groupId)) {
                    return ServiceUtil.returnError("不能修改当前登录账号自己的安全组，否则可能就管不了账号了");
                }
                if (personName != null) {
                    renamePerson(delegator, existing.getString("partyId"), personName);
                }
                String currentEnabled = "N".equals(existing.getString("enabled")) ? "N" : "Y";
                if (!enabled.equals(currentEnabled) || "Y".equals(enabled)) {
                    // switching an account on also unlocks it: OFBiz disables an account by itself
                    // after a few wrong passwords (security.properties max.failed.logins)
                    existing.set("enabled", enabled);
                    if ("Y".equals(enabled)) {
                        existing.set("disabledDateTime", null);
                        existing.set("disabledBy", null);
                        existing.set("successiveFailedLogins", Long.valueOf(0));
                    } else {
                        existing.set("disabledBy", caller);
                    }
                    existing.store();
                }
                if (groupId != null) {
                    if (!shopGroupIds(delegator).contains(groupId)) {
                        return ServiceUtil.returnError("安全组不属于本系统：" + groupId);
                    }
                    replaceSecurityGroup(delegator, actualId, groupId);
                }
                userLoginId = actualId;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess(created ? "账号已创建" : "账号已更新");
            result.put("account", userLoginId);
            result.put("created", created ? "Y" : "N");
            return result;
        } catch (GenericEntityException | GenericServiceException e) {
            return ServiceUtil.returnError("保存账号失败：" + e.getMessage());
        }
    }

    /**
     * Puts the account back on the password of a fresh database, which makes the password gate
     * force a new one at the next login.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgUserResetPassword}
     * @return the service result with {@code userLoginId}
     */
    public static Map<String, Object> userResetPassword(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return ServiceUtil.returnError("请先登录");
        }
        String denied = accountAdminProblem(dctx, userLogin);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String userLoginId = normalizeAccountName(context.get("userLoginId"));
        if (userLoginId == null) {
            return ServiceUtil.returnError("请选择要重置的账号");
        }
        try {
            GenericValue target = findUserLogin(delegator, userLoginId);
            if (target == null) {
                return ServiceUtil.returnError("账号不存在：" + userLoginId);
            }
            Map<String, Object> updateContext = new HashMap<>();
            updateContext.put("userLogin", userLogin);
            updateContext.put("userLoginId", target.getString("userLoginId"));
            updateContext.put("newPassword", MfgConstants.DEFAULT_PASSWORD);
            updateContext.put("newPasswordVerify", MfgConstants.DEFAULT_PASSWORD);
            updateContext.put("requirePasswordChange", "N");
            Map<String, Object> updated = dctx.getDispatcher().runSync("updatePassword", updateContext, -1, true);
            if (ServiceUtil.isError(updated)) {
                return copyErrors(updated);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess(
                    "密码已重置为出厂密码，该账号下次登录会被要求修改");
            result.put("userLoginId", target.getString("userLoginId"));
            result.put("reset", "Y");
            // the audit log answers "who put whose password back"
            MfgAudit.record(delegator, userLogin.getString("userLoginId"), MfgAudit.PASSWORD_CHANGE,
                    target.getString("userLoginId"), null, "管理员重置密码");
            return result;
        } catch (GenericEntityException | GenericServiceException e) {
            return ServiceUtil.returnError("重置密码失败：" + e.getMessage());
        }
    }

    /**
     * One page of the audit log: who logged in, who was refused, who asked for a password hint and
     * who changed a password, newest first.
     *
     * <p>Never returns an error for somebody who may not read it: the page it belongs to is the
     * account page, which everybody with a login can open.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgAuditList}
     * @return the service result with {@code rows}, {@code options} and the paging metadata
     */
    public static Map<String, Object> auditList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", rows);
        result.put("options", MfgAudit.options());
        result.put("canManage", "N");
        result.put("message", "");
        result.put("totalCount", Integer.valueOf(0));
        result.put("page", Long.valueOf(1));
        result.put("pageCount", Long.valueOf(1));
        result.put("pageSize", Long.valueOf(MfgPaging.pageSizeOf(context)));
        result.put("keepingDays", Long.valueOf(MfgAudit.KEEP_DAYS));
        result.put("lastPurge", "");
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return result;
        }
        String denied = accountAdminProblem(dctx, userLogin);
        result.put("canManage", denied == null ? "Y" : "N");
        result.put("message", denied == null ? "" : denied);
        if (denied != null) {
            return result;
        }
        try {
            GenericValue purgeDay = EntityQuery.use(delegator).from("SystemProperty")
                    .where("systemResourceId", "mfg", "systemPropertyId", "mfg.audit.lastPurge").queryOne();
            if (purgeDay != null) {
                result.put("lastPurge", MfgSupport.toText(purgeDay.getString("systemPropertyValue")) == null
                        ? "" : purgeDay.getString("systemPropertyValue"));
            }
            String search = MfgSupport.toText(context.get("search"));
            String actionId = MfgSupport.toText(context.get("actionId"));
            List<EntityCondition> conditions = new ArrayList<>();
            if (actionId != null) {
                conditions.add(EntityCondition.makeCondition("actionId", EntityOperator.EQUALS, actionId));
            }
            if (search != null) {
                conditions.add(EntityCondition.makeCondition(EntityCondition.makeCondition("userLoginId",
                        EntityOperator.LIKE, "%" + search + "%"), EntityOperator.OR,
                        EntityCondition.makeCondition("targetId", EntityOperator.LIKE, "%" + search + "%")));
            }
            EntityCondition where = conditions.isEmpty() ? null
                    : EntityCondition.makeCondition(conditions, EntityOperator.AND);
            List<GenericValue> found = EntityQuery.use(delegator).from("MfgAuditLog")
                    .where(where).orderBy("-createdStamp", "-auditId").queryList();
            for (GenericValue entry : found) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("auditId", MfgSupport.toText(entry.getString("auditId")));
                row.put("actionId", MfgSupport.toText(entry.getString("actionId")));
                row.put("actionLabel", MfgAudit.label(entry.getString("actionId")));
                row.put("userLoginId", MfgSupport.toText(entry.getString("userLoginId")));
                row.put("targetId", MfgSupport.toText(entry.getString("targetId")));
                row.put("remoteAddr", MfgSupport.toText(entry.getString("remoteAddr")));
                row.put("note", MfgSupport.toText(entry.getString("note")));
                row.put("when", stampText(entry.getTimestamp("createdStamp")));
                rows.add(row);
            }
            MfgPaging.putPage(result, "rows", rows, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), search);
            result.put("actionId", actionId == null ? "" : actionId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("读取操作记录失败：" + e.getMessage());
        }
    }

    /**
     * Drops the audit rows older than a year. The same clean up runs by itself about once a day, so
     * this button is only there for the person who wants to see it happen.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/account-services.xml#mfgAuditPurge}
     * @return the service result with {@code purged}
     */
    public static Map<String, Object> auditPurge(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ADMIN);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        try {
            int removed = MfgAudit.purge(dctx.getDelegator(),
                    MfgAudit.keepCutoff(System.currentTimeMillis(), MfgAudit.KEEP_DAYS));
            Map<String, Object> result = ServiceUtil.returnSuccess(
                    "已删除 " + removed + " 条一年以前的记录");
            // a redirect parameter has to be a String, so the page reads it back as one
            result.put("purged", String.valueOf(removed));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("清理操作记录失败：" + e.getMessage());
        }
    }

    /**
     * Formats a timestamp with the day and the minute, for the audit list.
     *
     * @param value the timestamp, may be {@code null}
     * @return {@code yyyy-MM-dd HH:mm} or an empty string
     */
    static String stampText(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(MfgConstants.STAMP);
    }

    /**
     * Trims an account name and lowers it, so {@code Admin} and {@code admin} cannot become two
     * different accounts.
     *
     * @param raw the value typed in
     * @return the normalized name or {@code null} when it is empty
     */
    static String normalizeAccountName(Object raw) {
        String text = MfgSupport.toText(raw);
        return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    /**
     * Checks an account name against the rules of the page (letters, digits, dot, dash, underscore;
     * at most 20 characters because the field is {@code id-ne}).
     *
     * @param userLoginId the normalized account name
     * @return the message to show, or {@code null} when the name is usable
     */
    static String accountNameProblem(String userLoginId) {
        if (userLoginId == null) {
            return "账号不能为空";
        }
        if (userLoginId.length() > MfgConstants.MAX_ACCOUNT_NAME) {
            return "账号最多 " + MfgConstants.MAX_ACCOUNT_NAME + " 个字符";
        }
        if (!userLoginId.matches("[a-z0-9._-]+")) {
            return "账号只能用字母、数字、点、下划线、短横线";
        }
        return null;
    }

    /**
     * Builds the name shown in the account list from the columns of {@code Person}.
     *
     * @param firstName first name
     * @param middleName middle name
     * @param lastName last name
     * @return the joined name, empty when all parts are empty
     */
    static String displayNameOf(String firstName, String middleName, String lastName) {
        StringBuilder name = new StringBuilder();
        for (String part : new String[] {firstName, middleName, lastName}) {
            String text = MfgSupport.toText(part);
            if (text != null) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(text);
            }
        }
        return name.toString();
    }

    /**
     * Says why the account may not manage other accounts.
     *
     * <p>Uses the same rule OFBiz applies before it lets an account reset the password of another
     * one ({@code PARTYMGR_UPDATE} / {@code PARTYMGR_ADMIN}), so a development database where the
     * administrator sits in {@code FULLADMIN} behaves like a fresh one where it sits in
     * {@code SUPER}.
     *
     * @param dctx the dispatch context
     * @param userLogin the logged in account
     * @return the message to show, or {@code null} when the account may manage accounts
     */
    static String accountAdminProblem(DispatchContext dctx, GenericValue userLogin) {
        if (userLogin == null) {
            return "请先登录";
        }
        if (dctx.getSecurity() != null && dctx.getSecurity().hasEntityPermission("PARTYMGR", "_UPDATE", userLogin)) {
            return null;
        }
        return "只有管理员可以管理账号";
    }

    /**
     * Returns the security groups that belong to this installation of the shop: the groups holding
     * one of the component permissions, the groups that may manage accounts and the administrator
     * group. Accounts of any other group (a web shop, the accounting module, …) are not shown on
     * the account page.
     *
     * @param delegator the delegator
     * @return the group ids
     * @throws GenericEntityException when the permissions cannot be read
     */
    static Set<String> shopGroupIds(Delegator delegator) throws GenericEntityException {
        Set<String> groupIds = new LinkedHashSet<>();
        groupIds.add(MfgConstants.ACCOUNT_ADMIN_GROUP);
        List<Object> permissions = new ArrayList<>();
        permissions.add(MfgConstants.PERM_VIEW);
        permissions.add(MfgConstants.PERM_ADMIN);
        for (String permission : MfgConstants.AREA_PERMISSIONS) {
            permissions.add(permission);
        }
        permissions.add("LEARNINGCURVE_VIEW");
        permissions.add("LEARNINGCURVE_ADMIN");
        // an account that may manage accounts is an administrator of the shop as well
        // (only _ADMIN: a group that may merely create parties belongs to another application)
        permissions.add("PARTYMGR_ADMIN");
        List<GenericValue> grants = EntityQuery.use(delegator).from("SecurityGroupPermission")
                .where(EntityCondition.makeCondition("permissionId", EntityOperator.IN, permissions)).queryList();
        for (GenericValue grant : EntityUtil.filterByDate(grants)) {
            String groupId = grant.getString("groupId");
            if (groupId != null) {
                groupIds.add(groupId);
            }
        }
        return groupIds;
    }

    /**
     * Says whether one of the groups of an account is a group of the shop.
     *
     * @param groupIds the groups of the account
     * @param shopGroups the groups that belong to the shop
     * @return {@code true} when the account should be listed
     */
    static boolean belongsToShop(List<String> groupIds, Set<String> shopGroups) {
        for (String groupId : groupIds) {
            if (shopGroups.contains(groupId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the security groups of an account that are valid today.
     *
     * @param delegator the delegator
     * @param userLoginId the account
     * @return the group ids, possibly empty
     * @throws GenericEntityException when the memberships cannot be read
     */
    static List<String> groupIdsOf(Delegator delegator, String userLoginId) throws GenericEntityException {
        List<String> groupIds = new ArrayList<>();
        if (userLoginId == null) {
            return groupIds;
        }
        List<GenericValue> memberships = EntityQuery.use(delegator).from("UserLoginSecurityGroup")
                .where("userLoginId", userLoginId).orderBy("groupId").queryList();
        for (GenericValue membership : EntityUtil.filterByDate(memberships)) {
            groupIds.add(membership.getString("groupId"));
        }
        return groupIds;
    }

    /**
     * Finds an account ignoring the case of the name, the way OFBiz checks for duplicates.
     *
     * @param delegator the delegator
     * @param userLoginId the account name
     * @return the row or {@code null}
     * @throws GenericEntityException when the accounts cannot be read
     */
    static GenericValue findUserLogin(Delegator delegator, String userLoginId) throws GenericEntityException {
        if (userLoginId == null) {
            return null;
        }
        for (GenericValue login : EntityQuery.use(delegator).from("UserLogin").queryList()) {
            String existing = login.getString("userLoginId");
            if (existing != null && existing.equalsIgnoreCase(userLoginId)) {
                return login;
            }
        }
        return null;
    }

    /**
     * Makes {@code groupId} the only active security group of the account, so the page has a single
     * group picker instead of a matrix of memberships.
     *
     * @param delegator the delegator
     * @param userLoginId the account
     * @param groupId the group to keep
     * @throws GenericEntityException when the memberships cannot be written
     */
    static void replaceSecurityGroup(Delegator delegator, String userLoginId, String groupId)
            throws GenericEntityException {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        boolean kept = false;
        for (GenericValue membership : EntityQuery.use(delegator).from("UserLoginSecurityGroup")
                .where("userLoginId", userLoginId).queryList()) {
            String existingGroup = membership.getString("groupId");
            Timestamp thruDate = membership.getTimestamp("thruDate");
            boolean active = thruDate == null || thruDate.after(now);
            if (groupId.equals(existingGroup)) {
                if (!active) {
                    membership.set("thruDate", null);
                    membership.store();
                }
                kept = true;
            } else if (active) {
                membership.set("thruDate", now);
                membership.store();
            }
        }
        if (!kept) {
            Map<String, Object> values = new HashMap<>();
            values.put("userLoginId", userLoginId);
            values.put("groupId", groupId);
            values.put("fromDate", now);
            delegator.create("UserLoginSecurityGroup", values);
        }
    }

    /**
     * Renames the person behind an account.
     *
     * @param delegator the delegator
     * @param partyId the party of the account, may be empty
     * @param personName the new name
     * @throws GenericEntityException when the person cannot be written
     */
    static void renamePerson(Delegator delegator, String partyId, String personName)
            throws GenericEntityException {
        if (partyId == null) {
            return;
        }
        GenericValue person = EntityQuery.use(delegator).from("Person").where("partyId", partyId).queryOne();
        if (person == null) {
            return;
        }
        if (!personName.equals(displayNameOf(person.getString("firstName"), person.getString("middleName"),
                person.getString("lastName")))) {
            person.set("firstName", personName);
            person.set("middleName", null);
            person.set("lastName", "");
            person.store();
        }
    }

    /**
     * Returns the name of the person behind an account, falling back to the party id.
     *
     * @param delegator the delegator
     * @param partyId the party of the account, may be empty
     * @return the name, never {@code null}
     * @throws GenericEntityException when the person cannot be read
     */
    static String personNameOf(Delegator delegator, String partyId) throws GenericEntityException {
        if (partyId == null) {
            return "";
        }
        GenericValue person = EntityQuery.use(delegator).from("Person").where("partyId", partyId).queryOne();
        if (person == null) {
            return partyId;
        }
        String name = displayNameOf(person.getString("firstName"), person.getString("middleName"),
                person.getString("lastName"));
        return name.isEmpty() ? partyId : name;
    }

    /**
     * Copies the messages of a failed service call into a new error result.
     *
     * @param failed the result of the failed call
     * @return the error result to hand back
     */
    static Map<String, Object> copyErrors(Map<String, Object> failed) {
        Object errors = failed.get("errorMessageList");
        if (errors instanceof List) {
            List<String> messages = new ArrayList<>();
            for (Object error : (List<?>) errors) {
                messages.add(String.valueOf(error));
            }
            return ServiceUtil.returnError(messages);
        }
        return ServiceUtil.returnError(ServiceUtil.getErrorMessage(failed));
    }

    /**
     * Formats the day part of a timestamp for the account list.
     *
     * @param value the timestamp, may be {@code null}
     * @return {@code yyyy-MM-dd} or an empty string
     */
    static String dayText(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(MfgConstants.FULL_DAY);
    }
}
