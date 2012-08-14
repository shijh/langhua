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
package org.langhua.opencms.jbossportal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.UnavailableException;

import org.jboss.portlet.JBossPortlet;
import org.jboss.portlet.JBossRenderRequest;
import org.jboss.portlet.JBossRenderResponse;
import org.langhua.opencms.portal.Converter;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author Wang Pengfei, wangpf@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class JBossPortalTag extends JBossPortlet {
	private String m_url;
	private String m_locale;
	private String m_timeout;
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final int DEFAULT_TIMEOUT = 5000;
	private String[] pageOrParametername = null;
	
	protected void doView(JBossRenderRequest request, JBossRenderResponse response)
			throws PortletException, IOException, UnavailableException {
		response.setContentType("text/html");
		PortletPreferences preferences = request.getPreferences();
		Enumeration names = preferences.getNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if ("url".equals(name)) {
				m_url = preferences.getValue(name, PARAM_NO_VALUE).toString()
						.trim();
			} else if ("locale".equals(name)) {
				m_locale = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("timeout".equals(name)) {
				m_timeout = preferences.getValue(name,
						String.valueOf(DEFAULT_TIMEOUT)).toString().trim();
			} else if ("iframe".equals(name)) {
				String iframe = preferences.getValue(name,
						String.valueOf(PARAM_NO_VALUE)).toString().trim();
				pageOrParametername = iframe.split("\\|");
			}
		}
		String htmlbody = this.buildHtml();
		response.getWriter().println(htmlbody);
	}

	protected void doHelp(JBossRenderRequest request, JBossRenderResponse response)
			throws PortletException, IOException, UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("Hello World!");
	}

	protected void doEdit(JBossRenderRequest renderrequest,
			JBossRenderResponse renderresponse) throws PortletException,
			IOException, UnavailableException {
		renderresponse.setContentType("text/html");
	}

	/**
	 * Provide a default generic editor for preferences that produce HTML
	 * markup.
	 */
	protected void doList(JBossRenderRequest request,
			JBossRenderResponse response) throws PortletException,
			IOException, UnavailableException {
		response.setContentType("text/html");
		response.getWriter().println("This is a list!");
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
				inputLine += s;
				s = in.readLine();
			}
			in.close();	
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (pageOrParametername != null && inputLine.length() > 0) {
			Converter converter = new Converter("UTF-8");
			inputLine = converter.htmlConverter(inputLine, pageOrParametername);
		}
		return inputLine;
	}
}
