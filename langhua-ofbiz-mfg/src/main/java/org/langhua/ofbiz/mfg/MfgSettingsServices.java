package org.langhua.ofbiz.mfg;

import org.langhua.ofbiz.mfg.oir.OirOrders;

import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * The settings the shop changes on the pages instead of in a start script.
 *
 * <p>They are rows of {@code SystemProperty} under the resource id {@code mfg}, read by
 * {@link MfgServices#setting}. The key of the vision service is only ever written here and never
 * read back to a page ({@code visionKeySet} says whether one is stored).
 *
 * <p>Moved out of {@code MfgServices} in Step 166.
 */
public final class MfgSettingsServices {

    private MfgSettingsServices() {
    }

    /**
     * Writes or removes one setting.
     *
     * @param delegator the delegator
     * @param id the property id
     * @param value the value, {@code null} to remove the row
     * @throws GenericEntityException when the setting cannot be written
     */
    static void storeSetting(Delegator delegator, String id, String value) throws GenericEntityException {
        GenericValue row = EntityQuery.use(delegator).from("SystemProperty")
                .where("systemResourceId", MfgServices.SETTINGS_RESOURCE, "systemPropertyId", id).queryOne();
        if (value == null) {
            if (row != null) {
                row.remove();
            }
            return;
        }
        if (row == null) {
            row = delegator.makeValue("SystemProperty");
            row.set("systemResourceId", MfgServices.SETTINGS_RESOURCE);
            row.set("systemPropertyId", id);
            row.set("description", "MFG：在「系统设置」页维护");
            delegator.create(row);
        }
        row.set("systemPropertyValue", value);
        row.store();
    }

    /**
     * The vision settings as the settings page shows them: the key is never sent back, only whether
     * one is set.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSettingsView}
     * @return the service result containing the endpoints and the state
     */
    public static Map<String, Object> settingsView(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String denied = MfgAccountServices.accountAdminProblem(dctx, (GenericValue) context.get("userLogin"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("visionEndpoint", "");
        result.put("visionModel", "");
        result.put("visionKeySet", "N");
        result.put("visionConfigured", "N");
        result.put("visionSource", "NONE");
        result.put("visionMessage", denied == null ? "" : denied);
        result.put("canManage", denied == null ? "Y" : "N");
        String endpoint = MfgServices.setting(delegator, "org.langhua.mfg.vision.endpoint");
        String model = MfgServices.setting(delegator, "org.langhua.mfg.vision.model");
        String key = MfgServices.setting(delegator, "org.langhua.mfg.vision.key");
        result.put("visionEndpoint", endpoint == null ? "" : endpoint);
        result.put("visionModel", model == null ? "" : model);
        result.put("visionKeySet", key == null ? "N" : "Y");
        result.put("visionConfigured", endpoint != null && model != null && key != null ? "Y" : "N");
        result.put("visionSource", settingsInDatabase(delegator) ? "DATABASE" : "JVM");
        return result;
    }

    /**
     * Saves the vision settings, or clears them when {@code clear} is {@code Y}. An empty key keeps
     * the one that is already stored, so the page never has to show it again.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/services.xml#mfgSettingsSave}
     * @return the service result
     */
    public static Map<String, Object> settingsSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String denied = MfgAccountServices.accountAdminProblem(dctx, (GenericValue) context.get("userLogin"));
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        try {
            if ("Y".equals(MfgSupport.toText(context.get("clear")))) {
                storeSetting(delegator, "org.langhua.mfg.vision.endpoint", null);
                storeSetting(delegator, "org.langhua.mfg.vision.model", null);
                storeSetting(delegator, "org.langhua.mfg.vision.key", null);
                return ServiceUtil.returnSuccess();
            }
            String endpoint = MfgSupport.toText(context.get("visionEndpoint"));
            if (endpoint != null && !endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                return ServiceUtil.returnError("识图接口要以 http:// 或 https:// 开头：" + endpoint);
            }
            storeSetting(delegator, "org.langhua.mfg.vision.endpoint", OirOrders.normalizeVisionEndpoint(endpoint));
            storeSetting(delegator, "org.langhua.mfg.vision.model", MfgSupport.toText(context.get("visionModel")));
            String key = MfgSupport.toText(context.get("visionKey"));
            if (key != null) {
                storeSetting(delegator, "org.langhua.mfg.vision.key", key);
            }
            return ServiceUtil.returnSuccess();
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the settings: " + e.getMessage());
        }
    }

    static boolean settingsInDatabase(Delegator delegator) {
        try {
            return EntityQuery.use(delegator).from("SystemProperty")
                    .where("systemResourceId", MfgServices.SETTINGS_RESOURCE).queryFirst() != null;
        } catch (GenericEntityException e) {
            return false;
        }
    }
}
