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

import org.apache.commons.logging.Log;
import org.opencms.main.*;
import org.opencms.util.CmsStringUtil;

import org.opencms.jsp.*;
import org.opencms.file.*;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.*;
import javax.servlet.jsp.*;

/**
 * example: <br>
 * 
 * &lt;%@ taglib &nbsp prefix="ocms" &nbsp
 * uri="http://www.langhua.cn/taglib/display"%&gt;<br>
 * 
 * &lt;ocms:sectionandsubsectionnavigation &nbsp linkpath="/taglib/LinkSrc"
 * &nbsp &nbsp cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * 
 * Image name: Legalstudiesborder-display.png<br>
 * 
 */
public class SectionAndSubSectionNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog
			.getLog(SectionAndSubSectionNavigation.class);

	CmsObject cmso;

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
		CmsJspXmlContentBean cmsx = new CmsJspXmlContentBean(pageContext,
				request, response);
		String title = "";

		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());
			cmso = cms.getCmsObject();

			CmsResource configFile = cmso.readResource(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			String fileName = configFile.getName();

			if (CmsStringUtil.isEmpty(getCssIndicator())) {
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append("-->");
				html.append("</style>\n");
			}
			if (CmsStringUtil.isNotEmpty(getCssIndicator())
					&& A_LanghuaTag.CSS_INDICATOR_NOSTYLE
							.equals(getCssIndicator())) {
				html.append(buildCSS(cms, cssfile));
			}

			if (CmsStringUtil.isEmpty(getCssIndicator())
					|| A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED
							.equals(getCssIndicator())) {

				I_CmsXmlContentContainer container;
				container = cmsx.contentload("singleFile", linkpath, false);
				while (container.hasMoreResources()) {
					title = cmsx.contentshow(container, "title");
					html.append("<div class=\"leftyj2j" + fileName + "\">\n");
					html.append("<div class=\"leftyj2jCard" + fileName
							+ "\">\n");
					html.append("<span>" + title + "</span>");
					html.append("<p>");
					I_CmsXmlContentContainer container1;
					container1 = cmsx.contentloop(container, "school");
					while (container1.hasMoreResources()) {
						String fileuri = "";
						String fontcolor = "";
						String fontweight = "";
						String fontsize = "";
						fontcolor = cmsx.contentshow(container1, "fontcolor");
						fontweight = cmsx.contentshow(container1, "fontweight");
						fontsize = cmsx.contentshow(container1, "fontsize");
						String teaser = cmsx.contentshow(container1, "school");
						String fileurl = cmsx.contentshow(container1,
								"file.url");
						boolean isProgram;
						isProgram = Boolean.parseBoolean(cmsx.contentshow(
								container1, "isProgram"));
						if (CmsStringUtil.isNotEmpty(fileurl)) {
							fileuri = cms.link(fileurl);
						}
						html.append("<a target=\"_blank\" href=\"" + fileuri
								+ "\"><font color=\"" + fontcolor
								+ "\";style=\"font-weight:" + fontweight
								+ ";font-size:" + fontsize + "px;\">" + teaser
								+ "");
						html.append("</font>");
						html.append("</a>\n");
						if (isProgram) {
							html.append("|");
						}
					}
					html.append("</p>");
					html.append("</div>\n");
					html.append("<div class=\"leftyj2jInfo" + fileName
							+ "\">\n");
				}
			}
		} catch (Exception e1) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}
}
