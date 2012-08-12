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
package org.ofbiz.zxing;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Map;
import java.lang.Integer;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.ofbiz.base.util.UtilHttp;
import org.ofbiz.base.util.UtilMisc;
import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.service.GenericServiceException;
import org.ofbiz.service.LocalDispatcher;
import org.ofbiz.service.ServiceUtil;

/**
 * Events for QRCode.
 */
public class QRCodeEvents {

    public static final String module = QRCodeEvents.class.getName();
    
    /** Streams QR Code to the output. */
    public static String serveQRCodeImage(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = ((HttpServletRequest) request).getSession();
        LocalDispatcher dispatcher = (LocalDispatcher) request.getAttribute("dispatcher");
        Map<String, Object> parameters = UtilHttp.getParameterMap(request);
        String message = (String) parameters.get("message");
        GenericValue userLogin = (GenericValue) request.getAttribute("userLogin");
        if (userLogin == null) userLogin = (GenericValue) session.getAttribute("userLogin");
        if (userLogin == null) userLogin = (GenericValue) session.getAttribute("autoUserLogin");
        Locale locale = UtilHttp.getLocale(request);
        
        if (UtilValidate.isEmpty(message)) {
        	message = "Error get message parameter.";
        }
        String mimeType = "application/octet-stream";
        String format = (String) parameters.get("format");
        if (UtilValidate.isEmpty(format)) {
        	format = "jpg";
        }
        if (format.equals("png")) {
        	mimeType = "image/png";
        } else if (format.equals("jpg")) {
        	mimeType = "image/jpeg";
        }
        String width = (String) parameters.get("width");
        String height = (String) parameters.get("height");

        try {
            if (mimeType != null) {
                response.setContentType(mimeType);
            }
            OutputStream os = response.getOutputStream();
            Map<String, Object> context = UtilMisc.<String, Object>toMap("message", message,
            		"format", format,
            		"userLogin", userLogin,
            		"locale", locale);
            if (width != null) {
            	context.put("width", Integer.parseInt(width));
            	if (height == null) {
                	context.put("height", Integer.parseInt(width));
            	}
            }
            if (height != null) {
            	context.put("height", Integer.parseInt(height));
            	if (width == null) {
                	context.put("width", Integer.parseInt(height));
            	}
            }
            Map<String, Object> results = dispatcher.runSync("generateQRCodeImage", context);
            if (!ServiceUtil.isError(results)) {
                BufferedImage bufferedImage = (BufferedImage) results.get("bufferedImage");
                if (!ImageIO.write(bufferedImage, format, os)) {
                    String errMsg = UtilProperties.getMessage(
    						"ZXingUiLabels",
    						"ErrorWriteFormatToFile",
    						new Object[] { format }, locale);
                    request.setAttribute("_ERROR_MESSAGE_", errMsg);
                    return "error";
                }
                os.flush();
            } else {
                String errMsg = ServiceUtil.getErrorMessage(results);
                request.setAttribute("_ERROR_MESSAGE_", errMsg);
                return "error";
            }
        } catch (IOException e) {
            String errMsg = UtilProperties.getMessage(
					"ZXingUiLabels",
					"ErrorGenerateQRCode",
					new Object[] { e.getMessage() }, locale);
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
        } catch (GenericServiceException e) {
            String errMsg = UtilProperties.getMessage(
					"ZXingUiLabels",
					"ErrorGenerateQRCode",
					new Object[] { e.getMessage() }, locale);
            request.setAttribute("_ERROR_MESSAGE_", errMsg);
            return "error";
		}
        return "success";
    }
}
