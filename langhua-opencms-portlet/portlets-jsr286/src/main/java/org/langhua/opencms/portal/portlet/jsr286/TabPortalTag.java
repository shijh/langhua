/*
 * This library is part of OpenCms-Portlet module
 *
 * Copyright (C) 2008 Beijing Langhua Ltd. (http://www.langhua.cn)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
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
 * project website: http://www.langhua.cn/langhua/modules/portlet/
 * Username: anon
 * Password: anon
 *  
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.portal.portlet.jsr286;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletSecurityException;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.UnavailableException;
import org.jboss.portlet.JBossActionRequest;
import org.jboss.portlet.JBossActionResponse;
import org.jboss.portlet.JBossPortlet;
import org.jboss.portlet.JBossRenderRequest;
import org.jboss.portlet.JBossRenderResponse;
import org.langhua.opencms.portal.portlet.jsr286.Converters.*;
import org.langhua.opencms.portal.portlet.jsr286.VO.TabBean;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * @author An Tao, antao@langhua.cn
 */
public class TabPortalTag extends JBossPortlet {

	private String[] m_url;
	private String m_helpurl;
	private String[] m_title;
	private String m_locale;
	private String m_timeout;
	private String nameSpace = "";
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final String[] PARAM_NO_VALUES = { "(no value)" };
	public static final int DEFAULT_TIMEOUT = 5000;
	private PortletURL m_portleturl;
	private PortletURLConverter converter = new PortletURLConverter("UTF-8");
	private HashMap<String, String> m_query;
	private List<String> m_publicRenderParameterNames;
	private String m_pageName;
	private Boolean m_displayTitle = false;
	public String portletTabQueryName = "PortletTabQuery";
	public String portletURLRender;
	public String portletTabQuery;
	private String m_titleString;
	private PortletTabURLConverter tabConverter = new PortletTabURLConverter(
			"UTF-8");
	private List<TabBean> tabs=null;

