/*
 * This library is part of OFBiz-RMI Component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.entity.GenericValue;
import org.ofbiz.order.shoppingcart.CartItemModifyException;
import org.ofbiz.order.shoppingcart.ItemNotFoundException;
import org.ofbiz.order.shoppingcart.ShoppingCart;
import org.ofbiz.order.shoppingcart.ShoppingCartItem;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * RMI Shopping Cart Services
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class CartServices implements I_CartServices {
    
    public static final String module = CartServices.class.getName();

    /**
     *  Empty constructor
     */
    public CartServices() {
    }
    
    /**
     *  Static method to get CartServices instance
     *  This method name is defined in java.lang.Class.
     */
    public static CartServices newInstance() {
    	return new CartServices();
    }

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_CartServices#addItemToEnd(DispatchContext, Map)
	 */
	public Map<String, Object> addItemToEnd(DispatchContext dctx,
			Map<String, ?> context) {
		ShoppingCart cart = (ShoppingCart) context.get("shoppingCart");
		ShoppingCartItem item = (ShoppingCartItem) context.get("shoppingCartItem");
		try {
			cart.addItemToEnd(item);
		} catch (CartItemModifyException e) {
			return ServiceUtil.returnError("Error found while adding item to shopping cart: " + e.getMessage());
		}
		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, cart);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_CartServices#addItemToEnd(DispatchContext, Map)
	 */
	public Map<String, Object> addProductToEnd(DispatchContext dctx,
			Map<String, ?> context) {
		ShoppingCart cart = (ShoppingCart) context.get("shoppingCart");
		String productId = (String) context.get("productId");
		BigDecimal amount = (BigDecimal) context.get("amount");
		BigDecimal quantity = (BigDecimal) context.get("quantity");
		BigDecimal unitPrice = (BigDecimal) context.get("unitPrice");
		HashMap features = (HashMap) context.get("features");
        if (features == null) features = new HashMap();
		HashMap attributes = (HashMap) context.get("attributes");
        if (attributes == null) attributes = new HashMap();
		String prodCatalogId = (String) context.get("prodCatalogId");
		String itemType = (String) context.get("itemType");
		Boolean triggerExternalOps = (Boolean) context.get("triggerExternalOps");
        if (triggerExternalOps == null) triggerExternalOps = Boolean.FALSE;
		Boolean triggerPriceRules = (Boolean) context.get("triggerPriceRules");
        if (triggerPriceRules == null) triggerPriceRules = Boolean.FALSE;
		try {
			cart.addItemToEnd(productId, amount, quantity, unitPrice, features, attributes, prodCatalogId, itemType, dctx.getDispatcher(), triggerExternalOps, triggerPriceRules);
		} catch (CartItemModifyException e) {
			return ServiceUtil.returnError("Error found while adding item to shopping cart: " + e.getMessage());
		} catch (ItemNotFoundException e) {
			return ServiceUtil.returnError("Error found while adding item to shopping cart: " + e.getMessage());
		}
		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, cart);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_CartServices#makeShoppingCart(DispatchContext, Map)
	 */
	public Map<String, Object> makeShoppingCart(DispatchContext dctx,
			Map<String, ?> context) {
		String productStoreId = (String) context.get("productStoreId");
		String webSiteId = (String) context.get("webSiteId");
		String externalId = (String) context.get("externalId");
		Locale locale = (Locale) context.get("locale");
		String currencyUom = (String) context.get("currencyUom");
		GenericValue user = (GenericValue) context.get("userLogin");
		ShoppingCart cart = new ShoppingCart(dctx.getDelegator(), productStoreId, webSiteId, locale, currencyUom);
		cart.setExternalId(externalId);
		try {
			cart.setUserLogin(user, dctx.getDispatcher());
		} catch (CartItemModifyException e) {
			return ServiceUtil.returnError("Error found while setting user to shopping cart: " + e.getMessage());
		}
		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, cart);
		return result;
	}

}
