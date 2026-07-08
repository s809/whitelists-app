package me.nonick.whitelisttracker.worker

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.nonick.whitelisttracker.data.CheckSummary
import me.nonick.whitelisttracker.data.ModeTransition
import me.nonick.whitelisttracker.data.TrackerRepository
import me.nonick.whitelisttracker.data.TrackerSettings
import java.net.HttpURLConnection
import java.net.URL

object WhitelistCheckRunner {
  suspend fun run(
    context: Context,
    onProgress: suspend (CheckProgressUpdate) -> Unit,
  ): CheckRunResult {
    val repository = TrackerRepository(context.applicationContext)
    val settings = repository.settingsSnapshot()

    if (!settings.monitoringEnabled) {
      return CheckRunResult.MonitoringDisabled
    }

    val totalCount = settings.controlUrls.size + settings.testUrls.size
    onProgress(CheckProgressUpdate(checkedCount = 0, totalCount = totalCount, label = "Подготовка проверки"))

    val probeSummary =
      probeUrlsInParallel(settings) { checkedCount ->
        onProgress(CheckProgressUpdate(checkedCount, totalCount, "Проверено: $checkedCount/$totalCount"))
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

    return CheckRunResult.Completed(
      transition = repository.applyCheckSummary(summary),
      settings = settings,
    )
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
          val success =
            probeUrl(
              url = target.url,
              timeoutSeconds = settings.timeoutSeconds,
              attempts = settings.probeAttempts,
              retryDelayMillis = settings.probeRetryDelaySeconds * 1_000L,
            )
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

  private suspend fun probeUrl(
    url: String,
    timeoutSeconds: Int,
    attempts: Int,
    retryDelayMillis: Long,
  ): Boolean {
    repeat(attempts) { attemptIndex ->
      if (probeUrlOnce(url, timeoutSeconds)) return true

      if (attemptIndex < attempts - 1) {
        delay(retryDelayMillis)
      }
    }

    return false
  }

  private suspend fun probeUrlOnce(url: String, timeoutSeconds: Int): Boolean =
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
}

data class CheckProgressUpdate(
  val checkedCount: Int,
  val totalCount: Int,
  val label: String,
)

sealed interface CheckRunResult {
  object MonitoringDisabled : CheckRunResult

  data class Completed(
    val transition: ModeTransition,
    val settings: TrackerSettings,
  ) : CheckRunResult
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
