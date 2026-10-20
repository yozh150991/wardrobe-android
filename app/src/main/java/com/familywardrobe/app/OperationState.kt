package com.familywardrobe.app

import java.io.FileNotFoundException
import java.io.IOException

enum class OperationKind(val title: Int, val canBrowse: Boolean = false) {
    INITIALIZE(R.string.operation_open), SAVE(R.string.operation_save),
    PHOTO(R.string.operation_photo), MASK(R.string.operation_mask), IMPORT(R.string.operation_import), SHOP(R.string.shop_loading),
    EXPORT(R.string.operation_export, true), VERIFY(R.string.operation_verify, true),
    PREVIEW_RESTORE(R.string.operation_verify, true), RESTORE(R.string.operation_restore),
    FOLDER(R.string.operation_folder), CAPSULE(R.string.operation_capsule), CLOUD(R.string.operation_cloud, true), CLEANUP(R.string.operation_cleanup, true)
}
enum class FailureAction { RETRY, FILE, EXPORT_LOCATION, FOLDER, REFRESH, CLOSE }
data class OperationState(val kind: OperationKind, val cancellable: Boolean, val cancelling: Boolean = false)
data class OperationFailure(val message: Int, val action: FailureAction, val kind: OperationKind)
class StaleSelectionException : IllegalArgumentException()
class BackupChangedException : IllegalStateException()
class WishCategoryInUseException : IllegalArgumentException()

internal fun operationFailure(kind: OperationKind, error: Exception): OperationFailure {
    val causes = generateSequence<Throwable>(error) { it.cause }.take(12).toList()
    if (causes.any { it is android.system.ErrnoException && it.errno == android.system.OsConstants.ENOSPC })
        return OperationFailure(R.string.failure_space, FailureAction.RETRY, kind)
    if (error is StaleSelectionException)
        return OperationFailure(R.string.failure_stale_selection, FailureAction.REFRESH, kind)
    if (error is WishCategoryInUseException)
        return OperationFailure(R.string.wish_category_in_outfits, FailureAction.CLOSE, kind)
    if (error is BackupChangedException)
        return OperationFailure(R.string.failure_backup_changed, FailureAction.FILE, kind)
    if (kind == OperationKind.CLOUD) {
        val message = when(error) {
            is CloudDraftException -> R.string.cloud_draft
            is CloudChangedException, is CloudConflictException -> R.string.cloud_changed
            is IllegalArgumentException -> R.string.cloud_invalid
            else -> R.string.cloud_retry_hint
        }
        return OperationFailure(message, FailureAction.CLOSE, kind)
    }
    if (kind == OperationKind.FOLDER)
        return OperationFailure(R.string.failure_folder, FailureAction.FOLDER, kind)
    if (kind == OperationKind.SHOP)
        return OperationFailure(R.string.shop_load_failed, FailureAction.RETRY, kind)
    if (kind == OperationKind.EXPORT && (error is SecurityException || error is FileNotFoundException))
        return OperationFailure(R.string.failure_export_access, FailureAction.EXPORT_LOCATION, kind)
    if (kind in setOf(OperationKind.PHOTO, OperationKind.IMPORT)) {
        val reason = photoFailure("", error).failure
        val action = if (reason in setOf(PhotoFailureKind.ACCESS, PhotoFailureKind.MISSING,
            PhotoFailureKind.UNREADABLE, PhotoFailureKind.TOO_LARGE)) FailureAction.CLOSE else FailureAction.RETRY
        return OperationFailure(photoFailureMessage(reason), action, kind)
    }
    if (error is SecurityException || error is FileNotFoundException)
        return OperationFailure(R.string.failure_file_access, if (kind in setOf(OperationKind.VERIFY, OperationKind.PREVIEW_RESTORE, OperationKind.RESTORE)) FailureAction.FILE else FailureAction.CLOSE, kind)
    if (kind in setOf(OperationKind.VERIFY, OperationKind.PREVIEW_RESTORE, OperationKind.RESTORE))
        return OperationFailure(if (error is IOException) R.string.failure_backup_read else R.string.failure_backup_invalid, FailureAction.FILE, kind)
    if (kind in setOf(OperationKind.PHOTO, OperationKind.MASK, OperationKind.IMPORT))
        return OperationFailure(R.string.failure_photo, FailureAction.RETRY, kind)
    if (kind == OperationKind.EXPORT)
        return OperationFailure(R.string.failure_export, FailureAction.RETRY, kind)
    return OperationFailure(if (kind == OperationKind.INITIALIZE) R.string.failure_open else R.string.failure_save,
        if (error is IllegalArgumentException) FailureAction.CLOSE else FailureAction.RETRY, kind)
}
