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

package org.langhua.opencms.identity.ldap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Hashtable;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.commons.logging.Log;
import org.jasig.cas.util.LdapUtils;
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
import org.langhua.opencms.identity.Messages;
import org.langhua.opencms.identity.commons.A_CmsAuthenticationHandler;
import org.langhua.opencms.identity.commons.CmsAuthenticationData;
import org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler;

/**
 * The abstract Authentication Handler.
 * 
 * The ACL of a user is still controlled by OpenCms.
 * <p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public abstract class CmsLdapAuthenticationHandler extends A_CmsAuthenticationHandler {

	/** The log object for this class. */
	public final Log LOG = CmsLog.getLog(CmsLdapAuthenticationHandler.class);
	
	public static final String LDAP_ATTRIBUTE = "Attribute";

	public static final String USE_CMSLOGIN_WHEN_LDAP_FAIL = "UseCmsLoginWhenLDAPFail";

	public static final String LDAP_GROUP_SEARCH_DN = "GroupSearchDN";

	public static final String LDAP_ROLE_SEARCH_DN = "RoleSearchDN";

	/** LDAP config parameters */
	public static final String LDAP_URL = "LdapURL";
	
	public static final String LDAP_BASEDN = "LdapBaseDN";

	public static final String LDAP_AUTHN_TYPE = "LdapAuthnType";

	public static final String LDAP_SCOPE = "LdapScope";

	public static final String LDAP_FILTER = "LdapFilter";

	public static final String LDAP_USERNAME_ATTRIBUTE = "LdapUserNameAttribute";

	public static final String LDAP_USER_PASSWORD = "LdapUserPassword";

	public static final String LDAP_AUTHN_USER_DN = "LdapAuthnUserDN";
	
	public static final String LDAP_SEARCH_TYPE = "SearchType";
	
	public static final String LDAP_USERDN_FOR_SEARCH = "UserDNForSearch";
	
	public static final String LDAP_PASSWORD_FOR_SEARCH = "PasswordForSearch";
	
	public static final String LDAP_AUTHZ_TYPE = "AuthzType";
	
	public static final String LDAP_DEREF_ALIASES = "DerefAliases";
	
	private DirContext m_searchContext = null;

	/**
	 * Get search context.
	 * 
	 * @return m_searchContext
	 */
	public DirContext getSearchContext() {
		return m_searchContext;
	}
	
	/**
	 * Set search context.
	 */
	public void setSearchContext(DirContext context) {
		m_searchContext = context;
	}

	/**
	 * 
	 * Login a user to an LDAP server.
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
	 * @return updated username and password if the user can login successfully; otherwise, both values will be null.
	 */
	public CmsAuthenticationData loginUser(CmsDbContext dbc, String userName,
			String password, String remoteAddress, I_CmsUserDriver userDriver)
			throws CmsPasswordEncryptionException, CmsDataAccessException,
			CmsAuthentificationException {

		Attributes attributes = null;

		// The username / password container that will be returned
		CmsAuthenticationData authenticationData = new CmsAuthenticationData();
		authenticationData.setPassword(initPassword(password));
		authenticationData.setUserName(userName);
		authenticationData.setRemoteAddress(remoteAddress);

		boolean ldapLogedIn = false;
		DirContext authenContext = null;
		SearchResult result = null;
		CmsModule identityModule = OpenCms.getModuleManager().getModule(
				I_CmsAuthenticationHandler.IDENTITY_MODULE_NAME);
		if (identityModule != null) {
			String ldapURL = getParamUrl(identityModule);
			String baseDN = getParamBaseDN(identityModule);
			Hashtable<String, String> env = new Hashtable<String, String>();
			String authenUserDN = getParamAuthenUserDN(identityModule);
			String authenUserPassword = getParamAuthenUserPassword(identityModule);
			env.put(Context.INITIAL_CONTEXT_FACTORY,
					"com.sun.jndi.ldap.LdapCtxFactory");
			env.put(Context.PROVIDER_URL, ldapURL);
			if (CmsStringUtil.isNotEmpty(authenUserDN) && CmsStringUtil.isNotEmpty(authenUserPassword)) {
				env.put(Context.SECURITY_PRINCIPAL, authenUserDN);
				env.put(Context.SECURITY_CREDENTIALS, authenUserPassword);
			} else {
				setUpSecurityCredentials(identityModule, env);
			}
			try {
				m_searchContext = new InitialDirContext(env);
				SearchControls controls = new SearchControls();
				// ldap search timeout
				controls.setTimeLimit(1000);
				// ldap search count
				controls.setCountLimit(2);
				// ldap search scope
				String sub = getParamScope(identityModule).toLowerCase().trim();
				if (sub.equals("sub")) {
					controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
				} else if (sub.equals("one")) {
					controls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
				} else {
					controls.setSearchScope(SearchControls.OBJECT_SCOPE);
				}
				String filter = getParamFilter(identityModule);
				String attribute = getParamAttribute(identityModule);
				attribute = LdapUtils.getFilterWithValues(attribute,
						authenticationData.getUserName());
				NamingEnumeration<SearchResult> answer = m_searchContext.search(baseDN,
				// Filter expression
						"(&(" + filter + ") (" + attribute + "))", controls);
				if (answer.hasMoreElements()) {
					result = (SearchResult) answer.next();
					authenContext = checkLogin(m_searchContext, authenticationData.getPassword(),
							result, identityModule, baseDN, env);
					attributes = result.getAttributes();

					// Now update the login name based on what was found in the directory
					// (this is usefull in particular if the directory is not case sensitive, which
					// OpenCms is)
					String userNameAttribute = getParamUserNameAttribute(identityModule);
					if (CmsStringUtil.isNotEmpty(userNameAttribute)) {
						authenticationData.setUserName(getAttributeString(
								attributes, userNameAttribute));
					}

					ldapLogedIn = true;
				}
			} catch (NamingException e) {
				// No ldap service found, or cannot login.
				if (LOG.isErrorEnabled()) {
					LOG.error(e.getLocalizedMessage(), e);
				}
			}
		}
		if (ldapLogedIn) {
			CmsUser user = syncUserToCms(dbc, authenticationData, userDriver, result, identityModule);
			if (!user.getName().equals(authenticationData.getUserName())) {
				authenticationData.setUserName(user.getName());
			}
		} else {
			// no ldap login in, throw a login exception or continue the login by OpenCms itself
			boolean UseCmsLoginWhenLDAPFail = getParamUseCmsLoginWhenLDAPFail(identityModule);
			if (!UseCmsLoginWhenLDAPFail) {
				if (authenContext == null) {
					throw new CmsAuthentificationException(Messages.get().container(
							Messages.ERR_LDAP_CONNECTION_FAILED_0),
							null);
				}
				throw new CmsAuthentificationException(Messages.get().container(
							Messages.ERR_LDAP_LOGIN_FAILED_2,
							authenticationData.getUserName(),
							remoteAddress), null);
			}
		}

		return authenticationData;
	}

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
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData, I_CmsUserDriver userDriver, Object result, CmsModule identityModule) throws CmsPasswordEncryptionException, CmsDataAccessException, CmsAuthentificationException {
		// check if user info in OpenCms should be synchronized with the identity source
		// when OpenCms is the backend of CAS, use CAS sync instead
		boolean sync = Boolean.valueOf(OpenCms.getModuleManager().getModule(I_CmsAuthenticationHandler.IDENTITY_MODULE_NAME).getParameter(I_CmsAuthenticationHandler.SYNC_USER, "true")).booleanValue();
		if (!sync || !(result instanceof SearchResult)) return null;
		
		// check if the password is the same effective. if not, substitute the password in opencms with the one passed ldap login
		// get the digest password string for OpenCms
		String cmsDigestType = OpenCms.getPasswordHandler().getDigestType();
		String cmsDigestEncoding = OpenCms.getPasswordHandler()
				.getInputEncoding();
		authenticationData
				.setPassword(postProcessPassword(authenticationData
						.getPassword()));
		String cmsDigestPassword = OpenCms.getPasswordHandler().digest(
				authenticationData.getPassword(), cmsDigestType,
				cmsDigestEncoding);
		CmsUser user = null;
		try {
			user = userDriver.readUser(dbc, authenticationData
					.getUserName());
		} catch (CmsDbEntryNotFoundException e) {
			LOG.debug(e.getLocalizedMessage(), e);
		}
		if (user == null) {
			// no such a user in OpenCms, add it
			Attributes attributes = ((SearchResult) result).getAttributes();
			String lastName = getAttributeString(attributes, "sn");
			String firstName = getAttributeString(attributes, "givenName");
			String email = getAttributeString(attributes, "mail");

			user = userDriver.createUser(dbc, (new CmsUUID()),
					authenticationData.getUserName(), cmsDigestPassword,
					firstName, lastName, email, 0, 0, 0, new HashMap<Object, Object>());
			if (user != null) {
				assignUserInGroups(userDriver, dbc, user);
				assignUserInRoles(userDriver, dbc, user, identityModule);
			}

		} else if (!cmsDigestPassword.equals(user.getPassword())) {
			// passwords are not the same, change the one in OpenCms
			Connection conn = null;
			PreparedStatement stmt = null;
			try {
				conn = userDriver.getSqlManager().getConnection(dbc);
				stmt = userDriver.getSqlManager().getPreparedStatement(
						conn, "C_USERS_SET_PWD_3");
				stmt.setString(1, cmsDigestPassword);
				stmt.setString(2, authenticationData.getUserName());
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
		
		return user;
	}

	/**
	 * @param attributes
	 * @param strAttrName
	 * @return
	 */
	protected String getAttributeString(Attributes attributes,
			String strAttrName) {
		Attribute attribute = attributes.get(strAttrName);
		String strValue = null;

		try {
			if (attribute != null) {
				strValue = attribute.get().toString();
			}
		} catch (NamingException e) {
			if (LOG.isErrorEnabled()) {
				LOG.error(e.getLocalizedMessage(), e);
			}
		}
		return strValue;
	}

	/**
	 * @param identityModule
	 * @param env
	 */
	protected abstract void setUpSecurityCredentials(CmsModule identityModule, Hashtable<String, String> env);
	
	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamUrl(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_URL, "ldap://127.0.0.1:389");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamBaseDN(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_BASEDN);
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamAuthenType(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_AUTHN_TYPE, "simple");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamScope(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_SCOPE, "sub");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamFilter(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_FILTER, "(objectclass=*)");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamAttribute(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_ATTRIBUTE, "uid=%u");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected boolean getParamUseCmsLoginWhenLDAPFail(CmsModule identityModule) {
		return Boolean.valueOf(
				identityModule.getParameter(USE_CMSLOGIN_WHEN_LDAP_FAIL, "true"))
				.booleanValue();
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamUserNameAttribute(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_USERNAME_ATTRIBUTE, null);
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamAuthenUserPassword(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_USER_PASSWORD, null);
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamAuthenUserDN(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_AUTHN_USER_DN, null);
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamGroupSearchDN(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_GROUP_SEARCH_DN, getParamBaseDN(identityModule));
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamRoleSearchDN(CmsModule identityModule) {
		return identityModule.getParameter(LDAP_ROLE_SEARCH_DN, getParamBaseDN(identityModule));
	}

	/**
	 * @param searchContext 
	 * @param ctx
	 * @param password
	 * @param result
	 * @param identityModule
	 * @param baseDN
	 * @param env
	 * @return
	 * @throws NamingException
	 */
	public DirContext checkLogin(DirContext searchContext, String password,
			SearchResult result, CmsModule identityModule, String baseDN,
			Hashtable<String, String> env) throws NamingException {
		DirContext ctx = null;
		String authenType = getParamAuthenType(identityModule);
		env.put(Context.SECURITY_AUTHENTICATION, authenType);
		// specify the username
		String userDN = result.getName();
		if (result.isRelative()) {
			userDN += "," + baseDN;
		}
		env.put(Context.SECURITY_PRINCIPAL, userDN);
		// specify the password
		env.put(Context.SECURITY_CREDENTIALS, password);
		ctx = new InitialDirContext(env);
		return ctx;
	}

}