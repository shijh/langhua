/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.langhua.ofbiz.htmlreport.sample;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.langhua.ofbiz.htmlreport.A_OFBizHtmlReport;
import org.langhua.ofbiz.htmlreport.I_OFBizReportThread;

/**
 * Provides a sample report for running parse excel html report.<p> 
 * 
 */
public class SampleParseExcelHtmlReport extends A_OFBizHtmlReport {
	
    /**
     * Public constructor with report variables.<p>
     * 
     * @param req the HttpServletRequest request
     * @param res the HttpServletResponse response
     */
    public SampleParseExcelHtmlReport(HttpServletRequest request, HttpServletResponse response) {

        super(request, response);
    }
    
    public static SampleParseExcelHtmlReport getReport(HttpServletRequest request, HttpServletResponse response) {
    	
    	SampleParseExcelHtmlReport wp = (SampleParseExcelHtmlReport) request.getAttribute(SESSION_REPORT_CLASS);
    	if (wp == null) {
    		wp = new SampleParseExcelHtmlReport(request, response);
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
        	if (threadInstance instanceof SampleParseExcelHtmlThread) {
        		thread = (I_OFBizReportThread) threadInstance;
        		break;
        	}
        }
        if (thread == null) {
            thread = new SampleParseExcelHtmlThread(request, response, name.toLowerCase());
        }

        return thread;
    }
}
