# Upstream issues in the OFBiz `qrcode` module

**Target**: Apache OFBiz **24.09.07**, `framework/common` (`servicedef/services_qrcode.xml`,
`src/main/java/org/apache/ofbiz/common/qrcode/QRCodeServices.java`, `config/qrcode.properties`).

**Why we looked**: the MES plugin prints outbound labels with a QR code
(`MfgDevBomEvents#labelQr`, see `AGENTS.md` step 247–254). Two things came up: every code came back with
the **OFBiz logo** stamped in the middle (that is `qrcode.default.logoimage`), and the documented
`logoImage` input turned out to be unusable. What follows is written so it can be turned into a pull
request — each item has the exact lines, a reproduction, the observed and the expected behaviour, and a
patch sketch.

Line numbers are from 24.09.07 and may drift; the code excerpts are the authority.

| # | Severity | Where | Effect |
| --- | --- | --- | --- |
| QR-1 | high | `services_qrcode.xml:36` vs `QRCodeServices.java:82,126` | the `logoImage` input cannot be used at all |
| QR-2 | high | `QRCodeServices.java:186–197` | `verifyOutput` returns **success** for an image that no decoder can read |
| QR-3 | medium | `QRCodeServices.java:118–120` | error-correction level is fixed at L, so a logo can make a code unscannable with no way out |
| QR-4 | medium | `QRCodeServices.java:151–171` | the logo is drawn at its native pixel size; a big default logo can eat a small code |
| QR-5 | low | `qrcode.properties:26` vs `QRCodeServices.java:107` | the configured format list is ignored (key typo, works only thanks to the default) |
| QR-6 | low | `QRCodeServices.java:91,98` | `width` and `height` default independently, so one of them silently gives a 200 px side |
| QR-7 | low | `QRCodeServices.java:166–169` | a transparent logo is drawn straight onto the modules (no white matte) |
| QR-8 | nit | `QRCodeServices.java:167` | `AffineTransformOp` is misused as the `drawImage` op, shifting the logo by one pixel |

---

## QR-1 — the `logoImage` input cannot be used

`servicedef/services_qrcode.xml`:

```xml
<attribute name="logoImage" type="java.awt.image.BufferedImage" mode="IN" optional="true"/>
```

`QRCodeServices.java`:

```java
String logoImage = (String) context.get("logoImage");            // line 82
...
logoImageResult = ImageTransform.getBufferedImage(FileUtil.getFile(logoImage).getAbsolutePath(), locale);  // line 126
```

The declaration says *image object*, the implementation wants a *path string*. There is no way to satisfy
both:

* pass a `String` (the natural reading of `FileUtil.getFile(...)`) →
  `org.apache.ofbiz.service.ServiceValidationException: Type check failed for field
  [generateQRCodeImage.logoImage]; expected type is [java.awt.image.BufferedImage]; actual type is
  [java.lang.String]` (thrown by `ModelService`, the service is never reached);
* pass a `BufferedImage` (what the declaration asks for) → `ClassCastException: … BufferedImage cannot be
  cast to java.lang.String` on line 82.

**Reproduction** (24.09.07, any instance):

```java
Map<String, Object> ctx = new HashMap<>();
ctx.put("message", "hello");
ctx.put("width", 300);
ctx.put("height", 300);
ctx.put("format", "png");
ctx.put("logoImage", "component://common-theme/webapp/images/ofbiz.png"); // a String, as line 126 wants
dispatcher.runSync("generateQRCodeImage", ctx, -1, true);
// → ServiceValidationException: Type check failed for field [generateQRCodeImage.logoImage]
```

**Expected**: one type, consistently. The least invasive change is to declare it as `String` (that is what
the implementation reads) and document it as a *resource path*; accepting a `BufferedImage` as well would
mean a small converter or a second attribute.

**Patch sketch**:

```diff
-        <attribute name="logoImage" type="java.awt.image.BufferedImage" mode="IN" optional="true"/>
+        <attribute name="logoImage" type="String" mode="IN" optional="true"/>
```

