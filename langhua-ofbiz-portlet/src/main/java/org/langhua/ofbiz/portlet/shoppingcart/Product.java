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

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @author Shi Yusen, shiys@langhua.cn
 */
public class Product implements Cloneable, Serializable, Comparable<Object> {

	private static final long serialVersionUID = 1L;

	private String m_id;
	private BigDecimal m_listPrice;
	private BigDecimal m_basePrice;
	private BigDecimal m_promoPrice;
	private BigDecimal m_specialPromoPrice;
	private String m_description;
	private String m_imageUri;

	public Product(String id, BigDecimal listPrice, BigDecimal basePrice,
			BigDecimal promoPrice, BigDecimal specialPromoPrice, String description,
			String imageUri) {
		setId(id);
		setListPrice(listPrice);
		setBasePrice(basePrice);
		setPromoPrice(promoPrice);
		setSpecialPromoPrice(specialPromoPrice);
		setDescription(description);
		setImageUri(imageUri);
	}

	public void setId(String id) {
		m_id = id;
	}

	public String getId() {
		return m_id;
	}

	/**
	 * @param listPrice
	 *            the m_listPrice to set
	 */
	public void setListPrice(BigDecimal listPrice) {
		m_listPrice = listPrice;
	}

	/**
	 * @return the m_listPrice
	 */
	public BigDecimal getListPrice() {
		return m_listPrice;
	}

	/**
	 * @param basePrice
	 *            the m_basePrice to set
	 */
	public void setBasePrice(BigDecimal basePrice) {
		m_basePrice = basePrice;
	}

	/**
	 * @return the m_basePrice
	 */
	public BigDecimal getBasePrice() {
		return m_basePrice;
	}

	/**
	 * @param promoPrice
	 *            the m_promoPrice to set
	 */
	public void setPromoPrice(BigDecimal promoPrice) {
		m_promoPrice = promoPrice;
	}

	/**
	 * @return the m_promoPrice
	 */
	public BigDecimal getPromoPrice() {
		return m_promoPrice;
	}

	/**
	 * @param specialPromoPrice
	 *            the m_specialPromoPrice to set
	 */
	public void setSpecialPromoPrice(BigDecimal specialPromoPrice) {
		m_specialPromoPrice = specialPromoPrice;
	}

	/**
	 * @return the m_specialPromoPrice
	 */
	public BigDecimal getSpecialPromoPrice() {
		return m_specialPromoPrice;
	}

	/**
	 * @param description
	 *            the m_description to set
	 */
	public void setDescription(String description) {
		m_description = description;
	}

	/**
	 * @return the m_description
	 */
	public String getDescription() {
		return m_description;
	}

	/**
	 * @param imageUri
	 *            the m_imageUri to set
	 */
	public void setImageUri(String imageUri) {
		m_imageUri = imageUri;
	}

	/**
	 * @return the m_imageUri
	 */
	public String getImageUri() {
		return m_imageUri;
	}

	public Object clone() {
		return new Product(m_id, m_listPrice, m_basePrice, m_promoPrice,
				m_specialPromoPrice, m_description, m_imageUri);
	}

	public int compareTo(Object arg0) {
		if (arg0 instanceof Product) {
			return ((Product) arg0).getId().compareTo(getId());
		}
		return 0;
	}

	public BigDecimal getPrice() {
		return getSpecialPromoPrice() == null ? (getPromoPrice() == null ? (getBasePrice() == null ? getListPrice()
				: getBasePrice())
				: getPromoPrice())
				: getSpecialPromoPrice();
	}
}
