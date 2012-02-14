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

import java.util.Iterator;
import java.util.List;
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
 * &lt;%ocms:verticalcolumnnavigation &nbsp linkpath="/taglib/LinkSrc" &nbsp
 * &nbsp cssfile="/taglib/CssSrc" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * 
 * cssfile : introduce css file.<br>
 * 
 * Image name: VerticalColNav-display.PNG<br>
 * 
 */
public class VerticalColumnNavigation extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog
			.getLog(VerticalColumnNavigation.class);

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
		Locale locale = cms.getRequestContext().getLocale();
		String name = "";
		String uri = "";
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
			}
			String mainwidth = configuration.getStringValue(cmso, "main.width",
					locale);
			String mainheight = configuration.getStringValue(cmso,
					"main.height", locale);
			String titleheight = configuration.getStringValue(cmso,
					"title.height", locale);
			String titlebgimg = configuration.getStringValue(cmso,
					"title.bgimg", locale);
			if (CmsStringUtil.isNotEmpty(titlebgimg)) {
				titlebgimg = cms.link(titlebgimg);
			}
			int tw = Integer.parseInt(mainwidth) - 4;
			String begintitleempty = configuration.getStringValue(cmso,
					"begin.title.empty", locale);
			String titlelineheight = configuration.getStringValue(cmso,
					"title.line.height", locale);
			String titlemorewidth = configuration.getStringValue(cmso,
					"title.more.width", locale);
			int trw = tw - Integer.parseInt(begintitleempty)
					- Integer.parseInt(titlemorewidth);
			String titledownheight = configuration.getStringValue(cmso,
					"title.down.height", locale);
			String contentbeightempty = configuration.getStringValue(cmso,
					"content.beight.empty", locale);
			String contentbackempty = configuration.getStringValue(cmso,
					"content.back.empty", locale);
			int contentwidth = Integer.parseInt(mainwidth) - 2
					- Integer.parseInt(contentbeightempty)
					- Integer.parseInt(contentbackempty);
			String contenttextbeightempty = configuration.getStringValue(cmso,
					"content.text.beightempty", locale);
			String contenttextheight = configuration.getStringValue(cmso,
					"content.text.height", locale);
			String contenttextwidth = configuration.getStringValue(cmso,
					"content.text.width", locale);
			String contenttextbgimg = configuration.getStringValue(cmso,
					"content.text.bgimg", locale);
			String contenttextempty = configuration.getStringValue(cmso,
					"content.text.empty", locale);
			String contentjiange = configuration.getStringValue(cmso,
					"content.jiange", locale);
			String mainborderwidth = configuration.getStringValue(cmso,
					"main.border.width", locale);
			String more = configuration.getStringValue(cmso, "more", locale);
			String moreimg = configuration.getStringValue(cmso, "more.img",
					locale);
			String moreimgwidth = configuration.getStringValue(cmso,
					"more.img.width", locale);
			String moreimgheight = configuration.getStringValue(cmso,
					"more.img.height", locale);

			int contentxwidth = Integer.parseInt(contenttextwidth) - 4;
			int textwidth = contentxwidth - Integer.parseInt(contenttextempty);
			int textbackempty = contentwidth
					- Integer.parseInt(contenttextbeightempty)
					- Integer.parseInt(contenttextwidth);
			if (CmsStringUtil.isNotEmpty(contenttextbgimg)) {
				contenttextbgimg = cms.link(contenttextbgimg);
			}
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
				html.append("<table width=\""
						+ mainwidth
						+ "\" height=\""
						+ mainheight
						+ "\"  border=\"0\" cellspacing=\"0\" cellpadding=\"0\"  class=\"huixian"
						+ fileName + "\">\n");
				html.append("<tr>\n");
				html.append("<td height=\""
						+ titleheight
						+ "\" colspan=\"3\" align=\"left\" background=\""
						+ titlebgimg
						+ "\"><table width=\""
						+ tw
						+ "\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n");
				html.append("<tr>\n");
				html.append("<td width=\"" + begintitleempty + "\"></td>\n");
				html.append("<td width=\"" + trw + "\" height=\""
						+ titlelineheight + "\"><span class=\"STYLE7"
						+ fileName + "\">" + name + "</span></td>\n");
				if ("false".equals(more)) {
					html.append("<td width=\"" + titlemorewidth
							+ "\"><span class=\"STYLE1" + fileName
							+ "\"><a target=\"_blank\" href=\"" + uri
							+ "\" >���&nbsp;&gt;&gt;</a></span></td>\n");
				} else {
					html.append("<td width=\"" + titlemorewidth
							+ "\"><span class=\"STYLE1" + fileName
							+ "\"><a target=\"_blank\" href=\"" + uri
							+ "\" ><img src=" + cms.link(moreimg) + " width=\""
							+ moreimgwidth + "\" height=\"" + moreimgheight
							+ "\" border=\"0\"/></a></span></td>\n");
				}
				html.append("</tr>\n");
				html.append("</table></td>\n");
				html.append("</tr>\n");
				html.append("<tr>\n");
				html.append("<td height=\"" + titledownheight
						+ "\" colspan=\"3\"></td>\n");
				html.append("</tr>\n");
				html.append("<tr>\n");
				html.append("<td width=\"" + contentbeightempty + "\"></td>\n");
				html.append("<td width=\""
						+ contentwidth
						+ "\" valign=\"top\"><table width=\""
						+ contentwidth
						+ "\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n");
				int n = 1;
				I_CmsXmlContentContainer container1;
				container1 = cmsx.contentload("singleFile", linkpath, true);
				while (container1.hasMoreResources()) {
					I_CmsXmlContentContainer containerz = cmsx.contentloop(
							container1, "Program");
					while (containerz.hasMoreResources()) {
						String number = cmsx.contentshow(containerz, "Number");
						String program = cmsx
								.contentshow(containerz, "Program");
						String filetype = cmsx.contentshow(containerz,
								"Filetype");
						int id = OpenCms.getResourceManager()
								.getResourceType(filetype).getTypeId();
						CmsResourceFilter filter = CmsResourceFilter.DEFAULT_FILES
								.addRequireFile().addRequireType(id);
						if (CmsStringUtil.isNotEmpty(number)) {
							filter = A_LanghuaTag.addTopLatest(filter, number);
						}
						List<CmsResource> list = cmso.readResources(program,
								filter, true);
						if (list.size() != 0) {
							Iterator<CmsResource> iterator = list.iterator();
							for (; iterator.hasNext(); n++) {
								if (!A_LanghuaTag.isPerformanceUsed()) {
									if (CmsStringUtil.isNotEmpty(number)) {
										int numb1 = Integer.parseInt(number) + 1;
										if (n == numb1)
											break;
									}
								}
								CmsResource source = (CmsResource) iterator
										.next();
								String rootpath = source.getRootPath();
								String path = cms.getRequestContext()
										.removeSiteRoot(rootpath);
								String filetitle = "";
								boolean result = cmso.existsResource(path);

								if (result) {
									filetitle = cms.property("Title", path);
								}
								if (CmsStringUtil.isEmpty(filetitle)) {

									filetitle = source.getName();
								}

								html.append("</tr>\n");
								html.append("<td width=\""
										+ contenttextbeightempty + "\"></td>\n");
								html.append("<td width=\""
										+ contenttextwidth
										+ "\" height=\""
										+ contenttextheight
										+ "\" background=\""
										+ contenttextbgimg
										+ "\"><table width=\""
										+ contentxwidth
										+ "\" border=\"0\" cellspacing=\"0\" cellpadding=\"0\">\n");
								html.append("<tr>\n");
								html.append("<td width=\"" + contenttextempty
										+ "\"></td>\n");
								html.append("<td width=\""
										+ textwidth
										+ "\" align=\"left\"><span class=\"STYLE1"
										+ fileName
										+ "\"><a target=\"_blank\" href=\""
										+ cms.link(path) + "\">" + filetitle
										+ "</a></span></td>\n");
								html.append("</tr>\n");
								html.append("</table></td>\n");
								html.append("<td width=\"" + textbackempty
										+ "\"></td>\n");
								html.append("</tr>\n");
								html.append("<tr>\n");
								html.append("<td height=\"" + contentjiange
										+ "\" colspan=\"3\"></td>\n");
								html.append("</tr>\n");
							}
						}
					}
				}
				html.append("</table></td>\n");
				html.append("<td width=\"" + contentbackempty + "\"></td>\n");
				html.append("</tr>\n");
				html.append("<tr>\n");
				int h = Integer.parseInt(mainheight) - n
						* Integer.parseInt(contenttextheight)
						- Integer.parseInt(titleheight)
						- Integer.parseInt(titledownheight) - n
						* Integer.parseInt(contentjiange) - 2
						* Integer.parseInt(mainborderwidth);
				html.append("<td height=\"" + h + "\" colspan=\"3\"></td>\n");
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