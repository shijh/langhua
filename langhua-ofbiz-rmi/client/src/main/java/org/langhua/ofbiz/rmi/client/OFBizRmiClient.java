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
package org.langhua.ofbiz.rmi.client;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javolution.util.FastMap;

import org.ofbiz.order.shoppingcart.ShoppingCart;
import org.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.ofbiz.service.GenericRequester;
import org.ofbiz.service.GenericResultWaiter;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.ServiceUtil;
import org.ofbiz.service.rmi.RemoteDispatcher;
import org.ofbiz.base.util.Debug;
import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericPK;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.model.DynamicViewEntity;

/**
 * An RMI client implements.
 * 
 * Note: perhaps you should change RemoteDispatcherImpl.exportAll from false to true to make
 * OFBiz java service open to rmi; or you could add export="true" to the java services you 
 * want to call from rmi.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class OFBizRmiClient implements I_RMIClient {

	public static String module = OFBizRmiClient.class.getName();

	protected final static String RMI_URL = "rmi://localhost:1099/RMIDispatcher";
	protected RemoteDispatcher m_rd = null;
	protected String m_remoteClientName = null;
	protected String m_currencyUom = "RMB";
	protected Locale m_locale = new Locale("zh");

	public OFBizRmiClient(String rmiUrl) {
		try {
			String RMIDispatcherUrl = RMI_URL;
			if (rmiUrl != null && !rmiUrl.trim().equals("")) {
				RMIDispatcherUrl = rmiUrl;
			}
			Debug.log(RMIDispatcherUrl, module);
			m_rd = (RemoteDispatcher) Naming.lookup(RMIDispatcherUrl);
		} catch (NotBoundException e) {
			Debug.logError(e, module);
		} catch (MalformedURLException e) {
			Debug.logError(e, module);
		} catch (RemoteException e) {
			Debug.logError(e, module);
		}
	}

	public OFBizRmiClient() {
		try {
			String RMIDispatcherUrl = RMI_URL;

			Debug.logInfo(RMIDispatcherUrl, module);

			m_rd = (RemoteDispatcher) Naming.lookup(RMIDispatcherUrl);
		} catch (NotBoundException e) {
			Debug.logError(e, module);
		} catch (MalformedURLException e) {
			Debug.logError(e, module);
		} catch (RemoteException e) {
			Debug.logError(e, module);
		}
	}

	public String getCurrencyUom() {
		return m_currencyUom;
	}

	public void setCurrencyUom(String currencyUom) {
		m_currencyUom = currencyUom;
	}

	public String getRemoteClientName() {
		return m_remoteClientName;
	}

	public void setRemoteClientName(String name) {
		m_remoteClientName = name;
	}

	/**
	 * Check whether a service name exists in RMI services.
	 * 
	 * @param serviceName
	 *            String of a service name
	 * @return true if the service exists; otherwise, false.
	 */
	public static boolean serviceExists(String serviceName) {
		return RMI_SERVICES.contains(serviceName);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.client.I_RMIClient#checkContext(String, Map)
	 * 
	 * TODO: Currently, this method is not beautiful, will be improved gradually.
	 */
	@SuppressWarnings("unchecked")
	public boolean checkContext(String serviceName,
			Map<String, ? extends Object> context) {
		if (serviceName.equals("testScv")) {
			String message = (String) context.get("message");
			if (message == null) {
				Debug.logError("In serice " + serviceName
						+ ", message should not be null.", module);
				return false;
			}
		/**
		 *  Product RMI Services
		 */
		} else if (serviceName.equals("calculateProductPrice")) {
			GenericValue product = (GenericValue) context.get("product");
			if (product == null) {
				Debug.logError("In serice " + serviceName
						+ ", product should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findProductById")) {
			String idToFind = (String) context.get("idToFind");
			if (idToFind == null) {
				Debug.logError("In serice " + serviceName
						+ ", idToFind should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getAllProductVariants")) {
			String productId = (String) context.get("productId");
			if (productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProductCategoryMembers")) {
			String categoryId = (String) context.get("categoryId");
			if (categoryId == null) {
				Debug.logError("In serice " + serviceName
						+ ", categoryId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getCategoryVariantProducts")) {
			String productCategoryId = (String) context.get("productCategoryId");
			List productFeatures = (List) context.get("productFeatures");
			if (productCategoryId == null || productFeatures == null) {
				Debug.logError("In serice " + serviceName
						+ ", productCategoryId and productFeatures should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getParentProduct")) {
			String productId = (String) context.get("productId");
			if (productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getPreviousNextProducts")) {
			String productId = (String) context.get("productId");
			String categoryId = (String) context.get("categoryId");
			if (categoryId == null || productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", categoryId and productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProdCatalogCategories")) {
			String prodCatalogId = (String) context.get("prodCatalogId");
			if (prodCatalogId == null) {
				Debug.logError("In serice " + serviceName
						+ ", prodCatalogId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProductCategoryAndLimitedMembers")) {
			String productCategoryId = (String) context.get("productCategoryId");
			Integer defaultViewSize = (Integer) context.get("defaultViewSize");
			Boolean limitView = (Boolean) context.get("limitView");
			if (productCategoryId == null || defaultViewSize == null || limitView == null) {
				Debug.logError("In serice " + serviceName
						+ ", productCategoryId, defaultViewSize and limitView should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProductStore")) {
			String productStoreId = (String) context.get("productStoreId");
			if (productStoreId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productStoreId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getStoreCatalogs")) {
			String productStoreId = (String) context.get("productStoreId");
			if (productStoreId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productStoreId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getStorePaymentMethodTypeIds")) {
			String productStoreId = (String) context.get("productStoreId");
			if (productStoreId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productStoreId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getVariantVirtualAssocs")) {
			GenericValue variantProduct = (GenericValue) context.get("variantProduct");
			if (variantProduct == null) {
				Debug.logError("In serice " + serviceName
						+ ", variantProduct should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("isSellable")) {
			String productId = (String) context.get("productId");
			Timestamp atTime = (Timestamp) context.get("atTime");
			if (productId == null || atTime == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId and atTime should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("isStoreInventoryAvailable")) {
			String productStoreId = (String) context.get("productStoreId");
			String productId = (String) context.get("productId");
			BigDecimal quantity = (BigDecimal) context.get("quantity");
			if (productStoreId == null || productId == null || quantity == null) {
				Debug.logError("In serice " + serviceName
						+ ", productStoreId, productId and quantity should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getAllProductVariants")) {
			String productId = (String) context.get("productId");
			if (productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProduct")) {
			String productId = (String) context.get("productId");
			if (productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProductVariant")) {
			String productId = (String) context.get("productId");
			Map selectedFeatures = (Map) context.get("selectedFeatures");
			if (productId == null || selectedFeatures == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId and selectedFeatures should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getProductFeatures")) {
			String productId = (String) context.get("productId");
			if (productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", productId should not be null.", module);
				return false;
			}
		/**
		 *  Order RMI Services
		 */
		} else if (serviceName.equals("assignItemShipGroup")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			Integer fromGroupIndex = (Integer) context.get("fromGroupIndex");
			Integer toGroupIndex = (Integer) context.get("toGroupIndex");
			Integer itemIndex = (Integer) context.get("itemIndex");
			BigDecimal quantity = (BigDecimal) context.get("quantity");
			if (shoppingCart == null || fromGroupIndex == null || toGroupIndex == null
					|| itemIndex == null || quantity == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart, fromGroupIndex, toGroupIndex, itemIndex and quantity should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("checkPaymentAmountForRefund")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("checkReturnComplete")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("runShoppingListAutoReorder")) {
			GenericValue userLogin = (GenericValue) context.get("userLogin");
			if (userLogin == null) {
				Debug.logError("In serice " + serviceName
						+ ", userLogin should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("storeOrder")) {
			String currencyUom = (String) context.get("currencyUom");
			List orderAdjustments = (List) context.get("orderAdjustments");
			List orderItems = (List) context.get("orderItems");
			List orderTerms = (List) context.get("orderTerms");
			String orderTypeId = (String) context.get("orderTypeId");
			String partyId = (String) context.get("partyId");
			if (currencyUom == null || orderAdjustments == null || orderItems == null || 
					orderTerms == null || orderTypeId == null || partyId == null) {
				Debug.logError("In serice " + serviceName
						+ ", currencyUom, orderAdjustments, orderItems, orderTypeId and partyId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createOrderFromShoppingCart")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createPaymentApplicationsFromReturnItemResponse")) {
			String returnItemResponseId = (String) context.get("returnItemResponseId");
			if (returnItemResponseId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnItemResponseId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createPaymentFromPreference")) {
			String orderPaymentPreferenceId = (String) context.get("orderPaymentPreferenceId");
			if (orderPaymentPreferenceId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderPaymentPreferenceId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createOrderPaymentPreference")) {
			String orderId = (String) context.get("orderId");
			String paymentMethodTypeId = (String) context.get("paymentMethodTypeId");
			if (orderId == null || paymentMethodTypeId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId and paymentMethodTypeId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createReturnAdjustment")) {
			// do nothing
		} else if (serviceName.equals("createSimpleNonProductSalesOrder")) {
			String currency = (String) context.get("currency");
			Map itemMap = (Map) context.get("itemMap");
			String partyId = (String) context.get("partyId");
			String paymentMethodId = (String) context.get("paymentMethodId");
			String productStoreId = (String) context.get("productStoreId");
			if (currency == null || itemMap == null || partyId == null || paymentMethodId == null ||
					productStoreId == null) {
				Debug.logError("In serice " + serviceName
						+ ", currency, itemMap, partyId, paymentMethodId and productStoreId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findOrders")) {
			Integer viewIndex = (Integer) context.get("viewIndex");
			Integer viewSize = (Integer) context.get("viewSize");
			if (viewIndex == null || viewSize == null) {
				Debug.logError("In serice " + serviceName
						+ ", viewIndex and viewSize should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("fulfillDigitalItems")) {
			String orderId = (String) context.get("orderId");
			List orderItems = (List) context.get("orderItems");
			if (orderId == null || orderItems == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId and orderItems should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getAvailableBalance")) {
			String finAccountId = (String) context.get("finAccountId");
			Timestamp authorizationDate = (Timestamp) context.get("authorizationDate");
			if (finAccountId == null || authorizationDate == null) {
				Debug.logError("In serice " + serviceName
						+ ", finAccountId and authorizationDate should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getBalance")) {
			String finAccountId = (String) context.get("finAccountId");
			Timestamp authorizationDate = (Timestamp) context.get("authorizationDate");
			if (finAccountId == null || authorizationDate == null) {
				Debug.logError("In serice " + serviceName
						+ ", finAccountId and authorizationDate should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getFinAccountFromCode")) {
			String finAccountCode = (String) context.get("finAccountCode");
			if (finAccountCode == null) {
				Debug.logError("In serice " + serviceName
						+ ", finAccountCode should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getOrderAddress")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getOrderHeaderInformationById")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getOrderItemInvoicedAmountAndQuantity")) {
			String orderId = (String) context.get("orderId");
			String orderItemSeqId = (String) context.get("orderItemSeqId");
			if (orderId == null || orderItemSeqId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId and orderItemSeqId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getOrderShippingAmount")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getRequirementsForSupplier")) {
			// do nothing
		} else if (serviceName.equals("getReturnableItems")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getReturnableQuantity")) {
			GenericValue orderItem = (GenericValue) context.get("orderItem");
			if (orderItem == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderItem should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getReturnAmountByOrder")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getReturnItemInitialCost")) {
			String returnId = (String) context.get("returnId");
			String returnItemSeqId = (String) context.get("returnItemSeqId");
			if (returnId == null || returnItemSeqId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId and returnItemSeqId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getShoppingCartData")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getShoppingCartItemIndex")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			String productId = (String) context.get("productId");
			if (shoppingCart == null || productId == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart and productId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("loadCartForUpdate")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("loadCartFromOrder")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("loadCartFromQuote")) {
			String quoteId = (String) context.get("quoteId");
			if (quoteId == null) {
				Debug.logError("In serice " + serviceName
						+ ", quoteId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("loadCartFromShoppingList")) {
			String shoppingListId = (String) context.get("shoppingListId");
			if (shoppingListId == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingListId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("makeShoppingListFromOrder")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("massChangeOrderStatus")) {
			List orderIdList = (List) context.get("orderIdList");
			String statusId = (String) context.get("statusId");
			if (orderIdList == null || statusId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderIdList and statusId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("prepareVendorShipGroups")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("processCreditReturn")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("processOrderPayments")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("processRefundReturn")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("processReplacementReturn")) {
			String returnId = (String) context.get("returnId");
			String returnTypeId = (String) context.get("returnTypeId");
			if (returnId == null || returnTypeId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId and returnTypeId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("recalcShippingTotal")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("refundBillingAccountPayment")) {
			GenericValue orderPaymentPreference = (GenericValue) context.get("orderPaymentPreference");
			BigDecimal refundAmount = (BigDecimal) context.get("refundAmount");
			if (orderPaymentPreference == null || refundAmount == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderPaymentPreference and refundAmount should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("removeOrderRole")) {
			String orderId = (String) context.get("orderId");
			String partyId = (String) context.get("partyId");
			String roleTypeId = (String) context.get("roleTypeId");
			if (orderId == null || partyId == null || roleTypeId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId, partyId and roleTypeId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("resetGrandTotal")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("resetShipGroupItems")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("runShoppingListAutoReorder")) {
			// do nothing
		} else if (serviceName.equals("saveUpdatedCartToOrder")) {
			Map changeMap = (Map) context.get("changeMap");
			String orderId = (String) context.get("orderId");
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (changeMap == null || orderId == null || shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", changeMap, orderId and shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendOrderBackorderNotification")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendOrderChangeNotification")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendOrderCompleteNotification")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendOrderConfirmation")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendOrderPayRetryNotification")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendProcessNotification")) {
			String adminEmailList = (String) context.get("adminEmailList");
			String workEffortId = (String) context.get("workEffortId");
			if (adminEmailList == null || workEffortId == null) {
				Debug.logError("In serice " + serviceName
						+ ", adminEmailList and workEffortId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendQuoteReportMail")) {
			String emailType = (String) context.get("emailType");
			String quoteId = (String) context.get("quoteId");
			String sendTo = (String) context.get("sendTo");
			if (emailType == null || quoteId == null || sendTo == null) {
				Debug.logError("In serice " + serviceName
						+ ", emailType, quoteId and sendTo should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendReturnAcceptNotification")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendReturnCancelNotification")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("sendReturnCompleteNotification")) {
			String returnId = (String) context.get("returnId");
			if (returnId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("changeOrderItemStatus")) {
			String orderId = (String) context.get("orderId");
			String statusId = (String) context.get("statusId");
			if (orderId == null || statusId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId and statusId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("changeOrderPaymentStatus")) {
			String orderPaymentPreferenceId = (String) context.get("orderPaymentPreferenceId");
			if (orderPaymentPreferenceId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderPaymentPreferenceId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("changeOrderStatus")) {
			String orderId = (String) context.get("orderId");
			String statusId = (String) context.get("statusId");
			if (orderId == null || statusId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId and statusId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("setCartOtherOptions")) {
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("setCartShippingOptions")) {
			Integer groupIndex = (Integer) context.get("groupIndex");
			String shipmentMethodString = (String) context.get("shipmentMethodString");
			Boolean isGift = (Boolean) context.get("isGift");
			Boolean maySplit = (Boolean) context.get("maySplit");
			String shippingContactMechId = (String) context.get("shippingContactMechId");
			ShoppingCart shoppingCart = (ShoppingCart) context.get("shoppingCart");
			if (groupIndex == null || maySplit == null || shipmentMethodString == null || 
					isGift == null || shippingContactMechId == null || shoppingCart == null) {
				Debug.logError("In serice " + serviceName
						+ ", groupIndex, isGift, maySplit, shipmentMethodString, shippingContactMechId and shoppingCart should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createShoppingListRecurrence")) {
			// do nothing
		} else if (serviceName.equals("splitShipmentMethodString")) {
			// do nothing
		} else if (serviceName.equals("storeQuote")) {
			// do nothing
		} else if (serviceName.equals("updateOrderItems")) {
			Map itemPriceMap = (Map) context.get("itemPriceMap");
			Map itemQtyMap = (Map) context.get("itemQtyMap");
			String orderId = (String) context.get("orderId");
			Map overridePriceMap = (Map) context.get("overridePriceMap");
			if (itemPriceMap == null || itemQtyMap == null || orderId == null || overridePriceMap == null) {
				Debug.logError("In serice " + serviceName
						+ ", itemPriceMap, itemQtyMap, orderId and overridePriceMap should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updateOrderPaymentPreference")) {
			String orderPaymentPreferenceId = (String) context.get("orderPaymentPreferenceId");
			if (orderPaymentPreferenceId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderPaymentPreferenceId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updateReturnAdjustment")) {
			String returnAdjustmentId = (String) context.get("returnAdjustmentId");
			if (returnAdjustmentId == null) {
				Debug.logError("In serice " + serviceName
						+ ", returnAdjustmentId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updateShoppingListQuantitiesFromOrder")) {
			String orderId = (String) context.get("orderId");
			if (orderId == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updateTrackingNumber")) {
			String orderId = (String) context.get("orderId");
			String shipGroupSeqId = (String) context.get("shipGroupSeqId");
			String trackingNumber = (String) context.get("trackingNumber");
			if (orderId == null || shipGroupSeqId == null || trackingNumber == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderId, shipGroupSeqId and trackingNumber should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("validatePin")) {
			String finAccountId = (String) context.get("finAccountId");
			String pinNumber = (String) context.get("pinNumber");
			if (finAccountId == null || pinNumber == null) {
				Debug.logError("In serice " + serviceName
						+ ", finAccountId and pinNumber should not be null.", module);
				return false;
			}
		/**
		 *  Login RMI Services
		 */
		} else if (serviceName.equals("checkNewPassword")) {
			String currentPassword = (String) context.get("currentPassword");
			String newPassword = (String) context.get("newPassword");
			String newPasswordVerify = (String) context.get("newPasswordVerify");
			String passwordHint = (String) context.get("passwordHint");
			String errorMessageList = (String) context.get("errorMessageList");
			Boolean ignoreCurrentPassword = (Boolean) context.get("ignoreCurrentPassword");
			if (currentPassword == null || newPassword == null || newPasswordVerify == null || 
					passwordHint == null || errorMessageList == null || ignoreCurrentPassword == null) {
				Debug.logError("In serice " + serviceName
						+ ", currentPassword, newPassword, newPasswordVerify, passwordHint, errorMessageList and ignoreCurrentPassword should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("createUserLogin")) {
			String currentPassword = (String) context.get("currentPassword");
			String currentPasswordVerify = (String) context.get("currentPasswordVerify");
			String userLoginId = (String) context.get("userLoginId");
			if (currentPassword == null || currentPasswordVerify == null || userLoginId == null) {
				Debug.logError("In serice " + serviceName
						+ ", currentPassword, currentPasswordVerify and userLoginId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updatePassword")) {
			// do nothing
		} else if (serviceName.equals("updateUserLoginId")) {
			String userLoginId = (String) context.get("userLoginId");
			if (userLoginId == null) {
				Debug.logError("In serice " + serviceName
						+ ", userLoginId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("updateUserLoginSecurity")) {
			String enabled = (String) context.get("enabled");
			String userLoginId = (String) context.get("userLoginId");
			if (enabled == null || userLoginId == null) {
				Debug.logError("In serice " + serviceName
						+ ", enabled and userLoginId should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("userLogin")) {
			String login_password = (String) context.get("login.password");
			String login_username = (String) context.get("login.username");
			if (login_password == null || login_username == null) {
				Debug.logError("In serice " + serviceName
						+ ", login.password and login.username should not be null.", module);
				return false;
			}
		/**
		 *  Entity Util RMI Services
		 */
		} else if (serviceName.equals("filterByAnd")) {
			String list = (String) context.get("list");
			String fields = (String) context.get("fields");
			String exprs = (String) context.get("exprs");
			if (list == null || fields == null || exprs == null) {
				Debug.logError("In serice " + serviceName
						+ ", list, fields and exprs should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("filterByCondition")) {
			List orderItems = (List) context.get("orderItems");
			EntityCondition entityCondition = (EntityCondition) context.get("entityCondition");
			if (orderItems == null || entityCondition == null) {
				Debug.logError("In serice " + serviceName
						+ ", orderItems and entityCondition should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("filterByDate")) {
			List datedValues = (List) context.get("datedValues");
			if (datedValues == null) {
				Debug.logError("In serice " + serviceName
						+ ", datedValues should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("filterByOr")) {
			List values = (List) context.get("values");
			if (values == null) {
				Debug.logError("In serice " + serviceName
						+ ", values should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getFieldListFromEntityList")) {
			List genericValueList = (List) context.get("genericValueList");
			String fieldName = (String) context.get("fieldName");
			if (genericValueList == null || fieldName == null) {
				Debug.logError("In serice " + serviceName
						+ ", genericValueList and fieldName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getFilterByDateExpr")) {
			Timestamp moment = (Timestamp) context.get("moment");
			if (moment == null) {
				Debug.logError("In serice " + serviceName
						+ ", moment should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getFirst")) {
			List list = (List) context.get("list");
			if (list == null) {
				Debug.logError("In serice " + serviceName
						+ ", list should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getIndexOf")) {
			List list = (List) context.get("list");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (list == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", list and genericValue should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getLinkedList")) {
			List list = (List) context.get("list");
			if (list == null) {
				Debug.logError("In serice " + serviceName
						+ ", list should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getValueFromMap")) {
			Map lookupMap = (Map) context.get("lookupMap");
			GenericPK genericPK = (GenericPK) context.get("genericPK");
			if (lookupMap == null || genericPK == null) {
				Debug.logError("In serice " + serviceName
						+ ", lookupMap and genericPK should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("orderBy")) {
			List list = (List) context.get("list");
			List orderByList = (List) context.get("orderByList");
			if (list == null || orderByList == null) {
				Debug.logError("In serice " + serviceName
						+ ", list and orderByList should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("putIntoMap")) {
			Map storeMap = (Map) context.get("storeMap");
			Object object = (Object) context.get("object");
			GenericPK genericPK = (GenericPK) context.get("genericPK");
			if (storeMap == null || object == null || genericPK == null) {
				Debug.logError("In serice " + serviceName
						+ ", storeMap, object and genericPK should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("removeValueFromList")) {
			List list = (List) context.get("list");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (list == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", list and genericValue should not be null.", module);
				return false;
			}
		/**
		 *  Entity RMI Services
		 */
		} else if (serviceName.equals("createValue")) {
			// do nothing
		} else if (serviceName.equals("getPrimaryKey")) {
			GenericEntity genericEntity = (GenericEntity) context.get("genericEntity");
			if (genericEntity == null) {
				Debug.logError("In serice " + serviceName
						+ ", genericEntity should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("setFieldValue")) {
			String name = (String) context.get("name");
			Object object = (Object) context.get("object");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (name == null || object == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", name, object and genericValue should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("setAllFields")) {
			Boolean setIfEmpty = (Boolean) context.get("setIfEmpty");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (setIfEmpty == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", setIfEmpty and genericValue should not be null.", module);
				return false;
			}
		/**
		 *  Delegator RMI Services
		 */
		} else if (serviceName.equals("create")) {
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", genericValue should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("find")) {
			String entityName = (String) context.get("entityName");
			if (entityName == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("find")) {
			String entityName = (String) context.get("entityName");
			if (entityName == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findCountByCondition")) {
			String entityName = (String) context.get("entityName");
			if (entityName == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findList")) {
			String entityName = (String) context.get("entityName");
			if (entityName == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findListIteratorByCondition")) {
			DynamicViewEntity dynamicViewEntity = (DynamicViewEntity) context.get("dynamicViewEntity");
			if (dynamicViewEntity == null) {
				Debug.logError("In serice " + serviceName
						+ ", dynamicViewEntity should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("findOne")) {
			// do nothing
		} else if (serviceName.equals("getModelEntity")) {
			String entityName = (String) context.get("entityName");
			if (entityName == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getNextSeqId")) {
			String seqName = (String) context.get("seqName");
			Long staggerMax = (Long) context.get("staggerMax");
			if (seqName == null || staggerMax == null) {
				Debug.logError("In serice " + serviceName
						+ ", seqName and staggerMax should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getRelated")) {
			String relationName = (String) context.get("relationName");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (relationName == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", relationName and genericValue should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("getRelatedOne")) {
			String relationName = (String) context.get("relationName");
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (relationName == null || genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", relationName and genericValue should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("makeValue")) {
			String entityName = (String) context.get("entityName");
			Map<String, Object> fields = (Map<String, Object>) context.get("fields");
			if (entityName == null || fields == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName or fields should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("removeByCondition")) {
			String entityName = (String) context.get("entityName");
			EntityCondition condition = (EntityCondition) context.get("condition");
			if (entityName == null || condition == null) {
				Debug.logError("In serice " + serviceName
						+ ", entityName or condition should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("store")) {
			GenericValue genericValue = (GenericValue) context.get("genericValue");
			if (genericValue == null) {
				Debug.logError("In serice " + serviceName
						+ ", genericValue should not be null.", module);
				return false;
			}
		/**
		 *  Cart RMI Services
		 */
		} else if (serviceName.equals("addItemToEnd")) {
			ShoppingCart cart = (ShoppingCart) context.get("shoppingCart");
			ShoppingCartItem item = (ShoppingCartItem) context.get("shoppingCartItem");
			if (cart == null || item == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart and shoppingCartItem should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("addProductToEnd")) {
			ShoppingCart cart = (ShoppingCart) context.get("shoppingCart");
			String productId = (String) context.get("productId");
			BigDecimal amount = (BigDecimal) context.get("amount");
			BigDecimal quantity = (BigDecimal) context.get("quantity");
			BigDecimal unitPrice = (BigDecimal) context.get("unitPrice");
			String prodCatalogId = (String) context.get("prodCatalogId");
			String itemType = (String) context.get("itemType");
			if (cart == null || productId == null || amount == null || quantity == null
					|| unitPrice == null || prodCatalogId == null || itemType == null) {
				Debug.logError("In serice " + serviceName
						+ ", shoppingCart, productId, amount, quantity, unitPrice, prodCatalogId and itemType should not be null.", module);
				return false;
			}
		} else if (serviceName.equals("makeShoppingCart")) {
			String productStoreId = (String) context.get("productStoreId");
			String currencyUom = (String) context.get("currencyUom");
			if (productStoreId == null || currencyUom == null) {
				Debug.logError("In serice " + serviceName
						+ ", productStoreId and currencyUom should not be null.", module);
				return false;
			}
		/**
		 * no match
		 */
		} else {
			Debug
					.logError(
							"Don't know how to check the context of "
									+ serviceName
									+ ", please add a piece of program in checkContext method of "
									+ module + ".", module);
		}
		return true;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.client.I_RMIClient#service(String, Map)
	 */
	public Map<String, ? extends Object> service(String serviceName,
			Map<String, ? extends Object> context) {
		if (Debug.warningOn()) {
			boolean isOK = serviceExists(serviceName);
			if (!isOK) {
				return ServiceUtil
						.returnError(serviceName + " is not found. Please use a legal service name.");
			}

			isOK = checkContext(serviceName, context);
			if (!isOK) {
				return ServiceUtil
						.returnError("Please correct " + serviceName + "'s context parameters.");
			}
		}

		Map<String, ? extends Object> result = FastMap.newInstance();
		try {
			result = m_rd.runSync(serviceName, context);
		} catch (GenericServiceException e) {
			Debug.log(e);
			return ServiceUtil.returnError(e.getMessage());
		} catch (RemoteException e) {
			Debug.log(e);
			return ServiceUtil.returnError(e.getMessage());
		}
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.client.I_RMIClient#service(String, Map, Map)
	 */
	public Map<String, ? extends Object> service(String serviceName,
			Map<String, ? extends Object> context,
			Map<String, ? extends Object> params) {
		if (Debug.warningOn()) {
			boolean isOK = serviceExists(serviceName);
			if (!isOK) {
				return ServiceUtil
						.returnError("Please use a legal service name.");
			}

			isOK = checkContext(serviceName, context);
			if (!isOK) {
				return ServiceUtil
						.returnError("Please correct context parameters.");
			}
		}

		Map<String, ? extends Object> result = FastMap.newInstance();
		int transactionTimeout = ((Integer) params.get("transactionTimeout"))
				.intValue();
		boolean requireNewTransaction = ((Boolean) params
				.get("requireNewTransaction")).booleanValue();
		try {
			result = m_rd.runSync(serviceName, context, transactionTimeout,
					requireNewTransaction);
		} catch (GenericServiceException e) {
			return ServiceUtil.returnError(e.getMessage());
		} catch (RemoteException e) {
			return ServiceUtil.returnError(e.getMessage());
		}
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.client.I_RMIClient#service(String, Map, int,
	 *      Map)
	 */
	public Map<String, ? extends Object> service(String serviceName,
			Map<String, ? extends Object> context, int command,
			Map<String, ? extends Object> params) {
		if (Debug.warningOn()) {
			boolean isOK = serviceExists(serviceName);
			if (!isOK) {
				return ServiceUtil
						.returnError("Please use a legal service name.");
			}

			isOK = checkContext(serviceName, context);
			if (!isOK) {
				return ServiceUtil
						.returnError("Please correct context parameters.");
			}
		}

		Map<String, ? extends Object> result = FastMap.newInstance();
		GenericRequester requester;
		boolean persist;
		int transactionTimeout;
		boolean requireNewTransaction;
		long startTime;

		switch (command) {
		case RUN_ASYNC:
			try {
				m_rd.runAsync(serviceName, context);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case RUN_ASYNC_PERSIST:
			persist = ((Boolean) params.get("persist")).booleanValue();
			try {
				m_rd.runAsync(serviceName, context, persist);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case RUN_ASYNC_REQUESTER:
			requester = (GenericRequester) params.get("requester");
			try {
				m_rd.runAsync(serviceName, context, requester);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case RUN_ASYNC_REQUESTER_PERSIST:
			requester = (GenericRequester) params.get("requester");
			persist = ((Boolean) params.get("persist")).booleanValue();
			try {
				m_rd.runAsync(serviceName, context, requester, persist);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case RUN_ASYNCWAIT:
			try {
				GenericResultWaiter resultWaiter = m_rd.runAsyncWait(
						serviceName, context);
				return resultWaiter.getResult();
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}

		case RUN_ASYNCWAIT_PERSIST:
			persist = ((Boolean) params.get("persist")).booleanValue();
			try {
				GenericResultWaiter resultWaiter = m_rd.runAsyncWait(
						serviceName, context, persist);
				return resultWaiter.getResult();
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}

		case RUN_SYNC:
			try {
				result = m_rd.runSync(serviceName, context);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return result;

		case RUN_SYNC_TRANSACTION:
			transactionTimeout = ((Integer) params.get("transactionTimeout"))
					.intValue();
			requireNewTransaction = ((Boolean) params
					.get("requireNewTransaction")).booleanValue();
			try {
				result = m_rd.runSync(serviceName, context, transactionTimeout,
						requireNewTransaction);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return result;

		case RUN_SYNCIGNORE:
			try {
				m_rd.runSyncIgnore(serviceName, context);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case RUN_SYNCIGNORE_TRANSACTION:
			transactionTimeout = ((Integer) params.get("transactionTimeout"))
					.intValue();
			requireNewTransaction = ((Boolean) params
					.get("requireNewTransaction")).booleanValue();
			try {
				m_rd.runSyncIgnore(serviceName, context, transactionTimeout,
						requireNewTransaction);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case SCHEDULE:
			startTime = ((Long) params.get("startTime")).longValue();
			try {
				m_rd.schedule(serviceName, context, startTime);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		case SCHEDULE_START_END:
			startTime = ((Long) params.get("startTime")).longValue();
			int frequency = ((Integer) params.get("frequency")).intValue();
			int interval = ((Integer) params.get("interval")).intValue();
			long endTime = ((Integer) params.get("endTime")).intValue();
			try {
				m_rd.schedule(serviceName, context, startTime, frequency,
						interval, endTime);
			} catch (GenericServiceException e) {
				return ServiceUtil.returnError(e.getMessage());
			} catch (RemoteException e) {
				return ServiceUtil.returnError(e.getMessage());
			}
			return ServiceUtil.returnSuccess();

		default:
			Debug
					.logError(
							command
									+ "is not a valid command choice. Please correct the command parameter.",
							module);
		}
		return result;
	}

}
