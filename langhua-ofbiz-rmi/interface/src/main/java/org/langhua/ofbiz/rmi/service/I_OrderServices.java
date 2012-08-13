/*
 * This library is part of OFBiz-RMI Component of Langhua
 *
 * Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/rmi/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.rmi.service;

import java.util.Map;

import org.ofbiz.service.DispatchContext;

/**
 * Interface of RMI Order Services which will be an counterpart of
 * org.ofbiz.order.*.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_OrderServices {

	/************************************************************
	 *  FinAccount services begin
	 ************************************************************/
	
	/**
	 * Get available balance of a finance account. This method calls
	 * {@link org.ofbiz.order.finaccount.FinAccountHelper#getAvailableBalance(String, java.sql.Timestamp, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.finAccountId String
     * @param context.authorizationDate Timestamp
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: BigDecimal if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> getAvailableBalance(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Get net balance of a finance account. This method calls
	 * {@link org.ofbiz.order.finaccount.FinAccountHelper#getBalance(String, java.sql.Timestamp, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.finAccountId String
     * @param context.authorizationDate Timestamp
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: BigDecimal if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> getBalance(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Gets the first (and should be only) FinAccount based on finAccountCode, which will be cleaned up to be only uppercase and alphanumeric
	 * This method calls
	 * {@link org.ofbiz.order.finaccount.FinAccountHelper#getFinAccountFromCode(String, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.finAccountCode String
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> getFinAccountFromCode(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Validates a FinAccount's PIN number. This method calls
	 * {@link org.ofbiz.order.finaccount.FinAccountHelper#getFinAccountFromCode(String, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.finAccountId String
     * @param context.pinNumber String
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Boolean if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> validatePin(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  FinAccount services end
	 ************************************************************/

	/************************************************************
	 *  Order services begin
	 ************************************************************/
	
	/**
     * Service for creating a new order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.locale Locale
     * @param context.userLogin GenericValue
     * @param context.orderTypeId String
     * @param context.partyId String
     * @param context.billFromVendorPartyId String
     * @param context.productStoreId String
     * @param context.orderItems List
     * @param context.orderAdjustments List
     * @param context.orderItemShipGroupInfo List
     * @param context.orderItemPriceInfos List
     * @param context.workEfforts List
     * @param context.orderId String
     * @param context.billingAccountId String
     * @param context.orderDate Timestamp
     * @param context.orderName String
     * @param context.salesChannelEnumId String
     * @param context.currencyUom String
     * @param context.firstAttemptOrderId String
     * @param context.grandTotal BigDecimal
     * @param context.visitId String
     * @param context.internalCode String
     * @param context.externalId String
     * @param context.originFacilityId String
     * @param context.transactionId String
     * @param context.terminalId String
     * @param context.autoOrderShoppingListId String
     * @param context.orderItemGroups List
     * @param context.orderAttributes List
     * @param context.orderItemAttributes List
     * @param context.orderInternalNotes List
     * @param context.orderNotes List
     * @param context.orderContactMechs List
     * @param context.orderItemContactMechs List
     * @param context.orderAdditionalPartyRoleMap Map
     * @param context.orderItemSurveyResponses List
     * @param context.orderItemAssociations List
     * @param context.orderProductPromoCodes Set
     * @param context.affiliateId String
     * @param context.distributorId String
     * @param context.orderPaymentInfo List
     * @param context.trackingCodeOrders List
     * @param context.orderTerms List
     * @param context.workEffortId String
     * @param context.placingCustomerPartyId String
     * @param context.billToCustomerPartyId String
     * @param context.billFromVendorPartyId String
     * @param context.shipToCustomerPartyId String
     * @param context.endUserCustomerPartyId String
     * @param context.shipFromVendorPartyId String
     * @param context.supplierAgentPartyId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  orderTypeId: String if success
	 *  statusId: String if success
	 *  orderId: String if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> storeOrder(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Generic method for creating an order from a shopping cart. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createOrderFromShoppingCart(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.userLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  shoppingCart: ShoppingCart if success
	 *  orderId: String if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> createOrderFromShoppingCart(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Generic method for creating an order from a shopping cart. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createOrderNote(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.workEffortId String
     * @param context.note String
     * @param context.noteName String
     * @param context.orderId String
     * @param context.internalNote String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> createOrderNote(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to create a payment using an order payment preference. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createPaymentFromPreference(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderPaymentPreferenceId String
     * @param context.paymentRefNum String
     * @param context.paymentFromId String
     * @param context.comments String
     *  
	 * @return the followings in return Map:
	 *  paymentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> createPaymentFromPreference(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to create an order payment preference. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createPaymentPreference(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.paymentMethodTypeId String
     * @param context.paymentMethodId String
     * @param context.maxAmount BigDecimal
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  orderPaymentPreferenceId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> createOrderPaymentPreference(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to create simple non-product order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#createSimpleNonProductSalesOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.paymentMethodId String
     * @param context.productStoreId String
     * @param context.currency String
     * @param context.partyId String
     * @param context.itemMap Map
     *  
	 * @return the followings in return Map:
	 *  orderId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> createSimpleNonProductSalesOrder(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Digital product has a relation with Content by ProductContent, find the Content entity related to
     * a product and run the service defined in serviceName field. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#fulfillDigitalItems(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItems List
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> fulfillDigitalItems(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to get an order contact mech. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#getOrderAddress(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     *  
	 * @return the followings in return Map:
	 *  orderHeader: GenericValue if success
	 *  billingAddress: GenericValue if success
	 *  shippingAddress: GenericValue if success
	 *  orderId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> getOrderAddress(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to get order header information as standard results.
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  Field names and values of OrderHeader entity if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> getOrderHeaderInformationById(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Determines the total amount invoiced for a given order item over all invoices by totalling the item subtotal (via OrderItemBilling),
     *  any adjustments for that item (via OrderAdjustmentBilling), and the item's share of any order-level adjustments (that calculated
     *  by applying the percentage of the items total that the item represents to the order-level adjustments total (also via
     *  OrderAdjustmentBilling). Also returns the quantity invoiced for the item over all invoices, to aid in prorating.
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#getOrderItemInvoicedAmountAndQuantity(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.orderItemSeqId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  invoicedAmount: BigDecimal if success
	 *  invoicedQuantity: BigDecimal if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> getOrderItemInvoicedAmountAndQuantity(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to get the total shipping for an order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#getOrderShippingAmount(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  shippingAmount: BigDecimal if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> getOrderShippingAmount(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to load a shipping cart for an order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#loadCartForUpdate(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  shoppingCart: GenericValue if success
	 *  orderId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> loadCartForUpdate(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to change orders' status. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#massChangeOrderStatus(DispatchContext, Map, String)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderIdList List of orderId String
     * @param context.userLogin GenericValue
     * @param context.statusId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> massChangeOrderStatus(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to process order payment. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#processOrderPayments(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> processOrderPayments(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for checking and re-calc the shipping amount. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#recalcOrderShipping(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> recalcShippingTotal(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for checking and re-calc the tax amount. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#recalcOrderTax(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> recalcOrderTax(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to remove a role type from an order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#removeRoleType(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.partyId String
     * @param context.roleTypeId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> removeOrderRole(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for resetting the OrderHeader grandTotal. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#resetGrandTotal(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> resetGrandTotal(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for saving the updated shopping cart to the order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#saveUpdatedCartToOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.shoppingCart ShoppingCart
     * @param context.changeMap Map
     *  
	 * @return the followings in return Map:
	 *  orderId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> saveUpdatedCartToOrder(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email a customer with order changes to PRDS_ODR_BACKORDER. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendOrderBackorderNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItemSeqId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     * @param context.screenUri String
     * @param context.temporaryAnonymousUserLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  return Map of sendMailFromScreen service
	 */
	public Map<String, Object> sendOrderBackorderNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email a customer with order changes to PRDS_ODR_CHANGE. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendOrderChangeNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItemSeqId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     * @param context.screenUri String
     * @param context.temporaryAnonymousUserLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  return Map of sendMailFromScreen service
	 */
	public Map<String, Object> sendOrderChangeNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email a customer with order changes to PRDS_ODR_COMPLETE. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendOrderCompleteNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItemSeqId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     * @param context.screenUri String
     * @param context.temporaryAnonymousUserLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  return Map of sendMailFromScreen service
	 */
	public Map<String, Object> sendOrderCompleteNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email a customer with order changes to PRDS_ODR_CONFIRM. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendOrderConfirmNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItemSeqId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     * @param context.screenUri String
     * @param context.temporaryAnonymousUserLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  return Map of sendMailFromScreen service
	 */
	public Map<String, Object> sendOrderConfirmation(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email a customer with order changes to PRDS_ODR_PAYRETRY. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendOrderPayRetryNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderItemSeqId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     * @param context.screenUri String
     * @param context.temporaryAnonymousUserLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  return Map of sendMailFromScreen service
	 */
	public Map<String, Object> sendOrderPayRetryNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to email order notifications for pending actions. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#sendProcessNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.adminEmailList String
     * @param context.orderItemSeqId String
     * @param context.workEffortId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> sendProcessNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for changing the status on order item(s). This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#setItemStatus(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderId String
     * @param context.orderItemSeqId String
     * @param context.fromStatusId String
     * @param context.statusId String
     * @param context.statusDateTime Timestamp
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> changeOrderItemStatus(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to set order payment status. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#setOrderPaymentStatus(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderPaymentPreferenceId String
     * @param context.orderItemSeqId String
     * @param context.changeReason String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> changeOrderPaymentStatus(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service for changing the status on an order header. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#setOrderStatus(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderId String
     * @param context.statusId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  oldStatusId: String if success
	 *  needsInventoryIssuance: String of Y|N if success
	 *  grandTotal: BigDecimal if success
	 *  orderTypeId: String if success
	 *  orderStatusId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> changeOrderStatus(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to update approved order items. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#updateApprovedOrderItems(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.orderId String
     * @param context.overridePriceMap Map
     * @param context.itemDescriptionMap Map
     * @param context.itemPriceMap Map
     * @param context.itemQtyMap Map
     * @param context.itemReasonMap Map
     * @param context.itemCommentMap Map
     * @param context.itemAttributesMap Map
     * @param context.itemShipDateMap Map
     * @param context.itemDeliveryDateMap Map
     *  
	 * @return the followings in return Map:
	 *  shoppingCart: ShoppingCart if success
	 *  orderId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> updateOrderItems(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to update order payment preference. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#updateOrderPaymentPreference(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderPaymentPreferenceId String
     * @param context.checkOutPaymentId String
     * @param context.cancelThis String
     *  
	 * @return the followings in return Map:
	 *  orderPaymentPreferenceId: String if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> updateOrderPaymentPreference(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Service to update the order tracking number. This method simply calls
	 * {@link org.ofbiz.order.order.OrderServices#updateTrackingNumber(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.shipGroupSeqId String
     * @param context.trackingNumber String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> updateTrackingNumber(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  Order services end
	 ************************************************************/

	/************************************************************
	 *  Order return services begin
	 ************************************************************/
	
	/**
     * Service to cancel replacement order if return not received within 30 days and send notification.
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#autoCancelReplacementOrders(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPOND_SUCCESS: String
	 */
//	public Map<String, Object> autoCancelReplacementOrders(DispatchContext dctx,
//			Map<String, ?> context);
	
	/**
     * Service to update the order tracking number. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#checkPaymentAmountForRefund(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.returnId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  statusId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> checkPaymentAmountForRefund(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#checkReturnComplete(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> checkReturnComplete(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#createPaymentApplicationsFromReturnItemResponse(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.returnItemResponseId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> createPaymentApplicationsFromReturnItemResponse(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#createReturnAdjustment(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderAdjustmentId String
     * @param context.returnAdjustmentTypeId String
     * @param context.returnId String
     * @param context.returnItemSeqId String
     * @param context.description String
     *  
	 * @return the followings in return Map:
	 *  returnAdjustmentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> createReturnAdjustment(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to get a map of returnable items (items not already returned) and quantities.
     * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#getReturnableItems(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  returnableItems: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> getReturnableItems(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to get the returnable quantiy for an order item. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#getReturnableQuantity(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderItem String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  returnableQuantity: BigDecimal if success
	 *  returnablePrice: BigDecimal if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> getReturnableQuantity(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to get the returnable amount for an order. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#getReturnAmountByOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  orderReturnAmountMap: Map if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> getReturnAmountByOrder(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to locate the return item's initial inventory item cost. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#getReturnItemInitialCost(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.returnItemSeqId String
     *  
	 * @return the followings in return Map:
	 *  initialItemCost: BigDecimal if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> getReturnItemInitialCost(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to credit (billingAccount) return. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#processCreditReturn(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> processCreditReturn(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to refund return. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#processRefundReturn(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> processRefundReturn(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to replacement return (create new order adjusted to be at no charge). This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#processReplacementReturn(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.returnTypeId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> processReplacementReturn(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to refund billing account payment. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#refundBillingAccountPayment(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderPaymentPreference GenericValue
     * @param context.refundAmount BigDecimal
     *  
	 * @return the followings in return Map:
	 *  paymentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> refundBillingAccountPayment(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to return request notification. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#sendReturnAcceptNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  paymentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> sendReturnAcceptNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to return cancel notification. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#sendReturnCancelNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  paymentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> sendReturnCancelNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Services to return complete notification. This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#sendReturnCompleteNotification(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnId String
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  paymentId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> sendReturnCompleteNotification(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.order.OrderReturnServices#updateReturnAdjustment(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.returnAdjustmentId String
     * @param context.originalReturnPrice BigDecimal
     * @param context.originalReturnQuantity BigDecimal
     * @param context.amount BigDecimal
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> updateReturnAdjustment(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  Order return services end
	 ************************************************************/

	/************************************************************
	 *  Order lookup services begin
	 ************************************************************/
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.order.OrderLookupServices#findOrders(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.viewIndex Integer
     * @param context.viewSize Integer
     * @param context.showAll String
     * @param context.useEntryDate String
     * @param context.orderId String
     * @param context.orderTypeId String
     * @param context.orderName String
     * @param context.orderStatusId String
     * @param context.productStoreId String
     * @param context.orderWebSiteId String
     * @param context.salesChannelEnumId String
     * @param context.createdBy String
     * @param context.terminalId String
     * @param context.transactionId String
     * @param context.externalId String
     * @param context.internalCode String
     * @param context.minDate String
     * @param context.maxDate String
     * @param context.userLoginId String
     * @param context.partyId String
     * @param context.roleTypeId String
     * @param context.isViewed String
     * @param context.shipmentMethod String
     * @param context.gatewayAvsResult String
     * @param context.gatewayScoreResult String
     * @param context.correspondingPoId String
     * @param context.subscriptionId String
     * @param context.productId String
     * @param context.budgetId String
     * @param context.quoteId String
     * @param context.billingAccountId String
     * @param context.finAccountId String
     * @param context.cardNumber String
     * @param context.accountNumber String
     * @param context.inventoryItemId String
     * @param context.softIdentifier String
     * @param context.serialNumber String
     * @param context.shipmentId String
     * @param context.countryGeoId String
     * @param context.includeCountry String
     *  
	 * @return the followings in return Map:
	 *  highIndex: Integer if success
	 *  lowIndex: Integer if success
	 *  viewIndex: Integer if success
	 *  viewSize: Integer if success
	 *  showAll: String if success
	 *  paramList: String if success
	 *  orderList: List of GenericValue if success
	 *  orderListSize: Integer if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> findOrders(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  Order lookup services end
	 ************************************************************/

	/************************************************************
	 *  Quote services begin
	 ************************************************************/
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.quote.QuoteServices#sendQuoteReportMail(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.emailType String
     * @param context.quoteId String
     * @param context.sendTo String
     * @param context.sendCc String
     * @param context.note String
     *  
	 * @return the followings in return Map:
	 *  emailType: String if success
	 *  sendMailFromScreen service return Map if success
	 *  ModelService.ERROR_MESSAGE: String or List if error
	 */
	public Map<String, Object> sendQuoteReportMail(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.quote.QuoteServices#storeQuote(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.quoteTypeId String
     * @param context.partyId String
     * @param context.issueDate Timestamp
     * @param context.statusId String
     * @param context.currencyUomId String
     * @param context.productStoreId String
     * @param context.salesChannelEnumId String
     * @param context.validFromDate Timestamp
     * @param context.validThruDate Timestamp
     * @param context.quoteName String
     * @param context.description String
     * @param context.quoteItems List
     * @param context.quoteAttributes List
     * @param context.quoteCoefficients List
     * @param context.quoteRoles List
     * @param context.quoteTerms List
     * @param context.quoteTermAttributes List
     * @param context.quoteWorkEfforts List
     * @param context.quoteAdjustments List
     *  
	 * @return the followings in return Map:
	 *  quoteId: String if success
	 *  ModelService.RESPOND_FAIL: String or List if error
	 */
	public Map<String, Object> storeQuote(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  Quote services end
	 ************************************************************/

	/************************************************************
	 *  Requirement services begin
	 ************************************************************/
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.requirement.RequirementServices#getRequirementsForSupplier(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.locale Locale
     * @param context.requirementConditions EntityCondition
     * @param context.unassignedRequirements String
     * @param context.currencyUomId String
     * @param context.statusIds List of String
     *  
	 * @return the followings in return Map:
	 *  requirementsForSupplier: List of Map if success
	 *  distinctProductCount: Integer if success
	 *  quantityTotal: BigDecimal if success
	 *  amountTotal: BigDecimal if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> getRequirementsForSupplier(DispatchContext dctx,
			Map<String, ?> context);
	
    /************************************************************
	 *  Requirement services end
	 ************************************************************/

	/************************************************************
	 *  ShoppingCart services begin
	 ************************************************************/
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#assignItemShipGroup(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.fromGroupIndex Integer
     * @param context.toGroupIndex Integer
     * @param context.itemIndex Integer
     * @param context.quantity BigDecimal
     * @param context.clearEmptyGroups Boolean
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 */
	public Map<String, Object> assignItemShipGroup(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#getShoppingCartData(DispatchContext, Map)
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.locale Locale
     * @param context.shoppingCart ShoppingCart
     * @param context.fromGroupIndex Integer
     * @param context.toGroupIndex Integer
     * @param context.itemIndex Integer
     * @param context.quantity BigDecimal
     * @param context.clearEmptyGroups Boolean
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  totalQuantity: BigDecimal
	 *  currencyIsoCode: String
	 *  subTotal: BigDecimal
	 *  subTotalCurrencyFormatted: String
	 *  totalShipping: BigDecimal
	 *  totalShippingCurrencyFormatted: String
	 *  totalSalesTax: BigDecimal
	 *  totalSalesTaxCurrencyFormatted: String
	 *  displayGrandTotal: BigDecimal
	 *  displayGrandTotalCurrencyFormatted: String
	 *  displayOrderAdjustmentsTotalCurrencyFormatted: String
	 *  cartItemData: Map of String
	 *    in cartItemData:
	 *    displayItemSubTotal_0,1,2...: BigDecimal
	 *    displayItemSubTotalCurrencyFormatted_0,1,2...: String
	 *    displayItemAdjustment_0,1,2...: String
	 */
	public Map<String, Object> getShoppingCartData(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#getShoppingCartItemIndex(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.productId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  itemIndex: String of an int number
	 */
	public Map<String, Object> getShoppingCartItemIndex(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#loadCartFromOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.orderId String
     * @param context.skipInventoryChecks Boolean
     * @param context.skipProductChecks Boolean
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  shoppingCart: ShoppingCart if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> loadCartFromOrder(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#loadCartFromQuote(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.quoteId String
     * @param context.applyQuoteAdjustments String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  shoppingCart: ShoppingCart if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> loadCartFromQuote(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#loadCartFromShoppingList(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.shoppingListId String
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  shoppingCart: ShoppingCart if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> loadCartFromShoppingList(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#prepareVendorShipGroups(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> prepareVendorShipGroups(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#resetShipGroupItems(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 */
	public Map<String, Object> resetShipGroupItems(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#setOtherOptions(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.orderAdditionalEmails String
     * @param context.correspondingPoId String
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String
	 */
	public Map<String, Object> setCartOtherOptions(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCartServices#setShippingOptions(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.groupIndex Integer
     * @param context.shippingContactMechId String
     * @param context.shipmentMethodString String
     * @param context.giftMessage String
     * @param context.maySplit Boolean
     * @param context.isGift Boolean
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> setCartShippingOptions(DispatchContext dctx,
			Map<String, Object> context);
	
    /************************************************************
	 *  ShoppingCart services end
	 ************************************************************/

	/************************************************************
	 *  ShoppingList services begin
	 ************************************************************/
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#createListReorders(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> runShoppingListAutoReorder(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#makeListFromOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingListTypeId String
     * @param context.shoppingListId String
     * @param context.orderId String
     * @param context.partyId String
     * @param context.startDateTime Timestamp
     * @param context.endDateTime Timestamp
     * @param context.frequency Integer
     * @param context.intervalNumber Integer
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  shoppingListId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> makeShoppingListFromOrder(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#makeShoppingListCart(org.ofbiz.service.LocalDispatcher, String, java.util.Locale)},
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#makeShoppingListCart(org.ofbiz.service.LocalDispatcher, org.ofbiz.entity.GenericValue, java.util.Locale)}
	 * or 
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#makeShoppingListCart(org.ofbiz.order.shoppingcart.ShoppingCart, org.ofbiz.service.LocalDispatcher, org.ofbiz.entity.GenericValue, java.util.Locale)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingListId String
     * @param context.shoppingList GenericValue
     * @param context.listCart ShoppingCart
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: ShoppingCart if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> makeShoppingListCart(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#setShoppingListRecurrence(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.startDateTime Timestamp
     * @param context.endDateTime Timestamp
     * @param context.frequency Integer
     * @param context.intervalNumber Integer
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  recurrenceInfoId: String if success
	 *  ModelService.RESPONSE_MESSAGE: String if success
	 *  ModelService.ERROR_MESSAGE: String if error
	 */
	public Map<String, Object> createShoppingListRecurrence(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#splitShipmentMethodString(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shippingMethodString String
     *  
	 * @return the followings in return Map:
	 *  shipmentMethodTypeId: String
	 *  carrierPartyId: String
	 *  ModelService.RESPONSE_MESSAGE: String
	 */
	public Map<String, Object> splitShipmentMethodString(DispatchContext dctx,
			Map<String, Object> context);
	
	/**
     * Given an orderId, this service will look through all its OrderItems and for each shoppingListItemId
     * and shoppingListItemSeqId, update the quantity purchased in the ShoppingListItem entity.  Used for
     * tracking how many of shopping list items are purchased.  This service is mounted as a seca on storeOrder.
	 * This method simply calls
	 * {@link org.ofbiz.order.shoppinglist.ShoppingListServices#updateShoppingListQuantitiesFromOrder(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.orderId String
     *  
	 * @return the followings in return Map:
	 * an empty Map
	 */
	public Map<String, Object> updateShoppingListQuantitiesFromOrder(DispatchContext dctx,
			Map<String, Object> context);
	
    /************************************************************
	 *  ShoppingList services end
	 ************************************************************/

	/************************************************************
	 *  Task services begin
	 ************************************************************/
	
    /************************************************************
	 *  Task services end
	 ************************************************************/

}
