package com.familywardrobe.app

import org.junit.Test

class PhotoRulesTest {
    @Test fun knownLabels() = PhotoRulesCases.knownLabels()
    @Test fun weakAndUnknownLabels() = PhotoRulesCases.weakAndUnknownLabels()
    @Test fun ambiguousCategories() = PhotoRulesCases.ambiguousCategories()
    @Test fun sameCategoryLabelsCombine() = PhotoRulesCases.sameCategoryLabelsCombine()
    @Test fun onePieceGarmentsStayManual() = PhotoRulesCases.onePieceGarmentsStayManual()
    @Test fun paletteColors() = PhotoRulesCases.paletteColors()
    @Test fun transparentPixelsDoNotInfluenceColor() = PhotoRulesCases.transparentPixelsDoNotInfluenceColor()
    @Test fun insufficientForegroundHasNoColor() = PhotoRulesCases.insufficientForegroundHasNoColor()
    @Test fun mixedColorsStayMixed() = PhotoRulesCases.mixedColorsStayMixed()
    @Test fun emptyOrOpaqueMasksRejected() = PhotoRulesCases.emptyOrOpaqueMasksRejected()
    @Test fun tinyOrNearlyFullMasksRejected() = PhotoRulesCases.tinyOrNearlyFullMasksRejected()
    @Test fun substantialMaskAccepted() = PhotoRulesCases.substantialMaskAccepted()
}
