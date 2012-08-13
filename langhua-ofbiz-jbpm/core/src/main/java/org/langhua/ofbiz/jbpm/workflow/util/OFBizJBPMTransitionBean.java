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
 * Contains the data of a transition.<p>
 *  
 * @author Shi Yusen, shiys@langhua.cn 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */
public class OFBizJBPMTransitionBean extends Object {

    /** The transition id. */
    private long m_transitionId;
    
    private String m_transitionName;
    
    /**
     * Creates a new, empty transition bean.<p>
     */
    public OFBizJBPMTransitionBean() {

        m_transitionId = 0;
        m_transitionName = "";
    }

    /**
     * Creates a new transition bean.<p>
     * 
     * @param transitionId
     *
     */
    public OFBizJBPMTransitionBean(long transitionId) {

        m_transitionId = transitionId;
        m_transitionName = "";
    }

    public OFBizJBPMTransitionBean(long id, String name) {
        m_transitionId = id;
        m_transitionName = name;
	}

	/**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }
        if (obj instanceof OFBizJBPMTransitionBean) {
            return ((OFBizJBPMTransitionBean)obj).m_transitionId == m_transitionId;
        }
        return false;
    }

    /**
     * Returns the transition id.<p>
     * 
     * @return the transition id
     * 
     */
    public long getTransitionId() {

        return m_transitionId;
    }
    
    
    public String getTransitionName() {
        
        return m_transitionName;
    }

    /**
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {

        return String.valueOf(getTransitionId()).hashCode();
    }

    /**
     * Sets the transition id.<p>
     * 
     * @param transitionId
     * 
     */
    public void setTransitionId(long transitionId) {

        m_transitionId = transitionId;
    }
    
    public void setTransitionName(String transitionName) {
        
        m_transitionName = transitionName;
    }

    /**
     * @see java.lang.Object#toString()
     */
    public String toString() {

        return "[" + getClass().getName() + ", transition id: " + m_transitionId + "]";
    }

    /**
     * @see java.lang.Object#clone()
     */
    public Object clone() {

        // create a copy of the jbpm transition bean
    	OFBizJBPMTransitionBean result = new OFBizJBPMTransitionBean(m_transitionId, m_transitionName);
        return result;
    }
    
}