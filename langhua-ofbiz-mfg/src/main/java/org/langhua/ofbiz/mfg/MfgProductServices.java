package org.langhua.ofbiz.mfg;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Product models and their engineering data: the BOM with its versions, the routing, the
 * replacement parts and what one unit costs to build.
 *
 * <p>A BOM is versioned ({@code MfgBomVersion} plus {@code MfgBomVersionItem}) and the model points
 * at the version in use, so a work order can freeze the version it was started with. The first
 * version of a model is released straight away, otherwise the product page would still say "no BOM"
 * after an import.
 *
 * <p>The cost of a model is the BOM material plus the standard hours at the labour rate; the same
 * arithmetic ({@code costSummary}) is used for the work order cost, which is why it lives here
 * rather than in the work order services. Moved out of {@code MfgServices} in Step 167.
 */
public final class MfgProductServices {

    private MfgProductServices() {
    }

    /**
     * Says "you may not see the cost" without failing the page.
     *
     * @param dctx the dispatch context
     * @param context service context, must carry the logged in {@code userLogin}
     * @return a result carrying {@code denied=Y}, or {@code null} when the account may see cost
     */
    static Map<String, Object> costDenied(DispatchContext dctx, Map<String, ? extends Object> context) {
        GenericValue userLogin = (GenericValue) context.get("userLogin");
        if (MfgSecurity.can(dctx, userLogin, MfgConstants.PERM_COST)) {
            return null;
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("denied", "Y");
        result.put("message", "无权查看成本与毛利（需要 MFG_COST）；找管理员在「用户账号」里给你加角色");
        return result;
    }

    /**
     * Creates or updates a product model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgProductSave}
     * @return the service result containing {@code productCode}
     */
    public static Map<String, Object> productSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        try {
            GenericValue product = delegator.makeValue("MfgProduct");
            product.set("productCode", productCode);
            product.set("productName", MfgSupport.toText(context.get("productName")));
            String snPrefix = MfgSupport.toText(context.get("snPrefix"));
            if (snPrefix == null) {
                snPrefix = productCode;
            }
            // snPrefix is a very-short field (10 characters): without this guard Derby refuses the
            // row and the page only shows a database error
            if (snPrefix.length() > 10) {
                return ServiceUtil.returnError("SN 前缀最长 10 个字符（现在是 \"" + snPrefix
                        + "\"）：不带前缀时它默认取型号编码，型号编码超过 10 位就要自己给一个短前缀");
            }
            product.set("snPrefix", snPrefix);
            double newHours = MfgSupport.toDouble(context.get("standardHours"), 0.0);
            product.set("standardHours", Double.valueOf(newHours));
            // hours changed by hand come from the shop floor again, not from a learning curve;
            // the base quantity and the estimate reference are left as they are, the page only
            // shows them while the source still says LEARNING_CURVE
            GenericValue existing = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            double storedHours = existing == null ? 0.0 : MfgSupport.toDouble(existing.get("standardHours"), 0.0);
            if (existing == null || Math.abs(storedHours - newHours) > 0.000001) {
                product.set("standardHoursSource", MfgConstants.HOURS_SOURCE_MANUAL);
            }
            product.set("laborRate", MfgSupport.optionalAmount(context.get("laborRate")));
            long warrantyMonths = MfgSupport.toLong(context.get("warrantyMonths"), 12);
            product.set("warrantyMonths", Long.valueOf(warrantyMonths <= 0 ? 12 : warrantyMonths));
            product.set("salePrice", Double.valueOf(MfgSupport.toDouble(context.get("salePrice"), 0.0)));
            String statusId = MfgSupport.toText(context.get("statusId"));
            product.set("statusId", statusId == null ? MfgConstants.STATUS_ACTIVE : statusId);
            String devStageId = MfgSupport.toText(context.get("devStageId"));
            if (devStageId != null) {
                product.set("devStageId", devStageId);
            }
            product.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(product);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the product model: " + e.getMessage());
        }
    }

