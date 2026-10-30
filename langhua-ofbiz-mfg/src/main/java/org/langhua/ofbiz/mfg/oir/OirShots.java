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

package org.langhua.ofbiz.mfg.oir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

/**
 * The product pictures of a shop order page, cut out of the screenshot.
 *
 * <p>Why: a receipt line says "0603 10nF x100, 0.29" and nothing else; the picture the shop printed
 * next to it is what tells a person "yes, that is the reel I meant". The picture is only in the
 * screenshot, and the vision service answers text, not coordinates, so it has to be found in the
 * pixels.
 *
 * <p>How: a product photo is colourful, the words around it are grey on white. So every row of the left
 * part of the page is counted by how many saturated pixels it holds, and the runs of rows that hold many
 * are the pictures. Prices are printed in the shop's orange and are colourful too, but they are one line
 * of text high, and the pictures of a page all start in the same column - so the second pass keeps only
 * the runs that start where most of the tall ones start, and measures them inside that column alone.
 * That is the whole trick: it does not depend on the shop's markup, the zoom or the theme, only on "a
 * photo has colour, and the pictures line up".
 */
public final class OirShots {

    /** How colourful a pixel must be to count as part of a photo (max channel - min channel). */
    static final int COLOUR_SPREAD = 40;

    /**
     * From which pixel value a row is the part photographed on a card and not the page itself.
     *
     * <p>Measured on a real page: an empty row is 255, a product card is 250, and the pale band an order
     * prints its header in is 235,231,215 - which sits between the two and is what this line has to
     * separate. Below 220 the band stays out and the part on the card counts; a photograph of white
     * connectors on a white card has nothing that dark, so that block is left without a picture and the
     * receipt line keeps the whole screenshot instead.
     */
    static final int PART_DARKEST = 220;

    /** The smallest side a product picture can have: smaller ones are icons and badges. */
    static final int MIN_SIDE = 90;

    /** How many pixels of a row must count before the row counts as part of a picture. */
    static final int MIN_ROW_PIXELS = 12;

    /** How much of the page width (from the left) is searched for pictures. */
    static final double SEARCH_SHARE = 0.45;

    /**
     * How wide the strip right of the picture column is searched in the second pass. Wider than one
     * picture, narrow enough that the title and the price of the row stay out of it.
     */
    static final int COLUMN_BAND = 340;

    /**
     * How far two left edges may differ and still be the same column. The pictures of one page all
     * start at the same place, whatever the zoom is.
     */
    static final int COLUMN_TOLERANCE = 60;

    /**
     * How far right of the column the content of a picture may start.
     *
     * <p>Only the left edge of a card is fixed; the photograph inside it is not. On the audited page the
     * cards start at 680, a picture that fills its card starts at once 690, and a black connector in the
     * middle of a white card starts at 806 - while the words of the row start around 1000. A narrower
     * reach dropped that first order (it was the one picture of 26 that never appeared).
     */
    static final int COLUMN_REACH = 220;

    /** The smallest box a picture is cut with: a card is about 250 wide, plus the margin on its left. */
    static final int MIN_PICTURE_SIDE = 300;

    /** Why a picture is taken a little larger than the colourful pixels inside it. */
    static final int PICTURE_MARGIN = 20;

    /**
     * How far right of the cards the words of a row are looked for.
     *
     * <p>The card is not always the same size: the same shop prints 250 wide cards at one zoom and 205 at
     * another, while the page stays 3434 wide. A fixed box then reaches into the title, which is what a
     * reader saw on a zoomed out screenshot (the first two characters of the title were inside the
     * picture). So the box is stopped where the words begin, and where they begin is measured.
     */
    static final int TEXT_SEARCH = 640;

    /** How many blank columns separate the card from the words of the row, at any zoom. */
    static final int TEXT_GAP = 12;

    /** How many blank columns may sit inside one photograph: the white stripes of a card. */
    static final int PHOTO_GAP = 4;

    /** How many rows of white a box leaves before the picture above or below it. */
    static final int ROW_GAP = 4;

