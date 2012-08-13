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

import org.hibernate.Session;
import org.hibernate.exception.SQLGrammarException;
import org.jbpm.JbpmConfiguration;
import org.jbpm.JbpmContext;
import org.jbpm.identity.Group;
import org.jbpm.identity.Membership;
import org.jbpm.identity.User;
import org.jbpm.identity.hibernate.IdentitySession;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.DispatchContext;

import org.langhua.ofbiz.report.I_OFBizReport;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * To make the users in JBPM consistent with the users in OFBiz.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * @author Shang Zhengwu,shangzw@langhua.cn
 * 
 */
public class OFBizUserToJBPMUser {

	public OFBizUserToJBPMUser(){
		
	}
	/**
	 * Synchronize users from OFBiz to JBPM.
	 * 
	 */
	public static boolean syncUsersToUser(DispatchContext ctx)
			throws GenericEntityException {

		GenericDelegator delegator = ctx.getDelegator();
		List personList = delegator.findAll("UserLogin");
		if (personList.size() > 0) {
			Iterator iter = personList.iterator();
			while (iter.hasNext()) {
				GenericValue user = (GenericValue) iter.next();
				if (user != user.NULL_VALUE && !user.isEmpty()) {
					List groups = delegator.findByAnd("UserLoginSecurityGroup",
							UtilMisc.toMap("userLoginId", user
									.getString("userLoginId")));
					syncUserToUser(user, groups);
				}
			}
		} else {
			return false;
		}

		return true;
	}

	/**
	 * Synchronize users from OFBiz to JBPM.
	 * 
	 */
	public static boolean syncUsersToUser(GenericDelegator delegator) {

		List personList;
		try {
			personList = delegator.findAll("UserLogin");
		} catch (GenericEntityException e) {
			if (Debug.errorOn()) {
				Debug.log(e);
			}
			return false;
		}
		if (personList.size() > 0) {
			Iterator iter = personList.iterator();
			while (iter.hasNext()) {
				GenericValue user = (GenericValue) iter.next();
				if (user != user.NULL_VALUE && !user.isEmpty()) {
					List groups;
					try {
						groups = delegator.findByAnd("UserLoginSecurityGroup",
								UtilMisc.toMap("userLoginId", user
										.getString("userLoginId")));
					} catch (GenericEntityException e) {
						if (Debug.errorOn()) {
							Debug.log(e);
						}
						continue;
					}
					syncUserToUser(user, groups);
				}
			}
		} else {
			return false;
		}

		return true;
	}

	/**
	 * Synchronize users from OFBiz to JBPM and output the procedure info to html report.
	 * 
	 * @param delegator
	 * @param report
	 * @param locale
	 * @return true if the synchronization successful; else false.
	 */
	public static boolean syncUsersToUser(GenericDelegator delegator, I_OFBizReport report, Locale locale) {

		List personList;
		try {
			personList = delegator.findAll("UserLogin");
		} catch (GenericEntityException e) {
			if (Debug.errorOn()) {
				Debug.log(e);
			}
			return false;
		}
		int total = personList.size(); 
		if (total > 0) {
			report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_TOTAL_USERS_1", new Object[] {String.valueOf(personList.size())}, locale));
			for (int i=0; i<total; i++) {
				GenericValue user = (GenericValue) personList.get(i);
				if (user != user.NULL_VALUE && !user.isEmpty()) {
					List groups;
					try {
						groups = delegator.findByAnd("UserLoginSecurityGroup",
								UtilMisc.toMap("userLoginId", user
										.getString("userLoginId")));
					} catch (GenericEntityException e) {
						report.println(e);
						if (Debug.errorOn()) {
							Debug.log(e);
						}
						continue;
					}
					boolean result = syncUserToUser(user, groups, String.valueOf(i+1) + "/" + String.valueOf(total), report, locale);
					if (result) {
						report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USER_OK_0", locale), I_OFBizReport.FORMAT_OK);
					} else {
						report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USER_ERROR_1", new Object[] {user.getString("userLoginId")}, locale), I_OFBizReport.FORMAT_WARNING);
					}
				}
			}
		} else {
			return false;
		}

		return true;
	}
	
	/**
	 * Synchronize  user from OFBiz to JBPM.
	 * 
	 */
	public static boolean syncCurrentUserToUser(DispatchContext ctx,
			GenericValue userLogin) throws GenericEntityException {

		GenericDelegator delegator = ctx.getDelegator();
		if (userLogin != userLogin.NULL_VALUE && !userLogin.isEmpty()) {
			List groups = delegator.findByAnd("UserLoginSecurityGroup",
					UtilMisc.toMap("userLoginId", userLogin
							.getString("userLoginId")));
			syncUserToUser(userLogin, groups);
		}
		return true;
	}
	
