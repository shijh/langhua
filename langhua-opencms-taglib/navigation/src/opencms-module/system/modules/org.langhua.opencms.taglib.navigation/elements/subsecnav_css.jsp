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
String mainheight = configuration.getStringValue(cmso, "main.height", locale); 
String mainColor = configuration.getStringValue(cmso, "main.color", locale); 
String columnfontcolor = configuration.getStringValue(cmso, "column.fontcolor", locale);
String linkfontcolor = configuration.getStringValue(cmso, "link.fontcolor", locale);
String filefrontimage = configuration.getStringValue(cmso, "file.front.image", locale);
String filefrontfontweight = configuration.getStringValue(cmso, "file.front.fontweight", locale);
String filefrontfontcolor = configuration.getStringValue(cmso, "file.front.fontcolor", locale);
String filefrontfontsize = configuration.getStringValue(cmso, "file.front.fontsize", locale);
String fileimgwidth = configuration.getStringValue(cmso, "file.imgwidth", locale);
String fileimgheight= configuration.getStringValue(cmso, "file.imgheight", locale);
String mainbordertopwidth = configuration.getStringValue(cmso, "main.border.top.width", locale);
String mainborderbottomwidth = configuration.getStringValue(cmso, "main.border.bottom.width", locale);
String mainborderleftwidth = configuration.getStringValue(cmso, "main.border.left.width", locale);
String mainborderrightwidth = configuration.getStringValue(cmso, "main.border.right.width", locale);
String mainborderstyle = configuration.getStringValue(cmso, "main.border.style", locale);
String mainbordercolor = configuration.getStringValue(cmso, "main.border.color", locale);
String externalfontcolor= configuration.getStringValue(cmso, "external.fontcolor", locale);
String titlebordertopwidth = configuration.getStringValue(cmso, "title.border.top.width", locale);
String titleborderbottomwidth = configuration.getStringValue(cmso, "title.border.bottom.width", locale);
String titleborderleftwidth = configuration.getStringValue(cmso, "title.border.left.width", locale);
String titleborderrightwidth = configuration.getStringValue(cmso, "title.border.right.width", locale);
String titleborderstyle = configuration.getStringValue(cmso, "title.border.style", locale);
String titlebordercolor = configuration.getStringValue(cmso, "title.border.color", locale);
String titleheight = configuration.getStringValue(cmso, "title.height", locale); 
String titleleftmargin = configuration.getStringValue(cmso, "title.left.margin", locale); 
String titlebottommargin = configuration.getStringValue(cmso, "title.bottom.margin", locale); 
String titlepbgimage = configuration.getStringValue(cmso, "title.p.bgimage", locale); 
String titleptalign = configuration.getStringValue(cmso, "title.p.talign", locale); 
String titlepbgcolor = configuration.getStringValue(cmso, "title.p.bgcolor", locale);
String titlepfontsize = configuration.getStringValue(cmso, "title.p.fontsize", locale); 
String titlepfontcolor = configuration.getStringValue(cmso, "title.p.fontcolor", locale); 
String titlefontweight = configuration.getStringValue(cmso, "title.fontweight", locale); 

String titlefontstyle = configuration.getStringValue(cmso, "title.fontstyle", locale); 

String contentmargint = configuration.getStringValue(cmso, "content.margint", locale);
String contentfontcolor= configuration.getStringValue(cmso, "content.fontcolor", locale);
String contentfontweight= configuration.getStringValue(cmso, "content.fontweight", locale);
String contentfontsize = configuration.getStringValue(cmso, "content.fontsize", locale);
String contentheight= configuration.getStringValue(cmso, "content.height", locale);
String compartlinewidth= configuration.getStringValue(cmso, "compartline.width", locale);
String compartlinestyle= configuration.getStringValue(cmso, "compartline.style", locale);
String compartlinecolor= configuration.getStringValue(cmso, "compartline.color", locale);
String linkalign=configuration.getStringValue(cmso, "link.align", locale);
String boldfontweight=configuration.getStringValue(cmso, "bold.fontweight", locale);
String timecolor=configuration.getStringValue(cmso, "time.color", locale);
String contenttextfontcolor=configuration.getStringValue(cmso, "contenttext.fontcolor", locale);
String contenttextfontweight=configuration.getStringValue(cmso, "contenttext.fontweight", locale);

String uri="";
if(CmsStringUtil.isNotEmpty(titlepbgimage)){
uri=cms.link(titlepbgimage);
}
String fileimguri="";
if(CmsStringUtil.isNotEmpty(filefrontimage)){
fileimguri=cms.link(filefrontimage);
}

