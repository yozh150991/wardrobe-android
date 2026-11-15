package com.familywardrobe.app

import com.familywardrobe.app.rules.AuthRulesCases
import org.junit.Test

class AuthRulesTest {
    @Test fun validationIdentityAndCancellation() { AuthRulesCases.run() }
}
