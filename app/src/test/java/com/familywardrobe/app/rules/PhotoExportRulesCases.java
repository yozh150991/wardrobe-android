package com.familywardrobe.app.rules;

import java.util.List;

/** PHOTO-17: sizes are never enlarged, JPEG blends onto a real background, names are safe. Run by tools/check_photo_export.py. */
public final class PhotoExportRulesCases {
    private static int checks = 0;
    private static void check(boolean value, String what) { checks++; if (!value) throw new AssertionError("Export check " + checks + ": " + what); }
    private static boolean refused(Runnable r) { try { r.run(); return false; } catch (IllegalArgumentException e) { return true; } }
    private static boolean same(int[] a, int w, int h) { return a[0] == w && a[1] == h; }

    public static void main(String[] args) {
        // Sizes.
        check(same(PhotoExportRules.size(2048, 1536, 0), 2048, 1536), "as saved");
        check(same(PhotoExportRules.size(2048, 1536, 1080), 1080, 810) && same(PhotoExportRules.size(1536, 2048, 1080), 810, 1080), "longest side, both orientations");
        check(same(PhotoExportRules.size(1200, 900, 2048), 1200, 900) && same(PhotoExportRules.size(720, 720, 720), 720, 720), "never enlarged");
        check(same(PhotoExportRules.size(4000, 3, 720), 720, 1) && same(PhotoExportRules.size(3, 4000, 720), 1, 720), "at least one pixel");
        check(same(PhotoExportRules.size(1000, 999, 720), 720, 719), "rounding keeps the ratio");
        for (int w = 1; w <= 3000; w += 37) for (int h = 1; h <= 3000; h += 41) for (int s : PhotoExportRules.SIZES) {
            int[] r = PhotoExportRules.size(w, h, s);
            check(r[0] <= w && r[1] <= h && r[0] >= 1 && r[1] >= 1 && (s == 0 || Math.max(r[0], r[1]) <= s), "within the photo and the size " + w + "×" + h + " " + s);
            check(s != 0 && Math.max(w, h) > s ? Math.max(r[0], r[1]) == s : r[0] == w && r[1] == h, "exactly the chosen size when smaller " + w + "×" + h + " " + s);
        }
        check(PhotoExportRules.offered(2048, 1536).equals(List.of(0, 1080, 720)) && PhotoExportRules.offered(2049, 10).equals(List.of(0, 2048, 1080, 720))
            && PhotoExportRules.offered(700, 500).equals(List.of(0)) && PhotoExportRules.offered(1080, 1080).equals(List.of(0, 720)), "only smaller sizes are offered");
        check(refused(() -> PhotoExportRules.size(0, 10, 0)) && refused(() -> PhotoExportRules.size(10, 10, 1000)) && refused(() -> PhotoExportRules.offered(10, 0)), "invalid input is refused");

        // Formats and backgrounds.
        check(!PhotoExportRules.jpeg("png") && PhotoExportRules.jpeg("white") && PhotoExportRules.jpeg("cream"), "PNG is lossless, the others JPEG");
        check(PhotoExportRules.mime("png").equals("image/png") && PhotoExportRules.mime("cream").equals("image/jpeg")
            && PhotoExportRules.extension("white").equals("jpg") && PhotoExportRules.extension("png").equals("png"), "type and extension");
        check(PhotoExportRules.background("white") == 0xFFFFFFFF && PhotoExportRules.background("cream") == 0xFFF6F0E4, "white and cream backgrounds");
        check(refused(() -> PhotoExportRules.jpeg("gif")) && refused(() -> PhotoExportRules.background(null)), "unknown formats are refused");
        int cream = PhotoExportRules.CREAM_ARGB;
        check(PhotoExportRules.blend(0xFF123456, cream) == 0xFF123456, "an opaque garment pixel keeps its colour");
        check(PhotoExportRules.blend(0x00123456, cream) == cream && PhotoExportRules.blend(0x00000000, 0xFFFFFFFF) == 0xFFFFFFFF, "a transparent pixel becomes the background (never black)");
        check(PhotoExportRules.blend(0x80000000, 0xFFFFFFFF) == 0xFF7F7F7F, "half transparent black on white is mid grey");
        check(PhotoExportRules.blend(0x80010203, 0xFF000000) == 0xFF010102, "channels are rounded, not cut");
        for (int a = 0; a <= 255; a++) {
            int v = PhotoExportRules.blend((a << 24) | 0x00FF0000, 0xFF0000FF);
            check((v >>> 24) == 255 && ((v >> 16) & 255) == Math.round(a * 255 / 255f) && (v & 255) == (int) ((255 * (255 - a) + 127) / 255), "source over, alpha " + a);
        }

        // File names.
        check(PhotoExportRules.fileName("Синя сорочка", "png").equals("синя-сорочка.png"), "Cyrillic letters are kept");
        check(PhotoExportRules.fileName("Koszula / biała: 100% len!", "white").equals("koszula-biała-100-len.jpg"), "punctuation and slashes become dashes");
        check(PhotoExportRules.fileName("../../etc/passwd", "png").equals("etc-passwd.png") && !PhotoExportRules.fileName("a/b\\c", "png").contains("/"), "never a path");
        check(PhotoExportRules.fileName("", "cream").equals("wardrobe-photo.jpg") && PhotoExportRules.fileName("!!!", "png").equals("wardrobe-photo.png")
            && PhotoExportRules.fileName(null, "png").equals("wardrobe-photo.png"), "a name is always left");
        String longName = PhotoExportRules.fileName("a".repeat(59) + " b c", "png");
        check(longName.equals("a".repeat(59) + ".png") && PhotoExportRules.fileName("x".repeat(200), "png").length() == 64, "at most 60 characters, no trailing dash");
        System.out.println("PASS: " + checks + " photo export assertions (sizes, backgrounds, blending, names).");
    }
}