    /**
     * How many blank rows may sit inside one picture. A photo of a part on a white card has bands of
     * white through it, and those bands must not cut it into two. A block that prints two articles
     * stacks their cards further apart than this, so two lines keep two pictures.
     */
    static final int MERGE_GAP = 40;

    private OirShots() {
    }

    /**
     * The boxes the product pictures of a page sit in.
     *
     * <p>Two passes over the whole picture. The first one looks at the left part of it and collects
     * every colourful run that is tall enough to be a picture, with its left edge; the edge most of
     * them share is the column the shop prints the pictures in. Prices are printed in the shop's
     * orange and are colourful too, but they are one line of text high, and an advertisement is tall
     * but does not start where the pictures start. The second pass measures again inside that column
     * only, so the title and the price of the row cannot stretch a picture sideways.
     *
     * <p>The column is measured on the whole page and not block by block: an order of one article
     * prints one picture, and one picture on its own says nothing about a column.
     *
     * @param page the screenshot
     * @return the boxes, top to bottom; empty when the page shows no picture
     */
    public static List<int[]> boxesOf(BufferedImage page) {
        if (page == null) {
            return Collections.emptyList();
        }
        int from = 0;
        int to = page.getHeight();
        int width = page.getWidth();
        int column = pictureColumn(runsOf(page, from, to, 0, (int) (width * SEARCH_SHARE), Mode.COLOUR));
        if (column < 0) {
            return Collections.emptyList();
        }
        List<int[]> boxes = new ArrayList<>();
        int xFrom = Math.max(0, column - PICTURE_MARGIN / 2);
        int xTo = Math.min(width, column + COLUMN_BAND);
        List<int[]> pictures = new ArrayList<>();
        for (int[] run : runsOf(page, from, to, xFrom, xTo, Mode.PICTURE)) {
            // the picture has to sit at the column the page prints its cards in - but only its left
            // edge is fixed: a photo of a part in the middle of a white card starts later than one of a
            // card whose picture fills it, so the reach is generous. Words of the row start further
            // right than the reach, so they stay out
            if (run[0] >= column - COLUMN_TOLERANCE && run[0] <= column + COLUMN_REACH) {
                pictures.add(run);
            }
        }
        // how far a box may reach: up to where this page prints the words of a row. Measured, because
        // the same shop prints smaller cards when the browser is zoomed out
        int reach = wordsStartOf(page, pictures, column);
        for (int i = 0; i < pictures.size(); i++) {
            int[] run = pictures.get(i);
            // and how tall it may be: the box is a square, so its height has to fit between the picture
            // before it and the picture after it - a box that grows into the next card shows two
            // products, and one that grows up into the band of its block shows the order number
            int above = run[1] - (i == 0 ? 0 : pictures.get(i - 1)[3]);
            int below = (i + 1 == pictures.size() ? page.getHeight() : pictures.get(i + 1)[1]) - run[3];
            boxes.add(boxAround(run, column, page, reach, Math.min(above, below)));
        }
        return boxes;
    }

    /**
     * Whether two pictures are printed side by side, on (nearly) the same rows.
     *
     * <p>An order prints its articles one under the other, so a picture of an article never shares its
     * rows with another one. The page's 猜你喜欢 grid prints three or four cards in a row, and those do:
     * that is what tells the feed of advertisements from the articles (Step 179q).
     *
     * @param one one box
     * @param other another box
     * @return {@code true} when more than half of the shorter box lies on the rows of the other
     */
    public static boolean sharesRows(int[] one, int[] other) {
        if (one == null || other == null) {
            return false;
        }
        int top = Math.max(one[1], other[1]);
        int bottom = Math.min(one[3], other[3]);
        int shorter = Math.min(one[3] - one[1], other[3] - other[1]);
        return shorter > 0 && (bottom - top) > shorter / 2;
    }

    /**
     * The first row of the page's strip of cards printed side by side, or
     * {@link Integer#MAX_VALUE} when every picture stands on its own.
     *
     * @param boxes the boxes of the page, top to bottom
     * @return the row the strip starts on
     */
    public static int stripStartOf(List<int[]> boxes) {
        if (boxes == null) {
            return Integer.MAX_VALUE;
        }
        for (int i = 0; i + 1 < boxes.size(); i++) {
            if (sharesRows(boxes.get(i), boxes.get(i + 1))) {
                return Math.min(boxes.get(i)[1], boxes.get(i + 1)[1]);
            }
        }
        return Integer.MAX_VALUE;
    }

