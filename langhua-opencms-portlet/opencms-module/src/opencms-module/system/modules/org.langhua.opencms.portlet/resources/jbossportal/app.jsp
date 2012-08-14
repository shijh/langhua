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
<!DOCTYPE jboss-app PUBLIC
   "-//JBoss Portal//DTD JBoss Web Application 2.6//EN"
   "http://www.jboss.org/portal/dtd/jboss-app_2_6.dtd">
<%
List locales = configuration.getLocales();
if (locales != null && locales.size() > 0 && !locales.contains(locale)) {
    locale = (Locale) locales.get(0);
}
List values = configuration.getValues("jboss-app", locale);
if (values.size() > 0) {
%><%= ((I_CmsXmlContentValue) values.get(0)).getElement().asXML() %>
<%
}
%>
