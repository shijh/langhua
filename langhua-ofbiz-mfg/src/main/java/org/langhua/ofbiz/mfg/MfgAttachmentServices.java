package org.langhua.ofbiz.mfg;

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
     * Tells whether a content type is an accepted image type.
     *
     * @param contentType the content type sent by the browser, may be null
     * @return {@code true} for JPG, PNG and GIF
     */
    static boolean isAllowedImageType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String type = contentType.trim().toLowerCase(Locale.ROOT);
        for (String allowed : MfgServices.IMAGE_TYPES) {
            if (allowed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tells whether a content type may be attached: an image or a pdf.
     *
     * @param contentType the content type, may be null
     * @return {@code true} for an accepted type
     */
    static boolean isAllowedAttachmentType(String contentType) {
        return isAllowedImageType(contentType)
                || (contentType != null && MfgServices.PDF_TYPE.equals(contentType.trim().toLowerCase(Locale.ROOT)));
    }

    /**
     * Tells whether an owner type is known.
     *
     * @param ownerTypeId the owner type
     * @return {@code true} for a type of {@link MfgServices#MfgServices.ATTACHMENT_OWNER_TYPES}
     */
    static boolean isKnownAttachmentOwnerType(String ownerTypeId) {
        if (ownerTypeId == null) {
            return false;
        }
        for (String type : MfgServices.ATTACHMENT_OWNER_TYPES) {
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
        if (name.endsWith(".pdf")) {
            return MfgServices.PDF_TYPE;
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
        for (GenericValue attachment : attachmentRows(delegator, MfgServices.ATT_PRODUCT, productCode)) {
            existing.add(attachment.getLong("seq"));
        }
        int copied = 0;
        for (GenericValue image : legacy) {
            Long seq = image.getLong("seq");
            if (seq == null || existing.contains(seq)) {
                continue;
            }
            GenericValue attachment = delegator.makeValue("MfgAttachment");
            attachment.set("ownerTypeId", MfgServices.ATT_PRODUCT);
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
        map.put("isImage", Boolean.valueOf(isAllowedImageType(attachment.getString("contentType"))));
        map.put("note", attachment.getString("note"));
        map.put("createdStamp", attachment.get("createdStamp"));
        return map;
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
            return attachmentError("type", "Only JPG, PNG, GIF and PDF files are supported");
        }
        boolean pdf = MfgServices.PDF_TYPE.equals(type);
        long limit = pdf ? MfgServices.PDF_MAX_BYTES : MfgServices.IMAGE_MAX_BYTES;
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
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentSave}
     * @return the service result containing {@code seq}
     */
    public static Map<String, Object> attachmentSave(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context,
                MfgServices.PERM_ENG, MfgServices.PERM_STOCK, MfgServices.PERM_PRODUCTION, MfgServices.PERM_SALES);
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
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentList}
     * @return the service result containing {@code attachments}
     */
    public static Map<String, Object> attachmentList(DispatchContext dctx, Map<String, ? extends Object> context) {
        Delegator delegator = dctx.getDelegator();
        String ownerTypeId = MfgSupport.toText(context.get("ownerTypeId"));
        String ownerId = MfgSupport.toText(context.get("ownerId"));
        List<Map<String, Object>> list = new ArrayList<>();
        if (ownerTypeId != null && ownerId != null) {
            try {
                if (MfgServices.ATT_PRODUCT.equals(ownerTypeId)) {
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
     * @param context service context, see {@code servicedef/services.xml#mfgAttachmentDelete}
     * @return the service result containing the owner keys and {@code seq}
     */
    public static Map<String, Object> attachmentDelete(DispatchContext dctx, Map<String, ? extends Object> context) {
        String denied = MfgServices.permissionProblem(dctx, context,
                MfgServices.PERM_ENG, MfgServices.PERM_STOCK, MfgServices.PERM_PRODUCTION, MfgServices.PERM_SALES);
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
}
