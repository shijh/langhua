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
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.exception.GenericJDBCException;
import org.hibernate.exception.SQLGrammarException;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.db.GraphSession;
import org.jbpm.graph.exe.ProcessInstance;
import org.jbpm.instantiation.Delegation;
import org.jbpm.taskmgmt.def.Swimlane;
import org.jbpm.taskmgmt.exe.SwimlaneInstance;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericValue;

import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMWorkflow;
import org.langhua.ofbiz.jbpm.workflow.util.OFBizUserToJBPMUser;

/**
 * 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizJBPMList {

	private GenericDelegator delegator;
	
	public static final String JBPM_WORKFLOWID = "jbpm_workflowid";

	/**
	 * Public constructor. <p>
	 * 
	 */
	public OFBizJBPMList(){
		
	}

	public OFBizJBPMList(String delegatorName){
		
		if (UtilValidate.isNotEmpty(delegatorName))
		    delegator = GenericDelegator.getGenericDelegator(delegatorName);
	}

	public static OFBizJBPMList getInstance() {
		return new OFBizJBPMList();
	}

	public static String endProcess(HttpServletRequest request,
			HttpServletResponse response) {

        GenericDelegator delegator = (GenericDelegator) request.getAttribute("delegator");
		GenericValue userLogin = (GenericValue) request.getSession().getAttribute("userLogin");
		String processId = request.getParameter("processInstanceId");
		long id = Long.parseLong(processId);

		OFBizUserToJBPMUser utu = new OFBizUserToJBPMUser();
		utu.syncCurrentUserToUser(delegator,userLogin);

		// terminate the action
		try {
			// terminate the workflow process instance
			JbpmContext context = JbpmConfiguration.getInstance()
					.getCurrentJbpmContext();
			GraphSession graphSession = context.getGraphSession();
			graphSession.deleteProcessInstance(id);
		} catch (Exception e) {
			// can usually be ignored
			return "error";
		}
		return "sucess";
	}
	/**
	 * Deletes the project and closes the dialog.
	 * <p>
	 * 
	 * @throws Exception
	 *             if something goes wrong
	 */
	public void actionStopWorkflowEntry(GenericDelegator delegator, GenericValue userLogin, String processId) {

		long id = Long.parseLong(processId);

		OFBizUserToJBPMUser utu = new OFBizUserToJBPMUser();
		utu.syncCurrentUserToUser(delegator,userLogin);

		
		// terminate the action
		try {
			// terminate the workflow process instance
			JbpmContext context = JbpmConfiguration.getInstance()
					.getCurrentJbpmContext();
			GraphSession graphSession = context.getGraphSession();
			graphSession.deleteProcessInstance(id);
		} catch (Exception e) {
			// can usually be ignored
			
		}
	}

	/**
	 * @see org.openOfbiz.workplace.list.A_OfbizListDialog#getListItems()
	 */
	public static  List getListItems(GenericDelegator delegator, GenericValue userLogin) {

		List ret = new ArrayList();
		String userName = userLogin.getString("userLoginId");
		OFBizUserToJBPMUser utu = new OFBizUserToJBPMUser();
		try {
			utu.syncCurrentUserToUser(delegator,userLogin);
		} catch (SQLGrammarException e) {
			// no jBPM database tables
			return ret;
		} catch (GenericJDBCException e) {
			return ret;
		}

		ret = getTaskInstances(userName);
		if (ret == null)
			return ret;

		Collections.sort(ret, COMPARE_PROCESSINSTANCE_DATE);
		// get the actions which current user has permission to do
		List result = new ArrayList();
		for (int k = 0; k < ret.size(); k++) {
			TaskInstance taskInstance = (TaskInstance) ret.get(k);
			OFBizTaskInstanceListBean item = new OFBizTaskInstanceListBean(taskInstance);

			result.add(item);
		}
		return result;
	}
	
	public static final Comparator COMPARE_PROCESSINSTANCE_DATE = new Comparator() {

		/**
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Object o1, Object o2) {

			if ((o1 == o2) || !(o1 instanceof TaskInstance)
					|| !(o2 instanceof TaskInstance)) {
				return 0;
			}

			ProcessInstance r1 = ((TaskInstance) o1).getTaskMgmtInstance().getProcessInstance();
			ProcessInstance r2 = ((TaskInstance) o2).getTaskMgmtInstance().getProcessInstance();

			long id1 = r1.getStart().getTime();
			long id2 = r2.getStart().getTime();

			return (id1 > id2) ? 0 : (id1 < id2) ? 1 : 0;
		}
	};
	
	/**
	 * Returns a list of all available task instances for current user.
	 * <p>
	 * 
	 * @param userName
	 *            a user name
	 * @return a list of all available task instances
	 * @throws OfbizException
	 *             if reading a folder or file fails
	 */
	public static List getTaskInstances(String userName) {
		List tasks = null;

		JbpmContext jbpmContext = JbpmConfiguration.getInstance()
				.getCurrentJbpmContext();
		if (jbpmContext == null || UtilValidate.isEmpty(userName))
			return tasks;
		jbpmContext.setActorId(userName);

		tasks = jbpmContext.getTaskMgmtSession().findTaskInstances(userName);
		Session session = jbpmContext.getSession();
		try {
			String queryString = "select ti "
					+ "from org.jbpm.taskmgmt.exe.TaskInstance as ti "
					+ "where (ti.actorId is null or ti.actorId = :actorId) "
					+ "and ti.isOpen = true";
			Query query = session.createQuery(queryString);
			query.setString("actorId", userName);
			tasks = query.list();
		} catch (Exception e) {
			e.printStackTrace();
			return tasks;
		}

		List list = new ArrayList();
		OFBizJBPMWorkflow jbpm = new OFBizJBPMWorkflow();
		for (int i = 0; i < tasks.size(); i++) {
			TaskInstance taskInstance = (TaskInstance) tasks.get(i);
			if (taskInstance == null)
				continue;
			SwimlaneInstance swimlaneInstance = taskInstance
					.getSwimlaneInstance();
			if (swimlaneInstance == null)
				continue;
			Swimlane swimlane = taskInstance.getSwimlaneInstance()
					.getSwimlane();
			if (swimlane == null)
				continue;
			Delegation delegation = swimlane.getAssignmentDelegation();
			if (!jbpm.hasPermission(jbpmContext, delegation, userName)) {
				list.add(tasks.get(i));
			}
		}
		tasks.removeAll(list);
		return tasks;
	}

}
