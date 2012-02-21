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
import org.opencms.util.CmsStringUtil;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.*;
import javax.servlet.jsp.*;

/**
 * example: <br>
 * 
 * &lt;%@ taglib &nbsp prefix="lh-nav" &nbsp
 * uri="http://www.langhua.cn/taglib/navigation"%&gt;<br>
 * 
 * &lt;%lh-nav:textnavbar linkFile="/" cssFile="/taglib/CssSrc"/&gt;<br>
 * 
 * cssFile : css file.<br>
 * linkFile: the path to get navigation.<br>
 * 
 * Image name: TextNavBar.PNG<br>
 * 
 */
public class TextNavBar extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(TextNavBar.class);

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
		CmsObject cmso = cms.getCmsObject();
		CmsJspNavBuilder cjnb = new CmsJspNavBuilder(cms.getCmsObject());

		String folderpath = cms.getRequestContext().removeSiteRoot(getLinkFile());
		CmsJspNavElement folderNav = cjnb.getNavigationForResource(folderpath);
		String mainText = folderNav.getNavText();
		if (CmsStringUtil.isEmptyOrWhitespaceOnly(mainText)) {
			mainText = folderNav.getTitle();
		}

		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(getCssFile());
			CmsResource configFile = cmso.readResource(cssfile, CmsResourceFilter.IGNORE_EXPIRATION);
			String fileName = configFile.getName();
			html.append("<style type=\"text/css\">\n");
			html.append(buildCSS(cms, cssfile));
			html.append("</style>\n");

			html.append("<div class=\"TNBmain" + fileName + "\">\n");
			if (CmsStringUtil.isNotEmpty(mainText)) {
				html.append(mainText + ":&nbsp;\n");
			}
			List<CmsJspNavElement> list = cjnb.getNavigationForFolder(folderpath);
			boolean isFirst = true;
			for (CmsJspNavElement nav : list) {
				String ntitle = nav.getNavText();
				if (CmsStringUtil.isEmptyOrWhitespaceOnly(ntitle)) {
					ntitle = nav.getTitle();
				}
				String npath = cms.link(nav.getResourceName());
				if (isFirst) {
					isFirst = false;
				} else {
					html.append("<span class=\"TNBcolumn" + fileName + "\">|&nbsp;</span>\n");
				}
				html.append("<a href=\"" + npath + "\">");
				html.append("<span class=\"TNBcolumn" + fileName + "\">");
				html.append(ntitle);
				html.append("</span></a>\n");
			}
			html.append("</div>\n");
		} catch (Exception e) {
			LOG.debug(e);
		}

		return html.toString();
	}
	
}