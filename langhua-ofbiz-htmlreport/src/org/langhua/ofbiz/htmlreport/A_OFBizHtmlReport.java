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

package org.langhua.ofbiz.htmlreport;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;

import org.langhua.ofbiz.htmlreport.util.ReportStringUtil;
import freemarker.core.Environment;
import freemarker.ext.beans.BeanModel;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 * HTML report output to be used for database create tables / drop tables operations.<p>
 * 
 * @author Alexander Kandzior 
 * @author Thomas Weckert  
 * @author Jan Baudisch 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public abstract class A_OFBizHtmlReport extends OFBizHtmlReport {

	public final static String THREAD_TYPE = "thread_type";
	
	public final static String RUN_CREATETABLE_SCRIPT = "runcreatetablescript";
	
	public final static String RUN_DROPTABLE_SCRIPT = "rundroptablescript";
	
	public final static String FILE_REPORT_OUTPUT = "hot-deploy/htmlreport/webapp/htmlreport/ftl/report.ftl";
	
    /**
     * Constructs a new report using the provided locale for the output language.<p>
     * 
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    public A_OFBizHtmlReport(HttpServletRequest request, HttpServletResponse response) {

        this(request, response, false, false);
    }

    /**
     * Constructs a new report using the provided locale for the output language.<p>
     *  
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param writeHtml if <code>true</code>, this report should generate HTML instead of JavaScript output
     * @param isTransient If set to <code>true</code> nothing is kept in memory
     */
    public A_OFBizHtmlReport(HttpServletRequest request, HttpServletResponse response, boolean writeHtml, boolean isTransient) {

        super(request, response, writeHtml, isTransient);
    }
    
    /**
     * Performs the dialog actions depending on the initialized action.<p>
     * 
     * @throws JspException if dialog actions fail
     * @throws IOException 
     * @throws ServletException 
     * @throws TemplateException 
     * @throws IOException 
     */
    public void displayReport(HttpServletRequest request, HttpServletResponse response, String name, String dialogUri) throws TemplateException, IOException {

        // save initialized instance of this class in request attribute for included sub-elements
        // request.setAttribute(SESSION_REPORT_CLASS, this);
    	if (ReportStringUtil.isNotEmpty(dialogUri)) {
    		setDialogRealUri(request, dialogUri);
    	}
        
        String action = getParamAction(request);
        if (action == null) action = "";
        if (action.equals("reportend") || action.equals("cancel")) {
            setParamAction("reportend");
            try {
            	Environment env = Environment.getCurrentEnvironment();
            	Configuration config = env.getConfiguration();
            	BeanModel model = (BeanModel) config.getSharedVariable("StringUtil");
            	config.setSharedVariable("StringUtil", new BeanModel(Boolean.FALSE, (BeansWrapper) config.getObjectWrapper()));
            	env.include(FILE_REPORT_OUTPUT, request.getCharacterEncoding(), true);
            	config.setSharedVariable("StringUtil", model);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (TemplateException e) {
				e.printStackTrace();
			}
        } else if (action.equals("reportupdate")) {
            setParamAction("reportupdate");
            try {
            	Environment env = Environment.getCurrentEnvironment();
            	Configuration config = env.getConfiguration();
            	BeanModel model = (BeanModel) config.getSharedVariable("StringUtil");
            	config.setSharedVariable("StringUtil", new BeanModel(Boolean.FALSE, (BeansWrapper) config.getObjectWrapper()));
            	env.include(FILE_REPORT_OUTPUT, request.getCharacterEncoding(), true);
            	config.setSharedVariable("StringUtil", model);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (TemplateException e) {
				e.printStackTrace();
			}
        } else {
            I_OFBizReportThread thread = initializeThread(request, response, name);
            thread.start();
            setParamAction("reportbegin");
            setParamThread(thread.getUUID().toString());
            try {
            	Environment env = Environment.getCurrentEnvironment();
            	Configuration config = env.getConfiguration();
            	BeanModel model = (BeanModel) config.getSharedVariable("StringUtil");
            	config.setSharedVariable("StringUtil", new BeanModel(Boolean.FALSE, (BeansWrapper) config.getObjectWrapper()));
            	env.include(FILE_REPORT_OUTPUT, request.getCharacterEncoding(), true);
            	config.setSharedVariable("StringUtil", model);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (TemplateException e) {
				e.printStackTrace();
			}
        }
    }

    /**
     * Initializes the report thread to use for this report.<p>
     * 
     * @return the reported thread to use for this report.
     */
    public abstract I_OFBizReportThread initializeThread(HttpServletRequest request, HttpServletResponse response, String name);

    /**
     * Set the report dialog uri.
     * 
     * @param dialogUri
     */
    public void setDialogRealUri(HttpServletRequest request, String dialogUri) {
    	request.setAttribute(DIALOG_URI, dialogUri);
    }

    public static String checkButton(HttpServletRequest request, HttpServletResponse response) {
    	String action = request.getParameter("action");
    	if (ReportStringUtil.isNotEmpty(action)) {
    		if (action.equalsIgnoreCase("ok")) {
    			request.removeAttribute(SESSION_REPORT_CLASS);
    			request.removeAttribute(DIALOG_URI);
    			return "ok";
    		} else if (action.equalsIgnoreCase("cancel")) {
    			request.removeAttribute(SESSION_REPORT_CLASS);
    			request.removeAttribute(DIALOG_URI);
    			return "cancel";
    		}
    	}
    	action = request.getParameter("ok");
    	if (ReportStringUtil.isNotEmpty(action)) {
    		if (action.equalsIgnoreCase("ok")) {
    			request.removeAttribute(SESSION_REPORT_CLASS);
    			request.removeAttribute(DIALOG_URI);
    			return "ok";
    		}
    	}
        action = request.getParameter("cancel");
        if (ReportStringUtil.isNotEmpty(action)) {
        	if (action.equalsIgnoreCase("cancel")) {
    			request.removeAttribute(SESSION_REPORT_CLASS);
    			request.removeAttribute(DIALOG_URI);
        		return "cancel";
        	}
        }
        
    	return "success";
    }
}