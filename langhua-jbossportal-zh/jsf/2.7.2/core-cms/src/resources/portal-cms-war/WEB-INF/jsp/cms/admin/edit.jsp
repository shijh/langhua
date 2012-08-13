<%@ page import="org.jboss.portal.core.cms.ui.admin.CMSAdminConstants" %>
<%@ page import="org.jboss.portal.common.text.EntityEncoder" %>
<%@ page import="java.util.Locale" %>
<%@ page language="java" extends="org.jboss.portal.core.servlet.jsp.PortalJsp" %>
<%@ taglib uri="http://java.sun.com/portlet_2_0" prefix="portlet" %>
<%@ taglib uri="/WEB-INF/portal-lib.tld" prefix="n" %>
<%@ page isELIgnored="false" %>

<portlet:defineObjects/>
<link rel="stylesheet" type="text/css" href="/portal-admin/css/style.css" media="screen"/>
<div class="admin-ui">
<br/>
<h3 class="sectionTitle-blue">${n:i18n("TITLE_EDIT")}</h3>
<div class=" cms-tab-container">
<%
   String sCurrPath = (String)request.getAttribute("currpath");
   String sContent = (String)request.getAttribute("content");
   String sDocBase = (String)request.getAttribute("document_base_url");
   String sCSSURL = (String)request.getAttribute("css_url");
   String sLanguage = (String)request.getAttribute("language");
   String sVersion = (String)request.getAttribute("version");
   String sTitle = (String)request.getAttribute("title");
   String sDescription = (String)request.getAttribute("description");
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

<form method="post" action="<portlet:actionURL>
    <portlet:param name="op" value="<%= CMSAdminConstants.OP_SAVETEXT %>"/>
    <portlet:param name="savetopath" value="<%= sCurrPath %>"/>
    <portlet:param name="language" value="<%= sLanguage %>"/>
    </portlet:actionURL>">

<table width="100%">

<tr>
   <td height="10"></td>
</tr>
<tr>
   <td>
<tr>
   <td>
      <table>
         <tr>
            <td>${n:i18n("CMS_EDITING")}:
            </td>
            <td><%= EntityEncoder.FULL.encode(sCurrPath) %>
            </td>
         </tr>
         <tr>
            <td>
               ${n:i18n("CMS_DESCRIPTION")}:
            </td>
            <td><input type="text"
                       size="40"
                       maxlength="80"
                       name="description"
                       value="<%= sDescription %>"
                       class="portlet-form-input-field"/>
            </td>
         </tr>
         <tr>
            <td>
               ${n:i18n("CMS_TITLE")}:
            </td>
            <td><input type="text" size="40"
                       maxlength="80"
                       name="title"
                       value="<%= sTitle %>"
                       class="portlet-form-input-field"/>
            </td>
         </tr>
         <tr>
            <td>
               ${n:i18n("CMS_LANGUAGE")}:
            </td>
            <td><%= new Locale(sLanguage).getDisplayLanguage() %>
            </td>
         </tr>
         <tr>
            <td>
               ${n:i18n("CMS_LIVE")}:
            </td>
            <td><input type="checkbox" name="makelive" checked
                       class="portlet-form-input-field"/>
            </td>
         </tr>
         <tr>
            <td>
               ${n:i18n("CMS_VERSION")}:
            </td>
            <td><%= sVersion %>
            </td>
         </tr>
         <tr>
            <td class="portlet-section-header" valign="bottom">
               ${n:i18n("CMS_WYSIWYG")}:
            </td>
            <td>
               <input type="checkbox" name="wysiwyg" class="portlet-form-input-field" checked
                      onclick="javascript:toggleEditor();"/>
            </td>
         </tr>
      </table>
   </td>
</tr>
<tr>
   <td height="10"></td>
</tr>
<tr>
   <td class="portlet-section-alternate">
      <img src="<%= renderRequest.getContextPath() + CMSAdminConstants.DEFAULT_IMAGES_PATH%>/info.gif"
           border="0"
           alt="Info"> ${n:i18n("CMS_LINK_TO_RESOURCES")}.
   </td>
</tr>
<tr>
   <td height="10"></td>
</tr>
<tr>
   <td align="center">
      <textarea id="elm1" name="elm1" rows="20" cols="80" style="width: 100%" class="textarea">
         <%= sContent %>
      </textarea>
      <br>
      <input type="submit" name="save" value="${n:i18n("CMS_CREATE")}" class="portlet-form-button"/>
      <input type="reset" name="reset" value="${n:i18n("CMS_RESET")}" class="portlet-form-button"/>
      <input class="portlet-form-button" type="button" value="${n:i18n("CMS_CANCEL")}" name="cancel"
             onclick="window.location='<portlet:renderURL><portlet:param name="op" value="<%= CMSAdminConstants.OP_VIEWFILE %>"/><portlet:param name="path" value="<%= sCurrPath %>"/></portlet:renderURL>'">
   </td>
</tr>
</table>
</form>

</div>
</div>