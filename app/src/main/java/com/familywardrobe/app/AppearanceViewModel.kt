package com.familywardrobe.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppearanceViewModel(application: Application) : AndroidViewModel(application) {
    private val appearanceStore = AppearanceStore(application)
    private val appearanceWrites = Mutex()
    private val _appearance = MutableStateFlow<AppearanceSettings?>(null)
    val appearance = _appearance.asStateFlow()
    private val _appearanceSaveFailed = MutableStateFlow(false)
    val appearanceSaveFailed = _appearanceSaveFailed.asStateFlow()
    init {
        viewModelScope.launch {
            try { _appearance.value = appearanceStore.load() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _appearance.value = AppearanceSettings()
                _appearanceSaveFailed.value = true
            }
        }
    }
    fun setPalette(palette: WardrobePalette) = saveAppearance((_appearance.value ?: AppearanceSettings()).copy(palette = palette))
    fun setAppearanceMode(mode: AppearanceMode) = saveAppearance((_appearance.value ?: AppearanceSettings()).copy(mode = mode))
    fun retryAppearanceSave() = saveAppearance(_appearance.value ?: AppearanceSettings())
    private fun saveAppearance(next: AppearanceSettings) {
        _appearance.value = next
        _appearanceSaveFailed.value = false
        // Queue writes on Main before suspending: rapid changes cannot persist an older choice last.
        viewModelScope.launch {
            appearanceWrites.withLock {
                try {
                    appearanceStore.save(next)
                    if (_appearance.value == next) _appearanceSaveFailed.value = false
                }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { if (_appearance.value == next) _appearanceSaveFailed.value = true }
            }
        }
    }
}
