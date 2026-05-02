package me.nonick.whitelisttracker.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.nonick.whitelisttracker.data.TrackerSettings
import me.nonick.whitelisttracker.data.sanitizeIntervalMinutes
import java.util.concurrent.TimeUnit
import java.util.UUID

object WhitelistWorkScheduler {
  const val CHECK_WORK_TAG = "whitelist_tracker_check"

  private const val PERIODIC_WORK_NAME = "whitelist_tracker_periodic_check"
  private const val NETWORK_CHANGE_WORK_NAME = "whitelist_tracker_network_change_check"
  private const val ACTIVE_RECHECK_TAG = "whitelist_tracker_active_recheck"
  private const val NETWORK_CHANGE_DEBOUNCE_SECONDS = 15L

  fun schedule(context: Context, settings: TrackerSettings) {
    val workManager = WorkManager.getInstance(context)
    if (!settings.monitoringEnabled) {
      workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
      workManager.cancelUniqueWork(NETWORK_CHANGE_WORK_NAME)
      cancelActiveRechecks(context)
      WhitelistNotifications.cancelCheckProgress(context)
      WhitelistNotifications.cancelActive(context)
      return
    }

    val request =
      PeriodicWorkRequestBuilder<WhitelistCheckWorker>(
          sanitizeIntervalMinutes(settings.intervalMinutes).toLong(),
          TimeUnit.MINUTES,
        )
        .addTag(CHECK_WORK_TAG)
        .setConstraints(networkConstraints())
        .build()

    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
  }

  fun runNow(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<WhitelistCheckWorker>()
        .addTag(CHECK_WORK_TAG)
        .setConstraints(networkConstraints())
        .build()
    WorkManager.getInstance(context).enqueue(request)
  }

  fun runAfterNetworkChange(context: Context) {
    val request =
      OneTimeWorkRequestBuilder<WhitelistCheckWorker>()
        .addTag(CHECK_WORK_TAG)
        .setInitialDelay(NETWORK_CHANGE_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
        .setConstraints(networkConstraints())
        .build()

    WorkManager
      .getInstance(context)
      .enqueueUniqueWork(NETWORK_CHANGE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
  }

  suspend fun scheduleActiveRecheck(context: Context, currentWorkId: UUID, delayMinutes: Int) {
    val workManager = WorkManager.getInstance(context)
    val hasQueuedRecheck =
      workManager.activeRechecks().any { workInfo ->
        workInfo.id != currentWorkId && !workInfo.state.isFinished
      }

    if (hasQueuedRecheck) return

    val request =
      OneTimeWorkRequestBuilder<WhitelistCheckWorker>()
        .addTag(CHECK_WORK_TAG)
        .addTag(ACTIVE_RECHECK_TAG)
        .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
        .setConstraints(networkConstraints())
        .build()

    workManager.enqueue(request)
  }

  fun cancelActiveRechecks(context: Context) {
    WorkManager.getInstance(context).cancelAllWorkByTag(ACTIVE_RECHECK_TAG)
  }

  suspend fun cancelActiveRechecks(context: Context, currentWorkId: UUID) {
    val workManager = WorkManager.getInstance(context)
    workManager
      .activeRechecks()
      .filter { workInfo -> workInfo.id != currentWorkId && !workInfo.state.isFinished }
      .forEach { workInfo -> workManager.cancelWorkById(workInfo.id) }
  }

  private suspend fun WorkManager.activeRechecks(): List<WorkInfo> =
    withContext(Dispatchers.IO) { getWorkInfosByTag(ACTIVE_RECHECK_TAG).get() }

  private fun networkConstraints(): Constraints =
    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