    /**
     * Imports BOM lines from CSV text into one BOM version. Each line is
     * {@code componentCode,componentName,qty,designators,isKeyPart}. When {@code replace} is
     * {@code Y} the existing lines of that version are removed first.
     *
     * <p>The version is the one given in {@code bomVersionId}, otherwise the version the model
     * currently uses, otherwise a fresh version 1 that is created on the fly.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomImport}
     * @return the service result containing {@code bomVersionId} and {@code importedCount}
     */
    public static Map<String, Object> bomImport(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        String csv = MfgSupport.toText(context.get("csv"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (csv == null) {
            return ServiceUtil.returnError("csv is required");
        }
        String requestedFormat = MfgSupport.toText(context.get("format"));
        MfgCsv.BomFormat forced = MfgCsv.BomFormat.of(requestedFormat);
        if (requestedFormat != null && forced == null) {
            return ServiceUtil.returnError("Unknown BOM format [" + requestedFormat
                    + "], expected MFG, FRITZING or FRITZING_HTML");
        }
        MfgCsv.BomFormat format = forced == null ? MfgCsv.detectBomFormat(csv) : forced;
        List<String[]> rows = MfgCsv.parseBom(csv, format);
        if (rows.isEmpty()) {
            return ServiceUtil.returnError("No BOM line could be parsed, expected componentCode,componentName,qty,designators,isKeyPart");
        }
        for (String[] row : rows) {
            if (row[0] == null || row[0].isEmpty()) {
                return ServiceUtil.returnError("一行没有料号，导入中止");
            }
            if (row[0].length() > MfgConstants.MAX_COMPONENT_CODE) {
                return ServiceUtil.returnError("料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE + " 个字符）：" + row[0]
                        + "；请在源文件里改用物料号，或改成短料号（例如 " + MfgProductServices.fittingComponentCode(row[0]) + "）");
            }
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String bomVersionId = MfgSupport.toText(context.get("bomVersionId"));
            if (bomVersionId != null) {
                if (EntityQuery.use(delegator).from("MfgBomVersion")
                        .where("bomVersionId", bomVersionId).queryOne() == null) {
                    return ServiceUtil.returnError("Unknown BOM version [" + bomVersionId + "]");
                }
            } else {
                bomVersionId = currentOrNewBomVersion(delegator, productCode, "Imported BOM");
            }
            if (!"N".equals(MfgSupport.toText(context.get("replace")))) {
                delegator.removeByAnd("MfgBomVersionItem", "bomVersionId", bomVersionId);
            }
            long lineSeq = nextBomLineSeq(delegator, bomVersionId);
            int imported = 0;
            for (String[] row : rows) {
                GenericValue item = delegator.makeValue("MfgBomVersionItem");
                item.set("bomVersionId", bomVersionId);
                item.set("lineSeq", Long.valueOf(lineSeq));
                item.set("componentCode", row[0]);
                item.set("componentName", row[1] == null ? row[0] : row[1]);
                item.set("qty", Double.valueOf(MfgSupport.toDouble(row[2], 1.0)));
                item.set("designators", row[3]);
                item.set("isKeyPart", "Y".equalsIgnoreCase(row[4]) ? "Y" : "N");
                item.set("unitPrice", MfgSupport.optionalAmount(row[5]));
                delegator.create(item);
                lineSeq += 10;
                imported++;
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            result.put("bomVersionId", bomVersionId);
            // String, so that the value can be carried by the redirect to the product page
            result.put("importedCount", String.valueOf(imported));
            result.put("bomFormat", format.name());
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError(bomImportFailure(e));
        }
    }

    /**
     * Turns the database error of an import into a message the shop can act on.
     *
     * @param e the error of the import
     * @return the message to show on the product page
     */
    static String bomImportFailure(GenericEntityException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("truncation error")) {
            return "导入失败：有字段超出长度限制（型号编码最多 20 个字符，料号最多 100 个字符），请缩短后再试";
        }
        return "Could not import the BOM: " + message;
    }

    /**
     * Saves one BOM line: appends a new line when no line number is given, updates the line with
     * that number otherwise, or removes it when {@code delete} is {@code Y}. Quantity, designators
     * and unit price are what a BOM gets wrong first, so they can be corrected here instead of
     * importing the whole file again.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomItemSave}
     * @return the service result containing {@code bomVersionId} and {@code lineSeq}
     */
    public static Map<String, Object> bomItemSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        boolean removing = "Y".equals(MfgSupport.toText(context.get("delete")));
        long lineSeq = MfgSupport.toLong(context.get("lineSeq"), 0L);
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String versionId = MfgSupport.toText(context.get("bomVersionId"));
            if (versionId != null && EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", versionId).queryOne() == null) {
                versionId = null;
            }
            if (versionId == null) {
                // a line sent without a version edits the one in use
                versionId = currentBomVersionId(delegator, productCode);
            }
            GenericValue item = null;
            if (versionId != null && lineSeq > 0L) {
                item = EntityQuery.use(delegator).from("MfgBomVersionItem")
                        .where("bomVersionId", versionId, "lineSeq", Long.valueOf(lineSeq)).queryOne();
            }
            if (removing) {
                if (item == null) {
                    return ServiceUtil.returnError("要删除的 BOM 行不在当前版本里，刷新页面后再试");
                }
                item.remove();
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("bomVersionId", versionId);
                result.put("lineSeq", String.valueOf(lineSeq));
                return result;
            }
            if (versionId == null) {
                versionId = currentOrNewBomVersion(delegator, productCode, "BOM maintained by hand");
            }
            String componentCode = MfgSupport.toText(context.get("componentCode"));
            boolean created = item == null;
            if (created) {
                if (componentCode == null) {
                    return ServiceUtil.returnError("料号不能为空");
                }
                if (componentCode.length() > MfgConstants.MAX_COMPONENT_CODE) {
                    return ServiceUtil.returnError("料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE + " 个字符）：" + componentCode
                            + "，可以改成 " + MfgProductServices.fittingComponentCode(componentCode));
                }
                lineSeq = nextBomLineSeq(delegator, versionId);
                item = delegator.makeValue("MfgBomVersionItem");
                item.set("bomVersionId", versionId);
                item.set("lineSeq", Long.valueOf(lineSeq));
                item.set("componentCode", componentCode);
                String name = MfgSupport.toText(context.get("partName"));
                item.set("componentName", name == null ? componentCode : name);
                item.set("isKeyPart", "N");
            } else if (componentCode != null && componentCode.length() <= MfgConstants.MAX_COMPONENT_CODE) {
                item.set("componentCode", componentCode);
                item.set("componentName", MfgSupport.toText(context.get("partName")) == null
                        ? componentCode : MfgSupport.toText(context.get("partName")));
            }
            double qty = MfgSupport.toDouble(context.get("qty"), created ? 1.0 : MfgSupport.toDouble(item.get("qty"), 1.0));
            if (qty <= 0.0) {
                return ServiceUtil.returnError("用量要大于 0");
            }
            String keyPart = MfgSupport.toText(context.get("isKeyPart"));
            if (keyPart != null) {
                item.set("isKeyPart", "Y".equalsIgnoreCase(keyPart) ? "Y" : "N");
            }
            item.set("qty", Double.valueOf(qty));
            item.set("designators", MfgSupport.toText(context.get("designators")));
            // an empty price box clears the price, a missing one leaves it alone
            if (context.containsKey("unitPrice")) {
                String priceText = MfgSupport.toText(context.get("unitPrice"));
                Double price = MfgSupport.optionalAmount(priceText);
                if (priceText != null && price == null) {
                    return ServiceUtil.returnError("单价要填非负数字，或留空：" + priceText);
                }
                item.set("unitPrice", price);
            }
            if (created) {
                delegator.create(item);
            } else {
                item.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("bomVersionId", versionId);
            result.put("lineSeq", String.valueOf(lineSeq));
            return result;
        } catch (GenericEntityException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("truncation error")) {
                return ServiceUtil.returnError("保存失败：有字段超出长度限制（料号最多 100 个字符），请缩短后再试");
            }
            return ServiceUtil.returnError("Could not save the BOM line: " + message);
        }
    }

