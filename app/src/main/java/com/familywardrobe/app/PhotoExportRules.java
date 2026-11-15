package com.familywardrobe.app;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * PHOTO-17 (0.14.46): a garment photo shared as a file, apart from the backgrounds the app only draws behind it.
 *
 * <ul>
 *   <li>{@link #PNG}: lossless; a cut-out photo keeps its transparency.</li>
 *   <li>{@link #WHITE} and {@link #CREAM}: JPEG on a real background — transparent pixels are blended onto it, so the
 *   file looks the same in any app (many ignore transparency or show it black).</li>
 * </ul>
 * Sizes are the longest side; a photo is never enlarged, so «2048» of a 1200 px photo stays 1200 px. The file has no
 * EXIF or other metadata: it is encoded from pixels only.
 */
public final class PhotoExportRules {
    private PhotoExportRules() {}
    public static final String PNG = "png", WHITE = "white", CREAM = "cream";
    public static final List<String> FORMATS = List.of(PNG, WHITE, CREAM);
    /** 0 — the saved size; otherwise the longest side in pixels. */
    public static final List<Integer> SIZES = List.of(0, 2048, 1080, 720);
    /** sRGB, opaque. Cream matches the viewer's warm backdrop closely enough to read as «кремовий», not as grey. */
    public static final int WHITE_ARGB = 0xFFFFFFFF, CREAM_ARGB = 0xFFF6F0E4;
    public static final int JPEG_QUALITY = 92;
    public static final int MAX_NAME = 60;

    public static boolean jpeg(String format) { check(format); return !format.equals(PNG); }
    public static int background(String format) {
        check(format);
        return format.equals(WHITE) ? WHITE_ARGB : format.equals(CREAM) ? CREAM_ARGB : 0;
    }
    public static String mime(String format) { return jpeg(format) ? "image/jpeg" : "image/png"; }
    public static String extension(String format) { return jpeg(format) ? "jpg" : "png"; }
    private static void check(String format) { if (format == null || !FORMATS.contains(format)) throw new IllegalArgumentException(); }

    /** The exported width and height for a saved photo of [width]×[height] and a [size] from {@link #SIZES}. */
    public static int[] size(int width, int height, int size) {
        if (width < 1 || height < 1 || !SIZES.contains(size)) throw new IllegalArgumentException();
        int longest = Math.max(width, height);
        if (size == 0 || longest <= size) return new int[] { width, height };
        double scale = (double) size / longest;
        int w = Math.max(1, (int) Math.round(width * scale)), h = Math.max(1, (int) Math.round(height * scale));
        if (width >= height) w = size; else h = size;
        return new int[] { w, h };
    }
    /** Sizes worth offering: the saved size and every smaller one; a size not smaller than the photo is the same file. */
    public static List<Integer> offered(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException();
        int longest = Math.max(width, height);
        List<Integer> result = new ArrayList<>();
        for (int s : SIZES) if (s == 0 || s < longest) result.add(s);
        return result;
    }

    /**
     * One opaque pixel of a JPEG: [argb] (straight, not premultiplied alpha) over the [background] — "source over".
     * The colour of the garment itself is never changed where it is opaque.
     */
    public static int blend(int argb, int background) {
        int a = argb >>> 24;
        if (a == 255) return argb | 0xFF000000;
        int r = mix((argb >> 16) & 255, (background >> 16) & 255, a);
        int g = mix((argb >> 8) & 255, (background >> 8) & 255, a);
        int b = mix(argb & 255, background & 255, a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    private static int mix(int top, int bottom, int alpha) { return (top * alpha + bottom * (255 - alpha) + 127) / 255; }

    /**
     * A file name from the garment's name: letters (any script) and digits kept, the rest becomes "-", at most
     * {@link #MAX_NAME} characters; "wardrobe-photo" when nothing is left. Never a path.
     */
    public static String fileName(String title, String format) {
        String base = Normalizer.normalize(title == null ? "" : title, Normalizer.Form.NFC).toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        if (base.length() > MAX_NAME) base = base.substring(0, MAX_NAME).replaceAll("-+$", "");
        if (base.isEmpty()) base = "wardrobe-photo";
        return base + "." + extension(format);
    }
}
