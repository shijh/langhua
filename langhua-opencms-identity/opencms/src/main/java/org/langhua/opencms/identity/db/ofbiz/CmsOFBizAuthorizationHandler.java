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

package org.langhua.opencms.identity.db.ofbiz;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.GenericValue;
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsUser;
import org.opencms.main.CmsLog;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsOrganizationalUnit;
import org.opencms.security.CmsPasswordEncryptionException;
import org.apache.commons.logging.Log;
import org.langhua.ofbiz.rmi.client.OFBizRmiClient;
import org.langhua.ofbiz.rmi.service.I_DelegatorService;
import org.langhua.opencms.identity.commons.CmsAuthenticationData;

/**
 * The OpenCms OFBiz Authentication Handler.
 * <p>
 * 
 * New login error messages are added.
 * 
 * User                          OpenCms                    OFBiz
 *  |       userid/password         |                          | 
 *  |------------req--------------->|      userid/password     |
 *  |                               |----------rmi req-------->|
 *  |                               |<---------rmi res---------|
 *  |<-----------res----------------|                          |
 * 
 * The ACL of a user is still controlled by OpenCms.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public class CmsOFBizAuthorizationHandler extends CmsOFBizAuthenticationHandler {

	/** The log object for this class. */
	public final Log LOG = CmsLog.getLog(CmsOFBizAuthorizationHandler.class);

	/**
	 * Public constructor, initializes some required member variables.
	 * <p>
	 */
	public CmsOFBizAuthorizationHandler() {

	}

	/**
	 * Sync user from OFBiz to OpenCms.
	 */
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData,
			I_CmsUserDriver userDriver, Object resultObj, CmsModule identityModule)
			throws CmsPasswordEncryptionException, CmsDataAccessException,
			CmsAuthentificationException {

		OFBizRmiClient rc = new OFBizRmiClient(getParamRmiServer(identityModule));
		Map<String, Object> context = new HashMap<String, Object>();
		context.put("entityName", "UserLoginOpencmsGroup");
		context.put("expressions", UtilMisc.toMap("userLoginId", authenticationData.getUserName()));
		context.put("useCache", Boolean.FALSE);
		Map<String, ? extends Object> result = rc.service("findList", context);
		List values = (List) result.get(I_DelegatorService.RMI_RESULTS);
		List<String> groups = new ArrayList<String>();
		List<String> ous = new ArrayList<String>();
		if (values != null) {
			for (Object value : values) {
				GenericValue gv = (GenericValue) value;
				String groupId = (String) gv.getAllFields().get("groupId");
				// the OpenCms OU group styles are /sites/default/Users and etc.
				if (groupId.startsWith(CmsOrganizationalUnit.SEPARATOR) && !groupId.endsWith(CmsOrganizationalUnit.SEPARATOR)) {
					groups.add(groupId);
					ous.add(groupId.substring(0, groupId.lastIndexOf(CmsOrganizationalUnit.SEPARATOR) + 1));
				}
			}
		}
		
		List<String> roles = new ArrayList<String>();
		context.put("entityName", "UserLoginOpencmsRole");
		result = rc.service("findList", context);
		values = (List) result.get(I_DelegatorService.RMI_RESULTS);
		if (values != null) {
			for (Object value : values) {
				GenericValue gv = (GenericValue) value;
				String roleId = (String) gv.getAllFields().get("roleId");
				// the OpenCms OU role styles are /sites/RoleDevelopers and etc.
				if (roleId.startsWith(CmsOrganizationalUnit.SEPARATOR) && !roleId.endsWith(CmsOrganizationalUnit.SEPARATOR)) {
					roles.add(roleId);
				}
			}
		}
		
		CmsUser user = null;
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
		user = super.syncUserToCms(dbc, authenticationData, userDriver, resultObj, identityModule);
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
		return user;
	}

}