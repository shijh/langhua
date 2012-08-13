/*
 * This library is part of OFBiz-AliPay Component of Langhua
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
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-alipay/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on AliPay, please see the its website:
 * https://www.alipay.com/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.alipay;

import org.ofbiz.base.util.*;
import org.ofbiz.entity.*;
import org.ofbiz.base.util.UtilHttp;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.ofbiz.service.LocalDispatcher;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.entity.GenericDelegator;
import com.alipay.util.CheckURL;

/**
 * @author Wang Suozhu
 *
 */
public class AlipayServices {

	public static final String module = AlipayServices.class.getName();

	/**
	 * Click the server of the notice sent to web server approach.
	 * 
	 * @param request
	 *            The HTTPRequest object for the current request
	 *@param response
	 *            The HTTPResponse object for the current request
	 *@return String specifying the exit status of this event
	 */
	public static String alipayNotify(HttpServletRequest request,
			HttpServletResponse response) {
		HttpSession session = request.getSession();
		LocalDispatcher dispatcher = (LocalDispatcher) request
				.getAttribute("dispatcher");
		GenericDelegator delegator = (GenericDelegator) request
				.getAttribute("delegator");
		Locale locale = UtilHttp.getLocale(request);
		String partner = UtilProperties.getPropertyValue("alipay.properties",
				"alipay.partner"); // partner partner id (must be filled out)
		String privateKey = UtilProperties.getPropertyValue(
				"alipay.properties", "alipay.partner.key"); // partner check the
															// corresponding
															// transaction
															// security code
															// (required)
		// **********************************************************************************
		// If you do not support the https server interaction, you can use the
		// http address verification inquiries
		// String alipayNotifyURLForHttps =
		// UtilProperties.getPropertyValue("alipay.properties",
		// "alipay.NotifyURLForHttps")
		String alipayNotifyURLForhttp = UtilProperties.getPropertyValue(
				"alipay.properties", "alipay.NotifyURLForHttp")
				+ "&partner="
				+ partner
				+ "&notify_id="
				+ request.getParameter("notify_id");

		// ATN to obtain payment of the return of the results of Bao, true
		// information is correct orders, false is not valid
		String responseTxt = CheckURL.check(alipayNotifyURLForhttp);

		Map params = new HashMap();
		// POST parameters obtained from the params in the new
		Map requestParams = request.getParameterMap();
		for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
			String name = (String) iter.next();
			String[] values = (String[]) requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i]
						: valueStr + values[i] + ",";
			}
			params.put(name, valueStr);
		}

		String mysign = AlipaySignatureHelper.sign(params, privateKey);
		Debug
				.logInfo(
						"**************************************************************",
						module);
		String orderId = request.getParameter("out_trade_no");
		Debug.logInfo("orderId = " + orderId, module);
		GenericValue userLogin = null;
		try {
			userLogin = delegator.findByPrimaryKey("UserLogin", UtilMisc.toMap(
					"userLoginId", "admin"));
		} catch (Exception e) {

		}
		if (mysign.equals(request.getParameter("sign"))
				&& responseTxt.equals("true")) {
			if (request.getParameter("trade_status").equals("WAIT_BUYER_PAY")) {
				// Transaction has been created, waiting for buyers to pay.
				// You can write data processing
				Debug
						.logInfo(
								"**************************************************************",
								module);
				Debug.logInfo("trade_status:WAIT_BUYER_PAY", module);
				Debug
						.logInfo(
								"**************************************************************",
								module);

				request.setAttribute("result", "success");
			} else if (request.getParameter("trade_status").equals(
					"WAIT_SELLER_SEND_GOODS")) {
				// Buyer to pay the success of sellers waiting for shipment
				// You can write data processing
				Debug
						.logInfo(
								"**************************************************************",
								module);
				Debug.logInfo("trade_status:WAIT_SELLER_SEND_GOODS", module);
				Debug
						.logInfo(
								"**************************************************************",
								module);

				/*
				 * //Accept payment,Pay for the right to take over after the
				 * orders automatically become approved by
				 * 
				 * // get the order header & payment preferences GenericValue
				 * orderHeader = null; try { orderHeader =
				 * delegator.findByPrimaryKey("OrderHeader",
				 * UtilMisc.toMap("orderId", orderId)); } catch
				 * (GenericEntityException e) { Debug.logError(e,
				 * "Problems reading order header from datasource.", module);
				 * 
				 * }
				 * 
				 * Double grandTotal = new Double(0.00); if (orderHeader !=
				 * null) { grandTotal = orderHeader.getDouble("grandTotal"); }
				 * 
				 * // get the payment types to receive List paymentMethodTypes =
				 * null;
				 * 
				 * try { List pmtFields = UtilMisc.toList(new
				 * EntityExpr("paymentMethodTypeId", EntityOperator.NOT_EQUAL,
				 * "EXT_OFFLINE")); paymentMethodTypes =
				 * delegator.findByAnd("PaymentMethodType", pmtFields); } catch
				 * (GenericEntityException e) { Debug.logError(e,
				 * "Problems getting payment types", module);
				 * 
				 * }
				 * 
				 * if (paymentMethodTypes == null) {
				 * //request.setAttribute("_ERROR_MESSAGE_",
				 * UtilProperties.getMessage
				 * (resource_error,"OrderProblemsWithPaymentTypeLookup",
				 * locale));
				 * 
				 * }
				 * 
				 * List toBeStored = new LinkedList(); GenericValue
				 * placingCustomer = null; try { List pRoles =
				 * delegator.findByAnd("OrderRole", UtilMisc.toMap("orderId",
				 * orderId, "roleTypeId", "PLACING_CUSTOMER")); if (pRoles !=
				 * null && pRoles.size() > 0) placingCustomer =
				 * EntityUtil.getFirst(pRoles); } catch (GenericEntityException
				 * e) { Debug.logError(e,
				 * "Problems looking up order payment preferences", module);
				 * //request.setAttribute("_ERROR_MESSAGE_",
				 * UtilProperties.getMessage
				 * (resource_error,"OrderErrorProcessingOfflinePayments",
				 * locale));
				 * 
				 * }
				 * 
				 * //Iterator pmti = paymentMethodTypes.iterator(); // while
				 * (pmti.hasNext()) { //GenericValue paymentMethodType =
				 * (GenericValue) pmti.next(); String paymentMethodTypeId =
				 * "EXT_ALIPAY"; String amountStr = grandTotal.toString();
				 * String paymentReference = ""; if
				 * (!UtilValidate.isEmpty(amountStr)) { BigDecimal
				 * paymentTypeAmount = BigDecimal.ZERO; try { paymentTypeAmount
				 * = new
				 * BigDecimal(NumberFormat.getNumberInstance(locale).parse(
				 * amountStr).doubleValue()); } catch (java.text.ParseException
				 * pe) { //request.setAttribute("_ERROR_MESSAGE_",
				 * UtilProperties
				 * .getMessage(resource_error,"OrderProblemsPaymentParsingAmount"
				 * , locale));
				 * 
				 * } if (paymentTypeAmount > 0.00) {
				 * 
				 * // create the OrderPaymentPreference // TODO: this should be
				 * done with a service Map prefFields =
				 * UtilMisc.toMap("orderPaymentPreferenceId",
				 * delegator.getNextSeqId("OrderPaymentPreference"));
				 * GenericValue paymentPreference =
				 * delegator.makeValue("OrderPaymentPreference", prefFields);
				 * paymentPreference.set("paymentMethodTypeId",
				 * paymentMethodTypeId); paymentPreference.set("maxAmount",
				 * paymentTypeAmount); paymentPreference.set("statusId",
				 * "PAYMENT_RECEIVED"); paymentPreference.set("orderId",
				 * orderId); paymentPreference.set("createdDate",
				 * UtilDateTime.nowTimestamp()); if (userLogin != null) {
				 * paymentPreference.set("createdByUserLogin",
				 * userLogin.getString("userLoginId")); }
				 * 
				 * try { delegator.create(paymentPreference); } catch
				 * (GenericEntityException ex) { Debug.logError(ex,
				 * "Cannot create a new OrderPaymentPreference", module);
				 * request.setAttribute("_ERROR_MESSAGE_", ex.getMessage());
				 * 
				 * }
				 * 
				 * // create a payment record Map results = null; try { results
				 * = dispatcher.runSync("createPaymentFromPreference",
				 * UtilMisc.toMap("userLogin", userLogin,
				 * "orderPaymentPreferenceId",
				 * paymentPreference.get("orderPaymentPreferenceId"),
				 * "paymentRefNum", paymentReference, "paymentFromId",
				 * placingCustomer.getString("partyId"), "comments",
				 * "Payment received offline and manually entered.")); } catch
				 * (GenericServiceException e) { Debug.logError(e,
				 * "Failed to execute service createPaymentFromPreference",
				 * module); request.setAttribute("_ERROR_MESSAGE_",
				 * e.getMessage());
				 * 
				 * }
				 * 
				 * if ((results == null) ||
				 * (results.get(ModelService.RESPONSE_MESSAGE
				 * ).equals(ModelService.RESPOND_ERROR))) {
				 * Debug.logError((String)
				 * results.get(ModelService.ERROR_MESSAGE), module);
				 * request.setAttribute("_ERROR_MESSAGE_", (String)
				 * results.get(ModelService.ERROR_MESSAGE));
				 * 
				 * } } } // }
				 * 
				 * // get the current payment prefs GenericValue offlineValue =
				 * null; List currentPrefs = null; double paymentTally = 0.00;
				 * try { List oppFields = UtilMisc.toList(new
				 * EntityExpr("orderId", EntityOperator.EQUALS, orderId), new
				 * EntityExpr("statusId", EntityOperator.NOT_EQUAL,
				 * "PAYMENT_CANCELLED")); currentPrefs =
				 * delegator.findByAnd("OrderPaymentPreference", oppFields); }
				 * catch (GenericEntityException e) { Debug.logError(e,
				 * "ERROR: Unable to get existing payment preferences from order"
				 * , module); } if (currentPrefs != null && currentPrefs.size()
				 * > 0) { Iterator cpi = currentPrefs.iterator(); while
				 * (cpi.hasNext()) { GenericValue cp = (GenericValue)
				 * cpi.next(); String paymentMethodType =
				 * cp.getString("paymentMethodTypeId"); if
				 * ("EXT_OFFLINE".equals(paymentMethodType)) { offlineValue =
				 * cp; } else { Double cpAmt = cp.getDouble("maxAmount"); if
				 * (cpAmt != null) { paymentTally += cpAmt.doubleValue(); } } }
				 * }
				 * 
				 * // now finish up boolean okayToApprove = false; if
				 * (paymentTally >= grandTotal.doubleValue()) { // cancel the
				 * offline preference okayToApprove = true; if (offlineValue !=
				 * null) { offlineValue.set("statusId", "PAYMENT_CANCELLED");
				 * toBeStored.add(offlineValue); } }
				 * 
				 * // store the status changes and the newly created payment
				 * preferences and payments
				 * // TODO: updating order payment
				 * preference should be done with a service try {
				 * delegator.storeAll(toBeStored); } catch
				 * (GenericEntityException e) { Debug.logError(e,
				 * "Problems storing payment information", module);
				 * //request.setAttribute("_ERROR_MESSAGE_",
				 * UtilProperties.getMessage
				 * (resource_error,"OrderProblemStoringReceivedPaymentInformation"
				 * , locale));
				 * 
				 * }
				 * 
				 * if (okayToApprove) { // update the status of the order and
				 * items OrderChangeHelper.approveOrder(dispatcher, userLogin,
				 * orderId); }
				 */
				Map serviceCtx = UtilMisc.toMap("statusId", "ITEM_APPROVED",
						"orderId", orderId, "userLogin", userLogin);
				try {
					Debug.logInfo("*****************************", module);

					Map resp = dispatcher.runSync("changeOrderItemStatus",
							serviceCtx);// Approval of orders

					Debug.logInfo("**************************", module);
				} catch (Exception e) {
					Debug.logError(e, "************************** "
							+ e.getMessage(), module);
				}

				request.setAttribute("result", "success");
			} else if (request.getParameter("trade_status").equals(
					"WAIT_BUYER_CONFIRM_GOODS")) {
				// The seller has shipped, waiting for buyers to confirm
				// You can write data processing
				Debug
						.logInfo(
								"**************************************************************",
								module);
				Debug.logInfo("trade_status:WAIT_BUYER_CONFIRM_GOODS", module);
				Debug
						.logInfo(
								"**************************************************************",
								module);
				// If the correct payment, where the code can be omitted
				/*
				 * Map serviceCtx = UtilMisc.toMap("statusId", "ITEM_APPROVED",
				 * "orderId", orderId,"userLogin", userLogin); try {
				 * Debug.logInfo("*****************************", module);
				 * 
				 * Map resp = dispatcher.runSync("changeOrderItemStatus",
				 * serviceCtx);//Approval of orders
				 * 
				 * Debug.logInfo("**************************", module); } catch
				 * (Exception e) { Debug.logError(e,
				 * "************************** " + e.getMessage(), module); }
				 */
				request.setAttribute("result", "success");
			} else if (request.getParameter("trade_status").equals(
					"TRADE_FINISHED")) {
				// The successful conclusion of the transaction
				// You can write data processing
				Map serviceCtx = UtilMisc.toMap("orderId", orderId,
						"userLogin", userLogin);
				try {
					Debug.logInfo("Running external fulfillment '" + "'",
							module);

					Map resp = dispatcher.runSync("quickShipEntireOrder",
							serviceCtx);// Fast delivery of goods, completion of
										// the transaction

				} catch (Exception e) {
					Debug.logError(e,
							"ERROR: Could not run external fulfillment service '"
									+ "'; " + e.getMessage(), module);
				}
				Debug
						.logInfo(
								"**************************************************************",
								module);
				Debug.logInfo("trade_status:TRADE_FINISHED", module);
				Debug
						.logInfo(
								"**************************************************************",
								module);
				request.setAttribute("result", "success");
			}

		} else {
			request.setAttribute("result", "fail");
		}
		return "success";
	}

	/**
	 * Click the server sent back to the web server approach.
	 * 
	 * @param request
	 *            The HTTPRequest object for the current request
	 *@param response
	 *            The HTTPResponse object for the current request
	 *@return String specifying the exit status of this event
	 */
	public static String alipayReturn(HttpServletRequest request,
			HttpServletResponse response) {
		String path = request.getScheme() + "://" + request.getServerName()
				+ ":" + request.getServerPort();

		String return_url = path
				+ UtilProperties.getPropertyValue("alipay.properties",
						"alipay.partner.main_url");
		request.setAttribute("return_url", return_url);
		String partner = UtilProperties.getPropertyValue("alipay.properties",
				"alipay.partner"); // partner partner id (must be filled out)
		String privateKey = UtilProperties.getPropertyValue(
				"alipay.properties", "alipay.partner.key"); // partner check the
															// corresponding
															// transaction
															// security code
															// (required)
		// **********************************************************************************
		// If you do not support the https server interaction, you can use the
		// http address verification inquiries
		// String alipayNotifyURLForHttps =
		// UtilProperties.getPropertyValue("alipay.properties",
		// "alipay.NotifyURLForHttps")
		String alipayNotifyURLForhttp = UtilProperties.getPropertyValue(
				"alipay.properties", "alipay.NotifyURLForHttp")
				+ "partner="
				+ partner
				+ "&notify_id="
				+ request.getParameter("notify_id");
		String sign = request.getParameter("sign");
		// ATN to obtain payment of the return of the results of Bao, true
		// information is correct orders, false is not valid
		String responseTxt = CheckURL.check(alipayNotifyURLForhttp);

		Map params = new HashMap();
		// POST parameters obtained from the params in the new
		Map requestParams = request.getParameterMap();
		for (Iterator iter = requestParams.keySet().iterator(); iter.hasNext();) {
			String name = (String) iter.next();
			String[] values = (String[]) requestParams.get(name);
			String valueStr = "";
			for (int i = 0; i < values.length; i++) {
				valueStr = (i == values.length - 1) ? valueStr + values[i]
						: valueStr + values[i] + ",";
			}
			try {
				valueStr = new String(valueStr.getBytes("ISO-8859-1"), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			params.put(name, valueStr);
		}

		String mysign = AlipaySignatureHelper_return.sign(params, privateKey);

		// Print, he had to sign more than the calculated results and the
		// transfer to match the sign

		if (mysign.equals(request.getParameter("sign"))
				&& responseTxt.equals("true")) {

			String get_order = request.getParameter("out_trade_no");
			String get_total_fee = request.getParameter("total_fee");
			String get_subject = "";
			try {
				get_subject = new String(request.getParameter("subject")
						.getBytes("ISO-8859-1"), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			String get_body = "";
			try {
				get_body = new String(request.getParameter("body").getBytes(
						"ISO-8859-1"), "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			request.setAttribute("get_order", get_order);
			request.setAttribute("get_total_fee", get_total_fee);
			request.setAttribute("get_subject", get_subject);
			request.setAttribute("get_body", get_body + "<br>");
			request.setAttribute("trade_status", request
					.getParameter("trade_status"));
			request.setAttribute("flag", "true");
			request.setAttribute("responseTxt", responseTxt);
			if ((request.getParameter("trade_status"))
					.equals("WAIT_SELLER_SEND_GOODS")) {
				request.setAttribute("result",
						"Buyer has to pay the seller to wait for delivery"); // Buyer
																				// has
																				// to
																				// pay
																				// the
																				// seller
																				// to
																				// wait
																				// for
																				// shipping,
																				// please
																				// change
																				// the
																				// order
																				// status

			}
			if ((request.getParameter("trade_status"))
					.equals("WAIT_BUYER_CONFIRM_GOODS")) {
				request.setAttribute("result",
						"he seller has shipped, waiting for buyers to confirm"); // The
																					// seller
																					// has
																					// shipped,
																					// waiting
																					// for
																					// buyers
																					// to
																					// confirm,
																					// please
																					// change
																					// the
																					// order
																					// status

			}
			if ((request.getParameter("trade_status")).equals("TRADE_FINISHED")) {
				request.setAttribute("result",
						"Transaction has been successfully"); // Buyer has to
																// pay the
																// completion of
																// the
																// transaction,
																// please change
																// the order
																// status
				// This return to the way that only after the success of
				// re-payment back to customers.
			}
		} else {
			request.setAttribute("mysign", mysign);
			request.setAttribute("sign", sign);

			request.setAttribute("flag", "fail");
		}

		return "success";
	}
}
