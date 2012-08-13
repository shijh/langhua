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

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javolution.util.FastSet;

import org.ofbiz.base.util.Debug;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

/**
 * RMI Product Services
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class ProductServices implements I_ProductServices {

	public static final String module = ProductServices.class.getName();

    /**
     *  Empty constructor
     */
    public ProductServices() {
    }
    
    /**
     *  Static method to get ProductServices instance
     *  This method name is defined in java.lang.Class.
     */
    public static ProductServices newInstance() {
    	return new ProductServices();
    }

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProductStore(DispatchContext,
	 *      Map)
	 */
	public Map<String, Object> getProductStore(DispatchContext ctx,
			Map<String, ?> context) {
		GenericDelegator delegator = ctx.getDelegator();
		String productStoreId = (String) context.get("productStoreId");

		GenericValue genericValue = org.ofbiz.product.store.ProductStoreWorker
				.getProductStore(productStoreId, delegator);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		if (genericValue != null) {
			result.put(I_DelegatorService.RMI_RESULTS, genericValue);
		}
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getStorePaymentMethodTypeIds(DispatchContext,
	 *      Map)
	 */
	public Map<String, Object> getStorePaymentMethodTypeIds(
			DispatchContext ctx, Map<String, ?> context) {
		GenericDelegator delegator = ctx.getDelegator();
		String productStoreId = (String) context.get("productStoreId");
		boolean useCache = ((Boolean) context.get("useCache")).booleanValue();
		List<GenericValue> values;
		Map<String, Object> expressions = new HashMap<String, Object>();
		expressions.put("productStoreId", productStoreId);
		try {
			values = delegator.findList("ProductStorePaymentSetting",
					EntityCondition.makeCondition(expressions), null, null,
					null, useCache);
		} catch (GenericEntityException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}
        Set<String> distinctSet = FastSet.newInstance();
		if (values != null) {
			for (GenericValue value : values) {
				String fieldValue = value.getString("paymentMethodTypeId");
				if (fieldValue != null && !distinctSet.contains(fieldValue)) {
					distinctSet.add(fieldValue);
				}
			}
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, distinctSet);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_ProductServices#isStoreInventoryAvailable(DispatchContext,
	 *      Map)
	 */
	public Map<String, Object> isStoreInventoryAvailable(DispatchContext ctx,
			Map<String, ?> context) {

		LocalDispatcher dispatcher = ctx.getDispatcher();
		boolean value = false;
		try {
			Map<String, Object> invReqResult = dispatcher.runSync("isStoreInventoryAvailable", context);
            if (ServiceUtil.isError(invReqResult)) {
                Debug.logError("Error calling isStoreInventoryAvailable service, result is: " + invReqResult, module);
                return ServiceUtil.returnError(invReqResult.toString());
            } else {
            	value = "Y".equals((String) invReqResult.get("available"));
            }
		} catch (GenericServiceException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, Boolean.valueOf(value));
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getAllProductVariants(DispatchContext, Map)
	 */
	public Map<String, Object> getAllProductVariants(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.product.ProductServices.prodFindAllVariants(dctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProduct(DispatchContext, Map)
	 */
	public Map<String, Object> getProduct(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.product.ProductServices.prodFindProduct(dctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#findProductById(DispatchContext, Map)
	 */
	public Map<String, Object> findProductById(DispatchContext ctx,
			Map<String, Object> context) {
		return org.ofbiz.product.product.ProductServices.findProductById(ctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProductVariant(DispatchContext, Map)
	 */
	public Map<String, Object> getProductVariant(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.product.ProductServices.prodFindSelectedVariant(dctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProductFeatures(DispatchContext, Map)
	 */
	public Map<String, Object> getProductFeatures(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.product.ProductServices.prodGetFeatures(dctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getStoreCatalogs(DispatchContext, Map)
	 */
	public Map<String, Object> getStoreCatalogs(DispatchContext ctx,
			Map<String, ?> context) {
		GenericDelegator delegator = ctx.getDelegator();
		String productStoreId = (String) context.get("productStoreId");
		List<GenericValue> values = org.ofbiz.product.catalog.CatalogWorker.getStoreCatalogs(delegator, productStoreId);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, values);
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#isSellable(DispatchContext, Map)
	 */
	public Map<String, Object> isSellable(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		GenericDelegator delegator = dctx.getDelegator();
		String productId = (String) context.get("productId");
		Timestamp atTime = (Timestamp) context.get("atTime");
		boolean value = false;
		try {
			value = org.ofbiz.product.product.ProductWorker.isSellable(delegator, productId, atTime);
		} catch (GenericEntityException e) {
			Debug.logWarning(e.getMessage(), module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, Boolean.valueOf(value));
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProdCatalogCategories(DispatchContext, Map)
	 */
	public Map<String, Object> getProdCatalogCategories(DispatchContext ctx,
			Map<String, ?> context) {
		GenericDelegator delegator = ctx.getDelegator();
		String prodCatalogId = (String) context.get("prodCatalogId");
		String prodCatalogCategoryTypeId = (String) context.get("prodCatalogCategoryTypeId");
		List<GenericValue> values = org.ofbiz.product.catalog.CatalogWorker.getProdCatalogCategories(delegator, prodCatalogId, prodCatalogCategoryTypeId);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, values);
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProductCategoryMembers(DispatchContext, Map)
	 */
	public Map<String, Object> getProductCategoryMembers(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.product.category.CategoryServices.getCategoryMembers(ctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getPreviousNextProducts(DispatchContext, Map)
	 */
	public Map<String, Object> getPreviousNextProducts(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.product.category.CategoryServices.getPreviousNextProducts(ctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getProductCategoryAndLimitedMembers(DispatchContext, Map)
	 */
	public Map<String, Object> getProductCategoryAndLimitedMembers(
			DispatchContext ctx, Map<String, ?> context) {
		return org.ofbiz.product.category.CategoryServices.getProductCategoryAndLimitedMembers(ctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getCategoryVariantProducts(DispatchContext, Map)
	 */
	public Map<String, Object> getCategoryVariantProducts(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.feature.ProductFeatureServices.getCategoryVariantProducts(dctx, context);
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getParentProduct(DispatchContext, Map)
	 */
	public Map<String, Object> getParentProduct(DispatchContext ctx,
			Map<String, ?> context) {
		GenericDelegator delegator = ctx.getDelegator();
		String productId = (String) context.get("productId");
		GenericValue value = org.ofbiz.product.product.ProductWorker.getParentProduct(productId, delegator);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, value);
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#getVariantVirtualAssocs(DispatchContext, Map)
	 */
	public Map<String, Object> getVariantVirtualAssocs(DispatchContext ctx,
			Map<String, ?> context) {
		GenericValue variantProduct = (GenericValue) context.get("variantProduct");
		GenericDelegator delegator = ctx.getDelegator();
		variantProduct.setDelegator(delegator);
		List<GenericValue> values;
		try {
			values = org.ofbiz.product.product.ProductWorker.getVariantVirtualAssocs(variantProduct);
		} catch (GenericEntityException e) {
            Debug.logError(e, module);
			return ServiceUtil.returnError(e.getMessage());
		}

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, values);
		return result;
	}

	/**
     * @see org.langhua.ofbiz.rmi.service.I_ProductServices#calculateProductPrice(DispatchContext, Map)
	 */
	public Map<String, Object> calculateProductPrice(DispatchContext dctx,
			Map<String, ? extends Object> context) {
		return org.ofbiz.product.price.PriceServices.calculateProductPrice(dctx, context);
	}
}
