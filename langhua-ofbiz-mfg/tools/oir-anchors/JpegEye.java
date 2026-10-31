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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Cuts the same band out of a screenshot twice: once as the page was captured, and once after the whole
 * picture went through the JPEG the scan sends (quality 0.9 on white). Puts the two next to each other,
 * so the digits of an order number can be read at both qualities - the answer to "did the JPEG spoil
 * the number?" without asking the vision service anything.
 */
public final class JpegEye {

    private JpegEye() {
    }

    /**
     * @param args the picture, then x, y, width, height of the band, then the output folder
     * @throws Exception when the picture cannot be read or written
     */
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int width = Integer.parseInt(args[3]);
        int height = Integer.parseInt(args[4]);
        File out = new File(args[5]);
        out.mkdirs();

        ImageIO.write(image.getSubimage(x, y, width, height), "png", new File(out, "band-png.png"));

        // the same encoding the scan uses: white background, JPEG at quality 0.9
        BufferedImage flat = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D canvas = flat.createGraphics();
        canvas.drawImage(image, 0, 0, Color.WHITE, null);
        canvas.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        ImageWriter writer = writers.next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.9f);
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(stream);
            writer.write(null, new javax.imageio.IIOImage(flat, null, null), param);
        }
        writer.dispose();
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
        ImageIO.write(back.getSubimage(x, y, width, height), "png", new File(out, "band-jpeg.png"));

        System.out.println("band " + width + "x" + height + " at " + x + "," + y);
        System.out.println("picture as PNG " + new File(args[0]).length() + " bytes, as JPEG "
                + bytes.size() + " bytes");
        System.out.println("darkest pixel in the band: png=" + darkest(image.getSubimage(x, y, width, height))
                + " jpeg=" + darkest(back.getSubimage(x, y, width, height)));
    }

    private static int darkest(BufferedImage band) {
        int lowest = 255;
        for (int y = 0; y < band.getHeight(); y++) {
            for (int x = 0; x < band.getWidth(); x++) {
                int rgb = band.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                lowest = Math.min(lowest, (r + g + b) / 3);
            }
        }
        return lowest;
    }
}