%>
ul,ol,li,p,h1,h2,h3,h4,h5,h6,div{margin:0;padding:0;border:0}
p,li,div{font-family:"宋体"}
.tmain<%=fileName%>{ 
        border-top-width:<%=mainbordertopwidth%>px;
        border-bottom-width:<%=mainborderbottomwidth%>px;
        border-left-width:<%=mainborderleftwidth%>px;
        border-right-width:<%=mainborderrightwidth%>px;
        border-style:<%=mainborderstyle%>;
        border-color:<%=mainbordercolor%>;
        width:<%=mainwidth%>px;
        height:<%=mainheight%>px;
        background-color:<%=mainColor%>;
	padding:0px 0px 0px 0px;   
        border-collapse : collapse;      
        }
 #titletd<%=fileName%>{
        border-top-width:<%=titlebordertopwidth%>px;
        border-bottom-width:<%=titleborderbottomwidth%>px;
        border-left-width:<%=titleborderleftwidth%>px;
        border-right-width:<%=titleborderrightwidth%>px;
        border-style:<%=titleborderstyle%>;
        border-color:<%=titlebordercolor%>; 
	height:<%=titleheight%>px;
        font-size:<%=titlepfontsize%>px;
	font-weight:<%=titlefontweight%>;
font-style:<%=titlefontstyle%>;
        background-image:url(<%=uri%>);		
	background-color:<%=titlepbgcolor%>;
	padding:0px;
	text-align:<%=titleptalign%>;
	color:<%=titlepfontcolor%>; 
	vertical-align:middle}
.columnfontcolor<%=fileName%>{
color:<%=columnfontcolor%>;
font-size:<%=contentfontsize%>px;
font-weight: <%=contentfontweight%>;
line-height:<%=contentheight%>px;
}
.linkfontcolor<%=fileName%>{
color:<%=linkfontcolor%>;
Text-decoration: none;
font-size:<%=contentfontsize%>px;
font-weight: <%=contentfontweight%>;
line-height:<%=contentheight%>px; 
}
.filefrontimage<%=fileName%>{
  width:<%=fileimgwidth%>px;
  height:<%=fileimgheight%>px;
  background-image:url(<%=fileimguri%>);
  color: <%=filefrontfontcolor%>;
  font-size:<%=filefrontfontsize%>px;
  font-weight: <%=filefrontfontweight%>; 
 }
.titlemargin<%=fileName%>{
     color: <%=titlepfontcolor%>;
     margin-left:<%=titleleftmargin%>px;
     margin-bottom:<%=titlebottommargin%>px;
     font-size:<%=titlepfontsize%>px
}
#tcontent<%= fileName%>{
        font-family:宋体        
        line-height:<%=contentheight%>px;       
	   }
.contentmargint<%=fileName%>{
  margin-left:<%=contentmargint%>px;
}
ul{
	list-style-type:none;}
.hr<%=fileName%>{
    border-top:<%=compartlinewidth%>px <%=compartlinestyle%> <%=compartlinecolor%>;
   }
.linkalign<%=fileName%>{
   text-align:<%=linkalign%>;                 
}
.link<%=fileName%>{
Text-decoration: none;
color: <%=contentfontcolor%>;
font-size:<%=contentfontsize%>px;
font-weight: <%=contentfontweight%>;
line-height:<%=contentheight%>px; 
}
.linktitcolor<%=fileName%>{
Text-decoration: none;
color: <%=contenttextfontcolor%>;
font-size:<%=contentfontsize%>px;
font-weight: <%=contenttextfontweight%>;
line-height:<%=contentheight%>px; 
}
.externallink<%=fileName%>{
Text-decoration: none;
color: <%=externalfontcolor%>;
font-size:<%=contentfontsize%>px;
font-weight: <%=contentfontweight%>;
}
#li1<%= fileName%>{
	text-align:left;	
	list-style-type:square;
	color:<%=contentfontcolor%>;    
        font-weight: <%=contentfontweight%>;
        font-size:<%=contentfontsize%>px;       
      }
.more<%=fileName%>{
color: <%=externalfontcolor%>;
}
.time<%=fileName%>{
color:<%=timecolor%>;
font-size:<%=contentfontsize%>px;
}
.bold<%=fileName%>{ 
font-weight: <%=boldfontweight%>;
color: <%=externalfontcolor%>;
text-align:right;
margin-bottom:<%=titlebottommargin%>px;
}
.linkboldred<%=fileName%>{
Text-decoration: none;
color: red;
font-size:<%=contentfontsize%>px;
font-weight: bold;
line-height:<%=contentheight%>px; 
}
.linkbold<%=fileName%>{
Text-decoration: none;
color: <%=contentfontcolor%>;
font-size:<%=contentfontsize%>px;
font-weight: bold;
line-height:<%=contentheight%>px; 
}
.linkred<%=fileName%>{
Text-decoration: none;
color: red;
font-size:<%=contentfontsize%>px;
font-weight: <%=contentfontweight%>;
line-height:<%=contentheight%>px; 
}
<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>