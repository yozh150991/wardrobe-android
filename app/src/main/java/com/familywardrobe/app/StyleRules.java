package com.familywardrobe.app;

import java.util.*;

/**
 * STYLE-01/02: a transparent outfit rating (0–10) and the colour-pair table behind «Добре → Краще». Every point
 * comes from a named rule, so the screen can say why; nothing here is learned or hidden.
 *
 * Colours are the app's twenty base colours (a custom shade uses its base; ten were added in 0.14.32, COL-02). Each
 * has a lightness (value) and a temperature, the two properties colour theory pairs by [S1][S2]; a bright accent also
 * has its place on the artist's colour wheel, so two accents can be neighbours or opposites [S1]. Rules v2 (3 November 2026) follow the sources
 * below; the research, the app survey and the trends are in COLOR-RULES-0.14.31.md. Points are this app's choice:
 * the sources say what works, not how many points it is worth.
 *
 * Sources (opened 3 November 2026):
 * [S1] Adobe, "Master the colour wheel": complementary, analogous, triadic harmonies; warm = red, orange, yellow,
 *      cool = blue, green, violet. https://www.adobe.com/uk/creativecloud/design/discover/color-wheel.html
 * [S2] Ape to Gentleman, "How to Colour Match Clothing": black, white, grey neutral; brown, tan, beige, khaki neutral
 *      versions; navy works as a neutral; usually two colours (or one) plus neutrals; vary hue/saturation/brightness.
 *      https://www.apetogentleman.com/colour-match/
 * [S3] Woman & Home, "The three colour rule": up to three colours including neutrals, roughly 60/30/10.
 *      https://www.womanandhome.com/fashion/three-colour-rule/
 * [S4] Coveteur, black and brown: the old rule is outdated; add a third colour, vary shades and materials.
 *      https://coveteur.com/black-brown-outfit-styling
 * [S5] Mr Porter, "Can you wear black and navy together?": acceptable when deliberate and clearly different.
 *      https://www.mrporter.com/en-es/journal/fashion/you-asked-can-you-wear-black-and-navy-together-762234
 * [S6] FashionBeans, outdated style rules: navy and black, brown and black no longer forbidden.
 *      https://www.fashionbeans.com/article/outdated-style-rules-should-break/
 * [S7] Ape to Gentleman, "8 Outfit Colour Matching Combinations That Always Work": black + white, navy + cream,
 *      brown + beige, green + black, olive + white, brown + green, grey + grey, pink + blue.
 *      https://www.apetogentleman.com/outfit-colour-combinations/
 * [S8] FashionBeans, "5 Colour Combinations That Always Work": black + camel, navy + burgundy, brown + green,
 *      pink + grey, black + white. https://www.fashionbeans.com/2017/5-colour-combinations-that-always-work/
 * [S9] Moss, tonal dressing: one colour family head to toe, varying the shade. https://www.moss.co.uk/inside-pocket/post/how-to-tonal-dressing
 * [S10] Who What Wear, blue + brown trend (27 Jan 2025): sky blue with chocolate brown.
 *      https://www.whowhatwear.com/fashion/celebrity-style/blue-brown-colour-trend-katie-holmes-alexa-chung
 * [S11] The user's screenshot (3 November 2026): grey with navy «good», grey with brown «better».
 */
public final class StyleRules {
    private StyleRules() {}

