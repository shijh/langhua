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
import java.util.Locale;
import org.apache.commons.logging.Log;
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
 * &lt;ocms:textnavigation &nbsp linkpath="/taglib/LinkSrc" &nbsp &nbsp
 * cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * 
 * Image name: TextNavigation-display.PNG<br>
 * 
 */
public class TextNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(TextNavigation.class);

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
		Locale locale = cms.getRequestContext().getLocale();
		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());
			CmsObject obj = cms.getCmsObject();
			CmsFile config = obj.readFile(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(obj,
					config);
			String imguprowbgimage = configuration.getStringValue(obj,
					"img.uprow.bgimage", locale);
			String imgdownrowbgimage = configuration.getStringValue(obj,
					"img.downrow.bgimage", locale);
			String Separatedimage = configuration.getStringValue(obj,
					"Separated.image", locale);
			String Separatedimageheight = configuration.getStringValue(obj,
					"Separated.image.height", locale);
			String Separatedimagewidth = configuration.getStringValue(obj,
					"Separated.image.width", locale);
			String fileName = config.getName();
			if (CmsStringUtil.isEmpty(getCssIndicator())) {
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append("-->");
				html.append("</style>\n");
			}
			if (CmsStringUtil.isNotEmpty(getCssIndicator())	&& A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {
				html.append(buildCSS(cms, cssfile));
			}
			if (CmsStringUtil.isEmpty(getCssIndicator())
					|| A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED.equals(getCssIndicator())) {
				html.append("<div align=\"left\">\n");
				html.append("<div class=\"content" + fileName + "\">\n");
				I_CmsXmlContentContainer container;

				container = cmsx.contentload("singleFile", linkpath, true);

				while (container.hasMoreResources()) {

					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container, "ParentLink");

					for (int number = 0; container1.hasMoreResources(); number++) {
						if (number == 0) {
							if (imguprowbgimage.equals("")) {
								html.append("<div class=\"uprow" + fileName
										+ "\">");
							} else {
								String uprowuri = cms.link(imguprowbgimage);
								html.append("<div class=\"uprow" + fileName
										+ "\" style=\"background:url("
										+ uprowuri + ");\">");

							}
							String folderpath = cmsx.contentshow(container1,
									"ParentLink");
							if (CmsStringUtil.isNotEmpty(folderpath)) {
								List<CmsJspNavElement> list1 = new CmsJspNavBuilder(cms.getCmsObject()).getNavigationForFolder(folderpath);
								if (list1.size() != 0) {
									int num = list1.size();
									for (int i = 0; i < num; i++) {
										CmsJspNavElement nav = (CmsJspNavElement) list1
												.get(i);
										String title1 = nav.getNavText();
										String path1 = nav.getResourceName();
										html.append("<div class=\"tr"
												+ fileName + "\">");
										html.append("<a href=\""
												+ cms.link(path1)
												+ "\" class=\"uplink"
												+ fileName + "\">\n");
										html.append("&nbsp;" + title1);
										html.append("</a>\n");
										html.append("</div>\n");
										if (i < num - 1) {
											if (CmsStringUtil
													.isEmpty(Separatedimage)) {
												html.append("<div class=\"tr"
														+ fileName + " uplink"
														+ fileName
														+ "\">|</div>\n");
											} else {
												html
														.append("<div class=\"tr"
																+ fileName
																+ " uplink"
																+ fileName
																+ "\"><img src=\""
																+ cms
																		.link(Separatedimage)
																+ "\" width=\""
																+ Separatedimagewidth
																+ "\" height=\""
																+ Separatedimageheight
																+ "\" /></div>\n");
											}

										}

									}
								}
							}
							html.append("</div>\n");

						}
						if (number == 1) {
							if (CmsStringUtil.isEmpty(imgdownrowbgimage)) {
								html.append("<div class=\"downrow" + fileName
										+ "\" >");
							} else {
								String downrowuri = cms.link(imgdownrowbgimage);
								html.append("<div class=\"downrow" + fileName
										+ "\" style=\"background:url("
										+ downrowuri + ");\">");
							}
						}
						if (number != 0) {
							String foldertitle = cmsx.contentshow(container1,
									"Description");
							String folderpath = cmsx.contentshow(container1,
									"ParentLink");
							I_CmsXmlContentContainer container2 = cmsx
									.contentloop(container1, "SeedLink");

							html.append("<div class=\"tr" + fileName + "\">");
							html.append("<a href=\"" + cms.link(folderpath)
									+ "\" class=\"downlink" + fileName
									+ "\">\n");
							html.append("<font id=\"parentsfont" + fileName
									+ "\">");
							html.append("&nbsp;" + foldertitle);
							html.append("</font>\n");
							html.append(":");
							html.append("</a>\n");
							html.append("</div>\n");
							while (container2.hasMoreResources()) {
								String foldertitle1 = cmsx.contentshow(
										container2, "Description");
								String folderpath1 = cmsx.contentshow(
										container2, "SeedLink");
								html.append("<div class=\"tr" + fileName
										+ "\">");
								html.append("<a href=\""
										+ cms.link(folderpath1)
										+ "\"  class=\"downlink" + fileName
										+ "\">");
								html.append(foldertitle1);
								html.append("</a>");
								html.append(" ");
								html.append("</div>\n");
							}
						}

					}

				}

				html.append("</div>");
				html.append("</div>");
			}
		} catch (Exception e1) {

			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}
}