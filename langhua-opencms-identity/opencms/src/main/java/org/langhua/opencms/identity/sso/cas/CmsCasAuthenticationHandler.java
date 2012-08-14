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

package org.langhua.opencms.identity.sso.cas;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import org.jasig.cas.client.validation.Assertion;
import org.jasig.cas.client.validation.TicketValidationException;
import org.opencms.db.CmsDbContext;
import org.opencms.db.I_CmsUserDriver;
import org.opencms.file.CmsDataAccessException;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsUser;
import org.opencms.main.OpenCms;
import org.opencms.module.CmsModule;
import org.opencms.security.CmsAuthentificationException;
import org.opencms.security.CmsPasswordEncryptionException;
import org.opencms.util.CmsRequestUtil;
import org.opencms.workplace.CmsLogin;

import org.langhua.opencms.identity.commons.A_CmsAuthenticationHandler;
import org.langhua.opencms.identity.commons.CmsAuthenticationData;
import org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler;

/**
 * The OpenCms CAS Authentication Handler.<p>
 * 
 * New login error messages are added.
 *  
 * Client                  OpenCms                  CAS    Auth Source
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
 *   |         the url        |                      |         |
 *   |<----------res----------|not a guest           |         |
 *
 * The ACL of a user is still controlled by OpenCms.
 *
 * @author Shi Yusen, shiys@langhua.cn
 * 
 */
public abstract class CmsCasAuthenticationHandler extends A_CmsAuthenticationHandler {

	public static final String PARAM_TICKET = "ticket";
	
	public static final String PARAM_SERVICE = "service";
	
	public static final String PARAM_RENEW = "renew";

	public static final String CMS_CAS_VALIDATE_ASSERTION = "CMS_CAS_VALIDATE_ASSERTION";

	/**
	 * 
	 * Login a user to Active Directory server.
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

		Assertion assertion = (Assertion) dbc.getRequestContext().getAttribute(CMS_CAS_VALIDATE_ASSERTION);
		LOG.debug("CAS Assertion from request: " + assertion.toString());

		// The username / password container that will be returned
		CmsAuthenticationData authenticationData = new CmsAuthenticationData();
		authenticationData.setPassword(randomString());
		authenticationData.setUserName(assertion.getPrincipal().getName());
		authenticationData.setRemoteAddress(remoteAddress);

		CmsModule identityModule = OpenCms.getModuleManager().getModule(I_CmsAuthenticationHandler.IDENTITY_MODULE_NAME);
		CmsUser user = syncUserToCms(dbc, authenticationData, userDriver, assertion, identityModule);
		if (user != null && !user.getName().equals(authenticationData.getUserName())) {
			authenticationData.setUserName(user.getName());
		}

		return authenticationData;
	}

    public static String randomString(int lo, int hi){
        int n = rand(lo, hi);
        byte b[] = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte)rand('a', 'z');
        }
        return new String(b);
    }

    private static int rand(int lo, int hi){
            java.util.Random rn = new java.util.Random();
        int n = hi - lo + 1;
        int i = rn.nextInt() % n;
        if (i < 0)
                i = -i;
        return lo + i;
    }

    public static String randomString(){
        return randomString(5, 15);
    }
    
    /**
     * @see org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler#preLogin(CmsObject)
     */
	public void preLogin(CmsLogin jsp, CmsModule identityModule) throws IOException {
		String casUrl = getParamCasUrl(identityModule);
		String loginUri = identityModule.getParameter(I_CmsAuthenticationHandler.CAS_LOGIN_URI, "/login");
		String validateUri = identityModule.getParameter(I_CmsAuthenticationHandler.CAS_VALIDATE_URI, "/serviceValidate");
		String lenientURL = identityModule.getParameter(I_CmsAuthenticationHandler.CAS_LENIENT_URL, null);
		String serviceUrl = jsp.getRequest().getRequestURL().toString();
		String url = URLEncoder.encode(serviceUrl, "UTF-8");
		String ticket = CmsRequestUtil.getNotEmptyParameter(jsp.getRequest(), PARAM_TICKET);
		if (ticket == null) {
    		jsp.getResponse().sendRedirect(casUrl + loginUri + "?" + PARAM_SERVICE + "=" + url);
    	} else {
    		// there's a ticket, we should validate the ticket
    		Assertion assertion = null;
			Cas20ServiceTicketValidator ticketValidator = new Cas20ServiceTicketValidator(
					lenientURL == null ? casUrl : lenientURL, validateUri);
			try {
				assertion = ticketValidator.validate(ticket, serviceUrl);
			} catch (TicketValidationException e) {
				LOG.debug(e);
        		jsp.getResponse().sendRedirect(casUrl + loginUri + "?service=" + url);
			}
			
			LOG.debug("CAS Assertion: " + assertion.toString());
    		if (assertion != null) {
    			jsp.getRequestContext().setAttribute(CMS_CAS_VALIDATE_ASSERTION, assertion);
    			LOG.debug("Assertion principal: " + assertion.getPrincipal().getName());
    			if (LOG.isDebugEnabled()) {
    				Map<String, String> attributes = assertion.getPrincipal().getAttributes();
    				LOG.debug("Assertion has " + attributes.size() + " attributes.");
    				Iterator<String> i = attributes.keySet().iterator();
    				while (i.hasNext()) {
    					String key = (String) i.next();
    					LOG.debug("Attribute: " + key + " - " + attributes.get(key));
    				}
    			}
				jsp.setUsername(assertion.getPrincipal().getName());
				jsp.setPassword("cas_login");
				jsp.setActionLogin("true");
    		}
    	}
	}

    /**
     * @see org.langhua.opencms.identity.commons.I_CmsAuthenticationHandler#preLogout(CmsLogin, CmsModule)
     */
	public void preLogout(CmsLogin jsp, CmsModule identityModule) throws IOException {
		String casUrl = getParamCasUrl(identityModule);
		String logoutUri = identityModule.getParameter(I_CmsAuthenticationHandler.CAS_LOGOUT_URI, "/logout");
        if (LOG.isErrorEnabled()) {
            LOG.info(casUrl + logoutUri);
        }
		jsp.getResponse().sendRedirect(casUrl + logoutUri);
	}

	/**
	 * @param identityModule
	 * @return
	 */
	protected String getParamCasUrl(CmsModule identityModule) {
		return identityModule.getParameter(I_CmsAuthenticationHandler.CAS_URL, "https://localhost:8443/cas");
	}
	
}