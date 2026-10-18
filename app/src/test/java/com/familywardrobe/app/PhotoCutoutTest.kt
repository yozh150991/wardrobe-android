package com.familywardrobe.app

import org.junit.Test

class PhotoCutoutTest {
    @Test fun roundedOpaqueMaskHasNoGrid() = PhotoCutoutCases.roundedConfidence()
    @Test fun gentleDefaultPreservesMoreFabric() = PhotoCutoutCases.gentleMode()
    @Test fun removesResidue() = PhotoCutoutCases.cleanup()
    @Test fun preservesFabricAndPairs() = PhotoCutoutCases.preserveDetails()
    @Test fun invalidMasksAndCancellation() = PhotoCutoutCases.malformedAndCancellation()
    @Test fun whiteAndCreamBackdrops() = PhotoCutoutCases.backdrops()
    @Test fun cameraSizedFrame() = PhotoCutoutCases.largeFrame()
}
