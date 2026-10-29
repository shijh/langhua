package org.langhua.ofbiz.mfg;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * Turns a business record into the flat map a page or a CSV writes: a work order, a claim, a
 * delivery, a serial number, a lot, an issue, a report, a vendor or a replacement.
 *
 * <p>One row of the page is built in exactly one place, so the list page, the detail page and the
 * export cannot disagree about what a field is called or how a quantity is rounded. Nothing here
 * writes: these are readers of entity values, which is why they can be shared by the services of
 * several areas (Step 172).
 */
public final class MfgRecordMaps {

    private MfgRecordMaps() {
    }

    static Map<String, Object> toLotMap(GenericValue lot, Double issuedQty) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("lotId", lot.getString("lotId"));
        map.put("lotKindId", MfgStockLedger.lotKindOf(lot));
        map.put("workOrderId", lot.getString("workOrderId"));
        map.put("componentCode", lot.getString("componentCode"));
        map.put("componentName", lot.getString("componentName"));
        map.put("supplierName", lot.getString("supplierName"));
        map.put("supplierLot", lot.getString("supplierLot"));
        map.put("unitPrice", lot.get("unitPrice"));
        map.put("receivedQty", lot.getDouble("receivedQty"));
        map.put("onHandQty", lot.getDouble("onHandQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("onHandQty"));
        map.put("reservedQty", lot.getDouble("reservedQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("reservedQty"));
        map.put("availableQty", lot.getDouble("availableQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("availableQty"));
        map.put("receivedDate", lot.getDate("receivedDate"));
        map.put("issuedQty", issuedQty == null ? Double.valueOf(0.0) : issuedQty);
        double received = lot.getDouble("receivedQty") == null ? 0.0 : lot.getDouble("receivedQty").doubleValue();
        double issued = issuedQty == null ? 0.0 : issuedQty.doubleValue();
        // what is really left comes from the ledger, so a scrap or an adjustment shows up here;
        // received minus issued only knows about issuing, which made a scrapped lot look full
        double onHand = lot.getDouble("onHandQty") == null
                ? MfgSupport.round2(received - issued) : lot.getDouble("onHandQty").doubleValue();
        map.put("remainingQty", Double.valueOf(MfgSupport.round2(onHand)));
        String inspectStatus = lot.getString("inspectStatusId");
        map.put("inspectStatusId", inspectStatus == null ? MfgConstants.IQC_PENDING : inspectStatus);
        map.put("inspectDate", lot.get("inspectDate"));
        map.put("inspector", lot.getString("inspector"));
        map.put("inspectNote", lot.getString("inspectNote"));
        map.put("rejectedQty", lot.getDouble("rejectedQty") == null
                ? Double.valueOf(0.0) : lot.getDouble("rejectedQty"));
        map.put("usable", Boolean.valueOf(MfgWorkOrderServices.isLotUsable(inspectStatus)));
        map.put("splitFromLotId", lot.getString("splitFromLotId"));
        map.put("note", lot.getString("note"));
        return map;
    }

    static Map<String, Object> toIssueMap(GenericValue issue, GenericValue lot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getString("issueId"));
        map.put("workOrderId", issue.getString("workOrderId"));
        map.put("componentCode", issue.getString("componentCode"));
        map.put("lotId", issue.getString("lotId"));
        map.put("qty", issue.getDouble("qty"));
        map.put("directionId", issue.getString("directionId"));
        map.put("issuedDate", issue.getDate("issuedDate"));
        map.put("designators", issue.getString("designators"));
        map.put("snFrom", issue.getString("snFrom"));
        map.put("snTo", issue.getString("snTo"));
        map.put("note", issue.getString("note"));
        if (lot != null) {
            map.put("supplierName", lot.getString("supplierName"));
            map.put("supplierLot", lot.getString("supplierLot"));
            map.put("receivedDate", lot.getDate("receivedDate"));
        }
        return map;
    }

    static Map<String, Object> toIssueMap(GenericValue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("issueId", issue.getString("issueId"));
        map.put("projectCode", issue.getString("projectCode"));
        map.put("stepId", issue.getString("stepId"));
        map.put("title", issue.getString("title"));
        map.put("detail", issue.getString("detail"));
        map.put("severityId", issue.getString("severityId"));
        map.put("statusId", issue.getString("statusId"));
        map.put("ownerName", issue.getString("ownerName"));
        map.put("foundDate", issue.get("foundDate"));
        map.put("dueDate", issue.get("dueDate"));
        map.put("closedDate", issue.get("closedDate"));
        map.put("resolution", issue.getString("resolution"));
        map.put("createdStamp", issue.get("createdStamp"));
        return map;
    }

    static List<Map<String, Object>> toReplacementMaps(List<GenericValue> replacements) {
        List<Map<String, Object>> list = new ArrayList<>(replacements.size());
        for (GenericValue replacement : replacements) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("replacementId", replacement.getString("replacementId"));
            map.put("sn", replacement.getString("sn"));
            map.put("workOrderId", replacement.getString("workOrderId"));
            map.put("componentCode", replacement.getString("componentCode"));
            map.put("opSeq", replacement.getLong("opSeq"));
            map.put("oldLotId", replacement.getString("oldLotId"));
            map.put("newLotId", replacement.getString("newLotId"));
            map.put("qty", replacement.getDouble("qty"));
            map.put("replacedDate", replacement.getDate("replacedDate"));
            map.put("reason", replacement.getString("reason"));
            map.put("hours", replacement.getDouble("hours"));
            map.put("note", replacement.getString("note"));
            list.add(map);
        }
        return list;
    }

    static Map<String, Object> toDeliveryMap(GenericValue delivery) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("deliveryId", delivery.getString("deliveryId"));
        map.put("customerName", delivery.getString("customerName"));
        map.put("customerPo", delivery.getString("customerPo"));
        map.put("deliveryDate", delivery.getDate("deliveryDate"));
        map.put("carrier", delivery.getString("carrier"));
        map.put("trackingNo", delivery.getString("trackingNo"));
        map.put("workOrderId", delivery.getString("workOrderId"));
        map.put("note", delivery.getString("note"));
        return map;
    }

    static List<Map<String, Object>> vendorMaps(Delegator delegator) throws GenericEntityException {
        List<GenericValue> vendors = EntityQuery.use(delegator).from("MfgVendor")
                .orderBy("vendorName").queryList();
        List<Map<String, Object>> list = new ArrayList<>(vendors.size());
        for (GenericValue vendor : vendors) {
            list.add(MfgWorkOrderServices.toVendorMap(vendor));
        }
        return list;
    }

    static Map<String, Object> toClaimMap(GenericValue claim) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("claimId", claim.getString("claimId"));
        map.put("sn", claim.getString("sn"));
        map.put("workOrderId", claim.getString("workOrderId"));
        map.put("customerName", claim.getString("customerName"));
        map.put("deliveryId", claim.getString("deliveryId"));
        map.put("deliveryDate", claim.getDate("deliveryDate"));
        map.put("warrantyEndDate", claim.getDate("warrantyEndDate"));
        map.put("inWarranty", claim.getString("inWarranty"));
        map.put("receivedDate", claim.getDate("receivedDate"));
        map.put("issueDesc", claim.getString("issueDesc"));
        map.put("statusId", claim.getString("statusId"));
        map.put("claimType", claim.getString("claimType"));
        map.put("tradeNo", claim.getString("tradeNo"));
        map.put("buyerNick", claim.getString("buyerNick"));
        map.put("signedDate", claim.getDate("signedDate"));
        map.put("sevenDayDeadline", claim.getTimestamp("sevenDayDeadline"));
        map.put("afterSaleDeadline", claim.getTimestamp("afterSaleDeadline"));
        map.put("sevenDayHoursLeft", Long.valueOf(MfgSupport.hoursLeft(claim.getTimestamp("sevenDayDeadline"))));
        map.put("afterSaleHoursLeft", Long.valueOf(MfgSupport.hoursLeft(claim.getTimestamp("afterSaleDeadline"))));
        map.put("refundAmount", claim.getDouble("refundAmount"));
        map.put("deductAmount", claim.getDouble("deductAmount"));
        map.put("freightPayer", claim.getString("freightPayer"));
        map.put("returnTrackingNo", claim.getString("returnTrackingNo"));
        map.put("receivedBackDate", claim.getDate("receivedBackDate"));
        map.put("inspectResult", claim.getString("inspectResult"));
        map.put("vendorClaimStatus", claim.getString("vendorClaimStatus"));
        map.put("buyerRiskLevel", claim.getString("buyerRiskLevel"));
        map.put("repairWorkOrderId", claim.getString("repairWorkOrderId"));
        map.put("exchangeDeliveryId", claim.getString("exchangeDeliveryId"));
        map.put("responsibility", claim.getString("responsibility"));
        map.put("repairHours", claim.getDouble("repairHours"));
        map.put("closedDate", claim.getDate("closedDate"));
        map.put("note", claim.getString("note"));
        return map;
    }

    static Map<String, Object> toWorkOrderMap(GenericValue order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("smtVendorId", order.getString("smtVendorId"));
        map.put("workOrderId", order.getString("workOrderId"));
        map.put("productCode", order.getString("productCode"));
        map.put("plannedQty", order.getLong("plannedQty"));
        map.put("statusId", order.getString("statusId"));
        map.put("dueDate", order.get("dueDate"));
        map.put("customerName", order.getString("customerName"));
        map.put("smtVendor", order.getString("smtVendor"));
        map.put("note", order.getString("note"));
        map.put("createdStamp", order.get("createdStamp"));
        return map;
    }

    static Map<String, Object> toSnMap(GenericValue unit) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sn", unit.getString("sn"));
        map.put("workOrderId", unit.getString("workOrderId"));
        map.put("productCode", unit.getString("productCode"));
        map.put("statusId", unit.getString("statusId"));
        map.put("createdStamp", unit.get("createdStamp"));
        return map;
    }

    static Map<String, Object> toReportMap(GenericValue report) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reportId", report.getString("reportId"));
        map.put("workOrderId", report.getString("workOrderId"));
        map.put("opSeq", report.getLong("opSeq"));
        map.put("sn", report.getString("sn"));
        map.put("operatorName", report.getString("operatorName"));
        map.put("workHours", report.getDouble("workHours"));
        map.put("qtyOk", report.getLong("qtyOk"));
        map.put("qtyNg", report.getLong("qtyNg"));
        map.put("reportDate", report.get("reportDate"));
        map.put("note", report.getString("note"));
        return map;
    }
}
