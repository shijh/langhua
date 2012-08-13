/*
 * This library is part of OFBiz-Portlet Component of Langhua
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
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-portlet/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on JBoss Portal, please see the
 * project website: http://jboss.org/jbossportal/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.portlet.shoppingcart;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javolution.util.FastMap;

import org.langhua.ofbiz.rmi.client.OFBizRmiClient;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.GenericEntity;
import org.ofbiz.service.ServiceUtil;

/**
 * @author Shi Yusen, shiys@langhua.cn
 */
public class Category {
	
	private Map<String, Product> m_items = FastMap.newInstance();
	
	public Category(OFBizRmiClient rc, String categoryId) {
		
		Map<String, ? extends Object> result = FastMap.newInstance();
		Map<String, Object> context = FastMap.newInstance();
		context.put("categoryId", categoryId);
		result = rc.service("getProductCategoryMembers", context);
		if (!ServiceUtil.isError(result)) {
			List<? extends Object> products = (List<? extends Object>) result.get("categoryMembers");
			Iterator<? extends Object> i = products.iterator();
			while (i.hasNext()) {
				GenericEntity productEntity = (GenericEntity) i.next();
				Map<String, Object> fields = productEntity.getAllFields();
				String productId = (String) fields.get("productId");

				Map<String, ? extends Object> productResult = rc.service("getProduct", UtilMisc.toMap(
						"productId", productId));
				GenericEntity productValue = null;
				if (productResult != null) {
					productValue = (GenericEntity) productResult.get("product");
					if (productValue != null) {
						fields = productValue.getAllFields();
						Product product = new Product(productId, null, null, null, null, (String) fields.get("description"), (String) fields.get("smallImageUrl"));
						Map<String, ? extends Object> priceResult = rc.service("calculateProductPrice",
								UtilMisc.toMap("product", productValue));
						Object listPrice = priceResult.get("listPrice");
						if (listPrice != null)
							product.setListPrice((BigDecimal) listPrice);
						Object basePrice = priceResult.get("basePrice");
						if (basePrice != null)
							product.setBasePrice((BigDecimal) basePrice);
						Object promoPrice = priceResult.get("promoPrice");
						if (promoPrice != null)
							product.setPromoPrice((BigDecimal) promoPrice);
						Object specialPromoPrice = priceResult.get("specialPromoPrice");
						if (specialPromoPrice != null)
							product.setSpecialPromoPrice((BigDecimal) specialPromoPrice);
						m_items.put(productId, product);
					}
				}
			}
		}
	}

	public Product get(String id) {
		return m_items.get(id);
	}

	public Collection<Product> getAll() {
		return m_items.values();
	}
}
