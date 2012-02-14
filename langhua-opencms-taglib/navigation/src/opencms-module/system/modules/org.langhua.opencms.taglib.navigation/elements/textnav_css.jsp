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
String imgwidth = configuration.getStringValue(cmso, "img.width", locale);
String mainheight = configuration.getStringValue(cmso, "main.height", locale); 
String titlebgcolor1 = configuration.getStringValue(cmso, "title.bgcolor1", locale); 
String titlebgcolor2 = configuration.getStringValue(cmso, "title.bgcolor2", locale); 
String fontweight= configuration.getStringValue(cmso, "font.weight", locale);
String fontsize= configuration.getStringValue(cmso, "font.size", locale);
String fontupcolor= configuration.getStringValue(cmso, "font.up.color", locale);
String fontdowncolor= configuration.getStringValue(cmso, "font.down.color", locale);
String trmargin= configuration.getStringValue(cmso, "tr.margin", locale);
String imgbgimage= configuration.getStringValue(cmso, "img.bgimage", locale);
String fontparentscolor= configuration.getStringValue(cmso, "font.parents.color", locale);
String parentsfontweight= configuration.getStringValue(cmso, "parents.fontweight", locale);
int mwidth=Integer.parseInt(mainwidth);
if(imgwidth.equals("")){
   imgwidth="0";
}
int iwidth=Integer.parseInt(imgwidth);
int rowwidth=mwidth-iwidth;
int mheight=Integer.parseInt(mainheight);
int rowheight=mheight/2;
String uri="";
if(CmsStringUtil.isNotEmptyOrWhitespaceOnly(imgbgimage)){
uri = cms.link(imgbgimage);
}
%>
#parentsfont<%=fileName%>{
        color:<%=fontparentscolor%>;
        font-weight: <%=parentsfontweight%>;        
}

.content<%=fileName%>{         
 width:<%=mainwidth%>px;
 font-size:<%=fontsize%>px;
 font-weight: <%=fontweight%>;
 text-align:left;
}

.img<%=fileName%>{         
float:left; 
width:<%=imgwidth%>px; 
height:<%=mainheight%>px;
background:url(<%=uri%>); 
}
.uprow<%=fileName%>{         
   float:left;   
   width:<%=rowwidth%>px; 
   height:<%=rowheight%>px; 
   line-height:<%=rowheight%>px;   
   background-color:<%=titlebgcolor1%>; 
  
}
.downrow<%=fileName%>{         
   float:left;    
   width:<%=rowwidth%>px; 
   height:<%=rowheight%>px;  
   line-height:<%=rowheight%>px;     
   background-color:<%=titlebgcolor2%>;
 
}
.tr<%=fileName%>{         
   margin-left:<%=trmargin%>px;    
   display:inline;
}
.uplink<%=fileName%>{
color:<%=fontupcolor%>; 
Text-decoration: none;
}
.downlink<%=fileName%>{
color:<%=fontdowncolor%>; 
Text-decoration: none;
}		
<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>	