    /**
     * The boxes whose middle falls between two rows: how the pictures are handed to the order blocks
     * of a page, which are known by the rows they print between.
     *
     * @param boxes the boxes of the whole page, as {@link #boxesOf(BufferedImage)} returns them
     * @param top the first row
     * @param bottom the row after the last one
     * @return the places of those boxes in the list, top to bottom
     */
    public static List<Integer> indexesWithin(List<int[]> boxes, int top, int bottom) {
        List<Integer> inside = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            int[] box = boxes.get(i);
            int middle = (box[1] + box[3]) / 2;
            if (middle >= top && middle < bottom) {
                inside.add(Integer.valueOf(i));
            }
        }
        return inside;
    }

    /**
     * One picture, cut out and scaled down to a square thumbnail: what the receipt line shows next to
     * the part number, so a person can see at a glance that this is the reel they meant.
     *
     * @param page the screenshot
     * @param box the box of the picture
     * @param side the side of the thumbnail in pixels
     * @return the bytes of a small JPEG, {@code null} when it could not be made
     */
    public static byte[] thumbnailOf(BufferedImage page, int[] box, int side) {
        try {
            int[] safe = clamp(page, box);
            BufferedImage shot = page.getSubimage(safe[0], safe[1], safe[2] - safe[0],
                    safe[3] - safe[1]);
            BufferedImage small = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = small.createGraphics();
            // white first: a JPEG has no transparency, and what is not drawn would come out black
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, side, side);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(shot, 0, 0, side, side, null);
            graphics.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(small, "jpeg", out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * The runs of rows that hold enough of a picture to be one, between {@code xFrom} and {@code xTo}.
     *
     * <p>Every second column is sampled: at four megabytes of pixels a full scan of a long screenshot
     * costs seconds, and a picture is wider than two pixels.
     *
     * @param mode how a row is measured: {@link Mode#COLOUR} finds the column on the whole page,
     *        {@link Mode#PICTURE} measures a picture inside that column - there the pale card the photo
     *        is printed on has to count too, because a photograph of white connectors on a white card
     *        has no colour and no dark pixel anywhere in its middle.
     */
    private static List<int[]> runsOf(BufferedImage page, int from, int to, int xFrom, int xTo,
            Mode mode) {
        int rows = to - from;
        int[] colourful = new int[rows];
        int[] left = new int[rows];
        int[] right = new int[rows];
        for (int y = from; y < to; y++) {
            int count = 0;
            int minX = Integer.MAX_VALUE;
            int maxX = -1;
            for (int x = xFrom; x < xTo; x += 2) {
                int rgb = page.getRGB(x, y);
                boolean hit = colourful(rgb) || (mode == Mode.PICTURE && part(rgb));
                if (hit) {
                    count++;
                    if (x < minX) {
                        minX = x;
                    }
                    maxX = x;
                }
            }
            colourful[y - from] = count;
            left[y - from] = minX == Integer.MAX_VALUE ? -1 : minX;
            right[y - from] = maxX;
        }
        // the bands of colour, then the bands a small gap apart put back together: a picture is one
        // thing even when the card it was shot on has white stripes through it
        List<int[]> runs = new ArrayList<>();
        int runStart = -1;
        int lastEnd = -1;
        for (int i = 0; i <= rows; i++) {
            boolean inRun = i < rows && colourful[i] >= MIN_ROW_PIXELS;
            if (inRun && runStart < 0) {
                runStart = i;
            } else if (!inRun && runStart >= 0) {
                boolean near = !runs.isEmpty() && runStart + from - lastEnd <= MERGE_GAP;
                int[] run = runOf(left, right, runStart, i, from);
                if (run != null && near) {
                    int[] previous = runs.get(runs.size() - 1);
                    runs.set(runs.size() - 1, new int[] {Math.min(previous[0], run[0]), previous[1],
                            Math.max(previous[2], run[2]), run[3]});
                } else if (run != null) {
                    runs.add(run);
                }
                lastEnd = run == null ? lastEnd : run[3];
                runStart = -1;
            }
        }
        List<int[]> pictures = new ArrayList<>();
        for (int[] run : runs) {
            if (run[3] - run[1] >= MIN_SIDE && run[2] - run[0] >= MIN_SIDE / 2) {
                pictures.add(run);
            }
        }
        return pictures;
    }

    /** The box of one run of colourful rows, or null when no colourful pixel is in it. */
    private static int[] runOf(int[] left, int[] right, int start, int end, int rowOffset) {
        int minX = Integer.MAX_VALUE;
        int maxX = -1;
        for (int i = start; i < end; i++) {
            if (left[i] >= 0) {
                minX = Math.min(minX, left[i]);
                maxX = Math.max(maxX, right[i]);
            }
        }
        return maxX < 0 ? null : new int[] {minX, rowOffset + start, maxX, rowOffset + end};
    }

    /**
     * The column the pictures start in: the left edge most of the tall runs share, or -1 when there
     * are too few to tell (one colourful thing is not a column).
     */
    private static int pictureColumn(List<int[]> runs) {
        if (runs.size() < 2) {
            return -1;
        }
        Map<Integer, Integer> edges = new TreeMap<>();
        for (int[] run : runs) {
            Integer seen = edges.get(Integer.valueOf(run[0] / 20));
            edges.put(Integer.valueOf(run[0] / 20), Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        }
        int bestBucket = -1;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : edges.entrySet()) {
            if (entry.getValue().intValue() > bestCount) {
                bestCount = entry.getValue().intValue();
                bestBucket = entry.getKey().intValue();
            }
        }
        // a column needs more than one picture in it to be a column
        return bestCount < 2 ? -1 : bestBucket * 20;
    }

    /**
     * The box of one picture: a square that starts where the cards start, is as tall as what was found,
     * and stays inside the room this picture has.
     *
     * <p>Anchored on the column and not on the content: the run of a picture can reach into the words of
     * the row (the first letters of the title are inside the strip), and a box centred on it drifts right
     * and cuts the card in half.
     *
     * @param run the rows of the picture, as the second pass measured them
     * @param column the left edge the page prints its cards at
     * @param page the screenshot, only read for its size
     * @param reach how far right the box may go before it would take in the words of the row, or
     *        {@link Integer#MAX_VALUE} when the page did not tell
     * @param room how many blank rows the picture has before the next thing above or below it
     */
    private static int[] boxAround(int[] run, int column, BufferedImage page, int reach, int room) {
        int height = run[3] - run[1];
        int wanted = Math.max(height + PICTURE_MARGIN * 2, MIN_PICTURE_SIDE);
        // never wider than the distance to the words of the row. What has to be in the box is the
        // photograph, and a photo of a long connector is taller than it is wide: taking its height as
        // the floor is what dragged the first characters of the title into the thumbnail of a reader's
        // page (Step 182), so the words are the hard limit and the photo loses a hair of its tips
        int side = Math.min(wanted, reach);
        // never taller than the room between the neighbouring pictures, less a hair of white so two
        // boxes never touch - but not so short that the photograph is cut below what the words allow
        int fits = height + Math.max(0, room) * 2 - ROW_GAP;
        side = Math.max(Math.min(side, fits), Math.min(height, Math.max(1, reach)));
        // left edge stays on the card: a narrower box takes the white right of the photograph and not a
        // slice of the photograph itself
        int x0 = Math.max(0, column - PICTURE_MARGIN / 2);
        int y0 = Math.max(0, (run[1] + run[3]) / 2 - side / 2);
        return new int[] {x0, y0,
                Math.min(page.getWidth(), x0 + side), Math.min(page.getHeight(), y0 + side)};
    }

    /**
     * How far right of the cards a picture box may reach on this page: the distance to the first column
     * that holds the words of a row.
     *
     * <p>The card is white and so is the space right of it, so the words are found as "a block of content
     * after a gutter": walk right from the card, let small white stripes pass (a photograph printed on a
     * card has them), and the first block that starts after {@link #TEXT_GAP} blank columns is the title.
     * The middle over the pictures of the page keeps one odd row from moving the whole page.
     *
     * @param page the screenshot
     * @param runs the pictures of the page
     * @param column the left edge the page prints its cards at
     * @return the distance, or {@link Integer#MAX_VALUE} when fewer than two pictures show where the
     *         words begin (one picture says nothing about a layout)
     */
    private static int wordsStartOf(BufferedImage page, List<int[]> runs, int column) {
        List<Integer> distances = new ArrayList<>();
        for (int[] run : runs) {
            int start = wordsStartIn(page, run, column);
            if (start > 0) {
                distances.add(Integer.valueOf(start - column));
            }
        }
        if (distances.size() < 2) {
            return Integer.MAX_VALUE;
        }
        Collections.sort(distances);
        return distances.get(distances.size() / 2).intValue();
    }

    /**
     * Where the words of one row start, or -1 when no block follows the picture.
     *
     * @param page the screenshot
     * @param run the rows of one picture
     * @param column the left edge the page prints its cards at
     */
    private static int wordsStartIn(BufferedImage page, int[] run, int column) {
        int xTo = Math.min(page.getWidth(), column + TEXT_SEARCH);
        boolean seenPicture = false;
        int blanks = 0;
        for (int x = Math.max(0, column); x < xTo; x++) {
            boolean content = false;
            // every second row is enough: a letter is many rows tall, a gutter many columns wide
            for (int y = run[1]; y < run[3] && !content; y += 2) {
                content = part(page.getRGB(x, y));
            }
            if (content) {
                if (seenPicture && blanks >= TEXT_GAP) {
                    return x;
                }
                seenPicture = true;
                blanks = 0;
            } else if (blanks < TEXT_GAP + PHOTO_GAP) {
                blanks++;
            }
        }
        return -1;
    }

    /** Whether one pixel is colourful enough to belong to a photo. */
    static boolean colourful(int rgb) {
        Color colour = new Color(rgb);
        return spread(colour) >= COLOUR_SPREAD;
    }

    /** Whether one pixel is dark or colourful enough to be the part photographed on the card. */
    static boolean part(int rgb) {
        Color colour = new Color(rgb);
        int max = Math.max(colour.getRed(), Math.max(colour.getGreen(), colour.getBlue()));
        return spread(colour) >= COLOUR_SPREAD || max < PART_DARKEST;
    }

    /** How far the channels of one pixel are apart. */
    private static int spread(Color colour) {
        int max = Math.max(colour.getRed(), Math.max(colour.getGreen(), colour.getBlue()));
        int min = Math.min(colour.getRed(), Math.min(colour.getGreen(), colour.getBlue()));
        return max - min;
    }

    /**
     * How a row of the page is measured: by its colour (finding the column the pictures are printed in),
     * or by the card a picture is printed on and the part photographed inside it.
     */
    private enum Mode { COLOUR, PICTURE }

    /**
     * Cuts the boxes out of the page.
     *
     * @param page the screenshot
     * @param boxes the boxes, as {@link #boxesOf} returns them
     * @return one picture per box, in the same order
     */
    public static List<BufferedImage> shotsOf(BufferedImage page, List<int[]> boxes) {
        List<BufferedImage> shots = new ArrayList<>(boxes.size());
        for (int[] box : boxes) {
            int[] safe = clamp(page, box);
            shots.add(page.getSubimage(safe[0], safe[1], safe[2] - safe[0], safe[3] - safe[1]));
        }
        return shots;
    }

    /** The box, moved inside the page. */
    private static int[] clamp(BufferedImage page, int[] box) {
        int x0 = Math.max(0, Math.min(box[0], page.getWidth() - 1));
        int y0 = Math.max(0, Math.min(box[1], page.getHeight() - 1));
        int x1 = Math.max(x0 + 1, Math.min(box[2], page.getWidth()));
        int y1 = Math.max(y0 + 1, Math.min(box[3], page.getHeight()));
        return new int[] {x0, y0, x1, y1};
    }
}
