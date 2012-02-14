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
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.jsp.*;
import org.opencms.main.CmsLog;
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
 * &lt;ocms:photocolumnnavigation &nbsp linkpath="/taglib/LinkSrc" &nbsp &nbsp
 * cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * 
 */
public class PhotoColumnNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;
	private static final Log LOG = CmsLog.getLog(PhotoColumnNavigation.class);

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
		String foldertitle = "";
		String folderpath = "";

		int imgheight = 0;
		int imgwidth = 0;
		int theight = 0;
		int cimgwidth = 0, cimgheight = 0;
		Locale locale = cms.getRequestContext().getLocale();

		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());
			I_CmsXmlContentContainer container;

			container = cmsx.contentload("singleFile", linkpath, true);

			while (container.hasMoreResources()) {
				I_CmsXmlContentContainer foldertit = cmsx.contentloop(
						container, "Superior");
				while (foldertit.hasMoreResources()) {
					foldertitle = cmsx.contentshow(container, "Superior");
				}
			}
			CmsObject cmso = cms.getCmsObject();
			CmsFile config = cmso.readFile(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
					config);
			String fileName = config.getName();
			String more = configuration.getStringValue(cmso, "more", locale);
			String picmore = configuration.getStringValue(cmso, "picmore",
					locale);
			String imgmore = configuration.getStringValue(cmso, "imgmore",
					locale);
			String titlebeforeimg = configuration.getStringValue(cmso,
					"title.beforeimg", locale);
			String titlebeforeimguri = configuration.getStringValue(cmso,
					"title.before.imguri", locale);
			String titlebeforeimgwidth = configuration.getStringValue(cmso,
					"title.beforeimg.width", locale);
			String titlebeforeimgheight = configuration.getStringValue(cmso,
					"title.beforeimg.height", locale);
			String moretext = configuration.getStringValue(cmso, "more.text",
					locale);
			String beforeimgbeforewidth = configuration.getStringValue(cmso,
					"beforeimg.before.width", locale);
			String contentimgwidth = configuration.getStringValue(cmso,
					"contentimg.width", locale);
			String contentimgheight = configuration.getStringValue(cmso,
					"contentimg.height", locale);
			String titleheight = configuration.getStringValue(cmso,
					"title.height", locale);
			String backtitwidth = configuration.getStringValue(cmso,
					"back.tit.width", locale);
			if (CmsStringUtil.isNotEmpty(contentimgwidth)) {
				cimgwidth = Integer.parseInt(contentimgwidth);

			}
			if (CmsStringUtil.isNotEmpty(contentimgheight)) {
				cimgheight = Integer.parseInt(contentimgheight);

			}

			if (CmsStringUtil.isNotEmpty(titlebeforeimgwidth)) {
				imgwidth = Integer.parseInt(titlebeforeimgwidth);

			}
			if (CmsStringUtil.isNotEmpty(titlebeforeimgheight)) {

				imgheight = Integer.parseInt(titlebeforeimgheight);
			}
			if (CmsStringUtil.isNotEmpty(titleheight)) {
				theight = Integer.parseInt(titleheight);
				theight = theight + 12;
			}

			if (!A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {
				if (!A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED
						.equals(getCssIndicator())) {
					html.append("<style type=\"text/css\">\n");
					html.append("<!--");
					html.append("/*" + foldertitle + " */\n");
					html.append(buildCSS(cms, cssfile));
					html.append("-->");
					html.append("</style>\n");
				}
				html.append("<!-- " + foldertitle + " begin-->\n");
				html.append("<table class=\"tmain" + fileName
						+ "\" style=\"border-collapse : collapse;\">\n");

				html.append("<tr>\n");
				html.append("<td valign=\"top\" height=\"" + theight
						+ "\" id=\"titletd" + fileName + "\" id=\"titletd"
						+ fileName + "\">\n");
				html.append("<table id=\"titletd"
						+ fileName
						+ "\" width=\"100%\" valign=\"top\" style=\"border-collapse : collapse;\">\n");
				html.append("<tr>\n");

				if (CmsStringUtil.isNotEmpty(titlebeforeimg)
						&& "true".equals(titlebeforeimg)) {
					String imguri = cms.link(titlebeforeimguri);

					if (CmsStringUtil.isNotEmpty(beforeimgbeforewidth)
							&& !"0".equals(beforeimgbeforewidth)) {
						int beforeimg = Integer.parseInt(beforeimgbeforewidth);
						html.append("<td width=\"" + beforeimg + "\">\n");
						html.append("</td>\n");
					}
					html.append("<td valign=\"top\">\n");
					html.append("<img id=\"titlebeforeimg" + fileName
							+ "\" src=\"");
					html.append(imguri);
					html.append("\" border=\"0\" width=\"" + imgwidth
							+ "\" height=\"" + imgheight + "\" />\n");
					html.append("</td>\n");
				}

				html.append("<td>\n");
				html.append("<div class=\"titlemargin" + fileName + "\">\n");
				html.append(foldertitle);
				html.append("</div>\n");
				html.append("</td>\n");
				if (CmsStringUtil.isNotEmpty(more) && "true".equals(more)) {
					if (CmsStringUtil.isNotEmpty(moretext)) {
						html.append("<td>\n");
						html.append("<a href=\"" + cms.link(folderpath)
								+ "\" target=\"_blank\" class=\"linkfontcolor"
								+ fileName + "\">\n");
						html.append("<div class=\"bold" + fileName + "\">\n");
						html.append(moretext);
						html.append("</div>\n");
						html.append("</a>\n");
						html.append("</td>\n");
					} else {
						html.append("<td>\n");
						html.append("<a href=\"" + cms.link(folderpath)
								+ "\" target=\"_blank\" class=\"linkfontcolor"
								+ fileName + "\">\n");
						html.append("<div class=\"bold" + fileName + "\">\n");
						html.append("���>>");
						html.append("</div>\n");
						html.append("</a>\n");
						html.append("</td>\n");
					}

				}
				if (CmsStringUtil.isNotEmpty(picmore) && "true".equals(picmore)) {
					String moreimg = cms.link(imgmore);
					html.append("<td>\n");
					html.append("<a href=\"" + cms.link(folderpath)
							+ "\" target=\"_blank\" class=\"linkfontcolor"
							+ fileName + "\">\n");
					html.append("<div class=\"bold" + fileName + "\">\n");
					html.append("<img id=\"img1" + fileName + "\" src=\"");
					html.append(moreimg);
					html.append("\" border=\"0\" />\n");
					html.append("</div>\n");
					html.append("</a>\n");
					html.append("</td>\n");
				}
				if (CmsStringUtil.isNotEmpty(backtitwidth)) {
					int backwidth = Integer.parseInt(backtitwidth);
					html.append("<td width=\"" + backwidth + "\">\n");
					html.append("</td>\n");
				}
				html.append("</tr>\n");
				html.append("</table>\n");
				html.append("</td>\n");
				html.append("</tr>\n");
				html.append("<tr valign=\"top\">\n");
				html.append("<td valign=\"top\">\n");
				html.append("<table width=\"100%\" valign=\"top\" style=\"border-collapse : collapse;\">\n");
				html.append("<tr valign=\"top\">\n");
				html.append("<td valign=\"top\">\n");
				html.append("<div align=\"center\">\n");
				html.append("<table id=\"tcontent" + fileName + "\" width=\""
						+ cimgwidth
						+ "\" style=\"border-collapse : collapse;\">\n");

				I_CmsXmlContentContainer container0;

				container0 = cmsx.contentload("singleFile", linkpath, true);

				while (container0.hasMoreResources()) {
					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container0, "FileLink");

					while (container1.hasMoreResources()) {
						String foldername = cmsx.contentshow(container1,
								"foldername");
						String filelink = cmsx.contentshow(container1,
								"FileLink");
						if (CmsStringUtil.isNotEmpty(foldername)) {
							html.append("<tr valign=\"top\" >\n");
							html.append("<td valign=\"top\" width=\""
									+ cimgwidth + "\">\n");
							html.append("<a href=\""
									+ cms.link(filelink)
									+ "\" target=\"_blank\" class=\"linktitcolor"
									+ fileName + "\">\n");
							html.append(foldername);
							html.append("</a>\n");
							html.append("</td>\n");
							html.append("</tr>\n");
							html.append("<tr valign=\"top\">\n");
							html.append("<td valign=\"top\">\n");
							html.append("<table width=\"100%\" valign=\"top\" style=\"border-collapse : collapse;\">\n");
							html.append("<tr valign=\"top\">\n");
							I_CmsXmlContentContainer container2 = cmsx
									.contentloop(container1, "FolderFile");
							while (container2.hasMoreResources()) {
								String folername = cmsx.contentshow(container2,
										"folername");
								String folerpath = cmsx.contentshow(container2,
										"folerpath");

								if (!folername.equals("")) {
									html.append("<td valign=\"top\">\n");
									html.append("<a href=\""
											+ cms.link(folerpath)
											+ "\" target=\"_blank\"class=\"link"
											+ fileName + "\">\n");
									html.append(folername);
									html.append("</a>\n");
									html.append("</td>\n");
								}

							}
							html.append("</tr>\n");
							html.append("</table>\n");
							html.append("</td>\n");
							html.append("</tr>\n");
						}
					}
				}
				html.append("</table>\n");
				html.append("</div>\n");
				html.append("</td>\n");
				html.append("</tr>\n");
				html.append("<tr >\n");
				html.append("<td >\n");
				html.append("<div align=\"center\">\n");
				html.append("<table width=\"" + cimgwidth
						+ "\" style=\"border-collapse : collapse;\">\n");
				I_CmsXmlContentContainer containeri;

				containeri = cmsx.contentload("singleFile", linkpath, true);

				while (containeri.hasMoreResources()) {
					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							containeri, "FileLink");
					while (container1.hasMoreResources()) {
						String folderimg = cmsx.contentshow(container1,
								"folderimg");
						String filelink = cmsx.contentshow(container1,
								"FileLink");
						if (!folderimg.equals("")) {
							String picurl = cms.link(folderimg);
							html.append("<tr valign=\"top\">\n");
							html.append("<td valign=\"top\">\n");
							html.append("<a href=\"" + cms.link(filelink)
									+ "\" target=\"_blank\" class=\"link"
									+ fileName + "\" >\n");
							html.append("<img  src=\"");
							html.append(picurl);
							html.append("\" border=\"0\" width=\"" + cimgwidth
									+ "\" height=\"" + cimgheight + "\" />\n");
							html.append("</a>\n");
							html.append("</td>\n");
							html.append("</tr>\n");
						}
					}
				}
				html.append("</table>\n");
				html.append("</div>\n");
				html.append("</td>\n");
				html.append("</tr>\n");
				html.append("</table>\n");
				html.append("</td>\n");
				html.append("</tr>\n");
				html.append("</table>\n");
				html.append("<!-- " + foldertitle + " end-->\n");
			}

			if (A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {
				html.append(buildCSS(cms, cssfile));
			}

		} catch (Exception e1) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}

}