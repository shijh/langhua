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
String mainheight= configuration.getStringValue(cmso, "main.height", locale); 
String mainbgcolor = configuration.getStringValue(cmso, "main.bgcolor", locale); 
String mainborderwidth= configuration.getStringValue(cmso, "main.border.width", locale);
String mainborderstyle= configuration.getStringValue(cmso, "main.border.style", locale);
String mainbordercolor= configuration.getStringValue(cmso, "main.border.color", locale);
String paddingtop= configuration.getStringValue(cmso, "padding.top", locale);
String titlebgimg= configuration.getStringValue(cmso, "title.bgimg", locale);
String titleheight= configuration.getStringValue(cmso, "title.height", locale);
String titleborderwidth= configuration.getStringValue(cmso, "title.border.width", locale);
String titleborderstyle= configuration.getStringValue(cmso, "title.border.style", locale);
String titlebordercolor= configuration.getStringValue(cmso, "title.border.color", locale);
String titlewordfontcolor= configuration.getStringValue(cmso, "title.word.fontcolor", locale);
String titlewordfontsize= configuration.getStringValue(cmso, "title.word.fontsize", locale);
String titlewordfontweight= configuration.getStringValue(cmso, "title.word.fontweight", locale);
String titlewordtextindent= configuration.getStringValue(cmso, "title.word.text.indent", locale);
String titlewordbgimg = configuration.getStringValue(cmso, "title.word.bgimg", locale);
String titlewordbgimgwidth = configuration.getStringValue(cmso, "title.word.bgimg.width", locale);
String titlewordbgimgheight= configuration.getStringValue(cmso, "title.word.bgimg.height", locale);
String titlewordbgimgbeforewidth = configuration.getStringValue(cmso, "title.word.bgimg.beforewidth", locale);
String titlewordbgimgonheight = configuration.getStringValue(cmso, "title.word.bgimg.onheight", locale);
String titlemorecolor= configuration.getStringValue(cmso, "title.more.color", locale);
String titlemorefontsize= configuration.getStringValue(cmso, "title.more.fontsize", locale);
String titlemoremarginright= configuration.getStringValue(cmso, "title.more.margin.right", locale);
String contentborderwidth= configuration.getStringValue(cmso, "content.border.width", locale);
String contentborderstyle= configuration.getStringValue(cmso, "content.border.style", locale);
String contentbordercolor= configuration.getStringValue(cmso, "content.border.color", locale);	
String contentbgimg= configuration.getStringValue(cmso, "content.bgimg", locale);
String contentbeforeimg= configuration.getStringValue(cmso, "content.before.img", locale);
String contentlineheight= configuration.getStringValue(cmso, "content.line.height", locale);
String contentfontsize= configuration.getStringValue(cmso, "content.fontsize", locale);
String contenttextindent= configuration.getStringValue(cmso, "content.text.indent", locale);
String contentfoldertitlecolor= configuration.getStringValue(cmso, "content.foldertitle.color", locale);
String contentfoldertitlefontsize= configuration.getStringValue(cmso, "content.foldertitle.fontsize", locale);
String contentfoldertitlemarginright= configuration.getStringValue(cmso, "content.foldertitle.marginright", locale);
String contentfontcolor= configuration.getStringValue(cmso, "content.fontcolor", locale);
String beforedistance= configuration.getStringValue(cmso, "before.distance", locale);
String moretop= configuration.getStringValue(cmso, "more.top", locale);
String isDottedline= configuration.getStringValue(cmso, "isDottedline", locale); 
String textborderwidth= configuration.getStringValue(cmso, "text.border.width", locale); 
String textbordercolor= configuration.getStringValue(cmso, "text.border.color", locale); 
String textborderstyle= configuration.getStringValue(cmso, "text.border.style", locale);
String tbgimg="";
if (CmsStringUtil.isNotEmpty(titlebgimg)) {
   tbgimg=cms.link(titlebgimg);
}
String twbgimg ="";
if (CmsStringUtil.isNotEmpty(titlewordbgimg)) {
   twbgimg =cms.link(titlewordbgimg);
}
String cbgimg="";
if (CmsStringUtil.isNotEmpty(contentbgimg)) {
   cbgimg=cms.link(contentbgimg);
}

String cbimg="";
if (CmsStringUtil.isNotEmpty(contentbeforeimg)) {
   cbimg=cms.link(contentbeforeimg);
}
int ctlh=Integer.parseInt(contentlineheight);
ctlh=ctlh+0;
%>
.newsRed<%=fileName%>{
	background-color:<%=mainbgcolor%>;
	border:<%=mainbordercolor%> <%=mainborderwidth%>px <%=mainborderstyle%>;
        padding-top:<%=paddingtop%>px;
        height:<%=mainheight%>px;
	}
