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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.ofbiz.base.util.Debug;
import org.apache.ofbiz.base.util.UtilGenerics;
import org.apache.ofbiz.base.util.UtilHttp;
import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;

/**
 * Web events of the attachments: the upload of a multipart form and the delivery of the bytes.
 *
 * <p>A file is attached to any business record through {@code ownerTypeId} and {@code ownerId}
 * (a product, a development project, a work order, a delivery or a claim). The bytes live in
 * {@code MfgAttachment}, so redeploying the component or cleaning the build directory never loses
 * them. Serving goes through the controller, which keeps the access control of the component in
 * place; a thumbnail is rendered on the fly so a list of rows does not download the full photos.
 *
 * <p>For compatibility the {@code productCode} parameter is understood as
 * {@code ownerId} of an {@code MFG_ATT_PRODUCT} record.
 */
public final class MfgAttachmentEvents {

    private static final String MODULE = MfgAttachmentEvents.class.getName();

    /** Longest edge of the thumbnail served for the lists, in pixels. */
    public static final int THUMB_MAX_EDGE = 200;

    /** Divisor that turns a byte count into kilobytes. */
    private static final double KB = 1024.0;

    private MfgAttachmentEvents() {
    }

    /**
     * Stores the file of a multipart form. The form field is {@code file}, the owner comes from
     * {@code ownerTypeId} and {@code ownerId} and an optional {@code note} is kept with the file.
     *
     * @param request the request, already holding the parsed multipart form
     * @param response the response
     * @return {@code success} to go back to the page, {@code error} to render it with a message
     */
    public static String upload(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        String ownerTypeId = text(parameters.get("ownerTypeId"));
        String ownerId = ownerIdOf(parameters);
        String fileName = text(parameters.get("_file_fileName"));
        if (fileName == null) {
            fileName = text(parameters.get("_imageFile_fileName"));
        }
        String contentType = text(parameters.get("_file_contentType"));
        if (contentType == null) {
            contentType = text(parameters.get("_imageFile_contentType"));
        }
        byte[] data = bytesOf(parameters.get("file"));
        if (data == null) {
            data = bytesOf(parameters.get("imageFile"));
        }

        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (delegator == null) {
            return redirectBack(request, response, parameters, "other");
        }
        Map<String, Object> result = MfgServices.storeAttachment(delegator, ownerTypeId, ownerId,
                fileName, contentType, data, text(parameters.get("note")));
        Object error = result.get("errorMessage");
        if (error != null) {
            Debug.logInfo("Attachment not stored: " + error, MODULE);
            return redirectBack(request, response, parameters, String.valueOf(result.get("errorCode")));
        }
        Debug.logInfo("Stored attachment " + result.get("seq") + " of " + ownerTypeId + "/" + ownerId
                + " (" + Math.round(((Long) result.get("fileSize")).longValue() / KB) + " KB)", MODULE);
        return redirectBack(request, response, parameters, null);
    }

    /**
     * Removes one attachment and goes back to the page.
     *
     * @param request the request
     * @param response the response
     * @return always {@code success}; the site configuration maps it to no view
     */
    public static String delete(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        String ownerTypeId = text(parameters.get("ownerTypeId"));
        String ownerId = ownerIdOf(parameters);
        long seq = 0;
        String seqText = text(parameters.get("seq"));
        try {
            seq = seqText == null ? 0 : Long.parseLong(seqText);
        } catch (NumberFormatException e) {
            seq = 0;
        }
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (delegator != null && ownerTypeId != null && ownerId != null && seq > 0) {
            Map<String, Object> result = MfgServices.deleteAttachment(delegator, ownerTypeId, ownerId, seq);
            Object error = result.get("errorMessage");
            if (error != null) {
                Debug.logInfo("Attachment not deleted: " + error, MODULE);
            }
        }
        return redirectBack(request, response, parameters, null);
    }

