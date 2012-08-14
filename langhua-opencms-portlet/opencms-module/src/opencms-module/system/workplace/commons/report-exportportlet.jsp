<%@ page import="org.langhua.opencms.portlet.*"%>
<%
CmsPortletExportReport wp = new CmsPortletExportReport(pageContext, request, response);
wp.displayReport();
%>