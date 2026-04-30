package me.nonick.whitelisttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.nonick.whitelisttracker.data.TrackerRepository
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import me.nonick.whitelisttracker.theme.MyApplicationTheme
import me.nonick.whitelisttracker.worker.WhitelistWorkScheduler

class MainActivity : ComponentActivity() {
  private val notificationPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) {
      // The app keeps working without notifications; status remains visible in the UI.
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    requestNotificationPermissionIfNeeded()
    schedulePeriodicChecks()
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
  }

  private fun schedulePeriodicChecks() {
    lifecycleScope.launch {
      val settings = TrackerRepository(applicationContext).settingsSnapshot()
      WhitelistWorkScheduler.schedule(applicationContext, settings)
    }
  }
}
