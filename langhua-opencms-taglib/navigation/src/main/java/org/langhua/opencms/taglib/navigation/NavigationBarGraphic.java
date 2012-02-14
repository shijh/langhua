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

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
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
 * &lt;%@ taglib &nbsp; prefix="ocms" &nbsp;
 * uri="http://www.langhua.cn/taglib/display"%&gt;<br>
 * 
 * &lt;%ocms:navigationbargraphic &nbsp; linkpath="/home/link/easy" &nbsp;
 * cssfile="/home/css/test" /&gt;<br>
 * 
 * linkpath : this is taglib's content.<br>
 * cssfile: the css config
 * 
 * Image name: Navigation-display.PNG<br>
 * 
 */
public class NavigationBarGraphic extends A_LanghuaTag {

	private static final long serialVersionUID = 1L;

	private static final Log LOG = CmsLog.getLog(NavigationBarGraphic.class);

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

	public class TitleFont {
		String titlefont, path;

		public String getTitlefont() {
			return titlefont;
		}

		public void setTitlefont(String titlefont) {
			this.titlefont = titlefont;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

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
		String folderpath1 = "";
		String folderpath2 = "";
		int mwidth = 0;
		int iwidth = 0;
		int fwidth = 0;
		int twidth = 0;
		int fs = 0;
		int zf = 0;
		boolean have = false;
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
			String mainwidth = configuration.getStringValue(obj, "main.width",
					locale);
			String imgwidth = configuration.getStringValue(obj, "img.width",
					locale);
			String trmargin = configuration.getStringValue(obj, "tr.margin",
					locale);
			String fontsize = configuration.getStringValue(obj, "font.size",
					locale);

			String imguprowbgimage = configuration.getStringValue(obj,
					"img.uprow.bgimage", locale);
			String imgdownrowbgimage = configuration.getStringValue(obj,
					"img.downrow.bgimage", locale);
			List lt = new ArrayList();

			I_CmsXmlContentContainer container;

			container = cmsx.contentload("singleFile", linkpath, true);

			while (container.hasMoreResources()) {

				I_CmsXmlContentContainer container1 = cmsx.contentloop(
						container, "ProgramLink");

				for (int number = 0; container1.hasMoreResources() && number < 2; number++) {

					if (number == 0) {
						folderpath1 = cmsx.contentshow(container1,
								"ProgramLink");
					} else {
						folderpath2 = cmsx.contentshow(container1,
								"ProgramLink");
					}
				}
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
				html.append("<div align=\"center\">\n");
				html.append("<div class=\"content" + fileName + "\">\n");
				html.append("<div class=\"img" + fileName + "\">");
				html.append("</div>");
				if (CmsStringUtil.isNotEmpty(mainwidth)
						&& CmsStringUtil.isNotEmpty(imgwidth)) {
					mwidth = Integer.parseInt(mainwidth);
					iwidth = Integer.parseInt(imgwidth);
					fwidth = mwidth - iwidth;
				}
				if (CmsStringUtil.isNotEmpty(trmargin)) {
					twidth = Integer.parseInt(trmargin);

				}
				if (CmsStringUtil.isNotEmpty(fontsize)) {
					fs = Integer.parseInt(fontsize);

				}
				if (CmsStringUtil.isEmpty(imguprowbgimage)) {
					html.append("<div class=\"uprow" + fileName + "\" >");
				} else {
					String uprowuri = cms.link(imguprowbgimage);
					html
							.append("<div class=\"uprow" + fileName
									+ "\" style=\"background:url(" + uprowuri
									+ ");\">");

				}
				if (CmsStringUtil.isNotEmpty(folderpath1)) {

					List<CmsJspNavElement> list = new CmsJspNavBuilder(cms.getCmsObject()).getNavigationForFolder(folderpath1);
					for (int j = 0; j < list.size(); j++) {
						CmsJspNavElement nav = (CmsJspNavElement) list.get(j);
						String ntitle = nav.getNavText();
						String npath = cms.link(nav.getResourceName());
						int nl = ntitle.length();
						nl = nl * fs;
						zf = zf + twidth + nl;
						if (zf > fwidth && (fwidth != 0)) {
							have = true;
						}
						if (have) {
							TitleFont titfont = new TitleFont();
							titfont.setTitlefont(ntitle);
							titfont.setPath(npath);
							lt.add(titfont);

						} else {
							html.append("<div class=\"tr" + fileName + "\">");
							html.append("<a href=\"" + npath
									+ "\"  class=\"uplink" + fileName + "\">");
							html.append(ntitle);
							html.append("</a>");
							html.append("</div>");
						}

					}
				}

				I_CmsXmlContentContainer container0;

				container0 = cmsx.contentload("singleFile", linkpath, true);

				while (container0.hasMoreResources()) {

					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container0, "SuperLink");

					while (container1.hasMoreResources()) {
						String description = cmsx.contentshow(container1,
								"Description");
						String uri = cmsx.contentshow(container1, "SuperLink");
						uri = cms.getRequestContext().removeSiteRoot(uri);
						String id = cmsx.contentshow(container1, "ID");
						if ("1".equals(id)) {
							html.append("<div class=\"tr" + fileName + "\">");
							html.append("<a href=\"" + uri
									+ "\" class=\"uplink" + fileName + "\">\n");
							html.append(description);
							html.append("</a>\n");
							html.append("</div>");
						}
					}
				}
				html.append("</div>");
				if (CmsStringUtil.isEmpty(imgdownrowbgimage)) {
					html.append("<div class=\"downrow" + fileName + "\" >");
				} else {
					String downrowuri = cms.link(imgdownrowbgimage);
					html.append("<div class=\"downrow" + fileName
							+ "\" style=\"background:url(" + downrowuri
							+ ");\">");
				}
				ListIterator iterator = lt.listIterator();
				if (have) {
					while (iterator.hasNext()) {
						TitleFont titfont = new TitleFont();
						titfont = (TitleFont) iterator.next();

						html.append("<div class=\"tr" + fileName + "\">");
						html.append("<a href=\"" + titfont.getPath()
								+ "\"  class=\"downlink" + fileName + "\">");
						html.append(titfont.getTitlefont());
						html.append("</a>");
						html.append("</div>");
					}

				}
				if (CmsStringUtil.isNotEmpty(folderpath2)) {
					List<CmsJspNavElement> list = new CmsJspNavBuilder(cms.getCmsObject()).getNavigationForFolder(folderpath2);
					for (int j = 0; j < list.size(); j++) {
						CmsJspNavElement nav = (CmsJspNavElement) list.get(j);
						html.append("<div class=\"tr" + fileName + "\">");
						html.append("<a href=\""
								+ cms.link(nav.getResourceName())
								+ "\"  class=\"downlink" + fileName + "\">");
						html.append(nav.getNavText());
						html.append("</a>");
						html.append("</div>");
					}
				}
				I_CmsXmlContentContainer container3;

				container3 = cmsx.contentload("singleFile", linkpath, true);

				while (container3.hasMoreResources()) {

					I_CmsXmlContentContainer container1 = cmsx.contentloop(
							container3, "SuperLink");

					while (container1.hasMoreResources()) {
						String description = cmsx.contentshow(container1,
								"Description");
						String uri = cmsx.contentshow(container1, "SuperLink");
						String id = cmsx.contentshow(container1, "ID");
						if ("2".equals(id)) {
							html.append("<div class=\"tr" + fileName + "\">");
							html.append("<a href=\"" + uri
									+ "\" class=\"downlink" + fileName
									+ "\">\n");
							html.append(description);
							html.append("</a>\n");
							html.append("</div>");
						}

					}
				}

				html.append("</div>");
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