	@Override
	public void render(JBossRenderRequest request, JBossRenderResponse response)
			throws PortletException, PortletSecurityException, IOException {
		tabs = new ArrayList<TabBean>();
		response.setTitle(getTitle(request));
		m_titleString = "";
		m_publicRenderParameterNames = new ArrayList<String>();
		PortletPreferences preferences = request.getPreferences();
		Enumeration names = preferences.getNames();
		portletTabQuery = "default";
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if ("url".equals(name)) {
				m_url = preferences.getValues(name, PARAM_NO_VALUES);
			} else if ("timeout".equals(name)) {
				m_timeout = preferences.getValue(name,
						String.valueOf(DEFAULT_TIMEOUT)).toString().trim();
			} else if ("iframe".equals(name)) {
				String iframe = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
				String[] pageOrParametername = iframe.split("\\|");
				m_pageName = pageOrParametername[0];
			} else if ("portletURL".equals(name)) {
				portletURLRender = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("page".equals(name)) {
				m_pageName = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("displayTitle".equals(name)) {
				m_displayTitle = Boolean.valueOf(preferences.getValue(name,
						PARAM_NO_VALUE));
			}
		}
		if (portletURLRender != null && portletURLRender.equals("action")) {
			m_portleturl = response.createActionURL();
		} else {
			m_portleturl = response.createRenderURL();
		}
		m_helpurl = "";
		m_query = new HashMap<String, String>();
		Enumeration publicRenderParameterNames = this.getPortletConfig()
				.getPublicRenderParameterNames();
		while (publicRenderParameterNames.hasMoreElements()) {
			String name = (String) publicRenderParameterNames.nextElement();
			m_publicRenderParameterNames.add(name);
			String query = request.getParameter(name);
			if (name.equals(portletTabQueryName) && query != null) {
				portletTabQuery = query;
			} else if (query != null && !name.equals(portletTabQueryName)) {
				m_query.put(name, query);
			}
		}
		m_locale = request.getLocale().getLanguage();
		doDispatch(request, response);
	}

	@Override
	protected void doView(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		nameSpace = response.getNamespace();
		String titles = "";
		titles = this.getBundleString("portlet.tab.name", request.getLocale());
		if (titles != null && !titles.equals("")) {
			m_title = titles.split("\\|");
		} else {
			for (int i = 0; i < m_url.length; i++) {
				m_title[i] = "No Name";
			}
		}
		String htmlbody = this.buildHtml();
		if (m_titleString != null && !m_titleString.equals("")
				&& m_displayTitle) {
			response.setTitle(m_titleString);
		}
		response.getWriter().println(htmlbody);
	}

	protected void doHelp(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		PortletPreferences preferences = request.getPreferences();
		try {
			m_helpurl = preferences.getValue("helpurl", PARAM_NO_VALUE)
					.toString().trim();
		} catch (Exception e) {
			// do nothing
		}
		String htmlbody = this.buildHtml();
		if (m_titleString != null && !m_titleString.equals("")) {
			response.setTitle(m_titleString);
		}
		response.getWriter().println(htmlbody);
	}

	@Override
	protected void doEdit(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("Hello World!");
	}

	public String buildHtml() throws IOException {
		StringBuffer inputScript = new StringBuffer(512);
		String inputLine = "";
		String inputString = "";
		if (m_url == null) {
			return inputLine.toString();
		} else if (m_url.length == 1) {
			if (m_url[0].indexOf("?") > 0) {
				m_url[0] += "&__locale=" + m_locale;
			} else {
				m_url[0] += "?__locale=" + m_locale;
			}
			Iterator iter = m_query.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = (String) entry.getKey();
				String val = (String) entry.getValue();
				if (m_url[0].indexOf("?") > 0) {
					m_url[0] += "&" + key + "=" + val;
				} else {
					m_url[0] += "?" + key + "=" + val;
				}
			}
			String in = getHtmlContent(m_url[0], DEFAULT_TIMEOUT);
			m_titleString = tabConverter.getTitleString(in);
			tabs = tabConverter.getPortletTabURL(in);
		} else if (m_url.length > 1) {
			for (int i = 0; i < m_url.length; i++) {
				TabBean tab = new TabBean();
				tab.setTabName(m_title[i]);
				tab.setTabURL(m_url[i]);
				tabs.add(tab);
			}
		}
		for (int i = 0; i < tabs.size(); i++) {
			TabBean tab = tabs.get(i);
			String tabURL = tab.getTabURL();
			if (tabURL.indexOf("?") > 0) {
				tabURL += "&__locale=" + m_locale;
			} else {
				tabURL += "?__locale=" + m_locale;
			}
			Iterator iter = m_query.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry entry = (Map.Entry) iter.next();
				String key = (String) entry.getKey();
				String val = (String) entry.getValue();
				if (tabURL.indexOf("?") > 0) {
					tabURL += "&" + key + "=" + val;
				} else {
					tabURL += "?" + key + "=" + val;
				}
			}
			tab.setTabURL(tabURL);
		}
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt(m_timeout);
		} catch (Exception e) {
			// do nothing
		}
		try {
			if (m_helpurl == null || m_helpurl.equals("")) {
				inputScript
						.append("<script language=\"JavaScript\" type=\"text/javascript\">\n");
				inputScript.append("//<![CDATA[\n");
				inputScript.append("var " + nameSpace + "_oldTabnum=\"0\";\n");
				inputScript.append("var " + nameSpace + "_oldIdt=\"\";\n");
				inputScript.append("function " + nameSpace
						+ "_Show_Tab(Tabnum){\n");
				inputScript
						.append("if(" + nameSpace + "_oldTabnum==Tabnum){\n");
				inputScript.append("return;\n");
				inputScript.append("}\n");
				for (int i = 0; i <tabs.size(); i++) {
					inputScript.append("document.getElementById(\"" + nameSpace
							+ "_Tabcontent_" + i
							+ "\").style.display=\"none\";\n");
				}
				inputScript.append("document.getElementById(\"" + nameSpace
						+ "_Tabcontent_\"+Tabnum).style.display=\"block\";\n");
				inputScript.append("document.getElementById(\"" + nameSpace
						+ "_l\"+Tabnum).className=\"currentTab\";\n");
				inputScript.append("if(" + nameSpace + "_oldIdt!=\"\"){");
				inputScript.append("document.getElementById(" + nameSpace
						+ "_oldIdt).className=\"NoCSS\";\n");
				inputScript.append("}else{\n");
				inputScript.append("document.getElementById(\"" + nameSpace
						+ "_l0\").className=\"NoCSS\";\n");
				inputScript.append("}\n");
				inputScript.append("" + nameSpace + "_oldIdt=\"" + nameSpace
						+ "_l\"+Tabnum;\n");
				inputScript.append("" + nameSpace + "_oldTabnum=Tabnum;\n");
				inputScript.append("}\n");
				inputScript.append("//]]>\n");
				inputScript.append("</script>");
				inputScript
						.append("<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
				inputScript.append("<tr>\n");
				inputScript.append("<td>\n");
				inputScript.append("<div class=\"tab-ui\">\n");
				inputScript.append("<ul class=\"topnav\">\n");
				for (int i = 0; i < tabs.size(); i++) {
					TabBean tab = tabs.get(i);
					if (i == 0) {
						inputScript
								.append("<li id=\""
										+ nameSpace
										+ "_l"
										+ i
										+ "\" class=\"currentTab\"><a href=\"javascript:void(null);\"  onclick=\""
										+ nameSpace + "_Show_Tab(" + i + ")\">"
										+ tab.getTabName() + "</a></li>\n");
					} else {
						inputScript
								.append("<li id=\""
										+ nameSpace
										+ "_l"
										+ i
										+ "\" class=\"NoCSS\"><a href=\"javascript:void(null);\"  onclick=\""
										+ nameSpace + "_Show_Tab(" + i + ")\">"
										+ tab.getTabName() + "</a></li>\n");
					}
				}
				inputScript.append("</ul>\n");
				inputScript.append("</div>");
				inputScript.append("</td>\n");
				inputScript.append("</tr>\n");
				inputScript.append("</table>\n");
				for (int i = 0; i < tabs.size(); i++) {
					TabBean tab = tabs.get(i);
					if (i == 0) {
						inputLine += "<div id=\"" + nameSpace + "_Tabcontent_"
								+ i + "\">\n";
					} else {
						inputLine += ("<div id=\"" + nameSpace + "_Tabcontent_"
								+ i + "\" style=\"display:none;\">\n");
					}
					if (tab.getTabURL() != null) {
						inputLine += getHtmlContent(tab.getTabURL(), timeout);
					}
					inputLine+="</div>\n";
				}
			} else {
				inputLine = getHtmlContent(m_helpurl, timeout);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (m_pageName != null && !m_pageName.equals(PARAM_NO_VALUE)
				&& !inputLine.equals("")) {
			inputLine = converter.htmlConverter(inputLine, m_pageName,
					m_publicRenderParameterNames, m_portleturl);
		}
		if (m_pageName == null && portletURLRender != null
				&& !inputLine.equals("")) {
			inputLine = converter.htmlPortletURLConverter(inputLine,
					m_portleturl, m_publicRenderParameterNames);
		}
		if (m_helpurl == null || m_helpurl.equals("")) {
			inputString = inputScript + inputLine;
		} else {
			inputString = inputLine;
		}
		return inputString;
	}

	@Override
	protected String getTitle(RenderRequest request) {
		ResourceBundle bundle = getResourceBundle(request.getLocale());
		String titleString = bundle.getString("javax.portlet.title");
		try {
			titleString = new String(titleString.getBytes("ISO8859-1"), "UTF-8");
		} catch (Exception e) {
			// do nothing
		}
		return titleString;
	}

	@Override
	public void destroy() {
		m_helpurl = null;
	}

	protected void processAction(JBossActionRequest req,
			JBossActionResponse resp) throws PortletException,
			PortletSecurityException, IOException {
		Enumeration publicRenderParameterNames = this.getPortletConfig()
				.getPublicRenderParameterNames();
		while (publicRenderParameterNames.hasMoreElements()) {
			String name = (String) publicRenderParameterNames.nextElement();
			String value = req.getParameter(name);
			if (value != null) {
				resp.setRenderParameter(name, java.net.URLEncoder.encode(value,
						"UTF-8"));
			}
		}
	}

	public String getHtmlContent(String url, int timeout) throws IOException {
		String inputLine = "";
		URL myurl = new URL(url);
		URLConnection myurlcon = myurl.openConnection();
		myurlcon.setConnectTimeout(timeout);
		InputStreamReader isr = new InputStreamReader(
				myurlcon.getInputStream(), "UTF-8");
		BufferedReader in = new BufferedReader(isr);
		String s = in.readLine();
		while (s != null) {
			if (m_pageName != null || m_publicRenderParameterNames.size() > 0) {
				inputLine += s;
			} else {
				inputLine += s + "\n";
			}
			s = in.readLine();
		}
		in.close();
		return inputLine;
	}

	protected String getBundleString(String keyString, Locale locale) {
		ResourceBundle bundle = getResourceBundle(locale);
		String bundleString = "";
		try {
			bundleString = bundle.getString(keyString);
			bundleString = new String(bundleString.getBytes("ISO8859-1"),
					"UTF-8");
		} catch (Exception e) {
			// do nothing
		}
		return bundleString;
	}
}