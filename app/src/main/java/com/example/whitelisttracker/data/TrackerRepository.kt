package me.nonick.whitelisttracker.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.trackerDataStore by preferencesDataStore(name = "whitelist_tracker")

class TrackerRepository(private val context: Context) {
  val state: Flow<TrackerState> = context.trackerDataStore.data.map { prefs ->
    val settings =
      TrackerSettings(
        monitoringEnabled = prefs[Keys.monitoringEnabled] ?: true,
        intervalMinutes = sanitizeIntervalMinutes(prefs[Keys.intervalMinutes] ?: MIN_INTERVAL_MINUTES),
        activeRecheckIntervalMinutes =
          sanitizeActiveRecheckIntervalMinutes(
            prefs[Keys.activeRecheckIntervalMinutes] ?: DEFAULT_ACTIVE_RECHECK_INTERVAL_MINUTES,
          ),
        timeoutSeconds = sanitizeTimeoutSeconds(prefs[Keys.timeoutSeconds] ?: DEFAULT_TIMEOUT_SECONDS),
        controlUrls = decodeUrls(prefs[Keys.controlUrls], TrackerDefaults.controlUrls),
        testUrls = decodeUrls(prefs[Keys.testUrls], TrackerDefaults.testUrls),
      )

    val status =
      TrackerStatus(
        whitelistModeActive = prefs[Keys.whitelistModeActive] ?: false,
        modeStartedAtMillis = prefs[Keys.modeStartedAtMillis].takeIfPresent(),
        lastEndedAtMillis = prefs[Keys.lastEndedAtMillis].takeIfPresent(),
        lastDurationMillis = prefs[Keys.lastDurationMillis].takeIfPresent(),
        lastCheckAtMillis = prefs[Keys.lastCheckAtMillis].takeIfPresent(),
        lastConnectedAtMillis = prefs[Keys.lastConnectedAtMillis].takeIfPresent(),
        controlSuccessCount = prefs[Keys.controlSuccessCount] ?: 0,
        controlTotalCount = prefs[Keys.controlTotalCount] ?: settings.controlUrls.size,
        testSuccessCount = prefs[Keys.testSuccessCount] ?: 0,
        testTotalCount = prefs[Keys.testTotalCount] ?: settings.testUrls.size,
        lastError = prefs[Keys.lastError]?.ifBlank { null },
      )

    TrackerState(settings = settings, status = status)
  }

  suspend fun settingsSnapshot(): TrackerSettings = state.first().settings

  suspend fun saveSettings(
    monitoringEnabled: Boolean,
    intervalMinutes: Int,
    activeRecheckIntervalMinutes: Int,
    timeoutSeconds: Int,
    controlUrls: List<String>,
    testUrls: List<String>,
  ) {
    context.trackerDataStore.edit { prefs ->
      prefs[Keys.monitoringEnabled] = monitoringEnabled
      prefs[Keys.intervalMinutes] = sanitizeIntervalMinutes(intervalMinutes)
      prefs[Keys.activeRecheckIntervalMinutes] = sanitizeActiveRecheckIntervalMinutes(activeRecheckIntervalMinutes)
      prefs[Keys.timeoutSeconds] = sanitizeTimeoutSeconds(timeoutSeconds)
      prefs[Keys.controlUrls] = encodeUrls(controlUrls.ifEmpty { TrackerDefaults.controlUrls })
      prefs[Keys.testUrls] = encodeUrls(testUrls.ifEmpty { TrackerDefaults.testUrls })
    }
  }

