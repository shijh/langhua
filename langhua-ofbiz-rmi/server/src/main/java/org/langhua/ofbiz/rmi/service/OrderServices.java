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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Locale;
import java.util.Map;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.GeneralException;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.order.shoppingcart.ShoppingCart;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

/**
 * RMI Order Services
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class OrderServices implements I_OrderServices {
    
    public static final String module = OrderServices.class.getName();

    /**
     *  Empty constructor
     */
    public OrderServices() {
    }
    
    /**
     *  Static method to get OrderServices instance
     *  This method name is defined in java.lang.Class.
     */
    public static OrderServices newInstance() {
    	return new OrderServices();
    }

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getAvailableBalance(DispatchContext, Map)
	 */
	public Map<String, Object> getAvailableBalance(DispatchContext dctx,
			Map<String, ?> context) {
		GenericDelegator delegator = dctx.getDelegator();
		String finAccountId = (String) context.get("finAccountId");
		Timestamp authorizationDate = (Timestamp) context.get("authorizationDate");

		BigDecimal value;
		try {
			value = org.ofbiz.order.finaccount.FinAccountHelper.getAvailableBalance(finAccountId, authorizationDate, delegator);
		} catch (GenericEntityException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, value);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getBalance(DispatchContext, Map)
	 */
	public Map<String, Object> getBalance(DispatchContext dctx,
			Map<String, ?> context) {
		GenericDelegator delegator = dctx.getDelegator();
		String finAccountId = (String) context.get("finAccountId");
		Timestamp authorizationDate = (Timestamp) context.get("authorizationDate");

		BigDecimal value;
		try {
			value = org.ofbiz.order.finaccount.FinAccountHelper.getBalance(finAccountId, authorizationDate, delegator);
		} catch (GenericEntityException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, value);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getFinAccountFromCode(DispatchContext, Map)
	 */
	public Map<String, Object> getFinAccountFromCode(DispatchContext dctx,
			Map<String, ?> context) {
		GenericDelegator delegator = dctx.getDelegator();
		String finAccountCode = (String) context.get("finAccountCode");

		GenericValue value;
		try {
			value = org.ofbiz.order.finaccount.FinAccountHelper.getFinAccountFromCode(finAccountCode, delegator);
		} catch (GenericEntityException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, value);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#validatePin(DispatchContext, Map)
	 */
	public Map<String, Object> validatePin(DispatchContext dctx,
			Map<String, ?> context) {
		GenericDelegator delegator = dctx.getDelegator();
		String finAccountId = (String) context.get("finAccountId");
		String pinNumber = (String) context.get("pinNumber");

		boolean value = org.ofbiz.order.finaccount.FinAccountHelper.validatePin(delegator, finAccountId, pinNumber);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, Boolean.valueOf(value));
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#storeOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> storeOrder(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createOrder(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createOrderFromShoppingCart(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createOrderFromShoppingCart(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createOrderFromShoppingCart(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createOrderNote(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createOrderNote(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createOrderNote(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createPaymentFromPreference(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createPaymentFromPreference(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createPaymentFromPreference(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createOrderPaymentPreference(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createOrderPaymentPreference(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createPaymentPreference(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createSimpleNonProductSalesOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createSimpleNonProductSalesOrder(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.createSimpleNonProductSalesOrder(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#fulfillDigitalItems(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> fulfillDigitalItems(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.fulfillDigitalItems(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getOrderAddress(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderAddress(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.getOrderAddress(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getOrderHeaderInformationById(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderHeaderInformationById(DispatchContext dctx,
			Map<String, ?> context) {
        GenericDelegator delegator = dctx.getDelegator();
        String orderId = (String) context.get("orderId");
        Locale locale = (Locale) context.get("locale");

        GenericValue orderHeader = null;
        try {
            orderHeader = delegator.findByPrimaryKey("OrderHeader", UtilMisc.toMap("orderId", orderId));
        } catch (GenericEntityException e) {
            Debug.logError(e, "Problem getting order header detial", module);
            return ServiceUtil.returnError(UtilProperties.getMessage(org.ofbiz.order.order.OrderServices.resource_error,"OrderCannotGetOrderHeader", locale) + e.getMessage());
        }
        if (orderHeader != null) {
            Map result = ServiceUtil.returnSuccess();
            result.put(I_DelegatorService.RMI_RESULTS, orderHeader);
            return result;
        }
        return ServiceUtil.returnError(UtilProperties.getMessage(org.ofbiz.order.order.OrderServices.resource_error,"OrderErrorGettingOrderHeaderInformationNull", locale));
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getOrderItemInvoicedAmountAndQuantity(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderItemInvoicedAmountAndQuantity(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.getOrderItemInvoicedAmountAndQuantity(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getOrderShippingAmount(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getOrderShippingAmount(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.getOrderShippingAmount(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#loadCartForUpdate(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> loadCartForUpdate(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.loadCartForUpdate(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#massChangeOrderStatus(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> massChangeOrderStatus(DispatchContext dctx,
			Map<String, ?> context) {
        String statusId = (String) context.get("statusId");
        context.remove("statusId");
		return org.ofbiz.order.order.OrderServices.massChangeOrderStatus(dctx, context, statusId);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#processOrderPayments(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> processOrderPayments(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.processOrderPayments(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#recalcShippingTotal(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> recalcShippingTotal(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.recalcOrderShipping(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#recalcOrderTax(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> recalcOrderTax(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.recalcOrderTax(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#removeOrderRole(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> removeOrderRole(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.removeRoleType(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#resetGrandTotal(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> resetGrandTotal(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.resetGrandTotal(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#saveUpdatedCartToOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> saveUpdatedCartToOrder(DispatchContext dctx,
			Map<String, ?> context) {
		try {
			return org.ofbiz.order.order.OrderServices.saveUpdatedCartToOrder(dctx, context);
		} catch (GeneralException e) {
			return ServiceUtil.returnError(e.getMessage());
		}
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendOrderBackorderNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendOrderBackorderNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendOrderBackorderNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendOrderChangeNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendOrderChangeNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendOrderChangeNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendOrderCompleteNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendOrderCompleteNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendOrderCompleteNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendOrderConfirmation(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendOrderConfirmation(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendOrderConfirmNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendOrderPayRetryNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendOrderPayRetryNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendOrderPayRetryNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendProcessNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendProcessNotification(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.sendProcessNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#changeOrderItemStatus(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> changeOrderItemStatus(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.setItemStatus(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#changeOrderPaymentStatus(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> changeOrderPaymentStatus(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.setOrderPaymentStatus(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#changeOrderStatus(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> changeOrderStatus(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.setOrderStatus(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#updateOrderItems(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateOrderItems(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.updateApprovedOrderItems(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#updateOrderPaymentPreference(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateOrderPaymentPreference(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.updateOrderPaymentPreference(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#updateTrackingNumber(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateTrackingNumber(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderServices.updateTrackingNumber(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#checkPaymentAmountForRefund(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> checkPaymentAmountForRefund(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.checkPaymentAmountForRefund(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#checkReturnComplete(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> checkReturnComplete(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.checkReturnComplete(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createPaymentApplicationsFromReturnItemResponse(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createPaymentApplicationsFromReturnItemResponse(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.createPaymentApplicationsFromReturnItemResponse(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createReturnAdjustment(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createReturnAdjustment(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.createReturnAdjustment(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getReturnableItems(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReturnableItems(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.getReturnableItems(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getReturnAmountByOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReturnAmountByOrder(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.getReturnAmountByOrder(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getReturnItemInitialCost(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReturnItemInitialCost(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.getReturnItemInitialCost(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getReturnableQuantity(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReturnableQuantity(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.getReturnableQuantity(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#processCreditReturn(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> processCreditReturn(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.processCreditReturn(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getReturnableItems(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> processRefundReturn(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.processRefundReturn(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#processReplacementReturn(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> processReplacementReturn(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.processReplacementReturn(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#refundBillingAccountPayment(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> refundBillingAccountPayment(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.refundBillingAccountPayment(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendReturnAcceptNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendReturnAcceptNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.sendReturnAcceptNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendReturnCancelNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendReturnCancelNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.sendReturnCancelNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendReturnCompleteNotification(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendReturnCompleteNotification(
			DispatchContext dctx, Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.sendReturnCompleteNotification(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#updateReturnAdjustment(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateReturnAdjustment(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderReturnServices.updateReturnAdjustment(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#findOrders(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> findOrders(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.order.OrderLookupServices.findOrders(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#sendQuoteReportMail(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> sendQuoteReportMail(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.quote.QuoteServices.sendQuoteReportMail(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#storeQuote(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> storeQuote(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.quote.QuoteServices.storeQuote(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getRequirementsForSupplier(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getRequirementsForSupplier(DispatchContext dctx,
			Map<String, ?> context) {
		return org.ofbiz.order.requirement.RequirementServices.getRequirementsForSupplier(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#assignItemShipGroup(DispatchContext, Map)
	 */
	public Map<String, Object> assignItemShipGroup(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.assignItemShipGroup(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getShoppingCartData(DispatchContext, Map)
	 */
	public Map<String, Object> getShoppingCartData(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.getShoppingCartData(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#getShoppingCartItemIndex(DispatchContext, Map)
	 */
	public Map<String, Object> getShoppingCartItemIndex(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.getShoppingCartItemIndex(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#loadCartFromOrder(DispatchContext, Map)
	 */
	public Map<String, Object> loadCartFromOrder(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.loadCartFromOrder(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#loadCartFromQuote(DispatchContext, Map)
	 */
	public Map<String, Object> loadCartFromQuote(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.loadCartFromQuote(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#loadCartFromShoppingList(DispatchContext, Map)
	 */
	public Map<String, Object> loadCartFromShoppingList(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.loadCartFromShoppingList(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#prepareVendorShipGroups(DispatchContext, Map)
	 */
	public Map<String, Object> prepareVendorShipGroups(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.prepareVendorShipGroups(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#resetShipGroupItems(DispatchContext, Map)
	 */
	public Map<String, Object> resetShipGroupItems(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.resetShipGroupItems(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#setCartOtherOptions(DispatchContext, Map)
	 */
	public Map<String, Object> setCartOtherOptions(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.setOtherOptions(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#setCartShippingOptions(DispatchContext, Map)
	 */
	public Map<String, Object> setCartShippingOptions(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppingcart.ShoppingCartServices.setShippingOptions(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#runShoppingListAutoReorder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> runShoppingListAutoReorder(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppinglist.ShoppingListServices.createListReorders(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#makeShoppingListFromOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> makeShoppingListFromOrder(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppinglist.ShoppingListServices.makeListFromOrder(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#setShippingOptions(DispatchContext, Map)
	 */
	public Map<String, Object> makeShoppingListCart(DispatchContext dctx,
			Map<String, Object> context) {
		LocalDispatcher dispatcher = dctx.getDispatcher();
		ShoppingCart listCart = (ShoppingCart) context.get("listCart");
		String shoppingListId = (String) context.get("shoppingListId");
		GenericValue shoppingList = (GenericValue) context.get("shoppingList");
		Locale locale = (Locale) context.get("locale");

		ShoppingCart value;
		if (listCart != null) {
			value = org.ofbiz.order.shoppinglist.ShoppingListServices.makeShoppingListCart(listCart, dispatcher, shoppingList, locale);
		} else if (shoppingListId != null) {
			value = org.ofbiz.order.shoppinglist.ShoppingListServices.makeShoppingListCart(dispatcher, shoppingListId, locale);
		} else if (shoppingList != null) {
			value = org.ofbiz.order.shoppinglist.ShoppingListServices.makeShoppingListCart(dispatcher, shoppingList, locale);
		} else {
			return ServiceUtil.returnError("No shoppingListId or shoppingList provided.");
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, value);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#createShoppingListRecurrence(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> createShoppingListRecurrence(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppinglist.ShoppingListServices.setShoppingListRecurrence(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#splitShipmentMethodString(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> splitShipmentMethodString(DispatchContext dctx,
			Map<String, Object> context) {
		return org.ofbiz.order.shoppinglist.ShoppingListServices.splitShipmentMethodString(dctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_OrderServices#updateShoppingListQuantitiesFromOrder(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> updateShoppingListQuantitiesFromOrder(
			DispatchContext dctx, Map<String, Object> context) {
		return org.ofbiz.order.shoppinglist.ShoppingListServices.updateShoppingListQuantitiesFromOrder(dctx, context);
	}

}
