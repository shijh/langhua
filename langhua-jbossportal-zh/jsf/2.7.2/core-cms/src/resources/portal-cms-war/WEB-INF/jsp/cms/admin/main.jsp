<%@ page import="org.jboss.portal.cms.model.File" %>
<%@ page import="org.jboss.portal.cms.model.Folder" %>
<%@ page import="org.jboss.portal.core.cms.ui.admin.CMSAdminConstants" %>
<%@ page import="org.jboss.portal.common.text.EntityEncoder" %>
<%@ page import="java.text.Format" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.StringTokenizer" %>

<%@ page language="java" extends="org.jboss.portal.core.servlet.jsp.PortalJsp" %>
<%@ taglib uri="/WEB-INF/portal-lib.tld" prefix="n" %>
<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<%@ page isELIgnored="false" %>

<portlet:defineObjects/>

<link rel="stylesheet" type="text/css" href="/portal-admin/css/style.css" media="screen"/>
<%
   String sConfirm = renderRequest.getParameter("confirm");
   String sCurrPath = (String)request.getAttribute("currpath");
   List<Folder> folders = (List<Folder>)request.getAttribute("folders");
   List<File> files = (List<File>)request.getAttribute("files");
   String createDate = "";
   String modifiedDate = "";
   Boolean manageWorkflowAccessible = (Boolean)request.getAttribute("manageWorkflowAccessible");
   List<String> messages = (List<String>)request.getAttribute("messages");
   Format dateFormat = (Format)request.getAttribute("dateFormat");
%>

<div class="admin-ui">
<br/>

<h3 class="sectionTitle-blue">
   ${n:i18n("CMS_MANAGE")}
</h3>

<div class="cms-tab-container">


<!-- Displaying any messages that may have occurred during this request -->
<%
   if (messages != null && !messages.isEmpty())
   {
%>
<table width="100%">
   <th colspan="2"><h3 class="sectionTitle-blue">${n:i18n("CMS_MESSAGES")}:</h3></th>
   <%
      for (String message : messages)
      {
   %>
   <tr align="center">
      <td colspan="2">
         <font color="red"><%=message%></font>
      </td>
   </tr>
   <%}%>
</table>
<br/><br/>
<%}%>

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
"><%= EntityEncoder.FULL.encode(sPathChunk) %>
   </a>
   </li>
   <%
   }
   else
   {
   %>
   <li class="pathSeperator"><img src="/portal-admin/img/pathSeparator.png" alt=">"></li>
   <li class="selected"><%= EntityEncoder.FULL.encode(sPathChunk) %>
   </li>
   <%
         }
      }
   %>
</ul>
<br/>
<!-- folder-level action dropdown -->
<div class="menu-container">
   <%
      if (sConfirm != null && !"".equals(sConfirm))
      {
   %>
   <div class="portlet-msg-success"><%= sConfirm %>
   </div>
   <br/>
   <%
      }
   %>


   <div class="menu">
      <select onchange="window.open(trim(this.options[this.selectedIndex].value),'_top')">
         <option value="">${n:i18n("CMS_SELECT_ACTION")}</option>
         <option value="<portlet:renderURL>
                     <portlet:param name="op" value="<%= CMSAdminConstants.OP_CONFIRM_CREATE_COLLECTION %>"/>
                     <portlet:param name="path" value="<%= sCurrPath %>"/>
                     </portlet:renderURL>">${n:i18n("CMS_CREATEFOLDER")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_CREATENEWTEXT %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("CMS_CREATEFILE")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_UPLOADCONFIRM %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("TITLE_UPLOAD")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_UPLOADARCHIVECONFIRM %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("CMS_UPLOADARCHIVE")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_EXPORTARCHIVE %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("CMS_EXPORTARCHIVE")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_CONFIRMSECURE %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      <portlet:param name="returnOp" value="<%= CMSAdminConstants.OP_MAIN %>"/>
      </portlet:renderURL>">${n:i18n("CMS_SECURE")}</option>
         <%
            if (manageWorkflowAccessible)
            {
         %>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWPENDING %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("CMS_APPROVAL")}</option>
         <%}%>
         <%
            if (!"/".equals(sCurrPath))
            {
         %>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_CONFIRMCOPY %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      <portlet:param name="type" value="fo"/>
      </portlet:renderURL>">${n:i18n("CMS_COPY")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_CONFIRMMOVE %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      <portlet:param name="type" value="fo"/>
      </portlet:renderURL>">${n:i18n("CMS_MOVE")}</option>
         <option value="<portlet:renderURL>
      <portlet:param name="op" value="<%= CMSAdminConstants.OP_CONFIRMDELETE %>"/>
      <portlet:param name="path" value="<%= sCurrPath %>"/>
      </portlet:renderURL>">${n:i18n("CMS_DELETE")}</option>
         <% } %>
      </select>

   </div>

</div>

<div class="search-container">
   <form method="post" action="<portlet:actionURL>
    <portlet:param name="op" value="<%= CMSAdminConstants.OP_DOSEARCH %>"/>
    </portlet:actionURL>">

      <input type="text"
             size="15"
             maxlength="80"
             name="search"
             class="portlet-form-input-field"/>
      <input type="submit" name="search" value="${n:i18n("CMS_SEARCH")}" class="portlet-form-button"/>

   </form>
