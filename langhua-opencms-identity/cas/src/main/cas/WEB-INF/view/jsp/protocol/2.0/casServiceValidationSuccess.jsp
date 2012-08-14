<%@ page session="false" pageEncoding="UTF-8"
%><%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"
%><%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn"
%><cas:serviceResponse xmlns:cas='http://www.yale.edu/tp/cas'>
	<cas:authenticationSuccess>
		<cas:user>${fn:escapeXml(assertion.chainedAuthentications[fn:length(assertion.chainedAuthentications)-1].principal.id)}</cas:user>
<c:if
 test="${fn:length(assertion.chainedAuthentications[fn:length(assertion.chainedAuthentications)-1].principal.attributes) > 0}">			<cas:attributes>
<c:forEach var="attr" items="${assertion.chainedAuthentications[fn:length(assertion.chainedAuthentications)-1].principal.attributes}"
	varStatus="loopStatus" begin="0" end="${fn:length(assertion.chainedAuthentications[fn:length(assertion.chainedAuthentications)-1].principal.attributes)}" step="1">
				<cas:attribute>
					<cas:name>${fn:escapeXml(attr.key)}</cas:name>
					<cas:value>${fn:escapeXml(attr.value)}</cas:value>
				</cas:attribute>
</c:forEach>				</cas:attributes>
</c:if>
<c:if test="${not empty pgtIou}">
		<cas:proxyGrantingTicket>${pgtIou}</cas:proxyGrantingTicket>
</c:if>
<c:if test="${fn:length(assertion.chainedAuthentications) > 1}">
		<cas:proxies>
<c:forEach var="proxy" items="${assertion.chainedAuthentications}" varStatus="loopStatus" begin="0" end="${fn:length(assertion.chainedAuthentications)-2}" step="1">
			<cas:proxy>${fn:escapeXml(proxy.principal.id)}</cas:proxy>
</c:forEach>
		</cas:proxies>
</c:if>
	</cas:authenticationSuccess>
</cas:serviceResponse>
