/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.langhua.ofbiz.axis2.webapp;

import org.apache.axis2.Constants;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.transport.http.AxisServlet;
import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilTimer;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericDelegator;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.security.Security;
import org.ofbiz.security.authz.Authorization;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.webapp.control.RequestHandler;
import org.ofbiz.webapp.stats.VisitHandler;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 *
 */
public class AxisOFBizAdminServlet extends AxisServlet {
    private static final long serialVersionUID = -1L;
    
    protected transient OFBizAdminAgent agent;
    
    public final static String module = AxisOFBizAdminServlet.class.getName();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        doGet(req, res);
    }

    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp) throws ServletException, IOException {
        try {
            req.getSession().setAttribute(Constants.SERVICE_PATH, configContext.getServicePath());
            ofbizServletProcess(req, resp);
            agent.handle(req, resp);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        ServletContext servletContext = config.getServletContext();
        this.configContext =
                (ConfigurationContext) servletContext.getAttribute(CONFIGURATION_CONTEXT);
        servletContext.setAttribute(this.getClass().getName(), this);
        agent = new OFBizAdminAgent(configContext);
        this.servletConfig = config;
    }

    @Override
    public void init() throws ServletException {
        if (this.servletConfig != null) {
            init(this.servletConfig);
        }
    }
    
    protected void ofbizServletProcess(HttpServletRequest request, HttpServletResponse response) {
        long requestStartTime = System.currentTimeMillis();
        RequestHandler requestHandler = RequestHandler.getRequestHandler(getServletContext());
        HttpSession session = request.getSession();

        // setup DEFAULT chararcter encoding and content type, this will be overridden in the RequestHandler for view rendering
        String charset = getServletContext().getInitParameter("charset");
        if (UtilValidate.isEmpty(charset)) charset = request.getCharacterEncoding();
        if (UtilValidate.isEmpty(charset)) charset = "UTF-8";
        if (Debug.verboseOn()) Debug.logVerbose("The character encoding of the request is: [" + request.getCharacterEncoding() + "]. The character encoding we will use for the request and response is: [" + charset + "]", module);

        if (!"none".equals(charset)) {
            try {
				request.setCharacterEncoding(charset);
			} catch (UnsupportedEncodingException e) {
		        if (Debug.verboseOn()) Debug.logVerbose("Failed to set the encoding of the request to [" + request.getCharacterEncoding() + "].", module);
			}
        }

        // setup content type
        String contentType = "text/html";
        if (charset.length() > 0 && !"none".equals(charset)) {
            response.setContentType(contentType + "; charset=" + charset);
            response.setCharacterEncoding(charset);
        } else {
            response.setContentType(contentType);
        }

        GenericValue userLogin = (GenericValue) session.getAttribute("userLogin");
        //Debug.log("Cert Chain: " + request.getAttribute("javax.servlet.request.X509Certificate"), module);

        // set the Entity Engine user info if we have a userLogin
        if (userLogin != null) {
            GenericDelegator.pushUserIdentifier(userLogin.getString("userLoginId"));
        }

        // workaraound if we are in the root webapp
        String webappName = UtilHttp.getApplicationName(request);

        String rname = "";
        if (request.getPathInfo() != null) {
            rname = request.getPathInfo().substring(1);
        }
        if (rname.indexOf('/') > 0) {
            rname = rname.substring(0, rname.indexOf('/'));
        }

        UtilTimer timer = null;
        if (Debug.timingOn()) {
            timer = new UtilTimer();
            timer.setLog(true);
            timer.timerString("[" + rname + "(Domain:" + request.getScheme() + "://" + request.getServerName() + ")] Request Begun, encoding=[" + charset + "]", module);
        }

        // Setup the CONTROL_PATH for JSP dispatching.
        String contextPath = request.getContextPath();
        if (contextPath == null || "/".equals(contextPath)) {
            contextPath = "";
        }
        request.setAttribute("_CONTROL_PATH_", contextPath + request.getServletPath());
        if (Debug.verboseOn())
            Debug.logVerbose("Control Path: " + request.getAttribute("_CONTROL_PATH_"), module);

        // for convenience, and necessity with event handlers, make security and delegator available in the request:
        // try to get it from the session first so that we can have a delegator/dispatcher/security for a certain user if desired
        Delegator delegator = null;
        String delegatorName = (String) session.getAttribute("delegatorName");
        if (UtilValidate.isNotEmpty(delegatorName)) {
            delegator = DelegatorFactory.getDelegator(delegatorName);
        }
        if (delegator == null) {
            delegator = (Delegator) getServletContext().getAttribute("delegator");
        }
        if (delegator == null) {
            Debug.logError("[ControlServlet] ERROR: delegator not found in ServletContext", module);
        } else {
            request.setAttribute("delegator", delegator);
            // always put this in the session too so that session events can use the delegator
            session.setAttribute("delegatorName", delegator.getDelegatorName());
        }

        LocalDispatcher dispatcher = (LocalDispatcher) session.getAttribute("dispatcher");
        if (dispatcher == null) {
            dispatcher = (LocalDispatcher) getServletContext().getAttribute("dispatcher");
        }
        if (dispatcher == null) {
            Debug.logError("[ControlServlet] ERROR: dispatcher not found in ServletContext", module);
        }
        request.setAttribute("dispatcher", dispatcher);

        Authorization authz = (Authorization) session.getAttribute("authz");
        if (authz == null) {
            authz = (Authorization) getServletContext().getAttribute("authz");
        }
        if (authz == null) {
            Debug.logError("[ControlServlet] ERROR: authorization not found in ServletContext", module);
        }
        request.setAttribute("authz", authz); // maybe we should also add the value to 'security'

        Security security = (Security) session.getAttribute("security");
        if (security == null) {
            security = (Security) getServletContext().getAttribute("security");
        }
        if (security == null) {
            Debug.logError("[ControlServlet] ERROR: security not found in ServletContext", module);
        }
        request.setAttribute("security", security);

        request.setAttribute("_REQUEST_HANDLER_", requestHandler);
        
        // setup some things that should always be there
        UtilHttp.setInitialRequestInfo(request);
        VisitHandler.getVisitor(request, response);

        // set the Entity Engine user info if we have a userLogin
        String visitId = VisitHandler.getVisitId(session);
        if (UtilValidate.isNotEmpty(visitId)) {
            GenericDelegator.pushSessionIdentifier(visitId);
        }
    }
}
