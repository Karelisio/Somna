package com.karelisio.somna.ui.journal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.karelisio.somna.AppContainer
import com.karelisio.somna.data.SleepEntry
import com.karelisio.somna.data.SleepStats
import com.karelisio.somna.data.SomnaSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class JournalViewModel(private val container: AppContainer) : ViewModel() {
    var period by mutableIntStateOf(7)

    val entries: StateFlow<List<SleepEntry>?> =
        container.sleep.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<SleepStats?> = snapshotFlow { period }
        .flatMapLatest { container.sleep.stats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<SomnaSettings> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SomnaSettings())

    fun save(entry: SleepEntry) = viewModelScope.launch {
        if (entry.id == 0L) container.sleep.add(entry) else container.sleep.update(entry)
    }

    fun delete(entry: SleepEntry) = viewModelScope.launch { container.sleep.delete(entry) }
    fun restore(entry: SleepEntry) = viewModelScope.launch { container.sleep.add(entry) }
}
