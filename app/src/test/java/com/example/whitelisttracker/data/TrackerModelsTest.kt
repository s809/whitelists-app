package me.nonick.whitelisttracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerModelsTest {
  @Test
  fun whitelistMode_whenControlRespondsAndTestDoesNotRespond_isActive() {
    assertTrue(isWhitelistMode(controlSuccessCount = 1, testSuccessCount = 0))
  }

  @Test
  fun whitelistMode_whenControlDoesNotRespond_isInactive() {
    assertFalse(isWhitelistMode(controlSuccessCount = 0, testSuccessCount = 0))
  }

  @Test
  fun whitelistMode_whenAnyTestResponds_isInactive() {
    assertFalse(isWhitelistMode(controlSuccessCount = 2, testSuccessCount = 1))
  }

  @Test
  fun successfulTarget_whenAnyTargetResponds_isTrue() {
    assertTrue(hasAnySuccessfulTarget(controlSuccessCount = 0, testSuccessCount = 1))
    assertFalse(hasAnySuccessfulTarget(controlSuccessCount = 0, testSuccessCount = 0))
  }

  @Test
  fun modeEndTime_afterConnectionGap_usesPreviousSuccessfulConnection() {
    assertEquals(
      1_000L,
      resolveModeEndAtMillis(
        checkedAtMillis = 3_000L,
        previousLastCheckAtMillis = 2_000L,
        previousLastConnectedAtMillis = 1_000L,
      ),
    )
  }

  @Test
  fun modeEndTime_withoutConnectionGap_usesCurrentCheck() {
    assertEquals(
      3_000L,
      resolveModeEndAtMillis(
        checkedAtMillis = 3_000L,
        previousLastCheckAtMillis = 1_000L,
        previousLastConnectedAtMillis = 1_000L,
      ),
    )
  }

  @Test
  fun durationFormatting_includesHoursMinutesAndSeconds() {
    assertEquals("1 ч 2 мин 3 сек", formatDurationMillis(3_723_000L))
  }

  @Test
  fun parseUrlList_trimsAddsHttpsAndRemovesDuplicates() {
    assertEquals(
      listOf("https://ya.ru/", "https://google.com/"),
      parseUrlList(" ya.ru/ \nhttps://google.com/\nya.ru/\n"),
    )
  }

  @Test
  fun interval_hasAndroidMinimum() {
    assertEquals(MIN_INTERVAL_MINUTES, sanitizeIntervalMinutes(5))
    assertEquals(30, sanitizeIntervalMinutes(30))
  }

  @Test
  fun activeRecheckInterval_hasFiveMinuteDefaultAndOneMinuteMinimum() {
    assertEquals(5, TrackerSettings().activeRecheckIntervalMinutes)
    assertEquals(1, sanitizeActiveRecheckIntervalMinutes(0))
    assertEquals(5, sanitizeActiveRecheckIntervalMinutes(5))
  }

  @Test
  fun probeAttempts_hasThreeAttemptDefaultAndOneAttemptMinimum() {
    assertEquals(3, TrackerSettings().probeAttempts)
    assertEquals(1, sanitizeProbeAttempts(0))
    assertEquals(4, sanitizeProbeAttempts(4))
  }

  @Test
  fun probeRetryDelay_hasOneSecondDefaultAndZeroSecondMinimum() {
    assertEquals(1, TrackerSettings().probeRetryDelaySeconds)
    assertEquals(0, sanitizeProbeRetryDelaySeconds(-1))
    assertEquals(2, sanitizeProbeRetryDelaySeconds(2))
  }
}
