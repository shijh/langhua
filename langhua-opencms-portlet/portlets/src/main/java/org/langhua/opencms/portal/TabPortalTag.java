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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.ResourceBundle;

import javax.portlet.GenericPortlet;
import javax.portlet.PortletException;
import javax.portlet.PortletPreferences;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;
import javax.portlet.UnavailableException;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author Wang Pengfei, wangpf@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * @author An Tao, antao@langhua.cn
 */
public class TabPortalTag extends GenericPortlet {

	private String[] m_url;
	private String[] m_title;
	private String m_locale;
	private String m_timeout;
	private String nameSpace = "";
	private String[] pageOrParametername = null;
	public static final String PARAM_NO_VALUE = "(no value)";
	public static final String[] PARAM_NO_VALUES = { "(no value)" };
	public static final int DEFAULT_TIMEOUT = 5000;

	protected void doView(RenderRequest request, RenderResponse response)
			throws PortletException, IOException, UnavailableException {
		response.setContentType("text/html");
		nameSpace = response.getNamespace();
		PortletPreferences preferences = request.getPreferences();
		Enumeration names = preferences.getNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			if ("url".equals(name)) {
				m_url = preferences.getValues(name, PARAM_NO_VALUES);
			} else if ("locale".equals(name)) {
				m_locale = preferences.getValue(name, PARAM_NO_VALUE)
						.toString().trim();
			} else if ("timeout".equals(name)) {
				m_timeout = preferences.getValue(name,
						String.valueOf(DEFAULT_TIMEOUT)).toString().trim();
			} else if ("title".equals(name)) {
				String titles = preferences.getValue(name, "(No Title)")
						.toString().trim();
				m_title = titles.split("\\|");
			} else if ("iframe".equals(name)) {
				String iframe = preferences.getValue(name,
						String.valueOf(PARAM_NO_VALUE)).toString().trim();
				pageOrParametername = iframe.split("\\|");
			}
		}
		String htmlbody = this.buildHtml();
		response.getWriter().println(htmlbody);
	}

	protected void doHelp(RenderRequest request, RenderResponse response)
			throws PortletException, IOException, UnavailableException {
		response.setContentType("text/html");

		response.getWriter().println("Hello World!");
	}

	protected void doEdit(RenderRequest renderrequest,
			RenderResponse renderresponse) throws PortletException,
			IOException, UnavailableException {
		renderresponse.setContentType("text/html");
	}

	public String buildHtml() {
		StringBuffer inputScript = new StringBuffer(512);
		String inputLine = "";
		String inputString = "";
		if (m_url == null || m_url.equals("") || m_url.equals(PARAM_NO_VALUES)) {
			return inputLine.toString();
		}
		if (m_locale != null && !m_locale.equals("")
				&& !m_locale.equals(PARAM_NO_VALUE)) {
			for (int i = 0; i < m_url.length; i++) {
				if (m_url[i].indexOf("?") > 0) {
					m_url[i] += "&__locale=" + m_locale;
				} else {
					m_url[i] += "?__locale=" + m_locale;
				}
			}
		}
		int timeout = DEFAULT_TIMEOUT;
		try {
			timeout = Integer.parseInt(m_timeout);
		} catch (Exception e) {
			// do nothing
		}
		try {
			int twidth=100/m_title.length;
			inputScript.append("<script language=\"JavaScript\" type=\"text/javascript\">\n");
			inputScript.append("//<![CDATA[\n");
			inputScript.append("var " + nameSpace + "_oldId=\"\";");
			inputScript.append("var " + nameSpace + "_oldIdt=\"\";");
			inputScript.append("function " + nameSpace + "_Show_Tab(Tabnum){");
			for (int i = 0; i < m_url.length; i++) {
				inputScript.append("document.getElementById(\"" + nameSpace
						+ "_Tabcontent_" + i + "\").style.display=\"none\";");
			}
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_Tabcontent_\"+Tabnum).style.display=\"block\";");
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_f\"+Tabnum).color=\"#000000\";");
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_t\"+Tabnum).className=\"open\";");
			inputScript.append("if(" + nameSpace + "_oldId!=\"\"&&" + nameSpace
					+ "_oldId!=\"" + nameSpace + "_f\"+Tabnum){");
			inputScript.append("document.getElementById(" + nameSpace
					+ "_oldIdt).className=\"off\";");
			inputScript.append("document.getElementById(" + nameSpace
					+ "_oldId).color=\"#42658c\";");
			inputScript.append("}else{");
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_f0\").color=\"#42658c\";");
			inputScript.append("document.getElementById(\"" + nameSpace
					+ "_t0\").className=\"off\";");
			inputScript.append("}");
			inputScript.append("" + nameSpace + "_oldId=\"" + nameSpace
					+ "_f\"+Tabnum;");
			inputScript.append("" + nameSpace + "_oldIdt=\"" + nameSpace
					+ "_t\"+Tabnum;");
			inputScript.append("}");
			inputScript.append("//]]>\n");
			inputScript.append("</script>");
		
			inputScript
					.append("<table width=\"100%\" border=\"0\" cellpadding=\"0\" cellspacing=\"0\">\n");
			inputScript.append("<tr>\n");
			for (int i = 0; i < m_title.length; i++) {
				if (i == 0) {
					inputScript
							.append(" <td width=\""+twidth+"%\" id="
									+ nameSpace
									+ "_t"
									+ i
									+ " align=\"center\" valign=\"middle\" class=\"open\"><a href=\"javascript:void(null);\" onclick=\""
									+ nameSpace + "_Show_Tab(" + i
									+ ")\"><font color=#000000;  id="
									+ nameSpace + "_f" + i + ">"
									+ m_title[i].toString()
									+ "</font></a></td>\n");
				} else {
					inputScript
							.append(" <td width=\""+twidth+"%\" id="
									+ nameSpace
									+ "_t"
									+ i
									+ " align=\"center\" valign=\"middle\"  class=\"off\"><a href=\"javascript:void(null);\" onclick=\""
									+ nameSpace + "_Show_Tab(" + i
									+ ")\"><font color=#42658c; id="
									+ nameSpace + "_f" + i + " >"
									+ m_title[i].toString()
									+ "</font></a></td>\n");
				}
			}
			inputScript.append("</tr>\n");
			inputScript.append("</table>\n");
			for (int i = 0; i < m_url.length; i++) {
				if (i == 0) {
					inputLine += "<div id=" + nameSpace + "_Tabcontent_" + i
							+ ">\n";
				} else {
					inputLine += ("<div id=" + nameSpace + "_Tabcontent_" + i + " style=\"display:none;\">\n");
				}
				URL myurl = new URL(m_url[i]);
				URLConnection myurlcon = myurl.openConnection();
				myurlcon.setConnectTimeout(timeout);
				InputStreamReader isr = new InputStreamReader(myurlcon
						.getInputStream(), "UTF-8");
				BufferedReader in = new BufferedReader(isr);
				String s = in.readLine();
				while (s != null) {
					if(pageOrParametername != null){
						inputLine += s;
					}else{
						inputLine += s+"\n";
					}
					s = in.readLine();
				}
				inputLine += "</div>\n";
				in.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (pageOrParametername != null&&!inputLine.equals("")) {
			Converter converter = new Converter("UTF-8");
			inputString= converter.htmlConverter(inputScript+inputLine,pageOrParametername);
		} else {
			inputString =inputScript+inputLine;
		}
		return inputString;
	}
	
	@Override
	protected String getTitle(RenderRequest request) {
		ResourceBundle bundle = getResourceBundle(request.getLocale());
		String titleString=bundle.getString("javax.portlet.title");
		try {
			return new String(titleString.getBytes("ISO8859-1"),"UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			return titleString;
		}
	}
}
