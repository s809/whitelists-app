package me.nonick.whitelisttracker.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

const val MIN_INTERVAL_MINUTES = 15
const val DEFAULT_ACTIVE_RECHECK_INTERVAL_MINUTES = 5
const val DEFAULT_TIMEOUT_SECONDS = 5
const val DEFAULT_PROBE_ATTEMPTS = 3
const val DEFAULT_PROBE_RETRY_DELAY_SECONDS = 1

object TrackerDefaults {
  val controlUrls = listOf("https://ya.ru/", "https://vk.com/")
  val testUrls = listOf("https://connectivitycheck.gstatic.com/generate_204")
}

data class TrackerSettings(
  val monitoringEnabled: Boolean = true,
  val intervalMinutes: Int = MIN_INTERVAL_MINUTES,
  val activeRecheckIntervalMinutes: Int = DEFAULT_ACTIVE_RECHECK_INTERVAL_MINUTES,
  val timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,
  val probeAttempts: Int = DEFAULT_PROBE_ATTEMPTS,
  val probeRetryDelaySeconds: Int = DEFAULT_PROBE_RETRY_DELAY_SECONDS,
  val controlUrls: List<String> = TrackerDefaults.controlUrls,
  val testUrls: List<String> = TrackerDefaults.testUrls,
)

data class TrackerStatus(
  val whitelistModeActive: Boolean = false,
  val modeStartedAtMillis: Long? = null,
  val lastEndedAtMillis: Long? = null,
  val lastDurationMillis: Long? = null,
  val lastCheckAtMillis: Long? = null,
  val lastConnectedAtMillis: Long? = null,
  val controlSuccessCount: Int = 0,
  val controlTotalCount: Int = TrackerDefaults.controlUrls.size,
  val testSuccessCount: Int = 0,
  val testTotalCount: Int = TrackerDefaults.testUrls.size,
  val lastError: String? = null,
)

data class TrackerState(
  val settings: TrackerSettings = TrackerSettings(),
  val status: TrackerStatus = TrackerStatus(),
)

data class CheckSummary(
  val checkedAtMillis: Long,
  val controlSuccessCount: Int,
  val controlTotalCount: Int,
  val testSuccessCount: Int,
  val testTotalCount: Int,
  val error: String? = null,
)

sealed interface ModeTransition {
  data class Entered(val startedAtMillis: Long) : ModeTransition
  data class StillActive(val startedAtMillis: Long) : ModeTransition
  data class Ended(val startedAtMillis: Long, val endedAtMillis: Long, val durationMillis: Long) : ModeTransition
  data class ConnectionUnavailable(val wasActive: Boolean, val startedAtMillis: Long?) : ModeTransition
  object StillInactive : ModeTransition
}

fun hasAnySuccessfulTarget(controlSuccessCount: Int, testSuccessCount: Int): Boolean =
  controlSuccessCount > 0 || testSuccessCount > 0

fun isWhitelistMode(controlSuccessCount: Int, testSuccessCount: Int): Boolean =
  controlSuccessCount > 0 && testSuccessCount == 0

fun resolveModeEndAtMillis(
  checkedAtMillis: Long,
  previousLastCheckAtMillis: Long?,
  previousLastConnectedAtMillis: Long?,
): Long =
  if (
    previousLastCheckAtMillis != null &&
      previousLastConnectedAtMillis != null &&
      previousLastCheckAtMillis > previousLastConnectedAtMillis
  ) {
    previousLastConnectedAtMillis
  } else {
    checkedAtMillis
  }

fun parseUrlList(input: String): List<String> =
  input
    .lineSequence()
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .map { url -> if ("://" in url) url else "https://$url" }
    .distinct()
    .toList()

fun sanitizeIntervalMinutes(value: Int): Int = max(MIN_INTERVAL_MINUTES, value)

fun sanitizeActiveRecheckIntervalMinutes(value: Int): Int = max(1, value)

fun sanitizeTimeoutSeconds(value: Int): Int = max(1, value)

fun sanitizeProbeAttempts(value: Int): Int = max(1, value)

fun sanitizeProbeRetryDelaySeconds(value: Int): Int = max(0, value)

fun formatDurationMillis(durationMillis: Long): String {
  val totalSeconds = max(0L, durationMillis / 1000L)
  val hours = totalSeconds / 3600L
  val minutes = (totalSeconds % 3600L) / 60L
  val seconds = totalSeconds % 60L

  return buildList {
      if (hours > 0) add("$hours ч")
      if (minutes > 0 || hours > 0) add("$minutes мин")
      add("$seconds сек")
    }
    .joinToString(" ")
}

fun formatTimestamp(millis: Long): String =
  SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(millis))
