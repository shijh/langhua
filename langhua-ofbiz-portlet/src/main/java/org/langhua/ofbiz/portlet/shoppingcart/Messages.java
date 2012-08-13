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

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Resource bundle.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class Messages {

	private static final String m_resource = "messages";
	
	public static final String PRODUCT_ID = "PRODUCT_ID";
	
	public static final String PRODUCT_IMAGE = "PRODUCT_IMAGE";
	
	public static final String PRODUCT_DESCRIPTION = "PRODUCT_DESCRIPTION";
	
	public static final String PRODUCT_PRICE = "PRODUCT_PRICE";
	
	public static final String PRODUCT_ACTIONS = "PRODUCT_ACTIONS";
	
	public static final String ERROR_CANNOT_CONNECT = "ERROR_CANNOT_CONNECT";
	
	public static final String ADD_TO_CART = "ADD_TO_CART";
	
	public static final String CART_TOTAL = "CART_TOTAL";
	
	public static final String CART_EMPTY = "CART_EMPTY";

	public static final String CATALOG_TITLE = "CATALOG_TITLE";

	public static final String CART_TITLE = "CART_TITLE";

	public static String getString(Locale locale, String name) {
		try {
			ResourceBundle rb = ResourceBundle.getBundle(
					m_resource, locale, Thread.currentThread()
							.getContextClassLoader());
			name = new String(rb.getString(name).getBytes("ISO-8859-1"), "UTF-8");
		} catch (MissingResourceException e) {
		} catch (UnsupportedEncodingException e) {
		}
		return name;
	}
}
