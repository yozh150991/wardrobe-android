package com.familywardrobe.app

import org.junit.Test

class PreferenceRulesTest {
    @Test fun reasonsScopeAndGeneratorEffect() { PreferenceRulesCases.main(emptyArray()) }
    @Test fun generalisedPreferencesNeverEmptyTheDay() { PreferenceProfileCases.main(emptyArray()) }
}
