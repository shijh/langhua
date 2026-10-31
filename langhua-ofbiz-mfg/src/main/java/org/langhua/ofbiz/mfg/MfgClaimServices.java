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

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
 * After sales: the claims (return, exchange, repair), the warranty and Taobao windows they are
 * judged against, the buyer risk list, and the quality report that turns all of it into rates.
 *
 * <p>The windows are pure functions of the delivery date, so the claim page, the dashboard alerts
 * and the report cannot disagree about when a claim is still allowed. Moved out of
 * {@code MfgServices} in Step 169.
 */
public final class MfgClaimServices {

    private MfgClaimServices() {
    }

    /**
     * Creates a warranty claim for one unit. The customer, the delivery date and the warranty end
     * date are derived from the delivery order and the product warranty months.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimCreate}
     * @return the service result containing {@code claimId} and {@code inWarranty}
     */
    public static Map<String, Object> claimCreate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String sn = MfgSupport.toText(context.get("sn"));
        String issueDesc = MfgSupport.toText(context.get("issueDesc"));
        if (sn == null) {
            return ServiceUtil.returnError("sn is required");
        }
        if (issueDesc == null) {
            return ServiceUtil.returnError("issueDesc is required");
        }
        try {
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }
            GenericValue delivery = null;
            GenericValue deliveryItem = EntityQuery.use(delegator).from("MfgDeliverySn")
                    .where("sn", sn).queryOne();
            if (deliveryItem != null) {
                delivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", deliveryItem.getString("deliveryId")).queryOne();
            }
            GenericValue product = EntityQuery.use(delegator).from("MfgProduct")
                    .where("productCode", unit.getString("productCode")).queryOne();
            long warrantyMonths = product == null ? 12 : MfgSupport.toLong(product.get("warrantyMonths"), 12);
            java.sql.Date deliveryDate = delivery == null ? null : delivery.getDate("deliveryDate");
            java.sql.Date endDate = warrantyEndDate(deliveryDate, warrantyMonths);
            java.sql.Date signedDate = (java.sql.Date) MfgSupport.parseDate(context.get("signedDate"));
            if (signedDate == null) {
                signedDate = deliveryDate;
            }
            java.sql.Date receivedDate = (java.sql.Date) MfgSupport.parseDate(context.get("receivedDate"));
            if (receivedDate == null) {
                receivedDate = java.sql.Date.valueOf(LocalDate.now());
            }
            String claimType = MfgSupport.toText(context.get("claimType"));
            if (claimType == null) {
                claimType = MfgConstants.CLAIM_RTN;
            } else if (!MfgConstants.CLAIM_RTN.equals(claimType) && !MfgConstants.CLAIM_EXCH.equals(claimType)
                    && !MfgConstants.CLAIM_REPAIR.equals(claimType) && !MfgConstants.CLAIM_REFUND_ONLY.equals(claimType)) {
                return ServiceUtil.returnError("claimType must be " + MfgConstants.CLAIM_RTN + ", " + MfgConstants.CLAIM_EXCH
                        + ", " + MfgConstants.CLAIM_REPAIR + " or " + MfgConstants.CLAIM_REFUND_ONLY);
            }
            String freightPayer = MfgSupport.toText(context.get("freightPayer"));
            if (freightPayer != null && !MfgConstants.FREIGHT_SELLER.equals(freightPayer)
                    && !MfgConstants.FREIGHT_BUYER.equals(freightPayer)) {
                return ServiceUtil.returnError("freightPayer must be " + MfgConstants.FREIGHT_SELLER
                        + " or " + MfgConstants.FREIGHT_BUYER);
            }

            String claimId = nextClaimId(delegator, LocalDate.now());
            String buyerNick = MfgSupport.toText(context.get("buyerNick"));
            GenericValue claim = delegator.makeValue("MfgWarrantyClaim");
            claim.set("claimId", claimId);
            claim.set("sn", sn);
            claim.set("workOrderId", unit.getString("workOrderId"));
            claim.set("customerName", delivery == null ? MfgSupport.toText(context.get("customerName"))
                    : delivery.getString("customerName"));
            claim.set("deliveryId", delivery == null ? null : delivery.getString("deliveryId"));
            claim.set("deliveryDate", deliveryDate);
            claim.set("warrantyEndDate", endDate);
            claim.set("inWarranty", isInWarranty(endDate, receivedDate) ? "Y" : "N");
            claim.set("receivedDate", receivedDate);
            claim.set("issueDesc", issueDesc);
            claim.set("statusId", MfgConstants.RMA_OPEN);
            claim.set("claimType", claimType);
            claim.set("tradeNo", MfgSupport.toText(context.get("tradeNo")));
            claim.set("buyerNick", buyerNick);
            claim.set("signedDate", signedDate);
            claim.set("sevenDayDeadline", sevenDayDeadline(signedDate));
            claim.set("afterSaleDeadline", afterSaleDeadline(signedDate));
            claim.set("refundAmount", Double.valueOf(MfgSupport.toDouble(context.get("refundAmount"), 0.0)));
            claim.set("deductAmount", Double.valueOf(MfgSupport.toDouble(context.get("deductAmount"), 0.0)));
            claim.set("freightPayer", freightPayer == null ? defaultFreightPayer(claimType) : freightPayer);
            claim.set("responsibility", MfgSupport.toText(context.get("responsibility")));
            claim.set("repairHours", Double.valueOf(MfgSupport.toDouble(context.get("repairHours"), 0.0)));
            claim.set("vendorClaimStatus", "NONE");
            claim.set("buyerRiskLevel", activeRiskLevel(delegator, buyerNick));
            claim.set("note", MfgSupport.toText(context.get("note")));
            delegator.create(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("inWarranty", claim.getString("inWarranty"));
            result.put("claimType", claimType);
            result.put("buyerRiskLevel", claim.getString("buyerRiskLevel"));
            result.put("sevenDayNoReason",
                    isWithinSevenDays(signedDate, receivedDate) ? "Y" : "N");
            java.sql.Timestamp sevenDay = sevenDayDeadline(signedDate);
            if (sevenDay != null) {
                result.put("sevenDayDeadline", sevenDay.toString());
            }
            java.sql.Timestamp afterSale = afterSaleDeadline(signedDate);
            if (afterSale != null) {
                result.put("afterSaleDeadline", afterSale.toString());
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not create the claim: " + e.getMessage());
        }
    }