	/**
	 * Synchronize user from OFBiz to JBPM.
	 * 
	 */
	public static boolean syncCurrentUserToUser(GenericDelegator delegator, GenericValue userLogin) {

		if (userLogin != userLogin.NULL_VALUE && !userLogin.isEmpty()) {
			List groups = null;
			try {
				groups = delegator.findByAnd("UserLoginSecurityGroup",
						UtilMisc.toMap("userLoginId", userLogin
								.getString("userLoginId")));
				syncUserToUser(userLogin, groups);
			} catch (GenericEntityException e) {
				if (Debug.errorOn()) {
					Debug.log(e);
				}
			}
		}

		return true;
	}
	
	/**
	 * Synchronize user from OFBiz to JBPM.
	 * 
	 */
	public static boolean syncUserToUser(GenericValue user, List groups) {

		String userName = user.getString("userLoginId");
		if (UtilValidate.isEmpty(userName)) {
			return false;
		}
		JbpmContext context1 = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		if (context1 == null) {
			context1 = JbpmConfiguration.getInstance().createJbpmContext();
		}
		if(context1 == null){
			
			return false;
		}
		// add current user to jBPM User
		User JBPMUser;

		if (!hasUserInJbpm(userName)) {
			// if no such a user, create it.
			JBPMUser = new User(userName);
			String password  = user.getString("currentPassword");
			if(UtilValidate.isEmpty(password))
				return false;
			JBPMUser.setPassword(password);
			JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
			if (context == null) {
				context = JbpmConfiguration.getInstance().createJbpmContext();
			}
			Session session = context.getSession();
			IdentitySession identitySession = new IdentitySession(session);
			identitySession.saveUser(JBPMUser);
		} else {
			JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
			if (context == null) {
				context = JbpmConfiguration.getInstance().createJbpmContext();
			}
			Session session = context.getSession();
			IdentitySession identitySession = new IdentitySession(session);
			JBPMUser = identitySession.getUserByName(userName);
		}

		// add the group(s) current user belonged to to jBPM Group
		GenericValue ofbizGroup;
		Group JBPMGroup;
		for (int k = 0; k < groups.size(); k++) {
			ofbizGroup = (GenericValue) groups.get(k);
			String groupName = ofbizGroup.getString("groupId");
			if (!hasGroupInJbpm(groupName)) {
				JBPMGroup = new Group(groupName, "security-role");
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				IdentitySession identitySession = new IdentitySession(session);
				identitySession.saveGroup(JBPMGroup);
			} else {
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				IdentitySession identitySession = new IdentitySession(session);
				JBPMGroup = identitySession.getGroupByName(groupName);
			}

			// set JBPMUser belong to JBPMGroup
			if (JBPMGroup.getUsers() != null) {
				if (!JBPMGroup.getUsers().contains(JBPMUser)) {
					Membership membership = Membership.create(JBPMUser,
							JBPMUser.getName() + "&" + JBPMGroup.getName(),
							JBPMGroup);
					JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
					if (context == null) {
						context = JbpmConfiguration.getInstance().createJbpmContext();
					}
					Session session = context.getSession();
					session.save(membership);
				}
			}
		}

		// check whether there are any more JBPMGroup names the user belonged
		// to, if yes, delete them.
		Set memberships = JBPMUser.getMemberships();
		if(memberships==null)
			return false;
		Iterator i = memberships.iterator();
		while (i.hasNext()) {
			Membership membership = (Membership) i.next();
			Group group = membership.getGroup();
			boolean found = false;
			for (int j = 0; j < groups.size(); j++) {
				if (group != null
						&& group.getName().equals(
								((GenericValue) groups.get(j)).getString("groupId"))) {
					found = true;
					break;
				}
			}
			if (!found) {
				// Set groupMemberships = group.getMemberships();
				// groupMemberships.remove(membership);
				membership.setGroup(null);
				membership.setUser(null);
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				session.save(membership);
				/**
				 * if (groupMemberships.size() <= 0) { session.delete(group); }
				 */
			}
		}

		return true;
	}

