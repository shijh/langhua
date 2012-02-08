/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.misc;

import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.jsp.tagext.TagSupport;

import org.apache.commons.logging.Log;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.main.CmsLog;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;

public class CmsWeatherDescription extends TagSupport  {	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(CmsWeatherDescription.class);
	
	private static final String LINKFILE = "/system/modules/org.langhua.opencms.weather/resources/weather";

	private CmsXmlContent cmsXmlContent;
	
	public static Map<String, String> mapDesc;
	
	public CmsWeatherDescription(CmsObject cmsObject){
		
		init(cmsObject);
		
	}
	
	public void init(CmsObject cmsObject) {
		
		mapDesc = new Hashtable<String, String>();
		
		try {
			CmsFile cmsFile = cmsObject.readFile(LINKFILE);
			cmsXmlContent = CmsXmlContentFactory.unmarshal(cmsObject, cmsFile);
			
			Document document = DocumentHelper.parseText(cmsXmlContent.toString());
			Element rootElement = document.getRootElement();		
			List list = rootElement.element("Weather").elements(
					"WeatherColumns");			
			Iterator iterator = list.iterator();
			while (iterator.hasNext()) {
				Element doc = (Element) iterator.next();
				String chineseName = doc.selectSingleNode("name").getStringValue();
				String englishName = doc.selectSingleNode("value").getStringValue();
				
				mapDesc.put(englishName, chineseName);			
			}
		
		} catch (Exception e) {
			if (LOG.isErrorEnabled()) {
				LOG.debug(e);
			}
		}
		
	}
}
