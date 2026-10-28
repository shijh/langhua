/*
 * This library is part of OFBiz-MFG component of Langhua
 *
 * Copyright (C) 2010  Langhua Opensource Foundation (http://langhua.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 */

package org.langhua.ofbiz.mfg;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * The helpers behind the login page and the audit log: which page a refused request came from, how
 * the actions are named in Chinese, and how the rows are numbered and cleaned up.
 */
public class MfgAuditRulesTest {

    @Test
    public void onlyAPageOfThisComponentIsWorthComingBackTo() {
        // the page a person was on is kept across the login, and then they land right back there
        assertEquals("/mfg/control/receipts?search=PR-260101-01",
                MfgLoginEvents.safeReturnTo("/mfg/control/receipts?search=PR-260101-01"));
        // anything else could send somebody to another site or another component
        assertNull(MfgLoginEvents.safeReturnTo("https://example.com/mfg/control/receipts"));
        assertNull(MfgLoginEvents.safeReturnTo("//example.com/mfg/control/receipts"));
        assertNull(MfgLoginEvents.safeReturnTo("/learningcurve/control/main"));
        assertNull(MfgLoginEvents.safeReturnTo("/mfg/control/../learningcurve/control/main"));
        assertNull(MfgLoginEvents.safeReturnTo("/mfg/control/receipts\r\nSet-Cookie: a=b"));
        assertNull(MfgLoginEvents.safeReturnTo(null));
    }

    @Test
    public void findsThePageOfTheRequestTheFrameworkRefused() {
        // OFBiz saves the request it refused, and the person should land on that page again
        assertEquals("/mfg/control/receipts", MfgLoginEvents.pageOf("/control/receipts"));
        assertEquals("/mfg/control/products", MfgLoginEvents.pageOf("products"));
        // an endpoint that answers with data is no place to land: the page that called it says where
        // to go instead (see session.js), and without that the start page is fine
        assertNull(MfgLoginEvents.pageOf("/control/orderImage"));
        assertNull(MfgLoginEvents.pageOf("/control/receiptSaveJson"));
        assertNull(MfgLoginEvents.pageOf("/control/freightCsv"));
        // the password gate is a view, not a request: coming back to it was a dead URL (Step 122)
        assertNull(MfgLoginEvents.pageOf("requirePasswordChange"));
        assertNull(MfgLoginEvents.pageOf("/control/requirePasswordChange"));
        assertNull(MfgLoginEvents.pageOf(null));
    }

    @Test
    public void namesTheAuditActionsInChinese() {
        // the page shows words, the table keeps the id: both come from one place
        assertEquals("登录成功", MfgAudit.label(MfgAudit.LOGIN_OK));
        assertEquals("登录失败", MfgAudit.label(MfgAudit.LOGIN_FAIL));
        assertEquals("查看密码提示", MfgAudit.label(MfgAudit.FORGOT_HINT));
        assertEquals("修改密码", MfgAudit.label(MfgAudit.PASSWORD_CHANGE));
        // a document write is not one action for the shop but two: it went through, or it was
        // refused, and the row says which (Step 125b)
        assertEquals("单据操作", MfgAudit.label(MfgAudit.DOC_CHANGE));
        assertEquals("操作被拒", MfgAudit.label(MfgAudit.DOC_FAIL));
        // an action this build does not know is shown as it is, not hidden
        assertEquals("SOMETHING_NEW", MfgAudit.label("SOMETHING_NEW"));
        assertEquals("", MfgAudit.label(null));
        assertEquals(6, MfgAudit.options().size());
        assertEquals(MfgAudit.LOGIN_OK, MfgAudit.options().get(0).get("actionId"));
        assertEquals("登录成功", MfgAudit.options().get(0).get("label"));
    }

    @Test
    public void namesTheRequestsThatChangeADocument() {
        // the hook after every request only writes a row for these, so a page that only reads leaves
        // nothing behind (Step 125b)
        assertEquals("新建工单", MfgAudit.documentLabel("workOrderCreate"));
        assertEquals("完工入库", MfgAudit.documentLabel("workOrderComplete"));
        assertEquals("发料", MfgAudit.documentLabel("issueSave"));
        assertEquals("出货", MfgAudit.documentLabel("deliverySave"));
        assertEquals("作废到货单", MfgAudit.documentLabel("receiptVoid"));
        // the same write reached through the JSON entry the page uses must carry the same words
        assertEquals("改到货单行", MfgAudit.documentLabel("receiptLineJson"));
        // reading a page, a lookup for a page, and the login itself are not documents
        assertNull(MfgAudit.documentLabel("main"));
        assertNull(MfgAudit.documentLabel("workOrderList"));
        assertNull(MfgAudit.documentLabel("orderPeek"));
        assertNull(MfgAudit.documentLabel("login"));
        assertNull(MfgAudit.documentLabel(null));
        // the component is mounted at /control/*, so the request name is the last segment of the
        // path info; reading the servlet path gave "/control" and recorded nothing at all
        assertEquals("workOrderCreate", MfgAudit.requestNameOf("/workOrderCreate"));
        assertEquals("workOrderCreate", MfgAudit.requestNameOf("/control/workOrderCreate"));
        assertEquals("workOrderCreate", MfgAudit.requestNameOf("/control/workOrderCreate/"));
        assertEquals("workOrderCreate", MfgAudit.requestNameOf("workOrderCreate"));
        assertNull(MfgAudit.requestNameOf("/"));
        assertNull(MfgAudit.requestNameOf(""));
        assertNull(MfgAudit.requestNameOf(null));
    }

    @Test
    public void saysWhyALoginWasRefusedInChinese() {
        // the audit row is read by the shop, so the framework's English is translated (the login
        // page does the same in signIn.groovy - keep the two in step)
        assertEquals("密码不对", MfgAudit.failureText(
                "following error occurred during login: Password incorrect."));
        assertEquals("账号被临时锁住了（连着输错密码）", MfgAudit.failureText(
                "following error occurred during login: The account for user login id \"admin\" has been disabled. It will be re-enabled 13:49:27."));
        assertEquals("没有这个账号名", MfgAudit.failureText("User not found."));
        assertEquals("", MfgAudit.failureText(null));
        // anything else is shown as it came, not swallowed
        assertEquals("something odd", MfgAudit.failureText("something odd"));
        // both components write into the same table, so the note says which one it was
        assertEquals("MES", MfgAudit.webappName("/mfg"));
        assertEquals("学习曲线", MfgAudit.webappName("/learningcurve"));
        assertEquals("webtools", MfgAudit.webappName("/webtools"));
        assertEquals("", MfgAudit.webappName(null));
    }

    @Test
    public void numbersAuditRowsByDayAndKeepsThemAYear() {        // same shape as every other number of the component, and it grows past 99 by itself
        assertEquals("AU-261026-01", MfgAudit.auditId(java.time.LocalDate.of(2026, 10, 26), 1));
        assertEquals("AU-261026-120", MfgAudit.auditId(java.time.LocalDate.of(2026, 10, 26), 120));
        // a year of retention, counted from an exact moment
        long now = java.sql.Timestamp.valueOf("2027-10-26 08:00:00").getTime();
        assertEquals("2026-10-26 08:00:00.0",
                MfgAudit.keepCutoff(now, MfgAudit.KEEP_DAYS).toString());
    }
}
