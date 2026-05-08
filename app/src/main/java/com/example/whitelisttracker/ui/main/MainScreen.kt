package me.nonick.whitelisttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.delay
import me.nonick.whitelisttracker.Settings
import me.nonick.whitelisttracker.data.TrackerRepository
import me.nonick.whitelisttracker.data.TrackerState
import me.nonick.whitelisttracker.data.formatDurationMillis
import me.nonick.whitelisttracker.data.formatTimestamp
import me.nonick.whitelisttracker.theme.MyApplicationTheme

@Composable
fun MainScreen(
  onItemClick: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
  providedViewModel: MainScreenViewModel? = null,
) {
  val appContext = LocalContext.current.applicationContext
  val viewModel =
    providedViewModel ?: viewModel {
      MainScreenViewModel(appContext, TrackerRepository(appContext))
    }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  when (val uiState = state) {
    MainScreenUiState.Loading -> {
      LoadingState(modifier)
    }
    is MainScreenUiState.Success -> {
      MainScreen(
        state = uiState.state,
        checkProgress = uiState.checkProgress,
        onRunNow = viewModel::runCheckNow,
        onSettingsClick = { onItemClick(Settings) },
        modifier = modifier,
      )
    }
    is MainScreenUiState.Error -> {
      Text("Error loading data: ${uiState.throwable.message}")
    }
  }
}

@Composable
internal fun MainScreen(
  state: TrackerState,
  checkProgress: CheckProgress? = null,
  onRunNow: () -> Unit,
  onSettingsClick: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    HeaderSection(state, onSettingsClick)

    if (checkProgress != null) {
      CheckProgressSection(checkProgress)
    } else {
      QuickActionsSection(onRunNow, onSettingsClick)
    }

    StatusSection(state)
    HorizontalDivider()
    SettingsSummarySection(state)
  }
}

@Composable
private fun LoadingState(modifier: Modifier) {
  Column(
    modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    CircularProgressIndicator()
  }
}

@Composable
private fun HeaderSection(
  state: TrackerState,
  onSettingsClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text("Whitelist Tracker", style = MaterialTheme.typography.headlineMedium)
      Text(
        if (state.settings.monitoringEnabled) "Периодический мониторинг включен" else "Мониторинг остановлен",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    OutlinedButton(onClick = onSettingsClick) {
      Text("Настройки")
    }
  }
}

@Composable
private fun QuickActionsSection(
  onRunNow: () -> Unit,
  onSettingsClick: () -> Unit,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text("Быстрые действия", style = MaterialTheme.typography.titleMedium)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Button(onClick = onRunNow, modifier = Modifier.weight(1f)) {
          Text("Проверить сейчас")
        }
        OutlinedButton(onClick = onSettingsClick, modifier = Modifier.weight(1f)) {
          Text("Изменить")
        }
      }
    }
  }
}

@Composable
internal fun SaveFeedbackText(saveFeedback: SaveFeedback) {
  when (saveFeedback) {
    SaveFeedback.Idle -> Unit
    SaveFeedback.Saving ->
      Text(
        "Сохраняем настройки...",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    SaveFeedback.Saved ->
      Text(
        "Настройки сохранены",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
      )
    is SaveFeedback.Error ->
      Text(
        "Не удалось сохранить: ${saveFeedback.message}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
  }
}

@Composable
private fun CheckProgressSection(progress: CheckProgress) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text("Идет проверка", style = MaterialTheme.typography.titleMedium)
        Text(
          "${progress.checkedCount}/${progress.totalCount}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (progress.totalCount > 0) {
        LinearProgressIndicator(
          progress = { progress.fraction.coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      Text(
        progress.label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun StatusSection(state: TrackerState) {
  val status = state.status
  val currentTimeMillis = currentTimeMillisTicker(status.whitelistModeActive)
  val hasLastMode = status.lastEndedAtMillis != null || status.lastDurationMillis != null

  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Статус", style = MaterialTheme.typography.titleLarge)
    Text(
      if (status.whitelistModeActive) "Обнаружен режим белых списков" else "Режим белых списков не обнаружен",
      color = if (status.whitelistModeActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.titleMedium,
    )

    if (status.whitelistModeActive) {
      StatusInfoGroup("Текущий режим") {
        status.modeStartedAtMillis?.let { startedAtMillis ->
          StatusInfoRow("Активен с", formatTimestamp(startedAtMillis))
          StatusInfoRow("Длится сейчас", formatDurationMillis(currentTimeMillis - startedAtMillis))
        }
        StatusInfoRow("Повторная проверка", "каждые ${state.settings.activeRecheckIntervalMinutes} мин")
        status.lastCheckAtMillis?.let { StatusInfoRow("Последняя проверка", formatTimestamp(it)) }
        status.lastConnectedAtMillis?.let { StatusInfoRow("Последнее подключение", formatTimestamp(it)) }
      }
    } else if (hasLastMode) {
      StatusInfoGroup("Последний режим") {
        status.lastEndedAtMillis?.let { StatusInfoRow("Завершился", formatTimestamp(it)) }
        status.lastDurationMillis?.let { StatusInfoRow("Длительность", formatDurationMillis(it)) }
      }
    }

    StatusInfoGroup("Последняя проверка") {
      StatusInfoRow("Контрольная группа", "${status.controlSuccessCount}/${status.controlTotalCount} ответили")
      StatusInfoRow("Тестовая группа", "${status.testSuccessCount}/${status.testTotalCount} ответили")
      status.lastCheckAtMillis?.let { StatusInfoRow("Проверка", formatTimestamp(it)) }
      status.lastConnectedAtMillis?.let { StatusInfoRow("Подключение", formatTimestamp(it)) }
    }

    status.lastError?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }
  }
}

@Composable
private fun SettingsSummarySection(state: TrackerState) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text("Текущие настройки", style = MaterialTheme.typography.titleMedium)
    StatusInfoRow("Обычная проверка", "каждые ${state.settings.intervalMinutes} мин")
    StatusInfoRow("Перепроверка", "каждые ${state.settings.activeRecheckIntervalMinutes} мин")
    StatusInfoRow("Таймаут", "${state.settings.timeoutSeconds} сек")
    StatusInfoRow("URL", "${state.settings.controlUrls.size} контрольных, ${state.settings.testUrls.size} тестовых")
  }
}

@Composable
private fun currentTimeMillisTicker(enabled: Boolean): Long {
  var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(enabled) {
    if (!enabled) return@LaunchedEffect
    while (true) {
      currentTimeMillis = System.currentTimeMillis()
      delay(1000L)
    }
  }
  return currentTimeMillis
}

@Composable
private fun StatusInfoGroup(
  title: String,
  content: @Composable ColumnScope.() -> Unit,
) {
  Surface(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 2.dp),
    shape = RoundedCornerShape(8.dp),
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
      )
    }
  }
}

@Composable
private fun StatusInfoRow(
  label: String,
  value: String,
  valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.Top,
  ) {
    Text(
      label,
      modifier = Modifier.weight(1f),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      value,
      modifier = Modifier.weight(1.35f),
      style = MaterialTheme.typography.bodyMedium,
      color = valueColor,
      textAlign = TextAlign.End,
    )
  }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
  MyApplicationTheme {
    MainScreen(
      state = TrackerState(),
      onRunNow = {},
      modifier = Modifier.width(420.dp),
    )
  }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun MainScreenPortraitPreview() {
  MyApplicationTheme {
    MainScreen(
      state = TrackerState(),
      onRunNow = {},
    )
  }
}
