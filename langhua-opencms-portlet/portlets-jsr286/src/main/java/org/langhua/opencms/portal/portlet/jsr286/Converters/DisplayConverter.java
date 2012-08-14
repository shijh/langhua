package org.langhua.opencms.portal.portlet.jsr286.Converters;

import javax.portlet.PortletURL;
import org.langhua.opencms.portal.portlet.jsr286.Converters.Converter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author An Tao, antao@langhua.cn
 * 
 */
public class DisplayConverter extends Converter {

	public DisplayConverter(String encoding) {
		super(encoding);
	}

	public String displayConverter(String in, PortletURL url) {
		String inputString = "";
		m_portleturl = url;
		Document doc;
		try {
			doc = convertToDocument(in);
			NodeList n_body = doc.getElementsByTagName("body");
			Element element_body = (Element) n_body.item(0);
			getDisplayElementList(element_body, m_portleturl);
			inputString = getHtmlString(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return inputString;
	}

	protected void getDisplayElementList(Element element, PortletURL url) {
		NodeList node_a = element.getElementsByTagName("a");
		for (int i = 0; i < node_a.getLength(); i++) {
			Element element_a = (Element) node_a.item(i);
			if (element_a.getAttribute("href") != null
					&& !element_a.getAttribute("href").equals("")) {
				String fhome = "";
				try {
					if (element_a.getAttribute("title") != null
							&& !element_a.getAttribute("title").equals("")) {
						url.setParameter("pageurl", element_a.getAttribute("href"));
						url.setParameter("title",element_a.getAttribute("title"));
					}
					fhome = url.toString();
				} catch (Exception e) {
					e.printStackTrace();
				}
				element_a.setAttribute("href", fhome);
			}
		}
	}
}
