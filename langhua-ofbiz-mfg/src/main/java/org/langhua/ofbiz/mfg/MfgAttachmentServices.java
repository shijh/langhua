/*
 * Copyright (C) 2010-2026 Tianjin Langhua Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.langhua.ofbiz.mfg;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.ofbiz.entity.Delegator;
import org.apache.ofbiz.entity.GenericEntityException;
import org.apache.ofbiz.entity.GenericValue;
import org.apache.ofbiz.entity.util.EntityQuery;
import org.apache.ofbiz.service.DispatchContext;
import org.apache.ofbiz.service.ServiceUtil;

/**
 * Photos and PDF files kept with any business record: product models, development projects, work
 * orders, deliveries and after sales claims all hang their files on the same table.
 *
 * <p>The rules live here rather than in the upload event, so the event and the service behave the
 * same way: which content types are accepted, how big a file may be, how a file name is cleaned
 * before it reaches the database, and how the old single photo table is copied over the first time
 * a model is read.
 *
 * <p>Moved out of {@code MfgServices} in Step 166.
 */
public final class MfgAttachmentServices {

    private MfgAttachmentServices() {
    }

    /**
     * Tells whether a content type is a raster image, the kind that can be scaled into a thumbnail.
     *
     * @param contentType the content type sent by the browser, may be null
     * @return {@code true} for JPG, PNG and GIF
     */
    static boolean isAllowedImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        for (String allowed : MfgConstants.IMAGE_TYPES) {
            if (allowed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a content type is shown as a picture on the pages.
     *
     * <p>The three raster types and SVG: a drawing is displayed the same way, it just cannot be turned
     * into a JPEG thumbnail, so {@link #isAllowedImageType} (which guards the scaling) stays narrower.
     *
     * @param contentType the content type, may be null
     * @return {@code true} for JPG, PNG, GIF and SVG
     */
    static boolean isShownAsImage(String contentType) {
        return isAllowedImageType(contentType)
                || (contentType != null && MfgConstants.SVG_TYPE.equals(contentType.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Tells whether a content type may be attached: an image, a drawing or a pdf.
     *
     * @param contentType the content type, may be null
     * @return {@code true} for an accepted type
     */
    static boolean isAllowedAttachmentType(String contentType) {
        return isShownAsImage(contentType)
                || (contentType != null && MfgConstants.PDF_TYPE.equals(contentType.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Tells whether an owner type is known.
     *
     * @param ownerTypeId the owner type
     * @return {@code true} for a type of {@link MfgConstants#ATTACHMENT_OWNER_TYPES}
     */
    static boolean isKnownAttachmentOwnerType(String ownerTypeId) {
        if (ownerTypeId == null) {
            return false;
        }
        for (String type : MfgConstants.ATTACHMENT_OWNER_TYPES) {
            if (type.equals(ownerTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Guesses the content type from a file name, used when the browser sends something useless such
     * as {@code application/octet-stream}.
     *
     * @param fileName the original file name, may be null
     * @return the content type, {@code null} when the extension is not supported
     */
    static String imageTypeOfFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String name = fileName.trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".gif")) {
            return "image/gif";
        }
        if (name.endsWith(".svg")) {
            return MfgConstants.SVG_TYPE;
        }
        if (name.endsWith(".pdf")) {
            return MfgConstants.PDF_TYPE;
        }
        return null;
    }

    /**
     * Keeps only the last part of a file name and replaces the characters that do not belong in a
     * name, so a Windows path or a strange upload cannot leak into the database.
     *
     * @param fileName the original file name, may be null
     * @return a short and safe file name
     */
    static String cleanFileName(String fileName) {
        if (fileName == null) {
            return "file";
        }
        String name = fileName.trim().replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._-]", "_");
        if (name.length() > 60) {
            name = name.substring(name.length() - 60);
        }
        return name.isEmpty() ? "file" : name;
    }

    static List<GenericValue> attachmentRows(Delegator delegator, String ownerTypeId, String ownerId)
            throws GenericEntityException {
        return EntityQuery.use(delegator).from("MfgAttachment")
                .where("ownerTypeId", ownerTypeId, "ownerId", ownerId).orderBy("-seq").queryList();
    }

    /**
     * Copies the rows of the old product photo table into the generic attachment table the first
     * time a model is read, so old and new photos live in one place.
     *
     * @param delegator the delegator
     * @param productCode the model
     * @return the number of copied rows
     * @throws GenericEntityException when the rows cannot be read or written
     */
    static int migrateProductPhotos(Delegator delegator, String productCode) throws GenericEntityException {
        List<GenericValue> legacy = EntityQuery.use(delegator).from("MfgProductImage")
                .where("productCode", productCode).orderBy("seq").queryList();
        if (legacy.isEmpty()) {
            return 0;
        }
        Set<Long> existing = new LinkedHashSet<>();
        for (GenericValue attachment : attachmentRows(delegator, MfgConstants.ATT_PRODUCT, productCode)) {
            existing.add(attachment.getLong("seq"));
        }
        int copied = 0;
        for (GenericValue image : legacy) {
            Long seq = image.getLong("seq");
            if (seq == null || existing.contains(seq)) {
                continue;
            }
            GenericValue attachment = delegator.makeValue("MfgAttachment");
            attachment.set("ownerTypeId", MfgConstants.ATT_PRODUCT);
            attachment.set("ownerId", productCode);
            attachment.set("seq", seq);
            attachment.set("fileName", image.getString("fileName"));
            attachment.set("contentType", image.getString("contentType"));
            attachment.set("fileSize", image.getLong("imageSize"));
            attachment.set("fileData", image.getBytes("imageData"));
            attachment.set("note", image.getString("note"));
            delegator.create(attachment);
            copied++;
        }
        return copied;
    }

    static long nextAttachmentSeq(Delegator delegator, String ownerTypeId, String ownerId)
            throws GenericEntityException {
        long max = 0;
        for (GenericValue attachment : attachmentRows(delegator, ownerTypeId, ownerId)) {
            Long seq = attachment.getLong("seq");
            if (seq != null) {
                max = Math.max(max, seq.longValue());
            }
        }
        return max + 1L;
    }

    static Map<String, Object> toAttachmentMap(GenericValue attachment) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("ownerTypeId", attachment.getString("ownerTypeId"));
        map.put("ownerId", attachment.getString("ownerId"));
        map.put("seq", attachment.getLong("seq"));
        map.put("fileName", attachment.getString("fileName"));
        map.put("contentType", attachment.getString("contentType"));
        map.put("fileSize", attachment.getLong("fileSize"));
        map.put("isImage", Boolean.valueOf(isShownAsImage(attachment.getString("contentType"))));
        map.put("note", attachment.getString("note"));
        // the note counter on the page reads its ceiling from here, so the two cannot drift apart
        map.put("noteLimit", Integer.valueOf(MfgConstants.MAX_ATTACHMENT_NOTE_CHARS));
        map.put("createdStamp", attachment.get("createdStamp"));
        return map;
    }

    /**
     * Stores a file as it is, without the picture/pdf rules of the attachment form.
     *
     * <p>Why: a screenshot of a shop page is a `png` of up to 32 MB, which the upload form would
     * refuse (it is meant for pictures of a product). The order recognition keeps the page it read so
     * the numbers can be held against it afterwards, so this writes the row directly.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record the file belongs to
     * @param ownerId the key of that record
     * @param fileName the original file name
     * @param contentType what the bytes are
     * @param data the bytes
     * @param note an optional note
     * @return the sequence number of the stored file
     * @throws GenericEntityException when it cannot be written
     */
    public static long storeScreenshot(Delegator delegator, String ownerTypeId, String ownerId, String fileName,
            String contentType, byte[] data, String note) throws GenericEntityException {
        long seq = nextAttachmentSeq(delegator, ownerTypeId, ownerId);
        GenericValue attachment = delegator.makeValue("MfgAttachment");
        attachment.set("ownerTypeId", ownerTypeId);
        attachment.set("ownerId", ownerId);
        attachment.set("seq", Long.valueOf(seq));
        attachment.set("fileName", cleanFileName(fileName));
        attachment.set("contentType", contentType == null ? "image/png" : contentType);
        attachment.set("fileSize", Long.valueOf(data.length));
        attachment.set("fileData", data);
        attachment.set("note", note);
        delegator.create(attachment);
        return seq;
    }

    /**
     * Validates and stores one attachment. Shared by the upload event and the service so both
     * behave the same way.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record the file belongs to
     * @param ownerId the key of that record
     * @param fileName the original file name
     * @param contentType the content type, the extension is used when it is not a known type
     * @param data the file bytes
     * @param note an optional note
     * @return the service result with {@code seq} and {@code fileSize}, or an error message
     */
    static Map<String, Object> storeAttachment(Delegator delegator, String ownerTypeId, String ownerId,
            String fileName, String contentType, byte[] data, String note) {
        if (!isKnownAttachmentOwnerType(ownerTypeId)) {
            return attachmentError("owner", "Unknown attachment owner type [" + ownerTypeId + "]");
        }
        if (ownerId == null) {
            return attachmentError("owner", "ownerId is required");
        }
        if (data == null || data.length == 0) {
            return attachmentError("nofile", "Choose a file first");
        }
        String type = isAllowedAttachmentType(contentType)
                ? contentType.trim().toLowerCase(Locale.ROOT) : imageTypeOfFileName(fileName);
        if (type == null) {
            return attachmentError("type", "Only JPG, PNG, GIF, SVG and PDF files are supported");
        }
        boolean pdf = MfgConstants.PDF_TYPE.equals(type);
        long limit = pdf ? MfgConstants.PDF_MAX_BYTES : MfgConstants.IMAGE_MAX_BYTES;
        if (data.length > limit) {
            return attachmentError("size",
                    (pdf ? "The PDF is larger than 8 MB" : "The image is larger than 4 MB")
                    + ", please shrink it first");
        }
        try {
            long seq = nextAttachmentSeq(delegator, ownerTypeId, ownerId);
            GenericValue attachment = delegator.makeValue("MfgAttachment");
            attachment.set("ownerTypeId", ownerTypeId);
            attachment.set("ownerId", ownerId);
            attachment.set("seq", Long.valueOf(seq));
            attachment.set("fileName", cleanFileName(fileName));
            attachment.set("contentType", type);
            attachment.set("fileSize", Long.valueOf(data.length));
            attachment.set("fileData", data);
            attachment.set("note", note);
            delegator.create(attachment);

            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ownerTypeId", ownerTypeId);
            result.put("ownerId", ownerId);
            result.put("seq", Long.valueOf(seq));
            result.put("fileSize", Long.valueOf(data.length));
            result.put("contentType", type);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not store the attachment: " + e.getMessage());
        }
    }

    /**
     * Stores an attachment given as a base64 string, handy for scripts and for the integration test.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/attachment-services.xml#mfgAttachmentSave}
     * @return the service result containing {@code seq}
     */
    public static Map<String, Object> attachmentSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context,
                MfgConstants.PERM_ENG, MfgConstants.PERM_STOCK, MfgConstants.PERM_PRODUCTION, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String base64 = MfgSupport.toText(context.get("base64Data"));
        if (base64 == null) {
            return ServiceUtil.returnError("base64Data is required");
        }
        byte[] data;
        try {
            data = java.util.Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return ServiceUtil.returnError("The attachment data is not valid base64");
        }
        return storeAttachment(dctx.getDelegator(), MfgSupport.toText(context.get("ownerTypeId")),
                MfgSupport.toText(context.get("ownerId")), MfgSupport.toText(context.get("fileName")),
                MfgSupport.toText(context.get("contentType")), data, MfgSupport.toText(context.get("note")));
    }

    /**
     * Lists the attachments of one record, newest first, without the file bytes.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/attachment-services.xml#mfgAttachmentList}
     * @return the service result containing {@code attachments}
     */
    public static Map<String, Object> attachmentList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ownerTypeId = MfgSupport.toText(context.get("ownerTypeId"));
        String ownerId = MfgSupport.toText(context.get("ownerId"));
        List<Map<String, Object>> list = new ArrayList<>();
        if (ownerTypeId != null && ownerId != null) {
            try {
                if (MfgConstants.ATT_PRODUCT.equals(ownerTypeId)) {
                    // photos of the old single table are moved over the first time they are read
                    migrateProductPhotos(delegator, ownerId);
                }
                for (GenericValue attachment : attachmentRows(delegator, ownerTypeId, ownerId)) {
                    list.add(toAttachmentMap(attachment));
                }
            } catch (GenericEntityException e) {
                return ServiceUtil.returnError("Could not read the attachments: " + e.getMessage());
            }
        }
        Map<String, Object> result = ServiceUtil.returnSuccess();
        result.put("attachments", list);
        result.put("attachmentCount", Integer.valueOf(list.size()));
        return result;
    }

    /**
     * Builds a service error that also carries a short code the page can translate.
     *
     * @param code the code, for example {@code size} or {@code type}
     * @param message the message for the log and for services callers
     * @return the error result
     */
    static Map<String, Object> attachmentError(String code, String message) {
        Map<String, Object> result = ServiceUtil.returnError(message);
        result.put("errorCode", code);
        return result;
    }

    /**
     * Removes one attachment of a record.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record
     * @param ownerId the key of the record
     * @param seq the sequence number of the file
     * @return the service result or an error
     */
    static Map<String, Object> deleteAttachment(Delegator delegator, String ownerTypeId, String ownerId,
            long seq) {
        try {
            GenericValue attachment = EntityQuery.use(delegator).from("MfgAttachment")
                    .where("ownerTypeId", ownerTypeId, "ownerId", ownerId, "seq", Long.valueOf(seq)).queryOne();
            if (attachment == null) {
                return ServiceUtil.returnError("Attachment " + seq + " of [" + ownerId + "] does not exist");
            }
            attachment.remove();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ownerTypeId", ownerTypeId);
            result.put("ownerId", ownerId);
            result.put("seq", Long.valueOf(seq));
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not delete the attachment: " + e.getMessage());
        }
    }

    /**
     * Removes one attachment of a record.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/attachment-services.xml#mfgAttachmentDelete}
     * @return the service result containing the owner keys and {@code seq}
     */
    public static Map<String, Object> attachmentDelete(DispatchContext dctx, Map<String, ? extends Object> context) {        String denied = MfgSecurity.permissionProblem(dctx, context,
                MfgConstants.PERM_ENG, MfgConstants.PERM_STOCK, MfgConstants.PERM_PRODUCTION, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String ownerTypeId = MfgSupport.toText(context.get("ownerTypeId"));
        String ownerId = MfgSupport.toText(context.get("ownerId"));
        long seq = MfgSupport.toLong(context.get("seq"), 0);
        if (ownerTypeId == null || ownerId == null || seq <= 0) {
            return ServiceUtil.returnError("ownerTypeId, ownerId and seq are required");
        }
        return deleteAttachment(dctx.getDelegator(), ownerTypeId, ownerId, seq);
    }

    /**
     * Rewrites the note of one attachment - a file is often uploaded first and described afterwards
     * ("正面" became "背面有摔伤"), and re-uploading it just to change the words is not an option.
     *
     * @param dctx the dispatch context
     * @param context service context, see {@code servicedef/attachment-services.xml#mfgAttachmentNote}
     * @return the service result containing the owner keys, {@code seq} and the new {@code note}
     */
    public static Map<String, Object> attachmentNote(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgSecurity.permissionProblem(dctx, context,
                MfgConstants.PERM_ENG, MfgConstants.PERM_STOCK, MfgConstants.PERM_PRODUCTION, MfgConstants.PERM_SALES);
        if (denied != null) {
            return ServiceUtil.returnError(denied);
        }
        String ownerTypeId = MfgSupport.toText(context.get("ownerTypeId"));
        String ownerId = MfgSupport.toText(context.get("ownerId"));
        long seq = MfgSupport.toLong(context.get("seq"), 0);
        if (ownerTypeId == null || ownerId == null || seq <= 0) {
            return ServiceUtil.returnError("ownerTypeId, ownerId and seq are required");
        }
        return attachmentNote(dctx.getDelegator(), ownerTypeId, ownerId, seq,
                MfgSupport.toText(context.get("note")));
    }

    /**
     * Rewrites the note of one attachment.
     *
     * @param delegator the delegator
     * @param ownerTypeId the kind of record
     * @param ownerId the key of the record
     * @param seq the sequence number of the file
     * @param note the new note, may be {@code null} to clear it
     * @return the service result or an error
     */
    static Map<String, Object> attachmentNote(Delegator delegator, String ownerTypeId, String ownerId, long seq,
            String note) {
        /* a note on an attachment is a caption, and its column is a 255 character description: checking
           it here keeps Derby's truncation error off the page */
        if (note != null && note.length() > MfgConstants.MAX_ATTACHMENT_NOTE_CHARS) {
            return ServiceUtil.returnError("附件备注最长 " + MfgConstants.MAX_ATTACHMENT_NOTE_CHARS
                    + " 个字（现在 " + note.length() + " 个），删掉一些再存");
        }
        try {
            GenericValue attachment = EntityQuery.use(delegator).from("MfgAttachment")
                    .where("ownerTypeId", ownerTypeId, "ownerId", ownerId, "seq", Long.valueOf(seq)).queryOne();
            if (attachment == null) {
                return ServiceUtil.returnError("Attachment " + seq + " of [" + ownerId + "] does not exist");
            }
            attachment.set("note", note);
            attachment.store();
            Map<String, Object> result = ServiceUtil.returnSuccess();
            result.put("ownerTypeId", ownerTypeId);
            result.put("ownerId", ownerId);
            result.put("seq", Long.valueOf(seq));
            result.put("note", note);
            return result;
        } catch (GenericEntityException e) {
            return ServiceUtil.returnError("Could not save the note: " + e.getMessage());
        }
    }

    /**
     * The ETag of one attachment, in ASCII.
     *
     * <p>An owner id may be a part number with Chinese in it (a shop sells
     * "蓝色WCH-LINKE下载器"), and an HTTP header can only carry ASCII: Tomcat writes headers as
     * ISO-8859-1 and HTTP/2 rejects the whole response, which reaches the browser as
     * {@code ERR_HTTP2_PROTOCOL_ERROR} while the PDF itself is perfectly fine. The tag is nothing
     * but an opaque cache key, so it is hashed.
     *
     * @param ownerTypeId what the file hangs on
     * @param ownerId the key of that record, may hold any character
     * @param seq the sequence number of the attachment
     * @param thumb whether the scaled down picture was asked for
     * @return the header value, quotes included
     */
    public static String etagOf(String ownerTypeId, String ownerId, long seq, boolean thumb) {
        String raw = ownerTypeId + "-" + ownerId + "-" + seq + "-" + (thumb ? "t" : "f");
        return "\"" + sha1Hex(raw) + "\"";
    }

    /**
     * A {@code Content-Disposition} header that survives a Chinese file name.
     *
     * <p>The plain {@code filename} is forced to ASCII (anything else breaks the response over
     * HTTP/2, see {@link #etagOf}), and the real name travels in the RFC 5987 {@code filename*}
     * parameter, which browsers understand - so the user still gets "使用说明书.pdf" when saving.
     *
     * @param kind {@code inline} to open in the browser, {@code attachment} to download
     * @param fileName the name of the file, may be null
     * @return the header value
     */
    public static String contentDispositionOf(String kind, String fileName) {
        String name = fileName == null || fileName.isEmpty() ? "file" : fileName;
        StringBuilder ascii = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            // printable ASCII only, and neither quote nor backslash inside a quoted string
            ascii.append(c >= 0x20 && c < 0x7F && c != '"' && c != '\\' ? c : '_');
        }
        String encoded;
        try {
            encoded = URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            encoded = ascii.toString();
        }
        return kind + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static String sha1Hex(String text) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // every JRE ships SHA-1; keep the header working anyway
            return Integer.toHexString(text.hashCode());
        }
    }
}
