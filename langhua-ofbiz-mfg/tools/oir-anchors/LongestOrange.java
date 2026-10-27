import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Prints every row of a page that carries a long unbroken band of the shop orange, with the width of
 * that band and where it starts. A "再买一单" button is a solid band about 170 px wide at the right
 * edge of its block; the orange words a block prints in its header ({@code 订单详情 >},
 * {@code 交易成功}, the coupon strip) are strokes a few pixels wide spread over the whole row - which
 * is why counting all the orange of a row found 32 buttons on a page of 23 orders.
 *
 * <p>This one needs nothing but a JDK, no instance classes.
 */
public final class LongestOrange {

    private LongestOrange() {
    }

    /**
     * @param args the picture, then optionally the shortest band in pixels that counts as a button
     * @throws Exception when the picture cannot be read
     */
    public static void main(String[] args) throws Exception {
        File file = new File(args[0]);
        int least = args.length > 1 ? Integer.parseInt(args[1]) : 85;
        BufferedImage image = ImageIO.read(file);
        int step = Math.max(2, image.getWidth() / 200);
        System.out.println(file.getName() + " " + image.getWidth() + "x" + image.getHeight()
                + " step " + step + " least " + least);
        List<int[]> runs = new ArrayList<>();
        int runStart = -1;
        int longest = 0;
        int at = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            int run = 0;
            int best = 0;
            int bestAt = -1;
            for (int x = 0; x < image.getWidth(); x += step) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r >= 200 && g >= 50 && g <= 140 && b <= 90) {
                    run++;
                    if (run > best) {
                        best = run;
                        bestAt = x - (run - 1) * step;
                    }
                } else {
                    run = 0;
                }
            }
            boolean hot = best * step >= least;
            if (hot) {
                if (runStart < 0) {
                    runStart = y;
                    longest = 0;
                    at = -1;
                }
                if (best > longest) {
                    longest = best;
                    at = bestAt;
                }
            } else if (runStart >= 0) {
                if (y - runStart >= 8) {
                    runs.add(new int[] {runStart, y - runStart, longest * step, at});
                }
                runStart = -1;
            }
        }
        List<Integer> anchors = new ArrayList<>();
        for (int[] run : runs) {
            if (!anchors.isEmpty() && run[0] - anchors.get(anchors.size() - 1).intValue() < 90) {
                continue;
            }
            anchors.add(Integer.valueOf(run[0]));
        }
        for (int[] run : runs) {
            System.out.println("  y=" + run[0] + " h=" + run[1] + " band=" + run[2] + " at=" + run[3]);
        }
        StringBuilder line = new StringBuilder("anchors " + anchors.size() + ":");
        int previous = -1;
        for (Integer anchor : anchors) {
            line.append(' ').append(anchor);
            if (previous >= 0) {
                line.append("(+").append(anchor.intValue() - previous).append(')');
            }
            previous = anchor.intValue();
        }
        System.out.println(line);
    }
}
