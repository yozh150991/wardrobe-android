import com.familywardrobe.app.rules.PhotoCutoutRules;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.FloatBuffer;
import javax.imageio.ImageIO;

/** PHOTO-19: runs the production cutout rules on a capture from a debug build (see MASK-CAPTURE-0.14.63.md).
 * Usage: PhotoStand <folder with *-source.png and *-mask.png> <output folder>
 * Writes <name>-rules.png and prints how the result differs from the phone's <name>-cutout.png, when present.
 */
public final class PhotoStand {
    public static void main(String[] args) throws Exception {
        File in = new File(args[0]), out = new File(args[1]);
        out.mkdirs();
        File[] masks = in.listFiles((d, n) -> n.endsWith("-mask.png"));
        java.util.Arrays.sort(masks);
        for (File maskFile : masks) {
            String name = maskFile.getName().replace("-mask.png", "");
            BufferedImage source = ImageIO.read(new File(in, name + "-source.png")), mask = ImageIO.read(maskFile);
            int w = source.getWidth(), h = source.getHeight();
            int[] pixels = source.getRGB(0, 0, w, h, null, 0, w), levels = mask.getRGB(0, 0, w, h, null, 0, w);
            float[] confidence = new float[w * h];
            for (int i = 0; i < confidence.length; i++) confidence[i] = (((levels[i] >> 16) & 255) * 256 + ((levels[i] >> 8) & 255)) / 65535f;
            long started = System.nanoTime();
            int[] result = PhotoCutoutRules.refine(pixels, w, h, FloatBuffer.wrap(confidence), name.contains("stronger"), () -> {});
            long ms = (System.nanoTime() - started) / 1_000_000;
            BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            image.setRGB(0, 0, w, h, result, 0, w);
            ImageIO.write(image, "png", new File(out, name + "-rules.png"));
            int kept = 0, removed = 0, added = 0;
            File phone = new File(in, name + "-cutout.png");
            int[] before = phone.isFile() ? ImageIO.read(phone).getRGB(0, 0, w, h, null, 0, w) : null;
            for (int i = 0; i < result.length; i++) {
                boolean now = (result[i] >>> 24) >= 128;
                if (now) kept++;
                if (before != null) { boolean was = (before[i] >>> 24) >= 128; if (was && !now) removed++; if (!was && now) added++; }
            }
            System.out.printf("%s %dx%d %d ms kept %.4f; against the phone: removed %.4f, added %.4f%n", name, w, h, ms,
                kept / (double) result.length, removed / (double) result.length, added / (double) result.length);
        }
    }
}
