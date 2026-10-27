import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Prints what the shipped code makes of a real page: the measured block height, how many orange buttons
 * it finds, and where it would cut the picture. Used to check a change of the button detection against
 * the page it was tuned on, without asking the vision service anything.
 *
 * <p>Run it with the classes of an instance on the classpath, see {@code run.bat}.
 */
public final class AnchorAudit {

    private AnchorAudit() {
    }

    /**
     * @param args the picture
     * @throws Exception when the picture cannot be read or the classes are not on the classpath
     */
    public static void main(String[] args) throws Exception {
        BufferedImage image = ImageIO.read(new File(args[0]));
        System.out.println("size " + image.getWidth() + "x" + image.getHeight());
        Class<?> scan = Class.forName("org.langhua.ofbiz.mfg.oir.OirScan");
        int block = ((Integer) scan.getMethod("blockHeightOf", BufferedImage.class)
                .invoke(null, image)).intValue();
        int target = ((Integer) scan.getMethod("sliceTargetOf", BufferedImage.class)
                .invoke(null, image)).intValue();
        @SuppressWarnings("unchecked")
        List<Integer> anchors = (List<Integer>) scan.getMethod("blockAnchors", BufferedImage.class)
                .invoke(null, image);
        int blocks = ((Integer) scan.getMethod("blockCountOf", BufferedImage.class)
                .invoke(null, image)).intValue();
        Method cutsM = scan.getDeclaredMethod("cutsBetweenBlocks", List.class, int.class, int.class);
        cutsM.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Integer> cuts = (List<Integer>) cutsM.invoke(null, anchors,
                Integer.valueOf(target), Integer.valueOf(image.getHeight()));
        System.out.println("blockHeight " + block + " sliceTarget " + target
                + " anchors " + anchors.size() + " slices " + (cuts.size() + 1));
        System.out.println("order blocks the page shows: " + blocks);
        @SuppressWarnings("unchecked")
        List<Integer> list = (List<Integer>) scan.getMethod("listButtonsOf", BufferedImage.class)
                .invoke(null, image);
        System.out.println("buttons of the list: " + list);
        Method insideM = scan.getMethod("blocksInside", List.class, int.class, int.class);
        int top = 0;
        int total = 0;
        for (int i = 0; i <= cuts.size(); i++) {
            int bottom = i < cuts.size() ? cuts.get(i).intValue() : image.getHeight();
            int inside = ((Integer) insideM.invoke(null, list, Integer.valueOf(top),
                    Integer.valueOf(bottom))).intValue();
            total += inside;
            System.out.println("  slice " + i + " rows " + top + ".." + bottom
                    + " shows " + inside + " blocks whole");
            if (bottom >= image.getHeight()) {
                break;
            }
            int next = bottom - 500;
            top = next > top ? next : bottom;
        }
        System.out.println("blocks counted over all slices: " + total);
        System.out.println("anchors " + anchors);
        System.out.println("cuts " + cuts);
    }
}
