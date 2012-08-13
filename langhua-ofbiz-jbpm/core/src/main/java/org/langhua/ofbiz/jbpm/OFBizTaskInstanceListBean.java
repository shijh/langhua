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

import org.jbpm.graph.def.ProcessDefinition;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.taskmgmt.exe.TaskInstance;

/**
 * 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizTaskInstanceListBean {

	private String processInstanceId ;
	private String taskInstanceId;
	private String processDefinitionId;
	private String processDefinitionName;
	private String taskInstanceName;
	private String tokenId;
	private String actorId;
	
	public OFBizTaskInstanceListBean(){
		
	}

	public OFBizTaskInstanceListBean(TaskInstance taskInstance) {
		
		ProcessInstance processInstance = taskInstance.getTaskMgmtInstance().getProcessInstance();
		ProcessDefinition processDefinition = processInstance.getProcessDefinition();
		processInstanceId = String.valueOf(processInstance.getId());
		taskInstanceId = String.valueOf(taskInstance.getId());
		processDefinitionId= String.valueOf(processDefinition.getId());
		processDefinitionName = processDefinition.getName();
		tokenId= String.valueOf(taskInstance.getToken().getId());
		taskInstanceName = taskInstance.getName();
		actorId = taskInstance.getActorId();
	}

	public String getProcessDefinitionId() {
		return processDefinitionId;
	}

	public void setProcessDefinitionId(String processDefinitionId) {
		this.processDefinitionId = processDefinitionId;
	}

	public String getProcessDefinitionName() {
		return processDefinitionName;
	}

	public void setProcessDefinitionName(String processDefinitionName) {
		this.processDefinitionName = processDefinitionName;
	}

	public String getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(String processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	public String getTaskInstanceId() {
		return taskInstanceId;
	}

	public void setTaskInstanceId(String taskInstanceId) {
		this.taskInstanceId = taskInstanceId;
	}

	public String getTaskInstanceName() {
		return taskInstanceName;
	}

	public void setTaskInstanceName(String taskInstanceName) {
		this.taskInstanceName = taskInstanceName;
	}

	public String getTokenId() {
		return tokenId;
	}

	public void setTokenId(String tokenId) {
		this.tokenId = tokenId;
	}

	public String getActorId() {
		return actorId;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}
	
}
