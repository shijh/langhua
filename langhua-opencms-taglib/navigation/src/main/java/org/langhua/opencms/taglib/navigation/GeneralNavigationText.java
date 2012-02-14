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
package org.langhua.opencms.taglib.navigation;

import java.util.List;

import org.apache.commons.logging.Log;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;

import org.opencms.jsp.*;
import org.opencms.main.CmsLog;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.*;
import javax.servlet.jsp.*;

/**
 * example: <br>
 * 
 * &lt;%@ taglib &nbsp prefix="ocms" &nbsp
 * uri="http://www.langhua.cn/taglib/display"%&gt;<br>
 * 
 * &lt;%ocms:generalnavigationtext &nbsp cssfile="/taglib/CssSrc"/&gt;<br>
 * 
 * cssfile : css file.<br>
 * 
 * Image name: GeneralNavigationText-display.PNG<br>
 * 
 */
public class GeneralNavigationText extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(GeneralNavigationText.class);

	public int doStartTag() throws JspTagException {

		String htmlbody = buildHtml();
		try {

			pageContext.getOut().print(htmlbody);
		} catch (Exception e) {

			if (LOG.isDebugEnabled()) {
				LOG.debug(e);
			}
		}

		return SKIP_BODY;
	}

	public String buildHtml() {
		StringBuffer html = new StringBuffer(512);
		HttpServletRequest request = (HttpServletRequest) pageContext
				.getRequest();
		HttpServletResponse response = (HttpServletResponse) pageContext
				.getResponse();
		CmsJspActionElement cms = new CmsJspActionElement(pageContext, request,
				response);

		String fileuri = cms.getRequestContext().getUri();
		String arg[] = fileuri.split("/");
		StringBuffer sb = new StringBuffer();
		sb = sb.append("/");
		sb = sb.append(arg[1]);
		sb = sb.append("/");
		String folderpath = sb.toString();

		CmsObject cmso = cms.getCmsObject();

		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());

			CmsResource configFile = cmso.readResource(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			String fileName = configFile.getName();
			html.append("<style type=\"text/css\">\n");
			html.append(buildCSS(cms, cssfile));
			html.append("</style>\n");

			html.append("<table style=\"border-collapse : collapse;\">\n");
			html.append("<tr>\n");
			html.append("<td>\n");
			List<CmsJspNavElement> list = new CmsJspNavBuilder(cms.getCmsObject()).getNavigationForFolder(folderpath);
			html.append("<table class=\"tmain" + fileName
					+ "\" style=\"border-collapse : collapse;\">\n");
			html.append("<tr id=\"lineheight" + fileName + "\">\n");
			for (int j = 0; j < list.size(); j++) {
				CmsJspNavElement nav = (CmsJspNavElement) list.get(j);
				String ntitle = nav.getNavText();
				String npath = cms.link(nav.getResourceName());
				html.append("<td id=\"tdmain" + fileName + "\">\n");
				html.append("<a href=\"" + npath + "\" id=\"a" + fileName
						+ "\">");
				html.append("<font id=\"font" + fileName + "\">");
				html.append(ntitle + "|");
				html.append("</font>\n");
				html.append("</a>");
				html.append("</td>\n");
			}
			html.append("</tr>\n");
			html.append("</table>\n");
			html.append("</td>\n");
			html.append("</tr>\n");
			html.append("</table>\n");
		} catch (Exception e1) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}
	
}