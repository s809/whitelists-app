package me.nonick.whitelisttracker.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import me.nonick.whitelisttracker.data.TrackerState
import org.junit.Rule
import org.junit.Test

/** UI tests for [me.nonick.whitelisttracker.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun mainScreen_showsStatusAndActions() {
    composeTestRule.setContent { MainScreen(state = TrackerState(), onRunNow = {}) }

    composeTestRule.onNodeWithText("Whitelist Tracker").assertExists()
    composeTestRule.onNodeWithText("Проверить сейчас").assertExists()
    composeTestRule.onNodeWithText("Настройки").assertExists()
    composeTestRule.onNodeWithText("Статус").assertExists()
    composeTestRule.onNodeWithText("Текущие настройки").assertExists()
  }

  @Test
  fun settingsScreen_showsMovedSettings() {
    composeTestRule.setContent {
      SettingsScreen(
        settings = TrackerState().settings,
        onBack = {},
        onSave = { _, _, _, _, _, _, _, _ -> },
      )
    }

    composeTestRule.onNodeWithText("Сохранить").assertExists()
    composeTestRule.onNodeWithText("Контрольные URL").assertExists()
    composeTestRule.onNodeWithText("Перепроверка, мин").assertExists()
    composeTestRule.onNodeWithText("Попытки").assertExists()
    composeTestRule.onNodeWithText("Пауза между попытками, сек").assertExists()
  }
}
