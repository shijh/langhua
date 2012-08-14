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
import java.net.HttpURLConnection;
import java.net.URL;
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
public class PortletContentTag extends JBossPortlet {
	private String m_url;
	private String m_locale = null;
	private String m_timeout;
	private String m_pageurl;
	private DisplayConverter converter = new DisplayConverter("UTF-8");
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final int DEFAULT_TIMEOUT = 5000;
	private String[] pageOrParametername = null;
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
		PortletPreferences preferences = request.getPreferences();
		m_publicRenderParameterNames = new ArrayList<String>();
		Enumeration names = preferences.getNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if ("url".equals(name)) {
				m_url = preferences.getValue(name, PARAM_NO_VALUE).toString()
						.trim();
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
		m_locale = request.getLocale().getLanguage();
		if (m_url == null || m_url.equals("") || m_url.equals(PARAM_NO_VALUE)) {
			doError(request, response);
		} else {
			if (m_url.indexOf("?") > 0) {
				m_url += "&__locale=" + m_locale;
			} else {
				m_url += "?__locale=" + m_locale;
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
		String htmlbody = this.buildHtml();
		PortletURL url = response.createActionURL();
		htmlbody = converter.displayConverter(htmlbody, url);
		response.getWriter().println(htmlbody);
	}

//	@Override
	protected void doContent(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException, IOException,
			UnavailableException {
		response.setContentType("text/html");
		m_pageurl = (String) request.getPortletSession()
				.getAttribute("pageurl");
		String title = (String) request.getPortletSession().getAttribute(
				"title");
		if (title != null) {
			response.setTitle(title);
		}
		if (m_pageurl != null && !m_pageurl.equals("")
				&& !m_pageurl.equals(PARAM_NO_VALUE)) {
			if (m_url.indexOf("?") > 0) {
				m_url += "&pageurl=" + m_pageurl;
			} else {
				m_url += "?pageurl=" + m_pageurl;
			}

			String htmlbody = this.buildHtml();
			if (pageOrParametername != null && !htmlbody.equals("")) {
				htmlbody = converter.htmlConverter(htmlbody, m_pageName,
						m_publicRenderParameterNames, m_portleturl);
			}
			response.getWriter().println(htmlbody);
		} else {
			doView(request, response);

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
		response.getWriter().println("PortletURL is NULL!");
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
			if (req.getParameter("title") != null) {
				ps.setAttribute("title", req.getParameter("title"));
			}
			resp.setWindowState(WindowState.MAXIMIZED);
			resp.setPortletMode(CONTENT);
		} else {
			resp.setWindowState(WindowState.NORMAL);
		}
	}

	public void processContent(JBossActionRequest req, JBossActionResponse resp)
			throws PortletException, PortletSecurityException, IOException {
		String paramValue = req.getParameter("pageurl");
		PortletSession ps = req.getPortletSession();
		ps.setAttribute("pageurl", paramValue);
		if (paramValue != null) {
			if (req.getParameter("title") != null) {
				ps.setAttribute("title", req.getParameter("title"));
			}
			resp.setWindowState(WindowState.MAXIMIZED);
			resp.setPortletMode(CONTENT);
		} else {
			resp.setWindowState(WindowState.NORMAL);
		}
	}

	public String buildHtml() {
		String inputLine = "";
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt(m_timeout);
		} catch (Exception e) {
			// do nothing
		}
		try {
			URL myurl = new URL(m_url);
			HttpURLConnection myurlcon = (HttpURLConnection) myurl
					.openConnection();
			myurlcon.connect();
			myurlcon.setConnectTimeout(timeout);
			if (myurlcon.getResponseCode() == 200) {
				InputStreamReader isr = new InputStreamReader(myurlcon
						.getInputStream(), "UTF-8");
				BufferedReader in = new BufferedReader(isr);
				String s = in.readLine();
				while (s != null) {
					inputLine += s;
					s = in.readLine();
				}
				in.close();
			} else {
				return inputLine = "";
			}
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
			// do nothing
		}
		return titleString;
	}

	@Override
	public void destroy() {
	}
}
