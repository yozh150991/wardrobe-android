package com.familywardrobe.app
import com.familywardrobe.app.rules.PhotoGeometryCases
import org.junit.Test

class PhotoGeometryTest {
    @Test fun fullImage() = PhotoGeometryCases.fullImage()
    @Test fun centralCrop() = PhotoGeometryCases.centralCrop()
    @Test fun portraitAfterRotation() = PhotoGeometryCases.portraitAfterRotation()
    @Test fun smallSelectionAtEdge() = PhotoGeometryCases.smallSelectionAtEdge()
    @Test fun rotationWraps() = PhotoGeometryCases.rotationWraps()
    @Test fun invalidBoundsRejected() = PhotoGeometryCases.invalidBoundsRejected()
    @Test fun everyValidCropFitsImage() = PhotoGeometryCases.everyValidCropFitsImage()
}
