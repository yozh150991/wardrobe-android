package com.familywardrobe.app

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Same bounded snapshot writer for initial migration and later additions. */
internal object FamilySnapshotZip {
    fun write(target: File, manifest: ByteArray, state: Snapshot, photos: PhotoStore, guard: () -> Unit) {
        val ids = state.photoIds().sorted(); require(ids.size <= 5000); var expanded = manifest.size.toLong()
        FileOutputStream(target).use { output -> ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(manifest); zip.closeEntry()
            for (id in ids) {
                guard(); val photo = photos.currentFile(id)
                require(photo.isFile && photo.extension in setOf("jpg", "png") && photo.length() in 1..12L*1024*1024)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(photo.path, bounds)
                require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong() * bounds.outHeight <= 16L*1024*1024)
                require(bounds.outMimeType == if (photo.extension == "png") "image/png" else "image/jpeg")
                expanded += photo.length(); require(expanded <= 256L*1024*1024)
                zip.putNextEntry(ZipEntry("photos/${photo.name}"))
                photo.inputStream().use { PhotoStore.copyLimited(it, zip, 12L*1024*1024, guard) }; zip.closeEntry()
                require(target.length() <= FamilyWire.MAX_ARCHIVE)
            }
            zip.finish(); zip.flush(); output.fd.sync()
        } }
        guard(); require(target.length() in 1..FamilyWire.MAX_ARCHIVE)
    }
}
