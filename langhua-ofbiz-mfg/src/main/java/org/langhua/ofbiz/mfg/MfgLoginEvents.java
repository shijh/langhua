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
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.securityext.login.LoginEvents;
import org.apache.ofbiz.webapp.control.LoginWorker;

/**
 * Remembers the page a person was on when the session ran out, so that logging in brings them back
 * there instead of dropping them on the start page.
 *
 * <p>The framework answers a request for a page that needs a login with 401 and the login form, and
 * after logging in it always goes to {@code main}: a person who was half way through booking in a
 * shop order had to walk back to the receipt page by hand. The page asks for the login through
 * {@code rememberReturn}, which keeps the address in the session, and {@code main} hands it back once.
 *
 * <p>Only addresses that this webapp answers as a <em>request</em> are kept: a view name is not an
 * address (see {@code NOT_A_PAGE}).
 */
public final class MfgLoginEvents {

    private static final String MODULE = MfgLoginEvents.class.getName();

    /** Where the address of the page to come back to waits while the person logs in. */
    static final String RETURN_TO = "mfgLoginReturnTo";

    /** Only pages of this component are worth coming back to. */
    private static final String COMPONENT_PATH = "/mfg/control/";

    /** What this webapp answers with data rather than a page: those are never worth coming back to. */
    private static final String[] NOT_A_PAGE = {"Json", "Csv", "orderImage", "orderPeek",
        "attachment", "thumb", "login", "logout", "rememberReturn",
        // a view the framework renders, not a request of this webapp: coming back to it is a dead
        // URL ("Unknown request [requirePasswordChange]" right after the password gate, Step 122)
        "requirePasswordChange"};

    /** Where the framework keeps the request it refused because nobody was logged in. */
    private static final String PREVIOUS_REQUEST = "_PREVIOUS_REQUEST_";

    private MfgLoginEvents() {
    }

    /**
     * Runs after every request of the webapp (Step 125b) and writes a line when that request changed
     * a document. It never answers anything but {@code success} and never throws: a request that
     * saved a work order must not fail because the audit table hiccuped.
     *
     * @param request the finished request
     * @param response the finished response, untouched
     * @return {@code success}
     */
    public static String auditRequest(HttpServletRequest request, HttpServletResponse response) {
        MfgAudit.recordDocumentRequest(request);
        return "success";
    }

    /**
     * The framework's login, plus one line in the audit log (Step 125): who got in, who was refused
     * and why. The framework only says "success", "error" or "requirePasswordChange" - the last one
     * means the password was right but has to be changed, so it counts as a login.
     *
     * <p>Recording is guarded: an audit row that cannot be written must never keep somebody out.
     *
     * @param request the login form, {@code USERNAME} / {@code PASSWORD}
     * @param response the response, untouched here
     * @return whatever the framework answered ({@code success}, {@code error}, …)
     */
    public static String login(HttpServletRequest request, HttpServletResponse response) {
        String outcome = LoginWorker.login(request, response);
        try {
            Delegator delegator = (Delegator) request.getAttribute("delegator");
            String typed = request.getParameter("USERNAME");
            String webapp = MfgAudit.webappName(request.getContextPath());
            String remote = request.getRemoteAddr();
            if ("error".equals(outcome)) {
                Object message = request.getAttribute("_ERROR_MESSAGE_");
                MfgAudit.record(delegator, null, MfgAudit.LOGIN_FAIL, typed, remote,
                        webapp + " " + MfgAudit.failureText(message == null ? null : String.valueOf(message)));
            } else if ("success".equals(outcome) || "requirePasswordChange".equals(outcome)) {
                MfgAudit.record(delegator, typed, MfgAudit.LOGIN_OK, null, remote, webapp);
                // the password gate posts back to this same request with the new password in it
                String newPassword = request.getParameter("newPassword");
                if (newPassword != null && !newPassword.isEmpty()) {
                    MfgAudit.record(delegator, typed, MfgAudit.PASSWORD_CHANGE, typed, remote,
                            webapp + " 首次登录改密");
                }
            }
        } catch (Throwable t) {
            Debug.logError(t, "Could not write the audit row of a login", MODULE);
        }
        return outcome;
    }

