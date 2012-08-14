package org.langhua.opencms.portal.portlet.jsr286.Converters;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import javax.portlet.PortletURL;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.tidy.Tidy;
import org.w3c.dom.*;

/**
 * A simple sample portlet which get a remote content by url and write it in the
 * portlet.
 * 
 * @author An Tao, antao@langhua.cn
 */
public class Converter {

	public static final String XMLTITLE = "<?xml version='1.0'?><!DOCTYPE html [ <!ELEMENT html (#PCDATA)><!ENTITY nbsp '&#160;'><!ENTITY span '&#111;'><!ENTITY aring '&#229;'><!ENTITY auml '&#228;'><!ENTITY ouml '&#246;'><!ENTITY Aring '&#197;'><!ENTITY Auml '&#196;'><!ENTITY Ouml '&#214;'>]>";
	private String m_encoding;
	private String[] m_replacePatterns = { "&#160;", "(\\r\\n){2,}",
			"(\\n){2,}", "\\(\\r\\n<", "\\(\\n<", "\\(\\r\\n(\\ ){1,}<",
			"\\(\\n(\\ ){1,}<" };
	private Pattern[] m_replaceStyle;
	private String[] m_replaceValues = { "&nbsp;", "", "&ndash;", "", "(<",
			"(<", "(<" };
	private Tidy m_tidy;
	private String m_page = "";
	public static final String PARAM_NO_VALUE = "(no value)";
	public List<String> m_publicRenderParameterNames = new ArrayList<String>();
	public static final String PORTLET_PAGE_TAG = "PortletPageTag";
	public PortletURL m_portleturl;

	public Converter(String encoding) {
		init(encoding);
	}

