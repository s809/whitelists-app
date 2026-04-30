package me.nonick.whitelisttracker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.nonick.whitelisttracker.data.TrackerState
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [me.nonick.whitelisttracker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen(state = TrackerState(), onSave = { _, _, _, _, _ -> }, onRunNow = {}) }
  }

  @Test
  fun statusAndDefaultControls_exist() {
    composeTestRule.onNodeWithText("Whitelist Tracker").assertExists()
    composeTestRule.onNodeWithText("Сохранить").assertExists()
    composeTestRule.onNodeWithText("Проверить сейчас").assertExists()
    composeTestRule.onNodeWithText("Контрольные URL").assertExists()
  }
}
