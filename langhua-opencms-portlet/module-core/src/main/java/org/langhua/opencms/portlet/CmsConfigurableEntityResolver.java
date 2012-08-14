/*
 * This library is part of OpenCms-Portlet module
 *
 * Copyright (C) 2009 Langhua Opensource Foundation (http://langhua.org)
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
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 *
 * For further information about OpenCms-Portlet, please see the 
 * project website: http://langhua.org/opencms/
 *  
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.portlet;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsResource;
import org.opencms.main.CmsException;
import org.opencms.util.CmsStringUtil;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Provides methods for a configurable EntityResolver.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsConfigurableEntityResolver implements EntityResolver {

	public static final String PROPERTY_DTD = "dtd";
	
	public static final String PROPERTY_XSD = "xsd";
	
	private HashMap<String, InputSource> m_resolverMap;
	
	public CmsConfigurableEntityResolver(CmsObject cms, CmsResource resource) {
		List<String> values;
		m_resolverMap = new HashMap<String, InputSource>();
		try {
			CmsProperty propertyDTD = cms.readPropertyObject(resource, PROPERTY_DTD, false);
			CmsProperty propertyXSD = cms.readPropertyObject(resource, PROPERTY_XSD, false);
			String dtd = propertyDTD.getResourceValue();
			String xsd = null;
			if (CmsStringUtil.isEmptyOrWhitespaceOnly(dtd)) {
				dtd = propertyDTD.getStructureValue();
			}
			if (CmsStringUtil.isEmptyOrWhitespaceOnly(dtd)) {
				xsd = propertyXSD.getResourceValue();
				if (CmsStringUtil.isEmptyOrWhitespaceOnly(xsd)) {
					xsd = propertyXSD.getStructureValue();
				}
			}
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(dtd)) {
				values = CmsStringUtil.splitAsList(dtd, CmsProperty.VALUE_LIST_DELIMITER);
			} else {
				values = CmsStringUtil.splitAsList(xsd, CmsProperty.VALUE_LIST_DELIMITER);
			}
		} catch (CmsException e) {
			values = new ArrayList<String>();
		}
		for (int i=0; i<values.size()/2; i++) {
			try {
				CmsFile file = cms.readFile(values.get(2*i+1));
				m_resolverMap.put(values.get(2*i), new InputSource(new ByteArrayInputStream(file.getContents())));
			} catch (CmsException e) {
				// do nothing
			}
		}
	}
	
	public InputSource resolveEntity(String publicId, String systemId)
			throws SAXException, IOException {
		return m_resolverMap.get(systemId);
	}

}
