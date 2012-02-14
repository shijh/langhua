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
import org.opencms.main.*;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
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
 * &lt;%ocms:iconnavigation &nbsp linkpath="/taglib/LinkSrc" &nbsp &nbsp
 * cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * 
 * Image name: PicNewsTag-display.PNG<br>
 * 
 */
public class IconNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(IconNavigation.class);

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
		String name = "";
		String uri = "";
		int numb = 0;
		Locale locale = cms.getRequestContext().getLocale();

		try {
			String cssfile = cms.getRequestContext().removeSiteRoot(
					getCssFile());
			String linkpath = cms.getRequestContext().removeSiteRoot(
					getLinkFile());

			cmso = cms.getCmsObject();
			CmsFile config = cmso.readFile(cssfile,
					CmsResourceFilter.IGNORE_EXPIRATION);
			CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
					config);
			String fileName = config.getName();
			I_CmsXmlContentContainer container;
			container = cmsx.contentload("singleFile", linkpath, true);
			while (container.hasMoreResources()) {
				name = cmsx.contentshow(container, "Title");
				uri = cmsx.contentshow(container, "uri");
				if (CmsStringUtil.isNotEmpty(uri)) {
					uri = cms.link(uri);
				}
				I_CmsXmlContentContainer containerz1 = cmsx.contentloop(
						container, "text");
				while (containerz1.hasMoreResources()) {
					numb++;
				}
			}

			String mainwidth = configuration.getStringValue(cmso, "main.width",
					locale);
			String titlemore = configuration.getStringValue(cmso, "title.more",
					locale);
			String contenttopfontsize = configuration.getStringValue(cmso,
					"content.top.fontsize", locale);
			String contenttopbeforewidth = configuration.getStringValue(cmso,
					"content.top.before.width", locale);
			String morepic = configuration.getStringValue(cmso, "more.pic",
					locale);
			String morepicwidth = configuration.getStringValue(cmso,
					"more.pic.width", locale);
			String morepicheight = configuration.getStringValue(cmso,
					"more.pic.height", locale);
			String num = configuration.getStringValue(cmso, "number", locale);
			String lineheight = configuration.getStringValue(cmso,
					"lineheight", locale);

			int nu = 1;
			if (CmsStringUtil.isNotEmpty(num)) {
				nu = Integer.parseInt(num);
			}
			if (CmsStringUtil.isEmpty(getCssIndicator())) {
				html.append("<style type=\"text/css\">\n");
				html.append("<!--");
				html.append(buildCSS(cms, cssfile));
				html.append(".newsRedInfo" + fileName + " ul h2{\n");
				html.append("font-size:" + contenttopfontsize + "px;\n");
				html.append("margin:0 auto;\n");
				html.append("padding:0px 0px 0px " + contenttopbeforewidth
						+ "px;\n");
				html.append("text-align:left;\n");
				html.append("}\n");
				html.append(".newsRedInfo" + fileName
						+ " ul h2 a:link,.newsRedInfo" + fileName
						+ " ul h2 a:visited{ text-decoration:none;}\n");
				html.append(".newsRedInfo" + fileName
						+ " ul h2 a:hover{text-decoration:underline;}\n");
				html.append("-->");
				html.append("</style>\n");
			}
			if (CmsStringUtil.isNotEmpty(getCssIndicator())
					&& A_LanghuaTag.CSS_INDICATOR_NOSTYLE.equals(getCssIndicator())) {

				html.append(buildCSS(cms, cssfile));
				html.append(".newsRedInfo" + fileName + " ul h2{\n");
				html.append("font-size:" + contenttopfontsize + "px;\n");
				html.append("margin:0 auto;\n");
				html.append("padding:0px 0px 0px " + contenttopbeforewidth
						+ "px;\n");
				html.append("text-align:left;\n");
				html.append("}\n");
				html.append(".newsRedInfo" + fileName
						+ " ul h2 a:link,.newsRedInfo" + fileName
						+ " ul h2 a:visited{ text-decoration:none;}\n");
				html.append(".newsRedInfo" + fileName
						+ " ul h2 a:hover{text-decoration:underline;}\n");
			}

			if (CmsStringUtil.isEmpty(getCssIndicator())
					|| A_LanghuaTag.CSS_INDICATOR_CUSTOMIZED.equals(getCssIndicator())) {

				html
						.append("<table width=\""
								+ mainwidth
								+ "\"   border=\"0\" cellspacing=\"0\" cellpadding=\"0\" >\n");
				html.append("<tr>\n");
				html.append("<td  valign=\"top\">\n");
				html.append("<div class=\"newsRed" + fileName + "\">\n");
				html.append("<div class=\"newsRedCard" + fileName + "\">\n");
				html.append("<div class=\"newsRedCardName" + fileName + "\">"
						+ name + "</div>\n");
				if (CmsStringUtil.isEmpty(morepic)) {
					html.append("<div class=\"newsRedMore" + fileName
							+ "\"><a target=\"_blank\" href=\"" + uri + "\" >"
							+ titlemore + "</a></div>\n");
					html.append("</div>\n");
				} else {
					html.append("<div class=\"newsRedMore1" + fileName
							+ "\"><a target=\"_blank\" href=\"" + uri
							+ "\" ><img src=\"" + cms.link(morepic)
							+ "\" width=\"" + morepicwidth + "\" height=\""
							+ morepicheight + "\"  border=\"0\"/></a></div>\n");
					html.append("</div>\n");
				}
				html.append("<div class=\"newsRedInfo" + fileName + "\">\n");

				I_CmsXmlContentContainer container1;
				container1 = cmsx.contentload("singleFile", linkpath, true);
				while (container1.hasMoreResources()) {
					I_CmsXmlContentContainer containerz = cmsx.contentloop(
							container1, "text");
					int n = 1;
					for (; containerz.hasMoreResources(); n++) {
						String icon = cmsx.contentshow(containerz, "icon");
						String filetitle = cmsx
								.contentshow(containerz, "title");
						String path = cmsx.contentshow(containerz, "uri");
						String iconwidth = cmsx.contentshow(containerz,
								"icon.width");
						String iconheight = cmsx.contentshow(containerz,
								"icon.height");
						if (n == 1) {
							html
									.append("<table width=\"98%\"   border=\"0\" cellspacing=\"0\" cellpadding=\"0\" ><tr><td height=\""
											+ lineheight
											+ "\"></td><td height=\""
											+ lineheight + "\"></td></tr><tr>");
						}
						if (CmsStringUtil.isNotEmpty(icon)) {
							html
									.append("<td height=\""
											+ lineheight
											+ "\" width=\""
											+ 98
											/ nu
											+ "%\" valign=\"middle\" align=\"center\" class=\"newsRedInfo1"
											+ fileName + "\"><img src=\""
											+ cms.link(icon) + "\" width=\""
											+ iconwidth + "\" height=\""
											+ iconheight + "\" /><a href=\""
											+ cms.link(path)
											+ "\" target=\"_blank\">"
											+ filetitle + "</a></td>\n");

						} else {
							html
									.append("<td height=\""
											+ lineheight
											+ "\" width=\""
											+ 98
											/ nu
											+ "%\" valign=\"middle\" align=\"center\" class=\"newsRedInfo1"
											+ fileName + "\"><a href=\""
											+ cms.link(path)
											+ "\" target=\"_blank\">"
											+ filetitle + "</a></td>\n");
						}
						if (n % nu == 0 && numb != n) {
							html
									.append("</tr></table><table width=\"98%\"   border=\"0\" cellspacing=\"0\" cellpadding=\"0\" ><tr><td height=\""
											+ lineheight
											+ "\"></td><td height=\""
											+ lineheight + "\"></td></tr><tr>");
						}
						if (n % nu == 0 && numb == n) {
							html.append("</tr></table>");
						}
						if (n % nu != 0 && numb == n) {
							for (int j = 1; j <= nu - n % nu; j++) {
								html.append("<td width=\"" + 98 / nu
										+ "%\" height=\"" + lineheight
										+ "\">&nbsp;</td>");
							}
							html.append("</tr></table>");
						}
					}

				}
				html.append("</div>\n");
				html.append("</div>\n");
				html.append("</td></tr></table>\n");
			}

		} catch (Exception e1) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e1);
			}
		}

		return html.toString();
	}

}