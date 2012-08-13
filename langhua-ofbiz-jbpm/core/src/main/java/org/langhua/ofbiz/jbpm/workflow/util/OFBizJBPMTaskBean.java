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

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.GraphSession;
import org.jbpm.db.TaskMgmtSession;
import org.jbpm.graph.def.Action;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.logging.exe.LoggingInstance;
import org.jbpm.taskmgmt.def.Task;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.jbpm.taskmgmt.log.TaskAssignLog;

/**
 * Contains the data of a task.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn 
 * @author Shang ZhengWu, shangzw@langhua.cn
 * 
 */
public class OFBizJBPMTaskBean extends Object {

	private static final Log log = LogFactory.getLog(OFBizJBPMTaskBean.class);

	private long taskInstanceId;

	private TaskInstance taskInstance;

	private OFBizJBPMProcessBean processBean;

	private JbpmContext jbpmContext;

	private GraphSession graphSession;

	private TaskMgmtSession taskMgmtSession;

	private List availableTransitions;

	// task name
	private String name;

	private String actorId;

	private Date end;

	private String rootPath;

	private String schema;

	private String folder;

	private String fileprefix;

	private List actions;

	private List variableAccesses;

	private String transitionId;

	public OFBizJBPMTaskBean() {
		jbpmContext = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		graphSession = jbpmContext.getGraphSession();
		taskMgmtSession = jbpmContext.getTaskMgmtSession();
	}

	public OFBizJBPMTaskBean(
			long taskInstanceId) {
		jbpmContext = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		graphSession = jbpmContext.getGraphSession();
		taskMgmtSession = jbpmContext.getTaskMgmtSession();
		this.taskInstanceId = taskInstanceId;
		taskInstance = taskMgmtSession.loadTaskInstance(taskInstanceId);
		name = taskInstance.getName();
		availableTransitions = taskInstance.getAvailableTransitions();
		processBean = new OFBizJBPMProcessBean(
				taskInstance
				.getTaskMgmtInstance().getProcessInstance().getId());
		variableAccesses = taskInstance.getTask().getTaskController()
				.getVariableAccesses();
	}

	/**
	 * 
	 * Constructor uses by {@link org.jbpm.webapp.bean.ProcessInstanceBean} to
	 * monitor tasks list
	 * 
	 * @param taskInstanceId
	 *            The id of the task instance
	 * @param name
	 *            The task name
	 * @param actorId
	 *            The task actor Id
	 * @param end
	 *            The task end date
	 * 
	 */
	public OFBizJBPMTaskBean(
			long taskInstanceId, String name,
			String actorId, Date end) {

		this();
		this.taskInstanceId = taskInstanceId;
		taskInstance = taskMgmtSession.loadTaskInstance(taskInstanceId);
		this.name = name;
		this.actorId = actorId;
		this.end = end;
		availableTransitions = taskInstance.getAvailableTransitions();
		processBean = new OFBizJBPMProcessBean(
				taskInstance.getTaskMgmtInstance().getProcessInstance().getId());
		variableAccesses = taskInstance.getTask().getTaskController()
				.getVariableAccesses();
	}

	// for clone operation
	public OFBizJBPMTaskBean(
			long taskInstanceId,
			TaskInstance taskInstance, OFBizJBPMProcessBean processBean,
			JbpmContext jbpmContext, GraphSession graphSession,
			TaskMgmtSession taskMgmtSession, List availableTransitions,
			String name, String actorId, Date end, String rootPath,
			String schema, String folder, String fileprefix, List actions,
			List variableAccesses, String transitionId,// CmsXmlContent content,
			List resources,String isCreateProject,String elementdefaultvisibility
			,String commentDefaultVisibility,String languagedefaultvisibility) {
		this.taskInstanceId = taskInstanceId;
		this.taskInstance = taskInstance;
		this.processBean = processBean;
		this.jbpmContext = jbpmContext;
		this.graphSession = graphSession;
		this.taskMgmtSession = taskMgmtSession;
		this.availableTransitions = availableTransitions;
		this.name = name;
		this.actorId = actorId;
		this.end = end;
		this.rootPath = rootPath;
		this.schema = schema;
		this.folder = folder;
		this.fileprefix = fileprefix;
		this.actions = actions;
		this.variableAccesses = variableAccesses;
		this.transitionId = transitionId;
		this.resources = resources;
		this.isCreateProject = isCreateProject;
		this.elementdefaultvisibility = elementdefaultvisibility;
		this.commentDefaultVisibility = commentDefaultVisibility;
		this.languagedefaultvisibility = languagedefaultvisibility;
	}

	/**
	 * Get the list of actions which belongs to this task instance.
	 * 
	 */
	public List getActions() {
		if (this.actions == null) {
			this.actions = new ArrayList();
			Task task = taskInstance.getTask();
			long taskId = task.getId();
			Map actions = task.getProcessDefinition().getActions();
			Iterator iterator = actions.keySet().iterator();
			while (iterator.hasNext()) {
				Object key = iterator.next();
				Action action = (Action) actions.get(key);
				long id = action.getEvent().getGraphElement().getId();
				if (taskId == id) {
					this.actions.add(action);
				}
			}
		}
		return this.actions;
	}

	public void initialize(TaskInstance taskInstance) {
		this.taskInstance = taskInstance;
		this.taskInstanceId = taskInstance.getId();

		// get the available transitions
		availableTransitions = null;
		availableTransitions = taskInstance.getAvailableTransitions();

		log.debug("initialized availableTransitions " + availableTransitions);
	}

