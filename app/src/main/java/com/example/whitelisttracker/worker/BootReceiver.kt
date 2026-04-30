package me.nonick.whitelisttracker.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.nonick.whitelisttracker.data.TrackerRepository

class BootReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

    val pendingResult = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
      try {
        val settings = TrackerRepository(context.applicationContext).settingsSnapshot()
        WhitelistWorkScheduler.schedule(context.applicationContext, settings)
      } finally {
        pendingResult.finish()
      }
    }
  }
}
