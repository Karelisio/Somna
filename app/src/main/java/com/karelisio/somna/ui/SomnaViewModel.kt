package com.karelisio.somna.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.somna.AppContainer
import com.karelisio.somna.BuildConfig
import com.karelisio.somna.data.SettingsRepository
import com.karelisio.somna.data.SomnaSettings
import com.karelisio.somna.update.CheckResult
import com.karelisio.somna.update.ReleaseInfo
import com.karelisio.somna.update.UpdateInstaller
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Activity-scoped state: settings, update availability. */
class SomnaViewModel(val container: AppContainer) : ViewModel() {
    val settings: StateFlow<SomnaSettings?> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A newer release the user hasn't skipped. */
    val pendingUpdate: StateFlow<ReleaseInfo?> =
        combine(container.updates.available, container.settings.settings) { info, s ->
            info?.takeIf { it.version != s.skippedVersion }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var updateSheetDismissed by mutableStateOf(false)
    var forceShowUpdate by mutableStateOf(false)

    fun edit(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settings.block() }
    }

    fun checkUpdateOnLaunch() {
        if (BuildConfig.DEBUG) return
        viewModelScope.launch {
            if (container.settings.current().autoUpdateCheck) container.updates.check(force = false)
        }
    }

    suspend fun checkUpdateNow(): CheckResult = container.updates.check(force = true)

    fun skipVersion(version: String) = edit { setSkippedVersion(version) }

    fun startUpdate(context: Context, info: ReleaseInfo) {
        val app = context.applicationContext
        viewModelScope.launch { UpdateInstaller.downloadAndInstall(app, info) }
    }
}

val LocalSomna = staticCompositionLocalOf<SomnaViewModel> { error("SomnaViewModel not provided") }
