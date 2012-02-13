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
	        org.opencms.xml.content.*"%><%
	        
	CmsJspActionElement cms = new CmsJspActionElement(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();
	try {
		CmsFile configFile = cmso.readFile(fileUri,
				CmsResourceFilter.IGNORE_EXPIRATION);
		CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(
				cmso, configFile);
		String fileName = configFile.getName();
		String mainwidth = configuration.getStringValue(cmso,
				"main.width", locale);
		String mainheight = configuration.getStringValue(cmso,
				"main.height", locale);
		String lineheight = configuration.getStringValue(cmso,
				"line.height", locale);
		String mainbordertopwidth = configuration.getStringValue(cmso,
				"main.border.top.width", locale);
		String mainborderbottomwidth = configuration.getStringValue(
				cmso, "main.border.bottom.width", locale);
		String mainborderleftwidth = configuration.getStringValue(cmso,
				"main.border.left.width", locale);
		String mainborderrightwidth = configuration.getStringValue(
				cmso, "main.border.right.width", locale);
		String mainborderstyle = configuration.getStringValue(cmso,
				"main.border.style", locale);
		String mainbordercolor = configuration.getStringValue(cmso,
				"main.border.color", locale);
		String textalign = configuration.getStringValue(cmso,
				"text.align", locale);
		String fontcolor = configuration.getStringValue(cmso,
				"fontcolor", locale);
		String fontsize = configuration.getStringValue(cmso,
				"fontsize", locale);
		String bgcolor = configuration.getStringValue(cmso, "bgcolor",
				locale);
		String bgimage = configuration.getStringValue(cmso, "bgimage",
				locale);
		String bgimguri = "";
		if (CmsStringUtil.isNotEmpty(bgimage)) {
			bgimguri = cms.link(bgimage);
		}
%>.topTime<%=fileName%>{ border-top-width:<%=mainbordertopwidth%>px;
border-bottom-width:<%=mainborderbottomwidth%>px; border-left-width:<%=mainborderleftwidth%>px;
border-right-width:<%=mainborderrightwidth%>px; border-style:<%=mainborderstyle%>;
border-color:<%=mainbordercolor%>; height:<%=mainheight%>px; width:<%=mainwidth%>px;
line-height:<%=lineheight%>px; text-align:<%=textalign%>; color:<%=fontcolor%>;
font-size:<%=fontsize%>px; background: <%=bgcolor%> url(<%=bgimguri%>); white-space: nowrap; }<%
	} catch (CmsException e) {
		e.printStackTrace();
	}
%>