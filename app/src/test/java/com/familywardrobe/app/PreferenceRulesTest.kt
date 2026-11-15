package com.familywardrobe.app

import com.familywardrobe.app.rules.PreferenceProfileCases
import com.familywardrobe.app.rules.PreferenceRulesCases
import org.junit.Test

class PreferenceRulesTest {
    @Test fun reasonsScopeAndGeneratorEffect() { PreferenceRulesCases.main(emptyArray()) }
    @Test fun generalisedPreferencesNeverEmptyTheDay() { PreferenceProfileCases.main(emptyArray()) }
}
