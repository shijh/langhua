/* This library is part of Taglib OpenCms module of Langhua
 *
 * Copyright (C) 2008 Langhua Opensource (http://www.langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org 
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.opencms.taglib.commons;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.servlet.jsp.tagext.TagSupport;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.logging.Log;
import org.opencms.file.CmsResourceFilter;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.site.CmsSite;
import org.opencms.util.CmsStringUtil;

/**
 * Abstract class to support Langhua taglibs in OpenCms. A Langhua taglib provides a piece of html content. 
 * Generally, it has its own css, resource type and etc.
 */
public abstract class A_LanghuaTag extends TagSupport {
	
	private static final long serialVersionUID = 753514338644521871L;

	private static final Log LOG = CmsLog.getLog(A_LanghuaTag.class);
	
	public static final String TAG_JSESSIONID = "JSESSIONID";
	
	/** A link file to be used to produce taglib content. */
	private String m_linkFile;
	
	/** A configure file of CSS. */
	private String m_cssFile;
	
	/** A CSS indicator, can be null, nostyle or customized. Null is default, in this case, the css will be included in an html style element. If nostyle, pure css will be built. If customized, a piece of customized css will be produced. */
	private String m_cssIndicator;
	
	public static final String CSS_INDICATOR_NOSTYLE = "nostyle";
	
	public static final String CSS_INDICATOR_CUSTOMIZED = "customized";

	public static final String TAGLIB_ICON_PREFIX = "taglibicon.";
	
	private static final String BASE_MODULE_NAME = "org.langhua.opencms.taglib.base";
	
	private static final String PARAM_USE_PERFORMANCE = "useperformance";
	
	/** */
	public static final String HELP_PICTURE_JS = "/system/modules/org.langhua.opencms.taglib.base/resources/helppicture.js";

	public String getLinkFile() {
		return m_linkFile;
	}

	public String getCssFile() {
		return m_cssFile;
	}

	public String getCssIndicator() {
		return m_cssIndicator;
	}

	public void setLinkFile(String linkFile) {
		m_linkFile = linkFile;
	}

	public void setCssFile(String cssFile) {
		m_cssFile = cssFile;
	}

	public void setCssIndicator(String cssIndicator) {
		m_cssIndicator = cssIndicator;
	}

