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

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

/**
 * RMI Login Services
 * 
 * @author Shi Yusen, shiys@langhua.cn
 */
public class LoginServices implements I_LoginServices {

	public static final String module = LoginServices.class.getName();

    /**
     *  Empty constructor
     */
    public LoginServices() {
    }
    
    /**
     *  Static method to get LoginServices instance
     *  This method name is defined in java.lang.Class.
     */
    public static LoginServices newInstance() {
    	return new LoginServices();
    }

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#checkNewPassword(DispatchContext, Map)
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> checkNewPassword(DispatchContext dctx, Map<String, ?> context) {

		GenericValue userLogin = (GenericValue) context.get("userLogin");
		String currentPassword = (String) context.get("currentPassword");
		String newPassword = (String) context.get("newPassword");
		String newPasswordVerify = (String) context.get("newPasswordVerify");
		String passwordHint = (String) context.get("passwordHint");
		List<String> errorMessageList = (List<String>) context.get("errorMessageList");
		Boolean ignoreCurrentPassword = (Boolean) context
				.get("ignoreCurrentPassword");
		Locale locale = (Locale) context.get("locale");

		org.ofbiz.common.login.LoginServices.checkNewPassword(userLogin,
				currentPassword, newPassword, newPasswordVerify, passwordHint,
				errorMessageList, ignoreCurrentPassword.booleanValue(), locale);

		Map<String, Object> result = ServiceUtil.returnSuccess();
		result.put(I_DelegatorService.RMI_RESULTS, errorMessageList);
		return result;
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#userLogin(DispatchContext, Map)
	 */
	public Map<String, Object> userLogin(DispatchContext ctx, Map<String, ?> context) {
		return org.ofbiz.common.login.LoginServices.userLogin(ctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#createUserLogin(DispatchContext, Map)
	 */
	public Map<String, Object> createUserLogin(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.common.login.LoginServices.createUserLogin(ctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#updatePassword(DispatchContext, Map)
	 */
	public Map<String, Object> updatePassword(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.common.login.LoginServices.updatePassword(ctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#updateUserLoginId(DispatchContext, Map)
	 */
	public Map<String, Object> updateUserLoginId(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.common.login.LoginServices.updateUserLoginId(ctx, context);
	}

	/**
	 * @see org.langhua.ofbiz.rmi.service.I_LoginServices#updateUserLoginSecurity(DispatchContext, Map)
	 */
	public Map<String, Object> updateUserLoginSecurity(DispatchContext ctx,
			Map<String, ?> context) {
		return org.ofbiz.common.login.LoginServices.updateUserLoginSecurity(ctx, context);
	}

}
