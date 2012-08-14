<%@page buffer="none" session="false" pageEncoding="UTF-8"
	import="java.util.*,java.lang.*,java.text.*,org.opencms.util.*,org.opencms.jsp.*,org.opencms.file.*,org.opencms.lock.*,org.opencms.main.*,org.opencms.xml.*,org.opencms.xml.types.*,org.opencms.xml.content.*,org.dom4j.Element"%><%
	CmsJspActionElement cms = new CmsJspActionElement(pageContext,
			request, response);
	CmsObject cmso = cms.getCmsObject();
	Locale locale = cms.getRequestContext().getLocale();
	String fileUri = cms.getRequestContext().getUri();

	CmsFile configFile = cmso.readFile(fileUri,
			CmsResourceFilter.IGNORE_EXPIRATION);
	CmsXmlContent configuration = CmsXmlContentFactory.unmarshal(cmso,
			configFile);
	List locales = configuration.getLocales();
	if (locales != null && locales.size() > 0
			&& !locales.contains(locale)) {
		locale = (Locale) locales.get(0);
	}
	String prefix = "";
	String nameSpace = "";
%><?xml version="1.0" encoding="UTF-8"?>
<%@page import="org.dom4j.Element"%><portlet-app
	xmlns="http://java.sun.com/xml/ns/portlet/portlet-app_2_0.xsd"
	xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://java.sun.com/xml/ns/portlet/portlet-app_2_0.xsd http://java.sun.com/xml/ns/portlet/portlet-app_2_0.xsd"
	version="2.0">
<%
	List values = configuration.getValues("portlet", locale);
	for (int i = 0; i < values.size(); i++) {
		Element element = ((I_CmsXmlContentValue) values.get(i))
				.getElement();
		Element proessingElement = element
				.element("supported-processing-event");
		Element publishingElement = element
				.element("supported-publishing-event");
		if (proessingElement != null) {
			prefix = "";
			nameSpace = "";
			Element prefixElement = proessingElement
					.element("namespace-prefix");
			if (prefixElement != null) {
				prefix = prefixElement.getText();
				proessingElement.remove(prefixElement);
			}
			Element nameSpaceElement = proessingElement
					.element("namespace");
			if (nameSpaceElement != null) {
				nameSpace = nameSpaceElement.getText();
				proessingElement.remove(nameSpaceElement);
			}
			Element qnameElement = proessingElement.element("qname");
			if (!nameSpace.equals("") && qnameElement != null) {
				qnameElement.addNamespace(prefix, nameSpace);
				if (!(qnameElement.getText().split(":"))[0].toString()
						.equals(prefix)) {
					qnameElement.setText(prefix + ":"
							+ qnameElement.getText());
				}
			}
		}
		if (publishingElement != null) {
			prefix = "";
			nameSpace = "";
			Element prefixElement = publishingElement
					.element("namespace-prefix");
			if (prefixElement != null) {
				prefix = prefixElement.getText();
				publishingElement.remove(prefixElement);
			}
			Element nameSpaceElement = publishingElement
					.element("namespace");
			if (nameSpaceElement != null) {
				nameSpace = nameSpaceElement.getText();
				publishingElement.remove(nameSpaceElement);
			}
			Element qnameElement = publishingElement.element("qname");
			if (!nameSpace.equals("") && qnameElement != null) {
				qnameElement.addNamespace(prefix, nameSpace);
				if (!(qnameElement.getText().split(":"))[0].toString()
						.equals(prefix)) {
					qnameElement.setText(prefix + ":"
							+ qnameElement.getText());
				}
			}
		}
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("custom-portlet-mode", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("custom-window-state", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("user-attribute", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("security-constraint", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("resource-bundle", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("filter", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("filter-mapping", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("default-namespace", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("event-definition", locale);
	for (int i = 0; i < values.size(); i++) {
		Element element = ((I_CmsXmlContentValue) values.get(i))
				.getElement();
		if (element != null) {
			prefix = "";
			nameSpace = "";
			Element prefixElement = element.element("namespace-prefix");
			if (prefixElement != null) {
				prefix = prefixElement.getText();
				element.remove(prefixElement);
			}
			Element nameSpaceElement = element.element("namespace");
			if (nameSpaceElement != null) {
				nameSpace = nameSpaceElement.getText();
				element.remove(nameSpaceElement);
			}
			Element qnameElement = element.element("qname");
			if (!nameSpace.equals("") && qnameElement != null) {
				qnameElement.addNamespace(prefix, nameSpace);
				if (!(qnameElement.getText().split(":"))[0].toString()
						.equals(prefix)) {
					qnameElement.setText(prefix + ":"
							+ qnameElement.getText());
				}
			}
		}
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("public-render-parameter", locale);
	for (int i = 0; i < values.size(); i++) {
		Element element = ((I_CmsXmlContentValue) values.get(i))
				.getElement();
		if (element != null) {
			prefix = "";
			nameSpace = "";
			Element prefixElement = element.element("namespace-prefix");
			if (prefixElement != null) {
				prefix = prefixElement.getText();
				element.remove(prefixElement);
			}
			Element nameSpaceElement = element.element("namespace");
			if (nameSpaceElement != null) {
				nameSpace = nameSpaceElement.getText();
				element.remove(nameSpaceElement);
			}
			Element qnameElement = element.element("qname");
			if (!nameSpace.equals("") && qnameElement != null) {
				qnameElement.addNamespace(prefix, nameSpace);
				if (!(qnameElement.getText().split(":"))[0].toString()
						.equals(prefix)) {
					qnameElement.setText(prefix + ":"
							+ qnameElement.getText());
				}
			}
		}
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration.getValues("listener", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}

	values = configuration
			.getValues("container-runtime-option", locale);
	for (int i = 0; i < values.size(); i++) {
%><%=((I_CmsXmlContentValue) values.get(i)).getElement()
								.asXML()%>
<%
	}
%>
</portlet-app>
