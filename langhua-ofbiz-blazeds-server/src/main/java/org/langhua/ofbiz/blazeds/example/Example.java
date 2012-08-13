/*
 * This library is part of OFBiz-BlazeDS Component of Langhua
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
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-blazeds/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on BlazeDS, please see the
 * project website: http://opensource.adobe.com/wiki/display/blazeds/BlazeDS/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.blazeds.example;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.parsers.ParserConfigurationException;

import javolution.util.FastList;
import javolution.util.FastMap;
import javolution.util.FastSet;

import org.ofbiz.base.component.ComponentConfig;
import org.ofbiz.base.component.ComponentException;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilDateTime;
import org.ofbiz.base.util.UtilGenerics;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilNumber;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilURL;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.base.util.UtilXml;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntity;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.condition.EntityCondition;
import org.ofbiz.entity.condition.EntityExpr;
import org.ofbiz.entity.condition.EntityOperator;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.product.product.ProductWorker;
import org.ofbiz.service.GenericDispatcher;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public class Example {

    public static final String module = Example.class.getName();
    public static final BigDecimal ONE_BASE = new BigDecimal("1.000"); 
    public static final BigDecimal PERCENT_SCALE = new BigDecimal("100.000"); 

    public static final int taxCalcScale = UtilNumber.getBigDecimalScale("salestax.calc.decimals");
    public static final int taxFinalScale = UtilNumber.getBigDecimalScale("salestax.final.decimals");
    public static final int taxRounding = UtilNumber.getBigDecimalRoundingMode("salestax.rounding");
    
    
    public static final BigDecimal ZERO_BASE = new BigDecimal("0.000");
    public static int salestaxFinalDecimals = UtilNumber.getBigDecimalScale("salestax.final.decimals");
    public static int salestaxCalcDecimals = UtilNumber.getBigDecimalScale("salestax.calc.decimals");
    public static int salestaxRounding = UtilNumber.getBigDecimalRoundingMode("salestax.rounding");
    
    
    private String ofbizUrl = "http://localhost:8080";
    private String delegatorName = "default";
    
	public Example() {
		
		initurlconfig();
	}

	public List getProductLists(String productId) {
		
		GenericDelegator delegator = GenericDelegator.getGenericDelegator(delegatorName);
		List result = new ArrayList(1);
		Map productResult = prodFindProduct(delegator, UtilMisc.toMap(
				"productId", productId));
		GenericEntity productValue = null;
		if (productResult != null) {
			productValue = (GenericEntity) productResult.get("product");
			if (productValue != null) {
				Map fields = productValue.getAllFields();
				Map priceResult = calculateProductPrice(delegator, UtilMisc
						.toMap("product", productValue));
				BigDecimal listPrice = (BigDecimal) priceResult
						.get("listPrice");
				BigDecimal basePrice = (BigDecimal) priceResult
						.get("basePrice");
				BigDecimal promoPrice = (BigDecimal) priceResult
						.get("promoPrice");
				BigDecimal specialPromoPrice = (BigDecimal) priceResult
						.get("specialPromoPrice");
				String url = (String) fields.get("smallImageUrl");
				if (url != null)
					url = ofbizUrl + url;
				HashMap array = new HashMap(6);
				String internalName = (String) fields.get("internalName") + (String) fields.get("description");
				array.put("Name", internalName);
				array.put("Image", url);
				if (listPrice != null) {
					array.put("ListPrice", String.valueOf(listPrice
							.doubleValue()));
				} else {
					array.put("ListPrice", "");
				}
				if (basePrice != null) {
					array.put("BasePrice", String.valueOf(basePrice
							.doubleValue()));
				} else {
					array.put("BasePrice", "");
				}
				if (promoPrice != null) {
					array.put("PromotionPrice", String.valueOf(promoPrice
							.doubleValue()));
				} else {
					array.put("PromotionPrice", "");
				}
				if (specialPromoPrice != null) {
					array.put("SpecialPrice", String.valueOf(specialPromoPrice
							.doubleValue()));
				} else {
					array.put("SpecialPrice", "");
				}
				result.add(array);
			}
		}else{
			HashMap array = new HashMap(6);
			array.put("Name","");
			array.put("Image", "");
			array.put("ListPrice", "");
			array.put("BasePrice", "");
			array.put("PromotionPrice", "");
			array.put("SpecialPrice", "");
		}
		return result;
	}
	
	public List getProductList() {
		
		GenericDelegator delegator = GenericDelegator.getGenericDelegator(delegatorName);
		List result = new ArrayList(10);
		Map context = new HashMap();
		context.put("categoryId", "PROMOTIONS");
		Map categorys = getCategoryMembers(delegator, context);
		List products = (List) categorys.get("categoryMembers");

		if(products==null||products.size() == 0){
			HashMap array = new HashMap(1);
			array.put("Id", null);
			array.put("Name", "");
			result.add(array);
			return result;
		}
		Iterator i = products.iterator();
		while (i.hasNext()) {
			GenericEntity product = (GenericEntity) i.next();
			Map fields = product.getAllFields();
			String productId = (String) fields.get("productId");

			Map productResult = prodFindProduct(delegator,UtilMisc.toMap("productId", productId));
			GenericEntity productValue = null;
			if (productResult != null) {
				productValue = (GenericEntity) productResult.get("product");
				if (productValue != null) {
					fields = productValue.getAllFields();
					String internalName = (String) fields.get("internalName")
							+ (String) fields.get("description") + productId;
					HashMap array = new HashMap(1);
					array.put("Id", productId);
					array.put("Name", internalName);
					result.add(array);
				}
			}
		}
		return result;
	}
	
	public static Map getCategoryMembers(GenericDelegator delegator, Map context) {
        String categoryId = (String) context.get("categoryId");
        GenericValue productCategory = null;
        List members = null;

        try {
            productCategory = delegator.findByPrimaryKeyCache("ProductCategory", UtilMisc.toMap("productCategoryId", categoryId));
            members = EntityUtil.filterByDate(productCategory.getRelatedCache("ProductCategoryMember", null, UtilMisc.toList("sequenceNum")), true);
        } catch (GenericEntityException e) {
            String errMsg = "Problem reading product categories: " + e.getMessage();
            return ServiceUtil.returnError(errMsg);
        }
        Map result = ServiceUtil.returnSuccess();
        result.put("category", productCategory);
        result.put("categoryMembers", members);
        return result;
    }
	
	public static Map prodFindProduct(GenericDelegator delegator, Map context) {
		
        // * String productId      -- Product ID to find
        Map result = new HashMap();
        String productId = (String) context.get("productId");
        String errMsg = null;

        if (productId == null || productId.length() == 0) {
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
            return result;
        }

        try {
            GenericValue product = delegator.findByPrimaryKeyCache("Product", UtilMisc.toMap("productId", productId));
            GenericValue mainProduct = product;

            if (product.get("isVariant") != null && product.getString("isVariant").equalsIgnoreCase("Y")) {
                List c = product.getRelatedByAndCache("AssocProductAssoc",
                        UtilMisc.toMap("productAssocTypeId", "PRODUCT_VARIANT"));

                if (c != null) {
                    c = EntityUtil.filterByDate(c, true);
                    if (c.size() > 0) {
                        GenericValue asV = (GenericValue) c.iterator().next();

                        mainProduct = asV.getRelatedOneCache("MainProduct");
                    }
                }
            }
            result.put("product", mainProduct);
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_SUCCESS);
        } catch (GenericEntityException e) {
            e.printStackTrace();
            Map messageMap = UtilMisc.toMap("errMessage", e.getMessage());
            result.put(ModelService.RESPONSE_MESSAGE, ModelService.RESPOND_ERROR);
        }

        return result;
    }
	public static Map<String, Object> calculateProductPrice(GenericDelegator delegator, Map context) {
        boolean optimizeForLargeRuleSet = false;

        Map<String, Object> result = FastMap.newInstance();
        Timestamp nowTimestamp = UtilDateTime.nowTimestamp();

        GenericValue product = (GenericValue) context.get("product");
        String productId = product.getString("productId");
        String prodCatalogId = (String) context.get("prodCatalogId");
        String webSiteId = (String) context.get("webSiteId");
        String checkIncludeVat = (String) context.get("checkIncludeVat");
        String surveyResponseId = (String) context.get("surveyResponseId");
        Map<String, Object> customAttributes = UtilGenerics.checkMap(context.get("customAttributes"));

        String findAllQuantityPricesStr = (String) context.get("findAllQuantityPrices");
        boolean findAllQuantityPrices = "Y".equals(findAllQuantityPricesStr);

        String agreementId = (String) context.get("agreementId");

        String productStoreId = (String) context.get("productStoreId");
        String productStoreGroupId = (String) context.get("productStoreGroupId");
        GenericValue productStore = null;
        try {
            // we have a productStoreId, if the corresponding ProductStore.primaryStoreGroupId is not empty, use that
            productStore = delegator.findByPrimaryKeyCache("ProductStore", UtilMisc.toMap("productStoreId", productStoreId));
        } catch (GenericEntityException e) {
            String errMsg = "Error getting product store info from the database while calculating price" + e.toString();
            Debug.logError(e, errMsg, module);
            return ServiceUtil.returnError(errMsg);
        }
        if (UtilValidate.isEmpty(productStoreGroupId)) {
            if (productStore != null) {
                try {
                    if (UtilValidate.isNotEmpty(productStore.getString("primaryStoreGroupId"))) {
                        productStoreGroupId = productStore.getString("primaryStoreGroupId");
                    } else {
                        // no ProductStore.primaryStoreGroupId, try ProductStoreGroupMember
                        List<GenericValue> productStoreGroupMemberList = delegator.findByAndCache("ProductStoreGroupMember", UtilMisc.toMap("productStoreId", productStoreId), UtilMisc.toList("sequenceNum", "-fromDate"));
                        productStoreGroupMemberList = EntityUtil.filterByDate(productStoreGroupMemberList, true);
                        if (productStoreGroupMemberList.size() > 0) {
                            GenericValue productStoreGroupMember = EntityUtil.getFirst(productStoreGroupMemberList);
                            productStoreGroupId = productStoreGroupMember.getString("productStoreGroupId");
                        }
                    }
                } catch (GenericEntityException e) {
                    String errMsg = "Error getting product store info from the database while calculating price" + e.toString();
                    Debug.logError(e, errMsg, module);
                    return ServiceUtil.returnError(errMsg);
                }
            }

            // still empty, default to _NA_
            if (UtilValidate.isEmpty(productStoreGroupId)) {
                productStoreGroupId = "_NA_";
            }
        }

        // if currencyUomId is null get from properties file, if nothing there assume USD (USD: American Dollar) for now
        String currencyUomId = (String) context.get("currencyUomId");
        if (UtilValidate.isEmpty(currencyUomId)) {
            currencyUomId = UtilProperties.getPropertyValue("general", "currency.uom.id.default", "USD");
        }

        // productPricePurposeId is null assume "PURCHASE", which is equivalent to what prices were before the purpose concept
        String productPricePurposeId = (String) context.get("productPricePurposeId");
        if (UtilValidate.isEmpty(productPricePurposeId)) {
            productPricePurposeId = "PURCHASE";
        }

        // termUomId, for things like recurring prices specifies the term (time/frequency measure for example) of the recurrence
        // if this is empty it will simply not be used to constrain the selection
        String termUomId = (String) context.get("termUomId");

        // if this product is variant, find the virtual product and apply checks to it as well
        String virtualProductId = null;
        if ("Y".equals(product.getString("isVariant"))) {
            try {
                virtualProductId = ProductWorker.getVariantVirtualId(product);
            } catch (GenericEntityException e) {
                String errMsg = "Error getting virtual product id from the database while calculating price" + e.toString();
                Debug.logError(e, errMsg, module);
                return ServiceUtil.returnError(errMsg);
            }
        }

        // get prices for virtual product if one is found; get all ProductPrice entities for this productId and currencyUomId
        List<GenericValue> virtualProductPrices = null;
        if (virtualProductId != null) {
            try {
                virtualProductPrices = delegator.findByAndCache("ProductPrice", UtilMisc.toMap("productId", virtualProductId, "currencyUomId", currencyUomId, "productStoreGroupId", productStoreGroupId), UtilMisc.toList("-fromDate"));
            } catch (GenericEntityException e) {
                Debug.logError(e, "An error occurred while getting the product prices", module);
            }
            virtualProductPrices = EntityUtil.filterByDate(virtualProductPrices, true);
        }

        // NOTE: partyId CAN be null
        String partyId = (String) context.get("partyId");
        if (UtilValidate.isEmpty(partyId) && context.get("userLogin") != null) {
            GenericValue userLogin = (GenericValue) context.get("userLogin");
            partyId = userLogin.getString("partyId");
        }

        // check for auto-userlogin for price rules
        if (UtilValidate.isEmpty(partyId) && context.get("autoUserLogin") != null) {
            GenericValue userLogin = (GenericValue) context.get("autoUserLogin");
            partyId = userLogin.getString("partyId");
        }

        BigDecimal quantity = (BigDecimal) context.get("quantity");
        if (quantity == null) quantity = BigDecimal.ONE;

        BigDecimal amount = (BigDecimal) context.get("amount");

        List<EntityCondition> productPriceEcList = FastList.newInstance();
        productPriceEcList.add(EntityCondition.makeCondition("productId", EntityOperator.EQUALS, productId));
        // this funny statement is for backward compatibility purposes; the productPricePurposeId is a new pk field on the ProductPrice entity and in order databases may not be populated, until the pk is updated and such; this will ease the transition somewhat
        if ("PURCHASE".equals(productPricePurposeId)) {
            productPriceEcList.add(EntityCondition.makeCondition(
                    EntityCondition.makeCondition("productPricePurposeId", EntityOperator.EQUALS, productPricePurposeId),
                    EntityOperator.OR,
                    EntityCondition.makeCondition("productPricePurposeId", EntityOperator.EQUALS, null)));
        } else {
            productPriceEcList.add(EntityCondition.makeCondition("productPricePurposeId", EntityOperator.EQUALS, productPricePurposeId));
        }
        productPriceEcList.add(EntityCondition.makeCondition("currencyUomId", EntityOperator.EQUALS, currencyUomId));
        productPriceEcList.add(EntityCondition.makeCondition("productStoreGroupId", EntityOperator.EQUALS, productStoreGroupId));
        if (UtilValidate.isNotEmpty(termUomId)) {
            productPriceEcList.add(EntityCondition.makeCondition("termUomId", EntityOperator.EQUALS, termUomId));
        }
        EntityCondition productPriceEc = EntityCondition.makeCondition(productPriceEcList, EntityOperator.AND);

        // for prices, get all ProductPrice entities for this productId and currencyUomId
        List<GenericValue> productPrices = null;
        try {
            productPrices = delegator.findList("ProductPrice", productPriceEc, null, UtilMisc.toList("-fromDate"), null, true);
        } catch (GenericEntityException e) {
            Debug.logError(e, "An error occurred while getting the product prices", module);
        }
        productPrices = EntityUtil.filterByDate(productPrices, true);

        // ===== get the prices we need: list, default, average cost, promo, min, max =====
        List<GenericValue> listPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "LIST_PRICE"));
        GenericValue listPriceValue = EntityUtil.getFirst(listPrices);
        if (listPrices != null && listPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one LIST_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + listPriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> defaultPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "DEFAULT_PRICE"));
        GenericValue defaultPriceValue = EntityUtil.getFirst(defaultPrices);
        if (defaultPrices != null && defaultPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one DEFAULT_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + defaultPriceValue.getBigDecimal("price"), module);
        }

        // If there is an agreement between the company and the client, and there is
        // a price for the product in it, it will override the default price of the
        // ProductPrice entity.
        if (UtilValidate.isNotEmpty(agreementId)) {
            try {
                List<GenericValue> agreementPrices = delegator.findByAnd("AgreementItemAndProductAppl", UtilMisc.toMap("agreementId", agreementId, "productId", productId, "currencyUomId", currencyUomId));
                GenericValue agreementPriceValue = EntityUtil.getFirst(agreementPrices);
                if (agreementPriceValue != null && agreementPriceValue.get("price") != null) {
                    defaultPriceValue = agreementPriceValue;
                }
            } catch (GenericEntityException e) {
                String errMsg = "Error getting agreement info from the database while calculating price" + e.toString();
                Debug.logError(e, errMsg, module);
                return ServiceUtil.returnError(errMsg);
            }
        }

        List<GenericValue> competitivePrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "COMPETITIVE_PRICE"));
        GenericValue competitivePriceValue = EntityUtil.getFirst(competitivePrices);
        if (competitivePrices != null && competitivePrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one COMPETITIVE_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + competitivePriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> averageCosts = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "AVERAGE_COST"));
        GenericValue averageCostValue = EntityUtil.getFirst(averageCosts);
        if (averageCosts != null && averageCosts.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one AVERAGE_COST with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + averageCostValue.getBigDecimal("price"), module);
        }

        List<GenericValue> promoPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "PROMO_PRICE"));
        GenericValue promoPriceValue = EntityUtil.getFirst(promoPrices);
        if (promoPrices != null && promoPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + promoPriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> minimumPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "MINIMUM_PRICE"));
        GenericValue minimumPriceValue = EntityUtil.getFirst(minimumPrices);
        if (minimumPrices != null && minimumPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one MINIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + minimumPriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> maximumPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "MAXIMUM_PRICE"));
        GenericValue maximumPriceValue = EntityUtil.getFirst(maximumPrices);
        if (maximumPrices != null && maximumPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one MAXIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + maximumPriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> wholesalePrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "WHOLESALE_PRICE"));
        GenericValue wholesalePriceValue = EntityUtil.getFirst(wholesalePrices);
        if (wholesalePrices != null && wholesalePrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one WHOLESALE_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + wholesalePriceValue.getBigDecimal("price"), module);
        }

        List<GenericValue> specialPromoPrices = EntityUtil.filterByAnd(productPrices, UtilMisc.toMap("productPriceTypeId", "SPECIAL_PROMO_PRICE"));
        GenericValue specialPromoPriceValue = EntityUtil.getFirst(specialPromoPrices);
        if (specialPromoPrices != null && specialPromoPrices.size() > 1) {
            if (Debug.infoOn()) Debug.logInfo("There is more than one SPECIAL_PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + productId + ", using the latest found with price: " + specialPromoPriceValue.getBigDecimal("price"), module);
        }

        // if any of these prices is missing and this product is a variant, default to the corresponding price on the virtual product
        if (UtilValidate.isNotEmpty(virtualProductPrices)) {
            if (listPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "LIST_PRICE"));
                listPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one LIST_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + listPriceValue.getBigDecimal("price"), module);
                }
            }
            if (defaultPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "DEFAULT_PRICE"));
                defaultPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one DEFAULT_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + defaultPriceValue.getBigDecimal("price"), module);
                }
            }
            if (averageCostValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "AVERAGE_COST"));
                averageCostValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one AVERAGE_COST with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + averageCostValue.getBigDecimal("price"), module);
                }
            }
            if (promoPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "PROMO_PRICE"));
                promoPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + promoPriceValue.getBigDecimal("price"), module);
                }
            }
            if (minimumPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "MINIMUM_PRICE"));
                minimumPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one MINIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + minimumPriceValue.getBigDecimal("price"), module);
                }
            }
            if (maximumPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "MAXIMUM_PRICE"));
                maximumPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one MAXIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + maximumPriceValue.getBigDecimal("price"), module);
                }
            }
            if (wholesalePriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "WHOLESALE_PRICE"));
                wholesalePriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one WHOLESALE_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + wholesalePriceValue.getBigDecimal("price"), module);
                }
            }
            if (specialPromoPriceValue == null) {
                List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(virtualProductPrices, UtilMisc.toMap("productPriceTypeId", "SPECIAL_PROMO_PRICE"));
                specialPromoPriceValue = EntityUtil.getFirst(virtualTempPrices);
                if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                    if (Debug.infoOn()) Debug.logInfo("There is more than one SPECIAL_PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + virtualProductId + ", using the latest found with price: " + specialPromoPriceValue.getBigDecimal("price"), module);
                }
            }
        }

        // now if this is a virtual product check each price type, if doesn't exist get from variant with lowest DEFAULT_PRICE
        if ("Y".equals(product.getString("isVirtual"))) {
            // only do this if there is no default price, consider the others optional for performance reasons
            if (defaultPriceValue == null) {
                // Debug.logInfo("Product isVirtual and there is no default price for ID " + productId + ", trying variant prices", module);

                //use the cache to find the variant with the lowest default price
                try {
                    List<GenericValue> variantAssocList = EntityUtil.filterByDate(delegator.findByAndCache("ProductAssoc", UtilMisc.toMap("productId", product.get("productId"), "productAssocTypeId", "PRODUCT_VARIANT"), UtilMisc.toList("-fromDate")));
                    BigDecimal minDefaultPrice = null;
                    List<GenericValue> variantProductPrices = null;
                    String variantProductId = null;
                    for (GenericValue variantAssoc: variantAssocList) {
                        String curVariantProductId = variantAssoc.getString("productIdTo");
                        List<GenericValue> curVariantPriceList = EntityUtil.filterByDate(delegator.findByAndCache("ProductPrice", UtilMisc.toMap("productId", curVariantProductId), UtilMisc.toList("-fromDate")), nowTimestamp);
                        List<GenericValue> tempDefaultPriceList = EntityUtil.filterByAnd(curVariantPriceList, UtilMisc.toMap("productPriceTypeId", "DEFAULT_PRICE"));
                        GenericValue curDefaultPriceValue = EntityUtil.getFirst(tempDefaultPriceList);
                        if (curDefaultPriceValue != null) {
                            BigDecimal curDefaultPrice = curDefaultPriceValue.getBigDecimal("price");
                            if (minDefaultPrice == null || curDefaultPrice.compareTo(minDefaultPrice) < 0) {
                                // check to see if the product is discontinued for sale before considering it the lowest price
                                GenericValue curVariantProduct = delegator.findByPrimaryKeyCache("Product", UtilMisc.toMap("productId", curVariantProductId));
                                if (curVariantProduct != null) {
                                    Timestamp salesDiscontinuationDate = curVariantProduct.getTimestamp("salesDiscontinuationDate");
                                    if (salesDiscontinuationDate == null || salesDiscontinuationDate.after(nowTimestamp)) {
                                        minDefaultPrice = curDefaultPrice;
                                        variantProductPrices = curVariantPriceList;
                                        variantProductId = curVariantProductId;
                                        // Debug.logInfo("Found new lowest price " + minDefaultPrice + " for variant with ID " + variantProductId, module);
                                    }
                                }
                            }
                        }
                    }

                    if (variantProductPrices != null) {
                        // we have some other options, give 'em a go...
                        if (listPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "LIST_PRICE"));
                            listPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one LIST_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + listPriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (defaultPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "DEFAULT_PRICE"));
                            defaultPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one DEFAULT_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + defaultPriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (competitivePriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "COMPETITIVE_PRICE"));
                            competitivePriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one COMPETITIVE_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + competitivePriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (averageCostValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "AVERAGE_COST"));
                            averageCostValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one AVERAGE_COST with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + averageCostValue.getBigDecimal("price"), module);
                            }
                        }
                        if (promoPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "PROMO_PRICE"));
                            promoPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + promoPriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (minimumPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "MINIMUM_PRICE"));
                            minimumPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one MINIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + minimumPriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (maximumPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "MAXIMUM_PRICE"));
                            maximumPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one MAXIMUM_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + maximumPriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (wholesalePriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "WHOLESALE_PRICE"));
                            wholesalePriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one WHOLESALE_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + wholesalePriceValue.getBigDecimal("price"), module);
                            }
                        }
                        if (specialPromoPriceValue == null) {
                            List<GenericValue> virtualTempPrices = EntityUtil.filterByAnd(variantProductPrices, UtilMisc.toMap("productPriceTypeId", "SPECIAL_PROMO_PRICE"));
                            specialPromoPriceValue = EntityUtil.getFirst(virtualTempPrices);
                            if (virtualTempPrices != null && virtualTempPrices.size() > 1) {
                                if (Debug.infoOn()) Debug.logInfo("There is more than one SPECIAL_PROMO_PRICE with the currencyUomId " + currencyUomId + " and productId " + variantProductId + ", using the latest found with price: " + wholesalePriceValue.getBigDecimal("price"), module);
                            }
                        }
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, "An error occurred while getting the product prices", module);
                }
            }
        }

        //boolean validPromoPriceFound = false;
        BigDecimal promoPrice = BigDecimal.ZERO;
        if (promoPriceValue != null && promoPriceValue.get("price") != null) {
            promoPrice = promoPriceValue.getBigDecimal("price");
            //validPromoPriceFound = true;
        }

        //boolean validWholesalePriceFound = false;
        BigDecimal wholesalePrice = BigDecimal.ZERO;
        if (wholesalePriceValue != null && wholesalePriceValue.get("price") != null) {
            wholesalePrice = wholesalePriceValue.getBigDecimal("price");
            //validWholesalePriceFound = true;
        }

        boolean validPriceFound = false;
        BigDecimal defaultPrice = BigDecimal.ZERO;
        List<GenericValue> orderItemPriceInfos = FastList.newInstance();
        if (defaultPriceValue != null) {
            // If a price calc formula (service) is specified, then use it to get the unit price
            if ("ProductPrice".equals(defaultPriceValue.getEntityName()) && UtilValidate.isNotEmpty(defaultPriceValue.getString("customPriceCalcService"))) {
                GenericValue customMethod = null;
                try {
                    customMethod = defaultPriceValue.getRelatedOne("CustomMethod");
                } catch (GenericEntityException gee) {
                    Debug.logError(gee, "An error occurred while getting the customPriceCalcService", module);
                }
                if (UtilValidate.isNotEmpty(customMethod) && UtilValidate.isNotEmpty(customMethod.getString("customMethodName"))) {
                    Map<String, Object> inMap = UtilMisc.toMap("userLogin", context.get("userLogin"), "product", product);
                    inMap.put("initialPrice", defaultPriceValue.getBigDecimal("price"));
                    inMap.put("currencyUomId", currencyUomId);
                    inMap.put("quantity", quantity);
                    inMap.put("amount", amount);
                    if (UtilValidate.isNotEmpty(surveyResponseId)) {
                        inMap.put("surveyResponseId", surveyResponseId);
                    }
                    if (UtilValidate.isNotEmpty(customAttributes)) {
                        inMap.put("customAttributes", customAttributes);
                    }
                    try {
                        Map<String, Object> outMap = GenericDispatcher.getLocalDispatcher("blazeds",delegator).runSync(customMethod.getString("customMethodName"), inMap);
                        if (!ServiceUtil.isError(outMap)) {
                            BigDecimal calculatedDefaultPrice = (BigDecimal)outMap.get("price");
                            orderItemPriceInfos = UtilGenerics.checkList(outMap.get("orderItemPriceInfos"));
                            if (UtilValidate.isNotEmpty(calculatedDefaultPrice)) {
                                defaultPrice = calculatedDefaultPrice;
                                validPriceFound = true;
                            }
                        }
                    } catch (GenericServiceException gse) {
                        Debug.logError(gse, "An error occurred while running the customPriceCalcService [" + customMethod.getString("customMethodName") + "]", module);
                    }
                }
            }
            if (!validPriceFound && defaultPriceValue.get("price") != null) {
                defaultPrice = defaultPriceValue.getBigDecimal("price");
                validPriceFound = true;
            }
        }

        BigDecimal listPrice = listPriceValue != null ? listPriceValue.getBigDecimal("price") : null;

        if (listPrice == null) {
            // no list price, use defaultPrice for the final price

            // ========= ensure calculated price is not below minSalePrice or above maxSalePrice =========
            BigDecimal maxSellPrice = maximumPriceValue != null ? maximumPriceValue.getBigDecimal("price") : null;
            if (maxSellPrice != null && defaultPrice.compareTo(maxSellPrice) > 0) {
                defaultPrice = maxSellPrice;
            }
            // min price second to override max price, safety net
            BigDecimal minSellPrice = minimumPriceValue != null ? minimumPriceValue.getBigDecimal("price") : null;
            if (minSellPrice != null && defaultPrice.compareTo(minSellPrice) < 0) {
                defaultPrice = minSellPrice;
                // since we have found a minimum price that has overriden a the defaultPrice, even if no valid one was found, we will consider it as if one had been...
                validPriceFound = true;
            }

            result.put("basePrice", defaultPrice);
            result.put("price", defaultPrice);
            result.put("defaultPrice", defaultPrice);
            result.put("competitivePrice", competitivePriceValue != null ? competitivePriceValue.getBigDecimal("price") : null);
            result.put("averageCost", averageCostValue != null ? averageCostValue.getBigDecimal("price") : null);
            result.put("promoPrice", promoPriceValue != null ? promoPriceValue.getBigDecimal("price") : null);
            result.put("specialPromoPrice", specialPromoPriceValue != null ? specialPromoPriceValue.getBigDecimal("price") : null);
            result.put("validPriceFound", Boolean.valueOf(validPriceFound));
            result.put("isSale", Boolean.FALSE);
            result.put("orderItemPriceInfos", orderItemPriceInfos);

            Map<String, Object> errorResult = addGeneralResults(result, competitivePriceValue, specialPromoPriceValue, productStore,
                    checkIncludeVat, currencyUomId, productId, quantity, partyId, delegator);
            if (errorResult != null) return errorResult;
        } else {
            try {
                List<GenericValue> allProductPriceRules = makeProducePriceRuleList(delegator, optimizeForLargeRuleSet, productId, virtualProductId, prodCatalogId, productStoreGroupId, webSiteId, partyId, currencyUomId);

                List<GenericValue> quantityProductPriceRules = null;
                List<GenericValue> nonQuantityProductPriceRules = null;
                if (findAllQuantityPrices) {
                    // split into list with quantity conditions and list without, then iterate through each quantity cond one
                    quantityProductPriceRules = FastList.newInstance();
                    nonQuantityProductPriceRules = FastList.newInstance();
                    for (GenericValue productPriceRule: allProductPriceRules) {
                        List<GenericValue> productPriceCondList = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("productPriceRuleId", productPriceRule.get("productPriceRuleId")));

                        boolean foundQuantityInputParam = false;
                        // only consider a rule if all conditions except the quantity condition are true
                        boolean allExceptQuantTrue = true;
                        for (GenericValue productPriceCond: productPriceCondList) {
                            if ("PRIP_QUANTITY".equals(productPriceCond.getString("inputParamEnumId"))) {
                                foundQuantityInputParam = true;
                            } else {
                                if (!checkPriceCondition(productPriceCond, productId, virtualProductId, prodCatalogId, productStoreGroupId, webSiteId, partyId, quantity, listPrice, currencyUomId, delegator, nowTimestamp)) {
                                    allExceptQuantTrue = false;
                                }
                            }
                        }

                        if (foundQuantityInputParam && allExceptQuantTrue) {
                            quantityProductPriceRules.add(productPriceRule);
                        } else {
                            nonQuantityProductPriceRules.add(productPriceRule);
                        }
                    }
                }

                if (findAllQuantityPrices) {
                    List<Map<String, Object>> allQuantityPrices = FastList.newInstance();

                    // if findAllQuantityPrices then iterate through quantityProductPriceRules
                    // foreach create an entry in the out list and eval that rule and all nonQuantityProductPriceRules rather than a single rule
                    for (GenericValue quantityProductPriceRule: quantityProductPriceRules) {
                        List<GenericValue> ruleListToUse = FastList.newInstance();
                        ruleListToUse.add(quantityProductPriceRule);
                        ruleListToUse.addAll(nonQuantityProductPriceRules);

                        Map<String, Object> quantCalcResults = calcPriceResultFromRules(ruleListToUse, listPrice, defaultPrice, promoPrice,
                            wholesalePrice, maximumPriceValue, minimumPriceValue, validPriceFound,
                            averageCostValue, productId, virtualProductId, prodCatalogId, productStoreGroupId,
                            webSiteId, partyId, null, currencyUomId, delegator, nowTimestamp);
                        Map<String, Object> quantErrorResult = addGeneralResults(quantCalcResults, competitivePriceValue, specialPromoPriceValue, productStore,
                            checkIncludeVat, currencyUomId, productId, quantity, partyId, delegator);
                        if (quantErrorResult != null) return quantErrorResult;

                        // also add the quantityProductPriceRule to the Map so it can be used for quantity break information
                        quantCalcResults.put("quantityProductPriceRule", quantityProductPriceRule);

                        allQuantityPrices.add(quantCalcResults);
                    }
                    result.put("allQuantityPrices", allQuantityPrices);

                    // use a quantity 1 to get the main price, then fill in the quantity break prices
                    Map<String, Object> calcResults = calcPriceResultFromRules(allProductPriceRules, listPrice, defaultPrice, promoPrice,
                        wholesalePrice, maximumPriceValue, minimumPriceValue, validPriceFound,
                        averageCostValue, productId, virtualProductId, prodCatalogId, productStoreGroupId,
                        webSiteId, partyId, BigDecimal.ONE, currencyUomId, delegator, nowTimestamp);
                    result.putAll(calcResults);
                    // The orderItemPriceInfos out parameter requires a special treatment:
                    // the list of OrderItemPriceInfos generated by the price rule is appended to
                    // the existing orderItemPriceInfos list and the aggregated list is returned.
                    List<GenericValue> orderItemPriceInfosFromRule = UtilGenerics.checkList(calcResults.get("orderItemPriceInfos"));
                    if (UtilValidate.isNotEmpty(orderItemPriceInfosFromRule)) {
                        orderItemPriceInfos.addAll(orderItemPriceInfosFromRule);
                    }
                    result.put("orderItemPriceInfos", orderItemPriceInfos);

                    Map<String, Object> errorResult = addGeneralResults(result, competitivePriceValue, specialPromoPriceValue, productStore,
                            checkIncludeVat, currencyUomId, productId, quantity, partyId, delegator);
                    if (errorResult != null) return errorResult;
                } else {
                    Map<String, Object> calcResults = calcPriceResultFromRules(allProductPriceRules, listPrice, defaultPrice, promoPrice,
                        wholesalePrice, maximumPriceValue, minimumPriceValue, validPriceFound,
                        averageCostValue, productId, virtualProductId, prodCatalogId, productStoreGroupId,
                        webSiteId, partyId, quantity, currencyUomId, delegator, nowTimestamp);
                    result.putAll(calcResults);
                    // The orderItemPriceInfos out parameter requires a special treatment:
                    // the list of OrderItemPriceInfos generated by the price rule is appended to
                    // the existing orderItemPriceInfos list and the aggregated list is returned.
                    List<GenericValue> orderItemPriceInfosFromRule = UtilGenerics.checkList(calcResults.get("orderItemPriceInfos"));
                    if (UtilValidate.isNotEmpty(orderItemPriceInfosFromRule)) {
                        orderItemPriceInfos.addAll(orderItemPriceInfosFromRule);
                    }
                    result.put("orderItemPriceInfos", orderItemPriceInfos);

                    Map<String, Object> errorResult = addGeneralResults(result, competitivePriceValue, specialPromoPriceValue, productStore,
                        checkIncludeVat, currencyUomId, productId, quantity, partyId, delegator);
                    if (errorResult != null) return errorResult;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, "Error getting rules from the database while calculating price", module);
                return ServiceUtil.returnError("Error getting rules from the database while calculating price: " + e.toString());
            }
        }

        // utilTimer.timerString("Finished price calc [productId=" + productId + "]", module);
        return result;
    }
	  public static Map<String, Object> addGeneralResults(Map<String, Object> result, GenericValue competitivePriceValue, GenericValue specialPromoPriceValue, GenericValue productStore,
		        String checkIncludeVat, String currencyUomId, String productId, BigDecimal quantity, String partyId, GenericDelegator delegator) {
		        result.put("competitivePrice", competitivePriceValue != null ? competitivePriceValue.getBigDecimal("price") : null);
		        result.put("specialPromoPrice", specialPromoPriceValue != null ? specialPromoPriceValue.getBigDecimal("price") : null);
		        result.put("currencyUsed", currencyUomId);

		        // okay, now we have the calculated price, see if we should add in tax and if so do it
		        if ("Y".equals(checkIncludeVat) && productStore != null && "Y".equals(productStore.getString("showPricesWithVatTax"))) {
		            Map<String, Object> calcTaxForDisplayContext = UtilMisc.toMap("productStoreId", productStore.get("productStoreId"),
		                    "productId", productId, "quantity", quantity,
		                    "basePrice", (BigDecimal) result.get("price"));
		            if (UtilValidate.isNotEmpty(partyId)) {
		                calcTaxForDisplayContext.put("billToPartyId", partyId);
		            }

//		            try {
		                Map<String, Object>  calcTaxForDisplayResult = rateProductTaxCalcForDisplay(delegator, calcTaxForDisplayContext);
		                if (ServiceUtil.isError(calcTaxForDisplayResult)) {
		                    return ServiceUtil.returnError("Error calculating VAT tax (with calcTaxForDisplay service)", null, null, calcTaxForDisplayResult);
		                }
		                // taxTotal, taxPercentage, priceWithTax
		                result.put("price", (BigDecimal) calcTaxForDisplayResult.get("priceWithTax"));

		                // based on the taxPercentage calculate the other amounts, including: listPrice, defaultPrice, averageCost, promoPrice, competitivePrice
		                BigDecimal taxPercentage = (BigDecimal) calcTaxForDisplayResult.get("taxPercentage");
		                BigDecimal taxMultiplier = ONE_BASE.add(taxPercentage.divide(PERCENT_SCALE, taxCalcScale));
		                if (result.get("listPrice") != null) {
		                    result.put("listPrice", ((BigDecimal) result.get("listPrice")).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ));
		                }
		                if (result.get("defaultPrice") != null) {
		                    result.put("defaultPrice", ((BigDecimal) result.get("defaultPrice")).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ));
		                }
		                if (result.get("averageCost") != null) {
		                    result.put("averageCost", ((BigDecimal) result.get("averageCost")).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ));
		                }
		                if (result.get("promoPrice") != null) {
		                    result.put("promoPrice", ((BigDecimal) result.get("promoPrice")).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ));
		                }
		                if (result.get("competitivePrice") != null) {
		                    result.put("competitivePrice", ((BigDecimal) result.get("competitivePrice")).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ));
		                }
//		            } catch (GenericServiceException e) {
//		                String errMsg = "Error calculating VAT tax (with calcTaxForDisplay service): " + e.toString();
//		                Debug.logError(e, errMsg, module);
//		                return ServiceUtil.returnError(errMsg);
//		            }
		        }

		        return null;
		    }
//    public static Map addGeneralResults(Map result, GenericValue competitivePriceValue, GenericValue specialPromoPriceValue, GenericValue productStore, 
//            String checkIncludeVat, String currencyUomId, String productId, double quantity, String partyId, GenericDelegator delegator) {
//            result.put("competitivePrice", competitivePriceValue != null ? competitivePriceValue.getDouble("price") : null);
//            result.put("specialPromoPrice", specialPromoPriceValue != null ? specialPromoPriceValue.getDouble("price") : null);
//            result.put("currencyUsed", currencyUomId);
//
//            // okay, now we have the calculated price, see if we should add in tax and if so do it
//            if ("Y".equals(checkIncludeVat) && productStore != null && "Y".equals(productStore.getString("showPricesWithVatTax"))) {
//                Map calcTaxForDisplayContext = UtilMisc.toMap("productStoreId", productStore.get("productStoreId"), 
//                        "productId", productId, "quantity", new BigDecimal(quantity), 
//                        "basePrice", new BigDecimal(((Double) result.get("price")).doubleValue()));
//                if (UtilValidate.isNotEmpty(partyId)) {
//                    calcTaxForDisplayContext.put("billToPartyId", partyId);
//                }
//                
//                    Map calcTaxForDisplayResult = rateProductTaxCalcForDisplay(delegator, calcTaxForDisplayContext);
//                    if (ServiceUtil.isError(calcTaxForDisplayResult)) {
//                        return ServiceUtil.returnError("Error calculating VAT tax (with calcTaxForDisplay service)", null, null, calcTaxForDisplayResult);
//                    }
//                    // taxTotal, taxPercentage, priceWithTax
//                    result.put("price", new Double(((BigDecimal) calcTaxForDisplayResult.get("priceWithTax")).doubleValue()));
//
//                    // based on the taxPercentage calculate the other amounts, including: listPrice, defaultPrice, averageCost, promoPrice, competitivePrice
//                    BigDecimal taxPercentage = (BigDecimal) calcTaxForDisplayResult.get("taxPercentage");
//                    BigDecimal taxMultiplier = ONE_BASE.add(taxPercentage.divide(PERCENT_SCALE, taxCalcScale));
//                    if (result.get("listPrice") != null) {
//                        result.put("listPrice", new Double( new BigDecimal(((Double) result.get("listPrice")).doubleValue()).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ).doubleValue()));
//                    }
//                    if (result.get("defaultPrice") != null) {                    
//                        result.put("defaultPrice", new Double( new BigDecimal(((Double) result.get("defaultPrice")).doubleValue()).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ).doubleValue()));
//                    }
//                    if (result.get("averageCost") != null) {
//                        result.put("averageCost", new Double( new BigDecimal(((Double) result.get("averageCost")).doubleValue()).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ).doubleValue()));
//                    }               
//                    if (result.get("promoPrice") != null) {
//                        result.put("promoPrice", new Double( new BigDecimal(((Double) result.get("promoPrice")).doubleValue()).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ).doubleValue()));
//                    }
//                    if (result.get("competitivePrice") != null) {
//                        result.put("competitivePrice", new Double( new BigDecimal(((Double) result.get("competitivePrice")).doubleValue()).multiply(taxMultiplier).setScale( taxFinalScale, taxRounding ).doubleValue()));
//                    }
//            }
//            
//            return null;
//        }
//        
        public static List makeProducePriceRuleList(GenericDelegator delegator, boolean optimizeForLargeRuleSet, String productId, String virtualProductId, String prodCatalogId, String productStoreGroupId, String webSiteId, String partyId, String currencyUomId) throws GenericEntityException {
            List productPriceRules = null;

            // At this point we have two options: optimize for large ruleset, or optimize for small ruleset
            // NOTE: This only effects the way that the rules to be evaluated are selected.
            // For large rule sets we can do a cached pre-filter to limit the rules that need to be evaled for a specific product.
            // Genercally I don't think that rule sets will get that big though, so the default is optimize for smaller rule set.
            if (optimizeForLargeRuleSet) {
                // ========= find all rules that must be run for each input type; this is kind of like a pre-filter to slim down the rules to run =========
                // utilTimer.timerString("Before create rule id list", module);
                TreeSet productPriceRuleIds = new TreeSet();

                // ------- These are all of the conditions that DON'T depend on the current inputs -------

                // by productCategoryId
                // for we will always include any rules that go by category, shouldn't be too many to iterate through each time and will save on cache entries
                // note that we always want to put the category, quantity, etc ones that find all rules with these conditions in separate cache lists so that they can be easily cleared
                Collection productCategoryIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PROD_CAT_ID"));
                if (productCategoryIdConds != null && productCategoryIdConds.size() > 0) {
                    Iterator productCategoryIdCondsIter = productCategoryIdConds.iterator();
                    while (productCategoryIdCondsIter.hasNext()) {
                        GenericValue productCategoryIdCond = (GenericValue) productCategoryIdCondsIter.next();
                        productPriceRuleIds.add(productCategoryIdCond.getString("productPriceRuleId"));
                    }
                }

                // by productFeatureId
                Collection productFeatureIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PROD_FEAT_ID"));
                if (productFeatureIdConds != null && productFeatureIdConds.size() > 0) {
                    Iterator productFeatureIdCondsIter = productFeatureIdConds.iterator();
                    while (productFeatureIdCondsIter.hasNext()) {
                        GenericValue productFeatureIdCond = (GenericValue) productFeatureIdCondsIter.next();
                        productPriceRuleIds.add(productFeatureIdCond.getString("productPriceRuleId"));
                    }
                }

                // by quantity -- should we really do this one, ie is it necessary?
                // we could say that all rules with quantity on them must have one of these other values
                // but, no we'll do it the other way, any that have a quantity will always get compared
                Collection quantityConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_QUANTITY"));
                if (quantityConds != null && quantityConds.size() > 0) {
                    Iterator quantityCondsIter = quantityConds.iterator();
                    while (quantityCondsIter.hasNext()) {
                        GenericValue quantityCond = (GenericValue) quantityCondsIter.next();
                        productPriceRuleIds.add(quantityCond.getString("productPriceRuleId"));
                    }
                }

                // by roleTypeId
                Collection roleTypeIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_ROLE_TYPE"));
                if (roleTypeIdConds != null && roleTypeIdConds.size() > 0) {
                    Iterator roleTypeIdCondsIter = roleTypeIdConds.iterator();
                    while (roleTypeIdCondsIter.hasNext()) {
                        GenericValue roleTypeIdCond = (GenericValue) roleTypeIdCondsIter.next();
                        productPriceRuleIds.add(roleTypeIdCond.getString("productPriceRuleId"));
                    }
                }

                // TODO, not supported yet: by groupPartyId
                // TODO, not supported yet: by partyClassificationGroupId
                // later: (by partyClassificationTypeId)

                // by listPrice
                Collection listPriceConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_LIST_PRICE"));
                if (listPriceConds != null && listPriceConds.size() > 0) {
                    Iterator listPriceCondsIter = listPriceConds.iterator();
                    while (listPriceCondsIter.hasNext()) {
                        GenericValue listPriceCond = (GenericValue) listPriceCondsIter.next();
                        productPriceRuleIds.add(listPriceCond.getString("productPriceRuleId"));
                    }
                }

                // ------- These are all of them that DO depend on the current inputs -------

                // by productId
                Collection productIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PRODUCT_ID", "condValue", productId));
                if (productIdConds != null && productIdConds.size() > 0) {
                    Iterator productIdCondsIter = productIdConds.iterator();
                    while (productIdCondsIter.hasNext()) {
                        GenericValue productIdCond = (GenericValue) productIdCondsIter.next();
                        productPriceRuleIds.add(productIdCond.getString("productPriceRuleId"));
                    }
                }

                // by virtualProductId, if not null
                if (virtualProductId != null) {
                    Collection virtualProductIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PRODUCT_ID", "condValue", virtualProductId));
                    if (virtualProductIdConds != null && virtualProductIdConds.size() > 0) {
                        Iterator virtualProductIdCondsIter = virtualProductIdConds.iterator();
                        while (virtualProductIdCondsIter.hasNext()) {
                            GenericValue virtualProductIdCond = (GenericValue) virtualProductIdCondsIter.next();
                            productPriceRuleIds.add(virtualProductIdCond.getString("productPriceRuleId"));
                        }
                    }
                }

                // by prodCatalogId - which is optional in certain cases
                if (UtilValidate.isNotEmpty(prodCatalogId)) {
                    Collection prodCatalogIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PROD_CLG_ID", "condValue", prodCatalogId));
                    if (prodCatalogIdConds != null && prodCatalogIdConds.size() > 0) {
                        Iterator prodCatalogIdCondsIter = prodCatalogIdConds.iterator();
                        while (prodCatalogIdCondsIter.hasNext()) {
                            GenericValue prodCatalogIdCond = (GenericValue) prodCatalogIdCondsIter.next();
                            productPriceRuleIds.add(prodCatalogIdCond.getString("productPriceRuleId"));
                        }
                    }
                }

                // by productStoreGroupId
                if (UtilValidate.isNotEmpty(productStoreGroupId)) {
                    Collection storeGroupConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PROD_SGRP_ID", "condValue", productStoreGroupId));
                    if (storeGroupConds != null && storeGroupConds.size() > 0) {
                        Iterator storeGroupCondsIter = storeGroupConds.iterator();
                        while (storeGroupCondsIter.hasNext()) {
                            GenericValue storeGroupCond = (GenericValue) storeGroupCondsIter.next();
                            productPriceRuleIds.add(storeGroupCond.getString("productPriceRuleId"));
                        }
                    }
                }

                // by webSiteId
                if (UtilValidate.isNotEmpty(webSiteId)) {
                    Collection webSiteIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_WEBSITE_ID", "condValue", webSiteId));
                    if (webSiteIdConds != null && webSiteIdConds.size() > 0) {
                        Iterator webSiteIdCondsIter = webSiteIdConds.iterator();
                        while (webSiteIdCondsIter.hasNext()) {
                            GenericValue webSiteIdCond = (GenericValue) webSiteIdCondsIter.next();
                            productPriceRuleIds.add(webSiteIdCond.getString("productPriceRuleId"));
                        }
                    }
                }

                // by partyId
                if (UtilValidate.isNotEmpty(partyId)) {
                    Collection partyIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_PARTY_ID", "condValue", partyId));
                    if (partyIdConds != null && partyIdConds.size() > 0) {
                        Iterator partyIdCondsIter = partyIdConds.iterator();
                        while (partyIdCondsIter.hasNext()) {
                            GenericValue partyIdCond = (GenericValue) partyIdCondsIter.next();
                            productPriceRuleIds.add(partyIdCond.getString("productPriceRuleId"));
                        }
                    }
                }

                // by currencyUomId
                Collection currencyUomIdConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("inputParamEnumId", "PRIP_CURRENCY_UOMID", "condValue", currencyUomId));
                if (currencyUomIdConds != null && currencyUomIdConds.size() > 0) {
                    Iterator currencyUomIdCondsIter = currencyUomIdConds.iterator();
                    while (currencyUomIdCondsIter.hasNext()) {
                        GenericValue currencyUomIdCond = (GenericValue) currencyUomIdCondsIter.next();
                        productPriceRuleIds.add(currencyUomIdCond.getString("productPriceRuleId"));
                    }
                }

                productPriceRules = FastList.newInstance();
                Iterator productPriceRuleIdsIter = productPriceRuleIds.iterator();
                while (productPriceRuleIdsIter.hasNext()) {
                    String productPriceRuleId = (String) productPriceRuleIdsIter.next();
                    GenericValue productPriceRule = delegator.findByPrimaryKeyCache("ProductPriceRule", UtilMisc.toMap("productPriceRuleId", productPriceRuleId));
                    if (productPriceRule == null) continue;
                    productPriceRules.add(productPriceRule);
                }
            } else {
                // this would be nice, but we can't cache this so easily...
                // List pprExprs = UtilMisc.toList(new EntityExpr("thruDate", EntityOperator.EQUALS, null),
                // new EntityExpr("thruDate", EntityOperator.GREATER_THAN, UtilDateTime.nowTimestamp()));
                // productPriceRules = delegator.findByOr("ProductPriceRule", pprExprs);

                productPriceRules = delegator.findAllCache("ProductPriceRule");
                if (productPriceRules == null) productPriceRules = new LinkedList();
            }
            
            return productPriceRules;
        }
        
        public static Map<String, Object> calcPriceResultFromRules(List<GenericValue> productPriceRules, BigDecimal listPrice, BigDecimal defaultPrice, BigDecimal promoPrice,
                BigDecimal wholesalePrice, GenericValue maximumPriceValue, GenericValue minimumPriceValue, boolean validPriceFound,
                GenericValue averageCostValue, String productId, String virtualProductId, String prodCatalogId, String productStoreGroupId,
                String webSiteId, String partyId, BigDecimal quantity, String currencyUomId, GenericDelegator delegator, Timestamp nowTimestamp) throws GenericEntityException {

                Map<String, Object> calcResults = FastMap.newInstance();

                List<GenericValue> orderItemPriceInfos = FastList.newInstance();
                boolean isSale = false;

                // ========= go through each price rule by id and eval all conditions =========
                // utilTimer.timerString("Before eval rules", module);
                int totalConds = 0;
                int totalActions = 0;
                int totalRules = 0;

                // get some of the base values to calculate with
                BigDecimal averageCost = (averageCostValue != null && averageCostValue.get("price") != null) ? averageCostValue.getBigDecimal("price") : listPrice;
                BigDecimal margin = listPrice.subtract(averageCost);

                // calculate running sum based on listPrice and rules found
                BigDecimal price = listPrice;

                for (GenericValue productPriceRule: productPriceRules) {
                    String productPriceRuleId = productPriceRule.getString("productPriceRuleId");

                    // check from/thru dates
                    java.sql.Timestamp fromDate = productPriceRule.getTimestamp("fromDate");
                    java.sql.Timestamp thruDate = productPriceRule.getTimestamp("thruDate");

                    if (fromDate != null && fromDate.after(nowTimestamp)) {
                        // hasn't started yet
                        continue;
                    }
                    if (thruDate != null && thruDate.before(nowTimestamp)) {
                        // already expired
                        continue;
                    }

                    // check all conditions
                    boolean allTrue = true;
                    StringBuilder condsDescription = new StringBuilder();
                    List<GenericValue> productPriceConds = delegator.findByAndCache("ProductPriceCond", UtilMisc.toMap("productPriceRuleId", productPriceRuleId));
                    for (GenericValue productPriceCond: productPriceConds) {

                        totalConds++;

                        if (!checkPriceCondition(productPriceCond, productId, virtualProductId, prodCatalogId, productStoreGroupId, webSiteId, partyId, quantity, listPrice, currencyUomId, delegator, nowTimestamp)) {
                            allTrue = false;
                            break;
                        }

                        // add condsDescription string entry
                        condsDescription.append("[");
                        GenericValue inputParamEnum = productPriceCond.getRelatedOneCache("InputParamEnumeration");

                        condsDescription.append(inputParamEnum.getString("enumCode"));
                        // condsDescription.append(":");
                        GenericValue operatorEnum = productPriceCond.getRelatedOneCache("OperatorEnumeration");

                        condsDescription.append(operatorEnum.getString("description"));
                        // condsDescription.append(":");
                        condsDescription.append(productPriceCond.getString("condValue"));
                        condsDescription.append("] ");
                    }

                    // add some info about the prices we are calculating from
                    condsDescription.append("[list:");
                    condsDescription.append(listPrice);
                    condsDescription.append(";avgCost:");
                    condsDescription.append(averageCost);
                    condsDescription.append(";margin:");
                    condsDescription.append(margin);
                    condsDescription.append("] ");

                    boolean foundFlatOverride = false;

                    // if all true, perform all actions
                    if (allTrue) {
                        // check isSale
                        if ("Y".equals(productPriceRule.getString("isSale"))) {
                            isSale = true;
                        }

                        List<GenericValue> productPriceActions = delegator.findByAndCache("ProductPriceAction", UtilMisc.toMap("productPriceRuleId", productPriceRuleId));
                        for (GenericValue productPriceAction: productPriceActions) {

                            totalActions++;

                            // yeah, finally here, perform the action, ie, modify the price
                            BigDecimal modifyAmount = BigDecimal.ZERO;

                            if ("PRICE_POD".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                if (productPriceAction.get("amount") != null) {
                                    modifyAmount = defaultPrice.multiply(productPriceAction.getBigDecimal("amount").movePointLeft(2));
                                }
                            } else if ("PRICE_POL".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                if (productPriceAction.get("amount") != null) {
                                    modifyAmount = listPrice.multiply(productPriceAction.getBigDecimal("amount").movePointLeft(2));
                                }
                            } else if ("PRICE_POAC".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                if (productPriceAction.get("amount") != null) {
                                    modifyAmount = averageCost.multiply(productPriceAction.getBigDecimal("amount").movePointLeft(2));
                                }
                            } else if ("PRICE_POM".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                if (productPriceAction.get("amount") != null) {
                                    modifyAmount = margin.multiply(productPriceAction.getBigDecimal("amount").movePointLeft(2));
                                }
                            } else if ("PRICE_FOL".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                if (productPriceAction.get("amount") != null) {
                                    modifyAmount = productPriceAction.getBigDecimal("amount");
                                }
                            } else if ("PRICE_FLAT".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                // this one is a bit different, break out of the loop because we now have our final price
                                foundFlatOverride = true;
                                if (productPriceAction.get("amount") != null) {
                                    price = productPriceAction.getBigDecimal("amount");
                                } else {
                                    Debug.logInfo("ProductPriceAction had null amount, using default price: " + defaultPrice + " for product with id " + productId, module);
                                    price = defaultPrice;
                                    isSale = false;                // reverse isSale flag, as this sale rule was actually not applied
                                }
                            } else if ("PRICE_PFLAT".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                // this one is a bit different too, break out of the loop because we now have our final price
                                foundFlatOverride = true;
                                price = promoPrice;
                                if (productPriceAction.get("amount") != null) {
                                    price = price.add(productPriceAction.getBigDecimal("amount"));
                                }
                                if (price.compareTo(BigDecimal.ZERO) == 0) {
                                    if (defaultPrice.compareTo(BigDecimal.ZERO) != 0) {
                                        Debug.logInfo("PromoPrice and ProductPriceAction had null amount, using default price: " + defaultPrice + " for product with id " + productId, module);
                                        price = defaultPrice;
                                    } else if (listPrice.compareTo(BigDecimal.ZERO) != 0) {
                                        Debug.logInfo("PromoPrice and ProductPriceAction had null amount and no default price was available, using list price: " + listPrice + " for product with id " + productId, module);
                                        price = listPrice;
                                    } else {
                                        Debug.logError("PromoPrice and ProductPriceAction had null amount and no default or list price was available, so price is set to zero for product with id " + productId, module);
                                        price = BigDecimal.ZERO;
                                    }
                                    isSale = false;                // reverse isSale flag, as this sale rule was actually not applied
                                }
                            } else if ("PRICE_WFLAT".equals(productPriceAction.getString("productPriceActionTypeId"))) {
                                // same as promo price but using the wholesale price instead
                                foundFlatOverride = true;
                                price = wholesalePrice;
                                if (productPriceAction.get("amount") != null) {
                                    price = price.add(productPriceAction.getBigDecimal("amount"));
                                }
                                if (price.compareTo(BigDecimal.ZERO) == 0) {
                                    if (defaultPrice.compareTo(BigDecimal.ZERO) != 0) {
                                        Debug.logInfo("WholesalePrice and ProductPriceAction had null amount, using default price: " + defaultPrice + " for product with id " + productId, module);
                                        price = defaultPrice;
                                    } else if (listPrice.compareTo(BigDecimal.ZERO) != 0) {
                                        Debug.logInfo("WholesalePrice and ProductPriceAction had null amount and no default price was available, using list price: " + listPrice + " for product with id " + productId, module);
                                        price = listPrice;
                                    } else {
                                        Debug.logError("WholesalePrice and ProductPriceAction had null amount and no default or list price was available, so price is set to zero for product with id " + productId, module);
                                        price = BigDecimal.ZERO;
                                    }
                                    isSale = false; // reverse isSale flag, as this sale rule was actually not applied
                                }
                            }

                            // add a orderItemPriceInfo element too, without orderId or orderItemId
                            StringBuilder priceInfoDescription = new StringBuilder();

                            priceInfoDescription.append(condsDescription.toString());
                            priceInfoDescription.append("[type:");
                            priceInfoDescription.append(productPriceAction.getString("productPriceActionTypeId"));
                            priceInfoDescription.append("]");

                            GenericValue orderItemPriceInfo = delegator.makeValue("OrderItemPriceInfo");

                            orderItemPriceInfo.set("productPriceRuleId", productPriceAction.get("productPriceRuleId"));
                            orderItemPriceInfo.set("productPriceActionSeqId", productPriceAction.get("productPriceActionSeqId"));
                            orderItemPriceInfo.set("modifyAmount", modifyAmount);
                            orderItemPriceInfo.set("rateCode", productPriceAction.get("rateCode"));
                            // make sure description is <= than 250 chars
                            String priceInfoDescriptionString = priceInfoDescription.toString();

                            if (priceInfoDescriptionString.length() > 250) {
                                priceInfoDescriptionString = priceInfoDescriptionString.substring(0, 250);
                            }
                            orderItemPriceInfo.set("description", priceInfoDescriptionString);
                            orderItemPriceInfos.add(orderItemPriceInfo);

                            if (foundFlatOverride) {
                                break;
                            } else {
                                price = price.add(modifyAmount);
                            }
                        }
                    }

                    totalRules++;

                    if (foundFlatOverride) {
                        break;
                    }
                }

                if (Debug.verboseOn()) {
                    Debug.logVerbose("Unchecked Calculated price: " + price, module);
                    Debug.logVerbose("PriceInfo:", module);
                    for (GenericValue orderItemPriceInfo: orderItemPriceInfos) {
                        Debug.logVerbose(" --- " + orderItemPriceInfo.toString(), module);
                    }
                }

                // if no actions were run on the list price, then use the default price
                if (totalActions == 0) {
                    price = defaultPrice;
                    // here we will leave validPriceFound as it was originally set for the defaultPrice since that is what we are setting the price to...
                } else {
                    // at least one price rule action was found, so we will consider it valid
                    validPriceFound = true;
                }

                // ========= ensure calculated price is not below minSalePrice or above maxSalePrice =========
                BigDecimal maxSellPrice = maximumPriceValue != null ? maximumPriceValue.getBigDecimal("price") : null;
                if (maxSellPrice != null && price.compareTo(maxSellPrice) > 0) {
                    price = maxSellPrice;
                }
                // min price second to override max price, safety net
                BigDecimal minSellPrice = minimumPriceValue != null ? minimumPriceValue.getBigDecimal("price") : null;
                if (minSellPrice != null && price.compareTo(minSellPrice) < 0) {
                    price = minSellPrice;
                    // since we have found a minimum price that has overriden a the defaultPrice, even if no valid one was found, we will consider it as if one had been...
                    validPriceFound = true;
                }

                if (Debug.verboseOn()) Debug.logVerbose("Final Calculated price: " + price + ", rules: " + totalRules + ", conds: " + totalConds + ", actions: " + totalActions, module);

                calcResults.put("basePrice", price);
                calcResults.put("price", price);
                calcResults.put("listPrice", listPrice);
                calcResults.put("defaultPrice", defaultPrice);
                calcResults.put("averageCost", averageCost);
                calcResults.put("orderItemPriceInfos", orderItemPriceInfos);
                calcResults.put("isSale", Boolean.valueOf(isSale));
                calcResults.put("validPriceFound", Boolean.valueOf(validPriceFound));

                return calcResults;
            }

        public static boolean checkPriceCondition(GenericValue productPriceCond, String productId, String virtualProductId, String prodCatalogId,
                String productStoreGroupId, String webSiteId, String partyId, BigDecimal quantity, BigDecimal listPrice,
                String currencyUomId, GenericDelegator delegator, Timestamp nowTimestamp) throws GenericEntityException {
            if (Debug.verboseOn()) Debug.logVerbose("Checking price condition: " + productPriceCond, module);
            int compare = 0;

            if ("PRIP_PRODUCT_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                compare = productId.compareTo(productPriceCond.getString("condValue"));
            } else if ("PRIP_PROD_CAT_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                // if a ProductCategoryMember exists for this productId and the specified productCategoryId
                String productCategoryId = productPriceCond.getString("condValue");
                List<GenericValue> productCategoryMembers = delegator.findByAndCache("ProductCategoryMember",
                        UtilMisc.toMap("productId", productId, "productCategoryId", productCategoryId));
                // and from/thru date within range
                productCategoryMembers = EntityUtil.filterByDate(productCategoryMembers, nowTimestamp, null, null, true);
                // then 0 (equals), otherwise 1 (not equals)
                if (UtilValidate.isNotEmpty(productCategoryMembers)) {
                    compare = 0;
                } else {
                    compare = 1;
                }

                // if there is a virtualProductId, try that given that this one has failed
                // NOTE: this is important becuase of the common scenario where a virtual product is a member of a category but the variants will typically NOT be
                // NOTE: we may want to parameterize this in the future, ie with an indicator on the ProductPriceCond entity
                if (compare == 1 && UtilValidate.isNotEmpty(virtualProductId)) {
                    List<GenericValue> virtualProductCategoryMembers = delegator.findByAndCache("ProductCategoryMember",
                            UtilMisc.toMap("productId", virtualProductId, "productCategoryId", productCategoryId));
                    // and from/thru date within range
                    virtualProductCategoryMembers = EntityUtil.filterByDate(virtualProductCategoryMembers, nowTimestamp, null, null, true);
                    if (UtilValidate.isNotEmpty(virtualProductCategoryMembers)) {
                        // we found a member record? great, then this condition is satisfied
                        compare = 0;
                    }
                }
            } else if ("PRIP_PROD_FEAT_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                // NOTE: DEJ20070130 don't retry this condition with the virtualProductId as well; this breaks various things you might want to do with price rules, like have different pricing for a variant products with a certain distinguishing feature

                // if a ProductFeatureAppl exists for this productId and the specified productFeatureId
                String productFeatureId = productPriceCond.getString("condValue");
                List<GenericValue> productFeatureAppls = delegator.findByAndCache("ProductFeatureAppl",
                        UtilMisc.toMap("productId", productId, "productFeatureId", productFeatureId));
                // and from/thru date within range
                productFeatureAppls = EntityUtil.filterByDate(productFeatureAppls, nowTimestamp, null, null, true);
                // then 0 (equals), otherwise 1 (not equals)
                if (UtilValidate.isNotEmpty(productFeatureAppls)) {
                    compare = 0;
                } else {
                    compare = 1;
                }
            } else if ("PRIP_PROD_CLG_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isNotEmpty(prodCatalogId)) {
                    compare = prodCatalogId.compareTo(productPriceCond.getString("condValue"));
                } else {
                    // this shouldn't happen because if prodCatalogId is null no PRIP_PROD_CLG_ID prices will be in the list
                    compare = 1;
                }
            } else if ("PRIP_PROD_SGRP_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isNotEmpty(productStoreGroupId)) {
                    compare = productStoreGroupId.compareTo(productPriceCond.getString("condValue"));
                } else {
                    compare = 1;
                }
            } else if ("PRIP_WEBSITE_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isNotEmpty(webSiteId)) {
                    compare = webSiteId.compareTo(productPriceCond.getString("condValue"));
                } else {
                    compare = 1;
                }
            } else if ("PRIP_QUANTITY".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (quantity == null) {
                    // if no quantity is passed in, assume all quantity conditions pass
                    // NOTE: setting compare = 0 won't do the trick here because the condition won't always be or include and equal
                    return true;
                } else {
                    compare = quantity.compareTo(new BigDecimal(productPriceCond.getString("condValue")));
                }
            } else if ("PRIP_PARTY_ID".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isNotEmpty(partyId)) {
                    compare = partyId.compareTo(productPriceCond.getString("condValue"));
                } else {
                    compare = 1;
                }
            } else if ("PRIP_PARTY_GRP_MEM".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isEmpty(partyId)) {
                    compare = 1;
                } else {
                    String groupPartyId = productPriceCond.getString("condValue");
                    if (partyId.equals(groupPartyId)) {
                        compare = 0;
                    } else {
                        // look for PartyRelationship with partyRelationshipTypeId=GROUP_ROLLUP, the partyIdTo is the group member, so the partyIdFrom is the groupPartyId
                        List<GenericValue> partyRelationshipList = delegator.findByAndCache("PartyRelationship", UtilMisc.toMap("partyIdFrom", groupPartyId, "partyIdTo", partyId, "partyRelationshipTypeId", "GROUP_ROLLUP"));
                        // and from/thru date within range
                        partyRelationshipList = EntityUtil.filterByDate(partyRelationshipList, nowTimestamp, null, null, true);
                        // then 0 (equals), otherwise 1 (not equals)
                        if (UtilValidate.isNotEmpty(partyRelationshipList)) {
                            compare = 0;
                        } else {
                            // before setting 1 try one more query: look for a 2 hop relationship
                            List<GenericValue> partyRelationshipTwoHopList = delegator.findByAndCache("PartyRelationshipToFrom", UtilMisc.toMap("onePartyIdFrom", groupPartyId, "twoPartyIdTo", partyId, "onePartyRelationshipTypeId", "GROUP_ROLLUP", "twoPartyRelationshipTypeId", "GROUP_ROLLUP"));
                            partyRelationshipTwoHopList = EntityUtil.filterByDate(partyRelationshipTwoHopList, nowTimestamp, "oneFromDate", "oneThruDate", true);
                            partyRelationshipTwoHopList = EntityUtil.filterByDate(partyRelationshipTwoHopList, nowTimestamp, "twoFromDate", "twoThruDate", true);
                            if (UtilValidate.isNotEmpty(partyRelationshipTwoHopList)) {
                                compare = 0;
                            } else {
                                compare = 1;
                            }
                        }
                    }
                }
            } else if ("PRIP_PARTY_CLASS".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (UtilValidate.isEmpty(partyId)) {
                    compare = 1;
                } else {
                    String partyClassificationGroupId = productPriceCond.getString("condValue");
                    // find any PartyClassification
                    List<GenericValue> partyClassificationList = delegator.findByAndCache("PartyClassification", UtilMisc.toMap("partyId", partyId, "partyClassificationGroupId", partyClassificationGroupId));
                    // and from/thru date within range
                    partyClassificationList = EntityUtil.filterByDate(partyClassificationList, nowTimestamp, null, null, true);
                    // then 0 (equals), otherwise 1 (not equals)
                    if (UtilValidate.isNotEmpty(partyClassificationList)) {
                        compare = 0;
                    } else {
                        compare = 1;
                    }
                }
            } else if ("PRIP_ROLE_TYPE".equals(productPriceCond.getString("inputParamEnumId"))) {
                if (partyId != null) {
                    // if a PartyRole exists for this partyId and the specified roleTypeId
                    GenericValue partyRole = delegator.findByPrimaryKeyCache("PartyRole",
                            UtilMisc.toMap("partyId", partyId, "roleTypeId", productPriceCond.getString("condValue")));

                    // then 0 (equals), otherwise 1 (not equals)
                    if (partyRole != null) {
                        compare = 0;
                    } else {
                        compare = 1;
                    }
                } else {
                    compare = 1;
                }
            } else if ("PRIP_LIST_PRICE".equals(productPriceCond.getString("inputParamEnumId"))) {
                BigDecimal listPriceValue = listPrice;

                compare = listPriceValue.compareTo(new BigDecimal(productPriceCond.getString("condValue")));
            } else if ("PRIP_CURRENCY_UOMID".equals(productPriceCond.getString("inputParamEnumId"))) {
                compare = currencyUomId.compareTo(productPriceCond.getString("condValue"));
            } else {
                Debug.logWarning("An un-supported productPriceCond input parameter (lhs) was used: " + productPriceCond.getString("inputParamEnumId") + ", returning false, ie check failed", module);
                return false;
            }

            if (Debug.verboseOn()) Debug.logVerbose("Price Condition compare done, compare=" + compare, module);

            if ("PRC_EQ".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare == 0) return true;
            } else if ("PRC_NEQ".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare != 0) return true;
            } else if ("PRC_LT".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare < 0) return true;
            } else if ("PRC_LTE".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare <= 0) return true;
            } else if ("PRC_GT".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare > 0) return true;
            } else if ("PRC_GTE".equals(productPriceCond.getString("operatorEnumId"))) {
                if (compare >= 0) return true;
            } else {
                Debug.logWarning("An un-supported productPriceCond condition was used: " + productPriceCond.getString("operatorEnumId") + ", returning false, ie check failed", module);
                return false;
            }
            return false;
        }
        
        /**
         * Calculates the purchase price of a product
         */
        public static Map calculatePurchasePrice(GenericDelegator delegator, Map context) {
            Map result = new HashMap();

            List orderItemPriceInfos = new LinkedList();
            boolean validPriceFound = false;
            double price = 0.0;

            GenericValue product = (GenericValue)context.get("product");
            String productId = product.getString("productId");
            String currencyUomId = (String)context.get("currencyUomId");
            String partyId = (String)context.get("partyId");
            Double quantity = (Double)context.get("quantity");
            
            // a) Get the Price from the Agreement* data model
            // TODO: Implement this

            // b) If no price can be found, get the lastPrice from the SupplierProduct entity
            if (!validPriceFound) {
                Map priceContext = UtilMisc.toMap("currencyUomId", currencyUomId, "partyId", partyId, "productId", productId, "quantity", quantity);
                List productSuppliers = null;
			Map priceResult = getSuppliersForProduct(delegator, priceContext);
			if (ServiceUtil.isError(priceResult)) {
				String errMsg = ServiceUtil.getErrorMessage(priceResult);
				Debug.logError(errMsg, module);
				return ServiceUtil.returnError(errMsg);
			}
			productSuppliers = (List) priceResult.get("supplierProducts");
			if (productSuppliers != null) {
                    for (int i = 0; i < productSuppliers.size(); i++) {
                        GenericValue productSupplier = (GenericValue) productSuppliers.get(i);
                        if (!validPriceFound) {
                            price = ((Double)productSupplier.get("lastPrice")).doubleValue();
                            validPriceFound = true;
                        }
                        // add a orderItemPriceInfo element too, without orderId or orderItemId
                        StringBuffer priceInfoDescription = new StringBuffer();
                        priceInfoDescription.append("SupplierProduct ");
                        priceInfoDescription.append("[minimumOrderQuantity:");
                        priceInfoDescription.append("" + productSupplier.getDouble("minimumOrderQuantity").doubleValue());
                        priceInfoDescription.append(", lastPrice: " + productSupplier.getDouble("lastPrice").doubleValue());
                        priceInfoDescription.append("]");
                        GenericValue orderItemPriceInfo = delegator.makeValue("OrderItemPriceInfo",  new Object[]{});
                        //orderItemPriceInfo.set("productPriceRuleId", productPriceAction.get("productPriceRuleId"));
                        //orderItemPriceInfo.set("productPriceActionSeqId", productPriceAction.get("productPriceActionSeqId"));
                        //orderItemPriceInfo.set("modifyAmount", new Double(modifyAmount));
                        // make sure description is <= than 250 chars
                        String priceInfoDescriptionString = priceInfoDescription.toString();
                        if (priceInfoDescriptionString.length() > 250) {
                            priceInfoDescriptionString = priceInfoDescriptionString.substring(0, 250);
                        }
                        orderItemPriceInfo.set("description", priceInfoDescriptionString);
                        orderItemPriceInfos.add(orderItemPriceInfo);
                    }
                }
            }

            // c) If no price can be found, get the averageCost from the ProductPrice entity
            if (!validPriceFound) {
                List prices = null;
                try {
                    prices = delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId", productId,
                            "productPricePurposeId", "PURCHASE"), UtilMisc.toList("-fromDate"));

                    // if no prices are found; find the prices of the parent product
                    if (prices == null || prices.size() == 0) {
                        GenericValue parentProduct = ProductWorker.getParentProduct(productId, delegator);
                        if (parentProduct != null) {
                            String parentProductId = parentProduct.getString("productId");
                            prices = delegator.findByAnd("ProductPrice", UtilMisc.toMap("productId", parentProductId,
                                    "productPricePurposeId", "PURCHASE"), UtilMisc.toList("-fromDate"));
                        }
                    }
                } catch (GenericEntityException e) {
                    Debug.logError(e, module);
                    return ServiceUtil.returnError(e.getMessage());
                }

                // filter out the old prices
                prices = EntityUtil.filterByDate(prices);

                // first check for the AVERAGE_COST price type
                List pricesToUse = EntityUtil.filterByAnd(prices, UtilMisc.toMap("productPriceTypeId", "AVERAGE_COST"));
                if (pricesToUse == null || pricesToUse.size() == 0) {
                    // next go with default price
                    pricesToUse = EntityUtil.filterByAnd(prices, UtilMisc.toMap("productPriceTypeId", "DEFAULT_PRICE"));
                    if (pricesToUse == null || pricesToUse.size() == 0) {
                        // finally use list price
                        pricesToUse = EntityUtil.filterByAnd(prices, UtilMisc.toMap("productPriceTypeId", "LIST_PRICE"));
                    }
                }

                // use the most current price
                GenericValue thisPrice = EntityUtil.getFirst(pricesToUse);
                if (thisPrice != null) {
                    price = thisPrice.getDouble("price").doubleValue();
                    validPriceFound = true;
                }
            }

            result.put("price", new Double(price));
            result.put("validPriceFound", new Boolean(validPriceFound));
            result.put("orderItemPriceInfos", orderItemPriceInfos);
            return result;
        }

        private static List getTaxAdjustments(GenericDelegator delegator, GenericValue product, GenericValue productStore, String payToPartyId, String billToPartyId, Set taxAuthoritySet, BigDecimal itemPrice, BigDecimal itemAmount, BigDecimal shippingAmount, BigDecimal orderPromotionsAmount) {
            Timestamp nowTimestamp = UtilDateTime.nowTimestamp();
            List adjustments = FastList.newInstance();

            if (payToPartyId == null) {
                if (productStore != null) {
                    payToPartyId = productStore.getString("payToPartyId");
                }
            }

            // store expr
            EntityCondition storeCond = null;
            if (productStore != null) {
                storeCond = EntityCondition.makeCondition(
                        EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, productStore.get("productStoreId")),
                        EntityOperator.OR,
                        EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, null));
            } else {
                storeCond = EntityCondition.makeCondition("productStoreId", EntityOperator.EQUALS, null);
            }

            // build the TaxAuthority expressions (taxAuthGeoId, taxAuthPartyId)
            List taxAuthCondOrList = FastList.newInstance();
            // start with the _NA_ TaxAuthority...
            taxAuthCondOrList.add(EntityCondition.makeCondition(
                    EntityCondition.makeCondition("taxAuthPartyId", EntityOperator.EQUALS, "_NA_"),
                    EntityOperator.AND,
                    EntityCondition.makeCondition("taxAuthGeoId", EntityOperator.EQUALS, "_NA_")));

            Iterator taxAuthorityIter = taxAuthoritySet.iterator();
            while (taxAuthorityIter.hasNext()) {
                GenericValue taxAuthority = (GenericValue) taxAuthorityIter.next();
                EntityCondition taxAuthCond = EntityCondition.makeCondition(
                        EntityCondition.makeCondition("taxAuthPartyId", EntityOperator.EQUALS, taxAuthority.getString("taxAuthPartyId")),
                        EntityOperator.AND,
                        EntityCondition.makeCondition("taxAuthGeoId", EntityOperator.EQUALS, taxAuthority.getString("taxAuthGeoId")));
                taxAuthCondOrList.add(taxAuthCond);
            }
            EntityCondition taxAuthoritiesCond = EntityCondition.makeCondition(taxAuthCondOrList, EntityOperator.OR);

            try {
                EntityCondition productCategoryCond = null;
                if (product != null) {
                    // find the tax categories associated with the product and filter by those, with an IN clause or some such
                    // question: get all categories, or just a special type? for now let's do all categories...
                    Set productCategoryIdSet = FastSet.newInstance();
                    List pcmList = delegator.findByAndCache("ProductCategoryMember", UtilMisc.toMap("productId", product.get("productId")));
                    pcmList = EntityUtil.filterByDate(pcmList, true);
                    Iterator pcmIter = pcmList.iterator();
                    while (pcmIter.hasNext()) {
                        GenericValue pcm = (GenericValue) pcmIter.next();
                        productCategoryIdSet.add(pcm.get("productCategoryId"));
                    }

                    if (productCategoryIdSet.size() == 0) {
                        productCategoryCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, null);
                    } else {
                        productCategoryCond = EntityCondition.makeCondition(
                                EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, null),
                                EntityOperator.OR,
                                EntityCondition.makeCondition("productCategoryId", EntityOperator.IN, productCategoryIdSet));
                    }
                } else {
                    productCategoryCond = EntityCondition.makeCondition("productCategoryId", EntityOperator.EQUALS, null);
                }

                // build the main condition clause
                List mainExprs = UtilMisc.toList(storeCond, taxAuthoritiesCond, productCategoryCond);
                mainExprs.add(EntityCondition.makeCondition(EntityCondition.makeCondition("minItemPrice", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("minItemPrice", EntityOperator.LESS_THAN_EQUAL_TO, itemPrice)));
                mainExprs.add(EntityCondition.makeCondition(EntityCondition.makeCondition("minPurchase", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("minPurchase", EntityOperator.LESS_THAN_EQUAL_TO, itemAmount)));
                EntityCondition mainCondition = EntityCondition.makeCondition(mainExprs, EntityOperator.AND);

                // create the orderby clause
                List orderList = UtilMisc.toList("minItemPrice", "minPurchase", "fromDate");

                // finally ready... do the rate query
                List lookupList = delegator.findList("TaxAuthorityRateProduct", mainCondition, null, orderList, null, false);
                List filteredList = EntityUtil.filterByDate(lookupList, true);

                if (filteredList.size() == 0) {
                    Debug.logWarning("In TaxAuthority Product Rate no records were found for condition:" + mainCondition.toString(), module);
                    return adjustments;
                }

                // find the right entry(s) based on purchase amount
                Iterator flIt = filteredList.iterator();
                while (flIt.hasNext()) {
                    GenericValue taxAuthorityRateProduct = (GenericValue) flIt.next();

                    BigDecimal taxRate = taxAuthorityRateProduct.get("taxPercentage") != null ? taxAuthorityRateProduct.getBigDecimal("taxPercentage") : ZERO_BASE;
                    BigDecimal taxable = ZERO_BASE;

                    if (product != null && (product.get("taxable") == null || (product.get("taxable") != null && product.getBoolean("taxable").booleanValue()))) {
                        taxable = taxable.add(itemAmount);
                    }
                    if (shippingAmount != null && taxAuthorityRateProduct != null && (taxAuthorityRateProduct.get("taxShipping") == null || (taxAuthorityRateProduct.get("taxShipping") != null && taxAuthorityRateProduct.getBoolean("taxShipping").booleanValue()))) {
                        taxable = taxable.add(shippingAmount);
                    }
                    if (orderPromotionsAmount != null && taxAuthorityRateProduct != null && (taxAuthorityRateProduct.get("taxPromotions") == null || (taxAuthorityRateProduct.get("taxPromotions") != null && taxAuthorityRateProduct.getBoolean("taxPromotions").booleanValue()))) {
                        taxable = taxable.add(orderPromotionsAmount);
                    }

                    if (taxable.compareTo(BigDecimal.ZERO) == 0) {
                        // this should make it less confusing if the taxable flag on the product is not Y/true, and there is no shipping and such
                        continue;
                    }

                    // taxRate is in percentage, so needs to be divided by 100
                    BigDecimal taxAmount = (taxable.multiply(taxRate)).divide(PERCENT_SCALE, salestaxCalcDecimals, salestaxRounding);

                    String taxAuthGeoId = taxAuthorityRateProduct.getString("taxAuthGeoId");
                    String taxAuthPartyId = taxAuthorityRateProduct.getString("taxAuthPartyId");

                    // get glAccountId from TaxAuthorityGlAccount entity using the payToPartyId as the organizationPartyId
                    GenericValue taxAuthorityGlAccount = delegator.findByPrimaryKey("TaxAuthorityGlAccount", UtilMisc.toMap("taxAuthPartyId", taxAuthPartyId, "taxAuthGeoId", taxAuthGeoId, "organizationPartyId", payToPartyId));
                    String taxAuthGlAccountId = null;
                    if (taxAuthorityGlAccount != null) {
                        taxAuthGlAccountId = taxAuthorityGlAccount.getString("glAccountId");
                    } else {
                        // TODO: what to do if no TaxAuthorityGlAccount found? Use some default, or is that done elsewhere later on?
                    }

                    GenericValue adjValue = delegator.makeValue("OrderAdjustment");
                    adjValue.set("taxAuthorityRateSeqId", taxAuthorityRateProduct.getString("taxAuthorityRateSeqId"));
                    adjValue.set("amount", taxAmount);
                    adjValue.set("sourcePercentage", taxRate);
                    adjValue.set("orderAdjustmentTypeId", "SALES_TAX");
                    // the primary Geo should be the main jurisdiction that the tax is for, and the secondary would just be to define a parent or wrapping jurisdiction of the primary
                    adjValue.set("primaryGeoId", taxAuthGeoId);
                    adjValue.set("comments", taxAuthorityRateProduct.getString("description"));
                    if (taxAuthPartyId != null) adjValue.set("taxAuthPartyId", taxAuthPartyId);
                    if (taxAuthGlAccountId != null) adjValue.set("overrideGlAccountId", taxAuthGlAccountId);
                    if (taxAuthGeoId != null) adjValue.set("taxAuthGeoId", taxAuthGeoId);

                    // check to see if this party has a tax ID for this, and if the party is tax exempt in the primary (most-local) jurisdiction
                    if (UtilValidate.isNotEmpty(billToPartyId) && UtilValidate.isNotEmpty(taxAuthGeoId)) {
                        // see if partyId is a member of any groups, if so honor their tax exemptions
                        // look for PartyRelationship with partyRelationshipTypeId=GROUP_ROLLUP, the partyIdTo is the group member, so the partyIdFrom is the groupPartyId
                        Set billToPartyIdSet = FastSet.newInstance();
                        billToPartyIdSet.add(billToPartyId);
                        List partyRelationshipList = EntityUtil.filterByDate(delegator.findByAndCache("PartyRelationship", UtilMisc.toMap("partyIdTo", billToPartyId, "partyRelationshipTypeId", "GROUP_ROLLUP")), true);
                        Iterator partyRelationshipIter = partyRelationshipList.iterator();
                        while (partyRelationshipIter.hasNext()) {
                            GenericValue partyRelationship = (GenericValue) partyRelationshipIter.next();
                            billToPartyIdSet.add(partyRelationship.get("partyIdFrom"));
                        }

                        handlePartyTaxExempt(adjValue, billToPartyIdSet, taxAuthGeoId, taxAuthPartyId, taxAmount, nowTimestamp, delegator);
                    } else {
                        Debug.logInfo("NOTE: A tax calculation was done without a billToPartyId or taxAuthGeoId, so no tax exemptions or tax IDs considered; billToPartyId=[" + billToPartyId + "] taxAuthGeoId=[" + taxAuthGeoId + "]", module);
                    }

                    adjustments.add(adjValue);
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, "Problems looking up tax rates", module);
                return new ArrayList();
            }

            return adjustments;
        }
        
        private static void handlePartyTaxExempt(GenericValue adjValue, Set billToPartyIdSet, String taxAuthGeoId, String taxAuthPartyId, BigDecimal taxAmount, Timestamp nowTimestamp, GenericDelegator delegator) throws GenericEntityException {
            Debug.logInfo("Checking for tax exemption : " + taxAuthGeoId + " / " + taxAuthPartyId, module);
            List ptiConditionList = UtilMisc.toList(
                    EntityCondition.makeCondition("partyId", EntityOperator.IN, billToPartyIdSet),
                    EntityCondition.makeCondition("taxAuthGeoId", EntityOperator.EQUALS, taxAuthGeoId),
                    EntityCondition.makeCondition("taxAuthPartyId", EntityOperator.EQUALS, taxAuthPartyId));
            ptiConditionList.add(EntityCondition.makeCondition("fromDate", EntityOperator.LESS_THAN_EQUAL_TO, nowTimestamp));
            ptiConditionList.add(EntityCondition.makeCondition(EntityCondition.makeCondition("thruDate", EntityOperator.EQUALS, null), EntityOperator.OR, EntityCondition.makeCondition("thruDate", EntityOperator.GREATER_THAN, nowTimestamp)));
            EntityCondition ptiCondition = EntityCondition.makeCondition(ptiConditionList, EntityOperator.AND);
            // sort by -fromDate to get the newest (largest) first, just in case there is more than one, we only want the most recent valid one, should only be one per jurisdiction...
            List partyTaxInfos = delegator.findList("PartyTaxAuthInfo", ptiCondition, null, UtilMisc.toList("-fromDate"), null, false);

            boolean foundExemption = false;
            if (partyTaxInfos.size() > 0) {
                GenericValue partyTaxInfo = (GenericValue) partyTaxInfos.get(0);
                adjValue.set("customerReferenceId", partyTaxInfo.get("partyTaxId"));
                if ("Y".equals(partyTaxInfo.getString("isExempt"))) {
                    adjValue.set("amount", BigDecimal.ZERO);
                    adjValue.set("exemptAmount", taxAmount);
                    foundExemption = true;
                }
            }

            // if no exceptions were found for the current; try the parent
            if (!foundExemption) {
                // try the "parent" TaxAuthority
                List taxAuthorityAssocList = delegator.findByAndCache("TaxAuthorityAssoc",
                        UtilMisc.toMap("toTaxAuthGeoId", taxAuthGeoId, "toTaxAuthPartyId", taxAuthPartyId, "taxAuthorityAssocTypeId", "EXEMPT_INHER"),
                        UtilMisc.toList("-fromDate"));
                taxAuthorityAssocList = EntityUtil.filterByDate(taxAuthorityAssocList, true);
                GenericValue taxAuthorityAssoc = EntityUtil.getFirst(taxAuthorityAssocList);
                // Debug.log("Parent assoc to " + taxAuthGeoId + " : " + taxAuthorityAssoc, module);
                if (taxAuthorityAssoc != null) {
                    handlePartyTaxExempt(adjValue, billToPartyIdSet, taxAuthorityAssoc.getString("taxAuthGeoId"), taxAuthorityAssoc.getString("taxAuthPartyId"), taxAmount, nowTimestamp, delegator);
                }
            }
        }
        
        public static Map rateProductTaxCalcForDisplay(GenericDelegator delegator, Map context) {
           
            String productStoreId = (String) context.get("productStoreId");
            String billToPartyId = (String) context.get("billToPartyId");
            String productId = (String) context.get("productId");
            BigDecimal quantity = (BigDecimal) context.get("quantity");
            BigDecimal basePrice = (BigDecimal) context.get("basePrice");
            BigDecimal shippingPrice = (BigDecimal) context.get("shippingPrice");

            if (quantity == null) quantity = ONE_BASE;
            BigDecimal amount = basePrice.multiply(quantity);

            BigDecimal taxTotal = ZERO_BASE;
            BigDecimal taxPercentage = ZERO_BASE;
            BigDecimal priceWithTax = basePrice;
            if (shippingPrice != null) priceWithTax = priceWithTax.add(shippingPrice);

            try {
                GenericValue product = delegator.findByPrimaryKeyCache("Product", UtilMisc.toMap("productId", productId));
                GenericValue productStore = delegator.findByPrimaryKeyCache("ProductStore", UtilMisc.toMap("productStoreId", productStoreId));
                if (productStore == null) {
                    throw new IllegalArgumentException("Could not find ProductStore with ID [" + productStoreId + "] for tax calculation");
                }

                if ("Y".equals(productStore.getString("showPricesWithVatTax"))) {
                    Set taxAuthoritySet = FastSet.newInstance();
                    if (productStore.get("vatTaxAuthPartyId") == null) {
                        List taxAuthorityRawList = delegator.findList("TaxAuthority", EntityCondition.makeCondition("taxAuthGeoId", EntityOperator.EQUALS, productStore.get("vatTaxAuthGeoId")), null, null, null, true);
                        taxAuthoritySet.addAll(taxAuthorityRawList);
                    } else {
                        GenericValue taxAuthority = delegator.findByPrimaryKeyCache("TaxAuthority", UtilMisc.toMap("taxAuthGeoId", productStore.get("vatTaxAuthGeoId"), "taxAuthPartyId", productStore.get("vatTaxAuthPartyId")));
                        taxAuthoritySet.add(taxAuthority);
                    }

                    if (taxAuthoritySet.size() == 0) {
                        throw new IllegalArgumentException("Could not find any Tax Authories for store with ID [" + productStoreId + "] for tax calculation; the store settings may need to be corrected.");
                    }

                    List taxAdustmentList = getTaxAdjustments(delegator, product, productStore, null, billToPartyId, taxAuthoritySet, basePrice, amount, shippingPrice, ZERO_BASE);
                    if (taxAdustmentList.size() == 0) {
                        // this is something that happens every so often for different products and such, so don't blow up on it...
                        Debug.logWarning("Could not find any Tax Authories Rate Rules for store with ID [" + productStoreId + "], productId [" + productId + "], basePrice [" + basePrice + "], amount [" + amount + "], for tax calculation; the store settings may need to be corrected.", module);
                    }

                    // add up amounts from adjustments (amount OR exemptAmount, sourcePercentage)
                    Iterator taxAdustmentIter = taxAdustmentList.iterator();
                    while (taxAdustmentIter.hasNext()) {
                        GenericValue taxAdjustment = (GenericValue) taxAdustmentIter.next();
                        taxPercentage = taxPercentage.add(taxAdjustment.getBigDecimal("sourcePercentage"));
                        BigDecimal adjAmount = taxAdjustment.getBigDecimal("amount");
                        taxTotal = taxTotal.add(adjAmount);
                        priceWithTax = priceWithTax.add(adjAmount.divide(quantity,salestaxCalcDecimals,salestaxRounding));
                        Debug.logInfo("For productId [" + productId + "] added [" + adjAmount.divide(quantity,salestaxCalcDecimals,salestaxRounding) + "] of tax to price for geoId [" + taxAdjustment.getString("taxAuthGeoId") + "], new price is [" + priceWithTax + "]", module);
                    }
                }
            } catch (GenericEntityException e) {
                String errMsg = "Data error getting tax settings: " + e.toString();
                Debug.logError(e, errMsg, module);
                return ServiceUtil.returnError(errMsg);
            }

            // round to 2 decimal places for display/etc
            taxTotal = taxTotal.setScale(salestaxFinalDecimals, salestaxRounding);
            priceWithTax = priceWithTax.setScale(salestaxFinalDecimals, salestaxRounding);

            Map result = ServiceUtil.returnSuccess();
            result.put("taxTotal", taxTotal);
            result.put("taxPercentage", taxPercentage);
            result.put("priceWithTax", priceWithTax);
            return result;
        }
        
        public static Map getSuppliersForProduct(GenericDelegator delegator, Map context) {
            Map results = new HashMap();
            
            GenericValue product = null;
            String productId = (String) context.get("productId");
            String partyId = (String) context.get("partyId");
            String currencyUomId = (String) context.get("currencyUomId");
            Double quantity =(Double) context.get("quantity");
            String canDropShip = (String) context.get("canDropShip");
            try {
                product = delegator.findByPrimaryKeyCache("Product", UtilMisc.toMap("productId", productId));
                if (product == null) {
                    results = ServiceUtil.returnSuccess();
                    results.put("supplierProducts",null);
                    return results;
                }
                List supplierProducts = product.getRelatedCache("SupplierProduct");
                
                // if there were no related SupplierProduct entities and the item is a variant, then get the SupplierProducts of the virtual parent product
                if (supplierProducts.size() == 0 && product.getString("isVariant") != null && product.getString("isVariant").equals("Y")) {
                    String virtualProductId = ProductWorker.getVariantVirtualId(product);
                    GenericValue virtualProduct = delegator.findByPrimaryKeyCache("Product", UtilMisc.toMap("productId", virtualProductId));
                    if (virtualProduct != null) {
                        supplierProducts = virtualProduct.getRelatedCache("SupplierProduct");
                    }
                }
                
                // filter the list down by the partyId if one is provided
                if (partyId != null) {
                    supplierProducts = EntityUtil.filterByAnd(supplierProducts, UtilMisc.toMap("partyId", partyId));
                }
                
                // filter the list down by the currencyUomId if one is provided
                if (currencyUomId != null) {
                    supplierProducts = EntityUtil.filterByAnd(supplierProducts, UtilMisc.toMap("currencyUomId", currencyUomId));
                }
                
                // filter the list down by the minimumOrderQuantity if one is provided
                if (quantity != null) {
                    //minimumOrderQuantity
                    supplierProducts = EntityUtil.filterByCondition(supplierProducts, new EntityExpr("minimumOrderQuantity", EntityOperator.LESS_THAN_EQUAL_TO, quantity));
                }

                // filter the list down by the canDropShip if one is provided
                if (canDropShip != null) {
                    supplierProducts = EntityUtil.filterByAnd(supplierProducts, UtilMisc.toMap("canDropShip", canDropShip));
                }

                // filter the list down again by date before returning it
                supplierProducts = EntityUtil.filterByDate(supplierProducts, UtilDateTime.nowTimestamp(), "availableFromDate", "availableThruDate", true);
                
                //sort resulting list of SupplierProduct entities by price in ASCENDING order
                supplierProducts = EntityUtil.orderBy(supplierProducts, UtilMisc.toList("lastPrice ASC"));
                
                results = ServiceUtil.returnSuccess();
                results.put("supplierProducts", supplierProducts);
            } catch (GenericEntityException ex) {
                Debug.logError(ex, ex.getMessage(), module);
                return ServiceUtil.returnError(ex.getMessage());
            }catch(Exception ex){
                Debug.logError(ex, ex.getMessage(), module);
                return ServiceUtil.returnError(ex.getMessage());
            }
            return results;
        }

        private void initurlconfig(){
        	
        	String path = null;
    		String rootLocation = null;
    		try {
    			rootLocation = ComponentConfig.getComponentConfig("blazeds")
    					.getRootLocation();
    		} catch (ComponentException e) {
    			e.printStackTrace();
    		}
    		if (!rootLocation.endsWith("/"))
    			rootLocation = path + "/";
    		path = rootLocation + "config/config.xml";
    		URL xmlUrl = UtilURL.fromFilename(path);
    		Document ofbizComponentDocument = null;
    		try {
    			ofbizComponentDocument = UtilXml.readXmlDocument(xmlUrl, true);
    		} catch (SAXException e) {
    			e.printStackTrace();
    		} catch (ParserConfigurationException e) {
    			e.printStackTrace();
    		} catch (IOException e) {
    			e.printStackTrace();
    		}
    		Element ofbizComponentElement = ofbizComponentDocument
    				.getDocumentElement();
    		Element delegator_name = UtilXml.firstChildElement(
    				ofbizComponentElement, "delegator-name");
    		Element urlprefix = UtilXml.firstChildElement(
    				ofbizComponentElement, "urlprefix");
    		delegatorName = UtilXml.elementValue(delegator_name);
    		String url = UtilXml.elementValue(urlprefix);
    		if(null!=url)ofbizUrl = url;
        }
}