	/**
	 * Build CSS output by requesting the css file through http.
	 * 
	 * @param cms
	 * @param cssFileUri
	 * @return CSS output
	 */
	public String buildCSS(CmsJspActionElement cms, String cssFileUri) {

		CmsSite site = OpenCms.getSiteManager().getCurrentSite(cms.getCmsObject());
		String siteUrl = "";
		if (site.getUrl().contains("*")) {
			siteUrl = OpenCms.getSiteManager().getWorkplaceSiteMatcher().getUrl();
		} else {
			siteUrl = site.getUrl();
		}
		String webapp = siteUrl + OpenCms.getSystemInfo().getOpenCmsContext();
		HttpClient httpClient = new HttpClient();
        HttpConnectionParams connectionParams = httpClient.getHttpConnectionManager().getParams();
        connectionParams.setConnectionTimeout(5000);
		httpClient.getState();
		httpClient.getHostConfiguration().getHost();
        String sessionId = null;

		javax.servlet.http.Cookie[] cookies = cms.getRequest().getCookies();
		if (cookies != null) {
			HttpState initialState = new HttpState();
			for (int i=0; i<cookies.length; i++) {
				javax.servlet.http.Cookie cookie = cookies[i];
    			if (cookie.getName().equalsIgnoreCase(TAG_JSESSIONID)) {
    				sessionId = cookie.getValue();
    			}
				initialState.addCookie(new Cookie(cookie.getDomain(), cookie.getName(), cookie.getValue(), cookie.getPath(), cookie.getMaxAge(), cookie.getSecure()));
			}
			httpClient.setState(initialState);
		}

		String url = webapp + cssFileUri;
		if (CmsStringUtil.isNotEmpty(sessionId)) {
			url += ";" + TAG_JSESSIONID.toLowerCase() + "=" + sessionId;
		}
		GetMethod getMethod = new GetMethod(url);
		getMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
		byte[] responseBody = "".getBytes();
		try {
			int statusCode = httpClient.executeMethod(getMethod);
			if (statusCode == HttpStatus.SC_OK) {
				responseBody = getMethod.getResponseBody();
			}
		} catch (IOException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e);
			}
		}

		return new String(responseBody);
	}
	
	public abstract String buildHtml();

    /**
     * Generates help picture html code.<p>
     * 
     * @return html code
     */
	public static String helpPicture() {
		StringBuffer result = new StringBuffer(8);
        result.append("<div class=\"help\" id=\"helpPicture\" onMouseOver=\"showHelpPicture();\" onMouseOut=\"hideHelpPicture();\"></div>\n");
		return result.toString();
	}

    /**
     * Output the HELP_PICTURE_JS.<p>
     * 
     * @return html code
     */
	public static String showHelpPicture(CmsJspActionElement jsp) {
		
		StringBuffer result = new StringBuffer(32);
		result.append("<script type='text/javascript' src='");
		result.append(jsp.link(HELP_PICTURE_JS));
		result.append("'></script>\n");
		return result.toString();
	}
	
	/**
	 * Check whether performance module is used.
	 * 
	 * @return boolean to indicate whether performance module is used.
	 */
	private static boolean isPerformanceUsed() {
		String valueUsePerformance = OpenCms.getModuleManager().getModule(BASE_MODULE_NAME).getParameter(PARAM_USE_PERFORMANCE);
		boolean isPerformanceUsed = CmsStringUtil.isNotEmptyOrWhitespaceOnly(valueUsePerformance) && (valueUsePerformance.equalsIgnoreCase("yes") || valueUsePerformance.equalsIgnoreCase("true")) ? true : false;
		return isPerformanceUsed;
	}
	
	/**
	 * Append a top latest parameter to the filter input.
	 * 
	 * @param filter
	 * @param topLatest
	 * @return the filter
	 */
	public static CmsResourceFilter addTopLatest(CmsResourceFilter filter, String topLatest) {
		if (isPerformanceUsed()) {
			try {
				// check whether the CmsResourceFilter has addTopLatest method
				Method method = filter.getClass().getMethod("addTopLatest", Integer.class);
				filter = (CmsResourceFilter) method.invoke(filter, Integer.parseInt(topLatest));
			} catch (NoSuchMethodException e) {
				LOG.debug(e);
			} catch (SecurityException e) {
				LOG.debug(e);
	        } catch (IllegalAccessException e) {
				LOG.debug(e);
	        } catch (IllegalArgumentException e) {
				LOG.debug(e);
		    }catch (InvocationTargetException e) {
				LOG.debug(e);
			}
		}
		return filter;
	}

	/**
	 * Append paged latest parameters to the filter input.
	 * 
	 * @param filter CmsResourceFilter
	 * @param startRow an int number to start a selection
	 * @param rowsInPage an int number representing rows in the selection
	 * @return the filter CmsResourceFilter
	 */
	public static CmsResourceFilter addPagedLatest(CmsResourceFilter filter, int startRow, int rowsInPage) {
		if (isPerformanceUsed()) {
			try {
				// check whether the CmsResourceFilter has addTopLatest method
				Method method = filter.getClass().getMethod("addPagedLatest", Integer.class);
				filter = (CmsResourceFilter) method.invoke(filter, Integer.valueOf(startRow), Integer.valueOf(rowsInPage));
			} catch (NoSuchMethodException e) {
				LOG.debug(e);
			} catch (SecurityException e) {
				LOG.debug(e);
	        } catch (IllegalAccessException e) {
				LOG.debug(e);
	        } catch (IllegalArgumentException e) {
				LOG.debug(e);
		    }catch (InvocationTargetException e) {
				LOG.debug(e);
			}
		}
		return filter;
	}
}
