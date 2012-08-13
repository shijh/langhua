<%@ page language="java" extends="org.jboss.portal.core.servlet.jsp.PortalJsp" %>
<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<%@ taglib uri="/WEB-INF/portal-lib.tld" prefix="n" %>
<%@ page isELIgnored="false" %>

<%@ page import="org.jboss.portal.core.cms.ui.admin.CMSAdminConstants,
                 java.util.Collection" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.StringTokenizer" %>

<portlet:defineObjects/>
<link rel="stylesheet" type="text/css" href="/portal-admin/css/style.css" media="screen"/>
<div class="admin-ui">
<br/>
<h3 class="sectionTitle-blue">
   ${n:i18n("CMS_MAIN_USE")}
</h3>
<div class=" cms-tab-container">
<%
   String sCurrPath = (String)request.getAttribute("currpath");
   Collection pendingQueue = (Collection)request.getAttribute("pendingQueue");   
   String exception = request.getParameter("exception");

   String rowClass = "portlet-section-body";
%>


<!-- Currently browsing -->
<ul class="objectpath">
   <li class="pathItem"><a href="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_MAIN %>"/>
      <portlet:param name="path" value="/"/>
      </portlet:renderURL>">${n:i18n("CMS_HOME")}</a></li>


   <%
      StringTokenizer parser = new StringTokenizer(sCurrPath, "/");
      String sPathBuilder = "";
      while (parser.hasMoreTokens())
      {
         String sPathChunk = parser.nextToken();
         sPathBuilder += "/" + sPathChunk;
         if (parser.hasMoreTokens())
         {
   %>
   <li class="pathSeperator"><img src="/portal-admin/img/pathSeparator.png" alt=">"></li>
   <li class="pathItem"><a href="
<portlet:renderURL>
   <portlet:param name="op" value="<%= CMSAdminConstants.OP_MAIN %>"/>
   <portlet:param name="path" value="<%= sPathBuilder %>"/>
</portlet:renderURL>
"><%= sPathChunk %>
   </a>
   </li>
   <%
   }
   else
   {
   %>
   <li class="pathSeperator"><img src="/portal-admin/img/pathSeparator.png" alt=">"></li>
   <li class="selected"><%= sPathChunk %>
   </li>
   <%
         }
      }
   %>
</ul>
<br/>
<!-- show any errors here -->
<%
   if (exception != null && exception.trim().length() > 0)
   {
%>
<table width="100%">
   <th colspan="2"><h3 class="sectionTitle-blue">${n:i18n("CMS_ERROR")}:</h3></th>
   <tr colspan="2" align="center">
      <td colspan="2">
         <font color="red">
            <%=exception%>
         </font>
      </td>
   </tr>
</table>
<br/><br/>
<input class="portlet-form-button" type="button" value="${n:i18n("CMS_CANCEL")}" name="cancel"
       onclick="window.location='<portlet:renderURL><portlet:param name="op" value="<%= CMSAdminConstants.OP_MAIN %>"/><portlet:param name="path" value="/"/></portlet:renderURL>'">
<%}%>

<!-- the table listing the pending queue items -->
<div align="center"><font class="portlet-font-dim"><b>${n:i18n("CMS_PENDING_APPROVAL_QUEUE")}</b></font></div>
<br/>


<!-- Preview of Content being approved -->
<%
	String pendingPreviewContent = (String)request.getAttribute("pendingPreviewContent");
	String contentBeingPreviewed = "-1"; 
	if(pendingPreviewContent == null)
	{
		pendingPreviewContent = "";
	} 
	String sDocBase = (String)request.getAttribute("document_base_url");
%>

<%if(sDocBase != null){%>
<%
	String pid = request.getParameter("pid");
	String path = request.getParameter("path");
    String sCSSURL = (String)request.getAttribute("css_url");      
    contentBeingPreviewed = pid;    
%>
<!-- tinyMCE -->
<script language="javascript" type="text/javascript"
        src="<%= renderRequest.getContextPath() + CMSAdminConstants.DEFAULT_IMAGES_PATH %>/tiny_mce/tiny_mce_src.js"></script>
<script language="javascript" type="text/javascript">
   tinyMCE.init({
   mode : "textareas",
   theme : "advanced",
   theme_advanced_disable : "styleselect",
   plugins : "advhr,advimage,advlink,emotions,iespell,insertdatetime,preview,zoom,flash",
   theme_advanced_buttons1_add : "fontselect,fontsizeselect,forecolor",
   theme_advanced_buttons2_add_before: "cut,copy,paste,separator",
   theme_advanced_buttons2_add : "separator,insertdate,inserttime,preview,zoom",
   theme_advanced_buttons3_add : "iespell,flash,advhr",
   content_css : "<%= sCSSURL %>",
   plugin_insertdate_dateFormat : "%Y-%m-%d",
   plugin_insertdate_timeFormat : "%H:%M:%S",
   relative_urls : "true",
   convert_urls: "false",
   document_base_url : "<%= sDocBase %>",
   extended_valid_elements :
   "style[type],a[name|href|target|title|onclick],img[class|src|border=0|alt|title|hspace|vspace|width|height|align|onmouseover|onmouseout|name],font[face|size|color],hr[class|width|size|noshade]"
   });

   var status = true;
   function toggleEditor()
   {
   if(status)
   {
   tinyMCE.execCommand("mceRemoveControl", false, "elm1");
   status = false;
   }
   else
   {
   tinyMCE.execCommand("mceAddControl", false, "elm1");
   status = true;
   }
   }
</script>
<!-- /tinyMCE -->
<div align="center">
	<form method="post"
	action="<portlet:actionURL>
 				<portlet:param name="op" value="<%= CMSAdminConstants.OP_MODIFYANDAPPROVE %>"/>
 				<portlet:param name="pid" value="<%= pid %>"/>
 				<portlet:param name="path" value="<%= path %>"/>
 			</portlet:actionURL>"
	>
		<table width="100%">		
			<tr>
				<!--
				<td style="border: 1px solid #000000;"><%=pendingPreviewContent%></td>
				-->
				<td>
				<textarea id="elm1" name="elm1" rows="20" cols="80" style="width: 100%" class="textarea">
	         		<%=pendingPreviewContent%>
	      		</textarea>
	      		</td>
			</tr>
			<tr>
				<td>&nbsp;</td>
			</tr>
			<tr align="center">
				<td>				
					<input class="portlet-form-button" type="submit" value="${n:i18n("CMS_MODIFY")}/${n:i18n("CMS_APPROVE")}"/>
					<input class="portlet-form-button" type="reset"  name="reset" value="${n:i18n("CMS_RESET")}"/>
					<input class="portlet-form-button" type="button" value="${n:i18n("CMS_CANCEL")}" name="cancel"
             		onclick="window.location='<portlet:renderURL><portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWPENDING %>"/><portlet:param name="path" value="<%= path %>"/></portlet:renderURL>'">
				</td>
			</tr>
		</table>
	</form>
</div>
<%}%>


