package com.familywardrobe.app

import com.familywardrobe.app.rules.PhotoRegionCases
import org.junit.Test

class PhotoRegionTest {
    @Test fun boundariesAndColour() = PhotoRegionCases.boundariesAndColour()
    @Test fun smallConnectedAreas() = PhotoRegionCases.allSmallTopologies()
    @Test fun crossToolHistoryAndCancellation() = PhotoRegionCases.historyAndCancellation()
    @Test fun cameraAndInspection() = PhotoRegionCases.cameraAndInspection()
}
