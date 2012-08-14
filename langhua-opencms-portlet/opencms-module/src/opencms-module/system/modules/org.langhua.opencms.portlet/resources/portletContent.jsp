<%@page buffer="none" session="false" pageEncoding="UTF-8"
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
	        org.opencms.i18n.*,
	        org.langhua.opencms.portlet.CmsPortletComparator,
	        org.dom4j.Element"%><%
	CmsJspActionElement cms = new CmsJspActionElement(pageContext, request,
			response);
	CmsJspXmlContentBean cmsx = new CmsJspXmlContentBean(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();
	String pageurl = request.getParameter("pageurl");
	String fName = OpenCms.getSiteManager().getCurrentSite(cmso).getUrl();
	String timeFormat="yyyy-MM-dd HH:mm";
	String DEFAULT_CLASS_NAME="portletContentStyle";
	try {
		String currentSite = OpenCms.getSiteManager().getCurrentSite(cms.getCmsObject()).getUrl();
		if (OpenCms.getSiteManager().getCurrentSite(cms.getCmsObject()).getSiteMatcher()
				.equals(OpenCms.getSiteManager().getWorkplaceSiteMatcher())) {
			currentSite = OpenCms.getSiteManager().getWorkplaceServer();
		}
		String currentSiteUrl = currentSite	+ cms.getRequest().getContextPath()	+ cms.getRequest().getServletPath();
        CmsFile configFile = cmso.readFile(fileUri,
                CmsResourceFilter.IGNORE_EXPIRATION);
        CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso, configFile);
        List<Locale> locales = configuration.getLocales();
        if (!locales.contains(locale)) {
            for (Locale tempLocale : locales) {
                if (tempLocale.getLanguage().startsWith(locale.getLanguage().substring(0,1))) {
                    locale = tempLocale;
                    break;
                }
            }
        }
        if (!locales.contains(locale)){
            locale = new Locale("en");
        }

		if (pageurl == null) {
			String fileName = configFile.getName();
			List list = new ArrayList();
			List list_news = new ArrayList();
			String newsUrl = configuration.getStringValue(cmso,"newsUrl", locale);
			String numberString = configuration.getStringValue(cmso, "number", locale);
			Boolean isNews = Boolean.parseBoolean(configuration.getStringValue(cmso, "isNews", locale));
			Boolean isXmlpage = Boolean.parseBoolean(configuration.getStringValue(cmso, "isXmlpage", locale));
			Boolean isSortByTime = Boolean.parseBoolean(configuration.getStringValue(cmso, "isSortByTime", locale));
			Boolean isSortByName = Boolean.parseBoolean(configuration.getStringValue(cmso, "isSortByName", locale));
			Boolean isShowTime = Boolean.parseBoolean(configuration.getStringValue(cmso, "isShowTime", locale));
			String pagetarget = configuration.getStringValue(cmso, "pageTarget", locale);
			String timeFormatString = configuration.getStringValue(cmso, "timeFormat", locale);
			String className = configuration.getStringValue(cmso, "className", locale);
			if(className==null || className.equals("")){
				className=DEFAULT_CLASS_NAME;
			}
			Element element_more =null;
			if(configuration.getValue("portlet-more", locale)!=null){
				element_more =((I_CmsXmlContentValue)configuration.getValue("portlet-more", locale)).getElement();
			}
			int number = 0;
			if (CmsStringUtil.isNotEmpty(numberString)) {
				number = Integer.parseInt(numberString);
			}
			if(!CmsMessages.isUnknownKey(timeFormatString)
					&& CmsStringUtil
					.isNotEmptyOrWhitespaceOnly(timeFormatString)){
				timeFormat=timeFormatString;
			}
			CmsResourceFilter filter1 = null;
			int id = 0;
			if (isNews) {
				List list_newstype_news = null;
				List list_newstype_ttnews = null;
				try {
					id = OpenCms.getResourceManager().getResourceType(
							"news").getTypeId();
					filter1 = CmsResourceFilter.DEFAULT_FILES
							.addRequireFile().addRequireType(id);
					list_newstype_news = cmso.readResources(newsUrl,
							filter1);
				} catch (Exception e) {
				}
				try {
					id = OpenCms.getResourceManager().getResourceType(
							"ttnews").getTypeId();
					filter1 = CmsResourceFilter.DEFAULT_FILES
							.addRequireFile().addRequireType(id);
					list_newstype_ttnews = cmso.readResources(newsUrl,
							filter1);
				} catch (Exception e) {
				}
				if (list_newstype_news != null) {
					for (int i = 0; i < list_newstype_news.size(); i++) {
						CmsResource source = (CmsResource) list_newstype_news
								.get(i);
						list.add(source);
					}
				}
				if (list_newstype_ttnews != null) {
					for (int i = 0; i < list_newstype_ttnews.size(); i++) {
						CmsResource source = (CmsResource) list_newstype_ttnews
								.get(i);
						list.add(source);
					}
				}
			}
			if (isXmlpage) {
				id = OpenCms.getResourceManager().getResourceType("xmlpage").getTypeId();
				filter1 = CmsResourceFilter.DEFAULT_FILES.addRequireFile().addRequireType(id);
				List list_xmlpage = cmso.readResources(newsUrl, filter1);
				if (list_xmlpage != null && list_xmlpage.size() != 0) {
					for (int i = 0; i < list_xmlpage.size(); i++) {
						CmsResource source = (CmsResource) list_xmlpage.get(i);
						list.add(source);
					}
				}
			}
			if (isSortByTime) {
				Collections.sort(list, CmsResource.COMPARE_DATE_RELEASED);
			}
			if (isSortByName) {
				CmsPortletComparator cpc = new CmsPortletComparator(cmso);
				Collections.sort(list, cpc);
			}
			int n = list.size() < number ? list.size() : number;
%>
<div class="<%=className %>">
	<ul>
<%
            
            for (int i = 0; i < n; i++) {
                CmsResource source = (CmsResource) list.get(i);
                String rootpath = source.getRootPath();
                String path = cms.getRequestContext().removeSiteRoot(rootpath);
                int source_id = source.getTypeId();
                String fileTitle = "";
                if (source_id == 30 || source_id == 121) {
                    boolean result = cmso.existsResource(path);
                    if (result) {
                        fileTitle = cmsx.getContent(path, "Title", locale);
                        if (CmsStringUtil.isEmptyOrWhitespaceOnly(fileTitle)) {
                            fileTitle = cmso.readPropertyObject(source, "Title", true).getValue();
                        }
                    }
                }
                if (source_id == 6 || source_id == 5) {
                    boolean result = cmso.existsResource(path);
                    if (result) {
                        fileTitle = cmso.readPropertyObject(source, "Title", true).getValue();
                    }
                }
%>
		    <li>
		    	<a href="<%=path%>" title="<%=fileTitle %>" target="_<%=pagetarget%>"><%=fileTitle%></a>
		    	<% if(isShowTime){
		    		SimpleDateFormat format = new SimpleDateFormat(timeFormat);
		    	%>
		    	<span>
		    		<%= format.format(new Date(source.getDateCreated())) %>
		    	</span>
		    	<% }%>
		    </li>
<%
            }
%>
	</ul><% 
	if(element_more!=null){
		String moreURL=null;
		String moreText=null;
%>
	<div class="portletMore">
<%
		Element element_more_URL=element_more.element("MoreURL");
		Element element_more_text=element_more.element("MoreText");
		if(element_more_URL!=null){ 
			moreURL=element_more_URL.getText();
		}
		if(element_more_text!=null){
			moreText=element_more_text.getText();
		}
		%><a href="<%=moreURL %>" class="portletMore_A" target="_blank"><%=moreText %></a>
	</div>
	<%} %>
</div>
<%
	} else {
			String fileTitle = null;
			String subTitle = null;
			String author = null;
			String teaser = null;
			String text = null;
			String imageurl = null;
			String description = null;
			int source_id = 0;
			//CmsUUID uid=new CmsUUID(pageurl);
			//CmsResource source = cmso.readResource(uid);
			//if(source.isFile()){
			CmsResource source = cmso.readResource(pageurl);
			CmsFile configFile1 = cmso.readFile(source);
		        CmsXmlContent configuration1 = CmsXmlContentFactory.unmarshal(cmso, configFile1);
			source_id = source.getTypeId();
			String linkurl = "";
				if (source_id == 30) {
					fileTitle = configuration1.getStringValue(cmso,"Title",locale);
					subTitle = configuration1.getStringValue(cmso,"SubTitle",locale);
					author = configuration1.getStringValue(cmso,"Author",locale);
					text = configuration1.getStringValue(cmso,"Text",locale);
					try{
					imageurl=((I_CmsXmlContentValue)configuration1.getValue("Image", locale)).getElement().element("Image").element("link").element("target").getText();
					}catch (Exception e){
					//do nothing
					}							
				}
				if (source_id == 121) {
					fileTitle = configuration1.getStringValue(cmso,"Title",locale);
					subTitle = configuration1.getStringValue(cmso,"SubTitle",locale);
					author = configuration1.getStringValue(cmso,"Author",locale);
					try{
					text =((I_CmsXmlContentValue)configuration1.getValue("Paragraph", locale)).getElement().element("Text").getText();
					}catch (Exception e){
					//do nothing
					}
					try{
					imageurl=((I_CmsXmlContentValue)configuration1.getValue("Paragraph", locale)).getElement().element("Image").element("Image").element("link").element("target").getText();
					}catch (Exception e){
					//do nothing
					}		
					//element_description =element_image.element("Description");
				}
				if (source_id == 6) {
					fileTitle = cmso.readPropertyObject(source, "Title", true).getValue();
					description = cmso.readPropertyObject(source, "Description", true).getValue();
					imageurl = cmso.readPropertyObject(source, "NavImage", true).getValue();
					text = configuration1.getStringValue(cmso, "element", locale);
				}
%><br/>
<table width="100%" border="0" cellpadding="0" cellspacing="0">
	<tr>
		<td align="center">
		<div><font
			style="font-size: 24px; font-weight: bold; padding-top: 5px; padding-bottom: 5px;"><%=fileTitle %></font></div>
		</td>
	</tr>
<%	if(subTitle !=null){%>
	<tr>
		<td align="center">
		<div><font
			style="font-size: 12px; padding-top: 5px; padding-bottom: 5px;"><%=subTitle %></font></div>
		</td>
	</tr>
	<%}%>
	<tr>
		<td align="center">
		<div style="margin-top: 10px; margin-bottom: 5px;"><font
			style="font-size: 12px; padding-top: 5px; padding-bottom: 5px;">
		<%
			if (locale.getLanguage().startsWith("zh")) {
		%>作者:<%
			} else {
		%>Written by <%
			}
		%><%=author%></font></div>
		</td>
	</tr>
	<%
				if (imageurl!=null) {
	%>
	<tr>
		<td align="center">
		<div style="margin-bottom: 5px;"><img
			src="<%=currentSiteUrl + imageurl %>" /></div>
		</td>
	</tr>
	<%
		}
				if (description!=null) {
	%>
	<tr>
		<td align="center">
		<div style="margin-bottom: 5px;"><font
			style="font-size: 11px; padding-top: 5px; padding-bottom: 5px;"><%=description%></font></div>
		</td>
	</tr>
	<%
		}
				if (text!=null) {
	%>
	<tr>
		<td>
		<div><font
			style="word-break: break-all; text-indent: 2em; font-size: 14px; padding-top: 5px; padding-bottom: 5px;line-height:18px;"><%=text%></font></div>
					</td>
	</tr>
	<%
		}
	%>
</table>
<%
	//}
	}
	} catch (Exception e) {
		e.printStackTrace();
	}
%>

