<%@ taglib uri="jbpmTags" prefix="jbpm" %>
<%@ page import="java.lang.*" %>
<%
	String taskInstanceId = request.getParameter("taskInstanceId");
	String tokenInstanceId = request.getParameter("tokenInstanceId");
	String processDefinitionId = request.getParameter("processDefinitionId");
%>
<table cellspacing="0" cellpadding="0" border="0">
<tr>
<td valign="top">
  <jbpm:processimage task="<%= Long.parseLong(taskInstanceId) %>"/> 
</td>
</tr>
</table>

