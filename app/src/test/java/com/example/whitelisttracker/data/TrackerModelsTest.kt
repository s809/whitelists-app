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
}
