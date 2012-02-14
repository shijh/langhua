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

import java.util.Locale;

import org.apache.commons.logging.Log;
import org.opencms.i18n.CmsEncoder;
import org.opencms.jsp.*;
import org.opencms.main.CmsLog;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
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
 * &lt;%ocms:subsectionsnavigation &nbsp linkpath="/taglib/LinkSrc" &nbsp &nbsp
 * cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * cssfile: the css config.
 * 
 * Image name: SubSectionsNavigation-display.PNG<br>
 * 
 */
public class SubSectionsNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(SubSectionsNavigation.class);

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
		String superiortitle = "";
		CmsObject cmso = cms.getCmsObject();
		Locale locale = cms.getRequestContext().getLocale();
		String folderpath = "";
		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());
			CmsFile config = cmso.readFile(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
					config);
			String contentfontsize = configuration.getStringValue(cmso,
					"content.fontsize", locale);
			String fileName = config.getName();
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
				html.append("<table class=\"tmain" + fileName + "\">\n");

				I_CmsXmlContentContainer container;

				container = cmsx.contentload("singleFile", linkpath, true);

				while (container.hasMoreResources()) {
					superiortitle = cmsx.contentshow(container, "Superior");
					html.append("<tr>\n");
					html.append("<td id=\"titletd" + fileName + "\">\n");
					html.append("<div class=\"titlemargin" + fileName + "\">\n");
					html.append(superiortitle);
					html.append("</div>\n");
					html.append("</td>\n");
					html.append("</tr>\n");
					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container, "ParentLink");

					while (container1.hasMoreResources()) {
						String foldertitle = cmsx.contentshow(container1,
								"Description");
						folderpath = cmsx.contentshow(container1, "ParentLink");
						String categoryLink = cmsx.contentshow(container1,
								"categoryLink");
						folderpath = cms.getRequestContext().removeSiteRoot(
								folderpath);
						if (!categoryLink.equals("")) {
							String d = CmsEncoder.escape(categoryLink,
									CmsEncoder.ENCODING_UTF_8);
							folderpath = folderpath + "?P_category=" + d;
						}
						int length = foldertitle.length();
						int fontsize = Integer.parseInt(contentfontsize);
						int size = (length + 1) * fontsize;
						html.append("<tr>\n");
						html.append("<td>\n");
						html.append("<table id=\"tcontent" + fileName + "\">\n");
						html.append("<tr valign=\"top\">\n");
						html.append("<td width=\"" + size + "px\">\n");
						html.append("<a href=\"" + cms.link(folderpath)
								+ "\" class=\"link" + fileName + "\">\n");
						html.append(foldertitle + ":");
						html.append("</a>\n");
						html.append("</td>\n");
						html.append("<td>\n");
						I_CmsXmlContentContainer container2 = cmsx.contentloop(
								container1, "SeedLink");
						while (container2.hasMoreResources()) {
							String foldertitle1 = cmsx.contentshow(container2,
									"Description");
							String folderpath1 = cmsx.contentshow(container2,
									"SeedLink");
							String RoomLink = cmsx.contentshow(container2,
									"RoomLink");
							folderpath1 = cms.getRequestContext()
									.removeSiteRoot(folderpath1);
							if (!RoomLink.equals("")) {
								String x = CmsEncoder.escape(RoomLink,
										CmsEncoder.ENCODING_UTF_8);
								folderpath1 = folderpath + "&P_Room=" + x;
							}
							html.append("<a href=\"" + cms.link(folderpath1)
									+ "\" class=\"link" + fileName + "\">\n");
							html.append(foldertitle1);
							html.append("</a>\n");
							html.append(" ");
						}
						html.append("</td>\n");
						html.append("</tr>\n");
						html.append("</table>\n");
						html.append("</td>\n");
						html.append("</tr>\n");
					}
				}
				html.append("</table>\n");
			}
		} catch (Exception e1) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}
}