  suspend fun applyCheckSummary(summary: CheckSummary): ModeTransition {
    var transition: ModeTransition = ModeTransition.StillInactive
    context.trackerDataStore.edit { prefs ->
      val wasActive = prefs[Keys.whitelistModeActive] ?: false
      val previousStart = prefs[Keys.modeStartedAtMillis]
      val previousLastCheckAt = prefs[Keys.lastCheckAtMillis].takeIfPresent()
      val previousLastConnectedAt = prefs[Keys.lastConnectedAtMillis].takeIfPresent()
      val anyTargetResponded = hasAnySuccessfulTarget(summary.controlSuccessCount, summary.testSuccessCount)
      val detected = isWhitelistMode(summary.controlSuccessCount, summary.testSuccessCount)

      prefs[Keys.lastCheckAtMillis] = summary.checkedAtMillis
      if (anyTargetResponded) {
        prefs[Keys.lastConnectedAtMillis] = summary.checkedAtMillis
      }
      prefs[Keys.controlSuccessCount] = summary.controlSuccessCount
      prefs[Keys.controlTotalCount] = summary.controlTotalCount
      prefs[Keys.testSuccessCount] = summary.testSuccessCount
      prefs[Keys.testTotalCount] = summary.testTotalCount
      prefs[Keys.lastError] = summary.error.orEmpty()

      transition =
        when {
          !anyTargetResponded -> {
            ModeTransition.ConnectionUnavailable(wasActive, previousStart.takeIfPresent())
          }
          detected && !wasActive -> {
            prefs[Keys.whitelistModeActive] = true
            prefs[Keys.modeStartedAtMillis] = summary.checkedAtMillis
            ModeTransition.Entered(summary.checkedAtMillis)
          }
          detected -> {
            val startedAt = previousStart ?: summary.checkedAtMillis
            prefs[Keys.whitelistModeActive] = true
            prefs[Keys.modeStartedAtMillis] = startedAt
            ModeTransition.StillActive(startedAt)
          }
          wasActive -> {
            val startedAt = previousStart ?: summary.checkedAtMillis
            val endedAt =
              resolveModeEndAtMillis(
                checkedAtMillis = summary.checkedAtMillis,
                previousLastCheckAtMillis = previousLastCheckAt,
                previousLastConnectedAtMillis = previousLastConnectedAt,
              )
            val duration = (endedAt - startedAt).coerceAtLeast(0L)
            prefs[Keys.whitelistModeActive] = false
            prefs[Keys.modeStartedAtMillis] = 0L
            prefs[Keys.lastEndedAtMillis] = endedAt
            prefs[Keys.lastDurationMillis] = duration
            ModeTransition.Ended(startedAt, endedAt, duration)
          }
          else -> {
            prefs[Keys.whitelistModeActive] = false
            prefs[Keys.modeStartedAtMillis] = 0L
            ModeTransition.StillInactive
          }
        }
    }
    return transition
  }

  private fun Long?.takeIfPresent(): Long? = this?.takeIf { it > 0L }

  private fun encodeUrls(urls: List<String>): String = urls.joinToString("\n")

  private fun decodeUrls(value: String?, defaults: List<String>): List<String> =
    value?.let(::parseUrlList).takeUnless { it.isNullOrEmpty() } ?: defaults

  private object Keys {
    val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
    val intervalMinutes = intPreferencesKey("interval_minutes")
    val activeRecheckIntervalMinutes = intPreferencesKey("active_recheck_interval_minutes")
    val timeoutSeconds = intPreferencesKey("timeout_seconds")
    val controlUrls = stringPreferencesKey("control_urls")
    val testUrls = stringPreferencesKey("test_urls")
    val whitelistModeActive = booleanPreferencesKey("whitelist_mode_active")
    val modeStartedAtMillis = longPreferencesKey("mode_started_at_millis")
    val lastEndedAtMillis = longPreferencesKey("last_ended_at_millis")
    val lastDurationMillis = longPreferencesKey("last_duration_millis")
    val lastCheckAtMillis = longPreferencesKey("last_check_at_millis")
    val lastConnectedAtMillis = longPreferencesKey("last_connected_at_millis")
    val controlSuccessCount = intPreferencesKey("control_success_count")
    val controlTotalCount = intPreferencesKey("control_total_count")
    val testSuccessCount = intPreferencesKey("test_success_count")
    val testTotalCount = intPreferencesKey("test_total_count")
    val lastError = stringPreferencesKey("last_error")
  }
}
