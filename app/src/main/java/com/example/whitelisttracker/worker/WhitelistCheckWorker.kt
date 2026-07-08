package me.nonick.whitelisttracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import me.nonick.whitelisttracker.data.ModeTransition

class WhitelistCheckWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    suspend fun publishProgress(progress: CheckProgressUpdate) {
      setProgress(
        workDataOf(
          KEY_CHECKED_COUNT to progress.checkedCount,
          KEY_TOTAL_COUNT to progress.totalCount,
          KEY_LABEL to progress.label,
        ),
      )
      WhitelistNotifications.showCheckProgress(
        applicationContext,
        progress.checkedCount,
        progress.totalCount,
        progress.label,
      )
    }

    try {
      when (val result = WhitelistCheckRunner.run(applicationContext, ::publishProgress)) {
        CheckRunResult.MonitoringDisabled -> {
          WhitelistWorkScheduler.cancelActiveRechecks(applicationContext, id)
          WhitelistActiveMonitorService.stop(applicationContext)
          WhitelistNotifications.cancelCheckProgress(applicationContext)
          WhitelistNotifications.cancelActive(applicationContext)
        }
        is CheckRunResult.Completed -> handleTransition(result)
      }

      return Result.success()
    } finally {
      WhitelistNotifications.cancelCheckProgress(applicationContext)
    }
  }

  private suspend fun handleTransition(result: CheckRunResult.Completed) {
    when (val transition = result.transition) {
      is ModeTransition.Entered -> {
        startActiveMonitorOrScheduleRecheck(transition.startedAtMillis, result.settings.activeRecheckIntervalMinutes)
      }
      is ModeTransition.StillActive -> {
        startActiveMonitorOrScheduleRecheck(transition.startedAtMillis, result.settings.activeRecheckIntervalMinutes)
      }
      is ModeTransition.Ended -> {
        WhitelistActiveMonitorService.stop(applicationContext)
        WhitelistNotifications.showEnded(applicationContext, transition.startedAtMillis, transition.durationMillis)
        WhitelistWorkScheduler.cancelActiveRechecks(applicationContext, id)
      }
      is ModeTransition.ConnectionUnavailable -> {
        if (transition.wasActive) {
          val startedAtMillis = transition.startedAtMillis ?: System.currentTimeMillis()
          startActiveMonitorOrScheduleRecheck(startedAtMillis, result.settings.activeRecheckIntervalMinutes)
        } else {
          WhitelistActiveMonitorService.stop(applicationContext)
          WhitelistNotifications.cancelActive(applicationContext)
          WhitelistWorkScheduler.cancelActiveRechecks(applicationContext, id)
        }
      }
      ModeTransition.StillInactive -> {
        WhitelistActiveMonitorService.stop(applicationContext)
        WhitelistNotifications.cancelActive(applicationContext)
        WhitelistWorkScheduler.cancelActiveRechecks(applicationContext, id)
      }
    }
  }

  private suspend fun startActiveMonitorOrScheduleRecheck(startedAtMillis: Long, activeRecheckIntervalMinutes: Int) {
    if (WhitelistActiveMonitorService.start(applicationContext, startedAtMillis)) {
      WhitelistWorkScheduler.cancelActiveRechecks(applicationContext, id)
    } else {
      WhitelistNotifications.showActive(applicationContext, startedAtMillis)
      WhitelistWorkScheduler.scheduleActiveRecheck(applicationContext, id, activeRecheckIntervalMinutes)
    }
  }

  companion object {
    const val KEY_CHECKED_COUNT = "checked_count"
    const val KEY_TOTAL_COUNT = "total_count"
    const val KEY_LABEL = "label"
  }
}