    // Pair codes, best first; their points are in [pairPoints]. Every code names its sources.
    /** 4: neutrals of opposite temperature — grey + brown, navy + beige/brown [S1][S7 navy + cream][S11]. */
    public static final String WARM_COOL = "warm_cool";
    /** 4: an accent on a neutral of the opposite temperature — blue + brown, red + navy [S1 complementary][S8 navy + burgundy][S10]. */
    public static final String ACCENT_CONTRAST = "accent_contrast";
    /** 3: light with dark — black + white, navy + white, brown + beige [S2 brightness][S7][S8]. */
    public static final String CONTRAST = "contrast";
    /** 3: one accent on a neutral base of the same or no temperature [S2 neutral base + one highlight][S8 pink + grey]. */
    public static final String ACCENT = "accent";
    /** 3: neutrals together — grey + navy, olive + white, black + olive [S2][S7 olive + white, green + black]. */
    public static final String CALM = "calm";
    /** 3: one neutral family head to toe; better in different shades [S7 grey + grey][S9]. */
    public static final String TONAL = "tonal";
    /** 3: two accents next to each other on the colour wheel — blue + green, red + pink [S1 analogous][S2]. */
    public static final String ANALOGOUS = "analogous";
    /** 2: an unknown or patterned colour is not judged (and never costs the harmony point). */
    public static final String UNKNOWN = "unknown";
    /** 2: one bright colour head to toe — bold, works in different shades [S9 bright hues also work]. */
    public static final String SAME_ACCENT = "same_accent";
    /** 2: debated — the old rule is outdated, but it needs a third colour or different shades [S4][S6]. */
    public static final String BLACK_BROWN = "black_brown";
    /** 2: debated — works when deliberate and clearly different, otherwise the darks blur [S5][S6]. */
    public static final String DARK_DARK = "dark_dark";
    /** 2: two bright colours — fine with neutrals around them [S2 two colours plus neutrals][S7 pink + blue]. */
    public static final String TWO_ACCENTS = "two_accents";
    /** 2: opposite accents — blue + orange, red + green: strong contrast, best with one toned down [S1 complementary][S2]. */
    public static final String COMPLEMENTARY = "complementary";
    /** The three-colour rule: at most three colours, neutrals included [S3]. */
    public static final int MAX_COLOURS = 3;

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
    /**
     * {base neutral (1) or bright accent (0), lightness, temperature, place of an accent on the artist's (red–yellow–blue)
     * wheel in degrees, -1 for neutrals}. Neutrals follow [S2]: black, white, grey; brown, tan/camel, beige, khaki and
     * cream as neutral versions; navy, olive and light blue work as neutrals. Warm and cool follow [S1].
     */
    private static final Map<String, int[]> COLOURS = new HashMap<>();
    static {
        COLOURS.put("white", new int[] {1, LIGHT, NEUTRAL, -1});
        COLOURS.put("cream", new int[] {1, LIGHT, WARM, -1});
        COLOURS.put("black", new int[] {1, DARK, NEUTRAL, -1});
        COLOURS.put("grey", new int[] {1, MID, COOL, -1});
        COLOURS.put("beige", new int[] {1, LIGHT, WARM, -1});
        COLOURS.put("camel", new int[] {1, MID, WARM, -1});
        COLOURS.put("khaki", new int[] {1, MID, WARM, -1});
        COLOURS.put("brown", new int[] {1, DARK, WARM, -1});
        COLOURS.put("navy", new int[] {1, DARK, COOL, -1});
        COLOURS.put("light_blue", new int[] {1, LIGHT, COOL, -1});
        COLOURS.put("olive", new int[] {1, MID, WARM, -1});
        COLOURS.put("red", new int[] {0, MID, WARM, 0});
        COLOURS.put("orange", new int[] {0, MID, WARM, 60});
        COLOURS.put("yellow", new int[] {0, LIGHT, WARM, 120});
        COLOURS.put("green", new int[] {0, MID, COOL, 180});
        COLOURS.put("blue", new int[] {0, MID, COOL, 240});
        COLOURS.put("purple", new int[] {0, MID, COOL, 300});
        COLOURS.put("pink", new int[] {0, LIGHT, WARM, 330});
        COLOURS.put("burgundy", new int[] {0, DARK, WARM, 350});
    }
    /** Small pieces do not decide the colours or the style of an outfit. */
    private static final Set<String> SMALL = Set.of("jewelry", "accessory");
    /** The two biggest pieces make the main colour pair. */
    private static final List<String> MAIN = List.of("top", "dress", "jumpsuit", "bottom", "layer", "outerwear", "shoes", "bag", "hat", "tights", "other_item");

