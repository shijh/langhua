/*
 * This library is part of OFBiz-HTMLReport Component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-htmlreport/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.htmlreport.sample;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.langhua.ofbiz.htmlreport.A_OFBizHtmlReport;
import org.langhua.ofbiz.htmlreport.I_OFBizReportThread;

/**
 * Provides a report for running sample html report.<p> 
 *
 * @author  Shi Yusen, shiys@langhua.cn
 * 
 */
public class SampleHtmlReport extends A_OFBizHtmlReport {
	
    /**
     * Public constructor with report variables.<p>
     * 
     * @param req the HttpServletRequest request
     * @param res the HttpServletResponse response
     */
    public SampleHtmlReport(HttpServletRequest request, HttpServletResponse response) {

        super(request, response);
    }
    
    public static SampleHtmlReport getReport(HttpServletRequest request, HttpServletResponse response) {
    	
    	SampleHtmlReport wp = (SampleHtmlReport) request.getAttribute(SESSION_REPORT_CLASS);
    	if (wp == null) {
    		wp = new SampleHtmlReport(request, response);
    		request.setAttribute(SESSION_REPORT_CLASS, wp);
    	}
    	return wp;
    }
    
    public I_OFBizReportThread initializeThread(HttpServletRequest request, HttpServletResponse response, String name) {

		if (name == null) {
			name = "";
		}
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        int i = threadGroup.activeCount();
        Thread[] threads = new Thread[i];
        threadGroup.enumerate(threads, true);
        I_OFBizReportThread thread = null;
        for (int j=0; j<threads.length; j++) {
        	Thread threadInstance = threads[j];
        	if (threadInstance instanceof SampleHtmlThread) {
        		thread = (I_OFBizReportThread) threadInstance;
        		break;
        	}
        }

        if (thread == null) {
        	thread = new SampleHtmlThread(request, response, name.toLowerCase());
        }
        return thread;
    }
}