    /**
     * Writes one attachment to the response. Parameters: {@code ownerTypeId} and {@code ownerId}
     * (required, {@code productCode} is accepted for products), {@code seq} (defaults to the newest
     * one) and {@code thumb=1} for a scaled down JPEG of an image.
     *
     * @param request the request
     * @param response the response, used to write the bytes
     * @return always {@code success}; the site configuration maps it to no view
     */
    public static String serve(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String ownerTypeId = text(request.getParameter("ownerTypeId"));
        if (ownerTypeId == null) {
            ownerTypeId = MfgServices.ATT_PRODUCT;
        }
        String ownerId = ownerIdOfQuery(request);
        String seqText = text(request.getParameter("seq"));
        boolean thumb = "1".equals(request.getParameter("thumb"));
        if (delegator == null || ownerId == null) {
            return notFound(response);
        }
        try {
            GenericValue attachment;
            if (seqText != null) {
                long seq;
                try {
                    seq = Long.parseLong(seqText);
                } catch (NumberFormatException e) {
                    return notFound(response);
                }
                attachment = EntityQuery.use(delegator).from("MfgAttachment")
                        .where("ownerTypeId", ownerTypeId, "ownerId", ownerId, "seq", Long.valueOf(seq)).queryOne();
            } else {
                attachment = EntityQuery.use(delegator).from("MfgAttachment")
                        .where("ownerTypeId", ownerTypeId, "ownerId", ownerId).orderBy("-seq").queryOne();
            }
            if (attachment == null) {
                return notFound(response);
            }
            byte[] data = attachment.getBytes("fileData");
            String contentType = attachment.getString("contentType");
            if (thumb && MfgServices.isAllowedImageType(contentType)) {
                byte[] scaled = thumbnail(data);
                if (scaled != null) {
                    data = scaled;
                    contentType = "image/jpeg";
                }
            }
            if (contentType == null || data == null) {
                return notFound(response);
            }
            response.setContentType(contentType);
            response.setContentLength(data.length);
            // the named download keeps the original file name, which matters for the pdf files
            if (!thumb) {
                String fileName = attachment.getString("fileName");
                if (fileName != null) {
                    response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
                }
            }
            // the file of one sequence number never changes, so the browser can keep it
            response.setHeader("Cache-Control", "private, max-age=86400");
            response.setHeader("ETag", "\"" + ownerTypeId + "-" + ownerId + "-" + attachment.getLong("seq")
                    + "-" + (thumb ? "t" : "f") + "\"");
            OutputStream out = response.getOutputStream();
            out.write(data);
            out.flush();
            return "success";
        } catch (GenericEntityException | IOException e) {
            Debug.logError(e, "Could not serve the attachment of [" + ownerId + "]", MODULE);
            return notFound(response);
        }
    }

    private static String ownerIdOf(Map<String, Object> parameters) {
        String ownerId = text(parameters.get("ownerId"));
        return ownerId != null ? ownerId : text(parameters.get("productCode"));
    }

    /**
     * Sends the browser back to the page that hosted the form, with an error code when something
     * went wrong.
     *
     * @param request the request
     * @param response the response
     * @param parameters the parsed parameters
     * @param errorCode the error code to show, {@code null} when there is nothing to report
     * @return always {@code success}; the site configuration maps it to no view
     */
    private static String redirectBack(HttpServletRequest request, HttpServletResponse response,
            Map<String, Object> parameters, String errorCode) {
        String back = text(parameters.get("back"));
        if (back == null || !back.startsWith("/mfg/control/")) {
            String productCode = text(parameters.get("productCode"));
            back = productCode == null ? "/mfg/control/products"
                    : "/mfg/control/products?productCode=" + productCode;
        }
        if (errorCode != null && !"null".equals(errorCode)) {
            back = back + (back.indexOf('?') >= 0 ? "&" : "?") + "err=" + errorCode;
        }
        try {
            response.sendRedirect(back);
        } catch (IOException e) {
            Debug.logError(e, "Could not redirect after the attachment upload", MODULE);
            return "error";
        }
        return "success";
    }

    private static String ownerIdOfQuery(HttpServletRequest request) {
        String ownerId = text(request.getParameter("ownerId"));
        return ownerId != null ? ownerId : text(request.getParameter("productCode"));
    }

    private static String notFound(HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            Debug.logWarning(e, "Could not send 404 for an attachment", MODULE);
        }
        return "error";
    }

    /**
     * Reads the parameters of the request, parsing a multipart form when the pipeline did not do it
     * already.
     *
     * @param request the request
     * @return the parameter map, with the uploaded file as {@link ByteBuffer}
     */
    private static Map<String, Object> parametersOf(HttpServletRequest request) {
        Map<String, Object> parsed = UtilGenerics.cast(request.getAttribute("multiPartMap"));
        if (parsed != null && !parsed.isEmpty()) {
            return parsed;
        }
        return UtilHttp.getParameterMap(request);
    }

    private static byte[] bytesOf(Object value) {
        if (value instanceof ByteBuffer) {
            ByteBuffer buffer = ((ByteBuffer) value).duplicate();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            return data;
        }
        if (value instanceof byte[]) {
            return (byte[]) value;
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * Scales an image down to {@link #THUMB_MAX_EDGE} and encodes it as JPEG.
     *
     * @param data the original image
     * @return the JPEG bytes, {@code null} when the image cannot be read
     */
    private static byte[] thumbnail(byte[] data) {
        try {
            BufferedImage source = ImageIO.read(new ByteArrayInputStream(data));
            if (source == null) {
                return null;
            }
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }
            double scale = Math.min(1.0, (double) THUMB_MAX_EDGE / Math.max(width, height));
            int targetWidth = Math.max(1, (int) Math.round(width * scale));
            int targetHeight = Math.max(1, (int) Math.round(height * scale));
            // JPEG has no alpha channel, so paint on white first
            BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            Debug.logWarning(e, "Could not build a thumbnail, serving the full image", MODULE);
            return null;
        }
    }
}
