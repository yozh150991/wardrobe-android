package com.familywardrobe.app.rules;

/**
 * WTH-01 / GEN-04 (0.14.38): what the forecast's temperature means for an outfit.
 *
 * The saved weather stays one of five modes (any, warm, cool, cold, rain) — backups, shared wardrobes and the reasons
 * people give are stored with it. Three bands were too coarse for choosing clothes, though: at +21 the "cool" band
 * added a coat and hid every summer item. So when the weather comes from the forecast, the temperature travels with
 * the mode and decides the details. With a manual choice there is no temperature (null) and the mode decides, as before.
 *
 * All limits are the app's own choices, in °C of the temperature below; they are not from a standard.
 */
public final class WeatherRules {
    private WeatherRules() {}
    /** The bands of {@link RoadmapRules#weatherMode}: warm from here, cold below {@link #COLD_BELOW}. */
    public static final double WARM_FROM = 22, COLD_BELOW = 8;
    /** A coat or jacket is added below this. */
    public static final double OUTERWEAR_BELOW = 15;
    /** A second layer (a jumper, a cardigan) is added below this. */
    public static final double LAYER_BELOW = COLD_BELOW;
    /** Tights are added to a dress below this. */
    public static final double TIGHTS_BELOW = 12;
    /** Near a band's limit the neighbouring season's items are allowed too. */
    public static final double OVERLAP = 3;
    /**
     * WTH-02 (0.14.45): from this chance of rain (%, the forecast's daily maximum) a mild day is «Дощ» by itself — it
     * was only ever chosen by hand. A warm day stays warm (a summer shower needs no coat) and a cold one stays cold (it
     * has a coat already).
     */
    public static final int RAIN_FROM = 60;
    /** PERS-02 «легше» / «тепліше»: the forecast as this person feels it. */
    public static final double SHIFT = 4;

    private static double valid(double value) {
        if (!Double.isFinite(value) || value < -90 || value > 65) throw new IllegalArgumentException();
        return value;
    }
    /** A day's temperature for dressing: the high counts twice, because the day is spent closer to it than to the night's low. */
    public static double day(double low, double high) {
        if (valid(low) > valid(high)) throw new IllegalArgumentException();
        return (low + 2 * high) / 3;
    }
    /** Today: the day's temperature and how it feels right now, equally — a cold morning does not make a mild day a winter one. */
    public static double today(double low, double high, double feelsLike) { return (day(low, high) + valid(feelsLike)) / 2; }
    /** [shift]: "lighter" — dress as if it were warmer; "warmer" — as if it were colder; anything else — the forecast. */
    public static double shifted(double temperature, String shift) {
        double value = valid(temperature) + ("lighter".equals(shift) ? SHIFT : "warmer".equals(shift) ? -SHIFT : 0);
        return Math.max(-90, Math.min(65, value));
    }
    public static boolean rainy(Integer chance) { return chance != null && chance >= RAIN_FROM && chance <= 100; }
    /** The mode for a forecast day: the temperature's band (as RoadmapRules#weatherMode), mild and rainy days «Дощ». */
    public static String mode(double temperature, Integer rainChance) {
        double t = valid(temperature);
        String band = t >= WARM_FROM ? "warm" : t < COLD_BELOW ? "cold" : "cool";
        return band.equals("cool") && rainy(rainChance) ? "rain" : band;
    }
    private static boolean chilly(String weather) { return weather.equals("cold") || weather.equals("cool") || weather.equals("rain"); }
    /** A coat below {@link #OUTERWEAR_BELOW}; on a rainy day (WTH-02) a jacket up to the warm band. */
    public static boolean outerwear(String weather, Double temperature) {
        if (temperature == null) return chilly(weather);
        double t = valid(temperature);
        return t < OUTERWEAR_BELOW || weather.equals("rain") && t < WARM_FROM;
    }
    public static boolean layer(String weather, Double temperature) {
        return temperature == null ? weather.equals("cold") : valid(temperature) < LAYER_BELOW;
    }
    public static boolean tights(String weather, Double temperature) {
        return temperature == null ? weather.equals("cold") : valid(temperature) < TIGHTS_BELOW;
    }
    /**
     * Whether an item of [season] suits. Without a temperature: the mode's season only (as before). With one: the
     * band's season, plus the neighbouring season within {@link #OVERLAP} of the band's limit — summer items from +19,
     * mid-season ones from +5 up to +25, winter ones below +11. Always at least what the mode alone allows.
     */
    public static boolean inSeason(String season, String weather, Double temperature) {
        if (temperature == null) return WardrobeRules.compatibleSeasons(season, DailyRules.season(weather));
        double t = valid(temperature);
        switch (season) {
            case "all": return true;
            case "summer": return t >= WARM_FROM - OVERLAP;
            case "midseason": return t >= COLD_BELOW - OVERLAP && t < WARM_FROM + OVERLAP;
            case "winter": return t < COLD_BELOW + OVERLAP;
            default: return false;
        }
    }
}
