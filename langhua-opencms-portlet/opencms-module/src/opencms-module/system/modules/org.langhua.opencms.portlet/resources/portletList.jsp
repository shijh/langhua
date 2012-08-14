<%@ taglib prefix="cms" uri="http://www.opencms.org/taglib/cms"%>
<%@page buffer="none" session="false" pageEncoding="UTF-8"
	import="java.util.*,java.lang.*,java.text.*,org.opencms.util.*,org.opencms.jsp.*,org.opencms.file.*,org.opencms.lock.*,org.opencms.main.*,org.opencms.xml.*,org.opencms.xml.types.*,org.opencms.xml.content.*,org.opencms.i18n.*,org.langhua.opencms.portlet.CmsPortletComparator,org.dom4j.Element"%>
<%
	CmsJspActionElement cms = new CmsJspActionElement(pageContext,
			request, response);
	CmsJspXmlContentBean cmsx = new CmsJspXmlContentBean(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();
	String currentPage = "";
	int currentPageNo = 0;
	String timeFormat = "yyyy-MM-dd HH:mm";
	String DEFAULT_CLASS_NAME = "portletContentStyle";
	String PORTLET_PARAMETER_NAME = "uuid";
	String PAGE_PARAMETER_NAME = "currentPage";
	String DEFAULT_UUID = "";
	try {
		String currentSite = OpenCms.getSiteManager().getCurrentSite(
				cms.getCmsObject()).getUrl();
		if (OpenCms.getSiteManager().getCurrentSite(cms.getCmsObject())
				.getSiteMatcher().equals(
						OpenCms.getSiteManager()
								.getWorkplaceSiteMatcher())) {
			currentSite = OpenCms.getSiteManager().getWorkplaceServer();
		}
		String currentSiteUrl = currentSite
				+ cms.getRequest().getContextPath()
				+ cms.getRequest().getServletPath();
		CmsFile configFile = cmso.readFile(fileUri,
				CmsResourceFilter.IGNORE_EXPIRATION);
		CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(
				cmso, configFile);
		List<Locale> locales = configuration.getLocales();
		if (!locales.contains(locale)) {
			for (Locale tempLocale : locales) {
				if (tempLocale.getLanguage().startsWith(
						locale.getLanguage().substring(0, 1))) {
					locale = tempLocale;
					break;
				}
			}
		}
		if (!locales.contains(locale)) {
			locale = new Locale("en");
		}
		String fileName = configFile.getName();
		List list = new ArrayList();
		List list_news = new ArrayList();
		String newsUrl = configuration.getStringValue(cmso, "newsUrl",
				locale);
		String numberString = configuration.getStringValue(cmso,
				"number", locale);
		Boolean isNews = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isNews", locale));
		Boolean isXmlpage = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isXmlpage", locale));
		Boolean isTabConfig = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isTabConfig", locale));
		Boolean isPointer = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isPointer", locale));
		Boolean isSortByTime = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isSortByTime", locale));
		Boolean isSortByName = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isSortByName", locale));
		Boolean isShowTime = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isShowTime", locale));
		Boolean isDisplayContent = Boolean.parseBoolean(configuration
				.getStringValue(cmso, "isDisplayContent", locale));
		String pagetarget = configuration.getStringValue(cmso,
				"pageTarget", locale);
		String timeFormatString = configuration.getStringValue(cmso,
				"timeFormat", locale);
		String className = configuration.getStringValue(cmso,
				"className", locale);
		String portlet_parameter_name = configuration.getStringValue(
				cmso, "portletParameterName", locale);
		if (className == null || className.equals("")) {
			className = DEFAULT_CLASS_NAME;
		}
		Element element_more = null;
		if (configuration.getValue("portlet-more", locale) != null) {
			element_more = ((I_CmsXmlContentValue) configuration
					.getValue("portlet-more", locale)).getElement();
		}
		Element element_title_limit = null;
		if (configuration.getValue("title-length-limit", locale) != null) {
			element_title_limit = ((I_CmsXmlContentValue) configuration
					.getValue("title-length-limit", locale))
					.getElement();
		}
		Element element_page = null;
		if (configuration.getValue("portlet-page", locale) != null) {
			element_page = ((I_CmsXmlContentValue) configuration
					.getValue("portlet-page", locale)).getElement();
			Element element_page_parameter_name = element_page
					.element("PortletPageParameterName");
			if (element_page_parameter_name != null
					&& !element_page_parameter_name.getText()
							.equals("")) {
				PAGE_PARAMETER_NAME = element_page_parameter_name
						.getText();
			}
			currentPage = request.getParameter(PAGE_PARAMETER_NAME);
			if (CmsStringUtil.isEmpty(currentPage))
				currentPage = "1";
			currentPageNo = Integer.parseInt(currentPage);
		}
		String uuidString = null;
		if (!CmsMessages.isUnknownKey(portlet_parameter_name)
				&& CmsStringUtil
						.isNotEmptyOrWhitespaceOnly(portlet_parameter_name)) {
			uuidString = request.getParameter(portlet_parameter_name);
		}

		if (isDisplayContent && uuidString != null) {
%>
<cms:include file="./portletGenericContent.jsp" />
<%
	} else {
			int number = 0;
			if (CmsStringUtil.isNotEmpty(numberString)) {
				number = Integer.parseInt(numberString);
			}
			if (!CmsMessages.isUnknownKey(timeFormatString)
					&& CmsStringUtil
							.isNotEmptyOrWhitespaceOnly(timeFormatString)) {
				timeFormat = timeFormatString;
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
				id = OpenCms.getResourceManager().getResourceType(
						"xmlpage").getTypeId();
				filter1 = CmsResourceFilter.DEFAULT_FILES
						.addRequireFile().addRequireType(id);
				List list_xmlpage = cmso
						.readResources(newsUrl, filter1);
				if (list_xmlpage != null && list_xmlpage.size() != 0) {
					for (int i = 0; i < list_xmlpage.size(); i++) {
						CmsResource source = (CmsResource) list_xmlpage
								.get(i);
						list.add(source);
					}
				}
			}
			if (isTabConfig) {
				id = OpenCms.getResourceManager().getResourceType(
						"portlet_tab_config").getTypeId();
				filter1 = CmsResourceFilter.DEFAULT_FILES
						.addRequireFile().addRequireType(id);
				List list_portlet_tab_config = cmso.readResources(
						newsUrl, filter1);
				if (list_portlet_tab_config != null
						&& list_portlet_tab_config.size() != 0) {
					for (int i = 0; i < list_portlet_tab_config.size(); i++) {
						CmsResource source = (CmsResource) list_portlet_tab_config
								.get(i);
						list.add(source);
					}
				}
			}
			if (isPointer) {
				id = OpenCms.getResourceManager().getResourceType(
						"pointer").getTypeId();
				filter1 = CmsResourceFilter.DEFAULT_FILES
						.addRequireFile().addRequireType(id);
				List list_pointer = cmso
						.readResources(newsUrl, filter1);
				if (list_pointer != null && list_pointer.size() != 0) {
					for (int i = 0; i < list_pointer.size(); i++) {
						CmsResource source = (CmsResource) list_pointer
								.get(i);
						list.add(source);
					}
				}
			}
			if (isSortByTime) {
				Collections.sort(list,
						CmsResource.COMPARE_DATE_RELEASED);
			}
			if (isSortByName) {
				CmsPortletComparator cpc = new CmsPortletComparator(
						cmso);
				Collections.sort(list, cpc);
			}
			int articleNumber = 0;
			int totalPage = 0;
			int n = 0;
			int newsNumber = 0;
			n = list.size() < number ? list.size() : number;
			if (element_page != null) {
				Element element_page_news_number = element_page
						.element("PageNewsNumber");
				if (element_page_news_number != null) {
					try {
						newsNumber = Integer
								.parseInt(element_page_news_number
										.getText());
					} catch (Exception e) {

					}

				}
				totalPage = (int) Math.ceil((double) n / newsNumber);
				articleNumber = (currentPageNo - 1) * newsNumber;
				n = list.size() < articleNumber + newsNumber ? list
						.size() : articleNumber + newsNumber;
			}
%>
<div class="<%=className%>">
<ul>
	<%
		for (; articleNumber < n; articleNumber++) {
					CmsResource source = (CmsResource) list
							.get(articleNumber);
					CmsUUID uuid = source.getStructureId();
					if (articleNumber == 0) {
						DEFAULT_UUID = uuid.toString();
					}
					String rootpath = source.getRootPath();
					String path = cms.getRequestContext().removeSiteRoot(
							rootpath);
					int source_id = source.getTypeId();
					String fileTitle = "";
					if (source_id == 30 || source_id == 121) {
						boolean result = cmso.existsResource(path);
						if (result) {
							fileTitle = cmsx.getContent(path, "Title",
									locale);
							if (CmsStringUtil
									.isEmptyOrWhitespaceOnly(fileTitle)) {
								fileTitle = cmso.readPropertyObject(source,
										"Title", true).getValue();
							}
						}
					}
					if (source_id == 6
							|| source_id == 5
							|| source_id == OpenCms.getResourceManager()
									.getResourceType("portlet_tab_config")
									.getTypeId()) {
						boolean result = cmso.existsResource(path);
						if (result) {
							fileTitle = cmso.readPropertyObject(source,
									"Title", true).getValue();
						}
					}
					if (element_title_limit != null) {
						String title_string_number_string = "";
						String title_suffix = "";
						int title_string_number = 0;
						Element element_title_string_number = element_title_limit
								.element("TitleStringNumber");
						Element element_title_suffix = element_title_limit
								.element("TitleSuffix");
						if (element_title_string_number != null) {
							title_string_number_string = element_title_string_number
									.getText();
							if (!title_string_number_string.equals("")) {
								title_string_number = Integer
										.parseInt(title_string_number_string);
							}
						}
						if (element_title_suffix != null) {
							title_suffix = element_title_suffix.getText();
						}
						if (fileTitle != null
								&& fileTitle.length() > title_string_number
								&& title_string_number > 0) {
							fileTitle = CmsStringUtil.trimToSize(fileTitle,
									title_string_number, title_suffix);
						}
					}
					if (articleNumber == (currentPageNo - 1) * 5) {
						DEFAULT_UUID = uuid.toString();
					}
	%>
	<li>
	<%
		if (isShowTime) {
						SimpleDateFormat format = new SimpleDateFormat(
								timeFormat);
	%> <span> <%=format.format(new Date(source
											.getDateCreated()))%> </span> <%
 	}
 %> <a href="<%=currentSite + cms.link(path)%>"
		target="_<%=pagetarget%>"> <%
 	if (!CmsMessages.isUnknownKey(portlet_parameter_name)
 						&& CmsStringUtil
 								.isNotEmptyOrWhitespaceOnly(portlet_parameter_name)) {
 %> <input type="hidden" name="<%=portlet_parameter_name%>"
		value="<%=uuid%>" /> <%
 	}
 %><%=fileTitle%></a></li>
	<%
		}
	%>
</ul>
<%
	if (element_page != null) {
				String pageClassName = "portletPageStyle";
				Element element_page_class_name = element_page
						.element("PageClassName");
				if (element_page_class_name != null
						&& !element_page_class_name.getText()
								.equals("")) {
					pageClassName = element_page_class_name.getText();
				}
%>
<div class="<%=pageClassName%>">
<%
	for (int i = 1; i <= totalPage; i++) {
					if (i == currentPageNo) {
%> <span><%=i%></span> <%
 	} else {
 						CmsResource source = (CmsResource) list
 								.get((i - 1) * newsNumber);
 						CmsUUID pageuuid = source.getStructureId();
 %> <span><a name="PortletPageTag"
	href="<%=currentSiteUrl + fileUri%>?<%=PAGE_PARAMETER_NAME%>=<%=i%>"><input
	type="hidden" name="<%=PAGE_PARAMETER_NAME%>" value="<%=i%>" /><input
	type="hidden" name="<%=portlet_parameter_name%>" value="<%=pageuuid%>" /><%=i%></a></span>
<%
	}
				}
%>
</div>
<%
	}
%> <%
 	if (element_more != null) {
 				String moreURL = "";
 				String moreText = "";
 				String moreTarget = "blank";
 				String moreClassName = "portletMore";
 				Element element_more_class_name = element_more
 						.element("MoreClassName");
 				if (element_more_class_name != null) {
 					moreClassName = element_more_class_name.getText();
 				}
 %>
<div class="<%=moreClassName%>">
<%
	Element element_more_URL = element_more
						.element("MoreURL");
				Element element_more_text = element_more
						.element("MoreText");
				Element element_more_target = element_more
						.element("MorePageTarget");
				if (element_more_URL != null) {
					moreURL = element_more_URL.getText();
				}
				if (element_more_text != null) {
					moreText = element_more_text.getText();
				}
				if (element_more_target != null) {
					moreTarget = element_more_target.getText();
				}
%><a href="<%=moreURL%>" class="portletMore_A" target="_<%=moreTarget%>">
<%
	if (!CmsMessages.isUnknownKey(portlet_parameter_name)
						&& CmsStringUtil
								.isNotEmptyOrWhitespaceOnly(portlet_parameter_name)) {
%><input type="hidden" name="<%=portlet_parameter_name%>"
	value="<%=DEFAULT_UUID%>" /> <%
 	}
 %><%=moreText%></a></div>
<%
	}
%>
</div>
<%
	}
	} catch (Exception e) {
		e.printStackTrace();
	}
%>