    /**
     * Creates or updates one routing operation of a product model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgRoutingSave}
     * @return the service result containing {@code productCode} and {@code opSeq}
     */
    public static Map<String, Object> routingSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        long opSeq = MfgSupport.toLong(context.get("opSeq"), 0);
        String opName = MfgSupport.toText(context.get("opName"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        if (opSeq <= 0) {
            return ServiceUtil.returnError("opSeq must be a positive number");
        }
        if (opName == null) {
            return ServiceUtil.returnError("opName is required");
        }
        try {
            GenericValue operation = delegator.makeValue("MfgRouting");
            operation.set("productCode", productCode);
            operation.set("opSeq", Long.valueOf(opSeq));
            operation.set("opName", opName);
            operation.set("workCenter", MfgSupport.toText(context.get("workCenter")));
            operation.set("stdHours", Double.valueOf(MfgSupport.toDouble(context.get("stdHours"), 0.0)));
            operation.set("note", MfgSupport.toText(context.get("note")));
            delegator.createOrStore(operation);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("productCode", productCode);
            result.put("opSeq", Long.valueOf(opSeq));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the routing operation: " + e.getMessage());
        }
    }

    /**
     * Costs one model: BOM lines times their unit price plus the standard hours times the labour
     * rate, then compares the result with the target cost and the target price of the development
     * project of that model.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgProductCost}
     * @return the service result containing the costs, the lines and the margin
     */
    public static Map<String, Object> productCost(DispatchContext dctx, Map<String, ? extends Object> context) {
        // the page cannot fail for a role without the cost area: it renders a hint instead
        Map<String, Object> denied = costDenied(dctx, context);
        if (denied != null) {
            return denied;
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        Map<String, Object> empty = costSummary(0.0, 0.0, 1.0, null, null);
        empty.put("lines", new ArrayList<Map<String, Object>>());
        empty.put("productCode", productCode);
        if (productCode == null) {
            return empty;
        }
        try {
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            if (product == null) {
                empty.put("message", "Unknown product model [" + productCode + "]");
                return empty;
            }
            double qty = MfgSupport.toDouble(context.get("qty"), 1.0);
            if (qty <= 0.0) {
                qty = 1.0;
            }
            String versionId = MfgSupport.toText(context.get("bomVersionId"));
            if (versionId == null) {
                versionId = currentBomVersionId(delegator, productCode);
            }
            List<GenericValue> bomItems = versionId == null ? new ArrayList<GenericValue>()
                    : bomItemsOfVersion(delegator, versionId);

            List<Map<String, Object>> lines = new ArrayList<>(bomItems.size());
            double materialPerUnit = 0.0;
            int missing = 0;
            for (GenericValue item : bomItems) {
                double lineQty = item.getDouble("qty") == null ? 0.0 : item.getDouble("qty").doubleValue();
                Object price = item.get("unitPrice");
                boolean hasPrice = price instanceof Number;
                if (!hasPrice) {
                    missing++;
                }
                double unitPrice = hasPrice ? ((Number) price).doubleValue() : 0.0;
                double amount = lineQty * unitPrice;
                materialPerUnit += amount;
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("componentCode", item.getString("componentCode"));
                line.put("componentName", item.getString("componentName"));
                line.put("qty", Double.valueOf(lineQty));
                line.put("unitPrice", hasPrice ? Double.valueOf(unitPrice) : null);
                line.put("amount", Double.valueOf(amount));
                line.put("hasPrice", Boolean.valueOf(hasPrice));
                lines.add(line);
            }

            Object rateInput = context.get("laborRate");
            double laborRate = rateInput == null
                    ? (product.get("laborRate") instanceof Number
                            ? ((Number) product.get("laborRate")).doubleValue() : 0.0)
                    : MfgSupport.toDouble(rateInput, 0.0);
            double productHours = product.getDouble("standardHours") == null
                    ? 0.0 : product.getDouble("standardHours").doubleValue();

            // labour hours: the routing of the model when it has one, else the hours on the product
            List<GenericValue> routing = routingOf(delegator, productCode);
            double routingHours = 0.0;
            List<Map<String, Object>> routingLines = new ArrayList<>(routing.size());
            for (GenericValue operation : routing) {
                Double operationHours = operation.getDouble("stdHours");
                double hours = operationHours == null ? 0.0 : operationHours.doubleValue();
                routingHours += hours;
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("opSeq", operation.getLong("opSeq"));
                line.put("opName", operation.getString("opName"));
                line.put("workCenter", operation.getString("workCenter"));
                line.put("stdHours", Double.valueOf(hours));
                line.put("amount", Double.valueOf(MfgSupport.round2(hours * laborRate)));
                routingLines.add(line);
            }
            String hoursSource = routing.isEmpty() ? "PRODUCT" : "ROUTING";
            double standardHours = routing.isEmpty() ? productHours : routingHours;
            double laborPerUnit = standardHours * laborRate;

            // one model may have several development projects: take the newest target
            GenericValue project = EntityQuery.use(delegator).from("MfgDevProject")
                    .where("productCode", productCode).orderBy("-createdStamp").queryFirst();
            Double targetCost = project == null || !(project.get("targetCost") instanceof Number)
                    ? null : Double.valueOf(((Number) project.get("targetCost")).doubleValue());
            Double targetPrice = project == null || !(project.get("targetPrice") instanceof Number)
                    ? null : Double.valueOf(((Number) project.get("targetPrice")).doubleValue());

            Map<String, Object> result = costSummary(materialPerUnit, laborPerUnit, qty, targetCost, targetPrice);
            result.put("productCode", productCode);
            result.put("bomVersionId", versionId);
            result.put("standardHours", Double.valueOf(standardHours));
            result.put("productStandardHours", Double.valueOf(productHours));
            result.put("routingHours", Double.valueOf(routingHours));
            result.put("hoursSource", hoursSource);
            result.put("routingLines", routingLines);
            result.put("laborRate", Double.valueOf(laborRate));
            result.put("lineCount", Integer.valueOf(lines.size()));
            result.put("missingPriceCount", Integer.valueOf(missing));
            result.put("lines", lines);
            result.put("salePrice", product.get("salePrice"));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not cost the product model: " + e.getMessage());
        }
    }

    /**
     * Totals the material and labour of one quantity and compares them with the target cost and the
     * target price.
     *
     * <p>Pure so the rounding and the division by zero can be tested without a database.
     *
     * @param materialPerUnit material cost of one unit
     * @param laborPerUnit labour cost of one unit
     * @param qty how many units to cost, at least zero
     * @param targetCost the target cost of one unit, may be {@code null}
     * @param targetPrice the target selling price of one unit, may be {@code null}
     * @return the totals, the unit cost and the margin (all rounded to two decimals)
     */
    static Map<String, Object> costSummary(double materialPerUnit, double laborPerUnit, double qty,
            Double targetCost, Double targetPrice) {
        double unit = materialPerUnit + laborPerUnit;
        Map<String, Object> map = ServiceUtil.returnSuccess();
        map.put("qty", Double.valueOf(qty));
        map.put("materialPerUnit", Double.valueOf(MfgSupport.round2(materialPerUnit)));
        map.put("laborPerUnit", Double.valueOf(MfgSupport.round2(laborPerUnit)));
        map.put("unitCost", Double.valueOf(MfgSupport.round2(unit)));
        map.put("materialCost", Double.valueOf(MfgSupport.round2(materialPerUnit * qty)));
        map.put("laborCost", Double.valueOf(MfgSupport.round2(laborPerUnit * qty)));
        map.put("totalCost", Double.valueOf(MfgSupport.round2(unit * qty)));
        map.put("targetCost", targetCost);
        map.put("targetPrice", targetPrice);
        map.put("targetDifference", targetCost == null
                ? null : Double.valueOf(MfgSupport.round2(unit - targetCost.doubleValue())));
        if (targetPrice == null || targetPrice.doubleValue() <= 0.0) {
            map.put("unitMargin", null);
            map.put("marginRate", null);
        } else {
            double margin = targetPrice.doubleValue() - unit;
            map.put("unitMargin", Double.valueOf(MfgSupport.round2(margin)));
            map.put("marginRate", Double.valueOf(MfgSupport.round2(margin / targetPrice.doubleValue() * 100.0)));
        }
        return map;
    }

    /**
     * Reads the operations of a model in sequence order.
     *
     * @param delegator the delegator
     * @param productCode the model, may be {@code null}
     * @return the operations, empty when the model has no routing
     * @throws GenericEntityException when the routing cannot be read
     */
    static List<GenericValue> routingOf(Delegator delegator, String productCode)
            throws GenericEntityException {
        if (productCode == null) {
            return new ArrayList<GenericValue>();
        }
        return EntityQuery.use(delegator).from("MfgRouting")
                .where("productCode", productCode).orderBy("opSeq").queryList();
    }

    /**
     * Renames a part number everywhere it is stored: the lots, the issues, the receipt lines, the
     * BOM lines of every version, the replaced parts of a repair and the stock setting of that part.
     * A code built from the name of an article is only a guess, so it has to be correctable without
     * breaking the traceability.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgPartCodeRename}
     * @return the service result containing the counts, {@code search} and {@code renamed}
     */
    public static Map<String, Object> partCodeRename(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_STOCK);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String oldCode = MfgSupport.toText(context.get("oldCode"));
        String newCode = MfgSupport.toText(context.get("newCode"));
        if (oldCode == null || newCode == null) {
            return ServiceUtil.returnError("旧料号与新料号都要填");
        }
        if (oldCode.equals(newCode)) {
            return ServiceUtil.returnError("新旧料号一样，没有可改的");
        }
        if (newCode.length() > MfgConstants.MAX_COMPONENT_CODE) {
            return ServiceUtil.returnError("新料号太长（最多 " + MfgConstants.MAX_COMPONENT_CODE + " 个字符）：" + newCode);
        }
        try {
            int lots = renameWhere(delegator, "MfgMaterialLot", oldCode, newCode);
            int issues = renameWhere(delegator, "MfgMaterialIssue", oldCode, newCode);
            int receiptLines = renameWhere(delegator, "MfgPurchaseReceiptLine", oldCode, newCode);
            int bomLines = renameWhere(delegator, "MfgBomItem", oldCode, newCode)
                    + renameWhere(delegator, "MfgBomVersionItem", oldCode, newCode);
            int replacements = renameWhere(delegator, "MfgReplacement", oldCode, newCode);
            // the stock setting keeps the code as its key, so it is copied when the new one is free
            boolean setting = false;
            GenericValue oldSetting = EntityQuery.use(delegator).from("MfgStockSetting")
                    .where("componentCode", oldCode).queryFirst();
            if (oldSetting != null) {
                GenericValue newSetting = EntityQuery.use(delegator).from("MfgStockSetting")
                        .where("componentCode", newCode).queryFirst();
                if (newSetting == null) {
                    GenericValue copy = delegator.makeValue("MfgStockSetting");
                    copy.setNonPKFields(oldSetting.getAllFields());
                    copy.set("componentCode", newCode);
                    delegator.create(copy);
                }
                oldSetting.remove();
                setting = true;
            }
            int total = lots + issues + receiptLines + bomLines + replacements;
            Map<String, Object> result = ServiceUtil.returnSuccess();
            if (total == 0 && !setting) {
                return ServiceUtil.returnError("没有找到料号 " + oldCode + "，没有改动");
            }
            result.put("lotsUpdated", Long.valueOf(lots));
            result.put("issuesUpdated", Long.valueOf(issues));
            result.put("receiptLinesUpdated", Long.valueOf(receiptLines));
            result.put("bomLinesUpdated", Long.valueOf(bomLines));
            result.put("replacementsUpdated", Long.valueOf(replacements));
            result.put("renamed", String.valueOf(total));
            result.put("search", newCode);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("重命名失败：" + e.getMessage());
        }
    }

    /**
     * Points every row of one entity from the old part number to the new one.
     *
     * @param delegator the delegator
     * @param entity the entity holding a {@code componentCode} field
     * @param oldCode the code to replace
     * @param newCode the new code
     * @return how many rows were changed
     * @throws GenericEntityException when the rows could not be read or stored
     */
    static int renameWhere(Delegator delegator, String entity, String oldCode, String newCode)
            throws GenericEntityException {
        int changed = 0;
        for (GenericValue row : EntityQuery.use(delegator).from(entity)
                .where("componentCode", oldCode).queryList()) {
            row.set("componentCode", newCode);
            row.store();
            changed++;
        }
        return changed;
    }

    /**
     * Records a component swapped on one unit during repair or rework. Unlike an SMT reel change this
     * is measured in house, so it is exact.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgReplacementSave}
     * @return the service result containing {@code replacementId}
     */
    public static Map<String, Object> replacementSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String sn = MfgSupport.toText(context.get("sn"));
        String componentCode = MfgSupport.toText(context.get("componentCode"));
        if (sn == null) {
            return ServiceUtil.returnError("sn is required");
        }
        if (componentCode == null) {
            return ServiceUtil.returnError("componentCode is required");
        }
        double qty = MfgSupport.toDouble(context.get("qty"), 1.0);
        if (qty <= 0.0) {
            return ServiceUtil.returnError("qty must be greater than zero");
        }
        try {
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }
            String oldLotId = MfgSupport.toText(context.get("oldLotId"));
            String newLotId = MfgSupport.toText(context.get("newLotId"));
            if (newLotId != null
                    && EntityQuery.use(delegator).from("MfgMaterialLot").where("lotId", newLotId).queryOne() == null) {
                return ServiceUtil.returnError("Unknown material lot [" + newLotId + "]");
            }
            String replacementId = delegator.getNextSeqId("MfgReplacement");
            GenericValue replacement = delegator.makeValue("MfgReplacement");
            replacement.set("replacementId", replacementId);
            replacement.set("sn", sn);
            replacement.set("workOrderId", unit.getString("workOrderId"));
            replacement.set("componentCode", componentCode);
            replacement.set("opSeq", Long.valueOf(MfgSupport.toLong(context.get("opSeq"), 0)));
            replacement.set("oldLotId", oldLotId);
            replacement.set("newLotId", newLotId);
            replacement.set("qty", Double.valueOf(qty));
            Object replacedDate = MfgSupport.parseDate(context.get("replacedDate"));
            replacement.set("replacedDate", replacedDate == null
                    ? java.sql.Date.valueOf(LocalDate.now()) : replacedDate);
            replacement.set("reason", MfgSupport.toText(context.get("reason")));
            replacement.set("hours", Double.valueOf(MfgSupport.toDouble(context.get("hours"), 0.0)));
            replacement.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(replacement);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("replacementId", replacementId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not record the replacement: " + e.getMessage());
        }
    }

