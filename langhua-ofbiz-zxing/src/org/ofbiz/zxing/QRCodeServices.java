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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.lang.Integer;

import org.ofbiz.base.util.UtilProperties;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.service.DispatchContext;
import org.ofbiz.service.ServiceUtil;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import freemarker.template.utility.StringUtil;

/**
 * Services for QRCode.
 */
public class QRCodeServices {

	public static final String module = QRCodeServices.class.getName();

	public static final String ZXING_SERVICES_TEST_STATUS = UtilProperties
			.getPropertyValue("zxing.properties", "zxing.services.test.status",
					"false");

	public static final String ZXING_SERVICES_TEST_MESSAGE = UtilProperties
			.getPropertyValue("zxing.properties", "zxing.services.test.message");

	public static final String ZXING_SERVICES_QRCODE_DEFAULT_WIDTH = UtilProperties
			.getPropertyValue("zxing.properties",
					"zxing.services.qrcode.default.width", "200");

	public static final String ZXING_SERVICES_QRCODE_DEFAULT_HEIGHT = UtilProperties
			.getPropertyValue("zxing.properties",
					"zxing.services.qrcode.default.height", "200");

	public static final String ZXING_SERVICES_QRCODE_DEFAULT_FORMAT = UtilProperties
			.getPropertyValue("zxing.properties",
					"zxing.services.qrcode.default.format", "jpg");

	public static final String ZXING_SERVICES_QRCODE_FORMAT_SUPPORTED = UtilProperties
			.getPropertyValue("zxing.properties",
					"zxing.services.qrcode.format.supported", "jpg|png");
	
	public static final String[] FORMAT_NAMES = StringUtil.split(ZXING_SERVICES_QRCODE_FORMAT_SUPPORTED, '|');
	
	public static final List<String> FORMAT_SUPPORTED = Arrays.asList(FORMAT_NAMES);

	public static final int MIN_SIZE = 20;

	public static final int MAX_SIZE = 500;

	private static final int BLACK = 0xFF000000;

	private static final int WHITE = 0xFFFFFFFF;

	/** Streams QR Code to the result. */
	public static Map<String, Object> generateQRCodeImage(DispatchContext ctx,
			Map<String, Object> context) {
		Locale locale = (Locale) context.get("locale");
		String message = (String) context.get("message");
		Integer width = (Integer) context.get("width");
		Integer height = (Integer) context.get("height");
		String format = (String) context.get("format");

		if (UtilValidate.isEmpty(message)) {
			if (ZXING_SERVICES_TEST_STATUS.equalsIgnoreCase("true")) {
				message = ZXING_SERVICES_TEST_MESSAGE;
			} else {
				return ServiceUtil.returnError(UtilProperties.getMessage(
						"ZXingUiLabels", "ParameterCannotEmpty",
						new Object[] { "message" }, locale));
			}
		}
		if (width == null) {
			width = Integer.parseInt(ZXING_SERVICES_QRCODE_DEFAULT_WIDTH);
		}
		if (width.intValue() < MIN_SIZE || width.intValue() > MAX_SIZE) {
			return ServiceUtil
					.returnError(UtilProperties.getMessage(
							"ZXingUiLabels",
							"SizeOutOfBorderError",
							new Object[] { "width", String.valueOf(width),
									String.valueOf(MIN_SIZE),
									String.valueOf(MAX_SIZE) }, locale));
		}
		if (height == null) {
			height = Integer.parseInt(ZXING_SERVICES_QRCODE_DEFAULT_HEIGHT);
		}
		if (height.intValue() < MIN_SIZE || height.intValue() > MAX_SIZE) {
			return ServiceUtil
					.returnError(UtilProperties.getMessage(
							"ZXingUiLabels",
							"SizeOutOfBorderError",
							new Object[] { "height", String.valueOf(height),
									String.valueOf(MIN_SIZE),
									String.valueOf(MAX_SIZE) }, locale));
		}
		if (UtilValidate.isEmpty(format)) {
			format = ZXING_SERVICES_QRCODE_DEFAULT_FORMAT;
		}
		if (!FORMAT_SUPPORTED.contains(format)) {
			return ServiceUtil
					.returnError(UtilProperties.getMessage(
							"ZXingUiLabels",
							"ErrorFormatNotSupported",
							new Object[] { format }, locale));
		}

		try {
			BitMatrix bitMatrix = new MultiFormatWriter().encode(message,
					BarcodeFormat.QR_CODE, width, height);
			BufferedImage bufferedImage = toBufferedImage(bitMatrix, format);
			Map<String, Object> result = ServiceUtil.returnSuccess();
			result.put("bufferedImage", bufferedImage);
			return result;
		} catch (WriterException e) {
			return ServiceUtil.returnError(UtilProperties.getMessage(
					"ZXingUiLabels", "ErrorGenerateQRCode",
					new Object[] { e.getMessage() }, locale));
		}
	}

	/**
	 * Renders a {@link BitMatrix} as an image, where "false" bits are rendered
	 * as white, and "true" bits are rendered as black.
	 * 
	 * This is to replace MatrixToImageWriter.toBufferedImage(bitMatrix) if you
	 * find the output image is not right, you can change BufferedImage image =
	 * new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); to
	 * BufferedImage image = new BufferedImage(width, height,
	 * BufferedImage.TYPE_INT_RGB); or others to make it work correctly.
	 */
	private static BufferedImage toBufferedImage(BitMatrix matrix, String format) {
		int width = matrix.getWidth();
		int height = matrix.getHeight();
		BufferedImage image = null;
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.startsWith("mac os")) {
			if (format.equals("jpg")) {
				image = new BufferedImage(width, height,
						BufferedImage.TYPE_INT_RGB);
			} else {
				image = new BufferedImage(width, height,
						BufferedImage.TYPE_INT_ARGB);
			}
		} else if (osName.startsWith("windows")) {
			image = new BufferedImage(width, height,
					BufferedImage.TYPE_INT_RGB);
		} else {
			image = new BufferedImage(width, height,
					BufferedImage.TYPE_INT_RGB);
		}
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				image.setRGB(x, y, matrix.get(x, y) ? BLACK : WHITE);
			}
		}
		return image;
	}
}
