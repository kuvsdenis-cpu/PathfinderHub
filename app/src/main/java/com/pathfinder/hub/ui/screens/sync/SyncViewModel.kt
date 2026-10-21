package com.pathfinder.hub.ui.screens.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pathfinder.hub.data.local.dao.SyncDao
import com.pathfinder.hub.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext

data class SyncUiState(
    val isSyncing: Boolean = false,
    val queueSize: Int = 0,
    val lastError: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncDao: SyncDao,
    @ApplicationContext private val app: Context) : ViewModel() {

    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            syncDao.observeStatus().collect { status ->
                status?.let {
                    _state.update { s ->
                        s.copy(
                            isSyncing = it.isSyncing,
                            queueSize = it.queueSize,
                            lastError = it.lastError
                        )
                    }
                }
            }
        }
    }

    fun syncNow() {
        syncScheduler.syncNow()
    }

    /** Принудительный пересид уровней из assets/levels.json. */
    fun forceReseedLevels() {
        val ctx = app
        val req = androidx.work.OneTimeWorkRequestBuilder<com.pathfinder.hub.data.local.worker.SeedDatabaseWorker>()
            .setInputData(androidx.work.workDataOf("forceLevels" to true))
            .build()
        androidx.work.WorkManager.getInstance(ctx).enqueue(req)
    }
}
