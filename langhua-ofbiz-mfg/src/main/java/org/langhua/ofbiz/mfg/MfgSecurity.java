package org.langhua.ofbiz.mfg;

import java.util.Map;

import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.security.Security;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Who may do what: the service the decorator asks for the navigation, the check every writing
 * service starts with, and the one place that decides that {@code MFG_ADMIN} and the OFBiz
 * administrator permission pass every check.
 *
 * <p>The permissions themselves ({@code MFG_VIEW}, {@code MFG_PRODUCTION}, ...) are in
 * {@link MfgConstants}; this class only answers questions about them (Step 172).
 */
public final class MfgSecurity {

    private MfgSecurity() {
    }

    /**
     * Tells the decorator which areas the logged in account may work in, so the navigation and the
     * cost blocks are hidden instead of failing with a permission error. Never fails.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/security-services.xml#mfgPermissions}
     * @return the service result with {@code canView} / {@code canProduce} / {@code canStock} /
     *         {@code canEng} / {@code canSales} / {@code canCost} / {@code isAdmin} ({@code Y} / {@code N})
     */
    public static Map<String, Object> permissions(DispatchContext dctx, Map<String, ? extends Object> context) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("canView", yesNo(can(dctx, userLogin, MfgConstants.PERM_VIEW)));
        result.put("canProduce", yesNo(can(dctx, userLogin, MfgConstants.PERM_PRODUCTION)));
        result.put("canStock", yesNo(can(dctx, userLogin, MfgConstants.PERM_STOCK)));
        result.put("canEng", yesNo(can(dctx, userLogin, MfgConstants.PERM_ENG)));
        result.put("canSales", yesNo(can(dctx, userLogin, MfgConstants.PERM_SALES)));
        result.put("canCost", yesNo(can(dctx, userLogin, MfgConstants.PERM_COST)));
        result.put("isAdmin", yesNo(can(dctx, userLogin, MfgConstants.PERM_ADMIN)));
        result.put("manager", yesNo(MfgAccountServices.accountAdminProblem(dctx, userLogin) == null));
        result.put("userLoginId", userLogin == null ? "" : userLogin.getString("userLoginId"));
        return result;
    }

    /**
     * Says whether the account holds one permission. {@code MFG_ADMIN} and the OFBiz administrator
     * permission pass every check, so a database whose administrator sits in {@code FULLADMIN}
     * (a demo database) behaves like a fresh one where it sits in {@code SUPER}.
     *
     * @param dctx the dispatch context
     * @param userLogin the logged in account, may be {@code null}
     * @param permission the permission to look for
     * @return {@code true} when the account may do it
     */
    static boolean can(DispatchContext dctx, GenericValue userLogin, String permission) {
        if (userLogin == null) {
            return false;
        }
        Security security = dctx.getSecurity();
        if (security == null) {
            // no security implementation configured: never lock the shop out of its own data
            return true;
        }
        if (security.hasPermission(permission, userLogin) || security.hasPermission(MfgConstants.PERM_ADMIN, userLogin)) {
            return true;
        }
        return security.hasEntityPermission("PARTYMGR", "_UPDATE", userLogin);
    }

    /**
     * Checks the area permissions of an operation.
     *
     * @param dctx the dispatch context
     * @param context service context, must carry the logged in {@code userLogin}
     * @param permissions the permissions of which the account needs at least one
     * @return the message to show, or {@code null} when the account may do it
     */
    static String permissionProblem(DispatchContext dctx, Map<String, ? extends Object> context,
            String... permissions) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (userLogin == null) {
            return "请先登录";
        }
        for (String permission : permissions) {
            if (can(dctx, userLogin, permission)) {
                return null;
            }
        }
        return "没有权限做这个操作（需要 " + String.join(" 或 ", permissions)
                + "）；请管理员在「用户账号」里调整你的角色";
    }

    /**
     * Turns a flag into the {@code Y} / {@code N} the templates compare against.
     *
     * @param value the flag
     * @return {@code Y} when true, {@code N} otherwise
     */
    private static String yesNo(boolean value) {
        return value ? "Y" : "N";
    }
}
