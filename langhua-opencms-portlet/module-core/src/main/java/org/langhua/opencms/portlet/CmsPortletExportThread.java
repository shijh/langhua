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

import java.util.List;

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.logging.Log;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.report.A_CmsReportThread;
import org.opencms.report.I_CmsReport;
import org.opencms.util.CmsStringUtil;
import org.opencms.workplace.CmsDialog;

/**
 * 
 * @author Shi Yusen, shiys@langhua.cn
 *
 */
public class CmsPortletExportThread extends A_CmsReportThread {

	/** The log object for this class. */
	private static final Log LOG = CmsLog.getLog(CmsPortletExportThread.class);

	private CmsJspActionElement m_jsp;

	private String warFilename;

	private String rfsPath;

	private String resourcePath;

	private List<Cookie> m_cookies;
	
	public String getRfsPath() {
		return rfsPath;
	}

	public void setRfsPath(String rfsPath) {
		this.rfsPath = rfsPath;
	}

	public String getResource() {
		return resourcePath;
	}

	public void setResource(String sourcePath) {
		this.resourcePath = sourcePath;
	}

	public String getWarFilename() {
		return warFilename;
	}

	public void setWarFilename(String warFilename) {
		this.warFilename = warFilename;
	}

	public CmsPortletExportThread(CmsJspActionElement jsp, List<Cookie> cookies) {

		super(jsp.getCmsObject(), CmsExportPortlet.DIALOG_TYPE);
		m_jsp = jsp;
		m_cookies = cookies;
		initHtmlReport(jsp.getRequestContext().getLocale());
	}

	public String getReportUpdate() {

		return getReport().getReportUpdate();
	}

	public synchronized void run() {
		try {
			warFilename = m_jsp.getRequest().getParameter(CmsExportPortlet.PARAM_WARNAME);
			resourcePath = m_jsp.getRequest().getParameter(CmsExportPortlet.PARAM_RESOURCE);
			if (CmsStringUtil.isEmpty(warFilename)) {
				getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_NOWARNAME_0), I_CmsReport.FORMAT_ERROR);
				return;
			}else if (!java.lang.Character.isLetter(warFilename.charAt(0))) {
				getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_NOWARNAME_1), I_CmsReport.FORMAT_ERROR);
				return;
			}else {
				if (CmsStringUtil.isEmpty(resourcePath)) {
					getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_NORESOURCE_0), I_CmsReport.FORMAT_ERROR);
					return;
				} else {
					getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_START_2, resourcePath, warFilename), I_CmsReport.FORMAT_HEADLINE);
				}
			}
			rfsPath = m_jsp.getRequest().getParameter(CmsExportPortlet.PARAM_RFSPATH);
			if (CmsStringUtil.isEmpty(rfsPath)) {
				rfsPath = OpenCms.getSystemInfo().getPackagesRfsPath();
				getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_NORFSPATH_1, rfsPath), I_CmsReport.FORMAT_WARNING);
			} else {
				getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_RFSPATH_1, rfsPath), I_CmsReport.FORMAT_HEADLINE);
			}
			
			String message = m_jsp.getRequest().getParameter(CmsDialog.PARAM_MESSAGE);
			if (message.equals(CmsExportPortlet.DIALOG_TYPE)) {
				new CmsExportPortletWar(m_jsp, warFilename, rfsPath, resourcePath, m_cookies, getReport());
				getReport().println(Messages.get().container(Messages.GUI_EXPORT_PORTLET_END_0));
			}
		} catch (CmsException e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(e);
			}
		}
	}

}