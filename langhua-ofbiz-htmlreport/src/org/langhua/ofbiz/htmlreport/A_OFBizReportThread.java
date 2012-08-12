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

import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.safehaus.uuid.EthernetAddress;
import org.safehaus.uuid.UUID;
import org.safehaus.uuid.UUIDGenerator;

/** 
 * Provides a common Thread class for the reports.<p>
 * 
 * @author Alexander Kandzior
 * @author Shi Yusen, shiys@langhua.cn  
 * 
 */
public abstract class A_OFBizReportThread extends Thread implements I_OFBizReportThread {

    /** Indicates if the thread was already checked by the grim reaper. */
    private boolean m_doomed;
    
    /** The report that belongs to the thread. */
    private I_OFBizReport m_report;

    /** The time this report is running. */
    private long m_starttime;
    
    private UUID m_uuid;

    private Locale m_locale;

    /**
     * Constructs a new report Thread with the given name.<p>
     *
     * @param name the name of the Thread
     */
    protected A_OFBizReportThread(HttpServletRequest request, HttpServletResponse response, String name) {

        super(Thread.currentThread().getThreadGroup(), name);
        // report Threads are never daemon Threads
        setDaemon(false);
        // the session must not be updated when it is used in a report
        EthernetAddress ethernetAddress = UUIDGenerator.getInstance().getDummyAddress();
        m_uuid = UUIDGenerator.getInstance().generateTimeBasedUUID(ethernetAddress);

        setName(name + " [" + m_uuid.toString() + "]");
        // new Threads are not doomed
        m_doomed = false;
        // set start time
        m_starttime = System.currentTimeMillis();
        m_locale = request.getLocale();
    }
    
    
    public UUID getUUID() {
    	return m_uuid;
    }

    /**
     * Adds an error object to the list of errors that occured during the report.<p>
     * 
     * @param obj the error object
     */
    public void addError(Object obj) {

        if (getReport() != null) {
            getReport().addError(obj);
        }
    }

    /**
     * Returns the error exception in case there was an error during the execution of
     * this Thread, null otherwise.<p>
     * 
     * @return the error exception in case there was an error, null otherwise
     */
    public Throwable getError() {

        return null;
    }

    /**
     * Returns a list of all errors that occured during the report.<p>
     * 
     * @return an error list that occured during the report
     */
    public List<?> getErrors() {

        if (getReport() != null) {
            return getReport().getErrors();
        } else {
            return null;
        }
    }

    /**
     * Returns the part of the report that is ready for output.<p>
     * 
     * @return the part of the report that is ready for output
     */
    public abstract String getReportUpdate();

    /** 
     * Returns the time this report has been running.<p>
     * 
     * @return the time this report has been running
     */
    public synchronized long getRuntime() {

        if (m_doomed) {
            return m_starttime;
        } else {
            return System.currentTimeMillis() - m_starttime;
        }
    }

    /**
     * Returns if the report generated an error output.<p>
     * 
     * @return true if the report generated an error, otherwise false
     */
    public boolean hasError() {

        if (getReport() != null) {
            return (getReport().getErrors().size() > 0);
        } else {
            return false;
        }
    }

    /**
     * Returns true if this thread is already "doomed" to be deleted.<p>
     * 
     * A OFBiz deamon Thread (the "Grim Reaper") will collect all 
     * doomed Threads, i.e. threads that are not longer active for some
     * time.<p>
     * 
     * @return true if this thread is already "doomed" to be deleted
     */
    public synchronized boolean isDoomed() {

        if (isAlive()) {
            // as long as the Thread is still active it is never doomed
            return false;
        }
        if (m_doomed) {
            // not longer active, and already doomed, so rest in peace...
            return true;
        }
        // condemn the Thread to be collected by the grim reaper next time  
        m_starttime = getRuntime();
        m_doomed = true;
        return false;
    }

    /**
     * Returns the report where the output of this Thread is written to.<p>
     * 
     * @return the report where the output of this Thread is written to
     */
    protected I_OFBizReport getReport() {

        return m_report;
    }

    /**
     * Initialize a HTML report for this Thread.<p>
     * 
     * @param locale the locale for the report output messages
     */
    protected void initHtmlReport(HttpServletRequest request, HttpServletResponse response) {

        m_report = OFBizHtmlReport.getInstance(request, response);
        ((OFBizHtmlReport) m_report).setParamThread(getUUID().toString());
    }
    
    protected Locale getLocale() {
    	
    	return m_locale;
    }

}
