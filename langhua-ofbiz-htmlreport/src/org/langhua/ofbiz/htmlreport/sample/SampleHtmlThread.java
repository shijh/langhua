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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilProperties;

import org.langhua.ofbiz.htmlreport.A_OFBizReportThread;
import org.langhua.ofbiz.htmlreport.I_OFBizReport;

/**
 * Thread for running sample html report. <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 *  
 */
public class SampleHtmlThread extends A_OFBizReportThread {

	public static final String COUNT_DOWN = "countdown";
	
	public static final String COUNT_UP = "countup";
	
	public static final String CONFIRM = "confirm_action";
	
	public static final String[] m_messageLables = new String[] {"FORMAT_DEFAULT", "FORMAT_WARNING", "FORMAT_HEADLINE", "FORMAT_NOTE", "FORMAT_OK", "FORMAT_ERROR", "FORMAT_THROWABLE"};
	
	public static final List<String> m_messages = Collections.unmodifiableList(Arrays.asList(m_messageLables));
	
    /**
     * Constructor, creates a new HtmlImportThreat.<p>
     * 
     * @param cms the current CmsObject
     * @param imp the HtmlImport Object
     */
    public SampleHtmlThread(HttpServletRequest request, HttpServletResponse response, String name) {

        super(request, response, name);
        initHtmlReport(request, response);
    }

    /**
     * @see cn.langhua.ofbiz.report.A_OFBizReportThread#getReportUpdate()
     */
    public String getReportUpdate() {

        return getReport().getReportUpdate();
    }

    /**
     * @see java.lang.Runnable#run()
     */
    public void run() {

        try {
            if (getName().startsWith(COUNT_DOWN)) {
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "START_COUNT_DOWN_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	Random random = new Random();
            	int j = 0;
            	for (int i=1000; i>0; i--) {
            		sleep(20);
            		j = random.nextInt(7);
            		if (j == 6) {
                		getReport().println(new Throwable(UtilProperties.getMessage("ReportUiLabels", m_messages.get(j), new Object[] {i}, getLocale())));
            		} else {
                		getReport().println(UtilProperties.getMessage("ReportUiLabels", m_messages.get(j), new Object[] {i}, getLocale()), j);
            		}
            	}
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "COUNT_COMPLETED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            } else if (getName().startsWith(COUNT_UP)) {
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "START_COUNT_UP_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
            	Random random = new Random();
            	int j = 0;
            	for (int i=1; i<=1000; i++) {
            		sleep(20);
            		j = random.nextInt(7);
            		if (j == 6) {
                		getReport().println(new Throwable(UtilProperties.getMessage("ReportUiLabels", m_messages.get(j), new Object[] {i}, getLocale())));
            		} else {
                		getReport().println(UtilProperties.getMessage("ReportUiLabels", m_messages.get(j), new Object[] {i}, getLocale()), j);
            		}
            	}
            	getReport().println(UtilProperties.getMessage("ReportUiLabels", "COUNT_COMPLETED_0", getLocale()), I_OFBizReport.FORMAT_HEADLINE);
        	} else {
            	getReport().println(getName(), I_OFBizReport.FORMAT_ERROR);
            }
        } catch (Exception e) {
            getReport().println(e);
            if (Debug.errorOn()) {
                Debug.log(e);
            }
        }
    }
    
    
}
