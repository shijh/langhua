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

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.langhua.ofbiz.rmi.client.OFBizRmiClient;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Locale;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision: 9932 $
 * 
 *          Change the doView method to get products through OFBiz RMI Client.
 * @author Shi Yusen, shiys@langhua.cn
 */
public class CatalogPortlet extends GenericPortlet {

	protected void doView(RenderRequest renderRequest,
			RenderResponse renderResponse) throws PortletException, IOException {
		renderResponse.setContentType("text/html");
		Writer writer = renderResponse.getWriter();
		Locale locale = renderRequest.getLocale();

		writer
				.append("<table width='95%'><thead><tr align='left'><th width='15%'>" + Messages.getString(locale, Messages.PRODUCT_ID) + "</th><th width='30'>" + Messages.getString(locale, Messages.PRODUCT_IMAGE) + "</th><th width='30%'>" + Messages.getString(locale, Messages.PRODUCT_DESCRIPTION) + "</th><th width='10%'>" + Messages.getString(locale, Messages.PRODUCT_PRICE) + "</th><th>" + Messages.getString(locale, Messages.PRODUCT_ACTIONS) + "</th></tr></thead><tbody>");

		PortletURL addURL = renderResponse.createActionURL();
		addURL.setParameter("op", "add");

		PortletPreferences preferences = renderRequest.getPreferences();
		String ofbizrmi = preferences.getValue("ofbizrmi",
				"rmi://localhost:1099/RMIDispatcher");
		String ofbizurl = preferences.getValue("ofbizurl",
				"http://localhost:8080");
		String categoryId = preferences.getValue("categoryId", "PROMOTIONS");
		OFBizRmiClient rc = new OFBizRmiClient(ofbizrmi);
		if (rc == null) {
			writer.append(Messages.getString(locale, Messages.ERROR_CANNOT_CONNECT) + ofbizrmi);
			writer.append("</tbody></table>");
			return;
		}
		Category category = new Category(rc, categoryId);
		Collection<Product> items = category.getAll();
		for (Product item : items) {
			addURL.setParameter("id", item.getId());
			writer
					.append("<tr><td>")
					.append(item.getId())
					.append("</td><td>")
					.append(
							"<img src=\"" + ofbizurl + item.getImageUri()
									+ "\"")
					.append("</td><td>")
					.append(
							item.getDescription() == null ? "" : item
									.getDescription())
					.append("</td><td>$")
					.append(String.valueOf(item.getPrice()))
					.append("</td><td><a href='")
					.append(addURL.toString())
					.append(
							"' onclick=\"new Effect.Highlight(document.getElementById('cart-table'))\">" + Messages.getString(locale, Messages.ADD_TO_CART) + "</a></td></tr>");
		}

		writer.append("</tbody></table>");
	}

	public void processAction(ActionRequest actionRequest,
			ActionResponse actionResponse) throws PortletException, IOException {
		String value = actionRequest.getParameter("id");
		actionResponse.setEvent(CartEvent.QNAME, new CartEvent(value));
	}
	
	public void render(RenderRequest renderRequest,	
			RenderResponse renderResponse) throws PortletException, IOException {
		renderResponse.setTitle(Messages.getString(renderRequest.getLocale(), Messages.CATALOG_TITLE));
		doDispatch(renderRequest, renderResponse);
	}

}
