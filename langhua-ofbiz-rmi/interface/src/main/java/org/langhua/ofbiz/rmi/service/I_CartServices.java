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

import java.util.Locale;
import java.util.Map;

import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.service.DispatchContext;

/**
 * Interface of RMI Shopping Cart Services.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_CartServices {

	/**
	 * Add a shopping cart item to the shopping cart. This method calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCart#addItemToEnd(org.ofbiz.order.shoppingcart.ShoppingCartItem)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.shoppingCartItem ShoppingCartItem
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: ShoppingCart if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> addItemToEnd(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Add a product to the shopping cart. This method calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCart#addItemToEnd(String, java.math.BigDecimal, java.math.BigDecimal, java.math.BigDecimal, java.util.HashMap, java.util.HashMap, String, String, org.ofbiz.service.LocalDispatcher, Boolean, Boolean)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.shoppingCart ShoppingCart
     * @param context.productId String
     * @param context.amount BigDecimal
     * @param context.quantity BigDecimal
     * @param context.unitPrice BigDecimal
     * @param context.features HashMap
     * @param context.attributes HashMap
     * @param context.prodCatalogId String
     * @param context.itemType String
     * @param context.triggerExternalOps Boolean
     * @param context.triggerPriceRules Boolean
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: ShoppingCart if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> addProductToEnd(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
	 * Make a shopping cart. This method calls
	 * {@link org.ofbiz.order.shoppingcart.ShoppingCart#ShoppingCart(GenericDelegator, String, String, Locale, String)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productStoreId String
     * @param context.webSiteId String
     * @param context.externalId String
     * @param context.locale Locale
     * @param context.currencyUom String
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: ShoppingCart if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> makeShoppingCart(DispatchContext dctx,
			Map<String, ?> context);
	
}
