package org.langhua.opencms.portal.portlet.jsr286.Converters;

import java.util.ArrayList;
import java.util.List;
import org.langhua.opencms.portal.portlet.jsr286.Converters.Converter;
import org.langhua.opencms.portal.portlet.jsr286.VO.TabBean;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PortletTabURLConverter extends Converter {

	public PortletTabURLConverter(String encoding) {
		super(encoding);
	}

	public List<TabBean> getPortletTabURL(String in) {
		List<TabBean> tabs = new ArrayList<TabBean>();
		Document doc;
		try {
			doc = convertToDocument(in);
			NodeList n_body = doc.getElementsByTagName("body");
			Element element_body = (Element) n_body.item(0);
			NodeList node_divs = element_body.getElementsByTagName("div");
			for (int i = 0; i < node_divs.getLength(); i++) {
				tabs.add(getTabBean((Element)node_divs.item(i)));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		return tabs;
	}

	protected TabBean getTabBean(Element element) {
			TabBean tab = new TabBean();
			NodeList n_spans = element.getElementsByTagName("span");
			for (int j = 0; j < n_spans.getLength(); j++) {
				Element n_span = (Element) n_spans.item(j);
				if (n_span.getAttribute("name") != null
						&& n_span.getAttribute("name").equals("tabName")) {
					tab.setTabName(n_span.getFirstChild().getNodeValue());
				} else if (n_span.getAttribute("name") != null
						&& n_span.getAttribute("name").equals("tabURL")) {
					tab.setTabURL(n_span.getFirstChild().getNodeValue());
				}
			}
		return tab;
	}
}
