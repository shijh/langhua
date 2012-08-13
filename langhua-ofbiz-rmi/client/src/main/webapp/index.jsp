<%@ page import="java.util.*,
                javolution.util.FastMap,
		        org.ofbiz.entity.*,
		        org.ofbiz.entity.model.*,
                org.ofbiz.service.ServiceUtil,
                org.ofbiz.service.ModelService,
                org.ofbiz.base.util.*,
		        org.langhua.ofbiz.rmi.client.*" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html;charset=UTF-8" >
<title>OFBiz RMI Test - Product List</title>
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
		<td colspan="2">Test RMI Service: <%= result %> </td>
	</tr>
	<tr>
		<td colspan="2">&nbsp;</td>
	</tr>
	<tr>
		<td colspan="2">Listing products in PROMOTIONS category by OFBiz RMI service</td>
	</tr>
<%
context.remove("message");
context.put("categoryId", "PROMOTIONS");
result = rc.service("getProductCategoryMembers", context);
String ofbizUrl = "http://localhost";
if (ServiceUtil.isError(result)) {
%>
	<tr>
		<td width="20%">Error: <%= result %> </td>
	</tr>
<%
} else {
	List products = (List) result.get("categoryMembers");

%>
	<tr>
		<td width="20%">Total</td><td align="right"><%= products.size() %> product(s)</td>
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
				if (listPrice != null) productContent += "<br>List Price:" + String.valueOf(listPrice);
				Object basePrice = priceResult.get("basePrice");
				if (basePrice != null) productContent += "<br>Base Price:" + String.valueOf(basePrice);
				Object promoPrice = priceResult.get("promoPrice");
				if (promoPrice != null) productContent += "<br>Promotion Price:" + String.valueOf(promoPrice);
				Object specialPromoPrice = priceResult.get("specialPromoPrice");
				if (specialPromoPrice != null) productContent += "<br>Special Price:" + String.valueOf(specialPromoPrice);
			}
		}
                productResult = rc.service("findProductById", UtilMisc.toMap("idToFind", productId));
                if (productResult != null) {
                        List productsList = (List) productResult.get("productsList");
                        if (productsList != null) {
                                productContent += "<br>There are " + productsList.size() + " products can be found related to this product.";
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