<br/></br>


<table width="100%" border="0" cellspacing="0" cellpadding="0">
   <tr>
      <td class="portlet-table-text"><b>${n:i18n("CMS_PATH")}</b></td>
      <td class="portlet-table-text"><b>${n:i18n("CMS_TYPE")}</b></td>
      <td class="portlet-table-text"><b>${n:i18n("CMS_CREATED")}</b></td>
      <td class="portlet-table-text"><b>${n:i18n("CMS_CREATED_BY")}</b></td>
      <td class="portlet-table-text"><b>${n:i18n("CMS_ACTION")}</b></td>
   </tr>
   <%int i = 0;%>
   <%
      for (Iterator itr = pendingQueue.iterator(); itr.hasNext();)
      {
   %>
   <%
      org.jboss.portal.cms.workflow.Content cour = (org.jboss.portal.cms.workflow.Content)itr.next();

      String linkPath = cour.getPath().substring(0, cour.getPath().lastIndexOf("/"));

      if (i % 2 == 0)
      {
         rowClass = "portlet-section-body";
      }
      else
      {
         rowClass = "portlet-section-alternate";
      }
      i++;
   %>
   <tr class="<%= rowClass %>">
      <!-- Path -->
      <td><a href="<portlet:renderURL>
          <portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWFILE %>"/>
          <portlet:param name="path"
            value="<%= linkPath %>"/>
        </portlet:renderURL>"><%= linkPath %>
      </a>
      </td>
      <!-- Mime Type -->
      <td><%= cour.getMimeType() %>
      </td>
      <!-- Creation Date -->
      <td>
         <%= cour.getCreationDateStr() %>
      </td>
      <!-- User who requested approval -->
      <td><%= cour.getUserName() %>
      </td>
      <td>
            <%
         if (cour.getMimeType().equals("text/html") || cour.getMimeType().equals("text/plain"))
         {
      %>
      	 <a href="<portlet:renderURL>
         		<portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWPENDINGPREVIEW %>"/>
         		<portlet:param name="pid" value="<%=cour.getProcessId()%>"/>
         		<portlet:param name="path" value="<%=linkPath%>"/>
         		<portlet:param name="contentPath" value="<%=cour.getPath()%>"/>
         </portlet:renderURL>" 
         <%if(contentBeingPreviewed.equals(cour.getProcessId())){%>style="color: red;"<%}%>
         >${n:i18n("CMS_PREVIEW")}</a>

      <% }
      else
      {
         //save the principal for the preview servlet
         String remoteUser = request.getRemoteUser();
         request.getSession().setAttribute("remoteUser", remoteUser);
      %>
      <a target="_blank"
         href="<%= request.getContextPath() %>/cmspreview?pid=<%= cour.getProcessId() %>&p=<%= cour.getPath() %>"<%if(contentBeingPreviewed.equals(cour.getProcessId())){%>style="color: red;"<%}%>
         >${n:i18n("CMS_PREVIEW")}</a>
      <%
      }
      %>
      
      
      
         &nbsp;
         <a href="<portlet:actionURL>
         		<portlet:param name="op" value="<%= CMSAdminConstants.OP_APPROVE %>"/>
         		<portlet:param name="pid" value="<%=cour.getProcessId()%>"/>
         		<portlet:param name="path" value="<%=linkPath%>"/>
         </portlet:actionURL>">${n:i18n("CMS_APPROVE")}</a>
         &nbsp;
         <a href="<portlet:actionURL>
         <portlet:param name="op" value="<%= CMSAdminConstants.OP_DENY %>"/>
         <portlet:param name="pid" value="<%=cour.getProcessId()%>"/>
         <portlet:param name="path" value="<%=linkPath%>"/>
         </portlet:actionURL>">${n:i18n("CMS_DENY")}</a>
      </td>
   </tr>
   <%}%>
</table>
</div>
</div>