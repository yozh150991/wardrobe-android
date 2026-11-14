package com.familywardrobe.app

import com.familywardrobe.app.rules.WardrobeRulesCases
import org.junit.Test

class WardrobeRulesTest {
    @Test fun nativeWardrobeRulesKeepDataAndOutfitsConsistent() {
        WardrobeRulesCases.runAll()
    }
}
