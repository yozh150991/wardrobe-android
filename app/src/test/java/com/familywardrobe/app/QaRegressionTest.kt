package com.familywardrobe.app

import com.familywardrobe.app.rules.QaFamilyClientCases
import com.familywardrobe.app.rules.QaPairwiseCases
import com.familywardrobe.app.rules.QaShopImportCases
import com.familywardrobe.app.rules.QaStateTransitionCases
import com.familywardrobe.app.rules.QaTextInputCases
import com.familywardrobe.app.rules.QaWardrobeDomainCases
import org.junit.Test

/**
 * Gradle entry point for the technique-tagged QA regression suites (QA-REPORT-0.14.13.md).
 * The same cases run on the host through tools/check_qa_regression.py. Open bugs are strict
 * expected failures: a suite fails when a listed bug stops reproducing, so the fix gets noticed.
 */
class QaRegressionTest {
    @Test fun textInputEquivalenceAndBoundaries() = QaTextInputCases.run()
    @Test fun shopImportEquivalenceBoundariesAndErrors() = QaShopImportCases.run()
    @Test fun wardrobeDomainEquivalenceAndBoundaries() = QaWardrobeDomainCases.run()
    @Test fun stateTransitions() = QaStateTransitionCases.run()
    @Test fun pairwiseCombinations() = QaPairwiseCases.run()
    @Test fun familyClientBoundariesAndCraftedArchives() = QaFamilyClientCases.run()
}
