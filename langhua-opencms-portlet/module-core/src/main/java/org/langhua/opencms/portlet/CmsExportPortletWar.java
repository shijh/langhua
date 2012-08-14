/*
 * This library is part of OpenCms-Portlet module
 *
 * Copyright (C) 2009 Langhua Opensource Foundation (http://langhua.org)
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
 * For further information about OpenCms-Portlet, please see the 
 * project website: http://langhua.org/opencms/
 *  
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.portlet;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.DefaultHttpMethodRetryHandler;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.logging.Log;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsFolder;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.types.CmsResourceTypeXmlContent;
import org.opencms.i18n.CmsMessageContainer;
import org.opencms.importexport.CmsImportExportException;
import org.opencms.importexport.Messages;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.report.I_CmsReport;
import org.opencms.site.CmsSite;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.CmsXmlException;
import org.opencms.xml.CmsXmlUtils;
import org.xml.sax.SAXException;

/**
 * Export portlet directory to a war file and you can deploy it directly to your portal.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */

public class CmsExportPortletWar {	
	
	private static final Log LOG = CmsLog.getLog(CmsExportPortletWar.class);

	private CmsObject m_cms;
	
	private CmsJspActionElement m_jsp;

	private int m_exportCount;

	private String m_exportFileName;

	private ZipOutputStream m_exportZipStream;

	private I_CmsReport m_report;
	
	public static final String C_JSESSIONID = "JSESSIONID";

	public CmsExportPortletWar(CmsJspActionElement jsp, String warFilename,
			String rfsPath, String resource, List<Cookie> m_cookies, I_CmsReport report) throws CmsException {

		setCms(jsp.getCmsObject());
		setReport(report);
		setExportFileName(rfsPath + warFilename);
		m_jsp = jsp;
		m_exportCount = 0;
		
		try {
			openExportFile();
			CmsSite site = OpenCms.getSiteManager().getCurrentSite(getCms());
	        String webapp = site.getUrl() + OpenCms.getSystemInfo().getOpenCmsContext();
			exportAllResources(resource, webapp, m_cookies);
			closeExportFile();
		} catch (SAXException se) {
			getReport().println(se);
			CmsMessageContainer message = Messages.get().container(
					Messages.ERR_IMPORTEXPORT_ERROR_EXPORTING_TO_FILE_1,
					getExportFileName());
			if (LOG.isDebugEnabled())
				LOG.debug(message.key(), se);
			throw new CmsImportExportException(message, se);
		} catch (IOException e) {
			getReport().println(e);
			CmsMessageContainer message = Messages.get().container(
					Messages.ERR_IMPORTEXPORT_ERROR_EXPORTING_TO_FILE_1,
					getExportFileName());
			if (LOG.isDebugEnabled())
				LOG.debug(message.key(), e);
			throw new CmsImportExportException(message, e);
		} 
	}

	protected void closeExportFile() throws IOException {		
		getExportZipStream().close();
	}	

