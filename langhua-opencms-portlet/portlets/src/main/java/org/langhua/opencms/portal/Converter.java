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
package org.langhua.opencms.portal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.w3c.tidy.Tidy;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author An Tao, antao@langhua.cn
 */
public class Converter {
	
	public static final String XMLTITLE = "<?xml version='1.0'?><!DOCTYPE html [ <!ELEMENT html (#PCDATA)><!ENTITY nbsp '&#160;'><!ENTITY span '&#111;'><!ENTITY aring '&#229;'><!ENTITY auml '&#228;'><!ENTITY ouml '&#246;'><!ENTITY Aring '&#197;'><!ENTITY Auml '&#196;'><!ENTITY Ouml '&#214;'>]>";
	private String m_encoding;
	private String[] m_replacePatterns = { "&#160;", "(\\r\\n){2,}",
			"(\\n){2,}", "\\(\\r\\n<", "\\(\\n<", "\\(\\r\\n(\\ ){1,}<",
			"\\(\\n(\\ ){1,}<" };
	private Pattern[] m_replaceStyle;
	private String[] m_replaceValues = { "&nbsp;", "", "&ndash;", "", "(<",
			"(<", "(<"};
	private Tidy m_tidy;
	private String _page = "";
	private String _parameterName = "pageurl";
	private Log log = LogFactory.getLog(Converter.class);

	public Converter(String encoding) {
		init(encoding);
	}

	public String htmlConverter(String in, String[] pageOrParameterName) {
		String inputString = "";
		String xmlTitle = "<?xml version='1.0'?><!DOCTYPE html [ <!ELEMENT html (#PCDATA)><!ENTITY nbsp '&#160;'><!ENTITY span '&#111;'><!ENTITY aring '&#229;'><!ENTITY auml '&#228;'><!ENTITY ouml '&#246;'><!ENTITY Aring '&#197;'><!ENTITY Auml '&#196;'><!ENTITY Ouml '&#214;'>]>";
		Document doc;
		String con = "";
		if (pageOrParameterName != null) {
			_page = pageOrParameterName[0];
			if (pageOrParameterName.length > 1) {
				_parameterName = pageOrParameterName[1];
			}
		}
		try {
			in = getHtmlString(in);
			con = convertToString(in);
			con = getHtmlString(con);
			doc = DocumentHelper.parseText(xmlTitle + con);
			Element element = doc.getRootElement();
			getElementList(element);
			Element scriptElement = element.element("head");
			Element bodyElement = element.element("body");
			inputString = this.getScriptList(scriptElement);
			String bodystring = bodyElement.asXML();
			inputString += bodystring.substring(bodystring.indexOf(">") + 1,
					bodystring.indexOf("</body>"));
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (DocumentException e) {
			e.printStackTrace();
		}
		return inputString;
	}

	protected String getHtmlString(String html) {
		String typestring = "";
		String htmlString = html;
		try {
			typestring = htmlString.substring(2, 9);
			if (typestring.equals("DOCTYPE")) {
				log.debug(typestring);
				htmlString = htmlString.substring(htmlString.indexOf("<html"));
			}
		} catch (Exception e) {
			// do nothing
		}
		return htmlString;
	}

	protected void getElementList(Element element) {
		if (element.getName().equals("a")) {
			if (element.attributeValue("href") != null
					&& !element.attributeValue("href").equals("")
					&& !element.attributeValue("href").equals(
							"javascript:void(null);")) {
				String fhome = "/portal/portal/default/" + _page + "?"
						+ _parameterName + "=";
				try {
					fhome = fhome
							+ java.net.URLEncoder.encode(element
									.attributeValue("href"), "UTF-8");
					log.debug(fhome);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				element.attribute("href").setValue(fhome);
			}
		}
		List<Element> elements = element.elements();
		if (elements.size() != 0) {
			for (Iterator<Element> it = elements.iterator(); it.hasNext();) {
				Element elem = (Element) it.next();
				getElementList(elem);
			}
		}
	}

	protected String getScriptList(Element element) {
		String scriptList = "";
		List<Element> elements = element.elements();
		if (elements.size() != 0) {
			for (Iterator<Element> it = elements.iterator(); it.hasNext();) {
				Element elem = (Element) it.next();
				if (!elem.getName().equals("script")) {
					element.remove(elem);
				}
			}
			scriptList = element.asXML().substring(
					element.asXML().indexOf(">")+ 1,
					element.asXML().indexOf("</head>"));
		}
		log.debug(scriptList);
		return scriptList;
	}

	protected String convertToString(String htmlInput)
			throws UnsupportedEncodingException {
		String workHtml = regExp(htmlInput);
		workHtml = parse(workHtml, m_encoding);
		workHtml = regExp(workHtml);
		htmlInput = workHtml;
		return htmlInput;
	}

	protected String regExp(String htmlInput) {
		String parsedHtml = htmlInput.trim();
		for (int i = 0; i < m_replacePatterns.length; i++) {
			parsedHtml = m_replaceStyle[i].matcher(parsedHtml).replaceAll(
					m_replaceValues[i]);
		}
		return parsedHtml;
	}

	protected String parse(String htmlInput, String encoding)
			throws UnsupportedEncodingException {
		ByteArrayInputStream in = new ByteArrayInputStream(htmlInput
				.getBytes(encoding));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		m_tidy.parse(in, out);
		byte[] result = out.toByteArray();
		return new String(result, encoding);
	}

	protected void init(String encoding) {
		m_encoding = encoding;
		m_tidy = new Tidy();
		m_tidy.setXHTML(true);
		m_tidy.setWord2000(false);
		Properties additionalTags = new Properties();
		additionalTags.put("new-empty-tags", "o:smarttagtype");
		additionalTags.put("new-inline-tags", "o:smarttagtype");
		m_tidy.getConfiguration().addProps(additionalTags);
		m_tidy.setInputEncoding(encoding);
		m_tidy.setOutputEncoding(encoding);
		m_tidy.setTidyMark(false);
		m_tidy.setMakeClean(false);
		m_tidy.setNumEntities(true);
		m_tidy.setPrintBodyOnly(false);
		m_tidy.setForceOutput(true);
		m_tidy.setQuiet(true);
		m_tidy.setShowWarnings(false);
		m_tidy.setHideComments(false);
		m_tidy.setBreakBeforeBR(false);
		m_tidy.setWrapAttVals(false);
		m_tidy.setWraplen(100);
		m_tidy.setSpaces(0);
		m_replaceStyle = new Pattern[m_replacePatterns.length];
		for (int i = 0; i < m_replacePatterns.length; i++) {
			m_replaceStyle[i] = Pattern.compile(m_replacePatterns[i]);
		}
	}
}
