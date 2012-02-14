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

String tdwidth = configuration.getStringValue(cmso, "td.width", locale); 
String fontparentscolor= configuration.getStringValue(cmso, "font.parents.color", locale); 
String fontcolor= configuration.getStringValue(cmso, "font.color", locale); 
String parentsfontweight= configuration.getStringValue(cmso, "parents.fontweight", locale);
String fontweight= configuration.getStringValue(cmso, "font.weight", locale);
String fontsize= configuration.getStringValue(cmso, "font.size", locale);
String contentheight= configuration.getStringValue(cmso, "content.height", locale);
%>
ul,ol,li,p,h1,h2,h3,h4,h5,h6,div{margin:0;padding:0;border:0;list-style-type:none;}
p,li,div{font-family:"宋体";}
.tmain<%=fileName%>{
        width:<%=mainwidth%>px;
        font-size:<%=fontsize%>px;       
        }
#tdmain<%=fileName%>{
        width:<%=tdwidth%>px;              
        }

#parentsfont<%=fileName%>{
        color:<%=fontparentscolor%>;
        font-weight: <%=parentsfontweight%>;        
}
#font<%=fileName%>{
        color:<%=fontcolor%>;
        font-weight: <%=fontweight%>;        
}
#lineheight<%=fileName%>{
   line-height:<%=contentheight%>px;
}
#a<%=fileName%>{
Text-decoration: none;
color:#000000;
}

<%
} catch (CmsException e) {
  e.printStackTrace();
}
%>
