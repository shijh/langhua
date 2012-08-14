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

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author Wang Pengfei, wangpf@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * @author An Tao, antao@langhua.cn
 */
public class PortalTag extends JBossPortlet {
	private String m_url;
	private String m_locale;
	private String m_timeout;
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final int DEFAULT_TIMEOUT = 5000;
	private PortletURL m_portleturl;
	private List<String> m_publicRenderParameterNames;
	private HashMap<String, String> m_query;
	private PortletURLConverter converter = new PortletURLConverter("UTF-8");;
	private String m_pageName;
	private Boolean m_displayTitle=false;
	public String portletURLRender;
	private String m_titleString;

	@Override
	public void render(JBossRenderRequest request, JBossRenderResponse response)
			throws PortletException, PortletSecurityException, IOException {
		response.setTitle(getTitle(request));
		m_titleString = "";
		m_publicRenderParameterNames = new ArrayList<String>();
		PortletPreferences preferences = request.getPreferences();
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
			} else if ("portletURL".equals(name)) {
				portletURLRender = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("page".equals(name)) {
				m_pageName = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("displayTitle".equals(name)) {
				m_displayTitle = Boolean.valueOf(preferences.getValue(name,
						"false"));
			}
		}
		if (portletURLRender != null && portletURLRender.equals("action")) {
			m_portleturl = response.createActionURL();
		} else {
			m_portleturl = response.createRenderURL();
		}
		m_query = new HashMap<String, String>();
		Enumeration publicRenderParameterNames = this.getPortletConfig()
				.getPublicRenderParameterNames();
		while (publicRenderParameterNames.hasMoreElements()) {
			String name = (String) publicRenderParameterNames.nextElement();
			m_publicRenderParameterNames.add(name);
			String query = request.getParameter(name);
			if (query != null) {
				query = java.net.URLDecoder.decode(request.getParameter(name),
						"UTF-8");
			}
			if (query != null) {
				m_query.put(name, java.net.URLEncoder.encode(query, "UTF-8"));
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
		String htmlbody = this.buildHtml();
		if (m_titleString != null && !m_titleString.equals("")) {
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
			m_url = preferences.getValue("helpurl", PARAM_NO_VALUE).toString()
					.trim();
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

	public String buildHtml() {
		String inputLine = "";
		if (m_url == null || m_url.equals("") || m_url.equals(PARAM_NO_VALUE)) {
			return inputLine;
		}
		if (m_locale != null && !m_locale.equals("")
				&& !m_locale.equals(PARAM_NO_VALUE)) {
			if (m_url.indexOf("?") > 0) {
				m_url += "&__locale=" + m_locale;
			} else {
				m_url += "?__locale=" + m_locale;
			}
		}
		Iterator iter = m_query.entrySet().iterator();
		while (iter.hasNext()) {
			Map.Entry entry = (Map.Entry) iter.next();
			String key = (String) entry.getKey();
			String val = (String) entry.getValue();
			if (m_url.indexOf("?") > 0) {
				m_url += "&" + key + "=" + val;
			} else {
				m_url += "?" + key + "=" + val;
			}
		}
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt(m_timeout);
		} catch (Exception e) {
			// do nothing
		}
		try {
			URL myurl = new URL(m_url);
			URLConnection myurlcon = myurl.openConnection();
			myurlcon.setConnectTimeout(timeout);
			InputStreamReader isr = new InputStreamReader(myurlcon
					.getInputStream(), "UTF-8");
			BufferedReader in = new BufferedReader(isr);
			String s = in.readLine();
			while (s != null) {
				if (m_pageName != null
						|| m_publicRenderParameterNames.size() > 0) {
					inputLine += s;
				} else {
					inputLine += s + "\n";
				}
				s = in.readLine();
			}
			in.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (m_displayTitle && !inputLine.equals("")) {
			m_titleString = converter.getTitleString(inputLine);
			try{
				inputLine = inputLine.substring(0, inputLine.indexOf("<title"))
				+ inputLine.substring(inputLine.indexOf("</title>")
						+ "</title>".length());
			}catch (Exception e) {
				// do nothing
			}
			
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

	protected void processAction(JBossActionRequest req,
			JBossActionResponse resp) throws PortletException,
			PortletSecurityException, IOException {
		Enumeration publicRenderParameterNames = this.getPortletConfig()
				.getPublicRenderParameterNames();
		while (publicRenderParameterNames.hasMoreElements()) {
			String name = (String) publicRenderParameterNames.nextElement();
			String value = req.getParameter(name);
			if (value != null) {
				resp.setRenderParameter(name, value);
			}
		}
	}
}
