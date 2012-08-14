package org.langhua.opencms.portal.portlet.jsr286.Converters;

import java.util.List;
import javax.portlet.PortletURL;
import org.langhua.opencms.portal.portlet.jsr286.Converters.Converter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PortletURLConverter extends Converter {


	public PortletURLConverter(String encoding) {
		super(encoding);
	}

	public String htmlPortletURLConverter(String in, PortletURL url,
			List<String> publicRenderParameterNames) {
		String inputString = "";
		m_portleturl = url;
		m_publicRenderParameterNames=publicRenderParameterNames;
		Document doc;
		try {
			doc = convertToDocument(in);
			NodeList n_body = doc.getElementsByTagName("body");
			Element element_body = (Element) n_body.item(0);
			setPortletURL(element_body, m_portleturl);
			inputString = getHtmlString(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return inputString;
	}

	protected void setPortletURL(Element element, PortletURL url) {
		NodeList node_a = element.getElementsByTagName("a");
		for (int i = 0; i < node_a.getLength(); i++) {
			Element element_a = (Element) node_a.item(i);
			if (element_a.getAttribute("href") != null
					&& !element_a.getAttribute("href").equals("")) {
				String fhome = "";
				try {
					fhome=getQuery(element_a,url);
				} catch (Exception e) {
					e.printStackTrace();
				}
				element_a.setAttribute("href", fhome);
			}
		}
		NodeList node_form = element.getElementsByTagName("form");
		for (int i = 0; i < node_form.getLength(); i++) {
			Element element_form= (Element)node_form.item(i);
			if (element_form.getAttribute("action") != null
					&& !element_form.getAttribute("action").equals("")) {
				String fhome = "";
				try {
					fhome=getQuery(element_form,url);
				} catch (Exception e) {
					e.printStackTrace();
				}
				element_form.setAttribute("action", fhome);
			}
		}
	}
}
