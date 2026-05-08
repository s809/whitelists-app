package me.nonick.whitelisttracker.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.nonick.whitelisttracker.data.TrackerRepository
import me.nonick.whitelisttracker.data.TrackerSettings
import me.nonick.whitelisttracker.data.TrackerState
import me.nonick.whitelisttracker.theme.MyApplicationTheme

@Composable
fun SettingsScreen(
  onBack: () -> Unit,
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
      Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
      }
    }
    is MainScreenUiState.Success -> {
      SettingsScreen(
        settings = uiState.state.settings,
        saveFeedback = uiState.saveFeedback,
        onBack = onBack,
        onSave = viewModel::saveSettings,
        modifier = modifier,
      )
    }
    is MainScreenUiState.Error -> {
      Text("Error loading data: ${uiState.throwable.message}")
    }
  }
}

@Composable
internal fun SettingsScreen(
  settings: TrackerSettings,
  saveFeedback: SaveFeedback = SaveFeedback.Idle,
  onBack: () -> Unit,
  onSave: (Boolean, String, String, String, String, String, String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var monitoringEnabled by rememberSaveable { mutableStateOf(settings.monitoringEnabled) }
  var intervalMinutes by rememberSaveable { mutableStateOf(settings.intervalMinutes.toString()) }
  var activeRecheckIntervalMinutes by rememberSaveable {
    mutableStateOf(settings.activeRecheckIntervalMinutes.toString())
  }
  var timeoutSeconds by rememberSaveable { mutableStateOf(settings.timeoutSeconds.toString()) }
  var probeAttempts by rememberSaveable { mutableStateOf(settings.probeAttempts.toString()) }
  var probeRetryDelaySeconds by rememberSaveable {
    mutableStateOf(settings.probeRetryDelaySeconds.toString())
  }
  var controlUrls by rememberSaveable { mutableStateOf(settings.controlUrls.joinToString("\n")) }
  var testUrls by rememberSaveable { mutableStateOf(settings.testUrls.joinToString("\n")) }

  LaunchedEffect(settings) {
    monitoringEnabled = settings.monitoringEnabled
    intervalMinutes = settings.intervalMinutes.toString()
    activeRecheckIntervalMinutes = settings.activeRecheckIntervalMinutes.toString()
    timeoutSeconds = settings.timeoutSeconds.toString()
    probeAttempts = settings.probeAttempts.toString()
    probeRetryDelaySeconds = settings.probeRetryDelaySeconds.toString()
    controlUrls = settings.controlUrls.joinToString("\n")
    testUrls = settings.testUrls.joinToString("\n")
  }

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)
        Text(
          "Проверки и списки URL",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      OutlinedButton(onClick = onBack) {
        Text("Назад")
      }
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

    HorizontalDivider()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Расписание", style = MaterialTheme.typography.titleMedium)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
          value = intervalMinutes,
          onValueChange = { intervalMinutes = it },
          label = "Обычная проверка, мин",
          supportingText = "Минимум Android: 15",
          modifier = Modifier.weight(1f),
        )
        NumberField(
          value = activeRecheckIntervalMinutes,
          onValueChange = { activeRecheckIntervalMinutes = it },
          label = "Перепроверка, мин",
          supportingText = "Если режим активен",
          modifier = Modifier.weight(1f),
        )
      }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("Запросы", style = MaterialTheme.typography.titleMedium)
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
          value = timeoutSeconds,
          onValueChange = { timeoutSeconds = it },
          label = "Таймаут, сек",
          modifier = Modifier.weight(1f),
        )
        NumberField(
          value = probeAttempts,
          onValueChange = { probeAttempts = it },
          label = "Попытки",
          supportingText = "Минимум: 1",
          modifier = Modifier.weight(1f),
        )
      }
      NumberField(
        value = probeRetryDelaySeconds,
        onValueChange = { probeRetryDelaySeconds = it },
        label = "Пауза между попытками, сек",
        supportingText = "Можно указать 0",
        modifier = Modifier.fillMaxWidth(),
      )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("URL", style = MaterialTheme.typography.titleMedium)
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
    }

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
        OutlinedButton(onClick = onBack) {
          Text("Готово")
        }
      }
      SaveFeedbackText(saveFeedback)
    }

    Spacer(Modifier.height(4.dp))
  }
}

@Composable
private fun NumberField(
  value: String,
  onValueChange: (String) -> Unit,
  label: String,
  modifier: Modifier = Modifier,
  supportingText: String? = null,
) {
  OutlinedTextField(
    value = value,
    onValueChange = { onValueChange(it.filter(Char::isDigit)) },
    label = { Text(label) },
    supportingText = supportingText?.let { text -> ({ Text(text) }) },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    modifier = modifier,
  )
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
  MyApplicationTheme {
    SettingsScreen(
      settings = TrackerState().settings,
      onBack = {},
      onSave = { _, _, _, _, _, _, _, _ -> },
      modifier = Modifier.width(420.dp),
    )
  }
}

@Preview(showBackground = true, widthDp = 340)
@Composable
fun SettingsScreenPortraitPreview() {
  MyApplicationTheme {
    SettingsScreen(
      settings = TrackerState().settings,
      onBack = {},
      onSave = { _, _, _, _, _, _, _, _ -> },
    )
  }
}
