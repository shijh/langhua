<%@ page language="java" session="false"
	import="java.util.*,
		org.apache.lucene.search.SortField,
        org.opencms.search.CmsSearchResult,
        org.opencms.search.CmsSearchParameters,
        org.opencms.i18n.CmsEncoder,
		org.opencms.util.CmsStringUtil,
		org.opencms.frontend.templateone.CmsTemplateBean,
        org.langhua.opencms.search.pipeline.CmsFilterCondition,
        org.langhua.opencms.search.pipeline.CmsSearchFilter,
        org.langhua.opencms.search.pipeline.CmsSearchPipeline,
        org.langhua.opencms.search.pipeline.Messages" pageEncoding="utf-8"%>
<%
String searchConfigurationPath = "/system/modules/org.langhua.opencms.search.pipeline/configuration/democonfig";
CmsSearchPipeline cms = new CmsSearchPipeline(pageContext, request, response, searchConfigurationPath);
cms.setSearchTimeStart();
String query = cms.getRequest().getParameter(CmsSearchPipeline.PARAM_QUERY);
if (CmsStringUtil.isEmpty(query)) query = "";
String currentPage = cms.getRequest().getParameter(CmsSearchPipeline.PARAM_PAGE);
if (CmsStringUtil.isEmpty(currentPage)) currentPage = "1";
int currentPageNo = Integer.parseInt(currentPage);
List<CmsSearchResult> results = cms.getSearchResultsList();
%>
<html>
<head>
<script type="text/javascript" src="<%=cms.link("/system/modules/org.langhua.opencms.search.pipeline/resources/search.js")%>"></script>
</head>
<body>
<table width="100%">
<tr>
<td width="30%">&nbsp;</td>
<td width="70%"><table width="100%"><tr><td>
<form action="<%=cms.link(cms.getRequestContext().getUri())%>" name="searchform" id="searchform" method="post">
 <input name="<%= CmsSearchPipeline.PARAM_PAGE %>" type="hidden" value="<%= currentPage %>" >
 <input name="<%= CmsSearchPipeline.PARAM_QUERY %>" type="text" size="45" value="<%= query %>">
 <input type="button" name="button" value="<%=Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_BUTTON_NAME_0)%>" onclick="javascript:testsubmit();"/>
</form>
</td>
</tr>
</table>
</td>
</tr>
</table>
<table width="100%">
<tr>
<td valign="top">
<%
List<CmsSearchFilter> filters = cms.getFilterList();
for (int i=0; i<filters.size(); i++) {
    CmsSearchFilter filter = filters.get(i);
%>
<table width="100%">
<tr><td width="30%"><%=  Messages.get().getBundle(cms.getRequestContext().getLocale()).key("label."+filter.getFilterName())  %></td></tr>
<%
    List<CmsFilterCondition> filterConditions = filter.getFilterConditions();
    for (int j=0; j<filterConditions.size(); j++) {
        CmsFilterCondition condition = filterConditions.get(j);
        CmsSearchParameters searchParams = new CmsSearchParameters(CmsEncoder.escape(condition.getQueryString(),"UTF-8"), cms.getSearchParameters().getFields(), cms.getSearchParameters().getRoots(), cms.getSearchParameters().getCategories(), cms.getSearchParameters().getCalculateCategories(), cms.getSearchParameters().getSort());
        searchParams.setIndex(cms.getSearchParameters().getIndex());
%>
<tr><td><a href="<%= cms.link(cms.getRequestContext().getUri()) +  CmsEncoder.unescape(searchParams.toQueryString(),"UTF-8") + "&" + CmsSearchPipeline.PARAM_SEQUENCE + "=" + j %>"><%= condition.getContent() %>(<%= condition.getResultCount() %>)</a></td></tr>
<%
    }
%>
</table><br><br>
<%
}
%>
</td>
<td width="70%">
<table width="100%">
<tr>
<td align="right" id="message_bar"></td>
</tr>
<td align="right">
<%
if (CmsStringUtil.isNotEmpty(query)) {
%>
<%=Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_SORTED_0)%> 
<%
    List<SortField> sortFields = cms.getSearchConfiguration().getSortFieldList();
    SortField currentSortField = cms.getCurrentSortField();
    for (int i=0; i<sortFields.size(); i++) {
        SortField sortField = sortFields.get(i);
        CmsSearchParameters searchParams = new CmsSearchParameters(CmsEncoder.escape(cms.getSearchParameters().getQuery(),"UTF-8"), cms.getSearchParameters().getFields(), cms.getSearchParameters().getRoots(), cms.getSearchParameters().getCategories(), cms.getSearchParameters().getCalculateCategories(), cms.getSearchParameters().getSort());
        searchParams.setIndex(cms.getSearchParameters().getIndex());
        if (searchParams.getSortName().equals(sortField.getField())) {
%><%=  Messages.get().getBundle(cms.getRequestContext().getLocale()).key("label."+sortField.getField()) %> 
<%
        } else {
            searchParams.setSortName(sortField.getField());
%><a href="<%= cms.link(cms.getRequestContext().getUri()) + CmsEncoder.unescape(searchParams.toQueryString(),"UTF-8") + "&" + CmsSearchPipeline.PARAM_PAGE + "=" + currentPageNo %>"><%= Messages.get().getBundle(cms.getRequestContext().getLocale()).key("label."+sortField.getField()) %></a> 
<%
        }
    }
}
%>
</td>
</tr>
</table>
<table width="100%">
<%
for (int i=0; i<results.size(); i++) {
    CmsSearchResult result = results.get(i);
    String resourcePath = result.getPath();
    String title = cms.property("Title", cms.getRequestContext().removeSiteRoot(resourcePath),  Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_RESULT_NO_TITLE_0)); 