</div>
<br style="clear:both"/>

<div class="file-table-container">
<%

   if (folders.size() > 0 || files.size() > 0)
   {

%>

<table width="100%" border="0" cellspacing="2" cellpadding="2">
<tr>
   <td class="portlet-section-header">${n:i18n("CMS_NAME")}</td>
   <td class="portlet-section-header">${n:i18n("CMS_ACTION")}</td>
   <td class="portlet-section-header">${n:i18n("CMS_CREATED")}</td>
   <td class="portlet-section-header">${n:i18n("CMS_MODIFIED")}</td>
</tr>

<%
   if (folders.size() > 0)
   {
      for (Folder folder : folders)
      {
%>
<tr onmouseover="this.className='portlet-section-alternate';" onmouseout="this.className='portlet-section-body';">
   <td><img
           src="<%= renderRequest.getContextPath() + CMSAdminConstants.DEFAULT_IMAGES_PATH%>/folder.gif"
           alt="${n:i18n("CMS_FOLDER")}"
           border="0">&nbsp;<a href="<portlet:renderURL>
          <portlet:param name="op" value="<%= CMSAdminConstants.OP_MAIN %>"/>
          <portlet:param name="path" value="<%= folder.getBasePath() %>"/>
        </portlet:renderURL>"><%=
        EntityEncoder.FULL.encode(folder.getBasePath().substring(folder.getBasePath().lastIndexOf("/") + 1, folder.getBasePath().length())) %>
   </a>
   </td>
   <td>
      <form method="POST" style="padding:0;margin:0;" action="<portlet:actionURL>
    <portlet:param name="path" value="<%= folder.getBasePath() %>"/>
    <portlet:param name="type" value="fo"/>
    <portlet:param name="dispatch" value="1"/>
   </portlet:actionURL>">
         <select name="op">
            <option value="<%= CMSAdminConstants.OP_MAIN %>">${n:i18n("CMS_VIEW")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMCOPY %>">${n:i18n("CMS_COPY")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMMOVE %>">${n:i18n("CMS_MOVE")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMDELETE %>">${n:i18n("CMS_DELETE")}</option>
         </select>
         <input type="submit" value="${n:i18n("CMS_GO")}" name="Go" class="portlet-form-button"/>
      </form>
   </td>
   <td>
      <%
         if (folder.getCreationDate() != null)
         {            
            createDate = dateFormat.format(folder.getCreationDate());
         }
      %>
      <%= createDate %>
   </td>
   <td>
      <%
         if (folder.getLastModified() != null)
         {
            modifiedDate = dateFormat.format(folder.getLastModified());
         }
      %>
      <%= modifiedDate %>
   </td>
</tr>
<%
      }
   }
%>

<%
   if (files.size() > 0)
   {
      for (File file : files)
      {
%>
<tr onmouseover="this.className='portlet-section-alternate';" onmouseout="this.className='portlet-section-body';">
   <td><img src="<%= renderRequest.getContextPath() + CMSAdminConstants.DEFAULT_IMAGES_PATH%>/file.gif"
            alt="${n:i18n("CMS_FILE")}"
            border="0">&nbsp;<a href="<portlet:renderURL>
          <portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWFILE %>"/>
          <portlet:param name="path"
            value="<%= file.getBasePath() %>"/>
        </portlet:renderURL>"><%=
        EntityEncoder.FULL.encode(file.getBasePath().substring(file.getBasePath().lastIndexOf("/") + 1, file.getBasePath().length())) %>
   </a>
   </td>
   <td>
      <form method="POST" style="padding:0;margin:0;" action="<portlet:actionURL>
    <portlet:param name="path" value="<%= file.getBasePath() %>"/>
    <portlet:param name="type" value="fi"/>
    <portlet:param name="dispatch" value="1"/>
   </portlet:actionURL>">
         <select name="op">
            <option value="<%= CMSAdminConstants.OP_VIEWFILE %>">${n:i18n("CMS_VIEW")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMCOPY %>">${n:i18n("CMS_COPY")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMMOVE %>">${n:i18n("CMS_MOVE")}</option>
            <option value="<%= CMSAdminConstants.OP_CONFIRMDELETE %>">${n:i18n("CMS_DELETE")}</option>
         </select>
         <input type="submit" value="${n:i18n("CMS_GO")}" name="Go" class="portlet-form-button"/>
      </form>
   </td>
   <td>
      <%
         if (file.getCreationDate() != null)
         {
            createDate = dateFormat.format(file.getCreationDate());
         }
      %>
      <%= createDate %>
   </td>
   <td>
      <%
         if (file.getLastModified() != null)
         {
            modifiedDate = dateFormat.format(file.getLastModified());
         }
      %>
      <%= modifiedDate %>
   </td>
</tr>
<%
   }
}
else
{%>

<tr>
   <td>
      <p>${n:i18n("CMS_EMPTY_FOLDER")}</p>
   </td>
</tr>

<%
   }
%>
</table>
<%

   }

%>
</div>
</div>
</div>
