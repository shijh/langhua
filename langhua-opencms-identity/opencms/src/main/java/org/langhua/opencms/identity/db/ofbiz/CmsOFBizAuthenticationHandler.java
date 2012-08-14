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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javolution.util.FastMap;

import org.apache.commons.logging.Log;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.ModelService;
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
import org.opencms.util.CmsUUID;
import org.langhua.ofbiz.rmi.client.OFBizRmiClient;
import org.langhua.ofbiz.rmi.service.I_DelegatorService;
import org.langhua.opencms.identity.Messages;
import org.langhua.opencms.identity.commons.A_CmsAuthenticationHandler;
import org.langhua.opencms.identity.commons.CmsAuthenticationData;
import org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler;

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
public class CmsOFBizAuthenticationHandler extends A_CmsAuthenticationHandler {

	/** The log object for this class. */
	private static final Log LOG = CmsLog
			.getLog(CmsOFBizAuthenticationHandler.class);

	public static final String USE_CMSLOGIN_WHEN_OFBIZ_FAIL = "UseCmsLoginWhenOFBizFail";

	public static final String RMI_SERVER = "RmiServer";

	/**
	 * Public constructor, initializes some required member variables.
	 * <p>
	 */
	public CmsOFBizAuthenticationHandler() {

	}

	/**
	 * Login the user to OFBiz by rmi.
	 * 
	 */
	public CmsAuthenticationData loginUser(CmsDbContext dbc, String userName,
			String password, String remoteAddress, I_CmsUserDriver userDriver)
			throws CmsPasswordEncryptionException, CmsDataAccessException,
			CmsAuthentificationException {
		
		// The username / password container that will be returned
		CmsAuthenticationData authenticationData = new CmsAuthenticationData();
		authenticationData.setPassword(initPassword(password));
		authenticationData.setUserName(userName);
		authenticationData.setRemoteAddress(remoteAddress);

		boolean logedIn = false;
		Map<String, ? extends Object> result = FastMap.newInstance();
		CmsModule identityModule = OpenCms.getModuleManager().getModule(
				I_CmsAuthenticationHandler.IDENTITY_MODULE_NAME);
		if (identityModule != null) {
			OFBizRmiClient rc = new OFBizRmiClient(getParamRmiServer(identityModule));
			Map<String, Object> context = new HashMap<String, Object>();
			context.put("login.username", userName);
			context.put("login.password", password);
			context.put("isServiceAuth", Boolean.TRUE);
			result = rc.service("userLogin", context);
			if (result.containsKey(ModelService.RESPONSE_MESSAGE)) {
				String response = (String) result.get(ModelService.RESPONSE_MESSAGE);
				if (response.equals(ModelService.RESPOND_SUCCESS)) {
					logedIn = true;
					context.clear();
					
					context.put("entityName", "UserLoginAndPartyDetails");
					context.put("lookupFields", UtilMisc.toMap("partyId", ((GenericValue) result.get("userLogin")).getAllFields().get("partyId"), "userLoginId", userName));
					context.put("useCache", Boolean.TRUE);
					
					result = rc.service("findOne", context);
				}
			}
		}
		if (logedIn) {
			CmsUser user = syncUserToCms(dbc, authenticationData, userDriver, result, identityModule);
			if (!user.getName().equals(authenticationData.getUserName())) {
				authenticationData.setUserName(user.getName());
			}
		} else {
			// cannot login, throw a login exception or continue the login by OpenCms itself
			boolean UseCmsLoginWhenOFBizFail = getParamUseCmsLoginWhenOFBizFail(identityModule);
			if (!UseCmsLoginWhenOFBizFail) {
				throw new CmsAuthentificationException(Messages.get().container(
							Messages.ERR_OFBIZ_LOGIN_FAILED_2,
							authenticationData.getUserName(),
							remoteAddress), new Exception((String) result.get(ModelService.ERROR_MESSAGE)));
			}
		}

		return authenticationData;
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamRmiServer(CmsModule identityModule) {
		return identityModule.getParameter(RMI_SERVER,
				"rmi://127.0.0.1:1099/RMIDispatcher");
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected boolean getParamUseCmsLoginWhenOFBizFail(CmsModule identityModule) {
		return Boolean.valueOf(
				identityModule
						.getParameter(USE_CMSLOGIN_WHEN_OFBIZ_FAIL, "true"))
				.booleanValue();
	}

	/**
	 * Sync user from OFBiz to OpenCms.
	 */
	public CmsUser syncUserToCms(CmsDbContext dbc,
			CmsAuthenticationData authenticationData,
			I_CmsUserDriver userDriver, Object result, CmsModule identityModule)
			throws CmsPasswordEncryptionException, CmsDataAccessException,
			CmsAuthentificationException {

		boolean sync = Boolean.valueOf(OpenCms.getModuleManager().getModule(I_CmsAuthenticationHandler.IDENTITY_MODULE_NAME).getParameter(I_CmsAuthenticationHandler.SYNC_USER, "true")).booleanValue();
		if (!sync) return null;
		
		authenticationData.setPassword(postProcessPassword(authenticationData.getPassword()));
		String cmsDigestPassword = OpenCms.getPasswordHandler().digest(authenticationData.getPassword());
		CmsUser user = null;
		try {
			user = userDriver.readUser(dbc, authenticationData
					.getUserName());
		} catch (CmsDbEntryNotFoundException e) {
			LOG.debug(e.getLocalizedMessage(), e);
		}
		
		if (user == null) {
			// no such a user in OpenCms, add it
			String lastName = authenticationData.getUserName();
			String firstName = "";
			if (result instanceof Map) {
				Map resultMap = (Map) result;
				Object findValue = resultMap.get(I_DelegatorService.RMI_RESULTS);
				if (findValue != null && findValue instanceof GenericValue) {
					Map values = ((GenericValue) findValue).getAllFields();
					lastName = (String) values.get("lastName");
					firstName = (String) values.get("firstName");
				}
			}

			user = userDriver.createUser(dbc, (new CmsUUID()),
					authenticationData.getUserName(), cmsDigestPassword,
					firstName, lastName, null, 0, 0, 0, new HashMap<Object, Object>());
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
		
		return user;
	}

}