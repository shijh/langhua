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

import java.util.Hashtable;

import javax.naming.Context;

import org.langhua.opencms.identity.ldap.CmsLdapAuthenticationHandler;
import org.opencms.module.CmsModule;

/**
 * The OpenCms LDAP Authentication Handler.<p>
 * 
 * New login error messages are added.
 *  
 * User                          OpenCms                      LDAP
 *  |        userid/password        |                          |
 *  |------------req--------------->|           userid         |
 *  |                               |------------req---------->|
 *  |                               |<-----------res-----------|
 *  |                               |           user dn        |
 *  |                               |    user dn/password      |
 *  |                               |------------req---------->|
 *  |                               |<-----------res-----------|
 *  |<-----------res----------------|                          |
 *
 * The ACL of a user is still controlled by OpenCms.
 *
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsOpenLDAPAuthenticationHandler extends CmsLdapAuthenticationHandler {

    /**
     * Public constructor, initializes some required member variables.<p> 
     */
    public CmsOpenLDAPAuthenticationHandler() {
    	
    }

	/**
	 * @param identityModule
	 * @param env
	 */
	protected void setUpSecurityCredentials(CmsModule identityModule, Hashtable<String, String> env) {
		env.put(Context.SECURITY_AUTHENTICATION, "none");
	}

}