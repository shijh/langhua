<%@page buffer="none" session="false" 
		import="java.util.*, 
			java.lang.*,
			java.text.*,
			org.opencms.util.*,
			org.opencms.jsp.*,
			org.opencms.file.*,
			org.opencms.lock.*,
			org.opencms.main.*"%><%

CmsJspActionElement cms = new CmsJspActionElement(pageContext, request, response);
String filename = cms.getRequestContext().getUri();
String folder = cms.getRequestContext().getFolderUri();
String title = cms.property("Title", folder);
CmsResourceFilter filter = CmsResourceFilter.ALL;
filter = filter.addTopLatest(10);

long t1 = System.currentTimeMillis();
List<CmsResource> resources = cms.getCmsObject().readResources("/sites/default/", filter, true);
long t2 = System.currentTimeMillis();
%>
<table width="760">
        <tr>
	  <td>Get <%= resources.size() %> top latest resources.</td>
        </tr>
<%
for (int i=0; i< resources.size(); i++) {
%>
	<tr>
	  <td><%= ((CmsResource) resources.get(i)).getRootPath() %></td>
	</tr>
<%
}
%>
        <tr>
	  <td>Time spent: <%= (t2-t1)/1000.0 %> seconds.</td>
        </tr>
<%
// filter = filter.addRequireType(OpenCms.getResourceManager().getResourceType("news").getTypeId());
for (int i=0; i<10; i++) {
filter = filter.addPagedLatest(30*i, 30);
t1 = System.currentTimeMillis();
resources = cms.getCmsObject().readResources("/sites/default/", filter, true);
int total = cms.getCmsObject().countResources("/sites/default/", filter, true);
t2 = System.currentTimeMillis();
%>
	<tr>
	  <td>Get <%= resources.size() %> resources start from row <%= filter.getStartRow() %> of <%= total %>.</td>
        </tr>
        <tr>
	  <td>Time spent: <%= (t2-t1)/1000.0 %> seconds.</td>
        </tr>
<%
}
%>
<table>