    /**
     * Updates the handling of a claim: status, responsibility, repair hours and note.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimUpdate}
     * @return the service result containing {@code claimId} and {@code statusId}
     */
    public static Map<String, Object> claimUpdate(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String claimId = MfgSupport.toText(context.get("claimId"));
        if (claimId == null) {
            return ServiceUtil.returnError("claimId is required");
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return ServiceUtil.returnError("Unknown claim [" + claimId + "]");
            }
            String statusId = MfgSupport.toText(context.get("statusId"));
            if (statusId != null) {
                if (!MfgConstants.RMA_OPEN.equals(statusId) && !MfgConstants.RMA_REPAIRED.equals(statusId)
                        && !MfgConstants.RMA_REPLACED.equals(statusId) && !MfgConstants.RMA_CLOSED.equals(statusId)) {
                    return ServiceUtil.returnError("statusId must be " + MfgConstants.RMA_OPEN + ", " + MfgConstants.RMA_REPAIRED
                            + ", " + MfgConstants.RMA_REPLACED + " or " + MfgConstants.RMA_CLOSED);
                }
                claim.set("statusId", statusId);
                claim.set("closedDate", MfgConstants.RMA_CLOSED.equals(statusId)
                        ? java.sql.Date.valueOf(LocalDate.now()) : null);
            }
            String responsibility = MfgSupport.toText(context.get("responsibility"));
            if (responsibility != null) {
                claim.set("responsibility", responsibility);
            }
            if (context.get("repairHours") != null) {
                claim.set("repairHours", Double.valueOf(MfgSupport.toDouble(context.get("repairHours"), 0.0)));
            }
            String claimType = MfgSupport.toText(context.get("claimType"));
            if (claimType != null) {
                if (!MfgConstants.CLAIM_RTN.equals(claimType) && !MfgConstants.CLAIM_EXCH.equals(claimType)
                        && !MfgConstants.CLAIM_REPAIR.equals(claimType) && !MfgConstants.CLAIM_REFUND_ONLY.equals(claimType)) {
                    return ServiceUtil.returnError("claimType must be " + MfgConstants.CLAIM_RTN + ", " + MfgConstants.CLAIM_EXCH
                            + ", " + MfgConstants.CLAIM_REPAIR + " or " + MfgConstants.CLAIM_REFUND_ONLY);
                }
                claim.set("claimType", claimType);
            }
            String freightPayer = MfgSupport.toText(context.get("freightPayer"));
            if (freightPayer != null) {
                if (!MfgConstants.FREIGHT_SELLER.equals(freightPayer) && !MfgConstants.FREIGHT_BUYER.equals(freightPayer)) {
                    return ServiceUtil.returnError("freightPayer must be " + MfgConstants.FREIGHT_SELLER
                            + " or " + MfgConstants.FREIGHT_BUYER);
                }
                claim.set("freightPayer", freightPayer);
            }
            String inspectResult = MfgSupport.toText(context.get("inspectResult"));
            if (inspectResult != null) {
                if (!MfgConstants.INSPECT_QUALITY.equals(inspectResult) && !MfgConstants.INSPECT_NO_FAULT.equals(inspectResult)
                        && !MfgConstants.INSPECT_HUMAN.equals(inspectResult) && !MfgConstants.INSPECT_UNKNOWN.equals(inspectResult)) {
                    return ServiceUtil.returnError("inspectResult must be " + MfgConstants.INSPECT_QUALITY + ", "
                            + MfgConstants.INSPECT_NO_FAULT + ", " + MfgConstants.INSPECT_HUMAN + " or " + MfgConstants.INSPECT_UNKNOWN);
                }
                claim.set("inspectResult", inspectResult);
            }
            if (context.get("refundAmount") != null) {
                claim.set("refundAmount", Double.valueOf(MfgSupport.toDouble(context.get("refundAmount"), 0.0)));
            }
            if (context.get("deductAmount") != null) {
                claim.set("deductAmount", Double.valueOf(MfgSupport.toDouble(context.get("deductAmount"), 0.0)));
            }
            String returnTrackingNo = MfgSupport.toText(context.get("returnTrackingNo"));
            if (returnTrackingNo != null) {
                claim.set("returnTrackingNo", returnTrackingNo);
            }
            java.sql.Date receivedBackDate = (java.sql.Date) MfgSupport.parseDate(context.get("receivedBackDate"));
            if (receivedBackDate != null) {
                claim.set("receivedBackDate", receivedBackDate);
            }
            String vendorClaimStatus = MfgSupport.toText(context.get("vendorClaimStatus"));
            if (vendorClaimStatus != null) {
                claim.set("vendorClaimStatus", vendorClaimStatus);
            }
            java.sql.Date signedDate = (java.sql.Date) MfgSupport.parseDate(context.get("signedDate"));
            if (signedDate != null) {
                claim.set("signedDate", signedDate);
                claim.set("sevenDayDeadline", sevenDayDeadline(signedDate));
                claim.set("afterSaleDeadline", afterSaleDeadline(signedDate));
            }
            String note = MfgSupport.toText(context.get("note"));
            if (note != null) {
                claim.set("note", note);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("statusId", claim.getString("statusId"));
            result.put("claimType", claim.getString("claimType"));
            java.sql.Timestamp sevenDay = claim.getTimestamp("sevenDayDeadline");
            if (sevenDay != null) {
                result.put("sevenDayDeadline", sevenDay.toString());
            }
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not update the claim: " + e.getMessage());
        }
    }

