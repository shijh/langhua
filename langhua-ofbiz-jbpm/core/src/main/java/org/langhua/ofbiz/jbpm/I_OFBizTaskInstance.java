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

package org.langhua.ofbiz.jbpm;

public interface I_OFBizTaskInstance {

	public static final String INCLUDE_FORM_REGEX = "include-form[\\s]*[\\(](.*),(.*)[\\)]";
	
	public static final String ENTITY_FORM_REGEX = "(.*)_FIELD_(.*)";
	
	/**
	 * Get html code from model form defined in process.
	 * 
	 * @param request HttpServletRequest
	 * @param response HttpServletResponse
	 * @return html code
	 */
	String getModelForm();
	
	/**
	 * Finish a task instance with an assigned transition.
	 * 
	 * @return result of this finish action
	 */
	String finishTask();
}