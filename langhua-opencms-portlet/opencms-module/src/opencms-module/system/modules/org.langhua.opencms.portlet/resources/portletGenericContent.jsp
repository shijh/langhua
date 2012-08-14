<%@page buffer="none" session="false" pageEncoding="UTF-8"
	import="java.util.*,java.lang.*,java.text.*,org.opencms.util.*,org.opencms.jsp.*,org.opencms.file.*,org.opencms.lock.*,org.opencms.main.*,org.opencms.xml.*,org.opencms.xml.types.*,org.opencms.xml.content.*,org.opencms.i18n.*,org.dom4j.Element"%>
<%
	CmsJspActionElement cms = new CmsJspActionElement(pageContext,
			request, response);
	CmsJspXmlContentBean cmsx = new CmsJspXmlContentBean(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();
	String uuidString = "";
	try {
		String currentSite = OpenCms.getSiteManager().getCurrentSite(
				cms.getCmsObject()).getUrl();
		if (OpenCms.getSiteManager().getCurrentSite(cms.getCmsObject())
				.getSiteMatcher().equals(
						OpenCms.getSiteManager()
								.getWorkplaceSiteMatcher())) {
			currentSite = OpenCms.getSiteManager().getWorkplaceServer();
		}
		String currentSiteUrl = currentSite
				+ cms.getRequest().getContextPath()
				+ cms.getRequest().getServletPath();
		CmsFile configFile = cmso.readFile(fileUri,
				CmsResourceFilter.IGNORE_EXPIRATION);
		CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(
				cmso, configFile);
		List<Locale> locales = configuration.getLocales();
		if (!locales.contains(locale)) {
			for (Locale tempLocale : locales) {
				if (tempLocale.getLanguage().startsWith(
						locale.getLanguage().substring(0, 1))) {
					locale = tempLocale;
					break;
				}
			}
		}
		String defaultContent = configuration.getStringValue(cmso,
				"defaultContent", locale);
		String portletParameterName = configuration.getStringValue(
				cmso, "portletParameterName", locale);
		if (!CmsMessages.isUnknownKey(portletParameterName)
				&& CmsStringUtil
						.isNotEmptyOrWhitespaceOnly(portletParameterName)) {
			uuidString = request.getParameter(portletParameterName);
		} else {
			uuidString = request.getParameter("uuid");
		}
		String fileTitle = null;
		String subTitle = null;
		String author = null;
		String teaser = null;
		String text = null;
		String imageurl = null;
		String description = null;
		int source_id = 0;
		CmsResource source = null;

		if (CmsStringUtil.isNotEmpty(uuidString)) {

			CmsUUID uuid = new CmsUUID(uuidString);
			source = cmso.readResource(uuid);
		} else if (CmsStringUtil.isNotEmpty(defaultContent)) {
			source = cmso.readResource(defaultContent);
		}
		if (source.isFile()) {
			CmsFile configFile1 = cmso.readFile(source);
			CmsXmlContent configuration1 = CmsXmlContentFactory
					.unmarshal(cmso, configFile1);
			source_id = source.getTypeId();
			String linkurl = "";
			if (source_id == OpenCms.getResourceManager()
					.getResourceType("portlet_tab_config").getTypeId()) {
				fileTitle = configuration1.getStringValue(cmso,
						"portletTitle", locale);
			} else {
				if (source_id == 30) {
					fileTitle = configuration1.getStringValue(cmso,
							"Title", locale);
					subTitle = configuration1.getStringValue(cmso,
							"SubTitle", locale);
					author = configuration1.getStringValue(cmso,
							"Author", locale);
					text = configuration1.getStringValue(cmso, "Text",
							locale);
					try {
						imageurl = ((I_CmsXmlContentValue) configuration1
								.getValue("Image", locale))
								.getElement().element("Image").element(
										"link").element("target")
								.getText();
					} catch (Exception e) {
					}
				}
				if (source_id == 121) {
					fileTitle = configuration1.getStringValue(cmso,
							"Title", locale);
					subTitle = configuration1.getStringValue(cmso,
							"SubTitle", locale);
					author = configuration1.getStringValue(cmso,
							"Author", locale);
					try {
						text = ((I_CmsXmlContentValue) configuration1
								.getValue("Paragraph", locale))
								.getElement().element("Text").element("content").getText();
					} catch (Exception e) {
						//do nothing
					}
					try {
						imageurl = ((I_CmsXmlContentValue) configuration1
								.getValue("Paragraph", locale))
								.getElement().element("Image").element(
										"Image").element("link")
								.element("target").getText();
					} catch (Exception e) {
					}
				}
				if (source_id == 6) {
					fileTitle = cmso.readPropertyObject(source,
							"Title", true).getValue();
					description = cmso.readPropertyObject(source,
							"Description", true).getValue();
					imageurl = cmso.readPropertyObject(source,
							"NavImage", true).getValue();
					text = configuration1.getStringValue(cmso,
							"element", locale);
				}
			}
%><title><%=fileTitle%></title>
<%
	if (source_id == OpenCms.getResourceManager()
					.getResourceType("portlet_tab_config").getTypeId()) {
				List cmsXmlContentValues = configuration1.getValues(
						"portlet-tab", locale);
				if (cmsXmlContentValues != null) {
					for (int i = 0; i < cmsXmlContentValues.size(); i++) {
						Element element_portlet_tab = ((I_CmsXmlContentValue) cmsXmlContentValues
								.get(i)).getElement();
						Element element_portlet_tab_name = element_portlet_tab
								.element("TabName");
						Element element_portlet_tab_URL = element_portlet_tab
								.element("TabURL");
						if (element_portlet_tab_name != null) {
%><div><span name="tabName"><%=element_portlet_tab_name
													.getText()%></span> <%
 	}
 						if (element_portlet_tab_URL != null) {
 %><span name="tabURL"><%=element_portlet_tab_URL
															.getText()%></span></div>
<%
	}
					}
				}
			} else {
%><table width="100%" border="0" cellpadding="0" cellspacing="0">
	<tr>
		<td align="center">
		<div><font
			style="font-size: 24px; font-weight: bold; padding-top: 5px; padding-bottom: 5px;"><%=fileTitle%></font></div>
		</td>
	</tr>
	<%
		if (subTitle != null) {
	%>
	<tr>
		<td align="center">
		<div><font
			style="font-size: 12px; padding-top: 5px; padding-bottom: 5px;"><%=subTitle%></font></div>
		</td>
	</tr>
	<%
		}
	%>
	<tr>
		<td align="center">
		<div style="margin-top: 10px; margin-bottom: 5px;"><font
			style="font-size: 12px; padding-top: 5px; padding-bottom: 5px;"><%
			if (locale.getLanguage().startsWith("zh")) {
		%> 
		作者: 
		<%
			} else {
		%>Written by <%
			}
		%><%=author%></font></div>
		</td>
	</tr>
	<%
		if (imageurl != null) {
	%>
	<tr>
		<td align="center">
		<div style="margin-bottom: 5px;"><img
			src="<%=OpenCms.getLinkManager().getOnlineLink(
											cmso, imageurl)%>"
			alt="<%=fileTitle%>" /></div>
		</td>
	</tr>
	<%
		}
					if (description != null) {
	%>
	<tr>
		<td align="center">
		<div style="margin-bottom: 5px;"><font
			style="font-size: 11px; padding-top: 5px; padding-bottom: 5px;"><%=description%></font></div>
		</td>
	</tr>
	<%
		}
					if (text != null) {
	%>
	<tr>
		<td>
		<div><font
			style="word-break: break-all; text-indent: 2em; font-size: 14px; padding-top: 5px; padding-bottom: 5px; line-height: 18px;"><%=text%></font></div>
		</td>
	</tr>
	<%
		}
	%>
</table>
<%
	}
		}
	} catch (Exception e) {
		e.printStackTrace();
	}
%>

