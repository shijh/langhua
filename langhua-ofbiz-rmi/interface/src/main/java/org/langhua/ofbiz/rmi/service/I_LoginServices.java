/*
 * This library is part of OFBiz-RMI Component of Langhua
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
 * For the latest version about this component, please see the
 * project website: http://langhua.org/opensource/ofbiz/rmi/
 * 
 * For more information on Apache OFBiz, please see the
 * project website: http://ofbiz.apache.org/
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.langhua.ofbiz.rmi.service;

import java.util.Map;

import org.ofbiz.service.DispatchContext;

/**
 * Interface of RMI Login Services which will be an counterpart of
 * org.ofbiz.common.login.LoginServices.
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public interface I_LoginServices {

	/**
	 * Check a UserLogin's new password. This method calls
	 * {@link org.ofbiz.common.login.LoginServices#checkNewPassword(org.ofbiz.entity.GenericValue, String, String, String, String, java.util.List, boolean, java.util.Locale)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.currentPassword String
     * @param context.newPassword String
     * @param context.newPasswordVerify String
     * @param context.passwordHint String
     * @param context.errorMessageList List
     * @param context.ignoreCurrentPassword Boolean
     * @param context.locale Locale
     * 
	 * @return the followings in return Map:
	 *  I_DelegatorService.RMI_RESULTS: List of error message String
	 */
	public Map<String, Object> checkNewPassword(DispatchContext dctx,
			Map<String, ?> context);

	/**
	 * Login service to authenticate username and password. This method simply
	 * calls
	 * {@link org.ofbiz.common.login.LoginServices#userLogin(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.isServiceAuth Boolean
     * @param context.login.username String
     * @param context.username String
     * @param context.login.password String
     * @param context.password String
     * @param context.visitId String
     * 
	 * @return the followings in return Map:
	 *  userLogin: GenericValue if success
	 *  ModelService.RESPONSE_MESSAGE: ModelService.RESPOND_ERROR if error found
	 *  ModelService.ERROR_MESSAGE: if error found
	 */
	public Map<String, Object> userLogin(DispatchContext ctx,
			Map<String, ?> context);

	/**
	 * Creates a UserLogin. This method simply calls
	 * {@link org.ofbiz.common.login.LoginServices#createUserLogin(DispatchContext, Map)}
	 * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.userLoginId String
     * @param context.partyId String
     * @param context.currentPassword String
     * @param context.currentPasswordVerify String
     * @param context.enabled Boolean
     * @param context.requirePasswordChange String
     * 
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: if success
	 *  ModelService.RESPOND_ERROR: if error found
	 */
	public Map<String, Object> createUserLogin(DispatchContext ctx, Map<String, ?> context);
	
    /** 
     * Updates UserLogin Password info. This method simply calls
	 * {@link org.ofbiz.common.login.LoginServices#updatePassword(DispatchContext, Map)}
     * 
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.userLoginId String
     * @param context.currentPassword String
     * @param context.newPassword String
     * @param context.newPasswordVerify String
     * @param context.passwordHint String
     * 
	 * @return the followings in return Map:
	 *  updatedUserLogin: if success
	 *  ModelService.RESPONSE_MESSAGE: if success
	 *  ModelService.RESPOND_ERROR: if error found
     */
    public Map<String, Object> updatePassword(DispatchContext ctx, Map<String, ?> context);
    
    /**
     * Updates the UserLoginId for a party, replicating password, etc from
     *    current login and expiring the old login. This method simply calls
	 * {@link org.ofbiz.common.login.LoginServices#updateUserLoginId(DispatchContext, Map)}
	 *  
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.userLoginId String
     * 
	 * @return the followings in return Map:
	 *  newUserLogin: if success
	 *  ModelService.RESPONSE_MESSAGE: if success
	 *  ModelService.RESPOND_ERROR: if error found
     */
    public Map<String, Object> updateUserLoginId(DispatchContext ctx, Map<String, ?> context);
    
    /** 
     * Updates UserLogin Security info. This method simply calls
	 * {@link org.ofbiz.common.login.LoginServices#updateUserLoginSecurity(DispatchContext, Map)}
	 *  
     * @param dctx DispatchContext
     * @param context Map
     * @param context.userLogin GenericValue
     * @param context.locale Locale
     * @param context.userLoginId String
     * @param context.enabled String
     * @param context.disabledDateTime String
     * @param context.successiveFailedLogins String
     * @param context.userLdapDn String
     * 
	 * @return the followings in return Map:
	 *  ModelService.RESPONSE_MESSAGE: if success
	 *  ModelService.RESPOND_ERROR: if error found
     */
    public Map<String, Object> updateUserLoginSecurity(DispatchContext ctx, Map<String, ?> context);
}
