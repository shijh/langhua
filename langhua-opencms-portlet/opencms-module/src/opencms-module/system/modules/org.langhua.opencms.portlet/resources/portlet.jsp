<%@page buffer="none" session="false" pageEncoding="UTF-8"
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
List locales = configuration.getLocales();
if (locales != null && locales.size() > 0 && !locales.contains(locale)) {
    locale = (Locale) locales.get(0);
}
%><?xml version="1.0" encoding="UTF-8"?>
<portlet-app xmlns="http://java.sun.com/xml/ns/portlet/portlet-app_1_0.xsd"
             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
             xsi:schemaLocation="http://java.sun.com/xml/ns/portlet/portlet-app_1_0.xsd http://java.sun.com/xml/ns/portlet/portlet-app_1_0.xsd"
             version="1.0">
<%
List values = configuration.getValues("portlet", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("custom-portlet-mode", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("custom-window-state", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("user-attribute", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}

values = configuration.getValues("security-constraint", locale);
for (int i=0; i<values.size(); i++) {
%><%= ((I_CmsXmlContentValue) values.get(i)).getElement().asXML() %>
<%
}
%></portlet-app>
