<%@page buffer="none" session="false"
	import="java.util.*,java.lang.*,java.text.*,org.opencms.util.*,org.opencms.jsp.*,org.opencms.file.*,org.opencms.lock.*,org.opencms.main.*,org.opencms.xml.*,org.opencms.xml.types.*,org.opencms.xml.content.*,org.dom4j.Element"%><%
	CmsJspActionElement cms = new CmsJspActionElement(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();
	CmsFile configFile = cmso.readFile(fileUri,
			CmsResourceFilter.IGNORE_EXPIRATION);
	CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
			configFile);
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
%><?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE deployments PUBLIC
   "-//JBoss Portal//DTD Portlet Instances 2.6//EN"
   "http://www.jboss.org/portal/dtd/portlet-instances_2_6.dtd">
<deployments>
<%
	List locales = configuration.getLocales();
	if (locales != null && locales.size() > 0
			&& !locales.contains(locale)) {
		locale = (Locale) locales.get(0);
	}
	List values = configuration.getValues("deployment", locale);
	for (int i = 0; i < values.size(); i++) {
		Element element = ((I_CmsXmlContentValue) values.get(i))
				.getElement();
		Element instanceElement = element.element("instance");
		Element preferencesElement = instanceElement
				.element("preferences");
		if (preferencesElement != null) {
			List preferences = preferencesElement
					.elements("preference");
			for (int j = 0; j < preferences.size(); j++) {
				Element preference = (Element) preferences.get(j);
				if (preference != null) {
					String pName = preference.elementText("name");
					if (pName.equals("url")
							&& preference.element("value") != null
							&& !preference.element("value").getText()
									.equals("")) {
						String pValue = preference.elementText("value");
						if(pValue.indexOf("http://")==-1 && pValue.indexOf("https://")==-1){
							preference.element("value").node(0).setText(currentSiteUrl+preference.elementText("value"));
						}
					}
				}
			}
		}
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
						.asXML()%>
<%
	}
%></deployments>