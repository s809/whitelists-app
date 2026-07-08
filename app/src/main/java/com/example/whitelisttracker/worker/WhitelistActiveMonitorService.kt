package me.nonick.whitelisttracker.worker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.nonick.whitelisttracker.data.ModeTransition
import me.nonick.whitelisttracker.data.TrackerRepository

class WhitelistActiveMonitorService : Service() {
  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var monitorJob: Job? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    val startedAtMillis =
      intent?.getLongExtra(EXTRA_STARTED_AT_MILLIS, 0L)?.takeIf { it > 0L }
        ?: System.currentTimeMillis()

    if (!promoteToForeground(startedAtMillis)) {
      stopSelf(startId)
      return START_NOT_STICKY
    }

    startMonitorLoop()
    return START_STICKY
  }

  override fun onDestroy() {
    monitorJob?.cancel()
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null

  private fun promoteToForeground(startedAtMillis: Long): Boolean =
    runCatching {
        ServiceCompat.startForeground(
          this,
          WhitelistNotifications.ACTIVE_NOTIFICATION_ID,
          WhitelistNotifications.activeNotification(this, startedAtMillis),
          foregroundServiceType(),
        )
      }
      .isSuccess

  private fun foregroundServiceType(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } else {
      0
    }

  private fun startMonitorLoop() {
    if (monitorJob?.isActive == true) return

    monitorJob =
      serviceScope.launch {
        val repository = TrackerRepository(applicationContext)
        while (isActive) {
          val state = repository.state.first()
          if (!state.settings.monitoringEnabled || !state.status.whitelistModeActive) {
            WhitelistNotifications.cancelActive(applicationContext)
            stopSelf()
            return@launch
          }

          promoteToForeground(state.status.modeStartedAtMillis ?: System.currentTimeMillis())
          delay(state.settings.activeRecheckIntervalMinutes * 60_000L)
          runActiveCheck()
        }
      }
  }

  private suspend fun runActiveCheck() {
    try {
      when (
        val result =
          WhitelistCheckRunner.run(applicationContext) { progress ->
            WhitelistNotifications.showCheckProgress(
              applicationContext,
              progress.checkedCount,
              progress.totalCount,
              progress.label,
            )
          }
      ) {
        CheckRunResult.MonitoringDisabled -> stopActiveMonitoring()
        is CheckRunResult.Completed -> handleTransition(result.transition)
      }
    } finally {
      WhitelistNotifications.cancelCheckProgress(applicationContext)
    }
  }

  private fun handleTransition(transition: ModeTransition) {
    when (transition) {
      is ModeTransition.Entered -> promoteToForeground(transition.startedAtMillis)
      is ModeTransition.StillActive -> promoteToForeground(transition.startedAtMillis)
      is ModeTransition.ConnectionUnavailable -> {
        if (transition.wasActive) {
          promoteToForeground(transition.startedAtMillis ?: System.currentTimeMillis())
        } else {
          stopActiveMonitoring()
        }
      }
      is ModeTransition.Ended -> {
        WhitelistNotifications.showEnded(applicationContext, transition.startedAtMillis, transition.durationMillis)
        stopSelf()
      }
      ModeTransition.StillInactive -> stopActiveMonitoring()
    }
  }

  private fun stopActiveMonitoring() {
    WhitelistNotifications.cancelActive(applicationContext)
    stopSelf()
  }

  companion object {
    private const val EXTRA_STARTED_AT_MILLIS = "started_at_millis"

    fun start(context: Context, startedAtMillis: Long): Boolean =
      runCatching {
          val intent =
            Intent(context, WhitelistActiveMonitorService::class.java)
              .putExtra(EXTRA_STARTED_AT_MILLIS, startedAtMillis)
          ContextCompat.startForegroundService(context, intent)
        }
        .isSuccess

    fun stop(context: Context) {
      context.stopService(Intent(context, WhitelistActiveMonitorService::class.java))
    }
  }
}
