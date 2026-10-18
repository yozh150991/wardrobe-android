package com.familywardrobe.app

import org.junit.Test

class WardrobeRulesTest {
    @Test fun nativeWardrobeRulesKeepDataAndOutfitsConsistent() {
        WardrobeRulesCases.runAll()
    }
}
