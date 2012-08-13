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

import java.util.Iterator;
import java.util.Set;


import org.hibernate.Session;
import org.jbpm.JbpmContext;
import org.jbpm.identity.Entity;
import org.jbpm.identity.Group;
import org.jbpm.identity.Membership;
import org.jbpm.identity.User;
import org.jbpm.identity.assignment.ExpressionAssignmentHandler;
import org.jbpm.identity.hibernate.IdentitySession;
import org.jbpm.instantiation.Delegation;
import org.jbpm.security.SecurityHelper;
import org.ofbiz.base.util.UtilValidate;

/**
 * 
 * @author Shi Yusen, shiys@langhua.cn 
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */
public class OFBizJBPMWorkflow extends ExpressionAssignmentHandler {

	private static final long serialVersionUID = 1L;
	private String m_name;
    private String m_entryId;
    private String m_entryName;
    private String m_actionId;
    private String m_actionName;
    private String m_attachFiles;
	
    public OFBizJBPMWorkflow(String name) {
    	m_name = name;
    	init();
    }

    public OFBizJBPMWorkflow() {
        m_name = "";
        init();
    }

	/**
	 * 
	 */
	private void init() {
		m_entryId = "";
		m_entryName = "";
		m_actionId = "";
		m_actionName = "";
		m_attachFiles = "";
	}

	public String getName() {
		return m_name;
	}
	
	public String getEntryId() {
		return m_entryId;
	}
	
	public String getEntryName() {
		return m_entryName;
	}
	
	public String getActionId() {
		return m_actionId;
	}
	
	public String getActionName() {
		return m_actionName;
	}
	
	public String getAttachFiles() {
		return m_attachFiles;
	}
	
	public void setName(String name) {
		m_name = name;
	}
	
	public void setEntryId(String entryId) {
		m_entryId = entryId;
	}
	
	public void setEntryName(String entryName) {
		m_entryName = entryName;
	}
	
	public void setActionId(String actionId) {
		m_actionId = actionId;
	}
	
	public void setActionName(String actionName) {
		m_actionName = actionName;
	}
	
	public void setAttachFiles(String attachFiles) {
		m_attachFiles = attachFiles;
	}

	/**
	 * implements an expression language for assigning actors to tasks based 
	 * on this identity component.
	 * 
	 * <pre>syntax : first-term --> next-term --> next-term --> ... --> next-term
	 * 
	 * first-term ::= previous |
	 *                swimlane(swimlane-name) |
	 *                variable(variable-name) |
	 *                user(user-name) |
	 *                group(group-name)
	 * 
	 * next-term ::= group(group-type) |
	 *               member(role-name)
	 * </pre> 
	 */
	public boolean hasPermission(JbpmContext jbpmContext, Delegation delegation, String userName) {
	    
		if (delegation == null) {
			return true;
		}
	    String expression = delegation.getConfiguration().trim();
	    if (expression.startsWith("<expression>")) expression = expression.substring(12);
	    if (expression.endsWith("</expression>")) expression = expression.substring(0, expression.length()-13);
	    if (UtilValidate.isEmpty(expression)) return false;
	    String className = delegation.getClassName();
	    if (className == null) return false;
	    if (className.equals("org.jbpm.identity.assignment.ExpressionAssignmentHandler")
	    		|| className.equals("org.jbpm.taskmgmt.assignment.ActorAssignmentHandler")
	    		|| className.equals(this.getClass().getName())) {
	    	String[] terms = expression.split("-->");
	    	if (foundInFirstTerm(jbpmContext, terms[0].trim(), userName)) return true;
	    	for (int i=1; i<terms.length; i++) {
		    	if (foundInSecondTerm(jbpmContext, terms[i].trim(), userName)) return true;
	    	}
	    } else {
	    	// other assignment handler
	    }
	    return false;
	}

	protected boolean foundInSecondTerm(JbpmContext jbpmContext, String term, String userName) {
	    Session session = jbpmContext.getSession();
	    IdentitySession identitySession = new IdentitySession(session);
	    if ( (term.startsWith("group("))
	            && (term.endsWith(")")) ) {
	       String groupType = term.substring(6,term.length()-1).trim();
			User user = identitySession.getUserByName(userName);
			if (user == null) return false;
	       Set groups = user.getGroupsForGroupType(groupType);
	       if (groups.size() == 0) {
	          return false;
	        }
	       Group group = (Group) groups.iterator().next();
			if (group == null) return false;
			if (group.getUsers().contains(user)) {
				return true;
			}
	    } else if ( (term.startsWith("member("))
	            && (term.endsWith(")")) ) {
	       String role = term.substring(7,term.length()-1).trim();
			User user = identitySession.getUserByName(userName);
	    	Set memberships = user.getMemberships();
			Iterator i = memberships.iterator();
			while(i.hasNext()) {
				Membership membership = (Membership) i.next();
				Group group = membership.getGroup();
				Entity entity = identitySession.getUserByGroupAndRole(group.getName(), role);
				if (entity != null) {
					return true;
				}
			}
	    }
		return false;
	}

	protected boolean foundInFirstTerm(JbpmContext jbpmContext, String term, String currentUserName) {
	    if (term.equalsIgnoreCase("previous")) {
	        String userName = SecurityHelper.getAuthenticatedActorId();
	        if (userName.equals(currentUserName)) return true;
	    } else if ( (term.startsWith("swimlane("))
	             && (term.endsWith(")")) ) {
	          String swimlaneName = term.substring(9,term.length()-1).trim();
	          String userName = getSwimlaneActorId(swimlaneName);
	          if (userName.equals(currentUserName)) return true;
	    } else if ( (term.startsWith("variable("))
	                    && (term.endsWith(")")) ) {
	          String variableName = term.substring(9,term.length()-1).trim();
	          Object value = getVariable(variableName);
	          if (value == null) {
	            return false;
	          } else if (value instanceof String) {
	  	        if (((String) value).equals(currentUserName)) return true;
	          } else if (value instanceof Entity) {
	            if (userInEntity(jbpmContext, (Entity)value, currentUserName)) return true;
	          }
	    } else if ( (term.startsWith("user("))
	                    && (term.endsWith(")")) ) {
	          String userName = term.substring(5,term.length()-1).trim();
	          if (userName.equals(currentUserName)) return true;
	    } else if ( (term.startsWith("group("))
	                    && (term.endsWith(")")) ) {
	          String groupName = term.substring(6,term.length()-1).trim();
	          if (userInGroup(jbpmContext, groupName, currentUserName)) return true;
	     }
	    return false;
	}

	protected boolean userInGroup(JbpmContext jbpmContext, String groupName, String currentUserName) {
       Session session = jbpmContext.getSession();
       IdentitySession identitySession = new IdentitySession(session);
		User user = identitySession.getUserByName(currentUserName);
		Group group = identitySession.getGroupByName(groupName);
		if (user == null || group == null) return false;
		if (group.getUsers().contains(user)) {
			return true;
		}
		return false;
	}

	protected boolean userInEntity(JbpmContext jbpmContext, Entity entity, String currentUserName) {
       Session session = jbpmContext.getSession();
       IdentitySession identitySession = new IdentitySession(session);
		User user = identitySession.getUserByName(currentUserName);
		Group group = identitySession.getGroupByName(entity.getName());
		if (user == null) return false;
		if (group == null) {
			// entity is a User
			if (entity.getName().equals(currentUserName)) {
				return true;
			}
		} else if (group.getUsers().contains(user)) {
			return true;
		}
		return false;
	}

}
