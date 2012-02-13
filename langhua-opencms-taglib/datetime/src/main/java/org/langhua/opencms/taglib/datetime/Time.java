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
package org.langhua.opencms.taglib.datetime;

import java.util.Locale;

import org.apache.commons.logging.Log;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResourceFilter;

import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsLog;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.*;

/**
 * example:
 * &lt;%@ taglib &nbsp prefix="ocms" &nbsp uri="http://www.langhua.org/taglib/display"%&gt;
 * &lt;ocms:time /&gt;
 * 
 * Image name: Time-display.PNG
 * 
 */
public class Time extends A_LanghuaTag {	
	private static final long serialVersionUID = 1L;
	private static final Log LOG = CmsLog.getLog(Time.class);

	public int doStartTag() throws JspTagException {

		String htmlbody = buildHtml();
				
		try {
			pageContext.getOut().print(htmlbody);
		} catch (Exception e) {			
            LOG.debug(e);
		}

		return SKIP_BODY;
	}	
	
	public String buildHtml() {
		HttpServletRequest request = (HttpServletRequest) pageContext.getRequest();
		HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
		CmsJspActionElement cms = new CmsJspActionElement(pageContext, request,	response);
		StringBuffer html = new StringBuffer(512);
		String fileName = null;
		boolean showWeekday = true;
		try {
			if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(getCssFile())) {
				String cssfile = cms.getRequestContext().removeSiteRoot(getCssFile());
				CmsObject cmso = cms.getCmsObject();
				Locale locale = cms.getRequestContext().getLocale();
				CmsFile configFile = cmso.readFile(cssfile, CmsResourceFilter.IGNORE_EXPIRATION);
				CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(
						cmso, configFile);
				showWeekday = Boolean.parseBoolean(configuration.getStringValue(cmso, "showweekday", locale));
				fileName = configFile.getName();
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append("-->");
				html.append("</style>\n");
			}
			html.append("<div" + (CmsStringUtil.isEmpty(fileName) ? "" : (" class=\"topTime"+ fileName + "\"")) + ">\n");
			html.append("<SCRIPT language=\"JavaScript\">\n");
			html.append("dayObj=new Date();\n");
			html.append("monthStr=dayObj.getMonth()+1;\n");
			html.append("year=dayObj.getFullYear();\n");
			html.append("document.write(year+\"" + cms.label(Messages.YEAR) + "\"+monthStr+\"" + cms.label(Messages.MONTH) + "\"+dayObj.getDate()+\""+ cms.label(Messages.DAY) +"\"+\" \"); \n");
			if (showWeekday) {
				html.append("document.write(\"&nbsp;\");\n");
				html.append("if(dayObj.getDay()==1) document.write(\"" + cms.label(Messages.XQYI) + "\");\n");
				html.append("if(dayObj.getDay()==2) document.write(\"" + cms.label(Messages.XQER) + "\");\n");
				html.append("if(dayObj.getDay()==3) document.write(\"" + cms.label(Messages.XQSAN) + "\");\n");
				html.append("if(dayObj.getDay()==4) document.write(\"" + cms.label(Messages.XQSI) + "\");\n");
				html.append("if(dayObj.getDay()==5) document.write(\"" + cms.label(Messages.XQWU) + "\");\n");
				html.append("if(dayObj.getDay()==6) document.write(\"" + cms.label(Messages.XQLIU) + "\");\n");
				html.append("if(dayObj.getDay()==0) document.write(\"" + cms.label(Messages.XQRI) + "\");\n");
			}
			html.append("</SCRIPT>");
			html.append("</div>\n");
		} catch (Exception e) {
            LOG.debug(e);
		}

		return html.toString();
	}

}
