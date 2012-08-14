/*
 * This library is part of OpenCms Identity module of Langhua Opensource Foundation
 *
 * Copyright (C) 2009  Langhua Opensource Foundation (http://langhua.org)
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
 * For the latest version about this module, please see the
 * project website: http://langhua.org/opensource/opencms/opencms-identity/
 * 
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.langhua.opencms.identity.commons;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsGroup;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProject;
import org.opencms.file.CmsUser;
import org.opencms.i18n.CmsMessageContainer;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsOrganizationalUnit;
import org.opencms.security.CmsPasswordEncryptionException;
import org.opencms.security.I_CmsPrincipal;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;
import org.opencms.workplace.CmsLogin;

import org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler;

import com.ca.commons.naming.DN;

/**
 * The abstract Authentication Handler.
 * 
 * The ACL of a user is still controlled by OpenCms.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public abstract class A_CmsAuthenticationHandler implements
		I_CmsAuthenticationHandler {

	/** The log object for this class. */
	public final Log LOG = CmsLog.getLog(A_CmsAuthenticationHandler.class);

    /** The name of the offline project. */
    private static final String OFFLINE_PROJECT_NAME = "Offline";

    /**
	 * Init password
	 * 
	 * @param password
	 * @return
	 */
	protected String initPassword(String password) {
		return password;
	}

	/**
	 * Init password
	 * 
	 * @param password
	 * @return
	 */
	protected String postProcessPassword(String password) {
		return password;
	}

	/**
	 * 
	 * Login a user.
	 * 
	 * @param dbc
	 * @param userName
	 * @param password
	 * @param remoteAddress
	 * @param userDriver
	 * 
	 * @throws CmsPasswordEncryptionException
	 * @throws CmsDataAccessException
	 * @throws CmsAuthentificationException
	 * 
	 * @return updated username and password if the user can login successfully;
	 *         otherwise, both values will be null.
	 */
	public abstract CmsAuthenticationData loginUser(CmsDbContext dbc,
			String userName, String password, String remoteAddress,
			I_CmsUserDriver userDriver) throws CmsPasswordEncryptionException,
			CmsDataAccessException, CmsAuthentificationException;

	/**
	 * Synchronize the user from identity source to OpenCms database.
	 * 
	 * @param dbc
	 * @param authenticationData
	 * @param userDriver
	 * @param searchContext
	 * @param userSearchResult
	 * @param identityModule
	 * @throws CmsPasswordEncryptionException
	 * @throws CmsDataAccessException
	 * @throws CmsAuthentificationException
	 */
	public abstract CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData,
			I_CmsUserDriver userDriver, Object result, CmsModule identityModule)
			throws CmsPasswordEncryptionException, CmsDataAccessException,
			CmsAuthentificationException;

	protected void assignUserInRoles(I_CmsUserDriver userDriver,
			CmsDbContext dbc, CmsUser user, CmsModule identityModule)
			throws CmsDataAccessException {
		// assign user's role
		String roleType = identityModule
				.getParameter(I_CmsAuthenticationHandler.AUTO_USER_ROLENAME);
		if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(roleType)) {
			// get the user's group(s) and role(s)
			boolean relationExists = false;
			try {
				List<CmsGroup> relations = userDriver.getGroups(dbc, userDriver.readOrganizationalUnit(dbc, ""), true, true);
				if (relations.size() > 0) {
					for (CmsGroup relation : relations) {
						if (relation.isGroup()) {
							continue;
						}
						if (relation.getName().equals(roleType)) {
							relationExists = true;
						} else {
							// remove the relation of role
							userDriver.deleteUserInGroup(dbc, user.getId(),
									relation.getId());
						}
					}
				}
			} catch (CmsDataAccessException e) {
				// do nothing
			}
			if (!relationExists) {
				userDriver.createUserInGroup(dbc, user.getId(), CmsUUID
						.getConstantUUID(roleType));
			}
		}
	}

	/**
	 * For authentication, only one group a user should belong to.
	 * 
	 * @param userDriver
	 * @param dbc
	 * @param user
	 * @throws CmsDataAccessException
	 */
	protected void assignUserInGroups(I_CmsUserDriver userDriver,
			CmsDbContext dbc, CmsUser user) throws CmsDataAccessException {
		// get the user's group(s)
		boolean relationExists = false;
		try {
			List<CmsGroup> groups = userDriver.getGroups(dbc, userDriver.readOrganizationalUnit(dbc, ""), true, false);
			if (groups.size() > 0) {
				for (CmsGroup group : groups) {
					if (group.getName().equals(OpenCms.getDefaultUsers().getGroupUsers())) {
						relationExists = true;
					} else {
						// remove the relation
						userDriver.deleteUserInGroup(dbc, user.getId(),
								group.getId());
					}
				}
			}
		} catch (CmsDataAccessException e) {
			// do nothing
		}
		// assign user's group
		if (!relationExists) {
			userDriver.createUserInGroup(dbc, user.getId(), CmsUUID
					.getConstantUUID(OpenCms.getDefaultUsers().getGroupUsers()));
		}
	}

	/**
	 * @see org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler#preLogin(CmsLogin,
	 *      CmsModule)
	 */
	public void preLogin(CmsLogin jsp, CmsModule identityModule)
			throws IOException {

	}

	/**
	 * @see org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler#preLogout(CmsLogin,
	 *      CmsModule)
	 */
	public void preLogout(CmsLogin cmsLogin, CmsModule identityModule)
			throws IOException {

	}

	/**
	 * @see org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler#getMessage(CmsObject,
	 *      String, String)
	 */
	public CmsMessageContainer getMessage(CmsObject cms, String username,
			String key) {
		if (org.langhua.opencms.identity.Messages.ERR_LDAP_LOGIN_FAILED_2 == key) {
			return org.langhua.opencms.identity.Messages
					.get()
					.container(
							org.langhua.opencms.identity.Messages.ERR_LDAP_LOGIN_FAILED_2,
							username,
							cms.getRequestContext().getRemoteAddress());
		} else if (org.langhua.opencms.identity.Messages.ERR_LDAP_CONNECTION_FAILED_0 == key) {
			return org.langhua.opencms.identity.Messages
					.get()
					.container(
							org.langhua.opencms.identity.Messages.ERR_LDAP_CONNECTION_FAILED_0);
		}
		return null;
	}

	protected CmsUser syncGroupsAndRolesToCms(CmsDbContext dbc,
			I_CmsUserDriver userDriver, CmsUser user, DN groupRootDN,
			List<DN> groups, DN roleRootDN, List<DN> roles) {

		List<CmsGroup> groupsInLdap = new ArrayList<CmsGroup>();
		List<CmsGroup> rolesInLdap = new ArrayList<CmsGroup>();

		// sync groups from LDAP to OpenCms
		LOG.debug(groups.size() + " groups will be synchronized.");
		for (int i = 0; i < groups.size(); i++) {
			DN groupDN = groups.get(i);
			LOG.debug("Synchronizing group " + groupDN.toString());
			String ou = "";
			String group = "";
			if (groupDN.size() - 1 >= 0) {
				group = groupDN.getLowestRDN().getRawVal();
			}
			// create OUs if necessary
			CmsOrganizationalUnit cmsOU = null;
			for (int j = groupRootDN.size(); j < groupDN.size() - 1; j++) {
				try {
					cmsOU = userDriver.readOrganizationalUnit(dbc, ou
							+ groupDN.getRDNValue(j) + "/");
				} catch (CmsDataAccessException e) {
					// this ou does not exist
				}
				if (cmsOU == null) {
					try {
						CmsOrganizationalUnit parent = null;
						try {
							parent = userDriver.readOrganizationalUnit(dbc, ou);
						} catch (CmsDataAccessException e) {
						}
						if (dbc.getRequestContext().currentProject().isOnlineProject()) {
							Connection conn = null;
							PreparedStatement stmt = null;
					        ResultSet res = null;
							String offlineProjectId = "";
							try {
								conn = userDriver.getSqlManager().getConnection(dbc);
								stmt = userDriver.getSqlManager().getPreparedStatement(
										conn, "C_PROJECTS_READ_BYNAME_2");
								stmt.setString(1, OFFLINE_PROJECT_NAME);
								stmt.setString(2, "/" + parent.getName());
								res = stmt.executeQuery();
								if (res.next()) {
									offlineProjectId = res.getString(1);
									while (res.next()) {
									}
								}
							} catch (SQLException e) {
							} finally {
								try {
									stmt.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
								try {
									conn.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
							}
							if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(offlineProjectId)) {
								try {
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(new CmsUUID(offlineProjectId)));
									CmsOrganizationalUnit newOrgUnit = userDriver.createOrganizationalUnit(dbc, groupDN.getRDNValue(j),
											"Auto created OU - " + ou
											+ groupDN.getRDNValue(j) + "/",
											I_CmsPrincipal.FLAG_ENABLED, parent, "/" + ou
											+ groupDN.getRDNValue(j) + "/");
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(CmsProject.ONLINE_PROJECT_ID));
								} catch (NumberFormatException e) {
								} catch (CmsException e) {
								}
							}
						} else {
							userDriver.createOrganizationalUnit(dbc, groupDN.getRDNValue(j),
									"Auto created OU - " + ou
									+ groupDN.getRDNValue(j) + "/",
									I_CmsPrincipal.FLAG_ENABLED, parent, "/" + ou
									+ groupDN.getRDNValue(j) + "/");
						}
					} catch (CmsDataAccessException e) {
						LOG.debug(e);
					}
				}
				ou += groupDN.getRDNValue(j) + "/";
			}
			// create the group if necessary
			CmsGroup cmsGroup = null;
			try {
				cmsGroup = userDriver.readGroup(dbc, ou + group);
			} catch (CmsDataAccessException e) {
				LOG.debug(e);
			}
			if (cmsGroup == null) {
				try {
					LOG.debug("Create group " + ou + group);
					cmsGroup = userDriver.createGroup(dbc, new CmsUUID(), ou
							+ group, "Auto created group - " + group,
							I_CmsPrincipal.FLAG_ENABLED, ou + "Users");
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
			// set the user belong to the group
			if (cmsGroup != null) {
				try {
					LOG.debug("Create user " + user.getName() + " in group "
							+ cmsGroup.getOuFqn() + cmsGroup.getName());
					userDriver.createUserInGroup(dbc, user.getId(), cmsGroup
							.getId());
					if (cmsGroup != null) {
						groupsInLdap.add(cmsGroup);
					}
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}

			// set the user belong to the OU
			if (cmsOU != null && (user.getOuFqn() == null || user.getOuFqn().equals(""))) {
				try {
					userDriver.setUsersOrganizationalUnit(dbc, cmsOU, user);
					user.setName(cmsOU.getName() + user.getName());
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
		}

		// sync roles from LDAP to OpenCms
		LOG.debug(roles.size() + " roles will be synchronized.");
		for (int i = 0; i < roles.size(); i++) {
			DN roleDN = roles.get(i);
			LOG.debug("Synchronizing role " + roleDN.toString());
			String ou = "";
			String role = roleDN.getLowestRDN().getRawVal();
			// create OUs if necessary
			for (int j = roleRootDN.size(); j < roleDN.size() - 1; j++) {
				CmsOrganizationalUnit cmsOU = null;
				try {
					cmsOU = userDriver.readOrganizationalUnit(dbc, ou
							+ roleDN.getRDNValue(j) + "/");
				} catch (CmsDataAccessException e) {
					// this ou does not exist
				}
				if (cmsOU == null) {
					try {
						CmsOrganizationalUnit parent = null;
						try {
							parent = userDriver.readOrganizationalUnit(dbc, ou);
						} catch (CmsDataAccessException e) {
						}
						if (dbc.getRequestContext().currentProject().isOnlineProject()) {
							Connection conn = null;
							PreparedStatement stmt = null;
					        ResultSet res = null;
							String offlineProjectId = "";
							try {
								conn = userDriver.getSqlManager().getConnection(dbc);
								stmt = userDriver.getSqlManager().getPreparedStatement(
										conn, "C_PROJECTS_READ_BYNAME_2");
								stmt.setString(1, OFFLINE_PROJECT_NAME);
								stmt.setString(2, "/" + parent.getName());
								res = stmt.executeQuery();
								if (res.next()) {
									offlineProjectId = res.getString(1);
									while (res.next()) {
									}
								}
							} catch (SQLException e) {
							} finally {
								try {
									stmt.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
								try {
									conn.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
							}
							if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(offlineProjectId)) {
								try {
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(new CmsUUID(offlineProjectId)));
									CmsOrganizationalUnit newOrgUnit = userDriver.createOrganizationalUnit(dbc, roleDN.getRDNValue(j),
											"Auto created OU - " + ou
											+ roleDN.getRDNValue(j) + "/",
											I_CmsPrincipal.FLAG_ENABLED, parent, "/" + ou
											+ roleDN.getRDNValue(j) + "/");
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(CmsProject.ONLINE_PROJECT_ID));
								} catch (NumberFormatException e) {
								} catch (CmsException e) {
								}
							}
						} else {
							userDriver.createOrganizationalUnit(dbc, roleDN.getRDNValue(j),
									"Auto created OU - " + ou
									+ roleDN.getRDNValue(j) + "/",
									I_CmsPrincipal.FLAG_ENABLED, parent, "/" + ou
									+ roleDN.getRDNValue(j) + "/");
						}
					} catch (CmsDataAccessException e) {
						LOG.debug(e);
					}
				}
				ou += roleDN.getRDNValue(j) + "/";
			}
			// create the role if necessary
			CmsGroup cmsRole = null;
			try {
				cmsRole = userDriver.readGroup(dbc, ou + role);
			} catch (CmsDataAccessException e) {
				LOG.debug(e);
			}
			if (cmsRole == null) {
				try {
					LOG.debug("Create role " + ou + role);
					cmsRole = userDriver.createGroup(dbc, new CmsUUID(), ou
							+ role, "Auto created role - " + role,
							I_CmsPrincipal.FLAG_GROUP_ROLE, null);
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
			// set the user belong to the role
			if (cmsRole != null) {
				try {
					LOG.debug("Create user " + user.getName() + " in role "
							+ cmsRole.getOuFqn() + cmsRole.getName());
					userDriver.createUserInGroup(dbc, user.getId(), cmsRole
							.getId());
					if (cmsRole != null) {
						rolesInLdap.add(cmsRole);
					}
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
		}

		// get user's current groups and roles
		List<CmsGroup> groupsAndRolesInCms = new ArrayList<CmsGroup>();
		try {
			groupsAndRolesInCms = userDriver.readGroupsOfUser(dbc,
					user.getId(), "", true, user.getAddress(), false);
		} catch (CmsDataAccessException e) {
			LOG.debug(e);
		}

		// if a group is not in LDAP, delete the group membership
		for (int i = 0; i < groupsAndRolesInCms.size(); i++) {
			CmsGroup groupAndRole = (CmsGroup) groupsAndRolesInCms.get(i);
			if (groupAndRole.isGroup() && !groupsInLdap.contains(groupAndRole)) {
				try {
					userDriver.deleteUserInGroup(dbc, user.getId(),
							groupAndRole.getId());
				} catch (CmsDataAccessException e) {
					try {
						userDriver.deleteUserInGroup(dbc, user.getId(),
								groupAndRole.getId());
					} catch (CmsDataAccessException e1) {
						LOG.debug(e1);
					}
				}
			}
		}

		try {
			groupsAndRolesInCms = userDriver.readGroupsOfUser(dbc,
					user.getId(), "", true, user.getAddress(), true);
		} catch (CmsDataAccessException e) {
			LOG.debug(e);
		}

		// if a role is not in LDAP, delete the role membership
		for (int i = 0; i < groupsAndRolesInCms.size(); i++) {
			CmsGroup groupAndRole = (CmsGroup) groupsAndRolesInCms.get(i);
			if (groupAndRole.isRole() && !rolesInLdap.contains(groupAndRole)) {
				try {
					userDriver.deleteUserInGroup(dbc, user.getId(),
							groupAndRole.getId());
				} catch (CmsDataAccessException e) {
					try {
						userDriver.deleteUserInGroup(dbc, user.getId(),
								groupAndRole.getId());
					} catch (CmsDataAccessException e1) {
						LOG.debug(e1);
					}
				}
			}
		}
		
		return user;
	}

	protected CmsUser syncGroupsAndRolesToCms(CmsDbContext dbc,
			I_CmsUserDriver userDriver, CmsUser user, List<String> groups,
			List<String> roles) {

		List<CmsGroup> groupsInDB = new ArrayList<CmsGroup>();
		List<CmsGroup> rolesInDB = new ArrayList<CmsGroup>();

		// sync groups and roles from DB to OpenCms
		LOG.debug(groups.size() + " groups will be synchronized.");
		for (int i = 0; i < groups.size(); i++) {
			String group = groups.get(i);
			LOG.debug("Synchronizing group " + group);
			List<String> groupPathes = new ArrayList<String>();
			do {
				group = CmsOrganizationalUnit.getParentFqn(group);
				groupPathes.add(group);
			} while (CmsOrganizationalUnit.getParentFqn(group) != null);
			
			// create OUs if necessary
			CmsOrganizationalUnit cmsOU = null;
			for (int j = groupPathes.size() - 1; j >= 0; j--) {
				String groupPath = groupPathes.get(j);
				try {
					if (groupPath.startsWith("/")) {
						cmsOU = userDriver.readOrganizationalUnit(dbc, groupPath.substring(1));
					} else {
						cmsOU = userDriver.readOrganizationalUnit(dbc, groupPath);
					}
				} catch (CmsDataAccessException e) {
					// this ou does not exist
					cmsOU = null;
				}
				if (cmsOU == null) {
					try {
						CmsOrganizationalUnit parent = null;
						if (j == groupPathes.size() - 1) {
							parent = userDriver.readOrganizationalUnit(dbc, "");
						} else {
							try {
								if (groupPathes.get(j + 1).startsWith("/")) {
									parent = userDriver.readOrganizationalUnit(dbc, groupPathes.get(j + 1).substring(1));
								} else {
									parent = userDriver.readOrganizationalUnit(dbc, groupPathes.get(j + 1));
								}
							} catch (CmsDataAccessException e) {
							}
						}

						if (dbc.getRequestContext().currentProject().isOnlineProject()) {
							Connection conn = null;
							PreparedStatement stmt = null;
					        ResultSet res = null;
							String offlineProjectId = "";
							try {
								conn = userDriver.getSqlManager().getConnection(dbc);
								stmt = userDriver.getSqlManager().getPreparedStatement(
										conn, "C_PROJECTS_READ_BYNAME_2");
								stmt.setString(1, OFFLINE_PROJECT_NAME);
								stmt.setString(2, "/" + parent.getName());
								res = stmt.executeQuery();
								if (res.next()) {
									offlineProjectId = res.getString(1);
									while (res.next()) {
									}
								}
							} catch (SQLException e) {
							} finally {
								try {
									stmt.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
								try {
									conn.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
							}
							if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(offlineProjectId)) {
								try {
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(new CmsUUID(offlineProjectId)));
									cmsOU = userDriver.createOrganizationalUnit(dbc, CmsOrganizationalUnit.getSimpleName(groupPath),
											"Auto created OU - " + groupPath,
											I_CmsPrincipal.FLAG_ENABLED, parent, groupPath);
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(CmsProject.ONLINE_PROJECT_ID));
								} catch (NumberFormatException e) {
								} catch (CmsException e) {
								}
							}
						} else {
							userDriver.createOrganizationalUnit(dbc,
									CmsOrganizationalUnit
											.getSimpleName(groupPath),
									"Auto created OU - " + groupPath,
									I_CmsPrincipal.FLAG_ENABLED, parent,
									groupPath);
						}
					} catch (CmsDataAccessException e) {
						LOG.debug(e);
					}
				}
			}

			// create the group if necessary
			CmsGroup cmsGroup = null;
			try {
				if (groups.get(i).startsWith("/")) {
					cmsGroup = userDriver.readGroup(dbc, groups.get(i).substring(1));
				} else {
					cmsGroup = userDriver.readGroup(dbc, groups.get(i));
				}
			} catch (CmsDataAccessException e) {
				LOG.debug(e);
			}
			if (cmsGroup == null) {
				try {
					LOG.debug("Create group " + groups.get(i));
					String tempGroup = groups.get(i);
					if (tempGroup.startsWith(CmsOrganizationalUnit.SEPARATOR)) {
						tempGroup = tempGroup.substring(1);
					}
					cmsGroup = userDriver.createGroup(dbc, new CmsUUID(),
							tempGroup, "Auto created group - "
									+ groups.get(i),
							I_CmsPrincipal.FLAG_ENABLED, CmsOrganizationalUnit
									.getParentFqn(tempGroup)
									+ "Users");
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}

			// set the user belong to the group
			if (cmsGroup != null) {
				try {
					LOG.debug("Create user " + user.getName() + " in group "
							+ cmsGroup.getName());
					userDriver.createUserInGroup(dbc, user.getId(), cmsGroup
							.getId());
					if (cmsGroup != null) {
						groupsInDB.add(cmsGroup);
					}
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
			
			// set the user belong to the OU
			if (cmsOU != null && (user.getOuFqn() == null || user.getOuFqn().equals(""))) {
				try {
					userDriver.setUsersOrganizationalUnit(dbc, cmsOU, user);
					user.setName(cmsOU.getName() + user.getName());
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
		}
		
		LOG.debug(roles.size() + " roles will be synchronized.");
		for (int i = 0; i < roles.size(); i++) {
			String role = roles.get(i);
			LOG.debug("Synchronizing role " + role);
			List<String> rolePathes = new ArrayList<String>();
			do {
				role = CmsOrganizationalUnit.getParentFqn(role);
				rolePathes.add(role);
			} while (CmsOrganizationalUnit.getParentFqn(role) != null);
			// create OUs if necessary
			for (int j = rolePathes.size() - 1; j >= 0; j--) {
				CmsOrganizationalUnit cmsOU = null;
				String rolePath = rolePathes.get(j);
				try {
					if (rolePath.startsWith("/")) {
						cmsOU = userDriver.readOrganizationalUnit(dbc, rolePath.substring(1));
					} else {
						cmsOU = userDriver.readOrganizationalUnit(dbc, rolePath);
					}
				} catch (CmsDataAccessException e) {
				}
				if (cmsOU == null) {
					try {
						CmsOrganizationalUnit parent = null;
						if (j == rolePathes.size() - 1) {
							parent = userDriver.readOrganizationalUnit(dbc, "");
						} else {
							try {
								if (rolePathes.get(j + 1).startsWith("/")) {
									parent = userDriver.readOrganizationalUnit(dbc, rolePathes.get(j + 1).substring(1));
								} else {
									parent = userDriver.readOrganizationalUnit(dbc, rolePathes.get(j + 1));
								}
							} catch (CmsDataAccessException e) {
							}
						}
						if (dbc.getRequestContext().currentProject().isOnlineProject()) {
							Connection conn = null;
							PreparedStatement stmt = null;
					        ResultSet res = null;
							String offlineProjectId = "";
							try {
								conn = userDriver.getSqlManager().getConnection(dbc);
								stmt = userDriver.getSqlManager().getPreparedStatement(
										conn, "C_PROJECTS_READ_BYNAME_2");
								stmt.setString(1, OFFLINE_PROJECT_NAME);
								stmt.setString(2, "/" + parent.getName());
								res = stmt.executeQuery();
								if (res.next()) {
									offlineProjectId = res.getString(1);
									while (res.next()) {
									}
								}
							} catch (SQLException e) {
							} finally {
								try {
									stmt.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
								try {
									conn.close();
								} catch (SQLException e) {
									if (LOG.isErrorEnabled()) {
										LOG.error(e.getLocalizedMessage(), e);
									}
								}
							}
							if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(offlineProjectId)) {
								try {
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(new CmsUUID(offlineProjectId)));
									CmsOrganizationalUnit newOrgUnit = userDriver.createOrganizationalUnit(dbc, CmsOrganizationalUnit.getSimpleName(rolePath),
											"Auto created OU - " + rolePath,
											I_CmsPrincipal.FLAG_ENABLED, parent, rolePath);
									dbc.getRequestContext().setCurrentProject(OpenCms.initCmsObject("Guest").readProject(CmsProject.ONLINE_PROJECT_ID));
								} catch (NumberFormatException e) {
								} catch (CmsException e) {
								}
							}
						} else {
							userDriver.createOrganizationalUnit(dbc, CmsOrganizationalUnit.getSimpleName(rolePath),
									"Auto created OU - " + rolePath,
									I_CmsPrincipal.FLAG_ENABLED, parent, rolePath);
						}
					} catch (CmsDataAccessException e) {
						LOG.debug(e);
					}
				}
			}

			// create the role if necessary
			CmsGroup cmsRole = null;
			try {
				if (roles.get(i).startsWith("/")) {
					cmsRole = userDriver.readGroup(dbc, roles.get(i).substring(1));
				} else {
					cmsRole = userDriver.readGroup(dbc, roles.get(i));
				}
			} catch (CmsDataAccessException e) {
				LOG.debug(e);
			}
			if (cmsRole == null) {
				try {
					LOG.debug("Create role " + roles.get(i));
					cmsRole = userDriver.createGroup(dbc, new CmsUUID(), roles.get(i), "Auto created role - " + role,
							I_CmsPrincipal.FLAG_GROUP_ROLE, null);
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}

			// set the user belong to the role
			if (cmsRole != null) {
				try {
					LOG.debug("Create user " + user.getName() + " in role "
							+ cmsRole.getName());
					userDriver.createUserInGroup(dbc, user.getId(), cmsRole
							.getId());
					if (cmsRole != null) {
						rolesInDB.add(cmsRole);
					}
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
			}
		}

		// get user's current groups and roles
		List<CmsGroup> groupsAndRolesInCms = new ArrayList<CmsGroup>();
		try {
			groupsAndRolesInCms = userDriver.readGroupsOfUser(dbc,
					user.getId(), "", true, user.getAddress(), false);
		} catch (CmsDataAccessException e) {
			LOG.debug(e);
		}

		// if a group is not in DB, delete the group membership
		for (int i = 0; i < groupsAndRolesInCms.size(); i++) {
			CmsGroup groupAndRole = (CmsGroup) groupsAndRolesInCms.get(i);
			if (groupAndRole.isGroup() && !groupsInDB.contains(groupAndRole)) {
				try {
					userDriver.deleteUserInGroup(dbc, user.getId(),
							groupAndRole.getId());
				} catch (CmsDataAccessException e) {
					try {
						userDriver.deleteUserInGroup(dbc, user.getId(),
								groupAndRole.getId());
					} catch (CmsDataAccessException e1) {
						LOG.debug(e1);
					}
				}
			}
		}

		try {
			groupsAndRolesInCms = userDriver.readGroupsOfUser(dbc,
					user.getId(), "", true, user.getAddress(), true);
		} catch (CmsDataAccessException e) {
			LOG.debug(e);
		}

		// if a role is not in DB, delete the role membership
		for (int i = 0; i < groupsAndRolesInCms.size(); i++) {
			CmsGroup groupAndRole = (CmsGroup) groupsAndRolesInCms.get(i);
			if (groupAndRole.isRole() && !rolesInDB.contains(groupAndRole)) {
				try {
					userDriver.deleteUserInGroup(dbc, user.getId(),
							groupAndRole.getId());
				} catch (CmsDataAccessException e) {
					try {
						userDriver.deleteUserInGroup(dbc, user.getId(),
								groupAndRole.getId());
					} catch (CmsDataAccessException e1) {
						LOG.debug(e1);
					}
				}
			}
		}
		
		return user;
	}

}