    /** No pairing is forbidden [S2 "no specific pairing is ruled out" in spirit; S4–S6]: the lowest tier is «with care». */
    public static int pairPoints(String code) {
        switch (code) {
            case WARM_COOL: case ACCENT_CONTRAST: return 4;
            case CONTRAST: case ACCENT: case CALM: case TONAL: case ANALOGOUS: return 3;
            case UNKNOWN: case SAME_ACCENT: case BLACK_BROWN: case DARK_DARK: case TWO_ACCENTS: case COMPLEMENTARY: return 2;
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
        if (x[0] == 0 && y[0] == 0) {
            int distance = Math.abs(x[3] - y[3]) % 360;
            distance = Math.min(distance, 360 - distance);
            return distance >= 150 ? COMPLEMENTARY : distance <= 60 ? ANALOGOUS : TWO_ACCENTS;
        }
        if (x[0] == 0 || y[0] == 0) return oppositeTemperature(x, y) ? ACCENT_CONTRAST : ACCENT;
        if (oppositeTemperature(x, y)) return WARM_COOL;
        if ((x[1] == LIGHT && y[1] == DARK) || (x[1] == DARK && y[1] == LIGHT)) return CONTRAST;
        return CALM;
    }

    private static boolean oppositeTemperature(int[] x, int[] y) {
        return (x[2] == WARM && y[2] == COOL) || (x[2] == COOL && y[2] == WARM);
    }

    public static final class Rating {
        /** 0–10 = [colour] (0–5) + [style] (0–3) + [weather] (0–2). */
        public final int score, colour, style, weather;
        /** The main pair (two biggest pieces): its code and colours; "" with fewer than two pieces. */
        public final String pair, pairA, pairB;
        /** The weakest «with care» pair (2 points, not an unknown colour), or "": it costs the harmony point. */
        public final String clash, clashA, clashB;
        /** Distinct known colours of the big pieces; more than [MAX_COLOURS] costs the harmony point [S3]. */
        public final int colours;
        /** Distinct bright (non-neutral) colours among them. */
        public final int accents;
        /** The outfit's main style and how many of the counted pieces share it. */
        public final String dominant;
        public final int sharing, counted;
        public final boolean targetStyle;
        public final String weatherCode;
        /** The piece out of season for the weather, or "". */
        public final String seasonItem;
        Rating(int colour, int style, int weather, String pair, String pairA, String pairB, String clash, String clashA, String clashB,
               int colours, int accents, String dominant, int sharing, int counted, boolean targetStyle, String weatherCode, String seasonItem) {
            this.score = colour + style + weather; this.colour = colour; this.style = style; this.weather = weather;
            this.pair = pair; this.pairA = pairA; this.pairB = pairB; this.clash = clash; this.clashA = clashA; this.clashB = clashB;
            this.colours = colours; this.accents = accents;
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
    public static Rating rate(List<WardrobeRules.Piece> look, String weather, String target) { return rate(look, weather, target, null); }
    /** [temperature]: the forecast's temperature behind [weather], or null (WTH-01, see WeatherRules). */
    public static Rating rate(List<WardrobeRules.Piece> look, String weather, String target, Double temperature) {
        List<WardrobeRules.Piece> big = new ArrayList<>();
        for (WardrobeRules.Piece p : look) if (!SMALL.contains(p.category)) big.add(p);
        if (big.isEmpty()) big = new ArrayList<>(look);
        big.sort(Comparator.<WardrobeRules.Piece>comparingInt(p -> priority(p.category)).thenComparing(p -> p.id));

        // Colour: the main pair's points, plus a harmony point when every other pair works well and the outfit keeps
        // to three colours [S3].
        int colour = 3; String pair = "", pairA = "", pairB = "";
        String clash = "", clashA = "", clashB = "";
        Set<String> known = new TreeSet<>();
        for (WardrobeRules.Piece p : big) if (COLOURS.containsKey(p.color)) known.add(p.color);
        if (big.size() >= 2) {
            pairA = big.get(0).color; pairB = big.get(1).color; pair = pair(pairA, pairB);
            for (int i = 0; i < big.size() && clash.isEmpty(); i++) for (int j = i + 1; j < big.size(); j++) {
                String code = pair(big.get(i).color, big.get(j).color);
                if (!code.equals(UNKNOWN) && pairPoints(code) <= 2) { clash = code; clashA = big.get(i).color; clashB = big.get(j).color; break; }
            }
            colour = pairPoints(pair) + (clash.isEmpty() && known.size() <= MAX_COLOURS ? 1 : 0);
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

        // Weather: every piece in season; a coat or jacket when the weather calls for one (WeatherRules).
        String outOfSeason = "";
        boolean outerwear = false;
        for (WardrobeRules.Piece p : look) {
            if (outOfSeason.isEmpty() && !WeatherRules.inSeason(p.season, weather, temperature)) outOfSeason = p.id;
            if (p.category.equals("outerwear")) outerwear = true;
        }
        boolean chilly = WeatherRules.outerwear(weather, temperature);
        String weatherCode = !outOfSeason.isEmpty() ? OUT_OF_SEASON : chilly && !outerwear ? NO_OUTERWEAR : WEATHER_OK;
        int weatherPoints = weatherCode.equals(WEATHER_OK) ? 2 : weatherCode.equals(NO_OUTERWEAR) ? 1 : 0;

        int accents = 0;
        for (String c : known) if (COLOURS.get(c)[0] == 0) accents++;
        return new Rating(colour, style, weatherPoints, pair, pairA, pairB, clash, clashA, clashB, known.size(), accents, dominant, sharing, counted,
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
        return better(look, candidates, weather, target, fixed, rejected, null);
    }
    public static Swap better(List<WardrobeRules.Piece> look, List<WardrobeRules.Piece> candidates, String weather, String target,
                              Set<String> fixed, Set<String> rejected, Double temperature) {
        Rating before = rate(look, weather, target, temperature);
        Set<String> inLook = new HashSet<>();
        for (WardrobeRules.Piece p : look) inLook.add(p.id);
        Swap best = null;
        List<WardrobeRules.Piece> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparing(p -> p.id));
        for (int index = 0; index < look.size(); index++) {
            WardrobeRules.Piece old = look.get(index);
            if (fixed.contains(old.id)) continue;
            for (WardrobeRules.Piece next : sorted) {
                if (!next.category.equals(old.category) || inLook.contains(next.id) || !next.wardrobe.equals(old.wardrobe)) continue;
                if (!WardrobeRules.ready(next) || !WeatherRules.inSeason(next.season, weather, temperature)) continue;
                List<WardrobeRules.Piece> changed = new ArrayList<>(look);
                changed.set(index, next);
                boolean seasons = true; List<String> ids = new ArrayList<>();
                for (WardrobeRules.Piece p : changed) {
                    ids.add(p.id);
                    if (p != next && !WardrobeRules.compatibleSeasons(p.season, next.season)) seasons = false;
                }
                if (!seasons || rejected.contains(DailyRules.signature(ids))) continue;
                Rating after = rate(changed, weather, target, temperature);
                if (after.score <= before.score) continue;
                // Ties: better colours, then fewer bright colours — a neutral base is the safe choice [S2].
                boolean wins = best == null || after.score > best.after.score || (after.score == best.after.score &&
                    (after.colour > best.after.colour || (after.colour == best.after.colour && after.accents < best.after.accents)));
                if (wins) best = new Swap(old.id, next.id, before, after);
            }
        }
        return best;
    }
}
