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
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsUser;
import org.opencms.i18n.CmsMessageContainer;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsPasswordEncryptionException;
import org.opencms.workplace.CmsLogin;

/**
 * The OpenCms LDAP Authentication Handler interface.<p>
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public interface I_CmsAuthenticationHandler {
	
	public static final String IDENTITY_MODULE_NAME = "org.langhua.opencms.identity";

	/** common module parameters */
	public static final String SYNC_USER = "syncUser";

	public static final String AUTH_HANDLER = "AuthHandler";

	public static final String AUTO_USER_ROLENAME = "AutoUserRoleName";

	/** CAS config parameters */
	public static final String CAS_LOGIN_URI = "CasLoginUri";
	
	public static final String CAS_VALIDATE_URI = "CasValidateUri";
	
	public static final String CAS_LENIENT_URL = "CasLenientURL";
	
	public static final String CAS_LOGOUT_URI = "CasLogoutUri";
	
	public static final String CAS_URL = "CasUrl";

	/**
     * Authenticate a user.
     * 
     * @throws CmsPasswordEncryptionException 
     * @throws CmsDataAccessException 
     * @throws CmsAuthentificationException
     *  
     * @return CmsAuthenticationData if the user can login successfully; otherwise, null.
     */
    CmsAuthenticationData loginUser(CmsDbContext dbc, String userName, String password, String remoteAddress, I_CmsUserDriver userDriver) throws CmsAuthentificationException, CmsDataAccessException, CmsPasswordEncryptionException;
    
    /**
     * Check a user login before OpenCms login, i.e. confirm current user's CAS ticket.<p>
     * 
     * @param jsp the CmsLogin
     * @param identityModule the module object
     * 
     * @throws IOException in case a redirect fails
     */
	void preLogin(CmsLogin jsp, CmsModule identityModule) throws IOException;
    
    /**
     * Log a user out before running OpenCms logout, i.e. destroy the current user's CAS ticket.<p>
     * 
     * @param jsp the CmsLogin object
     * @param identityModule the CmsModule object of ldap module
     * 
     * @throws IOException if redirect after logout fails
     */
    void preLogout(CmsLogin jsp, CmsModule identityModule) throws IOException;
    
    /**
     * Synchronize the user from LDAP to OpenCms database.
     * 
     * @param dbc
     * @param authenticationData
     * @param userDriver
     * @param result
     * @param identityModule
     * @throws CmsPasswordEncryptionException
     * @throws CmsDataAccessException
     * @throws CmsAuthentificationException
     */
    CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData, I_CmsUserDriver userDriver, Object result, CmsModule identityModule) throws CmsPasswordEncryptionException, CmsDataAccessException, CmsAuthentificationException;

    /**
     * The error message during handler login. 
     * 
     * @param cms the CmsObject
     * @param username 
     * @param key 
     * @return message
     */
	CmsMessageContainer getMessage(CmsObject cms, String username, String key);
	
}