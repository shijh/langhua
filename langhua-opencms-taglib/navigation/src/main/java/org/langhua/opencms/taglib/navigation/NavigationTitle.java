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
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResourceFilter;
import org.opencms.jsp.*;
import org.opencms.main.CmsLog;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;

import org.langhua.opencms.taglib.commons.A_LanghuaTag;

import javax.servlet.http.*;
import javax.servlet.jsp.*;

/**
 * example: <br>
 * 
 * &lt;%@ taglib &nbsp prefix="ocms" &nbsp
 * uri="http://www.langhua.cn/taglib/display"%&gt;<br>
 * 
 * &lt;%ocms:navigationtitle &nbsp linkpath="/home/link/easy" &nbsp &nbsp
 * cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * Image name: NavigationTitle-display.PNG<br>
 * 
 */
public class NavigationTitle extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog
			.getLog(NavigationTitle.class);

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
		CmsObject cmso = cms.getCmsObject();
		Locale locale = cms.getRequestContext().getLocale();
		int mwidth = 0;
		int twidth = 0;
		int fs = 12;
		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());
			CmsFile config = cmso.readFile(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
					config);
			String mainwidth = configuration.getStringValue(cmso, "main.width",
					locale);
			String tdwidth = configuration.getStringValue(cmso, "td.width",
					locale);
			String fontsize = configuration.getStringValue(cmso, "font.size",
					locale);
			if (CmsStringUtil.isNotEmpty(fontsize)) {
				fs = Integer.parseInt(fontsize);
			}
			if (CmsStringUtil.isNotEmpty(mainwidth)) {
				mwidth = Integer.parseInt(mainwidth);
			}
			if (CmsStringUtil.isNotEmpty(tdwidth)) {
				twidth = Integer.parseInt(tdwidth);
			}

			String fileName = config.getName();
			if (CmsStringUtil.isEmpty(getCssIndicator())) {
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append("-->");
				html.append("</style>\n");
			}
			if (CmsStringUtil.isNotEmpty(getCssIndicator())
					&& A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {

				html.append(buildCSS(cms, cssfile));
			}
			if (CmsStringUtil.isEmpty(getCssIndicator())
					|| A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED.equals(getCssIndicator())) {
				html.append("<table style=\"border-collapse : collapse;\">\n");
				html.append("<tr>\n");
				html.append("<td>\n");
				I_CmsXmlContentContainer container;
				container = cmsx.contentload("singleFile", linkpath, true);

				while (container.hasMoreResources()) {

					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container, "ParentLink");

					while (container1.hasMoreResources()) {
						String foldertitle = cmsx.contentshow(container1,
								"Description");
						String folderpath = cmsx.contentshow(container1,
								"ParentLink");
						folderpath = cms.getRequestContext().removeSiteRoot(
								folderpath);
						I_CmsXmlContentContainer container2 = cmsx.contentloop(
								container1, "SeedLink");
						html
								.append("<table class=\"tmain"
										+ fileName
										+ "\" style=\"border-collapse : collapse;\">\n");
						int fl = 0, number = 0, num = 0, fl0 = 0;
						if (CmsStringUtil.isNotEmpty(mainwidth)
								&& CmsStringUtil.isNotEmpty(tdwidth)) {
							fl = fl + twidth;
							html.append("<tr id=\"lineheight" + fileName
									+ "\">\n");
							html.append("<td id=\"tdmain" + fileName + "\">\n");
							html.append("<a href=\"" + cms.link(folderpath)
									+ "\" id=\"a" + fileName + "\">\n");
							html.append("<font id=\"parentsfont" + fileName
									+ "\">");
							html.append(foldertitle + ":");
							html.append("</font>\n");
							html.append("</a>\n");
							html.append("</td>\n");
							while (container2.hasMoreResources()) {
								number++;
								fl = fl + twidth;
								if (fl > mwidth) {
									num = number;
									number = 0;
									fl0 = mwidth - fl;
									html.append("<td width=\"" + fl0 + "\">\n");
									html.append("&nbsp;");
									html.append("</td>\n");
									html.append("</tr>\n");
									fl = twidth;
									html.append("<tr id=\"lineheight"
											+ fileName + "\">\n");
									html.append("<td id=\"tdmain" + fileName
											+ "\">\n");
									html.append("&nbsp;");
									html.append("</td>\n");
								}
								String foldertitle1 = cmsx.contentshow(
										container2, "Description");
								String folderpath1 = cmsx.contentshow(
										container2, "SeedLink");
								html.append("<td id=\"tdmain" + fileName
										+ "\">\n");
								html.append("<a href=\""
										+ cms.link(folderpath1) + "\" id=\"a"
										+ fileName + "\">");
								html.append("<font id=\"font" + fileName
										+ "\">");
								html.append(foldertitle1 + "|");
								html.append("</font>\n");
								html.append("</a>");
								html.append("</td>\n");
							}
							for (int i = 1; i < num - number; i++) {
								html.append("<td id=\"tdmain" + fileName
										+ "\">\n");
								html.append("&nbsp;");
								html.append("</td>\n");
							}
							html.append("<td width=\"" + fl0 + "\">\n");
							html.append("&nbsp;");
							html.append("</td>\n");
							html.append("</tr>\n");

						}
						if (CmsStringUtil.isNotEmpty(mainwidth)
								&& CmsStringUtil.isEmpty(tdwidth)) {

							int folderlength = foldertitle.length();
							fl = fl + folderlength * fs + fs / 2;
							html.append("<tr>\n");
							html.append("<td colspan=\"2\">\n");
							html
									.append("<table style=\"border-collapse : collapse;\">\n");
							html.append("<tr id=\"lineheight" + fileName
									+ "\">\n");
							html.append("<td>\n");
							html.append("<a href=\"" + cms.link(folderpath)
									+ "\" id=\"a" + fileName + "\">\n");
							html.append("<font id=\"parentsfont" + fileName
									+ "\">");
							html.append(foldertitle + ":");
							html.append("</font>\n");
							html.append("</a>\n");
							html.append("</td>\n");
							while (container2.hasMoreResources()) {
								number++;
								String foldertitle1 = cmsx.contentshow(
										container2, "Description");
								String folderpath1 = cmsx.contentshow(
										container2, "SeedLink");
								int folderlength1 = foldertitle1.length();
								fl = fl + folderlength1 * fs + fs / 2;

								if (fl > mwidth) {
									fl = (folderlength + 1) * fs - fs / 4;
									int fll = folderlength * fs + fs - 2;
									html.append("</tr>\n");
									html.append("</table>\n");
									html.append("</td>\n");
									html.append("</tr>\n");
									html.append("<tr>\n");
									html.append("<td width=\"" + fll + "\">\n");
									html
											.append("<table style=\"border-collapse : collapse;\">\n");
									int folderwidth0 = mwidth - fll;
									html.append("<tr id=\"lineheight"
											+ fileName + "\">\n");
									html.append("<td width=\"" + folderlength
											* fs + "\">\n");
									html.append("&nbsp;");
									html.append("</td>\n");
									html.append("</tr>\n");
									html.append("</table>\n");
									html.append("</td>\n");
									html.append("<td width=\"" + folderwidth0
											+ "\">\n");
									html
											.append("<table style=\"border-collapse : collapse;\">\n");
									html.append("<tr>\n");
								}
								html.append("<td>\n");
								html.append("<a href=\""
										+ cms.link(folderpath1) + "\" id=\"a"
										+ fileName + "\">");
								html.append("<font id=\"font" + fileName
										+ "\">");
								html.append(foldertitle1 + "|");
								html.append("</font>\n");
								html.append("</a>");
								html.append("</td>\n");
							}

							html.append("</tr>\n");
							html.append("</table>\n");
							html.append("</td>\n");
							html.append("</tr>\n");

						}
						if (CmsStringUtil.isEmpty(mainwidth)
								&& CmsStringUtil.isEmpty(tdwidth)) {
							html.append("<tr id=\"lineheight" + fileName
									+ "\">\n");
							html.append("<td id=\"tdmain" + fileName + "\">\n");
							html.append("<a href=\"" + cms.link(folderpath)
									+ "\" id=\"a" + fileName + "\">\n");
							html.append("<font id=\"parentsfont" + fileName
									+ "\">");
							html.append(foldertitle + ":");
							html.append("</font>\n");
							html.append("</a>\n");
							html.append("</td>\n");
							while (container2.hasMoreResources()) {
								String foldertitle1 = cmsx.contentshow(
										container2, "Description");
								String folderpath1 = cmsx.contentshow(
										container2, "SeedLink");
								html.append("<td id=\"tdmain" + fileName
										+ "\">\n");
								html.append("<a href=\""
										+ cms.link(folderpath1) + "\" id=\"a"
										+ fileName + "\">");
								html.append("<font id=\"font" + fileName
										+ "\">");
								html.append(foldertitle1 + "|");
								html.append("</font>\n");
								html.append("</a>");
								html.append("</td>\n");
							}
							html.append("</tr>\n");
						}

						html.append("</table>\n");
					}
				}
				html.append("</td>\n");
				html.append("</tr>\n");
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