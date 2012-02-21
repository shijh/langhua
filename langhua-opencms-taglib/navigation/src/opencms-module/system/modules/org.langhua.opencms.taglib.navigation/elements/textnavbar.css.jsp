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
	CmsJspActionElement cms = new CmsJspActionElement(pageContext, request,
			response);
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
		String mainfontname = configuration.getStringValue(cmso,
				"main.font.name", locale);
		String mainfontsize = configuration.getStringValue(cmso,
				"main.font.size", locale);
		String mainfontcolor = configuration.getStringValue(cmso,
				"main.font.color", locale);
		String mainfontweight = configuration.getStringValue(cmso,
				"main.font.weight", locale);
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
		String bgcolor = configuration.getStringValue(cmso, "main.bgcolor",
				locale);
		String bgimage = configuration.getStringValue(cmso, "main.bgimage",
				locale);
		String bgimguri = "";
		if (CmsStringUtil.isNotEmpty(bgimage)) {
			bgimguri = cms.link(bgimage);
		}

		String columnwidth = configuration.getStringValue(cmso, "column.width",
				locale);
		String columnfontcolor = configuration.getStringValue(cmso,
				"column.font.color", locale);
		String columnfontname = configuration.getStringValue(cmso,
				"column.font.name", locale);
		String columnfontweight = configuration.getStringValue(cmso,
				"column.font.weight", locale);
		String columnfontsize = configuration.getStringValue(cmso,
				"column.font.size", locale);
		boolean nowrap = Boolean.parseBoolean(configuration.getStringValue(cmso, "nowrap", locale));
%>.TNBmain<%=fileName%>{ width:<%=mainwidth%>px; height:<%= mainheight %>px;
font-size:<%=mainfontsize%>px; font-family:<%= mainfontname %>; color:<%= mainfontcolor %>; font-weight: <%= mainfontweight %>;<%= nowrap ? "white-space: nowrap; overflow: hidden;" : "" %>
border-top-width:<%=mainbordertopwidth%>px;
border-bottom-width:<%=mainborderbottomwidth%>px; border-left-width:<%=mainborderleftwidth%>px;
border-right-width:<%=mainborderrightwidth%>px; border-style:<%=mainborderstyle%>;
border-color:<%=mainbordercolor%>; background: <%=bgcolor%> url(<%=bgimguri%>);}
.TNBcolumn<%=fileName%>{ <%= columnwidth.equals("0") ? "" : ("width: " + columnwidth + "px; display: inline-block;") %>font-family:<%= columnfontname %>; color:<%= columnfontcolor %>;
font-weight:<%=columnfontweight%>;font-size:<%=columnfontsize%>px;
}<%
	} catch (CmsException e) {
		e.printStackTrace();
	}
%>
