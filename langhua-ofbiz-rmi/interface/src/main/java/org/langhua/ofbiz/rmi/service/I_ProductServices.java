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
 * Interface of RMI Product Services which will be an counterpart of
 * org.ofbiz.product.*.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_ProductServices {

	/************************************************************
	 *  ProductStore services begin
	 ************************************************************/
	
	/**
	 * Get a GenericValue of ProductStore. This method calls
	 * {@link org.ofbiz.product.store.ProductStoreWorker#getProductStore(String, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productStoreId String
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue if success
	 */
	public Map<String, Object> getProductStore(DispatchContext dctx,
			Map<String, ?> context);
	
	/**
     * Get payment method type ids available for a specific store. 
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productStoreId String
     * @param context.useCache Boolean
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Set of String if success
	 */
	public Map<String, Object> getStorePaymentMethodTypeIds(DispatchContext ctx,
			Map<String, ?> context);

	/**
     * Check inventory availability for the given catalog, product, quantity, etc. 
     * This method simply calls isStoreInventoryAvailable service
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String
     * @param context.productStoreId String
     * @param context.productStore GenericValue
     * @param context.product GenericValue
     * @param context.quantity BigDecimal
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Boolean
	 */
	public Map<String, Object> isStoreInventoryAvailable(DispatchContext ctx,
			Map<String, ?> context);

	
	/************************************************************
	 *  ProductStore services end
	 ************************************************************/

	/************************************************************
	 *  Catalog services begin
	 ************************************************************/

	/**
     * Get product catalogs of a store. This method calls
	 * {@link org.ofbiz.product.catalog.CatalogWorker#getStoreCatalogs(org.ofbiz.entity.GenericDelegator, String)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productStoreId String
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue
	 */
	public Map<String, Object> getStoreCatalogs(DispatchContext ctx,
			Map<String, ?> context);
	
	/**
     * Get product catalog categories. This method calls
	 * {@link org.ofbiz.product.catalog.CatalogWorker#getProdCatalogCategories(org.ofbiz.entity.GenericDelegator, String, String)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.prodCatalogId String
     * @param context.prodCatalogCategoryTypeId String
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue if success
	 */
	public Map<String, Object> getProdCatalogCategories(DispatchContext ctx,
			Map<String, ?> context);

	/************************************************************
	 *  Catalog services end
	 ************************************************************/
	
	/************************************************************
	 *  Category services begin
	 ************************************************************/

	/**
     * Get members of a product catalog category. This method simply calls
	 * {@link org.ofbiz.product.category.CategoryServices#getCategoryMembers(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.categoryId String
     * 
	 * @return the followings in return Map:
	 *  category: GenericValue if success
	 *  categoryMembers: List of GenericValue if success
	 *  ModelService.RESPOND_ERROR: String of error message if error
	 */
	public Map<String, Object> getProductCategoryMembers(DispatchContext ctx,
			Map<String, ?> context);

	/**
     * Get members of a product catalog category. This method simply calls
	 * {@link org.ofbiz.product.category.CategoryServices#getPreviousNextProducts(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.categoryId String
     * @param context.productId String
     * @param context.activeOnly String
     * @param context.index String
     * @param context.orderByFields List of String
     * 
	 * @return the followings in return Map:
	 *  category: GenericValue if success
	 *  previousProductId: String of previous productId if success
	 *  nextProductId: String of next productId if success
	 *  ModelService.RESPOND_SUCCESS: String of message if success but no index
	 *  ModelService.RESPOND_ERROR: String of error message if error
	 */
	public Map<String, Object> getPreviousNextProducts(DispatchContext ctx,
			Map<String, ?> context);

	/**
     * Get limited members of a product catalog category. This method simply calls
	 * {@link org.ofbiz.product.category.CategoryServices#getProductCategoryAndLimitedMembers(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productCategoryId String
     * @param context.limitView Boolean
     * @param context.defaultViewSize Integer
     * @param context.orderByFields List of String
     * @param context.prodCatalogId String
     * @param context.useCacheForMembers Boolean
     * @param context.activeOnly Boolean
     * @param context.checkViewAllow Boolean
     * @param context.viewIndexString String
     * @param context.viewSizeString String
     * 
	 * @return the followings in return Map:
	 *  viewIndex: Integer
	 *  viewSize: Integer
	 *  lowIndex: Integer
	 *  highIndex: Integer
	 *  listSize: Integer
	 *  productCategory: GenericValue
	 *  productCategoryMembers: List of GenericValue
	 */
	public Map<String, Object> getProductCategoryAndLimitedMembers(DispatchContext ctx,
			Map<String, ?> context);

    /************************************************************
	 *  Category services end
	 ************************************************************/
	
    /************************************************************
	 *  Product services begin
	 ************************************************************/
	
    /**
     * Finds a product by product ID.
     * This method simply calls
	 * {@link org.ofbiz.product.product.ProductServices#prodFindProduct(DispatchContext, Map)}
     * 
	 * @return the followings in return Map:
	 *  product: GenericValue if success
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  ModelService.ERROR_MESSAGE: String, if exception occured
     */
    public Map<String, Object> getProduct(DispatchContext dctx, Map<String, ? extends Object> context);

    /**
     * Creates a Collection of product entities which are variant products from the specified product ID.
     * This method simply calls
	 * {@link org.ofbiz.product.product.ProductServices#prodFindAllVariants(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String of productId
     * @param context.productIdTo String
     * @param context.locale Locale
     * @param context.checkViewAllow Boolean
     * @param context.prodCatalogId String
     *  
	 * @return the followings in return Map:
	 *  assocProducts: List of GenericValue if success
	 *  ModelService.RESPONSE_MESSAGE: String
	 *  ModelService.ERROR_MESSAGE: String, if exception occured 
     */
    public Map<String, Object> getAllProductVariants(DispatchContext dctx, Map<String, ? extends Object> context);

    /**
     * Finds a specific product or products which contain the selected features.
     * This method simply calls
	 * {@link org.ofbiz.product.product.ProductServices#prodFindSelectedVariant(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String of productId
     * @param context.selectedFeatures Map
     *  
	 * @return the followings in return Map:
	 *  products: List of GenericValue if success
	 *  ModelService.RESPOND_SUCCESS: String
	 *  ModelService.RESPOND_ERROR: String, if exception occured
     */
    public Map<String, Object> getProductVariant(DispatchContext dctx, Map<String, ? extends Object> context);

    /**
     * Finds productId(s) corresponding to a product reference, productId or a GoodIdentification idValue.
     * This method simply calls
	 * {@link org.ofbiz.product.product.ProductServices#findProductById(DispatchContext, Map)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.idToFind String of productId
     * @param context.goodIdentificationTypeId String of goodIdentification.idValue
     * @param context.searchProductFirst String of Y|N
     * @param context.searchAllId String of Y|N
     *  
	 * @return the followings in return Map:
	 *  product: GenericValue if success
	 *  productsList: List of GenericValue if success
     */
    public Map<String, Object> findProductById(DispatchContext ctx, Map<String, Object> context);

    /**
     * Gets the product features of a product.
     * This method simply calls
	 * {@link org.ofbiz.product.product.ProductServices#prodGetFeatures(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String
     * @param context.distinct String, Distinct feature (SIZE, COLOR)
     * @param context.type String, Type of feature (STANDARD_FEATURE, SELECTABLE_FEATURE)
     * @param context.locale Locale
     *  
	 * @return the followings in return Map:
	 *  productFeatures: GenericValue if success
	 *  ModelService.RESPONSE_MESSAGE: ModelService.RESPOND_SUCCESS if success, else error
	 *  ModelService.ERROR_MESSAGE: String of error message
     */
    public Map<String, Object> getProductFeatures(DispatchContext dctx, Map<String, ? extends Object> context);

    /**
     * Check whether the product is sellable at the specific time.
     * This method covers
	 * {@link org.ofbiz.product.product.ProductWorker#isSellable(org.ofbiz.entity.GenericDelegator, String)}
	 * {@link org.ofbiz.product.product.ProductWorker#isSellable(org.ofbiz.entity.GenericDelegator, String, java.sql.Timestamp)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String
     * @param context.atTime Timestamp
     *  
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: Boolean
     */
    public Map<String, Object> isSellable(DispatchContext dctx, Map<String, ? extends Object> context);

	/**
     * Get parent product. This method calls
	 * {@link org.ofbiz.product.product.ProductWorker#getParentProduct(String, org.ofbiz.entity.GenericDelegator)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productId String
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: GenericValue
	 */
	public Map<String, Object> getParentProduct(DispatchContext ctx, Map<String, ?> context);

	/**
     * Get a List of virtual associate products of the specific variant product. This method calls
	 * {@link org.ofbiz.product.product.ProductWorker#getVariantVirtualAssocs(org.ofbiz.entity.GenericValue)
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.variantProduct GenericValue
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of GenericValue if success
	 *  ModelService.RESPOND_ERROR: String if error
	 */
	public Map<String, Object> getVariantVirtualAssocs(DispatchContext ctx, Map<String, ?> context);

    /************************************************************
	 *  Product services end
	 ************************************************************/

    /************************************************************
	 *  Product Feature services begin
	 ************************************************************/

    /**
     * Get variant products in a category. This method simply calls
	 * {@link org.ofbiz.product.feature.ProductFeatureServices#getCategoryVariantProducts(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.productCategoryId String
     * @param context.productFeatures List
     *  
	 * @return the followings in return Map:
	 *  products: List of GenericValue if success
	 *  ModelService.RESPOND_SUCCESS: String if success
	 *  ModelService.RESPOND_ERROR: String if error
     */
    public Map<String, Object> getCategoryVariantProducts(DispatchContext dctx, Map<String, ? extends Object> context);
    
    /************************************************************
	 *  Product Feature services end
	 ************************************************************/

    /************************************************************
	 *  Price services begin
	 ************************************************************/

    /**
     * Calculates the price of a product from pricing rules given the following input, and of course access to the database.
     * This method simply calls
	 * {@link org.ofbiz.product.price.PriceServices#calculateProductPrice(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.product GenericValue
     * @param context.prodCatalogId String
     * @param context.webSiteId String
     * @param context.checkIncludeVat String
     * @param context.surveyResponseId String
     * @param context.customAttributes Map
     * @param context.findAllQuantityPrices String
     * @param context.agreementId String
     * @param context.productStoreId String
     * @param context.productStoreGroupId String
     * @param context.currencyUomId String
     * @param context.productPricePurposeId String
     * @param context.termUomId String
     * @param context.partyId String
     * @param context.userLogin GenericValue
     * @param context.autoUserLogin GenericValue
     * @param context.quantity BigDecimal
     * @param context.amount BigDecimal
     * 
	 * @return the followings in return Map:
	 *  if list price is null, the followings may return:
	 *    basePrice: BigDecimal
	 *    price: BigDecimal
	 *    defaultPrice: BigDecimal
	 *    competitivePrice: BigDecimal
	 *    averageCost: BigDecimal
	 *    promoPrice: BigDecimal
	 *    specialPromoPrice: BigDecimal
	 *    validPriceFound: Boolean
	 *    isSale: Boolean
	 *    orderItemPriceInfos: List of GenericValue
	 *    currencyUsed: String
	 *    ModelService.RESPOND_ERROR: String if error
	 *  
	 *  if list price is not null, the followings will be returned:
	 *    allQuantityPrices: List of Map
	 *    basePrice: BigDecimal
	 *    price: BigDecimal
	 *    listPrice: BigDecimal
	 *    defaultPrice: BigDecimal
	 *    averageCost: BigDecimal
	 *    orderItemPriceInfos: List of GenericValue
	 *    isSale: Boolean
	 *    validPriceFound: Boolean
	 *    
	 *  if error:
	 *    ModelService.RESPOND_ERROR: String
     */
    public Map<String, Object> calculateProductPrice(DispatchContext dctx, Map<String, ? extends Object> context);

    /************************************************************
	 *  Price services end
	 ************************************************************/

}
