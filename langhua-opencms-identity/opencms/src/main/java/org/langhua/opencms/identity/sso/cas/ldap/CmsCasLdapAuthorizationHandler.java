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

package org.langhua.opencms.identity.sso.cas.ldap;

import java.util.ArrayList;
import java.util.List;
import org.jasig.cas.client.validation.Assertion;
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsUser;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsPasswordEncryptionException;
import org.opencms.util.CmsStringUtil;
import com.ca.commons.naming.DN;

import org.langhua.opencms.identity.commons.CmsAuthenticationData;
import org.langhua.opencms.identity.ldap.CmsLdapAuthenticationHandler;

/**
 * The OpenCms CAS Authorization Handler.<p>
 * 
 * New login error messages are added.
 * 
 * Client                  OpenCms                  CAS       LDAP
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
 *   |                        |                      |<-authz->|
 *   |                        |                  logged in?    |
 *   |<-----------------res--------------------------|no       |
 *   |                        |    forward the url   |         |
 *   |                        |<--------res----------|yes      |
 *   |              a guest. has ticket?             |         |
 *   |                     yes|                      |         |
 *   |                   has CmsUser?                |         |
 *   |                      no|   /serviceValidate   |         |
 *   |                        |----------req-------->|         |
 *   |                        |                      |<-find-->|
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
public final class CmsCasLdapAuthorizationHandler extends CmsCasLdapAuthenticationHandler {
	
    /**
     * Public constructor, initializes some required member variables.<p> 
     */
    public CmsCasLdapAuthorizationHandler() {
    	
    }
    
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData,
			I_CmsUserDriver userDriver, Object assertionObj, CmsModule identityModule) throws CmsPasswordEncryptionException, CmsDataAccessException, CmsAuthentificationException {

		CmsUser user = super.syncUserToCms(dbc, authenticationData, userDriver, assertionObj, identityModule);

		if (!user.getName().equals("Admin")) {
			String groupSearchDN = getParamGroupSearchDN(identityModule);
			String roleSearchDN = getParamRoleSearchDN(identityModule);
			String groupsAttribute = (String) ((Assertion) assertionObj).getPrincipal().getAttributes().get("groups");
			LOG.debug("groups attribute: " + groupsAttribute);
			List<DN> groups = new ArrayList<DN>();
			if (CmsStringUtil.isNotEmpty(groupsAttribute)) {
				String[] groupsArray = CmsStringUtil.splitAsArray(groupsAttribute, '|');
				for (int i=0; i<groupsArray.length; i++) {
					String group = groupsArray[i];
					if (group.startsWith("alias")) {
						group = group.substring(group.indexOf(',') + 1);
					}
					groups.add(new DN(group));
				}
			}

			String rolesAttribute = (String) ((Assertion) assertionObj).getPrincipal().getAttributes().get("roles");
			LOG.debug("roles attribute: " + groupsAttribute);
			List<DN> roles = new ArrayList<DN>();
			if (CmsStringUtil.isNotEmpty(rolesAttribute)) {
				String[] rolesArray = CmsStringUtil.splitAsArray(rolesAttribute, '|');
				for (int i=0; i<rolesArray.length; i++) {
					String role = rolesArray[i];
					if (role.startsWith("alias")) {
						role = role.substring(role.indexOf(',') + 1);
					}
					roles.add(new DN(role));
				}
			}

			user = syncGroupsAndRolesToCms(dbc, userDriver, user, new DN(groupSearchDN), groups, new DN(roleSearchDN), roles);
		}
		
		return user;
		
	}
	
	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamGroupSearchDN(CmsModule identityModule) {
		return identityModule.getParameter(CmsLdapAuthenticationHandler.LDAP_GROUP_SEARCH_DN, getParamBaseDN(identityModule));
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamRoleSearchDN(CmsModule identityModule) {
		return identityModule.getParameter(CmsLdapAuthenticationHandler.LDAP_ROLE_SEARCH_DN, getParamBaseDN(identityModule));
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamBaseDN(CmsModule identityModule) {
		return identityModule.getParameter(CmsLdapAuthenticationHandler.LDAP_BASEDN);
	}

}