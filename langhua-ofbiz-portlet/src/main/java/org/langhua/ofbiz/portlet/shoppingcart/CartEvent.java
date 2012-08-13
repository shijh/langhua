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

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import java.io.Serializable;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision: 9912 $
 * 
 * This is copied from org/jboss/portal/portlet/samples/shoppingcart/CartEvent.java
 * The QNAME is modified.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
@XmlRootElement
public class CartEvent implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;

	public static final QName QNAME = new QName(
			"urn:ofbiz:portlet:shoppingcart:event", "CartEvent");

	public CartEvent(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}
}
