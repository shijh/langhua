/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.langhua.ofbiz.mfg;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.ofbiz.base.util.Debug;

/**
 * Remembers the page a person was on when the session ran out, so that logging in brings them back
 * there instead of dropping them on the start page.
 *
 * <p>The framework answers a request for a page that needs a login with 401 and the login form, and
 * after logging in it always goes to {@code main}: a person who was half way through booking in a
 * shop order had to walk back to the receipt page by hand. The page asks for the login through
 * {@code rememberReturn}, which keeps the address in the session, and {@code main} hands it back once.
 */
public final class MfgLoginEvents {

    private static final String MODULE = MfgLoginEvents.class.getName();

    /** Where the address of the page to come back to waits while the person logs in. */
    static final String RETURN_TO = "mfgLoginReturnTo";

    /** Only pages of this component are worth coming back to. */
    private static final String COMPONENT_PATH = "/mfg/control/";

    /** What this webapp answers with data rather than a page: those are never worth coming back to. */
    private static final String[] NOT_A_PAGE = {"Json", "Csv", "orderImage", "orderPeek",
        "attachment", "thumb", "login", "logout", "rememberReturn"};

    /** Where the framework keeps the request it refused because nobody was logged in. */
    private static final String PREVIOUS_REQUEST = "_PREVIOUS_REQUEST_";

    /** Where the framework keeps the last page drawn, when the request map says save-last-view. */
    private static final String LAST_VIEW = "_LAST_VIEW_NAME_";

    private MfgLoginEvents() {
    }

    /**
     * Keeps the page the person should land on once the login form has been filled in. The page itself
     * says where it was ({@link #rememberReturn}); when the login came from an ordinary refused page
     * this takes the request the framework saved in the session instead, so the same works without
     * any script.
     *
     * @param request the request; its session holds the refused request when there was one
     * @param response the response, not written to (the page rendered after the login does the jump)
     * @return {@code success}
     */
    public static String afterLogin(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession();
        Object saved = session.getAttribute(RETURN_TO);
        // the page that asked for the login knows better than the framework does: it was an Ajax call
        // that got refused, and the address of the page is not the address of that call
        String back = safeReturnTo(saved == null ? null : String.valueOf(saved));
        if (back == null) {
            back = pageOf(session.getAttribute(PREVIOUS_REQUEST));
        }
        if (back == null) {
            // the last page drawn before the login, kept by the framework for request maps with
            // save-last-view="true" (that is what the setting is for)
            back = pageOf(session.getAttribute(LAST_VIEW));
        }
        session.removeAttribute(PREVIOUS_REQUEST);
        if (back != null) {
            session.setAttribute(RETURN_TO, back);
        } else {
            session.removeAttribute(RETURN_TO);
        }
        return "success";
    }

    /**
     * The page a request of this webapp belongs to, as far as it is a page at all: the framework
     * saves the request it refused (for example {@code /control/receipts}) and the person should
     * land there again, not on an endpoint that answers with JSON.
     *
     * @param previous what the framework saved, may be {@code null}
     * @return the address of that page, {@code null} when it is not one worth returning to
     */
    static String pageOf(Object previous) {
        if (previous == null) {
            return null;
        }
        String path = String.valueOf(previous).trim();
        String name = path.startsWith("/control/") ? path.substring("/control/".length()) : path;
        if (name.startsWith("/")) {
            return safeReturnTo("/mfg" + path);
        }
        for (String notPage : NOT_A_PAGE) {
            if (name.contains(notPage)) {
                return null;
            }
        }
        return safeReturnTo(COMPONENT_PATH + name);
    }

    /**
     * Keeps the address of the page the person was on and sends them to the login form.
     *
     * @param request the request, its {@code to} parameter is the address of that page
     * @param response the response, redirected to the login form
     * @return {@code success}
     */
    public static String rememberReturn(HttpServletRequest request, HttpServletResponse response) {
        String back = safeReturnTo(request.getParameter("to"));
        HttpSession session = request.getSession();
        if (back != null) {
            session.setAttribute(RETURN_TO, back);
        } else {
            session.removeAttribute(RETURN_TO);
        }
        try {
            response.sendRedirect(request.getContextPath() + "/control/login");
        } catch (IOException e) {
            Debug.logError(e, "Could not send the person to the login form", MODULE);
        }
        return "success";
    }

    /**
     * Sends the person back to the page they were on, once, after the login form has been filled in.
     * The start page is what everybody else gets, so a normal visit is not affected.
     *
     * @param request the request, its session may hold the address to go back to
     * @param response the response, redirected when there is one
     * @return {@code redirected} when the person was sent back, {@code success} otherwise
     */
    public static String backAfterLogin(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "success";
        }
        Object back = session.getAttribute(RETURN_TO);
        if (back == null) {
            return "success";
        }
        // once only: walking to the start page later must not send the person back again
        session.removeAttribute(RETURN_TO);
        String target = safeReturnTo(String.valueOf(back));
        if (target == null) {
            return "success";
        }
        try {
            response.sendRedirect(target);
            return "redirected";
        } catch (IOException e) {
            Debug.logError(e, "Could not send the person back to " + target, MODULE);
            return "success";
        }
    }

    /**
     * The address of a page of this component, or {@code null} for anything else: an address from the
     * request must never be able to send somebody to another site.
     *
     * @param to the address asked for
     * @return the address when it is a page of this component, {@code null} otherwise
     */
    static String safeReturnTo(String to) {
        if (to == null) {
            return null;
        }
        String text = to.trim();
        if (!text.startsWith(COMPONENT_PATH) || text.contains("//") || text.contains("\\")
                || text.contains("..") || text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
            return null;
        }
        return text;
    }
}
