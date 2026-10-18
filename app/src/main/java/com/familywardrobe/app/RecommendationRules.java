package com.familywardrobe.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Actual wear only; calendar plans never reserve or consume clothing. */
public final class RecommendationRules {
    private RecommendationRules() {}
    public static boolean allowed(boolean excluded,String lastWear,String date,int noRepeatDays) {
        if(noRepeatDays<0 || noRepeatDays>30) throw new IllegalArgumentException();
        if(excluded) return false;
        if(noRepeatDays==0 || lastWear.isEmpty()) return true;
        long elapsed=ChronoUnit.DAYS.between(LocalDate.parse(lastWear),LocalDate.parse(date));
        return elapsed>=noRepeatDays;
    }
}
