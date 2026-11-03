package com.familywardrobe.app;

import java.util.*;

/**
 * STYLE-01/02: a transparent outfit rating (0–10) and the colour-pair table behind «Добре → Краще». Every point
 * comes from a named rule, so the screen can say why; nothing here is learned or hidden.
 *
 * Colours are the app's ten base colours (a custom shade uses its base). Each has a lightness and a temperature:
 * a neutral warm + cool pair (grey with brown, navy with beige) is the richest, light with dark is a classic contrast,
 * one bright accent on a neutral base is safe, two bright accents compete, black with brown and black with navy blur.
 */
public final class StyleRules {
    private StyleRules() {}

    // Pair codes, best first; their points are in [pairPoints].
    public static final String WARM_COOL = "warm_cool";
    public static final String CONTRAST = "contrast";
    public static final String ACCENT = "accent";
    public static final String CALM = "calm";
    public static final String TONAL = "tonal";
    public static final String UNKNOWN = "unknown";
    public static final String SAME_ACCENT = "same_accent";
    public static final String BLACK_BROWN = "black_brown";
    public static final String DARK_DARK = "dark_dark";
    public static final String TWO_ACCENTS = "two_accents";

    // Weather codes.
    public static final String WEATHER_OK = "ok";
    public static final String NO_OUTERWEAR = "no_outerwear";
    public static final String OUT_OF_SEASON = "season";

    // Labels by score.
    public static final String GREAT = "great";
    public static final String GOOD = "good";
    public static final String FAIR = "fair";
    public static final String WEAK = "weak";

    private static final int LIGHT = 0, MID = 1, DARK = 2;
    private static final int NEUTRAL = 0, WARM = 1, COOL = 2;
    /** {base neutral (1) or bright accent (0), lightness, temperature}. Olive is an earthy neutral. */
    private static final Map<String, int[]> COLOURS = new HashMap<>();
    static {
        COLOURS.put("white", new int[] {1, LIGHT, NEUTRAL});
        COLOURS.put("black", new int[] {1, DARK, NEUTRAL});
        COLOURS.put("grey", new int[] {1, MID, COOL});
        COLOURS.put("beige", new int[] {1, LIGHT, WARM});
        COLOURS.put("navy", new int[] {1, DARK, COOL});
        COLOURS.put("brown", new int[] {1, DARK, WARM});
        COLOURS.put("olive", new int[] {1, MID, WARM});
        COLOURS.put("blue", new int[] {0, MID, COOL});
        COLOURS.put("red", new int[] {0, MID, WARM});
    }
    /** Small pieces do not decide the colours or the style of an outfit. */
    private static final Set<String> SMALL = Set.of("jewelry", "accessory");
    /** The two biggest pieces make the main colour pair. */
    private static final List<String> MAIN = List.of("top", "dress", "jumpsuit", "bottom", "layer", "outerwear", "shoes", "bag", "hat", "tights", "other_item");

    public static int pairPoints(String code) {
        switch (code) {
            case WARM_COOL: return 4;
            case CONTRAST: case ACCENT: case CALM: return 3;
            case TONAL: case UNKNOWN: return 2;
            case SAME_ACCENT: case BLACK_BROWN: case DARK_DARK: return 1;
            case TWO_ACCENTS: return 0;
            default: throw new IllegalArgumentException(code);
        }
    }

    /** How two base colours work together; symmetric. */
    public static String pair(String a, String b) {
        int[] x = COLOURS.get(a), y = COLOURS.get(b);
        if (x == null || y == null) return UNKNOWN;
        if (a.equals(b)) return x[0] == 1 ? TONAL : SAME_ACCENT;
        Set<String> both = Set.of(a, b);
        if (both.equals(Set.of("black", "brown"))) return BLACK_BROWN;
        if (both.equals(Set.of("black", "navy"))) return DARK_DARK;
        if (x[0] == 0 && y[0] == 0) return TWO_ACCENTS;
        if (x[0] == 0 || y[0] == 0) return ACCENT;
        if ((x[2] == WARM && y[2] == COOL) || (x[2] == COOL && y[2] == WARM)) return WARM_COOL;
        if ((x[1] == LIGHT && y[1] == DARK) || (x[1] == DARK && y[1] == LIGHT)) return CONTRAST;
        return CALM;
    }

    public static final class Rating {
        /** 0–10 = [colour] (0–5) + [style] (0–3) + [weather] (0–2). */
        public final int score, colour, style, weather;
        /** The main pair (two biggest pieces): its code and colours; "" with fewer than two pieces. */
        public final String pair, pairA, pairB;
        /** The worst pair elsewhere when it clashes (1 point or less), or "". */
        public final String clash, clashA, clashB;
        /** The outfit's main style and how many of the counted pieces share it. */
        public final String dominant;
        public final int sharing, counted;
        public final boolean targetStyle;
        public final String weatherCode;
        /** The piece out of season for the weather, or "". */
        public final String seasonItem;
        Rating(int colour, int style, int weather, String pair, String pairA, String pairB, String clash, String clashA, String clashB,
               String dominant, int sharing, int counted, boolean targetStyle, String weatherCode, String seasonItem) {
            this.score = colour + style + weather; this.colour = colour; this.style = style; this.weather = weather;
            this.pair = pair; this.pairA = pairA; this.pairB = pairB; this.clash = clash; this.clashA = clashA; this.clashB = clashB;
            this.dominant = dominant; this.sharing = sharing; this.counted = counted; this.targetStyle = targetStyle;
            this.weatherCode = weatherCode; this.seasonItem = seasonItem;
        }
        public String label() { return score >= 9 ? GREAT : score >= 7 ? GOOD : score >= 5 ? FAIR : WEAK; }
    }

