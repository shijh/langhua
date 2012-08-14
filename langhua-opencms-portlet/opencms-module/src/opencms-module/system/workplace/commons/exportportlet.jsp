<%@ page import="org.opencms.workplace.commons.*,
                 org.opencms.workplace.CmsDialog,
                 org.langhua.opencms.portlet.*" %><%	
CmsExportPortlet wp = new CmsExportPortlet(pageContext, request, response);

switch (wp.getAction()) {

case CmsExportPortlet.ACTION_CANCEL:

	wp.actionCloseDialog();
break;
case CmsExportPortlet.ACTION_EXPORTPORTLET:

      wp.actionExportPortlet();

break;

case CmsExportPortlet.ACTION_DEFAULT:
default:
	wp.setParamAction(CmsExportPortlet.DIALOG_TYPE);

%>
<%= wp.htmlStart("help.explorer.contextmenu.export") %>
<%= wp.bodyStart("dialog", "onunload=\"top.closeTreeWin();\"") %>

<%= wp.dialogStart() %>
<%= wp.dialogContentStart(wp.getParamTitle()) %>
<%
boolean includeFiles = true;
%>
<%@ include file="includes/resourceinfo.txt" %>

<form name="main" action="<%= wp.getDialogUri() %>" method="post" class="nomargin" onsubmit="return submitAction('<%= wp.DIALOG_OK %>', null, 'main');">

<%= wp.paramsAsHidden() %>
<input type="hidden" name="<%= wp.PARAM_FRAMENAME %>" value="">


<table border="0" width="100%">
<tr>
	<td style="white-space: nowrap;" unselectable="on"><%= wp.key(org.langhua.opencms.portlet.Messages.GUI_EXPORT_PORTLET_WARFILENAME_0) %></td>
	<td class="maxwidth"><input name="<%= wp.PARAM_WARNAME %>" type="text" value="<%= wp.getCurrentResourceName() %>.war" class="maxwidth"></td>
	
</tr>
<tr>
	<td style="white-space: nowrap;" unselectable="on"><%= wp.key(org.langhua.opencms.portlet.Messages.GUI_EXPORT_PORTLET_FOLDERTO_0) %></td>
	<td class="maxwidth"><input name="<%= wp.PARAM_RFSPATH %>" type="text" value="" class="maxwidth" /></td>	
</tr>


</table>
<%= wp.dialogContentEnd() %>
<%= wp.dialogButtonsOkCancel() %>


</form>

<%= wp.dialogEnd() %>
<%= wp.bodyEnd() %>
<%= wp.htmlEnd() %>
<%
} 

%>