    /**
     * Lists warranty claims, newest first.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimList}
     * @return the service result containing {@code claims}
     */
    public static Map<String, Object> claimList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String sn = MfgSupport.toText(context.get("sn"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        String claimType = MfgSupport.toText(context.get("claimType"));
        try {
            EntityQuery query = EntityQuery.use(delegator).from("MfgWarrantyClaim");
            if (sn != null) {
                query = query.where("sn", sn);
            }
            if (statusId != null) {
                query = query.where("statusId", statusId);
            }
            if (claimType != null) {
                query = query.where("claimType", claimType);
            }
            List<GenericValue> claims = query.orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(claims.size());
            long openCount = 0;
            long expiringCount = 0;
            long sevenDayCount = 0;
            for (GenericValue claim : claims) {
                Map<String, Object> map = MfgRecordMaps.toClaimMap(claim);
                list.add(map);
                boolean open = MfgConstants.RMA_OPEN.equals(claim.getString("statusId"));
                if (open) {
                    openCount++;
                    long sevenDay = MfgSupport.hoursLeft(claim.getTimestamp("sevenDayDeadline"));
                    long afterSale = MfgSupport.hoursLeft(claim.getTimestamp("afterSaleDeadline"));
                    if (sevenDay > 0) {
                        sevenDayCount++;
                    }
                    if (isExpiringSoon(sevenDay) || isExpiringSoon(afterSale)) {
                        expiringCount++;
                    }
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "claims",
                    MfgPaging.filterByKeyword(list, MfgSupport.toText(context.get("search")), "claimId", "sn", "tradeNo",
                            "buyerNick", "customerName", "issueDesc", "responsibility"),
                    MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), MfgSupport.toText(context.get("search")));
            result.put("claimCount", Integer.valueOf(list.size()));
            result.put("openCount", Long.valueOf(openCount));
            result.put("expiringCount", Long.valueOf(expiringCount));
            result.put("sevenDayCount", Long.valueOf(sevenDayCount));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the claims: " + e.getMessage());
        }
    }

    /**
     * Returns one claim with the context needed to judge it: the unit, its delivery and the parts
     * that were replaced on it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimDetail}
     * @return the service result containing {@code claim}, {@code snRecord}, {@code delivery} and {@code replacements}
     */
    public static Map<String, Object> claimDetail(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String claimId = MfgSupport.toText(context.get("claimId"));
        Map<String, Object> empty = ServiceUtil.returnSuccess();
        empty.put("replacements", new ArrayList<Map<String, Object>>());
        if (claimId == null) {
            return empty;
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return empty;
            }
            String sn = claim.getString("sn");
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            GenericValue delivery = null;
            String deliveryId = claim.getString("deliveryId");
            if (deliveryId != null) {
                delivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", deliveryId).queryOne();
            }
            List<GenericValue> replacements = EntityQuery.use(delegator).from("MfgReplacement")
                    .where("sn", sn).orderBy("createdStamp").queryList();
            List<GenericValue> defects = EntityQuery.use(delegator).from("MfgDefect")
                    .where("workOrderId", claim.getString("workOrderId")).queryList();
            GenericValue exchangeDelivery = null;
            String exchangeDeliveryId = claim.getString("exchangeDeliveryId");
            List<Map<String, Object>> exchangeUnits = new ArrayList<>();
            if (exchangeDeliveryId != null) {
                exchangeDelivery = EntityQuery.use(delegator).from("MfgDelivery")
                        .where("deliveryId", exchangeDeliveryId).queryOne();
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", exchangeDeliveryId).orderBy("sn").queryList();
                for (GenericValue item : items) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("sn", item.getString("sn"));
                    exchangeUnits.add(map);
                }
            }
            GenericValue repairOrder = null;
            String repairWorkOrderId = claim.getString("repairWorkOrderId");
            if (repairWorkOrderId != null) {
                repairOrder = EntityQuery.use(delegator).from("MfgWorkOrder")
                        .where("workOrderId", repairWorkOrderId).queryOne();
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claim", MfgRecordMaps.toClaimMap(claim));
            result.put("snRecord", unit == null ? null : MfgRecordMaps.toSnMap(unit));
            result.put("delivery", delivery == null ? null : MfgRecordMaps.toDeliveryMap(delivery));
            result.put("exchangeDelivery", exchangeDelivery == null ? null : MfgRecordMaps.toDeliveryMap(exchangeDelivery));
            result.put("exchangeUnits", exchangeUnits);
            result.put("repairOrder", repairOrder == null ? null : MfgRecordMaps.toWorkOrderMap(repairOrder));
            result.put("replacements", MfgRecordMaps.toReplacementMaps(replacements));
            result.put("defectCount", Integer.valueOf(defects.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the claim: " + e.getMessage());
        }
    }

    /**
     * Ships a replacement unit for a claim: a delivery order is created for a free unit of the same
     * model and the claim moves to {@code MFG_RMA_REPLACED}.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimExchange}
     * @return the service result containing {@code claimId}, {@code deliveryId} and {@code newSn}
     */
    public static Map<String, Object> claimExchange(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES, MfgConstants.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String claimId = MfgSupport.toText(context.get("claimId"));
        if (claimId == null) {
            return ServiceUtil.returnError("claimId is required");
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return ServiceUtil.returnError("Unknown claim [" + claimId + "]");
            }
            if (claim.getString("exchangeDeliveryId") != null) {
                return ServiceUtil.returnError("Claim [" + claimId + "] already has a replacement delivery ["
                        + claim.getString("exchangeDeliveryId") + "]");
            }
            String originalSn = claim.getString("sn");
            GenericValue returned = EntityQuery.use(delegator).from("MfgSn")
                    .where("sn", originalSn).queryOne();
            if (returned == null) {
                return ServiceUtil.returnError("Unknown serial number [" + originalSn + "]");
            }
            String productCode = returned.getString("productCode");
            String newSn = MfgSupport.toText(context.get("newSn"));
            GenericValue replacement = null;
            if (newSn != null) {
                replacement = EntityQuery.use(delegator).from("MfgSn").where("sn", newSn).queryOne();
                if (replacement == null) {
                    return ServiceUtil.returnError("Unknown serial number [" + newSn + "]");
                }
                if (!productCode.equals(replacement.getString("productCode"))) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] does not belong to model ["
                            + productCode + "]");
                }
                if (MfgConstants.SN_SCRAPPED.equals(replacement.getString("statusId"))) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] is scrapped");
                }
                if (MfgDeliveryServices.isDelivered(delegator, newSn)) {
                    return ServiceUtil.returnError("Serial number [" + newSn + "] has already been delivered");
                }
            } else {
                List<GenericValue> candidates = EntityQuery.use(delegator).from("MfgSn")
                        .where("productCode", productCode).orderBy("sn").queryList();
                for (GenericValue candidate : candidates) {
                    String sn = candidate.getString("sn");
                    if (sn.equals(originalSn) || MfgConstants.SN_SCRAPPED.equals(candidate.getString("statusId"))
                            || MfgDeliveryServices.isDelivered(delegator, sn)) {
                        continue;
                    }
                    replacement = candidate;
                    newSn = sn;
                    break;
                }
                if (replacement == null) {
                    return ServiceUtil.returnError("No available unit of model [" + productCode
                            + "] to exchange");
                }
            }

            LocalDate today = LocalDate.now();
            String deliveryId = MfgDeliveryServices.nextDeliveryId(delegator, today);
            GenericValue delivery = delegator.makeValue("MfgDelivery");
            delivery.set("deliveryId", deliveryId);
            delivery.set("customerName", claim.getString("customerName"));
            delivery.set("customerPo", claim.getString("tradeNo"));
            delivery.set("deliveryDate", java.sql.Date.valueOf(today));
            delivery.set("carrier", MfgSupport.toText(context.get("carrier")));
            delivery.set("trackingNo", MfgSupport.toText(context.get("trackingNo")));
            delivery.set("workOrderId", replacement.getString("workOrderId"));
            delivery.set("note", "换货 " + claimId + " 替换 " + originalSn);
            delegator.create(delivery);

            GenericValue item = delegator.makeValue("MfgDeliverySn");
            item.set("deliveryId", deliveryId);
            item.set("sn", newSn);
            delegator.create(item);
            replacement.set("statusId", MfgConstants.SN_SHIPPED);
            delegator.store(replacement);

            claim.set("exchangeDeliveryId", deliveryId);
            claim.set("statusId", MfgConstants.RMA_REPLACED);
            if (claim.getString("claimType") == null) {
                claim.set("claimType", MfgConstants.CLAIM_EXCH);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("deliveryId", deliveryId);
            result.put("newSn", newSn);
            result.put("statusId", MfgConstants.RMA_REPLACED);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not exchange the unit: " + e.getMessage());
        }
    }

    /**
     * Opens a repair work order for a claim so the rework is measured like any other build step.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgClaimRepair}
     * @return the service result containing {@code claimId} and {@code workOrderId}
     */
    public static Map<String, Object> claimRepair(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES, MfgConstants.PERM_PRODUCTION);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String claimId = MfgSupport.toText(context.get("claimId"));
        if (claimId == null) {
            return ServiceUtil.returnError("claimId is required");
        }
        try {
            GenericValue claim = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .where("claimId", claimId).queryOne();
            if (claim == null) {
                return ServiceUtil.returnError("Unknown claim [" + claimId + "]");
            }
            if (claim.getString("repairWorkOrderId") != null) {
                return ServiceUtil.returnError("Claim [" + claimId + "] already has repair work order ["
                        + claim.getString("repairWorkOrderId") + "]");
            }
            String sn = claim.getString("sn");
            GenericValue unit = EntityQuery.use(delegator).from("MfgSn").where("sn", sn).queryOne();
            if (unit == null) {
                return ServiceUtil.returnError("Unknown serial number [" + sn + "]");
            }

            LocalDate today = LocalDate.now();
            java.sql.Date dueDate = (java.sql.Date) MfgSupport.parseDate(context.get("dueDate"));
            String workOrderId = MfgWorkOrderServices.nextWorkOrderId(delegator, today);
            GenericValue order = delegator.makeValue("MfgWorkOrder");
            order.set("workOrderId", workOrderId);
            order.set("productCode", unit.getString("productCode"));
            order.set("plannedQty", Long.valueOf(1));
            order.set("statusId", MfgConstants.WO_CREATED);
            order.set("dueDate", dueDate == null ? java.sql.Date.valueOf(today.plusDays(3)) : dueDate);
            String customerName = claim.getString("customerName");
            order.set("customerName", "售后返修" + (customerName == null ? "" : " " + customerName));
            order.set("note", claimId + " 返修 " + sn);
            delegator.create(order);

            claim.set("repairWorkOrderId", workOrderId);
            if (claim.getString("claimType") == null) {
                claim.set("claimType", MfgConstants.CLAIM_REPAIR);
            }
            delegator.store(claim);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("claimId", claimId);
            result.put("workOrderId", workOrderId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not open the repair work order: " + e.getMessage());
        }
    }

    /**
     * End of the warranty: delivery date plus the product warranty months (12 when unset).
     *
     * @param deliveryDate the date the unit was shipped
     * @param warrantyMonths warranty length in months
     * @return the end date, or {@code null} when the unit was never delivered
     */
    static java.sql.Date warrantyEndDate(java.sql.Date deliveryDate, long warrantyMonths) {
        if (deliveryDate == null) {
            return null;
        }
        long months = warrantyMonths <= 0 ? 12 : warrantyMonths;
        return java.sql.Date.valueOf(deliveryDate.toLocalDate().plusMonths(months));
    }

    /**
     * Tells whether a claim that arrived on {@code checkDate} is still inside the warranty.
     *
     * @param endDate the warranty end date
     * @param checkDate the date to test
     * @return {@code true} when the claim is covered
     */
    static boolean isInWarranty(java.sql.Date endDate, java.sql.Date checkDate) {
        if (endDate == null || checkDate == null) {
            return false;
        }
        return !checkDate.after(endDate);
    }

    /**
     * End of the marketplace seven day no-reason return window: the buyer signed on day D, the
     * window is counted from midnight after that day and lasts 168 hours, so it ends at D + 8 days.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @return the deadline, or {@code null} when the unit was never signed for
     */
    static java.sql.Timestamp sevenDayDeadline(java.sql.Date signedDate) {
        if (signedDate == null) {
            return null;
        }
        LocalDateTime start = signedDate.toLocalDate().plusDays(1).atStartOfDay();
        return java.sql.Timestamp.valueOf(start.plusHours(MfgConstants.SEVEN_DAY_HOURS));
    }

    /**
     * End of the marketplace after sales window, counted from the signed date.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @return the deadline at the end of the last day, or {@code null} when never signed for
     */
    static java.sql.Timestamp afterSaleDeadline(java.sql.Date signedDate) {
        if (signedDate == null) {
            return null;
        }
        LocalDateTime end = signedDate.toLocalDate().plusDays(MfgConstants.AFTER_SALE_DAYS)
                .atTime(23, 59, 59);
        return java.sql.Timestamp.valueOf(end);
    }

    /**
     * Tells whether a unit that came back on {@code receivedDate} still sits inside the seven day
     * no-reason window: the whole day {@code signedDate + 7} is still covered.
     *
     * @param signedDate the date the buyer signed for the parcel
     * @param receivedDate the date the unit came back
     * @return {@code true} when the return is inside the window
     */
    static boolean isWithinSevenDays(java.sql.Date signedDate, java.sql.Date receivedDate) {
        if (signedDate == null || receivedDate == null) {
            return false;
        }
        return !receivedDate.toLocalDate().isAfter(signedDate.toLocalDate().plusDays(7));
    }

    /**
     * Tells whether a deadline is close enough to be flagged in the list.
     *
     * @param hours the hours left, as returned by {@link MfgSupport#hoursLeft(java.sql.Timestamp)}
     * @return {@code true} when the deadline is inside the warning window but not yet passed
     */
    static boolean isExpiringSoon(long hours) {
        return hours >= 0 && hours <= MfgConstants.DEADLINE_WARNING_HOURS;
    }

    /**
     * Freight payer that follows from the claim type when the operator did not choose one: a
     * quality return is on us, a no-reason return is on the buyer.
     *
     * @param claimType the claim type
     * @return either {@link MfgConstants#FREIGHT_SELLER} or {@link MfgConstants#FREIGHT_BUYER}
     */
    static String defaultFreightPayer(String claimType) {
        return MfgConstants.CLAIM_RTN.equals(claimType) || MfgConstants.CLAIM_EXCH.equals(claimType)
                ? MfgConstants.FREIGHT_BUYER : MfgConstants.FREIGHT_SELLER;
    }

    static String formatClaimId(LocalDate date, int sequence) {
        return "RMA-" + date.format(MfgConstants.DAY) + "-" + String.format(Locale.ROOT, "%02d", sequence);
    }

    static String nextClaimId(Delegator delegator, LocalDate today) throws GenericEntityException {
        String prefix = "RMA-" + today.format(MfgConstants.DAY) + "-";
        List<GenericValue> existing = EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList();
        int max = 0;
        for (GenericValue claim : existing) {
            String id = claim.getString("claimId");
            if (id != null && id.startsWith(prefix)) {
                max = Math.max(max, MfgSupport.parseTrailingNumber(id.substring(prefix.length())));
            }
        }
        return formatClaimId(today, max + 1);
    }

    /**
     * Saves or updates a buyer on the internal risk list. The list only adds a warning before
     * shipping; a legal after sales request is never refused because of it.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgBuyerFlagSave}
     * @return the service result containing {@code buyerKey}, {@code riskLevel} and {@code statusId}
     */
    public static Map<String, Object> buyerFlagSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        String channelId = MfgSupport.toText(context.get("channelId"));
        if (channelId == null) {
            channelId = MfgConstants.CHANNEL_TAOBAO;
        }
        String rawMobile = MfgSupport.toText(context.get("receiverMobile"));
        String mobile = normalizeMobile(rawMobile);
        if (rawMobile != null && mobile == null) {
            return ServiceUtil.returnError("receiverMobile must be a full eleven digit number; a masked "
                    + "number such as 138****2222 cannot identify a buyer");
        }
        String buyerNick = MfgSupport.toText(context.get("buyerNick"));
        String buyerKey = buyerKey(channelId, mobile, buyerNick);
        if (buyerKey == null) {
            return ServiceUtil.returnError("receiverMobile or buyerNick is required");
        }
        String riskLevel = MfgSupport.toText(context.get("riskLevel"));
        if (riskLevel == null) {
            riskLevel = MfgConstants.RISK_WATCH;
        } else if (!MfgConstants.RISK_WATCH.equals(riskLevel) && !MfgConstants.RISK_BLOCK.equals(riskLevel)) {
            return ServiceUtil.returnError("riskLevel must be " + MfgConstants.RISK_WATCH + " or " + MfgConstants.RISK_BLOCK);
        }
        String statusId = MfgSupport.toText(context.get("statusId"));
        if (statusId == null) {
            statusId = MfgConstants.FLAG_ACTIVE;
        } else if (!MfgConstants.FLAG_ACTIVE.equals(statusId) && !MfgConstants.FLAG_RELEASED.equals(statusId)) {
            return ServiceUtil.returnError("statusId must be " + MfgConstants.FLAG_ACTIVE + " or " + MfgConstants.FLAG_RELEASED);
        }
        String reasonCode = MfgSupport.toText(context.get("reasonCode"));
        if (reasonCode != null && !isKnownReason(reasonCode)) {
            return ServiceUtil.returnError("reasonCode must be one of FREQ_RETURN, EMPTY_BOX, SWAP, "
                    + "ABUSE_7DAY, FAKE_CLAIM or THREAT");
        }
        try {
            GenericValue flag = EntityQuery.use(delegator).from("MfgBuyerFlag")
                    .where("buyerKey", buyerKey).queryOne();
            if (flag == null && mobile != null) {
                // an older entry may have been keyed on the mobile: reuse it instead of duplicating
                List<GenericValue> candidates = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
                for (GenericValue candidate : candidates) {
                    if (mobile.equals(candidate.getString("receiverMobile"))) {
                        flag = candidate;
                        break;
                    }
                }
            }
            boolean create = flag == null;
            if (create) {
                flag = delegator.makeValue("MfgBuyerFlag");
                flag.set("buyerKey", buyerKey);
                flag.set("channelId", channelId);
            }
            if (buyerNick != null) {
                flag.set("buyerNick", buyerNick);
            }
            if (mobile != null) {
                flag.set("receiverMobile", mobile);
            }
            String receiverName = MfgSupport.toText(context.get("receiverName"));
            if (receiverName != null) {
                flag.set("receiverName", receiverName);
            }
            String addressText = MfgSupport.toText(context.get("addressText"));
            if (addressText != null) {
                flag.set("addressText", addressText);
            }
            if (reasonCode != null) {
                flag.set("reasonCode", reasonCode);
            }
            String evidenceNote = MfgSupport.toText(context.get("evidenceNote"));
            if (evidenceNote != null) {
                flag.set("evidenceNote", evidenceNote);
            }
            String note = MfgSupport.toText(context.get("note"));
            if (note != null) {
                flag.set("note", note);
            }
            flag.set("riskLevel", riskLevel);
            flag.set("statusId", statusId);
            flag.set("releasedDate", MfgConstants.FLAG_RELEASED.equals(statusId)
                    ? java.sql.Date.valueOf(LocalDate.now()) : null);
            java.sql.Date expiresDate = (java.sql.Date) MfgSupport.parseDate(context.get("expiresDate"));
            if (expiresDate == null && create) {
                expiresDate = java.sql.Date.valueOf(LocalDate.now().plusMonths(MfgConstants.RISK_VALID_MONTHS));
            }
            if (expiresDate != null) {
                flag.set("expiresDate", expiresDate);
            }
            if (create) {
                delegator.create(flag);
            } else {
                delegator.store(flag);
            }

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("buyerKey", buyerKey);
            result.put("riskLevel", riskLevel);
            result.put("statusId", statusId);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the buyer flag: " + e.getMessage());
        }
    }

    /**
     * Lists the risk list, newest first, with the mobile number masked.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgBuyerFlagList}
     * @return the service result containing {@code flags}, {@code flagCount} and {@code blockCount}
     */
    public static Map<String, Object> buyerFlagList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String keyword = MfgSupport.toText(context.get("keyword"));
        String riskLevel = MfgSupport.toText(context.get("riskLevel"));
        String statusId = MfgSupport.toText(context.get("statusId"));
        try {
            List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag")
                    .orderBy("-createdStamp").queryList();
            List<Map<String, Object>> list = new ArrayList<>(flags.size());
            long blockCount = 0;
            for (GenericValue flag : flags) {
                if (riskLevel != null && !riskLevel.equals(flag.getString("riskLevel"))) {
                    continue;
                }
                if (statusId != null && !statusId.equals(flag.getString("statusId"))) {
                    continue;
                }
                Map<String, Object> map = toBuyerFlagMap(flag);
                if (keyword != null) {
                    String haystack = String.valueOf(map.get("buyerKey")) + " "
                            + String.valueOf(map.get("buyerNick")) + " "
                            + String.valueOf(map.get("receiverMobile"));
                    if (!haystack.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                        continue;
                    }
                }
                list.add(map);
                if (MfgConstants.RISK_BLOCK.equals(flag.getString("riskLevel"))
                        && isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"),
                                java.sql.Date.valueOf(LocalDate.now()))) {
                    blockCount++;
                }
            }
            Map<String, Object> result = ServiceUtil.returnSuccess();
            MfgPaging.putPage(result, "flags", list, MfgPaging.pageOf(context), MfgPaging.pageSizeOf(context), keyword);
            result.put("flagCount", Integer.valueOf(list.size()));
            result.put("blockCount", Long.valueOf(blockCount));
            result.put("keyword", keyword);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the buyer flags: " + e.getMessage());
        }
    }

    /**
     * Looks a buyer up on the risk list by mobile number or nickname.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgBuyerFlagCheck}
     * @return the service result containing {@code hit}, {@code riskLevel} and {@code flag}
     */
    public static Map<String, Object> buyerFlagCheck(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String mobile = normalizeMobile(MfgSupport.toText(context.get("receiverMobile")));
        String buyerNick = MfgSupport.toText(context.get("buyerNick"));
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("hit", "N");
        result.put("flags", new ArrayList<Map<String, Object>>());
        if (mobile == null && buyerNick == null) {
            return result;
        }
        try {
            List<Map<String, Object>> hits = new ArrayList<>();
            String bestLevel = null;
            List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
            java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
            for (GenericValue flag : flags) {
                if (!isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                    continue;
                }
                boolean byMobile = mobile != null && mobile.equals(flag.getString("receiverMobile"));
                boolean byNick = buyerNick != null && buyerNick.equals(flag.getString("buyerNick"));
                if (!byMobile && !byNick) {
                    continue;
                }
                hits.add(toBuyerFlagMap(flag));
                String level = flag.getString("riskLevel");
                if (MfgConstants.RISK_BLOCK.equals(level)) {
                    bestLevel = MfgConstants.RISK_BLOCK;
                } else if (bestLevel == null) {
                    bestLevel = level;
                }
            }
            result.put("hit", hits.isEmpty() ? "N" : "Y");
            result.put("flags", hits);
            if (bestLevel != null) {
                result.put("riskLevel", bestLevel);
            }
            result.put("buyerNick", buyerNick);
            result.put("receiverMobile", mobile);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not check the buyer: " + e.getMessage());
        }
    }

    /**
     * Aggregates the after sales history per buyer so the operator can spot a pattern before it
     * becomes a problem. Nothing is flagged automatically.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgBuyerRiskList}
     * @return the service result containing {@code buyers} and {@code buyerCount}
     */
    public static Map<String, Object> buyerRiskList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        try {
            List<GenericValue> claims = EntityQuery.use(delegator).from("MfgWarrantyClaim")
                    .orderBy("createdStamp").queryList();
            Map<String, Map<String, Object>> byNick = new LinkedHashMap<>();
            for (GenericValue claim : claims) {
                String nick = claim.getString("buyerNick");
                if (nick == null) {
                    continue;
                }
                Map<String, Object> row = byNick.get(nick);
                if (row == null) {
                    row = new LinkedHashMap<>();
                    row.put("buyerNick", nick);
                    row.put("claimCount", Integer.valueOf(0));
                    row.put("refundTotal", Double.valueOf(0.0));
                    row.put("blockCount", Integer.valueOf(0));
                    byNick.put(nick, row);
                }
                row.put("claimCount", Integer.valueOf(((Integer) row.get("claimCount")).intValue() + 1));
                double refund = claim.getDouble("refundAmount") == null ? 0.0
                        : claim.getDouble("refundAmount").doubleValue();
                row.put("refundTotal", Double.valueOf(((Double) row.get("refundTotal")).doubleValue() + refund));
                if (MfgConstants.RISK_BLOCK.equals(claim.getString("buyerRiskLevel"))) {
                    row.put("blockCount", Integer.valueOf(((Integer) row.get("blockCount")).intValue() + 1));
                }
                row.put("lastClaimDate", claim.getDate("receivedDate"));
            }
            List<Map<String, Object>> buyers = new ArrayList<>(byNick.values());
            for (Map<String, Object> row : buyers) {
                int count = ((Integer) row.get("claimCount")).intValue();
                double refund = ((Double) row.get("refundTotal")).doubleValue();
                row.put("suggestedRiskLevel", suggestRiskLevel(count, refund));
                row.put("flagged", hasActiveFlag(delegator, (String) row.get("buyerNick")) ? "Y" : "N");
            }
            buyers.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Integer) right.get("claimCount")).compareTo((Integer) left.get("claimCount"));
                }
            });
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("buyers", buyers);
            result.put("buyerCount", Integer.valueOf(buyers.size()));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not read the buyer history: " + e.getMessage());
        }
    }

    /**
     * Normalizes a mobile number to its digits, so formatting differences do not break matching.
     * Masked numbers such as {@code 138****2222} are rejected: they cannot identify a buyer and
     * would make different buyers collide on the same key.
     *
     * @param mobile the raw number
     * @return the eleven digits, or {@code null} when the number is masked or incomplete
     */
    static String normalizeMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        if (mobile.indexOf('*') >= 0 || mobile.indexOf('x') >= 0 || mobile.indexOf('X') >= 0
                || mobile.indexOf('＊') >= 0) {
            return null;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < mobile.length(); i++) {
            char c = mobile.charAt(i);
            if (c >= '0' && c <= '9') {
                digits.append(c);
            }
        }
        return digits.length() == 11 ? digits.toString() : null;
    }

    /**
     * Builds the matching key of a buyer. The marketplace nickname comes first because it is
always
     * available on an order, while the mobile number is often masked or replaced by a privacy
     * number; the mobile is only used when no nickname was given.
     *
     * @param channelId the marketplace, {@code TAOBAO} today
     * @param mobile the receiver mobile, may be {@code null} or masked
     * @param buyerNick the marketplace nickname, may be {@code null}
     * @return the key, or {@code null} when neither is usable
     */
    static String buyerKey(String channelId, String mobile, String buyerNick) {
        String prefix = MfgConstants.CHANNEL_TAOBAO.equals(channelId) || channelId == null ? "TB" : channelId;
        if (buyerNick != null && !buyerNick.trim().isEmpty()) {
            return prefix + ":N:" + buyerNick.trim().toLowerCase(Locale.ROOT);
        }
        String digits = normalizeMobile(mobile);
        return digits == null ? null : prefix + ":" + digits;
    }

    /**
     * Masks a mobile number so it can be shown on screen without exposing it.
     *
     * @param mobile the number
     * @return {@code 138****2222}, or the input when it is too short
     */
    static String maskMobile(String mobile) {
        String digits = normalizeMobile(mobile);
        if (digits == null) {
            return mobile == null ? "" : mobile;
        }
        if (digits.length() < 11) {
            return digits.substring(0, 1) + "****" + digits.substring(digits.length() - 1);
        }
        return digits.substring(0, 3) + "****" + digits.substring(digits.length() - 4);
    }

    /**
     * Tells whether a risk list entry still applies.
     *
     * @param statusId the entry status
     * @return {@code true} only when the entry is active
     */
    static boolean isFlagActive(String statusId) {
        return MfgConstants.FLAG_ACTIVE.equals(statusId);
    }

    /**
     * Tells whether a risk list entry applies today: active and not expired.
     *
     * @param statusId the entry status
     * @param expiresDate the date the entry stops applying, may be {@code null} for no limit
     * @param today today
     * @return {@code true} only when the entry is active and not yet expired
     */
    static boolean isFlagLive(String statusId, java.sql.Date expiresDate, java.sql.Date today) {
        if (!isFlagActive(statusId)) {
            return false;
        }
        if (expiresDate == null || today == null) {
            return true;
        }
        return !today.after(expiresDate);
    }

    /**
     * Suggests a risk level from the after sales history; the operator still decides.
     *
     * @param claimCount number of claims of the buyer
     * @param refundTotal amount refunded to the buyer
     * @return {@link MfgConstants#RISK_BLOCK}, {@link MfgConstants#RISK_WATCH} or {@code null} when nothing stands
     *         out
     */
    static String suggestRiskLevel(int claimCount, double refundTotal) {
        if (claimCount >= MfgConstants.RISK_BLOCK_CLAIMS || refundTotal >= MfgConstants.RISK_BLOCK_REFUND) {
            return MfgConstants.RISK_BLOCK;
        }
        if (claimCount >= MfgConstants.RISK_WATCH_CLAIMS || refundTotal >= MfgConstants.RISK_WATCH_REFUND) {
            return MfgConstants.RISK_WATCH;
        }
        return null;
    }

    static boolean isKnownReason(String reasonCode) {
        return "FREQ_RETURN".equals(reasonCode) || "EMPTY_BOX".equals(reasonCode)
                || "SWAP".equals(reasonCode) || "ABUSE_7DAY".equals(reasonCode)
                || "FAKE_CLAIM".equals(reasonCode) || "THREAT".equals(reasonCode);
    }

    static boolean hasActiveFlag(Delegator delegator, String buyerNick) throws GenericEntityException {
        if (buyerNick == null) {
            return false;
        }
        List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        for (GenericValue flag : flags) {
            if (buyerNick.equals(flag.getString("buyerNick"))
                    && isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                return true;
            }
        }
        return false;
    }

    static String activeRiskLevel(Delegator delegator, String buyerNick)
            throws GenericEntityException {
        if (buyerNick == null) {
            return null;
        }
        String level = null;
        List<GenericValue> flags = EntityQuery.use(delegator).from("MfgBuyerFlag").queryList();
        java.sql.Date today = java.sql.Date.valueOf(LocalDate.now());
        for (GenericValue flag : flags) {
            if (!buyerNick.equals(flag.getString("buyerNick"))
                    || !isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"), today)) {
                continue;
            }
            if (MfgConstants.RISK_BLOCK.equals(flag.getString("riskLevel"))) {
                return MfgConstants.RISK_BLOCK;
            }
            level = flag.getString("riskLevel");
        }
        return level;
    }

    static Map<String, Object> toBuyerFlagMap(GenericValue flag) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("buyerKey", flag.getString("buyerKey"));
        map.put("channelId", flag.getString("channelId"));
        map.put("buyerNick", flag.getString("buyerNick"));
        map.put("receiverName", flag.getString("receiverName"));
        map.put("receiverMobile", flag.getString("receiverMobile"));
        map.put("maskedMobile", maskMobile(flag.getString("receiverMobile")));
        map.put("addressText", flag.getString("addressText"));
        map.put("riskLevel", flag.getString("riskLevel"));
        map.put("reasonCode", flag.getString("reasonCode"));
        map.put("evidenceNote", flag.getString("evidenceNote"));
        map.put("statusId", flag.getString("statusId"));
        map.put("releasedDate", flag.getDate("releasedDate"));
        map.put("expiresDate", flag.getDate("expiresDate"));
        map.put("live", isFlagLive(flag.getString("statusId"), flag.getDate("expiresDate"),
                java.sql.Date.valueOf(LocalDate.now())) ? "Y" : "N");
        map.put("note", flag.getString("note"));
        map.put("createdStamp", flag.getTimestamp("createdStamp"));
        return map;
    }

    /**
     * Builds the after sales quality report: return, exchange and repair rates, the money they
     * cost, and the breakdowns that tell where the money goes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/claim-services.xml#mfgQualityReport}
     * @return the service result with the rates, the totals and the breakdown lists
     */
    public static Map<String, Object> qualityReport(DispatchContext dctx, Map<String, ? extends Object> context) {
        // the report is built around the after sales cost rate, so it belongs to the cost area
        String denied = MfgSecurity.permissionProblem(dctx, context, MfgConstants.PERM_COST);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        Delegator delegator = dctx.getDelegator();
        java.sql.Date toDate = (java.sql.Date) MfgSupport.parseDate(context.get("toDate"));
        if (toDate == null) {
            toDate = java.sql.Date.valueOf(LocalDate.now());
        }
        java.sql.Date fromDate = (java.sql.Date) MfgSupport.parseDate(context.get("fromDate"));
        if (fromDate == null) {
            fromDate = java.sql.Date.valueOf(toDate.toLocalDate().minusDays(89));
        }
        try {
            Map<String, GenericValue> products = new LinkedHashMap<>();
            for (GenericValue product : EntityQuery.use(delegator).from("MfgProduct").queryList()) {
                products.put(product.getString("productCode"), product);
            }
            Map<String, String> snToProduct = new LinkedHashMap<>();
            for (GenericValue unit : EntityQuery.use(delegator).from("MfgSn").queryList()) {
                snToProduct.put(unit.getString("sn"), unit.getString("productCode"));
            }

            // units actually sold in the period: replacement deliveries are not a sale
            Map<String, Map<String, Object>> byProduct = new LinkedHashMap<>();
            long shippedUnits = 0;
            long deliveries = 0;
            double salesTotal = 0.0;
            for (GenericValue delivery : EntityQuery.use(delegator).from("MfgDelivery").queryList()) {
                java.sql.Date deliveryDate = delivery.getDate("deliveryDate");
                if (deliveryDate == null || deliveryDate.before(fromDate) || deliveryDate.after(toDate)) {
                    continue;
                }
                String note = delivery.getString("note");
                if (note != null && note.startsWith("换货")) {
                    continue;
                }
                List<GenericValue> items = EntityQuery.use(delegator).from("MfgDeliverySn")
                        .where("deliveryId", delivery.getString("deliveryId")).queryList();
                boolean counted = false;
                for (GenericValue item : items) {
                    String productCode = snToProduct.get(item.getString("sn"));
                    if (productCode == null) {
                        continue;
                    }
                    Map<String, Object> row = productRow(byProduct, productCode);
                    row.put("shippedUnits", Long.valueOf(((Long) row.get("shippedUnits")).longValue() + 1));
                    shippedUnits++;
                    counted = true;
                }
                if (counted) {
                    deliveries++;
                }
            }
            for (Map.Entry<String, Map<String, Object>> entry : byProduct.entrySet()) {
                GenericValue product = products.get(entry.getKey());
                double price = product == null || product.getDouble("salePrice") == null
                        ? 0.0 : product.getDouble("salePrice").doubleValue();
                double rowSales = ((Long) entry.getValue().get("shippedUnits")).longValue() * price;
                entry.getValue().put("salePrice", Double.valueOf(price));
                entry.getValue().put("sales", Double.valueOf(rowSales));
                salesTotal += rowSales;
            }

            long claimCount = 0;
            long rtnCount = 0;
            long exchCount = 0;
            long repairCount = 0;
            long refundOnlyCount = 0;
            long claimsWithin30 = 0;
            double refundTotal = 0.0;
            double freightTotal = 0.0;
            double hoursTotal = 0.0;
            double costTotal = 0.0;
            Map<String, Map<String, Object>> byResponsibility = new LinkedHashMap<>();
            Map<String, Long> byAge = new LinkedHashMap<>();
            Map<String, Long> byInspect = new LinkedHashMap<>();
            Map<String, Long> claimsPerSn = new LinkedHashMap<>();
            Map<String, Long> claimsPerLot = new LinkedHashMap<>();
            Map<String, GenericValue> lots = new LinkedHashMap<>();
            Map<String, List<String>> lotsOfWorkOrder = new LinkedHashMap<>();

            for (GenericValue claim : EntityQuery.use(delegator).from("MfgWarrantyClaim").queryList()) {
                java.sql.Date receivedDate = claim.getDate("receivedDate");
                if (receivedDate == null || receivedDate.before(fromDate) || receivedDate.after(toDate)) {
                    continue;
                }
                claimCount++;
                String claimType = claim.getString("claimType");
                if (MfgConstants.CLAIM_EXCH.equals(claimType)) {
                    exchCount++;
                } else if (MfgConstants.CLAIM_REPAIR.equals(claimType)) {
                    repairCount++;
                } else if (MfgConstants.CLAIM_REFUND_ONLY.equals(claimType)) {
                    refundOnlyCount++;
                } else {
                    rtnCount++;
                }

                double refund = claim.getDouble("refundAmount") == null
                        ? 0.0 : claim.getDouble("refundAmount").doubleValue();
                double hours = claim.getDouble("repairHours") == null
                        ? 0.0 : claim.getDouble("repairHours").doubleValue();
                double freight = MfgConstants.FREIGHT_SELLER.equals(claim.getString("freightPayer"))
                        ? MfgConstants.QUALITY_FREIGHT_COST : 0.0;
                double cost = MfgReportServices.claimCost(refund, freight, hours, MfgConstants.QUALITY_HOURLY_RATE);
                refundTotal += refund;
                freightTotal += freight;
                hoursTotal += hours;
                costTotal += cost;

                int days = MfgSupport.daysBetween(claim.getDate("deliveryDate"), receivedDate);
                if (days >= 0 && days <= 30) {
                    claimsWithin30++;
                }

                String sn = claim.getString("sn");
                String productCode = snToProduct.get(sn);
                if (productCode != null) {
                    Map<String, Object> row = productRow(byProduct, productCode);
                    row.put("claimCount", Long.valueOf(((Long) row.get("claimCount")).longValue() + 1));
                    row.put("refundTotal", Double.valueOf(sumOf(row, "refundTotal") + refund));
                    row.put("repairHours", Double.valueOf(sumOf(row, "repairHours") + hours));
                    row.put("cost", Double.valueOf(sumOf(row, "cost") + cost));
                }

                String responsibility = claim.getString("responsibility");
                Map<String, Object> respRow = productRow(byResponsibility,
                        responsibility == null ? "-" : responsibility);
                respRow.put("claimCount", Long.valueOf(((Long) respRow.get("claimCount")).longValue() + 1));
                respRow.put("refundTotal", Double.valueOf(sumOf(respRow, "refundTotal") + refund));
                respRow.put("repairHours", Double.valueOf(sumOf(respRow, "repairHours") + hours));
                respRow.put("cost", Double.valueOf(sumOf(respRow, "cost") + cost));

                bump(byAge, MfgSupport.ageBucket(days));
                bump(byInspect, claim.getString("inspectResult") == null
                        ? "UNKNOWN" : claim.getString("inspectResult"));
                bump(claimsPerSn, sn);
                for (String lotId : lotsOf(delegator, claim.getString("workOrderId"), lotsOfWorkOrder)) {
                    bump(claimsPerLot, lotId);
                    if (!lots.containsKey(lotId)) {
                        GenericValue lot = EntityQuery.use(delegator).from("MfgMaterialLot")
                                .where("lotId", lotId).queryOne();
                        if (lot != null) {
                            lots.put(lotId, lot);
                        }
                    }
                }
            }

            List<Map<String, Object>> productRows = new ArrayList<>();
            for (Map<String, Object> row : byProduct.values()) {
                long shipped = ((Long) row.get("shippedUnits")).longValue();
                long claims = ((Long) row.get("claimCount")).longValue();
                row.put("claimRate", MfgSupport.percent(claims, shipped));
                row.put("costRate", MfgSupport.percentOf(sumOf(row, "cost"), sumOf(row, "sales")));
                productRows.add(row);
            }
            productRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            List<Map<String, Object>> responsibilityRows = new ArrayList<>(byResponsibility.values());
            responsibilityRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            double averageClaimsPerLot = claimsPerLot.isEmpty() ? 0.0
                    : (double) claimCount / (double) claimsPerLot.size();
            List<Map<String, Object>> lotRows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : claimsPerLot.entrySet()) {
                GenericValue lot = lots.get(entry.getKey());
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("lotId", entry.getKey());
                row.put("componentCode", lot == null ? null : lot.getString("componentCode"));
                row.put("supplierName", lot == null ? null : lot.getString("supplierName"));
                row.put("supplierLot", lot == null ? null : lot.getString("supplierLot"));
                row.put("claimCount", entry.getValue());
                row.put("average", Double.valueOf(averageClaimsPerLot));
                row.put("suspicious", entry.getValue().longValue() >= MfgConstants.QUALITY_LOT_FACTOR * averageClaimsPerLot
                        && entry.getValue().longValue() > 1 ? "Y" : "N");
                lotRows.add(row);
            }
            lotRows.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> left, Map<String, Object> right) {
                    return ((Long) right.get("claimCount")).compareTo((Long) left.get("claimCount"));
                }
            });

            long repeatedSnCount = 0;
            for (Long times : claimsPerSn.values()) {
                if (times.longValue() > 1) {
                    repeatedSnCount++;
                }
            }

            List<Map<String, Object>> ageRows = new ArrayList<>();
            for (String bucket : new String[] {"0-30", "31-90", "91-180", ">180", "未知"}) {
                Long value = byAge.get(bucket);
                if (value == null || value.longValue() == 0) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("bucket", bucket);
                row.put("claimCount", value);
                ageRows.add(row);
            }
            List<Map<String, Object>> inspectRows = new ArrayList<>();
            for (Map.Entry<String, Long> entry : byInspect.entrySet()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("inspectResult", entry.getKey());
                row.put("claimCount", entry.getValue());
                inspectRows.add(row);
            }

            String costRate = MfgSupport.percentOf(costTotal, salesTotal);
            String repairRate30 = MfgSupport.percent(claimsWithin30, shippedUnits);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("fromDate", fromDate);
            result.put("toDate", toDate);
            result.put("deliveries", Long.valueOf(deliveries));
            result.put("shippedUnits", Long.valueOf(shippedUnits));
            result.put("claimCount", Long.valueOf(claimCount));
            result.put("rtnCount", Long.valueOf(rtnCount));
            result.put("exchCount", Long.valueOf(exchCount));
            result.put("repairCount", Long.valueOf(repairCount));
            result.put("refundOnlyCount", Long.valueOf(refundOnlyCount));
            result.put("claimsWithin30", Long.valueOf(claimsWithin30));
            result.put("repeatedSnCount", Long.valueOf(repeatedSnCount));
            result.put("returnRate", MfgSupport.percent(rtnCount, shippedUnits));
            result.put("exchangeRate", MfgSupport.percent(exchCount, shippedUnits));
            result.put("repairRate", MfgSupport.percent(repairCount, shippedUnits));
            result.put("claimRate", MfgSupport.percent(claimCount, shippedUnits));
            result.put("repairRate30", repairRate30);
            result.put("refundTotal", Double.valueOf(refundTotal));
            result.put("freightTotal", Double.valueOf(freightTotal));
            result.put("hoursTotal", Double.valueOf(hoursTotal));
            result.put("costTotal", Double.valueOf(costTotal));
            result.put("costPerUnit", Double.valueOf(shippedUnits == 0 ? 0.0 : costTotal / shippedUnits));
            result.put("salesTotal", Double.valueOf(salesTotal));
            result.put("costRate", costRate);
            result.put("hourlyRate", Double.valueOf(MfgConstants.QUALITY_HOURLY_RATE));
            result.put("freightPerClaim", Double.valueOf(MfgConstants.QUALITY_FREIGHT_COST));
            result.put("repairRateLimit", Double.valueOf(MfgConstants.QUALITY_REPAIR_RATE_LIMIT));
            result.put("costRateLimit", Double.valueOf(MfgConstants.QUALITY_COST_RATE_LIMIT));
            result.put("lotFactor", Double.valueOf(MfgConstants.QUALITY_LOT_FACTOR));
            result.put("overRepairLimit", isOverLimit(claimsWithin30, shippedUnits,
                    MfgConstants.QUALITY_REPAIR_RATE_LIMIT) ? "Y" : "N");
            result.put("overCostLimit", isOverLimit(costTotal, salesTotal,
                    MfgConstants.QUALITY_COST_RATE_LIMIT) ? "Y" : "N");
            result.put("products", productRows);
            result.put("responsibilities", responsibilityRows);
            result.put("lots", lotRows);
            result.put("ages", ageRows);
            result.put("inspections", inspectRows);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not build the quality report: " + e.getMessage());
        }
    }

    /**
     * Tells whether a measured ratio passed a red line.
     *
     * @param part the numerator
     * @param whole the denominator
     * @param limit the red line in percent
     * @return {@code true} when the ratio is above the limit
     */
    static boolean isOverLimit(double part, double whole, double limit) {
        if (whole <= 0.0) {
            return false;
        }
        return part * 100.0 / whole > limit;
    }

    static Map<String, Object> productRow(Map<String, Map<String, Object>> rows, String key) {
        Map<String, Object> row = rows.get(key);
        if (row == null) {
            row = new LinkedHashMap<>();
            row.put("productCode", key);
            row.put("shippedUnits", Long.valueOf(0));
            row.put("claimCount", Long.valueOf(0));
            row.put("refundTotal", Double.valueOf(0.0));
            row.put("repairHours", Double.valueOf(0.0));
            row.put("cost", Double.valueOf(0.0));
            row.put("sales", Double.valueOf(0.0));
            rows.put(key, row);
        }
        return row;
    }

    static double sumOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? 0.0 : ((Double) value).doubleValue();
    }

    static void bump(Map<String, Long> counters, String key) {
        if (key == null) {
            return;
        }
        Long current = counters.get(key);
        counters.put(key, Long.valueOf(current == null ? 1 : current.longValue() + 1));
    }

    static List<String> lotsOf(Delegator delegator, String workOrderId,
            Map<String, List<String>> cache) throws GenericEntityException {
        if (workOrderId == null) {
            return new ArrayList<>();
        }
        List<String> cached = cache.get(workOrderId);
        if (cached != null) {
            return cached;
        }
        List<String> lotIds = new ArrayList<>();
        for (GenericValue issue : EntityQuery.use(delegator).from("MfgMaterialIssue")
                .where("workOrderId", workOrderId).queryList()) {
            String lotId = issue.getString("lotId");
            if (lotId != null && !lotIds.contains(lotId)) {
                lotIds.add(lotId);
            }
        }
        cache.put(workOrderId, lotIds);
        return lotIds;
    }
}
