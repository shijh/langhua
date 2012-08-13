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

import javax.portlet.Event;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;
import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.PortletSession;

import org.langhua.ofbiz.rmi.client.OFBizRmiClient;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision: 9932 $
 * 
 * Change the processEvent method to get products through OFBiz RMI client
 * @author Shi Yusen, shiys@langhua.cn
 */
public class CartPortlet extends GenericPortlet {
	public static final String ITEMS = "cart_items";

	protected void doView(RenderRequest renderRequest,
			RenderResponse renderResponse) throws PortletException, IOException {
		renderResponse.setContentType("text/html");
		Writer writer = renderResponse.getWriter();
		Locale locale = renderRequest.getLocale();

		List<Product> items = getItems(renderRequest.getPortletSession());

		if (!items.isEmpty()) {
			writer
					.append("<table width='95%' id=\"cart-table\"><thead><tr align='left'><th width='66%'>" + Messages.getString(locale, Messages.PRODUCT_ID) + "</th><th align='right'>" + Messages.getString(locale, Messages.PRODUCT_PRICE) + "</th></tr></thead><tbody>");
			double total = 0.0;
			for (Product item : items) {
				double price = item.getPrice().doubleValue();
				total += price;
				writer.append("<tr><td>").append(item.getId()).append(
						"</td><td align='right'>$").append("" + price).append(
						"</td></tr>");
			}
			writer
					.append(
							"<tr><td><b>" + Messages.getString(locale, Messages.CART_TOTAL) + "</b></td><td align='right' style='border-top: 2px solid #000;'>")
					.append("$" + total).append("</td></tr></tbody></table>");
		} else {
			writer.append(Messages.getString(locale, Messages.CART_EMPTY));
		}
	}

	public void processEvent(EventRequest eventRequest,
			EventResponse eventResponse) throws PortletException, IOException {
		List<Product> items = getItems(eventRequest.getPortletSession());

		Event event = eventRequest.getEvent();
		if (event.getName().equals("CartEvent")) {
			CartEvent cartEvent = (CartEvent) event.getValue();
			PortletPreferences preferences = eventRequest.getPreferences();
			String ofbizrmi = preferences.getValue("ofbizrmi",
					"rmi://localhost:1099/RMIDispatcher");
			String categoryId = preferences.getValue("categoryId", "PROMOTIONS");
			OFBizRmiClient rc = new OFBizRmiClient(ofbizrmi);
			if (rc == null) return;
			Category category = new Category(rc, categoryId);
			items.add(category.get(cartEvent.getId()));
		}

		eventRequest.getPortletSession().setAttribute(ITEMS, items);
	}

	public void render(RenderRequest renderRequest,	
			RenderResponse renderResponse) throws PortletException, IOException {
		renderResponse.setTitle(Messages.getString(renderRequest.getLocale(), Messages.CART_TITLE));
		doDispatch(renderRequest, renderResponse);
	}

	private List<Product> getItems(PortletSession session) {
		List<Product> items = (List<Product>) session
				.getAttribute(ITEMS);
		if (items == null) {
			items = new ArrayList<Product>();
		}
		return items;
	}

}