%>
<tr>
<td><a href="<%= CmsStringUtil.isEmpty(cms.getRequestContext().getSiteRoot()) ? (cms.getRequest().getScheme() + "://" + cms.getRequest().getServerName() + ":" + cms.getRequest().getServerPort() + cms.getRequest().getContextPath() + cms.getRequest().getServletPath() + resourcePath) : cms.link(resourcePath) %>" target="_blank"><%= title %></a></td>
</tr>
<%
}
%>
</table>
<table align="center">
<tr>
<%
// simulate Google
CmsSearchParameters searchParams = new CmsSearchParameters(CmsEncoder.escape(cms.getSearchParameters().getQuery(),"UTF-8"), cms.getSearchParameters().getFields(), cms.getSearchParameters().getRoots(), cms.getSearchParameters().getCategories(), cms.getSearchParameters().getCalculateCategories(), cms.getSearchParameters().getSort());
searchParams.setIndex(cms.getSearchParameters().getIndex());
if (currentPageNo > 1) {
%><td><a href="<%= cms.link(cms.getRequestContext().getUri()) + CmsEncoder.unescape(searchParams.toQueryString(),"UTF-8") %>&<%= CmsSearchPipeline.PARAM_PAGE %>=<%= currentPageNo - 1 %>"><u><%=Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_PAGE_PREVIOUSE_0)%></a></td>
<%
}
int total = cms.getSearchResultTotalPages();
int start = ((currentPageNo - 10) > 0) ? (currentPageNo - 10) : 1;
int end = ((total - currentPageNo) > 9) ? (currentPageNo + 9) : total;
for (int i=start; i<=end; i++) {
    if (i == currentPageNo) {
%><td><%= i %></td>
<%
    } else {
%><td><a href="<%= cms.link(cms.getRequestContext().getUri()) +  CmsEncoder.unescape(searchParams.toQueryString(),"UTF-8") %>&<%= CmsSearchPipeline.PARAM_PAGE %>=<%= i %>"><u><%= i %></u></a></td>
<%
    }
}
if (currentPageNo < total) {
%><td><a href="<%= cms.link(cms.getRequestContext().getUri()) +  CmsEncoder.unescape(searchParams.toQueryString(),"UTF-8") %>&<%= CmsSearchPipeline.PARAM_PAGE %>=<%= currentPageNo + 1 %>"><u><%=Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_PAGE_NEXT_0)%></u></a></td>
<%
}
%>
</tr>
</table>
</td>
</tr>
</table>
<%
if (CmsStringUtil.isNotEmpty(query) && cms.getSearchResultCount() > 0) {
int param1 = (currentPageNo - 1) * cms.getSearchConfiguration().getMatchesPerPage() + 1;
int param2 = (currentPageNo == total) ? cms.getSearchResultCount() : (currentPageNo * cms.getSearchConfiguration().getMatchesPerPage());
int param3 = cms.getSearchResultCount();
String param4 = cms.getSearchTimeConsumed();
String param = Messages.get().getBundle(cms.getRequestContext().getLocale()).key(Messages.GUI_SEARCH_RESULT_5,new Object[] {String.valueOf(param1),String.valueOf(param2),String.valueOf(param3),query,param4});
%>
<script type="text/javascript">
<!--
  var element = document.getElementById("message_bar");
  element.innerHTML = "<%=param%>";
//-->
</script>
<%
}
%>
</body>
</html>