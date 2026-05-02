package me.nonick.whitelisttracker

import android.app.Application
import me.nonick.whitelisttracker.worker.NetworkChangeWatcher

class WhitelistTrackerApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    NetworkChangeWatcher.start(this)
  }
}
