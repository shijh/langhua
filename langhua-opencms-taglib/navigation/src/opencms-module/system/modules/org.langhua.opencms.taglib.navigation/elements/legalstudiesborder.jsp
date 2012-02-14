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
String mainwidth= configuration.getStringValue(cmso, "mainwidth", locale);
String mainfloat= configuration.getStringValue(cmso, "mainfloat", locale);
String titleheight= configuration.getStringValue(cmso, "titleheight", locale);
String bgimg= configuration.getStringValue(cmso, "bgimg", locale);
String titlecolor= configuration.getStringValue(cmso, "titlecolor", locale);
String titleweight= configuration.getStringValue(cmso, "titleweight", locale);
String titlefloat= configuration.getStringValue(cmso, "titlefloat", locale);
String up= configuration.getStringValue(cmso, "up", locale);
String right= configuration.getStringValue(cmso, "right", locale);
String down= configuration.getStringValue(cmso, "down", locale);
String left= configuration.getStringValue(cmso, "left", locale);
String righttestup= configuration.getStringValue(cmso, "righttestup", locale);
String righttestright= configuration.getStringValue(cmso, "righttestright", locale);
String righttestdown= configuration.getStringValue(cmso, "righttestdown", locale);
String righttestleft= configuration.getStringValue(cmso, "righttestleft", locale);

String borderrightcolor= configuration.getStringValue(cmso, "borderrightcolor", locale);
String borderrightweight= configuration.getStringValue(cmso, "borderrightweight", locale);
String borderrightfloat= configuration.getStringValue(cmso, "borderrightfloat", locale);

String borderbottomcolor= configuration.getStringValue(cmso, "borderbottomcolor", locale);
String borderbottomweight= configuration.getStringValue(cmso, "borderbottomweight", locale);
String borderbottomfloat= configuration.getStringValue(cmso, "borderbottomfloat", locale);

String borderleftcolor= configuration.getStringValue(cmso, "borderleftcolor", locale);
String borderleftweight= configuration.getStringValue(cmso, "borderleftweight", locale);
String borderleftfloat= configuration.getStringValue(cmso, "borderleftfloat", locale);

String lefttestcolor= configuration.getStringValue(cmso, "lefttestcolor", locale);
String lefttestweight= configuration.getStringValue(cmso, "lefttestweight", locale);
String lefttestfloat= configuration.getStringValue(cmso, "lefttestfloat", locale);
String minheight= configuration.getStringValue(cmso, "minheight", locale);

if (CmsStringUtil.isNotEmpty(bgimg)) {
   bgimg=cms.link(bgimg);
}
%>
.leftyj2j<%=fileName%>{
	width:<%=mainwidth%>px;
	float:<%=mainfloat%>;
	margin:<%=up%>px <%=right%>px <%=down%>px <%=left%>px;
	}
.leftyj2jCard<%=fileName%>{
	background-image:url(<%=bgimg%>);
	height:<%=titleheight%>px;
	border:<%=titlecolor%> <%=titleweight%>px <%=titlefloat%>;
	}
.leftyj2jCard<%=fileName%> span{
	float:left;
	border:#8eb6d0;
	color:<%=lefttestcolor%>;
	font-size:<%=lefttestweight%>px;
	font-weight:<%=lefttestfloat%>;
	line-height:26px;
	text-align:left;
	text-indent:1em;
	}
.leftyj2jCard<%=fileName%> p{
	color:#095587;
	float:right;
	font-size:12px;
	line-height:26px;
	margin:<%=righttestup%>px <%=righttestright%>px <%=righttestdown%>px <%=righttestleft%>px;
	}
.leftyj2jCard<%=fileName%> p a:link,.leftyj2jCard<%=fileName%> p a:visited{color:#095587; text-decoration:none;}
.leftyj2jCard<%=fileName%> p a:hover{color:#095587; text-decoration:underline;}
.leftyj2jInfo<%=fileName%>{
	border-right:<%=borderrightcolor%> <%=borderrightweight%>px <%=borderrightfloat%>;
	border-bottom:<%=borderbottomcolor%> <%=borderbottomweight%>px <%=borderbottomfloat%>;
	border-left:<%=borderleftcolor%> <%=borderleftweight%>px <%=borderleftfloat%>;
	min-height:<%=minheight%>px;
	height:<%=minheight%>px;
	}

<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>