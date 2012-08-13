<%@ page pageEncoding="UTF-8" import="java.util.*,
                org.opencms.jsp.CmsJspActionElement,
                org.opencms.main.OpenCms,
                javolution.util.FastMap,
		        org.ofbiz.entity.*,
		        org.ofbiz.entity.model.*,
                org.ofbiz.service.ServiceUtil,
                org.ofbiz.service.ModelService,
                org.ofbiz.base.util.*,
		        org.langhua.ofbiz.rmi.client.*" %><%

Locale locale = request.getLocale();
CmsJspActionElement cms = new CmsJspActionElement(pageContext, request, response);
cms.getRequestContext().setLocale(locale);
%><!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html;charset=UTF-8" >
<title><%= cms.label("opencms.ofbiz.demo.pagetitle") %></title>
</head>
<body>
<table width='90%' align='center' cellspacing='1' cellpadding='1' border='1' class='xmlTable'>
<%
OFBizRmiClient rc = new OFBizRmiClient("rmi://localhost:1099/RMIDispatcher");
Map result = FastMap.newInstance();
Map context = FastMap.newInstance();
context.put("message", "Remote Service Test");
result = rc.service("testScv", context);
%>
	<tr>
		<td colspan="2"><%= cms.label("opencms.ofbiz.demo.testservice") %><%= result %> </td>
	</tr>
	<tr>
		<td colspan="2">&nbsp;</td>
	</tr>
	<tr>
		<td colspan="2"><%= cms.label("opencms.ofbiz.demo.promotioncategory") %></td>
	</tr>
<%
context.remove("message");
context.put("categoryId", "PROMOTIONS");
result = rc.service("getProductCategoryMembers", context);
String ofbizUrl = OpenCms.getModuleManager().getModule("org.langhua.opencms.ofbiz").getParameter("ofbizurl");
if (ServiceUtil.isError(result)) {
%>
	<tr>
		<td width="20%"><%= cms.label("opencms.ofbiz.demo.error") %><%= result %> </td>
	</tr>
<%
} else {
	List products = (List) result.get("categoryMembers");

%>
	<tr>
		<td width="20%"><%= cms.label("opencms.ofbiz.demo.total") %></td><td align="right"><%= products.size() %> <%= cms.label("opencms.ofbiz.demo.products") %></td>
	</tr>
<%
	Iterator i = products.iterator();
	while(i.hasNext()) {
		GenericEntity product = (GenericEntity) i.next();
		Map fields = product.getAllFields();
		String productId = (String) fields.get("productId");

		Map productResult = rc.service("getProduct", UtilMisc.toMap("productId", productId));
		String productContent = "";
		GenericEntity productValue = null;
		if (productResult != null) {
			productValue = (GenericEntity) productResult.get("product");
			if (productValue != null) {
				fields = productValue.getAllFields();
				productContent = (String) fields.get("internalName") + "<br>" + (String) fields.get("description") + "<br>" + productId;
				Map priceResult = rc.service("calculateProductPrice", UtilMisc.toMap("product", productValue));
				Object listPrice = priceResult.get("listPrice");
				if (listPrice != null) productContent += "<br>" + cms.label("opencms.ofbiz.demo.listprice") + String.valueOf(listPrice);
				Object basePrice = priceResult.get("basePrice");
				if (basePrice != null) productContent += "<br>" + cms.label("opencms.ofbiz.demo.baseprice") + String.valueOf(basePrice);
				Object promoPrice = priceResult.get("promoPrice");
				if (promoPrice != null) productContent += "<br>" + cms.label("opencms.ofbiz.demo.promotionprice") + String.valueOf(promoPrice);
				Object specialPromoPrice = priceResult.get("specialPromoPrice");
				if (specialPromoPrice != null) productContent += "<br>" + cms.label("opencms.ofbiz.demo.specialprice") + String.valueOf(specialPromoPrice);
			}
		}
                productResult = rc.service("findProductById", UtilMisc.toMap("idToFind", productId));
                if (productResult != null) {
                        List productsList = (List) productResult.get("productsList");
                        if (productsList != null) {
                                productContent += "<br>" + cms.label("opencms.ofbiz.demo.findproduct.0") + productsList.size() + cms.label("opencms.ofbiz.demo.findproduct.1");
                        }
                }
%>
	<tr>
		<td width="20%"><img src="<%= ofbizUrl %><%= fields.get("smallImageUrl") %>" border="0"></td><td align="left"><%= productContent %></td>
	</tr>
<%
	}
}
%>
</table>
</body>
</html>
