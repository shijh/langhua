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

String mainborderstyle= configuration.getStringValue(cmso, "main.border.style", locale); 
String mainbordercolor= configuration.getStringValue(cmso, "main.border.color", locale); 
String mainborderwidth= configuration.getStringValue(cmso, "main.border.width", locale);
String titlefontsize= configuration.getStringValue(cmso, "title.fontsize", locale); 
String titlefontweight= configuration.getStringValue(cmso, "title.fontweight", locale);
String titlecolor= configuration.getStringValue(cmso, "title.color", locale);
String contentfontsize= configuration.getStringValue(cmso, "content.fontsize", locale); 
String contentcolor= configuration.getStringValue(cmso, "content.color", locale);
%>
.huixian<%=fileName%>{
	border:<%=mainbordercolor%> <%=mainborderwidth%>px <%=mainborderstyle%>;
	}
.STYLE1<%=fileName%> {font-size: <%=contentfontsize%>px;color:<%=contentcolor%>;}
.STYLE7<%=fileName%> {
	font-size: <%=titlefontsize%>px;
	font-weight: <%=titlefontweight%>;
	color: <%=titlecolor%>;
}
.STYLE1<%=fileName%> a:link,.STYLE1<%=fileName%> a:visited{
        color: <%=contentcolor%>;
	text-decoration:none;
}
.STYLE1<%=fileName%> a:hover{
        color: <%=contentcolor%>;
	text-decoration:none;
}
<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>