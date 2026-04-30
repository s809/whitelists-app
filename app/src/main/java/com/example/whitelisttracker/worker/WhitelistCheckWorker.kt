package me.nonick.whitelisttracker.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import me.nonick.whitelisttracker.data.CheckSummary
import me.nonick.whitelisttracker.data.ModeTransition
import me.nonick.whitelisttracker.data.TrackerRepository
import me.nonick.whitelisttracker.data.TrackerSettings
import java.net.HttpURLConnection
import java.net.URL

class WhitelistCheckWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result {
    val repository = TrackerRepository(applicationContext)
    val settings = repository.settingsSnapshot()

    if (!settings.monitoringEnabled) {
      WhitelistNotifications.cancelCheckProgress(applicationContext)
      WhitelistNotifications.cancelActive(applicationContext)
      return Result.success()
    }

    val totalCount = settings.controlUrls.size + settings.testUrls.size
    suspend fun publishProgress(checkedCount: Int, label: String) {
      setProgress(
        workDataOf(
          KEY_CHECKED_COUNT to checkedCount,
          KEY_TOTAL_COUNT to totalCount,
          KEY_LABEL to label,
        ),
      )
      WhitelistNotifications.showCheckProgress(applicationContext, checkedCount, totalCount, label)
    }

    try {
      publishProgress(0, "Подготовка проверки")
      val probeSummary =
        probeUrlsInParallel(settings) { checkedCount ->
          publishProgress(checkedCount, "Проверено: $checkedCount/$totalCount")
        }

      val summary =
        CheckSummary(
          checkedAtMillis = System.currentTimeMillis(),
          controlSuccessCount = probeSummary.controlSuccessCount,
          controlTotalCount = settings.controlUrls.size,
          testSuccessCount = probeSummary.testSuccessCount,
          testTotalCount = settings.testUrls.size,
          error = null,
        )

      when (val transition = repository.applyCheckSummary(summary)) {
        is ModeTransition.Entered -> WhitelistNotifications.showActive(applicationContext, transition.startedAtMillis)
        is ModeTransition.StillActive -> WhitelistNotifications.showActive(applicationContext, transition.startedAtMillis)
        is ModeTransition.Ended ->
          WhitelistNotifications.showEnded(applicationContext, transition.endedAtMillis, transition.durationMillis)
        ModeTransition.StillInactive -> WhitelistNotifications.cancelActive(applicationContext)
      }

      return Result.success()
    } finally {
      WhitelistNotifications.cancelCheckProgress(applicationContext)
    }
  }

  private suspend fun probeUrlsInParallel(
    settings: TrackerSettings,
    onProgress: suspend (checkedCount: Int) -> Unit,
  ): ProbeSummary = coroutineScope {
    val targets =
      settings.controlUrls.map { ProbeTarget(ProbeKind.Control, it) } +
        settings.testUrls.map { ProbeTarget(ProbeKind.Test, it) }
    val results = Channel<ProbeResult>(Channel.UNLIMITED)

    val jobs =
      targets.map { target ->
        async {
          val success = probeUrl(target.url, settings.timeoutSeconds)
          results.send(ProbeResult(target.kind, success))
        }
      }

    var checkedCount = 0
    var controlSuccessCount = 0
    var testSuccessCount = 0

    repeat(targets.size) {
      val result = results.receive()
      checkedCount += 1

      if (result.success) {
        when (result.kind) {
          ProbeKind.Control -> controlSuccessCount += 1
          ProbeKind.Test -> testSuccessCount += 1
        }
      }

      onProgress(checkedCount)
    }

    jobs.awaitAll()
    results.close()

    ProbeSummary(
      controlSuccessCount = controlSuccessCount,
      testSuccessCount = testSuccessCount,
    )
  }

  private suspend fun probeUrl(url: String, timeoutSeconds: Int): Boolean =
    withContext(Dispatchers.IO) {
      var connection: HttpURLConnection? = null
      try {
        connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = timeoutSeconds * 1000
        connection.readTimeout = timeoutSeconds * 1000
        connection.requestMethod = "GET"
        connection.responseCode >= 100
      } catch (_: Exception) {
        false
      } finally {
        connection?.disconnect()
      }
    }

  companion object {
    const val KEY_CHECKED_COUNT = "checked_count"
    const val KEY_TOTAL_COUNT = "total_count"
    const val KEY_LABEL = "label"
  }
}

private enum class ProbeKind {
  Control,
  Test,
}

private data class ProbeTarget(
  val kind: ProbeKind,
  val url: String,
)

private data class ProbeResult(
  val kind: ProbeKind,
  val success: Boolean,
)

private data class ProbeSummary(
  val controlSuccessCount: Int,
  val testSuccessCount: Int,
)
