/*
 * This library is part of OpenCms Identity module of Langhua Opensource Foundation
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

package org.langhua.opencms.identity.sso.cas.db.jbossportal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.jasig.cas.client.validation.Assertion;
import org.opencms.db.CmsDbContext;
import org.opencms.db.CmsDbEntryNotFoundException;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsUser;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsOrganizationalUnit;
import org.opencms.security.CmsPasswordEncryptionException;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;
import org.langhua.opencms.identity.commons.CmsAuthenticationData;

/**
 * The OpenCms CAS Authorization Handler.<p>
 * 
 * New login error messages are added.
 * 
 * Client                  OpenCms                  CAS  JBoss Portal DB
 *   |          a url         |                      |         |
 *   |----------req---------->|check permission      |         |
 *   |                     allowed?                  |         |
 *   |<---------res-----------|yes                   |         |
 *   |               not allowd. guest?              |         |
 *   |<---------res-----------|not guest             |         |
 *   |                a guest. has ticket?           |         |
 *   |                        |      to /login       |         |
 *   |               no ticket|---------req--------->|         |
 *   |<-----------------res--------------------------|         |
 *   |           username/password                   |         |
 *   |------------------req------------------------->|         |
 *   |                        |                      |         |
 *   |                        |                    authz<----->| 
 *   |                        |               get attributes<->|
 *   |                        |                  logged in?    |
 *   |<-----------------res--------------------------|no       |
 *   |                        |    forward the url   |         |
 *   |                        |<--------res----------|yes      |
 *   |              a guest. has ticket?             |         |
 *   |                     yes|                      |         |
 *   |                   has CmsUser?                |         |
 *   |                      no|   /serviceValidate   |         |
 *   |                        |----------req-------->|         |
 *   |                        |<---------res---------|         |
 *   |                 store user info               |         |
 *   |                 parse assertion               |         |
 *   |              store user group/role            |         |
 *   |         the url        |                      |         |
 *   |<----------res----------|not a guest           |         |
 *
 * The ACL of a user is still controlled by OpenCms.
 *
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public final class CmsJBossPortalAuthorizationHandler extends CmsJBossPortalAuthnHandler {
	
    /**
     * Public constructor, initializes some required member variables.<p> 
     */
    public CmsJBossPortalAuthorizationHandler() {
    	super();
    }
    
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData,
			I_CmsUserDriver userDriver, Object assertionObj, CmsModule identityModule) throws CmsPasswordEncryptionException, CmsDataAccessException, CmsAuthentificationException {

		CmsUser user = null;
		String groupsAndRoles = (String) ((Assertion) assertionObj).getPrincipal().getAttributes().get("grouprolename");
		if (CmsStringUtil.isNotEmpty(groupsAndRoles)) {
			if (groupsAndRoles.startsWith("[")) {
				groupsAndRoles = groupsAndRoles.substring(1);
			}
			if (groupsAndRoles.endsWith("]")) {
				groupsAndRoles = groupsAndRoles.substring(0, groupsAndRoles.length() - 1);
			}
			List<String> relations = CmsStringUtil.splitAsList(groupsAndRoles, ", ");
			List<String> groups = new ArrayList<String>();
			List<String> roles = new ArrayList<String>();
			List<String> ous = new ArrayList<String>();
			for (String relation : relations) {
				if (relation.toLowerCase().endsWith(".group")) {
					groups.add(relation.substring(0, relation.length() - 6));
					ous.add(relation.substring(0, relation.lastIndexOf(CmsOrganizationalUnit.SEPARATOR) + 1));
				} else if (relation.toLowerCase().endsWith(".role")) {
					roles.add(relation.substring(0, relation.length() - 5));
				}
			}

			String username = authenticationData.getUserName();
			String userOU = "";
			for (String ou : ous) {
				userOU = ou.replaceFirst(CmsOrganizationalUnit.SEPARATOR, "");
				try {
					user = userDriver.readUser(dbc, userOU + username);
				} catch (CmsDataAccessException e) {
					LOG.debug(e);
				}
				if (user != null) {
					break;
				}
				userOU = "";
			}
			authenticationData.setUserName(userOU + username);
			
			// sync user info
			String cmsDigestType = OpenCms.getPasswordHandler().getDigestType();
			String cmsDigestEncoding = OpenCms.getPasswordHandler()
					.getInputEncoding();
			String cmsDigestPassword = OpenCms.getPasswordHandler().digest(
					authenticationData.getPassword(), cmsDigestType,
					cmsDigestEncoding);
			try {
				user = userDriver.readUser(dbc, authenticationData
						.getUserName());
			} catch (CmsDbEntryNotFoundException e1) {

				if (LOG.isErrorEnabled()) {
					LOG.error(e1.getLocalizedMessage(), e1);
				}

			}
			if (user == null) {
				// no such a user in OpenCms, add it
				Map attributes = ((Assertion) assertionObj).getAttributes();
				String lastName = (String) attributes.get("lastname");
				String firstName = (String) attributes.get("firstname");
				String email = (String) attributes.get("email");

				user = userDriver.createUser(dbc, (new CmsUUID()),
						authenticationData.getUserName(), cmsDigestPassword,
						firstName, lastName, email, 0, 0, 0, new HashMap());
			} else {
				if (!cmsDigestPassword.equals(user.getPassword())) {
					// passwords are not the same, change the one in OpenCms
					Connection conn = null;
					PreparedStatement stmt = null;
					try {
						conn = userDriver.getSqlManager().getConnection(dbc);
						stmt = userDriver.getSqlManager().getPreparedStatement(
								conn, "C_USERS_SET_PWD_3");
						stmt.setString(1, cmsDigestPassword);
						stmt.setString(2, CmsOrganizationalUnit.getSimpleName(authenticationData.getUserName()));
						stmt.setString(3, CmsOrganizationalUnit.SEPARATOR
								+ CmsOrganizationalUnit.getParentFqn(authenticationData.getUserName()));
						stmt.executeUpdate();
					} catch (SQLException e) {
						throw new CmsAuthentificationException(
								org.opencms.security.Messages
										.get()
										.container(
												org.opencms.security.Messages.ERR_LOGIN_FAILED_2,
												authenticationData.getUserName(),
												authenticationData.getRemoteAddress()), e);
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
				}
			}
			
			if (user == null) {
				throw new CmsAuthentificationException(
						org.opencms.security.Messages
								.get()
								.container(
										org.opencms.security.Messages.ERR_LOGIN_FAILED_2,
										authenticationData.getUserName(),
										authenticationData.getRemoteAddress()));
			}

			if (!user.getName().equals("Admin")) {
				user = syncGroupsAndRolesToCms(dbc, userDriver, user, groups, roles);
			}
		}
		return user;
	}
	
}