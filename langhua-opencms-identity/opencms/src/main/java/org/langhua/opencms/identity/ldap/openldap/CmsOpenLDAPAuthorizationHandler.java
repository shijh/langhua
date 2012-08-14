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

package org.langhua.opencms.identity.ldap.openldap;

import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.logging.Log;
import org.jasig.cas.util.LdapUtils;
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsUser;
import org.opencms.i18n.CmsEncoder;
import org.opencms.main.CmsLog;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsPasswordEncryptionException;
import com.ca.commons.naming.DN;

import org.langhua.opencms.identity.commons.CmsAuthenticationData;
import org.langhua.opencms.identity.ldap.CmsLdapAuthenticationHandler;

/**
 * The OpenCms LDAP Authorization Handler.<p>
 * 
 * The ACL is controlled by OpenLDAP.<p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public final class CmsOpenLDAPAuthorizationHandler extends CmsOpenLDAPAuthenticationHandler {

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsOpenLDAPAuthorizationHandler.class);
    
    public static final String AUTHZTYPE_MEMBER = "member";
    
    public static final String AUTHZTYPE_ALIAS = "alias";
    
    /**
     * Public constructor, initializes some required member variables.<p> 
     */
    public CmsOpenLDAPAuthorizationHandler() {
    	
    }
    
    public static CmsOpenLDAPAuthorizationHandler getInstance() {
    	return new CmsOpenLDAPAuthorizationHandler();
    }
    
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData, I_CmsUserDriver userDriver, Object userSearchResult, CmsModule identityModule) throws CmsPasswordEncryptionException, CmsDataAccessException, CmsAuthentificationException {

		String authorType = identityModule.getParameter(CmsLdapAuthenticationHandler.LDAP_AUTHZ_TYPE, AUTHZTYPE_MEMBER);
		List<DN> roles = new ArrayList<DN>();
		List<DN> groups = new ArrayList<DN>();
		String groupSearchDN = getParamGroupSearchDN(identityModule);
		String roleSearchDN = getParamRoleSearchDN(identityModule);
		String userDN = CmsEncoder.encode(((SearchResult) userSearchResult).getNameInNamespace()).replaceAll("%", "\\\\");
		if (authorType.equalsIgnoreCase(AUTHZTYPE_ALIAS)) {
			// alias
			String derefAliases = identityModule.getParameter(CmsLdapAuthenticationHandler.LDAP_DEREF_ALIASES, "finding");
			try {
				getSearchContext().addToEnvironment("java.naming.ldap.derefAliases", derefAliases);
			} catch (NamingException e) {
				LOG.error(e);
			}
			
			SearchControls controls = new SearchControls();
			controls.setTimeLimit(1000);
			controls.setCountLimit(20);
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			String attribute = getParamAttribute(identityModule);
			attribute = LdapUtils.getFilterWithValues(attribute,
					authenticationData.getUserName());
			
			// get groups
			try {
				String groupAlias = "aliasedObjectName=" + userDN;
				NamingEnumeration<SearchResult> answer = getSearchContext().search(groupSearchDN,
						"(&(objectClass=alias)(" + groupAlias + "))", controls);
				while (answer.hasMoreElements()) {
					SearchResult result = (SearchResult) answer.next();
					String groupName = result.getNameInNamespace();
					groups.add((new DN(groupName)).parentDN());
					LOG.debug("User group name: " + groupName);
				}
			} catch (NamingException e) {
				LOG.debug(e);
			}
			
			// get roles
			try {
				String roleAlias = "aliasedObjectName=" + userDN;
				NamingEnumeration<SearchResult> answer = getSearchContext().search(roleSearchDN,
						"(&(objectClass=alias)(" + roleAlias + "))", controls);
				while (answer.hasMoreElements()) {
					SearchResult result = (SearchResult) answer.next();
					String roleName = result.getNameInNamespace();
					roles.add((new DN(roleName)).parentDN());
					LOG.debug("User role name: " + roleName);
				}
			} catch (NamingException e) {
				LOG.debug(e);
			}
		} else {
			// member
			SearchControls controls = new SearchControls();
			controls.setTimeLimit(1000);
			controls.setCountLimit(20);
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			String attribute = getParamAttribute(identityModule);
			attribute = LdapUtils.getFilterWithValues(attribute,
					authenticationData.getUserName());
			
			// get groups
			try {
				String member = "member=" + userDN;
				String uniqueMember = "uniqueMember=" + userDN;
				NamingEnumeration<SearchResult> answer = getSearchContext().search(groupSearchDN,
						"(&(|(objectClass=groupOfUniqueNames)(objectClass=groupOfNames))(|(" + member + ")(" + uniqueMember + ")))", controls);
				while (answer.hasMoreElements()) {
					SearchResult result = (SearchResult) answer.next();
					String groupName = result.getNameInNamespace();
					groups.add(new DN(groupName));
					LOG.debug("User group name: " + groupName);
				}
			} catch (NamingException e) {
				LOG.debug(e);
			}
			
			// get roles
			try {
				String roleOccupant = "roleOccupant=" + userDN;
				NamingEnumeration<SearchResult> answer = getSearchContext().search(roleSearchDN,
						"(&(objectClass=organizationalRole)(" + roleOccupant + "))", controls);
				while (answer.hasMoreElements()) {
					SearchResult result = (SearchResult) answer.next();
					String roleName = result.getNameInNamespace();
					roles.add(new DN(roleName));
					LOG.debug("User role name: " + roleName);
				}
			} catch (NamingException e) {
				LOG.debug(e);
			}
		}
		
		CmsUser user = super.syncUserToCms(dbc, authenticationData, userDriver, userSearchResult, identityModule);
		
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
			user = syncGroupsAndRolesToCms(dbc, userDriver, user, new DN(groupSearchDN), groups, new DN(roleSearchDN), roles);
		}
		
		return user;
	}

}