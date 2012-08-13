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

package org.langhua.ofbiz.jbpm.workflow.util;
/**
 * Contains the data to initialize a process.<p>
 *  
 * @author Shi Yusen, shiys@langhua.cn 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */
public class OFBizProcessInitial extends Object {

    /** The process name. */
    private String m_processName;

    /**
     * Creates a new, empty process initial.<p>
     */
    public OFBizProcessInitial() {

        m_processName = new String();
    }

    /**
     * Creates a new process initial.<p>
     * 
     * @param processName the process name to be initialized
     *
     */
    public OFBizProcessInitial(String processName) {

        m_processName = processName;
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }
        if (obj instanceof OFBizProcessInitial) {
            return ((OFBizProcessInitial)obj).m_processName.equals(m_processName);
        }
        return false;
    }

    /**
     * Returns the process name.<p>
     * 
     * @return the process name
     * 
     */
    public String getProcessName() {

        return m_processName;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {

        return getProcessName().hashCode();
    }

    /**
     * Sets the process name.<p>
     * 
     * @param processName the process name
     * 
     */
    public void setProcessName(String processName) {

        m_processName = processName;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {

        return "[" + getClass().getName() + ", process name: " + m_processName + "]";
    }
    
    /**
     * @see java.lang.Object#clone()
     */
    public Object clone() {

        // create a copy of the jbpm process initial bean
        OFBizProcessInitial result = new OFBizProcessInitial(m_processName);
        return result;
    }

}