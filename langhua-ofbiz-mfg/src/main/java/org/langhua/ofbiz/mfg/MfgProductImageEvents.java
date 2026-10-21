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
 * Web events of the product photos: the upload of a multipart form and the delivery of the bytes.
 *
 * <p>The photo itself is kept in the database ({@code MfgProductImage}), so redeploying the
 * component or cleaning the build directory never loses it. Serving goes through the controller,
 * which keeps the access control of the component in place: a thumbnail is rendered on the fly so
 * the product list does not have to download a full photo per row.
 */
public final class MfgProductImageEvents {

    private static final String MODULE = MfgProductImageEvents.class.getName();

    /** Longest edge of the thumbnail served for the lists, in pixels. */
    public static final int THUMB_MAX_EDGE = 200;

    /** Multiplier that turns a byte count into a readable size. */
    private static final double KB = 1024.0;

    private MfgProductImageEvents() {
    }

    /**
     * Stores the photo of a multipart form. The form field is {@code imageFile}, the model comes
     * from the {@code productCode} field and an optional {@code note} is kept with the photo.
     *
     * @param request the request, already holding the parsed multipart form
     * @param response the response
     * @return {@code success} to go back to the product page, {@code error} to render it with a message
     */
    public static String upload(HttpServletRequest request, HttpServletResponse response) {
        Map<String, Object> parameters = parametersOf(request);
        String productCode = text(parameters.get("productCode"));
        String fileName = text(parameters.get("_imageFile_fileName"));
        String contentType = text(parameters.get("_imageFile_contentType"));
        byte[] data = bytesOf(parameters.get("imageFile"));

        Delegator delegator = (Delegator) request.getAttribute("delegator");
        if (delegator == null) {
            request.setAttribute("_ERROR_MESSAGE_", "No delegator, the photo was not stored");
            return "error";
        }
        Map<String, Object> result = MfgServices.storeProductImage(delegator, productCode, fileName,
                contentType, data, text(parameters.get("note")));
        Object error = result.get("errorMessage");
        if (error != null) {
            request.setAttribute("_ERROR_MESSAGE_", String.valueOf(error));
            return "error";
        }
        Debug.logInfo("Stored photo " + result.get("seq") + " of " + productCode + " ("
                + Math.round(((Long) result.get("imageSize")).longValue() / KB) + " KB)", MODULE);
        return "success";
    }

    /**
     * Writes one photo to the response. Parameters: {@code productCode} (required), {@code seq}
     * (defaults to the newest photo) and {@code thumb=1} for a scaled down JPEG.
     *
     * @param request the request
     * @param response the response, used to write the bytes
     * @return always {@code success}; the site configuration maps it to no view
     */
    public static String serve(HttpServletRequest request, HttpServletResponse response) {
        Delegator delegator = (Delegator) request.getAttribute("delegator");
        String productCode = text(request.getParameter("productCode"));
        String seqText = text(request.getParameter("seq"));
        boolean thumb = "1".equals(request.getParameter("thumb"));
        if (delegator == null || productCode == null) {
            return notFound(response);
        }
        try {
            GenericValue image;
            if (seqText != null) {
                long seq;
                try {
                    seq = Long.parseLong(seqText);
                } catch (NumberFormatException e) {
                    return notFound(response);
                }
                image = EntityQuery.use(delegator).from("MfgProductImage")
                        .where("productCode", productCode, "seq", Long.valueOf(seq)).queryOne();
            } else {
                image = EntityQuery.use(delegator).from("MfgProductImage")
                        .where("productCode", productCode).orderBy("-seq").queryOne();
            }
            if (image == null) {
                return notFound(response);
            }
            byte[] data = image.getBytes("imageData");
            String contentType = image.getString("contentType");
            if (thumb) {
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
            // the photo of one sequence number never changes, so the browser can keep it
            response.setHeader("Cache-Control", "private, max-age=86400");
            response.setHeader("ETag", "\"" + productCode + "-" + image.getLong("seq") + "-"
                    + (thumb ? "t" : "f") + "\"");
            OutputStream out = response.getOutputStream();
            out.write(data);
            out.flush();
            return "success";
        } catch (GenericEntityException | IOException e) {
            Debug.logError(e, "Could not serve the photo of [" + productCode + "]", MODULE);
            return notFound(response);
        }
    }

    private static String notFound(HttpServletResponse response) {
        try {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        } catch (IOException e) {
            Debug.logWarning(e, "Could not send 404 for a photo", MODULE);
        }
        return "error";
    }

    /**
     * Reads the parameters of the request, parsing a multipart form when the pipeline did not do it
     * already.
     *
     * @param request the request
     * @return the parameter map, with the uploaded files as {@link ByteBuffer}
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
     * Scales a photo down to {@link #THUMB_MAX_EDGE} and encodes it as JPEG.
     *
     * @param data the original photo
     * @return the JPEG bytes, {@code null} when the photo cannot be read
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
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, targetWidth, targetHeight);
                graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(target, "jpg", out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            Debug.logWarning(e, "Could not build a thumbnail, serving the full photo", MODULE);
            return null;
        }
    }
}