(and a note in the service description: "resource path of a logo, e.g.
`component://common-theme/webapp/images/ofbiz.png`")

*Our workaround*: we cannot pass the input, so we point `qrcode.default.logoimage` at our own file through
the `SystemProperty` entity (`systemResourceId=qrcode`) from a service started with
`requireNewTransaction` — see `MfgDevBomServices#qrLogoEnsure` in this component. If QR-1 is fixed, that
workaround disappears.

---

## QR-2 — `verifyOutput` accepts an image that cannot be decoded

```java
if (UtilValidate.isNotEmpty(verifyOutput) && verifyOutput) {
    ...
    DecoderResult result = null;
    try {
        detectorResult = new Detector(newBitMatrix).detect(decodeHints);
        result = decoder.decode(detectorResult.getBits(), decodeHints);
    } catch (ChecksumException | FormatException | NotFoundException e) {
        Debug.logError(e, MODULE);                       // line 186: only logged
    }
    if (UtilValidate.isNotEmpty(result) && !result.getText().equals(message)) {
        ...                                             // decode the undecorated matrix and compare
    } else {
        bufferedImage = newBufferedImage;               // line 197: result == null lands here too
    }
}
```

When the decorated image cannot be decoded (exactly the case `verifyOutput` exists for), `result` stays
`null`, the `if` is false, and the **undecodable image is returned as a success**.

**Reproduction** (ours): a 300 px code with a 40 px white logo tile in the middle. The service answers
`success` with a PNG; the instance log contains
`com.google.zxing.ChecksumException` — the image is unreadable for zxing, and it would be for a phone
as well. Nothing in the service result says so, so `if (ServiceUtil.isError(result))` in the caller is
silently useless.

**Expected**: `verifyOutput=true` should mean "the image that is returned can be decoded back to
`message`". If the decorated image fails to decode, return an error (or, if that is considered too
strict, at least set an OUT flag such as `verifyFailed=Y`).

**Patch sketch**:

```diff
     } catch (ChecksumException | FormatException | NotFoundException e) {
-        Debug.logError(e, MODULE);
+        return ServiceUtil.returnError(UtilProperties.getMessage("QRCodeUiLabels",
+                "GeneratedOutputNotDecodable", new Object[]{e.toString()}, locale));
     }
```

---

## QR-3 — the error-correction level is not configurable

```java
Map<EncodeHintType, Object> encodeHints = null;
if (UtilValidate.isNotEmpty(encoding)) {
    encodeHints = new EnumMap<>(EncodeHintType.class);
    encodeHints.put(EncodeHintType.CHARACTER_SET, encoding);       // line 118–120, and nothing else
}
BitMatrix bitMatrix = new MultiFormatWriter().encode(message, BarcodeFormat.QR_CODE, width, height, encodeHints);
```

zxing then uses its default, error correction **L** (~7 % recovery). That is fine for a plain code and
hopeless for one with a logo: at L a centred white tile may cover only about 7 % of the area, which on a
250 px code is a tile of roughly 36 px. There is no way for a caller to ask for Q or H.

**Expected**: an optional attribute, e.g.

```xml
<attribute name="errorCorrectionLevel" type="String" mode="IN" optional="true"/>
```

```java
String ecLevel = (String) context.get("errorCorrectionLevel");
if (UtilValidate.isNotEmpty(ecLevel)) {
    encodeHints = encodeHints == null ? new EnumMap<>(EncodeHintType.class) : encodeHints;
    encodeHints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.valueOf(ecLevel));
}
```

(L for compatibility by default, so existing callers see no change.)

---

## QR-4 — the logo is drawn at its own pixel size

```java
if (UtilValidate.isNotEmpty(logoBufferedImage)) {
    if (UtilValidate.isNotEmpty(logoImageMaxWidth) && UtilValidate.isNotEmpty(logoImageMaxHeight)
            && (logoBufferedImage.getWidth() > logoImageMaxWidth || logoBufferedImage.getHeight() > logoImageMaxHeight)) {
        ...scale...
    }
    ...draw at (w - logo.getWidth()) / 2, (h - logo.getHeight()) / 2...
}
```

