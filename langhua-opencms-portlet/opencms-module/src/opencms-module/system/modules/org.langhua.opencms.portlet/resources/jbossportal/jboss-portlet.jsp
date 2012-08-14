<%@page buffer="none" session="false" 
		import="java.util.*, 
			java.lang.*,
			java.text.*,
			org.opencms.util.*,
			org.opencms.jsp.*,
			org.opencms.file.*,
			org.opencms.lock.*,
			org.opencms.main.*,
			org.opencms.xml.*,
   			org.opencms.xml.types.*,
            org.opencms.xml.content.*" %><%
CmsJspActionElement cms = new CmsJspActionElement(pageContext, request, response);
CmsObject cmso = cms.getCmsObject();
Locale locale = cms.getRequestContext().getLocale();
String fileUri = cms.getRequestContext().getUri();

CmsFile configFile = cmso.readFile(fileUri, CmsResourceFilter.IGNORE_EXPIRATION);
CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso, configFile);
%><?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE portlet-app PUBLIC
   "-//JBoss Portal//DTD JBoss Portlet 2.6//EN"
   "http://www.jboss.org/portal/dtd/jboss-portlet_2_6.dtd">
<portlet-app>
<%
List locales = configuration.getLocales();
if (locales != null && locales.size() > 0 && !locales.contains(locale)) {
    locale = (Locale) locales.get(0);
}
List values = configuration.getValues("remotable", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("portlet", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("service", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}
%></portlet-app>
