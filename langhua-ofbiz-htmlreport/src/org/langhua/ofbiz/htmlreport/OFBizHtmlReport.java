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

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilProperties;

import org.langhua.ofbiz.htmlreport.util.ReportEncoder;
import org.langhua.ofbiz.htmlreport.util.ReportStringUtil;

/**
 * HTML report output to be used in report.ftl.<p>
 * 
 * @author Alexander Kandzior 
 * @author Thomas Weckert  
 * @author Jan Baudisch 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizHtmlReport extends A_OFBizReport {

    /** The delimiter that is used in the resource list request parameter. */
    public static final String DELIMITER_RESOURCES = "|";

    /** Request parameter name for the resource list. */
    public static final String PARAM_RESOURCELIST = "resourcelist";

    /** Constant for a HTML linebreak with added "real" line break. */
    protected static final String LINEBREAK = "<br>";

    /** 
     * Constant for a HTML linebreak with added "real" line break- 
     * traditional style for report threads that still use XML templates for their output.
     */
    protected static final String LINEBREAK_TRADITIONAL = "<br>\\n";

    /** The list of report objects e.g. String, Exception ... */
    protected List<Serializable> m_content;

    /**
     * Counter to remember what is already shown,
     * indicates the next index of the m_content list that has to be reported.
     */
    protected int m_indexNext;

    /** Flag to indicate if an exception should be displayed long or short. */
    protected boolean m_showExceptionStackTrace;

    /** If set to <code>true</code> nothing is kept in memory. */
    protected boolean m_transient;

    /** Boolean flag indicating whether this report should generate HTML or JavaScript output. */
    protected boolean m_writeHtml;
    
    /** Helper variable to deliver the html end part. */
    public static final int HTML_END = 1;

    /** Helper variable to deliver the html start part. */
    public static final int HTML_START = 0;

    /** The thread to display in this report. */
    protected String m_paramThread;

    /** The next thread to display after this report. */
    protected String m_paramThreadHasNext;
    
    protected String m_paramAction;
    
    protected String m_paramTitle;
    
    protected String m_paramResource;

    /** Flag for refreching workplace .*/
    protected String m_paramRefreshWorkplace;

    /** Constant for the "OK" button in the build button methods. */
    public static final int BUTTON_OK = 0;

    /** Constant for the "Cancel" button in the build button methods. */
    public static final int BUTTON_CANCEL = 1;

    /** Constant for the "Close" button in the build button methods. */
    public static final int BUTTON_CLOSE = 2;

    /** Constant for the "Advanced" button in the build button methods. */
    public static final int BUTTON_ADVANCED = 3;

    /** Constant for the "Set" button in the build button methods. */
    public static final int BUTTON_SET = 4;

    /** Constant for the "Details" button in the build button methods. */
    public static final int BUTTON_DETAILS = 5;

    /** Constant for the "OK" button in the build button methods (without form submission). */
    public static final int BUTTON_OK_NO_SUBMIT = 6;

    /** Constant for the "Edit" button in the build button methods (same function as "Ok" button but different text on button. */
    public static final int BUTTON_EDIT = 7;

    /** Constant for the "Discard" button in the build button methods (same function as "Cancel" button but different text on button. */
    public static final int BUTTON_DISCARD = 8;

    /** Constant for the "Back" button in the build button methods. */
    public static final int BUTTON_BACK = 9;

    /** Constant for the "Continue" button in the build button methods. */
    public static final int BUTTON_CONTINUE = 10;

    /** Request parameter value for the action: back. */
    public static final String DIALOG_BACK = "back";

    /** Request parameter value for the action: cancel. */
    public static final String DIALOG_CANCEL = "cancel";

    /** Request parameter value for the action: continue. */
    public static final String DIALOG_CONTINUE = "continue";

    /** Request parameter value for the action: set. */
    public static final String DIALOG_SET = "set";

    /** The resource list parameter value. */
    protected String m_paramResourcelist;

    /** The list of resource names for the multi operation. */
    protected List<String> m_resourceList;

    /** The key name which contains the localized message for the continue checkbox. */
    protected String m_paramReportContinueKey;

    public static final String DIALOG_URI = "dialoguri";
    
    /**
     * Constructs a new report using the provided locale for the output language.<p>
     * 
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    public OFBizHtmlReport(HttpServletRequest request, HttpServletResponse response) {

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
    public OFBizHtmlReport(HttpServletRequest request, HttpServletResponse response, boolean writeHtml, boolean isTransient) {

        init(request.getLocale());
        m_content = new ArrayList<Serializable>(256);
        m_showExceptionStackTrace = true;
        m_writeHtml = writeHtml;
        m_transient = isTransient;
    }
    
    public static OFBizHtmlReport getInstance(HttpServletRequest request, HttpServletResponse response) {
    	OFBizHtmlReport wp = (OFBizHtmlReport) request.getSession().getAttribute(SESSION_REPORT_CLASS);
    	if (wp == null) {
    		wp = new OFBizHtmlReport(request, response);
    		request.getSession().setAttribute(SESSION_REPORT_CLASS, wp);
    	}
    	return wp;
    }
    
    
    public String getParamAction(HttpServletRequest request) {

		m_paramAction = request.getParameter("action");
    	return m_paramAction != null ? m_paramAction : "reportbegin";
    }

    
    public void setParamAction(String action) {
    	
    	m_paramAction = action;
    }

    public void setParamThread(String name) {
    	
    	m_paramThread = name;
    }

    /**
     * @see cn.langhua.ofbiz.report.I_OFBizReport#getReportUpdate()
     */
    public synchronized String getReportUpdate() {

        StringBuffer result = new StringBuffer();
        int indexEnd = m_content.size();
        for (int i = m_indexNext; i < indexEnd; i++) {
            int pos = m_transient ? 0 : i;
            Object obj = m_content.get(pos);
            if ((obj instanceof String) || (obj instanceof StringBuffer)) {
                result.append(obj);
            } else if (obj instanceof Throwable) {
                result.append(getExceptionElement((Throwable)obj));
            }
            if (m_transient) {
                m_content.remove(m_indexNext);
            }
        }
        m_indexNext = m_transient ? 0 : indexEnd;
        return result.toString();
    }

    /**
     * Returns if the report writes html or javascript code.<p> 
     * 
     * @return <code>true</code> if the report writes html, and <code>false</code> if the report writes javascript code
     */
    public boolean isWriteHtml() {
        
        return m_writeHtml;
    }

    /**
     * @see cn.langhua.ofbiz.report.I_OFBizReport#print(java.lang.String, int)
     */
    public synchronized void print(String value, int format) {

        StringBuffer buf = null;

        if (!m_writeHtml) {
            value = ReportStringUtil.escapeJavaScript(value);
            switch (format) {
                case FORMAT_HEADLINE:
                    buf = new StringBuffer();
                    buf.append("aH('");
                    buf.append(value);
                    buf.append("'); ");
                    break;
                case FORMAT_WARNING:
                    buf = new StringBuffer();
                    buf.append("aW('");
                    buf.append(value);
                    buf.append("'); ");
                    addWarning(value);
                    break;
                case FORMAT_ERROR:
                    buf = new StringBuffer();
                    buf.append("aE('");
                    buf.append(value);
                    buf.append("'); ");
                    addError(value);
                    break;
                case FORMAT_NOTE:
                    buf = new StringBuffer();
                    buf.append("aN('");
                    buf.append(value);
                    buf.append("'); ");
                    break;
                case FORMAT_OK:
                    buf = new StringBuffer();
                    buf.append("aO('");
                    buf.append(value);
                    buf.append("'); ");
                    break;
                case FORMAT_DEFAULT:
                default:
                    buf = new StringBuffer();
                    buf.append("a('");
                    buf.append(value);
                    buf.append("'); ");
            }
            // the output lines get split back into single lines on the client-side.
            // thus, a separate JavaScript call has to be added here to tell the
            // client that we want a linebreak here...
            if (value.trim().endsWith(getLineBreak())) {
                buf.append("aB(); ");
            }
            m_content.add(buf.toString());
        } else {
            switch (format) {
                case FORMAT_HEADLINE:
                    buf = new StringBuffer();
                    buf.append("<span class='head'>");
                    buf.append(value);
                    buf.append("</span>");
                    break;
                case FORMAT_WARNING:
                    buf = new StringBuffer();
                    buf.append("<span class='warn'>");
                    buf.append(value);
                    buf.append("</span>");
                    addWarning(value);
                    break;
                case FORMAT_ERROR:
                    buf = new StringBuffer();
                    buf.append("<span class='err'>");
                    buf.append(value);
                    buf.append("</span>");
                    addError(value);
                    break;
                case FORMAT_NOTE:
                    buf = new StringBuffer();
                    buf.append("<span class='note'>");
                    buf.append(value);
                    buf.append("</span>");
                    break;
                case FORMAT_OK:
                    buf = new StringBuffer();
                    buf.append("<span class='ok'>");
                    buf.append(value);
                    buf.append("</span>");
                    break;
                case FORMAT_DEFAULT:
                default:
                    buf = new StringBuffer(value);
            }
            if (value.trim().endsWith(getLineBreak())) {
                buf.append("\n");
            }
            m_content.add(buf.toString());
        }
    }

    /**
     * @see cn.langhua.ofbiz.report.I_OFBizReport#println()
     */
    public void println() {
        print(getLineBreak());
    }

    /**
     * @see cn.langhua.ofbiz.report.I_OFBizReport#println(java.lang.Throwable)
     */
    public synchronized void println(Throwable t) {

        addError(t.getMessage());
        m_content.add(getExceptionElement(t));
    }
    
    /**
     * Returns the correct line break notation depending on the output style of this report.
     * 
     * @return the correct line break notation
     */
    protected String getLineBreak() {

        return m_writeHtml ? LINEBREAK_TRADITIONAL : LINEBREAK;
    }

    /**
     * Output helper method to format a reported <code>Throwable</code> element.<p>
     * 
     * This method ensures that exception stack traces are properly escaped
     * when they are added to the report.<p>
     * 
     * There is a member variable {@link #m_showExceptionStackTrace} in this
     * class that controls if the stack track is shown or not.
     * In a later version this might be configurable on a per-user basis.<p>
     *      
     * @param throwable the exception to format
     * @return the formatted StringBuffer
     */
    private StringBuffer getExceptionElement(Throwable throwable) {

        StringBuffer buf = new StringBuffer(256);

        if (!m_writeHtml) {
            if (m_showExceptionStackTrace) {
                buf.append("aT('");
                buf.append(UtilProperties.getMessage("ReportUiLabels", "REPORT_EXCEPTION_0", getLocale()));
                String exception = ReportEncoder.escapeXml(throwable.getLocalizedMessage());
                exception = exception.replaceAll("[\r\n]+", LINEBREAK);
                buf.append(ReportStringUtil.escapeJavaScript(exception) + LINEBREAK);
                buf.append("'); ");
            } else {
                buf.append("aT('");
                buf.append(UtilProperties.getMessage("ReportUiLabels", "REPORT_EXCEPTION_0", getLocale()));
                buf.append(ReportStringUtil.escapeJavaScript(throwable.toString()));
                buf.append("'); ");
            }
        } else {
            if (m_showExceptionStackTrace) {
                buf.append("<span class='throw'>");
                buf.append(UtilProperties.getMessage("ReportUiLabels", "REPORT_EXCEPTION_0", getLocale()));
                String exception = ReportEncoder.escapeXml(throwable.getLocalizedMessage());
                exception = exception.replaceAll("[\r\n]+", "<br>");
                buf.append(exception);
                buf.append("</span>");
            } else {
                buf.append("<span class='throw'>");
                buf.append(UtilProperties.getMessage("ReportUiLabels", "REPORT_EXCEPTION_0", getLocale()));
                buf.append(throwable.toString());
                buf.append("</span>");
                buf.append(getLineBreak());
            }
        }
        return buf;
    }

	public void printMessageWithParam(String uiLabel, Object param) {
        print(uiLabel, I_OFBizReport.FORMAT_NOTE);
	}

	public void printMessageWithParam(int m, int n, String uiLabel, Object param) {
        print(uiLabel, I_OFBizReport.FORMAT_NOTE);
	}

    /**
     * Builds the start html of the page, including setting of DOCTYPE and 
     * inserting a header with the content-type.<p>
     * 
     * This overloads the default method of the parent class.<p>
     * 
     * @return the start html of the page
     */
    public String htmlStart() {

        return pageHtml(HTML_START, true);
    }

    /**
     * Builds the start html of the page, including setting of DOCTYPE and 
     * inserting a header with the content-type.<p>
     * 
     * This overloads the default method of the parent class.<p>
     * 
     * @param loadStyles if true, the defaul style sheet will be loaded
     * @return the start html of the page
     */
    public String htmlStart(boolean loadStyles) {

        return pageHtml(HTML_START, loadStyles);
    }

    /**
     * Builds the start html of the page, including setting of DOCTYPE and 
     * inserting a header with the content-type.<p>
     * 
     * This overloads the default method of the parent class.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param loadStyles if true, the defaul style sheet will be loaded
     * @return the start html of the page
     */
    public String pageHtml(int segment, boolean loadStyles) {

        if (segment == HTML_START) {
            StringBuffer result = new StringBuffer(512);
            result.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
            result.append("<html>\n<head>\n");
            result.append("<meta HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
            if (loadStyles) {
                result.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"");
                result.append("/htmlreport/includes/workplace.css");
                result.append("\">\n");
                result.append("<script type=\"text/javascript\">\n");
                result.append(dialogScriptSubmit());
                result.append("</script>\n");
            }
            return result.toString();
        } else {
            return "</html>";
        }
    }

    /**
     * Builds the standard javascript for submitting the dialog.<p>
     * 
     * @return the standard javascript for submitting the dialog
     */
    public String dialogScriptSubmit() {

        StringBuffer result = new StringBuffer(512);
        result.append("function submitAction(actionValue, theForm, formName) {\n");
        result.append("\tif (theForm == null) {\n");
        result.append("\t\ttheForm = document.forms[formName];\n");
        result.append("\t}\n");
        result.append("\ttheForm.framename.value = window.name;\n");
        result.append("\tif (actionValue == \"ok\") {\n");
        result.append("\t\treturn true;\n");
        result.append("\t}\n");
        result.append("\ttheForm.action.value = actionValue;\n");
        result.append("\ttheForm.submit();\n");
        result.append("\treturn false;\n");
        result.append("}\n");

        return result.toString();
    }

    /**
     * Returns true if the report Thread is still alive (i.e. running), false otherwise.<p>
     *  
     * @return true if the report Thread is still alive
     */
    public boolean isAlive(HttpServletRequest request) {

        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        int i = threadGroup.activeCount();
        Thread[] threads = new Thread[i];
        threadGroup.enumerate(threads, true);
        A_OFBizReportThread thread = null;
        for (int j=0; j<threads.length; j++) {
        	Thread threadInstance = threads[j];
        	if (threadInstance instanceof A_OFBizReportThread) {
        		if(((A_OFBizReportThread)threadInstance).getUUID().toString().equals(getParamThread(request))) {
            		thread = (A_OFBizReportThread) threadInstance;
            		break;
        		}
        	}
        }
        if (thread != null) {
            return thread.isAlive();
        } else {
            return false;
        }
    }

    /**
     * Returns the style parameter value.<p>
     *
     * @return the style parameter value
     */
    public String getParamStyle(HttpServletRequest request) {
    	
    	String style = request.getParameter("style");
    	return ReportStringUtil.isNotEmptyOrWhitespaceOnly(style) ? style : "new";
    }

    /**
     * Returns the thread parameter value.<p>
     *
     * @return the thread parameter value
     */
    public String getParamThread(HttpServletRequest request) {
    	
    	String thread = request.getParameter("thread");
    	return ReportStringUtil.isNotEmptyOrWhitespaceOnly(thread) ? thread : (m_paramThread == null? "" : m_paramThread);
    }

    /**
     * Returns the threadhasnext parameter value.<p>
     *
     * @return the threadhasnext parameter value
     */
    public String getParamThreadHasNext(HttpServletRequest request) {
    	
    	String threadhasnext = request.getParameter("threadhasnext");
    	return ReportStringUtil.isNotEmptyOrWhitespaceOnly(threadhasnext) ? threadhasnext : "false";
    }

    /**
     * Returns the reporttype parameter value.<p>
     *
     * @return the reporttype parameter value
     */
    public String getParamReportType(HttpServletRequest request) {
    	
    	String reporttype = request.getParameter("reporttype");
    	return ReportStringUtil.isNotEmptyOrWhitespaceOnly(reporttype) ? reporttype : "extended";
    }

    /**
     * Builds the start html of the body.<p>
     * 
     * @param className optional class attribute to add to the body tag
     * @param parameters optional parameters to add to the body tag
     * @return the start html of the body
     */
    public String bodyStart(String className, String parameters) {

        return pageBody(HTML_START, className, parameters);
    }

    /**
     * Builds the html of the body.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param className optional class attribute to add to the body tag
     * @param parameters optional parameters to add to the body tag
     * @return the html of the body
     */
    public String pageBody(int segment, String className, String parameters) {

        if (segment == HTML_START) {
            StringBuffer result = new StringBuffer(128);
            result.append("</head>\n<body unselectable=\"on\"");
            if (ReportStringUtil.isNotEmptyOrWhitespaceOnly(className)) {
                result.append(" class=\"");
                result.append(className);
                result.append("\"");
            }
            if (ReportStringUtil.isNotEmpty(parameters)) {
                result.append(" ");
                result.append(parameters);
            }
            result.append(">\n");
            return result.toString();
        } else {
            return "</body>";
        }
    }

    /**
     * Builds the end html of the body.<p>
     * 
     * @return the end html of the body
     */
    public String bodyEnd() {

        return pageBody(HTML_END, null, null);
    }

    /**
     * Builds the end html of the page.<p>
     * 
     * @return the end html of the page
     */
    public String htmlEnd() {

        return pageHtml(HTML_END, null);
    }

    /**
     * Returns the default html for a workplace page, including setting of DOCTYPE and 
     * inserting a header with the content-type.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param title the title of the page, if null no title tag is inserted
     * @return the default html for a workplace page
     */
    public String pageHtml(int segment, String title) {

        return pageHtmlStyle(segment, title, null);
    }

    /**
     * Returns the default html for a workplace page, including setting of DOCTYPE and 
     * inserting a header with the content-type, allowing the selection of an individual style sheet.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param title the title of the page, if null no title tag is inserted
     * @param stylesheet the used style sheet, if null the default stylesheet 'workplace.css' is inserted
     * @return the default html for a workplace page
     */
    public String pageHtmlStyle(int segment, String title, String stylesheet) {

        if (segment == HTML_START) {
            StringBuffer result = new StringBuffer(512);
            result.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
            result.append("<html>\n<head>\n");
            if (title != null) {
                result.append("<title>");
                result.append(title);
                result.append("</title>\n");
            }
            result.append("<meta HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n");
            result.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"");
            result.append("/htmlreport/includes/workplace.css");
            result.append("\">\n");
            return result.toString();
        } else {
            return "</html>";
        }
    }

    /**
     * Returns the start html for the outer dialog window border.<p>
     * 
     * @return the start html for the outer dialog window border
     */
    public String dialogStart() {

        return dialog(HTML_START, null);
    }

    /**
     * Builds the outer dialog window border.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param attributes optional additional attributes for the opening dialog table
     * 
     * @return a dialog window start / end segment
     */
    public String dialog(int segment, String attributes) {

        if (segment == HTML_START) {
            StringBuffer html = new StringBuffer(512);
            html.append("<table class=\"dialog\" cellpadding=\"0\" cellspacing=\"0\"");
            if (attributes != null) {
                html.append(" ");
                html.append(attributes);
            }
            html.append("><tr><td>\n<table class=\"dialogbox\" cellpadding=\"0\" cellspacing=\"0\">\n");
            html.append("<tr><td>\n");
            return html.toString();
        } else {
            return "</td></tr></table>\n</td></tr></table>\n<p>&nbsp;</p>\n";
        }
    }

    /**
     * Returns the start html for the content area of the dialog window.<p>
     * 
     * @param title the title for the dialog
     * 
     * @return the start html for the content area of the dialog window
     */
    public String dialogContentStart(String title) {

        return dialogContent(HTML_START, title);
    }

    /**
     * Builds the content area of the dialog window.<p>
     * 
     * @param segment the HTML segment (START / END)
     * @param title the title String for the dialog window
     * 
     * @return a content area start / end segment
     */
    public String dialogContent(int segment, String title) {

        if (segment == HTML_START) {
            StringBuffer result = new StringBuffer(512);
            // null title is ok, we always want the title headline
            result.append(dialogHead(title));
            result.append("<div class=\"dialogcontent\" unselectable=\"on\">\n");
            result.append("<!-- dialogcontent start -->\n");
            return result.toString();
        } else {
            return "<!-- dialogcontent end -->\n</div>\n";
        }
    }

    /**
     * Builds the title of the dialog window.<p>
     * 
     * @param title the title String for the dialog window
     * 
     * @return the HTML title String for the dialog window
     */
    public String dialogHead(String title) {

        return "<div class=\"dialoghead\" unselectable=\"on\">" + (title == null ? "" : title) + "</div>";
    }

    /**
     * Returns the value of the title parameter, 
     * or null if this parameter was not provided.<p>
     * 
     * This parameter is used to build the title 
     * of the dialog. It is a parameter so that the title 
     * can be passed to included elements.<p>
     * 
     * @return the value of the title parameter
     */
    public String getParamTitle(HttpServletRequest request) {
    	
    	if (m_paramTitle == null) {
    		m_paramTitle = request.getParameter("title");
    	}
    	return m_paramTitle != null ? m_paramTitle : "";
    }

    /**
     * Returns all initialized parameters of the current workplace class 
     * as hidden field tags that can be inserted in a form.<p>
     * 
     * @return all initialized parameters of the current workplace class
     * as hidden field tags that can be inserted in a html form
     */
    public String paramsAsHidden(HttpServletRequest request) {

        return paramsAsHidden(request, null);
    }

    /**
     * Returns all initialized parameters of the current workplace class 
     * that are not in the given exclusion list as hidden field tags that can be inserted in a form.<p>
     * 
     * @param excludes the parameters to exclude 
     * 
     * @return all initialized parameters of the current workplace class
     * that are not in the given exclusion list as hidden field tags that can be inserted in a form
     */
    public String paramsAsHidden(HttpServletRequest request, Collection<?> excludes) {

        StringBuffer result = new StringBuffer(512);
        Map<String, Object> params = paramValues(request);
        Iterator<?> i = params.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry entry = (Map.Entry)i.next();
            String param = (String)entry.getKey();
            if ((excludes == null) || (!excludes.contains(param))) {
                result.append("<input type=\"hidden\" name=\"");
                result.append(param);
                result.append("\" value=\"");
                String encoded = ReportEncoder.encode(
                    entry.getValue().toString(),
                    "UTF-8");
                result.append(encoded);
                result.append("\">\n");
            }
        }
        
        return result.toString();
    }

    /**
     * Returns the values of all parameter methods of this workplace class instance.<p>
     * 
     * @return the values of all parameter methods of this workplace class instance
     */
    protected Map<String, Object> paramValues(HttpServletRequest request) {

        List<Method> methods = paramGetMethods();
        Map<String, Object> map = new HashMap<String, Object>(methods.size());
        Iterator<Method> i = methods.iterator();
        while (i.hasNext()) {
            Method m = (Method)i.next();
            Object o = null;
            try {
                o = m.invoke(this, new Object[0]);
            } catch (InvocationTargetException ite) {
                // can usually be ignored
            } catch (IllegalAccessException eae) {
                // can usually be ignored
            }
            if (o != null) {
                map.put(m.getName().substring(8).toLowerCase(), o);
            }
        }
        return map;
    }

    /**
     * Returns a list of all methods of the current class instance that 
     * start with "getParam" and have no parameters.<p> 
     * 
     * @return a list of all methods of the current class instance that 
     * start with "getParam" and have no parameters
     */
    private List<Method> paramGetMethods() {

        List<Method> list = new ArrayList<Method>();
        Method[] methods = this.getClass().getMethods();
        int length = methods.length;
        for (int i = 0; i < length; i++) {
            Method method = methods[i];
            if (method.getName().startsWith("getParam") && (method.getParameterTypes().length == 0)) {
                if (Debug.errorOn()) {
                    System.err.println("getMethod: " + method.getName());
                }
                list.add(method);
            }
        }
        return list;
    }

    /**
     * Returns an optional introduction text to be displayed above the report output.<p>
     * 
     * @return an optional introduction text
     */
    public String reportIntroductionText() {

        return "";
    }

    /**
     * Returns an optional conclusion text to be displayed below the report output.<p>
     * 
     * @return an optional conclusion text
     */
    public String reportConclusionText() {

        return "";
    }

    /**
     * Returns the end html for the content area of the dialog window.<p>
     * 
     * @return the end html for the content area of the dialog window
     */
    public String dialogContentEnd() {

        return dialogContent(HTML_END, null);
    }

    /**
     * Builds a button row with an "Ok", a "Cancel" and a "Details" button.<p>
     * 
     * This row is displayed when the first report is running.<p>
     * 
     * @param okAttrs optional attributes for the ok button
     * @param cancelAttrs optional attributes for the cancel button
     * @param detailsAttrs optional attributes for the details button
     * @return the button row
     */
    public String dialogButtonsContinue(String okAttrs, String cancelAttrs, String detailsAttrs) {

        if (ReportStringUtil.isEmptyOrWhitespaceOnly(detailsAttrs)) {
            detailsAttrs = "";
        } else {
            detailsAttrs += " ";
        }
        return dialogButtons(new int[] {BUTTON_OK, BUTTON_CANCEL, BUTTON_DETAILS}, new String[] {
            okAttrs,
            cancelAttrs,
            detailsAttrs + "onclick=\"switchOutputFormat();\""});
    }

    /**
     * Builds a button row with an "Ok", a "Cancel" and a "Details" button.<p>
     * 
     * This row is used when a single report is running or after the first report has finished.<p>
     * 
     * @param okAttrs optional attributes for the ok button
     * @param cancelAttrs optional attributes for the cancel button
     * @param detailsAttrs optional attributes for the details button
     * @return the button row
     */
    public String dialogButtonsOkCancelDetails(HttpServletRequest request, String okAttrs, String cancelAttrs, String detailsAttrs) {

        if (ReportStringUtil.isEmptyOrWhitespaceOnly(detailsAttrs)) {
            detailsAttrs = "";
        } else {
            detailsAttrs += " ";
        }

        if (Boolean.valueOf(getParamThreadHasNext(request)).booleanValue()
            && ReportStringUtil.isNotEmpty(getParamReportContinueKey())) {
            return dialogButtons(new int[] {BUTTON_OK, BUTTON_CANCEL, BUTTON_DETAILS}, new String[] {
                okAttrs,
                cancelAttrs,
                detailsAttrs + "onclick=\"switchOutputFormat();\""});
        }
        return dialogButtons(new int[] {BUTTON_OK, BUTTON_DETAILS}, new String[] {
            okAttrs,
            detailsAttrs + "onclick=\"switchOutputFormat();\""});
    }

    /**
     * Builds the html for the button row under the dialog content area, including buttons.<p>
     * 
     * @param buttons array of constants of which buttons to include in the row
     * @param attributes array of Strings for additional button attributes
     * 
     * @return the html for the button row under the dialog content area, including buttons
     */
    public String dialogButtons(int[] buttons, String[] attributes) {

        StringBuffer result = new StringBuffer(256);
        result.append(dialogButtonRow(HTML_START));
        for (int i = 0; i < buttons.length; i++) {
            dialogButtonsHtml(result, buttons[i], attributes[i]);
        }
        result.append(dialogButtonRow(HTML_END));
        return result.toString();
    }

    /**
     * Builds the button row under the dialog content area without the buttons.<p>
     * 
     * @param segment the HTML segment (START / END)
     * 
     * @return the button row start / end segment
     */
    public String dialogButtonRow(int segment) {

        if (segment == HTML_START) {
            return "<!-- button row start -->\n<div class=\"dialogbuttons\" unselectable=\"on\">\n";
        } else {
            return "</div>\n<!-- button row end -->\n";
        }
    }

    /**
     * Renders the HTML for a single input button of a specified type.<p>
     * 
     * @param result a string buffer where the rendered HTML gets appended to
     * @param button a integer key to identify the button
     * @param attribute an optional string with possible tag attributes, or null
     */
    protected void dialogButtonsHtml(StringBuffer result, int button, String attribute) {

        attribute = appendDelimiter(attribute);

        switch (button) {
            case BUTTON_OK:
                result.append("<input name=\"ok\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_OK_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" type=\"submit\"");
                } else {
                    result.append(" type=\"button\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_CANCEL:
                result.append("<input name=\"cancel\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_CANCEL_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_CANCEL + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_EDIT:
                result.append("<input name=\"ok\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_EDIT_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" type=\"submit\"");
                } else {
                    result.append(" type=\"button\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_DISCARD:
                result.append("<input name=\"cancel\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_DISCARD_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_CANCEL + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_CLOSE:
                result.append("<input name=\"close\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_CLOSE_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_CANCEL + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_ADVANCED:
                result.append("<input name=\"advanced\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_ADVANCED_0", getLocale()) + "\"");
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_SET:
                result.append("<input name=\"set\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_SET_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_SET + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_BACK:
                result.append("<input name=\"set\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_BACK_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_BACK + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_CONTINUE:
                result.append("<input name=\"set\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_CONTINUE_0", getLocale()) + "\"");
                if (attribute.toLowerCase().indexOf("onclick") == -1) {
                    result.append(" onclick=\"submitAction('" + DIALOG_CONTINUE + "', form);\"");
                }
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            case BUTTON_DETAILS:
                result.append("<input name=\"details\" type=\"button\" value=\"");
                result.append(UtilProperties.getMessage("ReportUiLabels", "GUI_DIALOG_BUTTON_DETAIL_0", getLocale()) + "\"");
                result.append(" class=\"dialogbutton\"");
                result.append(attribute);
                result.append(">\n");
                break;
            default:
                // not a valid button code, just insert a warning in the HTML
                result.append("<!-- invalid button code: ");
                result.append(button);
                result.append(" -->\n");
        }
    }

    /**
     * Appends a space char. between tag attributes.<p>
     * 
     * @param attribute a tag attribute
     * 
     * @return the tag attribute with a leading space char
     */
    protected String appendDelimiter(String attribute) {

        if (ReportStringUtil.isNotEmpty(attribute)) {
            if (!attribute.startsWith(" ")) {
                // add a delimiter space between the beginning button HTML and the button tag attributes
                return " " + attribute;
            } else {
                return attribute;
            }
        }

        return "";
    }

    /**
     * Tests if we are working with the new administration dialog style.<p>
     * 
     * The default is the new style, this parameter is not intended for external use.<p>
     * 
     * @return <code>true</code> if using the new style
     */
    public boolean useNewStyle(HttpServletRequest request) {

        return (getParamStyle(request) != null) && getParamStyle(request).equals("new");
    }

    /**
     * Returns true if the dialog operation has to be performed on multiple resources.<p>
     * 
     * @return true if the dialog operation has to be performed on multiple resources, otherwise false
     */
    public boolean isMultiOperation(HttpServletRequest request) {

        return (getResourceList(request).size() > 1);
    }

    /**
     * Returns the resources that are defined for the dialog operation.<p>
     * 
     * For single resource operations, the list contains one item: the resource name found 
     * in the request parameter value of the "resource" parameter.<p>
     * 
     * @return the resources that are defined for the dialog operation
     */
    public List<String> getResourceList(HttpServletRequest request) {

        if (m_resourceList == null) {
            // use lazy initializing
            if (getParamResourcelist(request) != null) {
                // found the resourcelist parameter
                m_resourceList = ReportStringUtil.split(getParamResourcelist(request), DELIMITER_RESOURCES);
                Collections.sort(m_resourceList);
            } else {
                // this is a single resource operation, create list containing the resource name
                m_resourceList = new ArrayList<String>(1);
                String resource = getParamResource(request);
                if (ReportStringUtil.isNotEmptyOrWhitespaceOnly(resource)) {
                    m_resourceList.add(resource);
                } else {
                    m_resourceList.add("");
                }
            }
        }
        return m_resourceList;
    }

    /**
     * Returns the value of the resource list parameter, or null if the parameter is not provided.<p>
     * 
     * This parameter selects the resources to perform operations on.<p>
     *  
     * @return the value of the resource list parameter or null, if the parameter is not provided
     */
    public String getParamResourcelist(HttpServletRequest request) {

        if (ReportStringUtil.isNotEmpty(m_paramResourcelist) && !"null".equals(m_paramResourcelist)) {
            return m_paramResourcelist;
        } else {
            return null;
        }
    }

    /**
     * Returns the value of the file parameter, 
     * or null if this parameter was not provided.<p>
     * 
     * The file parameter selects the file on which the dialog action
     * is to be performed.<p>
     * 
     * @return the value of the file parameter
     */
    public String getParamResource(HttpServletRequest request) {

    	m_paramResource = request.getParameter("resource");
        if ((m_paramResource != null) && !"null".equals(m_paramResource)) {
            return m_paramResource;
        } else {
            return null;
        }
    }

    /**
     * Returns if the workplace must be refreshed.<p>
     * 
     * @return <code>"true"</code> if the workplace must be refreshed.
     */
    public String getParamRefreshWorkplace() {

        return m_paramRefreshWorkplace;
    }

    /**
     * Returns the key name which contains the localized message for the continue checkbox.<p>
     * 
     * @return the key name which contains the localized message for the continue checkbox
     */
    public String getParamReportContinueKey() {

        if (m_paramReportContinueKey == null) {
            m_paramReportContinueKey = "";
        }
        return m_paramReportContinueKey;
    }

    /**
     * Returns the value of the resourcelist parameter in form of a String separated 
     * with {@link #DELIMITER_RESOURCES}, or the value of the  resource parameter if the 
     * first parameter is not provided (no multiple choice has been done.<p>
     * 
     * This may be used for jsps as value for the parameter for resources {@link #PARAM_RESOURCELIST}.<p>
     *  
     * @return the value of the resourcelist parameter or null, if the parameter is not provided
     */
    public String getResourceListAsParam(HttpServletRequest request) {

        String result = getParamResourcelist(request);
        if (ReportStringUtil.isEmptyOrWhitespaceOnly(result)) {
            result = getParamResource(request);
        }
        return result;
    }

    /**
     * Returns the end html for the outer dialog window border.<p>
     * 
     * @return the end html for the outer dialog window border
     */
    public String dialogEnd() {

        return dialog(HTML_END, null);
    }
    
    /**
     * Returns the http URI of the current dialog, to be used
     * as value for the "action" attribute of a html form.<p>
     *
     * This URI is the real one.<p>
     *  
     * @return the http URI of the current dialog
     */
    public String getDialogRealUri(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(DIALOG_URI));
    }

}