	public String save() {

		// submit the parameters in the jbpm task controller
		TaskInstance taskInstance = taskMgmtSession
				.loadTaskInstance(taskInstanceId);
		taskInstance.setActorId(actorId);

		// save the process instance and hence the updated task instance
		// variables
		jbpmContext.save(taskInstance);

		return "home";
	}

	public String saveAndClose() {
		// save
		save();

		// close the task instance
		TaskInstance taskInstance = taskMgmtSession
				.loadTaskInstance(taskInstanceId);
		taskInstance.setActorId(actorId);
		taskInstance.end();

		ProcessInstance processInstance = taskInstance.getTaskMgmtInstance()
				.getProcessInstance();
		LoggingInstance loggingInstance = processInstance.getLoggingInstance();
		List assignmentLogs = loggingInstance.getLogs(TaskAssignLog.class);

		log.debug("assignmentlogs: " + assignmentLogs);

		jbpmContext.save(taskInstance);

		return "home";
	}

	public long getTaskInstanceId() {
		return taskInstanceId;
	}

	public void setTaskInstanceId(long taskInstanceId) {
		this.taskInstanceId = taskInstanceId;
	}

	public TaskInstance getTaskInstance() {
		return taskInstance;
	}

	public void setTaskInstance(TaskInstance taskInstance) {
		this.taskInstance = taskInstance;
	}

	public OFBizJBPMProcessBean getProcessBean() {
		return processBean;
	}

	public void setProcessBean(OFBizJBPMProcessBean processBean) {
		this.processBean = processBean;
	}

	public JbpmContext getJbpmContext() {
		return jbpmContext;
	}

	public void setJbpmContext(JbpmContext jbpmContext) {
		this.jbpmContext = jbpmContext;
	}

	public GraphSession getGraphSession() {
		return graphSession;
	}

	public void setGraphSession(GraphSession graphSession) {
		this.graphSession = graphSession;
	}

	public TaskMgmtSession getTaskMgmtSession() {
		return taskMgmtSession;
	}

	public void setTaskMgmtSession(TaskMgmtSession taskMgmtSession) {
		this.taskMgmtSession = taskMgmtSession;
	}

	public List getAvailableTransitions() {
		return availableTransitions;
	}

	public void setAvailableTransitions(List availableTransitions) {
		this.availableTransitions = availableTransitions;
	}

	public String getActorId() {
		return actorId;
	}

	public void setActorId(String actorId) {
		this.actorId = actorId;
	}

	public String getTaskName() {
		return name;
	}

	public void setTaskName(String name) {
		this.name = name;
	}

	public Date getEnd() {
		return end;
	}

	public void setEnd(Date end) {
		this.end = end;
	}

	public boolean isEnded() {
		if (end == null)
			return true;
		return false;
	}

	public String getRootPath() {
		return rootPath;
	}

	public void setRootPath(String rootPath) {
		this.rootPath = rootPath;
	}

	public String getSchema() {
		return schema;
	}

	public void setSchema(String schema) {
		this.schema = schema;
	}

	public String getFolder() {
		return folder;
	}

	public void setFolder(String folder) {
		this.folder = folder;
	}

	public String getFilePrefix() {
		return fileprefix;
	}

	public void setFilePrefix(String fileprefix) {
		this.fileprefix = fileprefix;
	}

	public void setActions(List actions) {
		this.actions = actions;
	}

	public List getVariableAccesses() {
		return variableAccesses;
	}

	public void setVariableAccesses(List variableAccesses) {
		this.variableAccesses = variableAccesses;
	}

	public String getTransitionId() {
		return transitionId;
	}

	public void setTransitionId(String transitionId) {
		this.transitionId = transitionId;
	}

	/**
	 * @see java.lang.Object#clone()
	 */
	public Object clone() {

		// create a copy of the jbpm task bean
		OFBizJBPMTaskBean result = new OFBizJBPMTaskBean(taskInstanceId,
				taskInstance, processBean, jbpmContext, graphSession,
				taskMgmtSession, availableTransitions, name, actorId, end,
				rootPath, schema, folder, fileprefix, actions,
				variableAccesses, transitionId,resources,isCreateProject,
				elementdefaultvisibility,commentDefaultVisibility,languagedefaultvisibility);
		return result;
	}

	private List resources;

	public List getResources() {
		return resources;
	}

	public void setResources(List resources) {
		this.resources = resources;
	}
	private String isCreateProject;
	public void createProject(String iscreateproject){
		this.isCreateProject = iscreateproject;
	}
	public boolean isCreateProject(){
		if(isCreateProject==null){
			return false;
		}
		return Boolean.valueOf(isCreateProject).booleanValue();
	}
	private String elementdefaultvisibility;
	public String getElementdefaultvisibility(){
		return elementdefaultvisibility;
	}
	public void setElementdefaultvisibility(String elementdefaultvisibility){
		this.elementdefaultvisibility =elementdefaultvisibility;
	}
	private String commentDefaultVisibility;
	public String getCommentDefaultVisibility(){
		return commentDefaultVisibility;
	}
	public void setCommentDefaultVisibility(String commentDefaultVisibility){
		this.commentDefaultVisibility =commentDefaultVisibility;
	}
	private String languagedefaultvisibility;
	public String getLanguageDefaultVisibility() {
		return languagedefaultvisibility;
	} 
	public void setLanguageDefaultVisibility(String language){
		this.languagedefaultvisibility = language;
	}
}