    /**
     * The framework's "forgot my password", plus one line in the audit log when somebody really
     * asked for a hint (opening the page leaves no trace). This is the interesting one for security:
     * the page needs no login, so the row is the only way to answer "who looked at whose hint".
     *
     * @param request the form, {@code USERNAME} and one of the two buttons
     * @param response the response, untouched here
     * @return whatever the framework answered ({@code success}, {@code error}, {@code auth}, …)
     */
    public static String forgotPassword(HttpServletRequest request, HttpServletResponse response) {
        String outcome = LoginEvents.forgotPassword(request, response);
        try {
            boolean asked = request.getParameter("GET_PASSWORD_HINT") != null
                    || request.getParameter("GET_PASSWORD_HINT.x") != null
                    || request.getParameter("EMAIL_PASSWORD") != null
                    || request.getParameter("EMAIL_PASSWORD.x") != null;
            if (asked) {
                Delegator delegator = (Delegator) request.getAttribute("delegator");
                String what = request.getAttribute("_EVENT_MESSAGE_") != null ? " 看到提示" : " 没有提示";
                MfgAudit.record(delegator, null, MfgAudit.FORGOT_HINT, request.getParameter("USERNAME"),
                        request.getRemoteAddr(), MfgAudit.webappName(request.getContextPath()) + what);
            }
        } catch (Throwable t) {
            Debug.logError(t, "Could not write the audit row of a password hint", MODULE);
        }
        return outcome;
    }

    /**
     * Decides what happens after the login form has been filled in.
     *
     * <p>Two flows end up here:
     * <ul>
     *   <li>a page asked for the login through {@link #rememberReturn} - an Ajax call was refused, so
     *       the address of the page is nowhere in the framework's books. It is handed to {@code main},
     *       which redirects to it once;</li>
     *   <li>an ordinary page was refused (the session ran out, the person clicked a navigation entry).
     *       The framework keeps that request and redirects there itself once the login has passed, so
     *       all this has to do is leave the attribute alone - and make sure the browser never gets
     *       sent to an endpoint that answers with JSON.</li>
     * </ul>
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
        if (back != null) {
            // this one cannot be left to the framework: it would send the browser to the Ajax
            // endpoint and the person would see raw JSON
            session.removeAttribute(PREVIOUS_REQUEST);
            session.setAttribute(RETURN_TO, back);
            return "success";
        }
        // an ordinary refused page (点击导航 / 书签，会话过期): the framework keeps it in the session
        // and, once the login has passed, redirects there itself - as long as the attribute is
        // still there. Visiting the login form used to wipe it (the framework only spares the URIs
        // listed in security.properties' login.uris), which is why the person landed on the start
        // page; the instances now list "control" there (Step 176), so it survives and the redirect
        // happens without us lifting a finger.
        // Whatever is left is either a page - leave it for the framework - or an endpoint, which no
        // one wants to see: drop it.
        if (pageOf(session.getAttribute(PREVIOUS_REQUEST)) == null) {
            session.removeAttribute(PREVIOUS_REQUEST);
        }
        session.removeAttribute(RETURN_TO);
        return "success";
    }

    /**
     * The page a request of this webapp belongs to, as far as it is a page at all: the framework
     * saves the request it refused - as {@code /<request name>}, because the webapp answers under
     * {@code /mfg/control} - and the person should land there again, not on an endpoint that answers
     * with JSON.
     *
     * <p>The three shapes it can arrive in are all understood: {@code /dev} (what OFBiz saves),
     * {@code /control/dev} (an address of this webapp) and {@code dev} (a bare request name).
     *
     * @param previous what the framework saved, may be {@code null}
     * @return the address of that page, {@code null} when it is not one worth returning to
     */
    static String pageOf(Object previous) {
        if (previous == null) {
            return null;
        }
        String path = String.valueOf(previous).trim();
        String name;
        if (path.startsWith("/control/")) {
            name = path.substring("/control/".length());
        } else if (path.startsWith("/")) {
            name = path.substring(1);
        } else {
            name = path;
        }
        if (name.isEmpty() || name.indexOf('/') >= 0 || name.indexOf('?') >= 0) {
            return null;
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
        // the refused call is an endpoint (an Ajax one), so the framework's idea of "where the person
        // was" must not win over the address the page gave us
        session.removeAttribute(PREVIOUS_REQUEST);
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