    /**
     * Finished goods on hand per model: what the completions put into stock, minus what the
     * deliveries took out, with what is reserved for a sales order and what it is worth.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgProductStock}
     * @return the service result containing {@code rows} and the totals
     */
    public static Map<String, Object> productStock(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("rows", new ArrayList<Map<String, Object>>());
        result.put("productCount", Integer.valueOf(0));
        result.put("onHandTotal", Double.valueOf(0.0));
        result.put("shippedTotal", Double.valueOf(0.0));
        result.put("valueTotal", Double.valueOf(0.0));
        try {
            Map<String, Map<String, Object>> byModel = new LinkedHashMap<>();
            for (GenericValue lot : EntityQuery.use(delegator).from("MfgMaterialLot")
                    .orderBy("componentCode", "lotId").queryList()) {
                if (!MfgStockLedger.isProductLot(lot)) {
                    continue;
                }
                MfgStockLedger.ensureLotLedger(delegator, lot);
                String productCode = lot.getString("componentCode");
                if (productCode == null) {
                    continue;
                }
                Map<String, Object> row = byModel.get(productCode);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("productCode", productCode);
                    row.put("productName", lot.getString("componentName"));
                    row.put("onHandQty", Double.valueOf(0.0));
                    row.put("reservedQty", Double.valueOf(0.0));
                    row.put("availableQty", Double.valueOf(0.0));
                    row.put("shippedQty", Double.valueOf(0.0));
                    row.put("value", Double.valueOf(0.0));
                    row.put("lotCount", Integer.valueOf(0));
                    row.put("lastCompletedDate", null);
                    byModel.put(productCode, row);
                }
                double onHand = MfgSupport.numberOrZero(lot.get("onHandQty"));
                row.put("onHandQty", Double.valueOf(MfgSupport.numberOrZero(row.get("onHandQty")) + onHand));
                row.put("reservedQty", Double.valueOf(MfgSupport.numberOrZero(row.get("reservedQty"))
                        + MfgSupport.numberOrZero(lot.get("reservedQty"))));
                row.put("availableQty", Double.valueOf(MfgSupport.numberOrZero(row.get("availableQty"))
                        + MfgSupport.numberOrZero(lot.get("availableQty"))));
                row.put("lotCount", Integer.valueOf(((Integer) row.get("lotCount")).intValue() + 1));
                Object price = lot.get("unitPrice");
                if (price instanceof Number) {
                    row.put("value", Double.valueOf(MfgSupport.numberOrZero(row.get("value"))
                            + onHand * ((Number) price).doubleValue()));
                }
                java.sql.Date receivedDate = lot.getDate("receivedDate");
                Object last = row.get("lastCompletedDate");
                if (receivedDate != null && (last == null || receivedDate.after((java.util.Date) last))) {
                    row.put("lastCompletedDate", receivedDate);
                }
                double shipped = 0.0;
                for (GenericValue movement : MfgStockLedger.movementsOf(delegator, lot.getString("lotId"))) {
                    if (MfgConstants.MOVE_ISSUE.equals(movement.getString("movementTypeId"))) {
                        shipped += -MfgSupport.numberOrZero(movement.get("quantityDiff"));
                    }
                }
                row.put("shippedQty", Double.valueOf(MfgSupport.numberOrZero(row.get("shippedQty")) + shipped));
            }
            List<Map<String, Object>> rows = new ArrayList<>(byModel.size());
            double onHandTotal = 0.0;
            double shippedTotal = 0.0;
            double valueTotal = 0.0;
            for (Map<String, Object> row : byModel.values()) {
                row.put("onHandQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("onHandQty")))));
                row.put("reservedQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("reservedQty")))));
                row.put("availableQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("availableQty")))));
                row.put("shippedQty", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("shippedQty")))));
                row.put("value", Double.valueOf(MfgSupport.round2(MfgSupport.numberOrZero(row.get("value")))));
                onHandTotal += MfgSupport.numberOrZero(row.get("onHandQty"));
                shippedTotal += MfgSupport.numberOrZero(row.get("shippedQty"));
                valueTotal += MfgSupport.numberOrZero(row.get("value"));
                rows.add(row);
            }
            String search = MfgSupport.toText(context.get("search"));
            if (search != null) {
                String needle = search.toLowerCase(Locale.ROOT);
                List<Map<String, Object>> matched = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    String code = String.valueOf(row.get("productCode")).toLowerCase(Locale.ROOT);
                    String name = row.get("productName") == null ? ""
                            : String.valueOf(row.get("productName")).toLowerCase(Locale.ROOT);
                    if (code.contains(needle) || name.contains(needle)) {
                        matched.add(row);
                    }
                }
                rows = matched;
            }
            MfgPaging.putPage(result, "rows", rows, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), search);
            result.put("productCount", Integer.valueOf(rows.size()));
            result.put("onHandTotal", Double.valueOf(MfgSupport.round2(onHandTotal)));
            result.put("shippedTotal", Double.valueOf(MfgSupport.round2(shippedTotal)));
            result.put("valueTotal", Double.valueOf(MfgSupport.round2(valueTotal)));
            result.put("search", search);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the finished goods: " + e.getMessage());
        }
    }

    /**
     * Returns the products with their BOM and routing line counts, used by the product page.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgProductList}
     * @return the service result containing {@code products}
     */
    public static Map<String, Object> productList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> products = EntityQuery.use(delegator).from("MfgProduct").orderBy("productCode").queryList();
            List<Map<String, Object>> list = new ArrayList<>(products.size());
            for (GenericValue product : products) {
                String productCode = product.getString("productCode");
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("productCode", productCode);
                map.put("productName", product.getString("productName"));
                map.put("snPrefix", product.getString("snPrefix"));
                map.put("standardHours", product.getDouble("standardHours"));
                map.put("warrantyMonths", product.getLong("warrantyMonths"));
                map.put("salePrice", product.getDouble("salePrice"));
                map.put("statusId", product.getString("statusId"));
                map.put("devStageId", product.getString("devStageId"));
                String currentVersion = currentBomVersionId(delegator, productCode);
                map.put("currentBomVersionId", currentVersion);
                map.put("bomCount", Integer.valueOf(currentVersion == null ? 0
                : bomItemsOfVersion(delegator, currentVersion).size()));
                map.put("versionCount", Integer.valueOf(bomVersionCount(delegator, productCode)));
                MfgAttachmentServices.migrateProductPhotos(delegator, productCode);
                List<GenericValue> attachments =
                MfgAttachmentServices.attachmentRows(delegator, MfgConstants.ATT_PRODUCT, productCode);
                map.put("imageCount", Integer.valueOf(attachments.size()));
                map.put("latestImageSeq", attachments.isEmpty() ? null : attachments.get(0).getLong("seq"));
                map.put("routingCount", Integer.valueOf(count(delegator, "MfgRouting", productCode)));
                list.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "products",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "productCode", "productName",
                            "snPrefix", "statusId"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            // the list unfolds its rows in place, so the BOM lines of the visible page come along
            for (Map<String, Object> row : MfgPaging.rowsOf(result, "products")) {
                String version = (String) row.get("currentBomVersionId");
                row.put("bomItems", version == null ? new ArrayList<Map<String, Object>>()
                        : bomItemMaps(bomItemsOfVersion(delegator, version)));
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the product models: " + e.getMessage());
        }
    }

    static int count(Delegator delegator, String entityName, String productCode) {
        try {
            return EntityQuery.use(delegator).from(entityName).where("productCode", productCode).queryList().size();
        } catch (GenericEntityException e) {
            return 0;
        }
    }

    /**
     * Returns the BOM and routing of one product model, used by the product page.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgProductDetail}
     * @return the service result containing {@code product}, {@code bomItems} and {@code routings}
     */
    public static Map<String, Object> productDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        try {
            if (productCode == null) {
                Map<String, Object> result = ServiceUtil.returnSuccess();
                result.put("bomItems", new ArrayList<Map<String, Object>>());
                result.put("routings", new ArrayList<Map<String, Object>>());
                return result;
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            String currentVersion = currentBomVersionId(delegator, productCode);
            List<GenericValue> bomItems = currentVersion == null ? new ArrayList<GenericValue>()
                    : bomItemsOfVersion(delegator, currentVersion);
            List<GenericValue> routings = EntityQuery.use(delegator).from("MfgRouting")
                    .where("productCode", productCode).orderBy("opSeq").queryList();

            List<Map<String, Object>> routingMaps = new ArrayList<>(routings.size());
            for (GenericValue operation : routings) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("opSeq", operation.getLong("opSeq"));
                map.put("opName", operation.getString("opName"));
                map.put("workCenter", operation.getString("workCenter"));
                map.put("stdHours", operation.getDouble("stdHours"));
                routingMaps.add(map);
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            if (product != null) {
                result.put("product", newMapWithProduct(product));
            }
            result.put("bomVersionId", currentVersion);
            result.put("bomItems", bomItemMaps(bomItems));
            result.put("routings", routingMaps);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the product model: " + e.getMessage());
        }
    }

    /**
     * Turns BOM lines into the maps the pages show.
     *
     * @param bomItems the BOM lines of one version
     * @return one map per line, in line order
     */
    static List<Map<String, Object>> bomItemMaps(List<GenericValue> bomItems) {
        List<Map<String, Object>> bomMaps = new ArrayList<>(bomItems.size());
        for (GenericValue item : bomItems) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("lineSeq", item.getLong("lineSeq"));
            map.put("componentCode", item.getString("componentCode"));
            map.put("componentName", item.getString("componentName"));
            map.put("qty", item.getDouble("qty"));
            map.put("designators", item.getString("designators"));
            map.put("isKeyPart", item.getString("isKeyPart"));
            map.put("unitPrice", item.get("unitPrice"));
            bomMaps.add(map);
        }
        return bomMaps;
    }

    static Map<String, Object> newMapWithProduct(GenericValue product) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("productCode", product.getString("productCode"));
        map.put("productName", product.getString("productName"));
        map.put("snPrefix", product.getString("snPrefix"));
        map.put("standardHours", product.getDouble("standardHours"));
        String hoursSource = product.getString("standardHoursSource");
        // models created before the provenance was recorded are hand filled in by definition
        map.put("standardHoursSource", hoursSource == null ? MfgConstants.HOURS_SOURCE_MANUAL : hoursSource);
        map.put("standardHoursBaseQty", product.getDouble("standardHoursBaseQty"));
        map.put("standardHoursEstimateRef", product.getString("standardHoursEstimateRef"));
        map.put("laborRate", product.get("laborRate"));
        map.put("warrantyMonths", product.getLong("warrantyMonths"));
        map.put("salePrice", product.getDouble("salePrice"));
        map.put("statusId", product.getString("statusId"));
        map.put("devStageId", product.getString("devStageId"));
        map.put("currentBomVersionId", product.getString("currentBomVersionId"));
        map.put("note", product.getString("note"));
        return map;
    }

    /**
     * Builds the identifier of one BOM version, {@code model-Vnumber}.
     *
     * <p>The identifier has to fit the {@value MfgConstants#MAX_BOM_VERSION_ID} characters of an {@code id}
     * column, so a model code that uses the whole column is shortened — the version number is the
     * part that has to stay readable.
     *
     * @param productCode the model code
     * @param versionNo the version number, starting at 1
     * @return the version identifier, e.g. {@code BT5-V2}, or {@code orpah-client-demo-V1} for a
     *         model code that is 20 characters long
     */
    static String formatBomVersionId(String productCode, long versionNo) {
        String suffix = MfgConstants.BOM_VERSION_SEPARATOR + versionNo;
        String code = productCode == null ? "" : productCode;
        if (code.length() + suffix.length() <= MfgConstants.MAX_BOM_VERSION_ID) {
            return code + suffix;
        }
        return code.substring(0, Math.max(1, MfgConstants.MAX_BOM_VERSION_ID - suffix.length())) + suffix;
    }

    /**
     * Returns an identifier no other model already uses. {@link #formatBomVersionId} shortens a long
     * model code, so two long codes that share a prefix can produce the same identifier; the second
     * one then carries a counter in front of the version number.
     *
     * @param delegator the delegator
     * @param productCode the model code
     * @param versionNo the version number
     * @return a free version identifier
     * @throws GenericEntityException when the versions cannot be read
     */
    static String freeBomVersionId(Delegator delegator, String productCode, long versionNo)
            throws GenericEntityException {
        String suffix = MfgConstants.BOM_VERSION_SEPARATOR + versionNo;
        String id = formatBomVersionId(productCode, versionNo);
        int attempt = 0;
        while (true) {
            GenericValue existing = EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", id).queryOne();
            if (existing == null || productCode.equals(existing.getString("productCode"))) {
                return id;
            }
            attempt++;
            String tag = "-" + attempt;
            int room = MfgConstants.MAX_BOM_VERSION_ID - suffix.length() - tag.length();
            if (room < 1) {
                return id;
            }
            id = productCode.substring(0, Math.min(productCode.length(), room)) + tag + suffix;
        }
    }

    /**
     * Returns the next version number of a model.
     *
     * <p>The number is read back from the identifiers instead of from a fixed prefix, because the
     * identifiers of a long model code are shortened by {@link #formatBomVersionId}.
     *
     * @param versionIds the identifiers of the versions that already exist
     * @param productCode the model code
     * @return one more than the highest version number found, 1 when there is none
     */
    static long nextBomVersionNo(List<String> versionIds, String productCode) {
        long max = 0;
        long candidate = 1;
        while (candidate <= versionIds.size() + 1) {
            if (versionIds.contains(formatBomVersionId(productCode, candidate))) {
                max = candidate;
            }
            candidate++;
        }
        return max + 1L;
    }

    static List<String> versionIds(Delegator delegator, String productCode) throws GenericEntityException {
        List<String> ids = new ArrayList<>();
        for (GenericValue version : bomVersionsOf(delegator, productCode)) {
            ids.add(version.getString("bomVersionId"));
        }
        return ids;
    }

    static List<GenericValue> bomVersionsOf(Delegator delegator, String productCode)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgBomVersion")
                .where("productCode", productCode).orderBy("-versionNo").queryList();
    }

    static int bomVersionCount(Delegator delegator, String productCode) {
        try {
            return bomVersionsOf(delegator, productCode).size();
        } catch (GenericEntityException e) {
            return 0;
        }
    }

    static List<GenericValue> bomItemsOfVersion(Delegator delegator, String bomVersionId)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgBomVersionItem")
                .where("bomVersionId", bomVersionId).orderBy("lineSeq").queryList();
    }

    /**
     * Returns the line number a new BOM line of this version gets: ten more than the highest one in
     * use, so that appending never collides with an existing line.
     *
     * @param delegator the delegator
     * @param bomVersionId the version
     * @return the next free line number, 10 for an empty version
     * @throws GenericEntityException when the version cannot be read
     */
    static long nextBomLineSeq(Delegator delegator, String bomVersionId) throws GenericEntityException {
        long max = 0;
        for (GenericValue item : bomItemsOfVersion(delegator, bomVersionId)) {
            Long lineSeq = item.getLong("lineSeq");
            if (lineSeq != null) {
                max = Math.max(max, lineSeq.longValue());
            }
        }
        return max + 10L;
    }

    /**
     * Returns the BOM version the model currently uses, freezing the old single BOM table into a
     * released version the first time a model that only has the old rows is read.
     *
     * @param delegator the delegator
     * @param productCode the model code
     * @return the version identifier, {@code null} when the model has no BOM at all
     */
    static String currentBomVersionId(Delegator delegator, String productCode) {
        try {
            if (productCode == null) {
                return null;
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            if (product == null) {
                return null;
            }
            String current = MfgSupport.toText(product.getString("currentBomVersionId"));
            if (current != null && EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", current).queryOne() != null) {
                return current;
            }
            List<GenericValue> legacy = EntityQuery.use(delegator).from("MfgBomItem")
                    .where("productCode", productCode).orderBy("lineSeq").queryList();
            if (legacy.isEmpty()) {
                return null;
            }
            long versionNo = nextBomVersionNo(versionIds(delegator, productCode), productCode);
            String versionId = freeBomVersionId(delegator, productCode, versionNo);
            GenericValue version = delegator.makeValue("MfgBomVersion");
            version.set("bomVersionId", versionId);
            version.set("productCode", productCode);
            version.set("versionNo", Long.valueOf(versionNo));
            version.set("statusId", MfgConstants.BOM_RELEASED);
            version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            version.set("note", "Frozen from the single BOM table");
            delegator.create(version);
            long lineSeq = 10;
            for (GenericValue item : legacy) {
                GenericValue copy = delegator.makeValue("MfgBomVersionItem");
                copy.set("bomVersionId", versionId);
                copy.set("lineSeq", Long.valueOf(lineSeq));
                copy.set("componentCode", item.getString("componentCode"));
                copy.set("componentName", item.getString("componentName"));
                copy.set("qty", item.getDouble("qty"));
                copy.set("designators", item.getString("designators"));
                copy.set("isKeyPart", item.getString("isKeyPart"));
                delegator.create(copy);
                lineSeq += 10;
            }
            product.set("currentBomVersionId", versionId);
            product.store();
            return versionId;
        } catch (GenericEntityException e) {
            return null;
        }
    }

    /**
     * Returns the BOM version the model uses, creating a released first version when it has none
     * yet, so that a line added or imported by hand shows up as the current BOM straight away.
     *
     * @param delegator the delegator
     * @param productCode the model code
     * @param note the note written on the version created
     * @return the version identifier
     * @throws GenericEntityException when the version cannot be written
     */
    static String currentOrNewBomVersion(Delegator delegator, String productCode, String note)
            throws GenericEntityException {
        String versionId = currentBomVersionId(delegator, productCode);
        if (versionId != null) {
            return versionId;
        }
        versionId = createBomVersion(delegator, productCode, null, "N", note);
        GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                .where("bomVersionId", versionId).queryOne();
        if (version != null) {
            version.set("statusId", MfgConstants.BOM_RELEASED);
            version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            version.store();
        }
        GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                .where("productCode", productCode).queryOne();
        if (product != null) {
            product.set("currentBomVersionId", versionId);
            product.store();
        }
        return versionId;
    }

    static String createBomVersion(Delegator delegator, String productCode, String ecoId,
            String copyCurrent, String note) throws GenericEntityException {
        long versionNo = nextBomVersionNo(versionIds(delegator, productCode), productCode);
        String versionId = freeBomVersionId(delegator, productCode, versionNo);
        String basis = currentBomVersionId(delegator, productCode);
        GenericValue version = delegator.makeValue("MfgBomVersion");
        version.set("bomVersionId", versionId);
        version.set("productCode", productCode);
        version.set("versionNo", Long.valueOf(versionNo));
        version.set("statusId", MfgConstants.BOM_DRAFT);
        version.set("basisVersionId", basis);
        version.set("ecoId", ecoId);
        version.set("note", note);
        delegator.create(version);
        if (!"N".equals(copyCurrent) && basis != null) {
            long lineSeq = 10;
            for (GenericValue item : bomItemsOfVersion(delegator, basis)) {
                GenericValue copy = delegator.makeValue("MfgBomVersionItem");
                copy.set("bomVersionId", versionId);
                copy.set("lineSeq", Long.valueOf(lineSeq));
                copy.set("componentCode", item.getString("componentCode"));
                copy.set("componentName", item.getString("componentName"));
                copy.set("qty", item.getDouble("qty"));
                copy.set("designators", item.getString("designators"));
                copy.set("isKeyPart", item.getString("isKeyPart"));
                delegator.create(copy);
                lineSeq += 10;
            }
        }
        return versionId;
    }

    static Map<String, Object> toBomVersionMap(Delegator delegator, GenericValue version)
            throws GenericEntityException {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bomVersionId", version.getString("bomVersionId"));
        map.put("productCode", version.getString("productCode"));
        map.put("versionNo", version.getLong("versionNo"));
        map.put("statusId", version.getString("statusId"));
        map.put("effectiveDate", version.get("effectiveDate"));
        map.put("basisVersionId", version.getString("basisVersionId"));
        map.put("ecoId", version.getString("ecoId"));
        map.put("note", version.getString("note"));
        map.put("itemCount", Integer.valueOf(bomItemsOfVersion(delegator, version.getString("bomVersionId")).size()));
        return map;
    }

    /**
     * Creates the next BOM version of a model as a draft, copying the current lines by default.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomVersionCreate}
     * @return the service result containing {@code bomVersionId}
     */
    public static Map<String, Object> bomVersionCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        if (productCode == null) {
            return ServiceUtil.returnError("productCode is required");
        }
        try {
            if (EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryOne() == null) {
                return ServiceUtil.returnError("Unknown product model [" + productCode + "], create it first");
            }
            String copyCurrent = MfgSupport.toText(context.get("copyCurrent"));
            String versionId = createBomVersion(delegator, productCode, MfgSupport.toText(context.get("ecoId")),
                    copyCurrent == null ? "Y" : copyCurrent, MfgSupport.toText(context.get("note")));
            GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", versionId).queryOne();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("bomVersionId", versionId);
            result.put("versionNo", version.getLong("versionNo"));
            result.put("copiedCount", Long.valueOf(bomItemsOfVersion(delegator, versionId).size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the BOM version: " + e.getMessage());
        }
    }

    /**
     * Makes a BOM version the one in use. The version becomes released and the other released
     * versions of the same model become obsolete.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomVersionActivate}
     * @return the service result containing {@code bomVersionId} and {@code replacedVersionId}
     */
    public static Map<String, Object> bomVersionActivate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String bomVersionId = MfgSupport.toText(context.get("bomVersionId"));
        if (bomVersionId == null) {
            return ServiceUtil.returnError("bomVersionId is required");
        }
        try {
            GenericValue version = EntityQuery.use(delegator).from("MfgBomVersion")
                    .where("bomVersionId", bomVersionId).queryOne();
            if (version == null) {
                return ServiceUtil.returnError("Unknown BOM version [" + bomVersionId + "]");
            }
            String productCode = version.getString("productCode");
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", productCode).queryOne();
            String replaced = product == null ? null : MfgSupport.toText(product.getString("currentBomVersionId"));
            for (GenericValue other : bomVersionsOf(delegator, productCode)) {
                if (!bomVersionId.equals(other.getString("bomVersionId"))
                        && MfgConstants.BOM_RELEASED.equals(other.getString("statusId"))) {
                    other.set("statusId", MfgConstants.BOM_OBSOLETE);
                    other.store();
                }
            }
            version.set("statusId", MfgConstants.BOM_RELEASED);
            version.set("effectiveDate", java.sql.Date.valueOf(LocalDate.now()));
            version.store();
            if (product != null) {
                product.set("currentBomVersionId", bomVersionId);
                product.store();
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("bomVersionId", bomVersionId);
            result.put("productCode", productCode);
            result.put("replacedVersionId", replaced);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not activate the BOM version: " + e.getMessage());
        }
    }

    /**
     * Lists the BOM versions of one model, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomVersionList}
     * @return the service result containing {@code bomVersions}
     */
    public static Map<String, Object> bomVersionList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        try {
            List<GenericValue> versions = productCode == null
                    ? EntityQuery.use(delegator).from("MfgBomVersion").orderBy("-createdStamp").queryList()
                    : bomVersionsOf(delegator, productCode);
            List<Map<String, Object>> list = new ArrayList<>(versions.size());
            for (GenericValue version : versions) {
                list.add(toBomVersionMap(delegator, version));
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "bomVersions",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "bomVersionId", "productCode",
                            "statusId", "note"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the BOM versions: " + e.getMessage());
        }
    }

    /**
     * Freezes the rows of the old single BOM table into a released version for the models that
     * still only have those rows. Idempotent, so it can be called on every deployment.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/product-services.xml#mfgBomMigrateLegacy}
     * @return the service result containing the migrated model codes
     */
    public static Map<String, Object> bomMigrateLegacy(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_ENG);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String productCode = MfgSupport.toText(context.get("productCode"));
        List<String> migrated = new ArrayList<>();
        try {
            List<GenericValue> products = productCode == null
                    ? EntityQuery.use(delegator).from("MfgProduct").orderBy("productCode").queryList()
                    : EntityQuery.use(delegator).from("MfgProduct").where("productCode", productCode).queryList();
            for (GenericValue product : products) {
                String code = product.getString("productCode");
                String before = MfgSupport.toText(product.getString("currentBomVersionId"));
                String after = currentBomVersionId(delegator, code);
                if (before == null && after != null) {
                    migrated.add(code);
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("migratedProducts", migrated);
            result.put("migratedCount", Long.valueOf(migrated.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not migrate the legacy BOM: " + e.getMessage());
        }
    }

    /**
     * Reads the order lines typed or pasted by the person: {@code code,name,quantity,amount} per
     * line, separated by comma or tab; blank lines and lines starting with {@code #} are ignored.
     *
     * @param text the pasted text
     * @return one array per line: code, name, quantity, amount (as text)
     */
    public static String fittingComponentCode(String code) {
        // the spaces between the words are part of the number (5.5V1.5F C型 21*7.5); only runs of
        // blanks are squeezed, so F, C型 and 21*7.5 do not run together
        String trimmed = code == null ? "" : code.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= MfgConstants.MAX_COMPONENT_CODE) {
            return trimmed;
        }
        String hash = String.format(Locale.ROOT, "%06x", ((long) trimmed.hashCode()) & 0xffffffL);
        int keep = MfgConstants.MAX_COMPONENT_CODE - hash.length() - 1;
        String head = trimmed.substring(0, keep).trim();
        int cut = head.lastIndexOf(' ');
        if (cut >= keep - 8) {
            head = head.substring(0, cut);
        }
        return head + "-" + hash;
    }

    /**
     * Rewrites the code column of pasted order lines so every code fits the code field. A name may
     * hold commas, so the rows are joined with tabs, which the parser accepts too.
     *
     * @param lines the pasted or recognised lines
     * @return the same lines with fitting codes
     */
    public static String nameWithFullCode(String name, String fullCode) {
        String base = name == null ? "" : name.trim();
        if (fullCode == null || fullCode.isEmpty() || base.contains(fullCode)) {
            return base;
        }
        String combined = base.isEmpty() ? fullCode : base + ' ' + fullCode;
        return combined.length() <= 100 ? combined : combined.substring(0, 100);
    }
}
