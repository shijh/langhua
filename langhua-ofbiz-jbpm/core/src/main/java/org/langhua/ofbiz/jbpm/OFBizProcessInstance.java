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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.graph.exe.Comment;
import org.jbpm.graph.exe.Token;
import org.jbpm.taskmgmt.exe.TaskInstance;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericValue;

import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMProcessBean;
import org.langhua.ofbiz.jbpm.workflow.util.OFBizJBPMTokenBean;

/**
 * Class to show a process instance.
 * <p>
 * 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class OFBizProcessInstance {

	protected OFBizJBPMProcessBean m_processBean;

	public OFBizProcessInstance() {

	}

	/**
	 * Creates the list of widgets for this dialog.
	 * <p>
	 */
	public String showProcessInfos(GenericValue userLogin, long id) {

		if (id <= 0) {
			return "";
		}
		m_processBean = new OFBizJBPMProcessBean(id);
		String userName = userLogin.getString("userLoginId");
		if (UtilValidate.isNotEmpty(userName)) {
			ArrayList tasks = m_processBean.getTasks();
			ArrayList tokens = m_processBean.getTokens();
			StringBuffer result = new StringBuffer(1024);

			result.append("<table>\n");
			result.append("<tr><td width=20%><b>" + "Process ID" + " : </b>"
					+ String.valueOf(m_processBean.getId()) + "</td></tr>");
			result.append("<tr><td width=20%><b>" + "Process Name" + " : </b>"
					+ m_processBean.getProcessName() + "</td></tr>");
			result.append("<tr><td width=20%><b>"
					+ "ProcessDefinitionVersion"
					+ " : </b>"
					+ String.valueOf(m_processBean
							.getProcessDefinitionVersion()) + "</td></tr>");
			result.append("</table>");

			JbpmContext context = JbpmConfiguration.getInstance()
					.getCurrentJbpmContext();
			if (context == null) {
				context = JbpmConfiguration.getInstance().createJbpmContext();
			}
			// output the data of the process tokens
			for (int i = 0; i < tokens.size(); i++) {
				result.append("<table>");
				OFBizJBPMTokenBean token = (OFBizJBPMTokenBean) tokens.get(i);
				result.append("<tr><td>" + "tokens" + "[" + (i + 1) + "]"
						+ String.valueOf(token.getName()) + "</td></tr>");
				Token jbpmToken = context.loadToken(token.getId());
				List comments = jbpmToken.getComments();
				if (comments == null || comments.size() <= 0)
					continue;
				for (int j = 0; j < comments.size(); j++) {
					Comment comment = (Comment) comments.get(j);
					result.append("<tr><td>" + "comments" + "[" + (j + 1)
							+ "]" + comment.getMessage() + "</td></tr>");
				}
				result.append("</table>");
			}
			SimpleDateFormat dateFormat = new SimpleDateFormat();
			result.append("<table>");
			for (int i = 0; i < tasks.size(); i++) {

				TaskInstance taskInstance = (TaskInstance) tasks.get(i);
				result.append("<tr>\n");
				String actorId = taskInstance.getActorId();
				result.append("<td width=20%>" + taskInstance.getName()
						+ "</td>");
				// output the actorId
				result.append("<td width=20%><b>" + "user "
						+ " :</b> ");
				if (UtilValidate.isNotEmpty(actorId)) {
					result.append(actorId);
				} else {
					result.append("(" + " null " + ")");
				}
				TaskInstance task = context.loadTaskInstance(taskInstance
						.getId());
				Date start = task.getStart();
				if (start == null) {
					start = task.getCreate();
				}
				Date end = task.getEnd();
				if (end == null) {
					result.append("<td width=20%></td>");
				} else {
					result.append("<td width=20%><b>" + "endtime"
							+ " :</b> " + dateFormat.format(end) + "</td>");
				}
				// output the task's status
				result.append("<td width=20%><b>" + "status"
						+ " :</b> ");
				if (end == null) {
					result.append("open");
				} else {
					result.append("end");
				}
				result.append("</td>");

				result.append("</tr>");
			}
			result.append("</table>");
			return result.toString();
		}
		return "";
	}

}