	/**
	 * Synchronize user from OFBiz to JBPM and output procedure messages to html report.
	 * 
	 */
	public static boolean syncUserToUser(GenericValue user, List groups, String number, I_OFBizReport report, Locale locale) {

		String userName = user.getString("userLoginId");
		report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_USER_START_2", new Object[] {number, userName}, locale));
		if (UtilValidate.isEmpty(userName)) {
			return false;
		}
		JbpmContext context1 = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		if (context1 == null) {
			context1 = JbpmConfiguration.getInstance().createJbpmContext();
		}
		if(context1 == null){
			
			return false;
		}
		// add current user to jBPM User
		User JBPMUser;

		if (!hasUserInJbpm(userName)) {
			// if no such a user, create it.
			JBPMUser = new User(userName);
			String password  = user.getString("currentPassword");
			if(UtilValidate.isEmpty(password))
				return false;
			JBPMUser.setPassword(password);
			JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
			if (context == null) {
				context = JbpmConfiguration.getInstance().createJbpmContext();
			}
			Session session = context.getSession();
			IdentitySession identitySession = new IdentitySession(session);
			identitySession.saveUser(JBPMUser);
		} else {
			JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
			if (context == null) {
				context = JbpmConfiguration.getInstance().createJbpmContext();
			}
			Session session = context.getSession();
			IdentitySession identitySession = new IdentitySession(session);
			JBPMUser = identitySession.getUserByName(userName);
		}

		// add the group(s) current user belonged to to jBPM Group
		GenericValue ofbizGroup;
		Group JBPMGroup;
		for (int k = 0; k < groups.size(); k++) {
			ofbizGroup = (GenericValue) groups.get(k);
			String groupName = ofbizGroup.getString("groupId");
			if (!hasGroupInJbpm(groupName)) {
				JBPMGroup = new Group(groupName, "security-role");
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				IdentitySession identitySession = new IdentitySession(session);
				identitySession.saveGroup(JBPMGroup);
				report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_CREATE_GROUP_1", new Object[] {groupName}, locale), I_OFBizReport.FORMAT_NOTE);
			} else {
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				IdentitySession identitySession = new IdentitySession(session);
				JBPMGroup = identitySession.getGroupByName(groupName);
			}

			// set JBPMUser belong to JBPMGroup
			if (JBPMGroup.getUsers() != null) {
				if (!JBPMGroup.getUsers().contains(JBPMUser)) {
					Membership membership = Membership.create(JBPMUser,
							JBPMUser.getName() + "&" + JBPMGroup.getName(),
							JBPMGroup);
					JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
					if (context == null) {
						context = JbpmConfiguration.getInstance().createJbpmContext();
					}
					Session session = context.getSession();
					session.save(membership);
					report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_CREATE_MEMBERSHIP_1", new Object[] {membership.getName()}, locale), I_OFBizReport.FORMAT_NOTE);
				}
			}
		}

		// check whether there are any more JBPMGroup names the user belonged
		// to, if yes, delete them.
		Set memberships = JBPMUser.getMemberships();
		if(memberships==null)
			return false;
		Iterator i = memberships.iterator();
		while (i.hasNext()) {
			Membership membership = (Membership) i.next();
			Group group = membership.getGroup();
			boolean found = false;
			for (int j = 0; j < groups.size(); j++) {
				if (group != null
						&& group.getName().equals(
								((GenericValue) groups.get(j)).getString("groupId"))) {
					found = true;
					break;
				}
			}
			if (!found) {
				// Set groupMemberships = group.getMemberships();
				// groupMemberships.remove(membership);
				membership.setGroup(null);
				membership.setUser(null);
				JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
				if (context == null) {
					context = JbpmConfiguration.getInstance().createJbpmContext();
				}
				Session session = context.getSession();
				session.save(membership);
				report.println(UtilProperties.getMessage("JBPMUiLabels", "GUI_JBPM_SYNCHRONIZE_DELETE_MEMBERSHIP_1", new Object[] {membership.getName()}, locale), I_OFBizReport.FORMAT_NOTE);
				/**
				 * if (groupMemberships.size() <= 0) { session.delete(group); }
				 */
			}
		}

		return true;
	}

	public static boolean hasUserInJbpm(String userName) {

		JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		if (context == null) {
			context = JbpmConfiguration.getInstance().createJbpmContext();
		}
		Session session = context.getSession();
		IdentitySession identitySession = new IdentitySession(session);
		boolean foundUser = false;
		try {
			identitySession.beginTransaction();
			List users = identitySession.getUsers();
			identitySession.getTransaction().commit();
			for (int i = 0; i < users.size(); i++) {
				User oneUser = (User) users.get(i);
				if (oneUser.getName().equals(userName)) {
					foundUser = true;
					break;
				}
			}
		} catch (SQLGrammarException e) {
			return foundUser;
		}
		return foundUser;
	}

	public static boolean hasGroupInJbpm(String groupName) {

		JbpmContext context = JbpmConfiguration.getInstance().getCurrentJbpmContext();
		if (context == null) {
			context = JbpmConfiguration.getInstance().createJbpmContext();
		}
		Session session = context.getSession();
		IdentitySession identitySession = new IdentitySession(session);
		Group group = null;
		try {
			identitySession.beginTransaction();
			group = identitySession.getGroupByName(groupName);
			identitySession.getTransaction().commit();
		} catch (SQLGrammarException e) {
			return false;
		}
		return group == null ? false : true;
	}


}