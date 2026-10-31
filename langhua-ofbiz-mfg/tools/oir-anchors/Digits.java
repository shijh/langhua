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

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Measures the ink of a band of a screenshot: how tall the letters are and how wide a run of digits is.
 * That is what decides whether a 19 digit order number survives the downsizing the vision endpoint does
 * to a picture, and whether there is empty margin that could be cut away first.
 */
public final class Digits {

    private Digits() {
    }

    /**
     * @param args the picture, then x, y, width, height of the band, then the darkest value that counts
     *        as ink (120 by default)
     * @throws Exception when the picture cannot be read
     */
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        int x = Integer.parseInt(args[1]);
        int y = Integer.parseInt(args[2]);
        int width = Integer.parseInt(args[3]);
        int height = Integer.parseInt(args[4]);
        int ink = args.length > 5 ? Integer.parseInt(args[5]) : 120;
        BufferedImage band = image.getSubimage(x, y, width, height);
        int left = Integer.MAX_VALUE;
        int right = -1;
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        int inked = 0;
        for (int row = 0; row < band.getHeight(); row++) {
            for (int column = 0; column < band.getWidth(); column++) {
                int rgb = band.getRGB(column, row);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if ((r + g + b) / 3 <= ink) {
                    inked++;
                    left = Math.min(left, column);
                    right = Math.max(right, column);
                    top = Math.min(top, row);
                    bottom = Math.max(bottom, row);
                }
            }
        }
        System.out.println("band " + width + "x" + height + ", ink pixels " + inked);
        if (inked == 0) {
            System.out.println("no ink: the band is empty (a margin that could be cut away)");
            return;
        }
        System.out.println("ink box x " + left + ".." + right + " (" + (right - left + 1)
                + " wide), y " + top + ".." + bottom + " (" + (bottom - top + 1) + " tall)");
        int digits = 19;
        System.out.println("about " + rounded((right - left + 1) / (double) digits)
                + " px per digit of a " + digits + " digit number");
        for (double scale : new double[] {1.0, 0.457, 0.35}) {
            System.out.println("  scaled to " + rounded(scale) + ": " + rounded((bottom - top + 1) * scale)
                    + " px tall, " + rounded((right - left + 1) * scale / digits) + " px per digit");
        }
    }

    private static String rounded(double value) {
        return String.format("%.1f", Double.valueOf(value));
    }
}
