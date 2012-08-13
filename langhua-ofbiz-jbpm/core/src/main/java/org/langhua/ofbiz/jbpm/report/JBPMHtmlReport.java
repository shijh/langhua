/*
 * This library is part of OFBiz-jBPM Component of Langhua
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
 * project website: http://langhua.org/opensource/ofbiz/ofbiz-jbpm/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * For more information on JBPM, please see the
 * project website: http://www.jboss.org/jbpm/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.ofbiz.jbpm.report;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.langhua.ofbiz.report.A_OFBizHtmlReport;
import org.langhua.ofbiz.report.A_OFBizReportThread;
import org.langhua.ofbiz.report.I_OFBizReportThread;
import org.langhua.ofbiz.report.util.ReportStringUtil;

/**
 * Provides a report for running jbpm html report.<p> 
 *
 * @author  Shi Yusen, shiys@langhua.cn
 * 
 */
public class JBPMHtmlReport extends A_OFBizHtmlReport {
	
    /**
     * Public constructor with report variables.<p>
     * 
     * @param req the HttpServletRequest request
     * @param res the HttpServletResponse response
     */
    public JBPMHtmlReport(HttpServletRequest request, HttpServletResponse response) {

        super(request, response);
    }
    
    public static JBPMHtmlReport getReport(HttpServletRequest request, HttpServletResponse response) {
    	
    	JBPMHtmlReport wp = (JBPMHtmlReport) request.getAttribute(SESSION_REPORT_CLASS);
    	if (wp == null) {
    		wp = new JBPMHtmlReport(request, response);
    		request.setAttribute(SESSION_REPORT_CLASS, wp);
    	}
    	return wp;
    }
    
    public I_OFBizReportThread initializeThread(HttpServletRequest request, HttpServletResponse response, String name) {

		if (name == null) {
			name = "";
		}
        I_OFBizReportThread reportThread = new JBPMHtmlThread(request, response, name.toLowerCase());
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        int i = threadGroup.activeCount();
        Thread[] threads = new Thread[i];
        threadGroup.enumerate(threads, true);
        Thread thread = null;
        for (int j=0; j<threads.length; j++) {
        	Thread threadInstance = threads[j];
        	if (threadInstance instanceof A_OFBizReportThread) {
        		thread = threadInstance;
        		break;
        	}
        }

        return reportThread;
    }

    public static String checkButton(HttpServletRequest request, HttpServletResponse response) {
    	String action = request.getParameter("action");
    	if (ReportStringUtil.isNotEmpty(action)) {
    		if (action.equalsIgnoreCase("ok")) {
    			return "ok";
    		} else if (action.equalsIgnoreCase("cancel")) {
    			return "cancel";
    		}
    	}
    	action = request.getParameter("ok");
    	if (ReportStringUtil.isNotEmpty(action)) {
    		if (action.equalsIgnoreCase("ok")) {
    			return "ok";
    		}
    	}
        action = request.getParameter("cancel");
        if (ReportStringUtil.isNotEmpty(action)) {
        	if (action.equalsIgnoreCase("cancel")) {
        		return "cancel";
        	}
        }
    	return "success";
    }
}