Scaling only happens when **both** max values are given, and a caller that does not pass them gets the
default logo at its native pixel size: a 100 px `ofbiz.png` inside a 250 px code covers 40 % of the side
(16 % of the area — more than twice what error correction L can recover), and nothing in the service
result says the sticker will not scan. The maxima do apply to the default logo as well (the code does
not distinguish where the image came from), but an API that asks the caller to set the logo *and* its size
is blocked by QR-1.

**Expected**: a proportional default (for instance at most 15 % of the code's width) when no maximum is
given, and the maximum applied also to the **default** logo, not only to the one from the input.

---

## QR-5 — the configured format list is ignored

`config/qrcode.properties`:

```properties
qrcode.formats.supported=jpg|png|bmp
```

`QRCodeServices.java:107`:

```java
String qrCodeFormatSupported = EntityUtilProperties.getPropertyValue("qrcode", "qrcode.format.supported", "jpg|png|bmp", delegator);
```

`formats` vs `format`: the configured value is never read. Nothing breaks today because the hard-coded
default is the same string, but an operator who edits the property gets no effect (and would file a bug
like this one).

**Patch**: pick one spelling — renaming the property to `qrcode.format.supported` is the smaller change,
since the code already uses it in three places.

---

## QR-6 — `width` and `height` default independently

```java
if (width == null)  { width  = Integer.parseInt(... "qrcode.default.width",  "200", ...); }
if (height == null) { height = Integer.parseInt(... "qrcode.default.height", "200", ...); }
```

A caller that asks for `width=300` and nothing else does not get a 300 px code: the matrix is square, so
zxing fits it into the 300 × 200 canvas and the image comes back with white bars down the sides. The code
itself is 200 px, i.e. what the caller did *not* ask for. **Expected**: the missing side follows the given
one.

---

## QR-7 — no white matte under the logo

```java
Graphics2D graphics = newBufferedImage.createGraphics();
graphics.drawImage(logoBufferedImage, new AffineTransformOp(...), x, y);
```

A PNG with an alpha channel is composited straight over the modules: the code shows *through* the logo,
which looks broken in print, and the "logo area" is not a clean white patch that the error correction can
recover. **Expected**: fill the logo rectangle with white first (or draw an opaque tile).

---

## QR-8 — `AffineTransformOp` misuse (nit)

`new AffineTransformOp(AffineTransform.getTranslateInstance(1, 1), null)` is passed as the
`BufferedImageOp` of `drawImage(Image, BufferedImageOp, int, int)`. The op is applied to the logo *and*
the x/y offsets are applied again by `drawImage`, so the logo lands one pixel off; a plain
`drawImage(logo, x, y, null)` is what is meant.

---

## How our component works around all of this today

* the code is generated by `generateQRCodeImage` (no `logoImage`, QR-1) with `verifyOutput=true`;
* the logo is changed by writing `SystemProperty` (`qrcode` / `qrcode.default.logoimage` →
  `component://mfg/webapp/mfg/images/logo-qr.png`) from a service with `requireNewTransaction`
  (writing it from the request thread left a row lock that made the QR service wait 60 s and return an
  empty sticker);
* the logo tile is **36 × 36 px** and sits on white: measured on our three sticker sizes (400 / 300 / 250
  px codes) that is the largest tile that still decodes at error correction L. 40 px already produces
  `ChecksumException` on the two smaller codes — and because of QR-2 the service still answers `success`,
  so the only way to notice was the log;
* a sweep script (`F:\build-ofbiz\label254\sweep.ps1`) regenerates the tile at several sizes, copies it to
  the deployed component, asks for the three codes and counts `ChecksumException` lines in the log.

With QR-1 … QR-4 fixed the tile could be sized per label (a 20 % logo at error correction H) and none of
these workarounds would be needed.
