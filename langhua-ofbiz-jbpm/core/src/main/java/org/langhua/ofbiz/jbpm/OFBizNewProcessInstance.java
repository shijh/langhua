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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.GraphSession;
import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.instantiation.Delegation;
import org.jbpm.taskmgmt.def.Swimlane;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.ModelService;
import org.ofbiz.service.ServiceUtil;

import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMProcessBean;
import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMWorkflow;
import org.langhua.ofbiz.jbpm.workflow.util.OFBizUserToJBPMUser;

/**
 * Class to edit a module dependencies.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * @author Shang ZhengWu, shangzw@langhua.cn
 * 
 */
public class OFBizNewProcessInstance {

	public static String WORKFLOW_ID = "workflowId";

	public boolean isInWorkplace = true;
	 
	public final static String module = OFBizNewProcessInstance.class.getName();

	public static String buildWorkflow(HttpServletRequest request, HttpServletResponse response){
        
		Map result = null;
        GenericDelegator delegator = (GenericDelegator) request.getAttribute("delegator");
		GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
        if (userLogin == null) {
        	return ModelService.RESPOND_ERROR;
        	}
        String workFlowName = request.getParameter("processname");
        if(UtilValidate.isEmpty(workFlowName)){
            return  ModelService.RESPOND_ERROR;
        }
        result = actionCommit(delegator, workFlowName, userLogin);
        String resultString = ModelService.RESPOND_ERROR;
        Iterator iter = result.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry rme = (Map.Entry) iter.next();
            String resultKey = (String) rme.getKey();
            if (resultKey != null && ModelService.SUCCESS_MESSAGE.equals(resultKey)){
            	resultString = ModelService.RESPOND_SUCCESS;
            	break;
            }else{
            	resultString = ModelService.RESPOND_ERROR;
            }
        }
        return resultString;
	}
	/**
	 * Commits the selected process.
	 * <p>
	 */
	public static Map actionCommit(GenericDelegator delegator,
			String workFlowName, GenericValue userLogin) {

		Map result = null;
		String userName = userLogin.getString("userLoginId");
		JbpmContext context = JbpmConfiguration.getInstance()
				.getCurrentJbpmContext();
		if (context == null) {
			result = ServiceUtil.returnError("error: jbpmContext is null!!");
			return result;
		}
		context.setActorId(userName);
		GraphSession graphSession = context.getGraphSession();
		ProcessDefinition processDefinition = graphSession
				.findLatestProcessDefinition(workFlowName);
		ProcessInstance processInstance = new ProcessInstance(processDefinition);
		processInstance.getTaskMgmtInstance().createStartTaskInstance();
		context.save(processInstance);

		new OFBizJBPMProcessBean(processInstance.getId());

		result = ServiceUtil.returnSuccess(ModelService.RESPOND_SUCCESS);
		return result;
	}

	/**
	 * Creates the list of widgets for this dialog.
	 * <p>
	 */
	public static List defineWidgets(GenericDelegator delegator,GenericValue userLogin) {

		List processes;
		try {
			processes = getWorkflowOptions(delegator,userLogin);//getCms());

			if (processes == null) {
				processes = new ArrayList();
			}
    	} catch (Exception e) {
			// do nothing
			return null;
		}

		return processes;
	}
	
	/**
	 * Returns a sorted Map of all available workflows as options for current
	 * user.
	 * <p>
	 * 
	 * @return a sorted map with the template title as key and absolute path to
	 *         the template as value
	 */
	public static List getWorkflowOptions(GenericDelegator delegator,GenericValue userLogin){

		List flows = new ArrayList();
		// get the login user name
		String userName = userLogin.getString("userLoginId");
		// make the JBPM users consistent with the Ofbiz users
		OFBizUserToJBPMUser utu = new OFBizUserToJBPMUser();
		utu.syncCurrentUserToUser(delegator,userLogin);

		JbpmContext jbpmContext = JbpmConfiguration.getInstance()
				.getCurrentJbpmContext();
		if (jbpmContext == null)
			return flows;
		jbpmContext.setActorId(userName);
		GraphSession graphSession = jbpmContext.getGraphSession();
		List processDefinitions = graphSession.findLatestProcessDefinitions();
		OFBizJBPMWorkflow jbpm = new OFBizJBPMWorkflow();
        for (int i = 0; i < processDefinitions.size(); i++) {
			ProcessDefinition processDefinition = (ProcessDefinition) processDefinitions
					.get(i);
			Swimlane swimlane = processDefinition.getTaskMgmtDefinition()
					.getStartTask().getSwimlane();
			String processName = processDefinition.getName();
			String processLocaleName = processName;
			if (swimlane == null) {
				flows.add(processLocaleName);
				continue;
			}
			Delegation delegation = swimlane.getAssignmentDelegation();
			if (jbpm.hasPermission(jbpmContext, delegation, userName)) {
				flows.add(processLocaleName);
			}
		}
		return flows;
	}

	public void setInWorkplace(boolean inworkplace) {
		isInWorkplace = inworkplace;
	}
}