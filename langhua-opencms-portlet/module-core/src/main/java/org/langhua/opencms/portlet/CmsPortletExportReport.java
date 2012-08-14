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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Cookie;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.report.I_CmsReportThread;
import org.opencms.workplace.list.A_CmsListReport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;

/**
 * Provides a report for a portlet export operation.<p> 
 *
 * @author  Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsPortletExportReport extends A_CmsListReport {

	private List<Cookie> m_cookies;
	
    /**
     * Public constructor with JSP action element.<p>
     * 
     * @param jsp an initialized JSP action element
     */
    public CmsPortletExportReport(CmsJspActionElement jsp) {

        super(jsp);
    }

    /**
     * Public constructor with JSP variables.<p>
     * 
     * @param context the JSP page context
     * @param req the JSP request
     * @param res the JSP response
     */
    public CmsPortletExportReport(PageContext context, HttpServletRequest req, HttpServletResponse res) {
           
        this(new CmsJspActionElement(context, req, res));
        javax.servlet.http.Cookie[] cookies = req.getCookies();
        m_cookies = new ArrayList<Cookie>();
        for (int i=0; i<cookies.length; i++) {
        	javax.servlet.http.Cookie cookie = cookies[i];
        	m_cookies.add(new Cookie(cookie.getDomain(), cookie.getName(), cookie.getValue(), cookie.getPath(), cookie.getMaxAge(), cookie.getSecure()));
        }
    }
    
    /** 
     * 
     * @see org.opencms.workplace.list.A_CmsListReport#initializeThread()
     */
    public I_CmsReportThread initializeThread() {
        I_CmsReportThread exportThread = new CmsPortletExportThread(getJsp(), m_cookies);
        return exportThread;
    }

    public void actionCloseDialog() throws JspException {
        Map<String, String> params = new HashMap<String, String>();
        params.put(PARAM_RESOURCE, "");
        getJsp().include(FILE_EXPLORER_FILELIST, null, params);
    }
}
