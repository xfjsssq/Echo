package com.echo.recorder.ui.list

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echo.recorder.ServiceLocator
import com.echo.recorder.domain.model.Recording
import com.echo.recorder.domain.recording.RecordingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** 录音列表 ViewModel: 订阅仓库流, 对外暴露 StateFlow. */
class ListViewModel(
    context: Context,
    private val repository: RecordingRepository = ServiceLocator.repository(context),
) : ViewModel() {

    val recordings: StateFlow<List<Recording>> =
        repository.getAll().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )
}
