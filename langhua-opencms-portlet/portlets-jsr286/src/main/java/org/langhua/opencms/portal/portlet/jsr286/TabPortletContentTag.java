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
import java.util.List;
import java.util.ResourceBundle;
import javax.portlet.PortletException;
import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletSecurityException;
import javax.portlet.PortletSession;
import javax.portlet.PortletURL;
import javax.portlet.RenderRequest;
import javax.portlet.UnavailableException;
import javax.portlet.WindowState;
import org.jboss.portlet.JBossActionRequest;
import org.jboss.portlet.JBossActionResponse;
import org.jboss.portlet.JBossPortlet;
import org.jboss.portlet.JBossRenderRequest;
import org.jboss.portlet.JBossRenderResponse;
import org.langhua.opencms.portal.portlet.jsr286.Converters.DisplayConverter;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author An Tao, antao@langhua.cn
 */
public class TabPortletContentTag extends JBossPortlet {

	private String[] m_url;
	private String[] m_title;
	private String m_locale;
	private String m_timeout;
	private String nameSpace = "";
	private String[] pageOrParametername = null;
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final String[] PARAM_NO_VALUES = { "(no value)" };
	public static final int DEFAULT_TIMEOUT = 5000;
	private String m_pageurl;
	private DisplayConverter converter = new DisplayConverter("UTF-8");
	private static final PortletMode CONTENT = new PortletMode("content");
	private static final PortletMode ADMIN = new PortletMode("admin");
	private List<String> m_publicRenderParameterNames;
	private String m_pageName;
	private PortletURL m_portleturl;
	public String portletURLRender;

