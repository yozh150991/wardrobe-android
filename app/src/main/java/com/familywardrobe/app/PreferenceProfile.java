package com.familywardrobe.app;

import java.util.*;

/**
 * PERS-02: generalised preferences built from PERS-01 reasons and the person's own choices. Transparent rules only:
 * every effect is a target style or a score change, never an exclusion, so preferences can not leave the daily
 * suggestions empty however they combine.
 *
 * <ul>
 *   <li>Style per occasion: the person's choice; otherwise casual after {@link #DERIVE} «Надто офіційно» reasons for
 *   that occasion; otherwise the occasion's usual style. After those reasons formal items are also suggested less
 *   often there, unless the person chose formal.</li>
 *   <li>Warmth: the person's choice; otherwise one step lighter than the forecast after {@link #DERIVE}
 *   «Надто тепло» reasons; otherwise the forecast as it is. A manually chosen weather is never shifted.</li>
 *   <li>Colours (PERS-03, 0.14.34): the person's own palette — liked colours a little more often, colours worn less
 *   less often; see {@link #PALETTE_LIKED}.</li>
 * </ul>
 */
public final class PreferenceProfile {
    public static final String AUTO = "auto";
    public static final List<String> STYLES = List.of("casual", "formal", "sport");
    public static final List<String> WARMTH = List.of("forecast", "lighter", "warmer");
    /** Reasons of one kind before a preference forms by itself. One stray reason never generalises. */
    public static final int DERIVE = 2;
    public enum Source { DEFAULT, AUTO, CHOICE }

    public static final class Style {
        public final String style; public final Source source; public final int evidence; public final boolean avoidFormal;
        Style(String style, Source source, int evidence, boolean avoidFormal) {
            this.style = style; this.source = source; this.evidence = evidence; this.avoidFormal = avoidFormal;
        }
    }
    public static final class Warmth {
        public final String shift; public final Source source; public final int evidence;
        Warmth(String shift, Source source, int evidence) { this.shift = shift; this.source = source; this.evidence = evidence; }
    }
    private PreferenceProfile() {}

    public static String defaultStyle(String occasion) {
        return occasion.equals("work") || occasion.equals("occasion") ? "formal" : occasion.equals("active_day") ? "sport" : "casual";
    }

    /** [choice]: the person's choice for this occasion, or {@link #AUTO} / null. */
    public static Style style(List<PreferenceRules.Row> rows, String occasion, String choice) {
        Set<String> sets = new HashSet<>();
        for (PreferenceRules.Row row : rows)
            if (row.reason.equals(PreferenceRules.OCCASION) && row.occasion.equals(occasion) && !row.item()) sets.add(row.signature);
        int evidence = sets.size();
        if (choice != null && STYLES.contains(choice)) return new Style(choice, Source.CHOICE, evidence, evidence >= DERIVE && !choice.equals("formal"));
        if (evidence >= DERIVE) return new Style("casual", Source.AUTO, evidence, true);
        return new Style(defaultStyle(occasion), Source.DEFAULT, evidence, false);
    }

    public static Warmth warmth(List<PreferenceRules.Row> rows, String choice) {
        Set<String> sets = new HashSet<>();
        for (PreferenceRules.Row row : rows) if (row.reason.equals(PreferenceRules.HOT) && !row.item()) sets.add(row.signature + "@" + row.weather);
        int evidence = sets.size();
        if (choice != null && WARMTH.contains(choice)) return new Warmth(choice, Source.CHOICE, evidence);
        if (evidence >= DERIVE) return new Warmth("lighter", Source.AUTO, evidence);
        return new Warmth("forecast", Source.DEFAULT, evidence);
    }

    /** The forecast's weather as this person feels it. */
    public static String apply(String weather, String shift) {
        switch (shift) {
            case "lighter": return PreferenceRules.lighter(weather);
            case "warmer": return warmer(weather);
            default: return weather;
        }
    }
    /** One step warmer clothes: warm → cool → cold. Rain stays rain; "any weather" stays as it is. */
    public static String warmer(String weather) {
        switch (weather) {
            case "warm": return "cool";
            case "cool": return "cold";
            default: return weather;
        }
    }

    /** PERS-01 penalties plus "formal items less often" for this occasion when its style says so; still capped. */
    public static Map<String, Integer> penalties(List<PreferenceRules.Row> rows, Style style, String occasion, String weather,
            Collection<WardrobeRules.Piece> pieces) {
        return penalties(rows, style, occasion, weather, pieces, Collections.emptySet());
    }

    /**
     * PERS-03 «Мої кольори»: base colours the person marks themselves — ones they like wearing and ones they would rather
     * wear less. No photo or selfie analysis. Like every preference here it only changes scores:
     * <ul>
     *   <li>a liked colour adds {@link #PALETTE_LIKED} per item — less than a favourite item (+4), because it covers a
     *   whole colour, not one chosen piece;</li>
     *   <li>a colour worn less takes {@link #PALETTE_AVOIDED} per item, as much as one context reason, within the same
     *   {@link PreferenceRules#MAX} cap, so an item that is the only choice for its category is still suggested.</li>
     * </ul>
     * A colour is in one list at most; if both say so, "less" wins. The 0–10 outfit rating stays the colour theory's
     * (StyleRules): the palette moves variants up or down, it does not rate them.
     */
    public static final int PALETTE_LIKED = 3;
    public static final int PALETTE_AVOIDED = PreferenceRules.CONTEXT;

    /** The penalties above plus PERS-03 colours worn less ([avoided]); still capped per item. */
    public static Map<String, Integer> penalties(List<PreferenceRules.Row> rows, Style style, String occasion, String weather,
            Collection<WardrobeRules.Piece> pieces, Set<String> avoided) {
        Map<String, Integer> result = new HashMap<>(PreferenceRules.penalties(rows, occasion, weather));
        for (WardrobeRules.Piece piece : pieces) {
            if (style.avoidFormal && piece.style.equals("formal")) result.merge(piece.id, PreferenceRules.CONTEXT, PreferenceProfile::capped);
            if (avoided.contains(piece.color)) result.merge(piece.id, PALETTE_AVOIDED, PreferenceProfile::capped);
        }
        return result;
    }
    private static int capped(int a, int b) { return Math.min(PreferenceRules.MAX, a + b); }

    /** PERS-03: points added per item of a liked colour (not one that is also marked "less"). */
    public static Map<String, Integer> bonus(Collection<WardrobeRules.Piece> pieces, Set<String> liked, Set<String> avoided) {
        Map<String, Integer> result = new HashMap<>();
        for (WardrobeRules.Piece piece : pieces)
            if (liked.contains(piece.color) && !avoided.contains(piece.color)) result.put(piece.id, PALETTE_LIKED);
        return result;
    }
}