	public String getTitleString(String in) {
		String titleString = "";
		Document doc;
		try {
			doc = convertToDocument(in);
			NodeList n_head = doc.getElementsByTagName("head");
			Element element_head = (Element) n_head.item(0);
			NodeList n_titles = element_head.getElementsByTagName("title");
			Node n_title = (Node) n_titles.item(0);
			if (n_title.hasChildNodes()) {
				titleString = n_title.getFirstChild().getNodeValue();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return titleString;
	}

	public String htmlConverter(String in, String page,
			List<String> publicRenderParameterNames, PortletURL url) {
		String inputString = "";
		m_portleturl = url;
		m_page = page;
		m_publicRenderParameterNames = publicRenderParameterNames;
		Document doc;
		try {
			doc = convertToDocument(in);
			NodeList n_body = doc.getElementsByTagName("body");
			Element element_body = (Element) n_body.item(0);
			setElementList(element_body);
			inputString = getHtmlString(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return inputString;
	}

	protected void setElementList(Element element) {
		NodeList node_a = element.getElementsByTagName("a");
		for (int i = 0; i < node_a.getLength(); i++) {
			// modified by Shi Yusen, shiys@langhua.cn
			// String fhome = "/portal/portal/default/" + _page;
			String fhome = m_page;
			Element element_a = (Element) node_a.item(i);
			if (element_a.getAttribute("name") != null
					&& PORTLET_PAGE_TAG.equals(element_a.getAttribute("name"))) {
				try {
					fhome = getQuery(element_a, m_portleturl);
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			} else if (element_a.getAttribute("href") != null
					&& !element_a.getAttribute("href").equals("")
					&& !element_a.getAttribute("href").equals(
							"javascript:void(null);")) {
				try {
					if (m_page.split("/")[m_page.split("/").length-1].equals("iframe")) {
						fhome += "?pageurl="
								+ java.net.URLEncoder.encode(element_a
										.getAttribute("href"), "UTF-8");
					} else {
						fhome += "?" + getQuery(element_a, null);
					}
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
			}
			if (element_a.getAttribute("href") != null) {
				element_a.setAttribute("href", fhome);
			}
		}
	}

	protected String getQuery(Element element, PortletURL portleturl)
			throws UnsupportedEncodingException {
		String queryString = "";
		List<String> names = new ArrayList<String>();
		NodeList node_input = element.getElementsByTagName("input");
		for (int i = 0; i < node_input.getLength(); i++) {
			Element element_input = (Element) node_input.item(i);
			if (element_input.getAttribute("name") != null
					&& element_input.getAttribute("type").equals("hidden")
					&& m_publicRenderParameterNames.contains(element_input
							.getAttribute("name"))) {
				if (portleturl == null) {
					if (!queryString.equals("")) {
						queryString += "&";
					}
					queryString += element_input.getAttribute("name") + "="
							+ element_input.getAttribute("value");
				} else {
					names.add(element_input.getAttribute("name"));
					portleturl.setParameter(element_input.getAttribute("name"),
							element_input.getAttribute("value"));
				}
			}
		}
		if (portleturl != null) {
			queryString = portleturl.toString();
			for(int i=0;i<names.size();i++){
				portleturl.removePublicRenderParameter(names.get(i));
			}
			
		}
		return queryString;
	}

	protected Document convertToDocument(String htmlInput)
			throws UnsupportedEncodingException {
		String workHtml = regExp(htmlInput);
		Document doc = null;
		doc = parseDOM(workHtml, m_encoding);
		NodeList nodeList = doc.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
				node.getParentNode().removeChild(node);
			}
		}
		return doc;
	}

	protected String regExp(String htmlInput) {
		String parsedHtml = htmlInput.trim();
		for (int i = 0; i < m_replacePatterns.length; i++) {
			parsedHtml = m_replaceStyle[i].matcher(parsedHtml).replaceAll(
					m_replaceValues[i]);
		}
		return parsedHtml;
	}

	protected Document parseDOM(String htmlInput, String encoding)
			throws UnsupportedEncodingException {
		ByteArrayInputStream in = new ByteArrayInputStream(htmlInput
				.getBytes(encoding));
		Document doc = m_tidy.parseDOM(in, null);
		return doc;
	}

	protected String parse(String htmlInput, String encoding)
			throws UnsupportedEncodingException {
		ByteArrayInputStream in = new ByteArrayInputStream(htmlInput
				.getBytes(encoding));
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		m_tidy.parse(in, out);
		byte[] result = out.toByteArray();
		return new String(result, encoding);
	}

	protected void init(String encoding) {
		m_encoding = encoding;
		m_tidy = new Tidy();
		m_tidy.setXHTML(true);
		m_tidy.setWord2000(false);
		Properties additionalTags = new Properties();
		additionalTags.put("new-empty-tags", "o:smarttagtype");
		additionalTags.put("new-inline-tags", "o:smarttagtype");
		m_tidy.getConfiguration().addProps(additionalTags);
		m_tidy.setInputEncoding(encoding);
		m_tidy.setOutputEncoding(encoding);
		m_tidy.setTidyMark(false);
		m_tidy.setMakeClean(false);
		m_tidy.setNumEntities(true);
		m_tidy.setPrintBodyOnly(false);
		m_tidy.setForceOutput(true);
		m_tidy.setQuiet(true);
		m_tidy.setShowWarnings(false);
		m_tidy.setHideComments(false);
		m_tidy.setBreakBeforeBR(false);
		m_tidy.setWrapAttVals(false);
		m_tidy.setWraplen(100);
		m_tidy.setSpaces(0);
		m_replaceStyle = new Pattern[m_replacePatterns.length];
		for (int i = 0; i < m_replacePatterns.length; i++) {
			m_replaceStyle[i] = Pattern.compile(m_replacePatterns[i]);
		}
	}

	protected String getHtmlString(Node element)
			throws TransformerFactoryConfigurationError, TransformerException {
		String htmlString = "";
		DOMSource source = new DOMSource(element);
		StringWriter writer = new StringWriter();
		Result result = new StreamResult(writer);
		Transformer transformer;
		transformer = TransformerFactory.newInstance().newTransformer();
		transformer.transform(source, result);
		htmlString = writer.getBuffer().toString();
		String html_body = "";
		String html_head = "";
		try {
			htmlString = regExp(htmlString);
			htmlString = parse(htmlString, m_encoding);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!htmlString.equals("")) {
			if (htmlString.indexOf("<body") != -1) {
				html_body = htmlString.substring(htmlString.indexOf(">",
						htmlString.indexOf("<body")) + 1, htmlString
						.indexOf("</body>"));
			}
			if (htmlString.indexOf("<head") != -1) {
				html_head = htmlString.substring(htmlString.indexOf(">",
						htmlString.indexOf("<head")) + 1, htmlString
						.indexOf("</head>"));
			}
		}
		String html_head_content = "";
		if (!html_head.equals("")) {
			html_head_content += getHeadContentString(html_head, "script");
			html_head_content += getHeadContentString(html_head, "style");
		}
		htmlString = html_head_content + html_body;
		return htmlString;
	}

	protected String getHeadContentString(String headString, String tagName) {
		String tagString = "";
		int beginIndex = headString.indexOf("<" + tagName);
		int endIndex = headString.indexOf("</" + tagName + ">", beginIndex)
				+ tagName.length() + 3;
		if (beginIndex != -1) {
			for (int i = 1; i < headString.split("<" + tagName).length; i++) {
				headString.indexOf(beginIndex, headString
						.indexOf("<" + tagName));
				if (beginIndex != -1 && endIndex != -1) {
					tagString += headString.substring(beginIndex, endIndex);
				}
				beginIndex = headString.indexOf("<" + tagName, endIndex);
				if (beginIndex != -1) {
					endIndex = headString.indexOf("</" + tagName + ">",
							beginIndex)
							+ tagName.length() + 3;
				} else {
					break;
				}
			}
		}
		return tagString;
	}
}
