 <%@ page language="java" session="false"
	import="java.util.*,
        org.opencms.main.*,
        org.opencms.search.*,
		org.opencms.jsp.*,
        org.opencms.file.*, 
		org.opencms.test.*,
		org.opencms.search.fields.*,
		org.apache.lucene.search.SortField,
        org.langhua.opencms.search.pipeline.*" pageEncoding="utf-8"%>
<%
	String searchConfigurationPath = "/system/modules/org.langhua.opencms.search.pipeline/configuration/democonfig";
        CmsSearchPipeline cmsSearchPipelineContent = new CmsSearchPipeline(pageContext, request, response, searchConfigurationPath);
        boolean flag = false;
        flag = cmsSearchPipelineContent.isIndexExist();
        if(flag){
%>
function testonchange() {
  alert(document.searchform.query.value);
 if(document.searchform.query.value ==""){
     alert("请输入查询内容"); 
     return false;
    }
  document.searchform.submit(); 
}
function testsubmit() {
   if(document.searchform.query.value ==""){
     alert("请输入查询内容"); 
     return false;
    }
  document.searchform.submit();
}
<%
 }
%>