    private static int priority(String category) {
        int i = MAIN.indexOf(category);
        return i < 0 ? MAIN.size() : i;
    }

    /** [target]: the occasion's style (with the person's PERS-02 choice). */
    public static Rating rate(List<WardrobeRules.Piece> look, String weather, String target) {
        List<WardrobeRules.Piece> big = new ArrayList<>();
        for (WardrobeRules.Piece p : look) if (!SMALL.contains(p.category)) big.add(p);
        if (big.isEmpty()) big = new ArrayList<>(look);
        big.sort(Comparator.<WardrobeRules.Piece>comparingInt(p -> priority(p.category)).thenComparing(p -> p.id));

        // Colour: the main pair's points, plus one when nothing else clashes.
        int colour = 3; String pair = "", pairA = "", pairB = "";
        String clash = "", clashA = "", clashB = ""; int worst = Integer.MAX_VALUE;
        if (big.size() >= 2) {
            pairA = big.get(0).color; pairB = big.get(1).color; pair = pair(pairA, pairB);
            for (int i = 0; i < big.size(); i++) for (int j = i + 1; j < big.size(); j++) {
                String code = pair(big.get(i).color, big.get(j).color);
                int points = pairPoints(code);
                if (points <= 1 && points < worst) { worst = points; clash = code; clashA = big.get(i).color; clashB = big.get(j).color; }
            }
            colour = pairPoints(pair) + (clash.isEmpty() ? 1 : 0);
        }

        // Style: one style throughout (2), mostly one (1), plus one when it is the occasion's style.
        Map<String, Integer> styles = new TreeMap<>();
        for (WardrobeRules.Piece p : big) styles.merge(p.style, 1, Integer::sum);
        String dominant = ""; int sharing = 0;
        for (Map.Entry<String, Integer> e : styles.entrySet()) {
            boolean better = e.getValue() > sharing || (e.getValue() == sharing && e.getKey().equals(target));
            if (better) { dominant = e.getKey(); sharing = e.getValue(); }
        }
        int counted = big.size();
        int consistency = sharing == counted ? 2 : sharing * 3 >= counted * 2 ? 1 : 0;
        boolean onTarget = dominant.equals(target);
        int style = consistency + (onTarget ? 1 : 0);

        // Weather: every piece in season; a coat or jacket when it is cool, cold or raining.
        String season = DailyRules.season(weather); String outOfSeason = "";
        boolean outerwear = false;
        for (WardrobeRules.Piece p : look) {
            if (outOfSeason.isEmpty() && !WardrobeRules.compatibleSeasons(p.season, season)) outOfSeason = p.id;
            if (p.category.equals("outerwear")) outerwear = true;
        }
        boolean chilly = weather.equals("cold") || weather.equals("cool") || weather.equals("rain");
        String weatherCode = !outOfSeason.isEmpty() ? OUT_OF_SEASON : chilly && !outerwear ? NO_OUTERWEAR : WEATHER_OK;
        int weatherPoints = weatherCode.equals(WEATHER_OK) ? 2 : weatherCode.equals(NO_OUTERWEAR) ? 1 : 0;

        return new Rating(colour, style, weatherPoints, pair, pairA, pairB, clash, clashA, clashB, dominant, sharing, counted,
            onTarget, weatherCode, outOfSeason);
    }

    public static final class Swap {
        public final String oldId, newId;
        public final Rating before, after;
        Swap(String oldId, String newId, Rating before, Rating after) { this.oldId = oldId; this.newId = newId; this.before = before; this.after = after; }
    }

    /**
     * «Добре → Краще»: the one replacement among [candidates] (the person's ready items) that raises the rating most.
     * Pinned pieces ([fixed]) stay; the new outfit must keep every season compatible and must not be a combination
     * the person hid ([rejected] signatures). Null when no single change makes it better.
     */
    public static Swap better(List<WardrobeRules.Piece> look, List<WardrobeRules.Piece> candidates, String weather, String target,
                              Set<String> fixed, Set<String> rejected) {
        Rating before = rate(look, weather, target);
        Set<String> inLook = new HashSet<>();
        for (WardrobeRules.Piece p : look) inLook.add(p.id);
        String season = DailyRules.season(weather);
        Swap best = null;
        List<WardrobeRules.Piece> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(p -> p.id));
        for (int index = 0; index < look.size(); index++) {
            WardrobeRules.Piece old = look.get(index);
            if (fixed.contains(old.id)) continue;
            for (WardrobeRules.Piece next : sorted) {
                if (!next.category.equals(old.category) || inLook.contains(next.id) || !next.wardrobe.equals(old.wardrobe)) continue;
                if (!WardrobeRules.ready(next) || !WardrobeRules.compatibleSeasons(next.season, season)) continue;
                List<WardrobeRules.Piece> changed = new ArrayList<>(look);
                changed.set(index, next);
                boolean seasons = true; List<String> ids = new ArrayList<>();
                for (WardrobeRules.Piece p : changed) {
                    ids.add(p.id);
                    if (p != next && !WardrobeRules.compatibleSeasons(p.season, next.season)) seasons = false;
                }
                if (!seasons || rejected.contains(DailyRules.signature(ids))) continue;
                Rating after = rate(changed, weather, target);
                if (after.score <= before.score) continue;
                if (best == null || after.score > best.after.score || (after.score == best.after.score && after.colour > best.after.colour))
                    best = new Swap(old.id, next.id, before, after);
            }
        }
        return best;
    }
}