	protected void exportAllResources(String portletPath, String webapp, List<Cookie> cookies) throws IOException, CmsException {	

		List<CmsResource> sourceList = m_jsp.getCmsObject().readResources(portletPath, CmsResourceFilter.ALL, true);
		int portletPathLength = portletPath.length();
		HttpClient httpClient = new HttpClient();
        HttpConnectionParams connectionParams = httpClient.getHttpConnectionManager().getParams();
        connectionParams.setConnectionTimeout(5000);
		httpClient.getState();
		httpClient.getHostConfiguration().getHost();
        String sessionId = null;
        if (cookies != null) {
            HttpState initialState = new HttpState();
    		for (int i=0; i<cookies.size(); i++) {
    			if (cookies.get(i).getName().equalsIgnoreCase(C_JSESSIONID)) {
    				sessionId = cookies.get(i).getValue();
    			}
                initialState.addCookie(cookies.get(i));
    		}
            httpClient.setState(initialState);
        }
		for (Iterator<CmsResource> it = sourceList.iterator(); it.hasNext();) {
			CmsResource resource = (CmsResource) it.next();

			String rootpath = resource.getRootPath();
			String path = getCms().getRequestContext().removeSiteRoot(rootpath);
			String pathInZip = path.substring(portletPathLength);
			m_exportCount++;

			if (CmsResource.isFolder(path)) {									
				CmsFolder folder = getCms().readFolder(path,
								CmsResourceFilter.IGNORE_EXPIRATION);	
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_SUCCESSION_1,
						String.valueOf(m_exportCount)), I_CmsReport.FORMAT_NOTE);
				getReport().print(Messages.get().container(Messages.RPT_EXPORT_0), I_CmsReport.FORMAT_NOTE);
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_ARGUMENT_1,
						getCms().getSitePath(folder)));
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_DOTS_0));
				getReport().println(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_OK_0), I_CmsReport.FORMAT_OK);					
			} else {
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_SUCCESSION_1,
						String.valueOf(m_exportCount)), I_CmsReport.FORMAT_NOTE);
				getReport().print(Messages.get().container(Messages.RPT_EXPORT_0), I_CmsReport.FORMAT_NOTE);
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_ARGUMENT_1,
						path));
				getReport().print(org.opencms.report.Messages.get().container(
						org.opencms.report.Messages.RPT_DOTS_0));
				ZipEntry entry = new ZipEntry(pathInZip);
				entry.setTime(resource.getDateLastModified());
				getExportZipStream().putNextEntry(entry);
				
				if(CmsResourceTypeXmlContent.isXmlContent(resource)){
					byte[] responseBody = "".getBytes();
					String url = webapp + path;
					if (CmsStringUtil.isNotEmpty(sessionId)) {
						url += ";" + C_JSESSIONID.toLowerCase() + "=" + sessionId;
					}
					GetMethod getMethod = new GetMethod(url);
					getMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new DefaultHttpMethodRetryHandler());
					try {
						int statusCode = httpClient.executeMethod(getMethod);
						if (statusCode != HttpStatus.SC_OK) {
							getReport().println(org.langhua.opencms.portlet.Messages.get().container(
									org.langhua.opencms.portlet.Messages.RPT_STATUSCODE_ERROR_1, String.valueOf(statusCode)), 
									I_CmsReport.FORMAT_ERROR);
							responseBody = getMethod.getResponseBody();
							getExportZipStream().write(responseBody);
						} else {
							responseBody = getMethod.getResponseBody();
							getExportZipStream().write(responseBody);
							getReport().println(org.opencms.report.Messages.get().container(
									org.opencms.report.Messages.RPT_OK_0), I_CmsReport.FORMAT_OK);
							
							// verify the xml content with XSD or DTD
							getReport().print(org.langhua.opencms.portlet.Messages.get().container(
									org.langhua.opencms.portlet.Messages.RPT_VALIDATING_XML_0), 
									I_CmsReport.FORMAT_NOTE);
							getReport().print(org.opencms.report.Messages.get().container(
									org.opencms.report.Messages.RPT_ARGUMENT_1,
									path));
							getReport().print(org.opencms.report.Messages.get().container(
									org.opencms.report.Messages.RPT_DOTS_0));
							CmsConfigurableEntityResolver resolver = new CmsConfigurableEntityResolver(getCms(), resource);
							try {
								CmsXmlUtils.validateXmlStructure(responseBody, resolver);
								getReport().println(org.opencms.report.Messages.get().container(
										org.opencms.report.Messages.RPT_OK_0), I_CmsReport.FORMAT_OK);
							} catch (CmsXmlException e) {
								getReport().println(e);
								if (LOG.isDebugEnabled()) {
									LOG.debug(e);
								}
							}
						}
					} catch (HttpException e) {
						getReport().println(e);
						if (LOG.isDebugEnabled()) {
							LOG.debug(e);
						}
					} catch (IOException e) {
						getReport().println(e);
						if (LOG.isDebugEnabled()) {
							LOG.debug(e);
						}
					} finally {
						getMethod.releaseConnection();
					}
				}else{
					CmsFile file = getCms().readFile(path,
							CmsResourceFilter.IGNORE_EXPIRATION);				
					getExportZipStream().write(file.getContents());
					getReport().println(org.opencms.report.Messages.get().container(
							org.opencms.report.Messages.RPT_OK_0), I_CmsReport.FORMAT_OK);
				}
				getExportZipStream().closeEntry();
			}
		}
	}

	protected String getExportFileName() {
		return m_exportFileName;
	}

	protected ZipOutputStream getExportZipStream() {
		return m_exportZipStream;
	}

	protected I_CmsReport getReport() {
		return m_report;
	}

	protected void openExportFile() throws IOException, SAXException {
		setExportZipStream(new ZipOutputStream(new FileOutputStream(
				getExportFileName())));
		
	}
	protected void setCms(CmsObject cms) {
		m_cms = cms;
	}

	protected void setExportFileName(String warFilename) {
		if (!warFilename.toLowerCase().endsWith(".war")) {
			m_exportFileName = warFilename + ".war";
		} else {
			m_exportFileName = warFilename;
		}
	}

	protected void setExportZipStream(ZipOutputStream exportZipStream) {
		m_exportZipStream = exportZipStream;
	}

	protected void setReport(I_CmsReport report) {
		m_report = report;
	}

	protected CmsObject getCms() {
		return m_cms;
	}
}
