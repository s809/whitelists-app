package me.nonick.whitelisttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import androidx.navigation3.runtime.NavKey
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
      Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator() }
    }
    is MainScreenUiState.Success -> {
      MainScreen(
        state = uiState.state,
        checkProgress = uiState.checkProgress,
        saveFeedback = uiState.saveFeedback,
        onSave = viewModel::saveSettings,
        onRunNow = viewModel::runCheckNow,
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
  saveFeedback: SaveFeedback = SaveFeedback.Idle,
  onSave: (Boolean, String, String, String, String, String, String, String) -> Unit,
  onRunNow: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var monitoringEnabled by rememberSaveable { mutableStateOf(state.settings.monitoringEnabled) }
  var intervalMinutes by rememberSaveable { mutableStateOf(state.settings.intervalMinutes.toString()) }
  var activeRecheckIntervalMinutes by rememberSaveable {
    mutableStateOf(state.settings.activeRecheckIntervalMinutes.toString())
  }
  var timeoutSeconds by rememberSaveable { mutableStateOf(state.settings.timeoutSeconds.toString()) }
  var probeAttempts by rememberSaveable { mutableStateOf(state.settings.probeAttempts.toString()) }
  var probeRetryDelaySeconds by rememberSaveable {
    mutableStateOf(state.settings.probeRetryDelaySeconds.toString())
  }
  var controlUrls by rememberSaveable { mutableStateOf(state.settings.controlUrls.joinToString("\n")) }
  var testUrls by rememberSaveable { mutableStateOf(state.settings.testUrls.joinToString("\n")) }

  LaunchedEffect(state.settings) {
    monitoringEnabled = state.settings.monitoringEnabled
    intervalMinutes = state.settings.intervalMinutes.toString()
    activeRecheckIntervalMinutes = state.settings.activeRecheckIntervalMinutes.toString()
    timeoutSeconds = state.settings.timeoutSeconds.toString()
    probeAttempts = state.settings.probeAttempts.toString()
    probeRetryDelaySeconds = state.settings.probeRetryDelaySeconds.toString()
    controlUrls = state.settings.controlUrls.joinToString("\n")
    testUrls = state.settings.testUrls.joinToString("\n")
  }

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text("Whitelist Tracker", style = MaterialTheme.typography.headlineMedium)
      Text(
        "Мониторинг доступности контрольных и тестовых URL",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text("Мониторинг", style = MaterialTheme.typography.titleMedium)
        Text(
          if (monitoringEnabled) "Периодические проверки включены" else "Периодические проверки остановлены",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = monitoringEnabled, onCheckedChange = { monitoringEnabled = it })
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        value = intervalMinutes,
        onValueChange = { intervalMinutes = it.filter(Char::isDigit) },
        label = { Text("Обычная проверка, мин") },
        supportingText = { Text("Минимум Android: 15") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = activeRecheckIntervalMinutes,
        onValueChange = { activeRecheckIntervalMinutes = it.filter(Char::isDigit) },
        label = { Text("Перепроверка, мин") },
        supportingText = { Text("Если режим активен") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedTextField(
        value = timeoutSeconds,
        onValueChange = { timeoutSeconds = it.filter(Char::isDigit) },
        label = { Text("Таймаут, сек") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
      OutlinedTextField(
        value = probeAttempts,
        onValueChange = { probeAttempts = it.filter(Char::isDigit) },
        label = { Text("Попытки") },
        supportingText = { Text("Минимум: 1") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f),
      )
    }

    OutlinedTextField(
      value = probeRetryDelaySeconds,
      onValueChange = { probeRetryDelaySeconds = it.filter(Char::isDigit) },
      label = { Text("Пауза между попытками, сек") },
      supportingText = { Text("Можно указать 0") },
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = controlUrls,
      onValueChange = { controlUrls = it },
      label = { Text("Контрольные URL") },
      minLines = 4,
      modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
      value = testUrls,
      onValueChange = { testUrls = it },
      label = { Text("Тестовые URL") },
      minLines = 4,
      modifier = Modifier.fillMaxWidth(),
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(
          onClick = {
            onSave(
              monitoringEnabled,
              intervalMinutes,
              activeRecheckIntervalMinutes,
              timeoutSeconds,
              probeAttempts,
              probeRetryDelaySeconds,
              controlUrls,
              testUrls,
            )
          },
          enabled = saveFeedback !is SaveFeedback.Saving,
        ) {
          if (saveFeedback is SaveFeedback.Saving) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text("Сохраняем")
          } else {
            Text("Сохранить")
          }
        }
        OutlinedButton(onClick = onRunNow, enabled = checkProgress == null) { Text("Проверить сейчас") }
      }
      SaveFeedbackText(saveFeedback)
    }

    checkProgress?.let { CheckProgressSection(it) }

    HorizontalDivider()
    StatusSection(state)
  }
}

@Composable
private fun SaveFeedbackText(saveFeedback: SaveFeedback) {
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
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text("Идёт проверка", style = MaterialTheme.typography.titleMedium)
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

    if (!status.whitelistModeActive) {
      status.lastCheckAtMillis?.let { Text("Последняя проверка: ${formatTimestamp(it)}") }
      status.lastConnectedAtMillis?.let { Text("Последнее подключение: ${formatTimestamp(it)}") }
    }
    Text("Контрольная группа: ${status.controlSuccessCount}/${status.controlTotalCount} ответили")
    Text("Тестовая группа: ${status.testSuccessCount}/${status.testTotalCount} ответили")
    status.lastError?.let { Text("Ошибка: $it", color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(4.dp))
    Text(
      "URL считается доступным, если получен любой HTTP-ответ.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
      onSave = { _, _, _, _, _, _, _, _ -> },
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
      onSave = { _, _, _, _, _, _, _, _ -> },
      onRunNow = {},
    )
  }
}
