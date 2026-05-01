package me.nonick.whitelisttracker.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.nonick.whitelisttracker.data.DEFAULT_ACTIVE_RECHECK_INTERVAL_MINUTES
import me.nonick.whitelisttracker.data.DEFAULT_TIMEOUT_SECONDS
import me.nonick.whitelisttracker.data.MIN_INTERVAL_MINUTES
import me.nonick.whitelisttracker.data.TrackerRepository
import me.nonick.whitelisttracker.data.TrackerState
import me.nonick.whitelisttracker.data.parseUrlList
import me.nonick.whitelisttracker.worker.WhitelistCheckWorker
import me.nonick.whitelisttracker.worker.WhitelistWorkScheduler

class MainScreenViewModel(
  private val appContext: Context,
  private val repository: TrackerRepository,
) : ViewModel() {
  private val _saveFeedback = MutableStateFlow<SaveFeedback>(SaveFeedback.Idle)

  private val checkProgress =
    WorkManager.getInstance(appContext)
      .getWorkInfosByTagFlow(WhitelistWorkScheduler.CHECK_WORK_TAG)
      .map { workInfos ->
        workInfos
          .firstOrNull { it.state == WorkInfo.State.RUNNING }
          ?.toCheckProgress()
      }
      .onStart { emit(null) }

  val uiState: StateFlow<MainScreenUiState> =
    combine(repository.state, checkProgress, _saveFeedback) { state, progress, saveFeedback ->
        MainScreenUiState.Success(state, progress, saveFeedback) as MainScreenUiState
      }
      .catch { emit(MainScreenUiState.Error(it)) }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

  fun saveSettings(
    monitoringEnabled: Boolean,
    intervalMinutesText: String,
    activeRecheckIntervalMinutesText: String,
    timeoutSecondsText: String,
    controlUrlsText: String,
    testUrlsText: String,
  ) {
    viewModelScope.launch {
      _saveFeedback.value = SaveFeedback.Saving
      runCatching {
          repository.saveSettings(
            monitoringEnabled = monitoringEnabled,
            intervalMinutes = intervalMinutesText.toIntOrNull() ?: MIN_INTERVAL_MINUTES,
            activeRecheckIntervalMinutes =
              activeRecheckIntervalMinutesText.toIntOrNull() ?: DEFAULT_ACTIVE_RECHECK_INTERVAL_MINUTES,
            timeoutSeconds = timeoutSecondsText.toIntOrNull() ?: DEFAULT_TIMEOUT_SECONDS,
            controlUrls = parseUrlList(controlUrlsText),
            testUrls = parseUrlList(testUrlsText),
          )
          WhitelistWorkScheduler.schedule(appContext, repository.settingsSnapshot())
        }
        .onSuccess { _saveFeedback.value = SaveFeedback.Saved }
        .onFailure { throwable ->
          _saveFeedback.value = SaveFeedback.Error(throwable.message ?: "неизвестная ошибка")
        }
    }
  }

  fun runCheckNow() {
    WhitelistWorkScheduler.runNow(appContext)
  }

  private fun WorkInfo.toCheckProgress(): CheckProgress {
    val checkedCount = progress.getInt(WhitelistCheckWorker.KEY_CHECKED_COUNT, 0)
    val totalCount = progress.getInt(WhitelistCheckWorker.KEY_TOTAL_COUNT, 0)
    val label = progress.getString(WhitelistCheckWorker.KEY_LABEL) ?: "Подготовка проверки"
    return CheckProgress(checkedCount = checkedCount, totalCount = totalCount, label = label)
  }
}

data class CheckProgress(
  val checkedCount: Int,
  val totalCount: Int,
  val label: String,
) {
  val fraction: Float
    get() = if (totalCount <= 0) 0f else checkedCount.toFloat() / totalCount.toFloat()
}

sealed interface SaveFeedback {
  object Idle : SaveFeedback

  object Saving : SaveFeedback

  object Saved : SaveFeedback

  data class Error(val message: String) : SaveFeedback
}

sealed interface MainScreenUiState {
  object Loading : MainScreenUiState

  data class Error(val throwable: Throwable) : MainScreenUiState

  data class Success(
    val state: TrackerState,
    val checkProgress: CheckProgress?,
    val saveFeedback: SaveFeedback,
  ) : MainScreenUiState
}
