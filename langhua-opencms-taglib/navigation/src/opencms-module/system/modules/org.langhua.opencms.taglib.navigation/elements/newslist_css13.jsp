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
                        org.opencms.xml.content.*,
			org.opencms.frontend.templateone.*" %>

<%
CmsJspActionElement cms = new CmsTemplateBean(pageContext, request, response);
CmsObject cmso = cms.getCmsObject();
Locale locale = cms.getRequestContext().getLocale();
String fileUri = cms.getRequestContext().getUri();
try {
CmsFile configFile = cmso.readFile(fileUri, CmsResourceFilter.IGNORE_EXPIRATION);
CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso, configFile);
String fileName = configFile.getName();

String mainwidth = configuration.getStringValue(cmso, "main.width", locale); 
String titleimgurl = configuration.getStringValue(cmso, "title.img.url", locale); 
String titleimgwidth = configuration.getStringValue(cmso, "title.img.width", locale);
String titleimgheight = configuration.getStringValue(cmso, "title.img.height", locale); 
String contentimgurl = configuration.getStringValue(cmso, "content.img.url", locale); 
String contentimgwidth = configuration.getStringValue(cmso, "content.img.width", locale); 
String contentimgheight = configuration.getStringValue(cmso, "content.img.height", locale); 
String contentleftpadding = configuration.getStringValue(cmso, "content.left.padding", locale); 
String contentfontsize = configuration.getStringValue(cmso, "content.fontsize", locale);
String contentfontcolor = configuration.getStringValue(cmso, "content.fontcolor", locale);
String contentfontweight = configuration.getStringValue(cmso, "content.fontweight", locale);

String titleimguri="";
if(CmsStringUtil.isNotEmptyOrWhitespaceOnly(titleimgurl)){
titleimguri=cms.link(titleimgurl);
}
String contentimguri="";
if(CmsStringUtil.isNotEmptyOrWhitespaceOnly(contentimgurl)){
contentimguri=cms.link(contentimgurl);
}
%>
.tmain<%=fileName%>{float:left;width:<%=mainwidth%>px;}
.titleimg<%=fileName%>{float:left;background:url(<%=titleimguri%>); width:<%=titleimgwidth%>px; height:<%=titleimgheight%>px;}
.contentimg<%=fileName%>{ float:left; background:url(<%=contentimguri%>); width:<%=contentimgwidth%>px; height:<%=contentimgheight%>px;}
.font<%=fileName%>{font-size:<%=contentfontsize%>px;font-weight:<%=contentfontweight%>; 
 margin-left:<%=contentleftpadding%>px; line-height:<%=contentimgheight%>px;}
.link<%=fileName%>{Text-decoration: none;color:<%=contentfontcolor%>;}
<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>