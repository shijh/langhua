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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.langhua.ofbiz.mfg.oir.OirScan;

/**
 * The two request handlers of the order image recognition, kept under the name the controller and the
 * component use. The work - the request to the vision service, the slicing of a long page, the prompts
 * and the retries - lives in {@code org.langhua.ofbiz.mfg.oir}, so the algorithm can be read, reviewed
 * and improved on its own (see {@code oir/README.md}).
 */
public final class MfgOrderScan {

    private MfgOrderScan() {
    }

    /**
     * Reads a screenshot of shop orders and answers the page with the orders it found.
     *
     * @param request the upload, the picture is the multipart field {@code image}
     * @param response the response the JSON answer is written to
     * @return "success"
     */
    public static String scan(HttpServletRequest request, HttpServletResponse response) {
        return OirScan.scan(request, response);
    }

    /**
     * Calls the vision endpoint once with a one pixel picture, so the person can see whether the
     * address, the model and the key work before trusting a whole page to them.
     *
     * @param request the settings of the page, or the ones stored in the database
     * @param response the response the JSON answer is written to
     * @return "success"
     */
    public static String test(HttpServletRequest request, HttpServletResponse response) {
        return OirScan.test(request, response);
    }
}