	@Override
	public void render(JBossRenderRequest request, JBossRenderResponse response)
			throws PortletException, PortletSecurityException, IOException {
		response.setTitle(getTitle(request));
		m_publicRenderParameterNames = new ArrayList<String>();
		PortletPreferences preferences = request.getPreferences();
		Enumeration names = preferences.getNames();
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
			} else if ("page".equals(name)) {
				m_pageName = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("portletURL".equals(name)) {
				portletURLRender = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			}
		}
		if (portletURLRender != null && portletURLRender.equals("action")) {
			m_portleturl = response.createActionURL();
		} else {
			m_portleturl = response.createRenderURL();
		}
		String titles = "No Name";
		ResourceBundle bundle = getResourceBundle(request.getLocale());
		try {
			titles = bundle.getString("portlet.tab.name");
			titles = new String(titles.getBytes("ISO8859-1"), "UTF-8");
		} catch (Exception e) {
			// do nothing
		}
		m_title = titles.split("\\|");
		m_locale = request.getLocale().getLanguage();
		if (m_url == null) {
			doError(request, response);
		} else {
			for (int i = 0; i < m_url.length; i++) {
				if (m_url[i].equals("") || m_url[i].equals(PARAM_NO_VALUE)) {
					continue;
				}
				if (m_url[i].indexOf("?") > 0) {
					m_url[i] += "&__locale=" + m_locale;
				} else {
					m_url[i] += "?__locale=" + m_locale;
				}
			}
			doDispatch(request, response);
		}
	}

	@Override
	protected void doDispatch(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException,
			PortletSecurityException, IOException {
		if (!WindowState.MINIMIZED.equals(request.getWindowState())) {
			PortletMode portletMode = request.getPortletMode();
			if (PortletMode.VIEW.equals(portletMode)) {
				doView(request, response);
			} else if (PortletMode.HELP.equals(portletMode)) {
				doHelp(request, response);
			} else if (PortletMode.EDIT.equals(portletMode)) {
				doEdit(request, response);
			} else if (CONTENT.equals(portletMode)) {
				doContent(request, response);
			} else if (ADMIN.equals(portletMode)) {
				doAdmin(request, response);
			}
		}
	}

	@Override
	protected void doView(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		nameSpace = response.getNamespace();
		PortletURL url = response.createActionURL();
		String htmlbody = this.getScript();
		for (int i = 0; i < m_url.length; i++) {
			String htmlString = "";
			if (i == 0) {
				htmlbody += "<div id=\"" + nameSpace + "_Tabcontent_" + i
						+ "\">\n";
			} else {
				htmlbody += ("<div id=\"" + nameSpace + "_Tabcontent_" + i + "\" style=\"display:none;\">\n");
			}
			htmlString = this.buildHtml(m_url[i]);
			url.setParameter("urlkey", String.valueOf(i));
			htmlbody += converter.displayConverter(htmlString, url);
			htmlbody += "</div>\n";
		}
		response.getWriter().println(htmlbody);
	}

	protected void doContent(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		int i = 0;
		m_pageurl = (String) request.getPortletSession()
				.getAttribute("pageurl");
		try {
			i = Integer.parseInt((String) request.getPortletSession()
					.getAttribute("urlkey"));
		} catch (Exception e) {
			// do nothing
		}
		if (m_pageurl != null && !m_pageurl.equals("")
				&& !m_pageurl.equals(PARAM_NO_VALUE)) {
			if (m_url[i].indexOf("?") > 0) {
				m_url[i] += "&pageurl=" + m_pageurl;
			} else {
				m_url[i] += "?pageurl=" + m_pageurl;
			}
			String htmlbody = this.buildHtml(m_url[i]);
			if (pageOrParametername != null && !htmlbody.equals("")) {
				htmlbody = converter.htmlConverter(htmlbody, m_pageName,
						m_publicRenderParameterNames, m_portleturl);
			}
			String title = (String) request.getPortletSession().getAttribute(
					"title");
			response.setTitle(title);
			response.getWriter().println(htmlbody);
		} else {
			PortletURL url = response.createRenderURL();
			url.setPortletMode(PortletMode.VIEW);
			url.setWindowState(WindowState.NORMAL);
			response.getWriter().println(
					"<a href=\"" + url
							+ "\">Please select the contents of the title</a>");
		}
	}

	@Override
	protected void doHelp(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("Hello World!");
	}

	@Override
	protected void doEdit(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("Hello World!");
	}

	protected void doError(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("PorletURL is NULL!");
	}

	@Override
	protected void processDispatch(JBossActionRequest req,
			JBossActionResponse resp) throws PortletException,
			PortletSecurityException, IOException {
		PortletMode portletMode = req.getPortletMode();
		if (PortletMode.VIEW.equals(portletMode)) {
			processView(req, resp);
		} else if (PortletMode.HELP.equals(portletMode)) {
			processHelp(req, resp);
		} else if (PortletMode.EDIT.equals(portletMode)) {
			processEdit(req, resp);
		} else if (ADMIN.equals(portletMode)) {
			processAdmin(req, resp);
		} else if (CONTENT.equals(portletMode)) {
			processContent(req, resp);
		}
	}

	@Override
	public void processView(JBossActionRequest req, JBossActionResponse resp)
			throws PortletException, PortletSecurityException, IOException {
		String paramValue = req.getParameter("pageurl");
		PortletSession ps = req.getPortletSession();
		ps.setAttribute("pageurl", paramValue);
		if (paramValue != null) {
			ps.setAttribute("title", req.getParameter("title"));
			resp.setWindowState(WindowState.MAXIMIZED);
			resp.setPortletMode(CONTENT);
		} else {
			resp.setWindowState(WindowState.NORMAL);
		}
	}

	public void processContent(JBossActionRequest req, JBossActionResponse resp)
			throws PortletException, PortletSecurityException, IOException {
		// do nothing
	}

	public String getScript() {
		StringBuffer inputScript = new StringBuffer(512);
		inputScript
				.append("<script language=\"JavaScript\" type=\"text/javascript\">\n");
		inputScript.append("//<![CDATA[\n");
		inputScript.append("var " + nameSpace + "_oldTabnum=\"0\";\n");
		inputScript.append("var " + nameSpace + "_oldIdt=\"\";\n");
		inputScript.append("function " + nameSpace + "_Show_Tab(Tabnum){\n");
		inputScript.append("if(" + nameSpace + "_oldTabnum==Tabnum){\n");
		inputScript.append("return;\n");
		inputScript.append("}\n");
		for (int i = 0; i < m_url.length; i++) {
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_Tabcontent_" + i + "\").style.display=\"none\";\n");
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
		for (int i = 0; i < m_title.length; i++) {
			if (i == 0) {
				inputScript
						.append("<li id=\""
								+ nameSpace
								+ "_l"
								+ i
								+ "\" class=\"currentTab\"><a href=\"javascript:void(null);\"  onclick=\""
								+ nameSpace + "_Show_Tab(" + i + ")\">"
								+ m_title[i].toString() + "</a></li>\n");
			} else {
				inputScript
						.append("<li id=\""
								+ nameSpace
								+ "_l"
								+ i
								+ "\" class=\"NoCSS\"><a href=\"javascript:void(null);\"  onclick=\""
								+ nameSpace + "_Show_Tab(" + i + ")\">"
								+ m_title[i].toString() + "</a></li>\n");
			}
		}
		inputScript.append("</ul>\n");
		inputScript.append("</div>");
		inputScript.append("</td>\n");
		inputScript.append("</tr>\n");
		inputScript.append("</table>\n");
		return inputScript.toString();
	}

	public String buildHtml(String url) {
		String inputLine = "";
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt(m_timeout);
		} catch (Exception e) {
			// do nothing
		}
		try {
			URL myurl = new URL(url);
			URLConnection myurlcon = myurl.openConnection();
			myurlcon.setConnectTimeout(timeout);
			InputStreamReader isr = new InputStreamReader(myurlcon
					.getInputStream(), "UTF-8");
			BufferedReader in = new BufferedReader(isr);
			String s = in.readLine();
			while (s != null) {
				inputLine += s;
				s = in.readLine();
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return inputLine;
	}

	@Override
	protected String getTitle(RenderRequest request) {
		ResourceBundle bundle = getResourceBundle(request.getLocale());
		String titleString = bundle.getString("javax.portlet.title");
		try {
			titleString = new String(titleString.getBytes("ISO8859-1"), "UTF-8");
		} catch (Exception e) {
			// not doing
		}
		return titleString;
	}

	@Override
	public void destroy() {

	}
}
