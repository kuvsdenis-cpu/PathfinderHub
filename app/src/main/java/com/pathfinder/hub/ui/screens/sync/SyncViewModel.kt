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
import javax.inject.Inject

data class SyncUiState(
    val isSyncing: Boolean = false,
    val queueSize: Int = 0,
    val lastError: String? = null
)

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncScheduler: SyncScheduler,
    private val syncDao: SyncDao
) : ViewModel() {

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
}
