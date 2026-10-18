package com.familywardrobe.app;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** Shared by Gradle/JUnit and the SDK-free runner; exercises the production class. */
public final class PhotoRulesCases {
    private static void equal(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static int[] solid(int color) {
        int[] pixels = new int[256]; Arrays.fill(pixels, color); return pixels;
    }
    public static void knownLabels() {
        for (int label : new int[] {165, 197, 230, 255}) equal("top", PhotoRules.category(Map.of(label, .95f)));
        for (int label : new int[] {19, 51, 249, 390}) equal("bottom", PhotoRules.category(Map.of(label, .95f)));
        for (int label : new int[] {293, 343}) equal("shoes", PhotoRules.category(Map.of(label, .95f)));
    }
    public static void weakAndUnknownLabels() {
        equal(null, PhotoRules.category(Map.of()));
        equal(null, PhotoRules.category(Map.of(197, .69f, 999, .99f)));
        equal(null, PhotoRules.category(Map.of(197, Float.NaN, 293, Float.POSITIVE_INFINITY, 390, 1.1f)));
    }
    public static void ambiguousCategories() {
        equal(null, PhotoRules.category(Map.of(197, .91f, 293, .89f)));
        equal(null, PhotoRules.category(Map.of(197, .90f, 293, .90f)));
        equal("top", PhotoRules.category(Map.of(197, .98f, 293, .72f)));
    }
    public static void sameCategoryLabelsCombine() {
        equal("top", PhotoRules.category(Map.of(197, .94f, 230, .93f, 255, .92f)));
    }
    public static void onePieceGarmentsStayManual() {
        for (int label : new int[] {34, 58, 68, 137, 358}) {
            equal(null, PhotoRules.category(Map.of(197, .90f, label, .85f)));
        }
        equal("top", PhotoRules.category(Map.of(197, .98f, 58, .40f)));
    }
    public static void paletteColors() {
        int[] values = {0xff000000,0xffffffff,0xff808080,0xffe0cba8,0xff142850,0xff805030,0xff666633,0xff2060d0,0xffe02020,0xff20e020};
        String[] names = {"black","white","grey","beige","navy","brown","olive","blue","red","other"};
        for (int i = 0; i < names.length; i++) equal(names[i], PhotoRules.dominantColor(solid(values[i])));
    }
    public static void transparentPixelsDoNotInfluenceColor() {
        int[] pixels = new int[512]; Arrays.fill(pixels, 0x00ffffff);
        Arrays.fill(pixels, 100, 200, 0xffe02020);
        equal("red", PhotoRules.dominantColor(pixels));
    }
    public static void insufficientForegroundHasNoColor() {
        equal(null, PhotoRules.dominantColor(new int[0]));
        equal(null, PhotoRules.dominantColor(solid(0x80ffffff)));
        int[] pixels = new int[31]; Arrays.fill(pixels, 0xffe02020);
        equal(null, PhotoRules.dominantColor(pixels));
    }
    public static void mixedColorsStayMixed() {
        int[] pixels = solid(0xff2060d0); Arrays.fill(pixels, 0, 128, 0xffe02020);
        equal("other", PhotoRules.dominantColor(pixels));
        Arrays.fill(pixels, 0, 220, 0xffe02020);
        equal("red", PhotoRules.dominantColor(pixels));
    }
    public static void emptyOrOpaqueMasksRejected() {
        equal(false, PhotoRules.usableCutout(new int[0]));
        equal(false, PhotoRules.usableCutout(solid(0x00ffffff)));
        equal(false, PhotoRules.usableCutout(solid(0xffffffff)));
    }
    public static void tinyOrNearlyFullMasksRejected() {
        int[] pixels = new int[4096]; Arrays.fill(pixels, 0, 80, 0xffffffff);
        equal(false, PhotoRules.usableCutout(pixels));
        Arrays.fill(pixels, 0xffffffff); Arrays.fill(pixels, 0, 80, 0);
        equal(false, PhotoRules.usableCutout(pixels));
        int[] small = new int[128]; Arrays.fill(small, 0, 63, 0xffffffff);
        equal(false, PhotoRules.usableCutout(small));
    }
    public static void substantialMaskAccepted() {
        int[] pixels = new int[256]; Arrays.fill(pixels, 64, 192, 0xffffffff);
        equal(true, PhotoRules.usableCutout(pixels));
    }
    public static void main(String[] args) {
        knownLabels(); weakAndUnknownLabels(); ambiguousCategories(); sameCategoryLabelsCombine();
        onePieceGarmentsStayManual(); paletteColors(); transparentPixelsDoNotInfluenceColor();
        insufficientForegroundHasNoColor(); mixedColorsStayMixed(); emptyOrOpaqueMasksRejected();
        tinyOrNearlyFullMasksRejected(); substantialMaskAccepted();
        System.out.println("PASS: 12 photo-rule scenarios against production PhotoRules.java. No ML model or Android runtime exercised.");
    }
}