.newsRedCard<%=fileName%>{
	margin:0 auto;
	width:<%=mainwidth%>px;
	background:url(<%=tbgimg%>);
	height:<%=titleheight%>px;
	border:<%=titlebordercolor%> <%=titleborderwidth%>px <%=titleborderstyle%>;
	}
.newsRedCardName<%=fileName%>{
	color:<%=titlewordfontcolor%>;
	font-size:<%=titlewordfontsize%>px;
	font-weight:<%=titlewordfontweight%>;
	float:left;
	line-height:<%=titleheight%>px;
	text-indent:<%=titlewordtextindent%>em;
<%if (CmsStringUtil.isNotEmpty(twbgimg)) {%>
	background:url(<%=twbgimg%>);     
	background-repeat:no-repeat;   
	background-position:<%=titlewordbgimgbeforewidth%>px <%=titlewordbgimgonheight%>px; 
<%}%> 
	}
.newsRedMore1<%=fileName%>{
	float:right;
	padding-top:<%=moretop%>px;
	margin-right:<%=titlemoremarginright%>px; 
	}
.newsRedMore<%=fileName%>{
	font-size:<%=titlemorefontsize%>px;
	line-height:<%=titleheight%>px;
	float:right;
	margin-right:<%=titlemoremarginright%>px; 
	}
.newsRedMore<%=fileName%> a:link,.newsRedMore<%=fileName%> a:visited{
	color:<%=titlemorecolor%>;
	text-decoration:none;
	}
.newsRedMore<%=fileName%> a:hover{color:<%=titlemorecolor%>; text-decoration:underline;}
.newsRedInfo<%=fileName%>{
	border:<%=contentbordercolor%> <%=contentborderwidth%>px <%=contentborderstyle%>;
<%if (CmsStringUtil.isNotEmpty(cbgimg)) {%>
	background:url(<%=cbgimg%>);  
<%}%>
	margin:0 auto;
	padding:8px 5px 8px 0px;
	}
.newsRedInfo<%=fileName%> ul{
	display:inline;
	margin:0 auto;
	padding:0px;
	}
.newsRedInfo<%=fileName%> li{
	list-style:none;
	list-style-position:outside;
<%if("true".equals(isDottedline)){%>
border-bottom:<%=textborderwidth%>px <%=textborderstyle%> <%=textbordercolor%>;
<%}%>	
	}
.newsRedInfo1<%=fileName%>{
margin:0 auto;
padding:0px 0px 0px <%=beforedistance%>px;
color: <%=contentfoldertitlecolor%>;
	font-size:<%=contentfoldertitlefontsize%>px;
	margin-right:<%=contentfoldertitlemarginright%>px;
	line-height:<%=contentlineheight%>px;
    *line-height:<%=contentlineheight%>px;
    _line-height:<%=ctlh%>px;
}
.newsRedInfo<%=fileName%> ul li{
	list-style-image:url(<%=cbimg%>);
	line-height:<%=contentlineheight%>px;
    *line-height:<%=contentlineheight%>px;
    _line-height:<%=ctlh%>px;
	font-size:<%=contentfontsize%>px;
	text-align:left;
	margin-left:27px;
    *margin-left:18px;
    _margin-left:20px;
	text-indent:<%=contenttextindent%>em;
	}
.newsRedInfo<%=fileName%> ul li span{
	color: <%=contentfoldertitlecolor%>;
	font-size:<%=contentfoldertitlefontsize%>px;
	margin-right:<%=contentfoldertitlemarginright%>px;
	}
.newsRedInfo<%=fileName%> ul li a:link,.newsRedInfo<%=fileName%> ul li a:visited{
        color: <%=contentfontcolor%>;
	text-decoration:none;
	}
.newsRedInfo<%=fileName%> ul li a:hover{
color: <%=contentfontcolor%>;
	text-decoration:underline;
	}
.newsRedInfo1<%=fileName%>  a:link,.newsRedInfo1<%=fileName%>  a:visited{
        color: <%=contentfontcolor%>;
	text-decoration:none;
	}
.newsRedInfo1<%=fileName%> a:hover{
color: <%=contentfontcolor%>;
	text-decoration:underline;
	}
.news<%=fileName%> a:link,.news<%=fileName%> a:visited{
        font-size:<%=contentfontsize%>px;
        color: <%=contentfontcolor%>;
	text-decoration:none;
	}
.news<%=fileName%> a:hover{
        font-size:<%=contentfontsize%>px;
        color: <%=contentfontcolor%>;
	text-decoration:underline;
	}
<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>