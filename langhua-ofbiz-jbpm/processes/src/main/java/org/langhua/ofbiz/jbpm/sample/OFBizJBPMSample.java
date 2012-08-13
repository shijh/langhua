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
package org.langhua.ofbiz.jbpm.sample;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;

import org.langhua.ofbiz.jbpm.OFBizNewProcessInstance;


public class OFBizJBPMSample {

	public static final String module = OFBizJBPMSample.class.getName();
	
	public static String  initWorkflowEntity(HttpServletRequest request,
			HttpServletResponse response) {
		Debug.log("Entry "+OFBizJBPMSample.class.getName() +" class initWorkflowEntity Method. ", module);
		String taskInstanceId = request.getParameter("taskInstanceId");
		long taskId;
		try {
			taskId = Long.parseLong(taskInstanceId);
		} catch (NumberFormatException e) {
			return "error";
		}
		JbpmContext jbpmContext = JbpmConfiguration.getInstance()
				.getCurrentJbpmContext();
		TaskInstance taskInstance = jbpmContext.getTaskMgmtSession()
				.loadTaskInstance(taskId);
		long workflowId = taskInstance.getTaskMgmtInstance()
				.getProcessInstance().getId();
		GenericDelegator delegator = (GenericDelegator) request
				.getAttribute("delegator");
		try {
			GenericValue value = GenericValue.create(delegator
					.getModelEntity("JbpmContent"), UtilMisc
					.toMap(OFBizNewProcessInstance.WORKFLOW_ID,
							"" + workflowId, "Id", "" + workflowId, "content",
							request.getParameter("content")));
			delegator.create(value);
		} catch (GenericEntityException e) {
			Debug.logWarning(e, module);
			return "error";
		}
		Debug.log("End "+OFBizJBPMSample.class.getName() +" class initWorkflowEntity Method. The WorkflowId is : "+workflowId, module);
		return "success";
	}

}
