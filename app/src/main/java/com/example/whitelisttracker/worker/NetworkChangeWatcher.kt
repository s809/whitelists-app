package me.nonick.whitelisttracker.worker

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.nonick.whitelisttracker.data.TrackerRepository

object NetworkChangeWatcher {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Any()

  @Volatile private var started = false
  private var lastDefaultNetworkSignature: String? = null

  fun start(context: Context) {
    if (started) return

    val appContext = context.applicationContext
    val connectivityManager =
      appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    synchronized(lock) {
      if (started) return
      lastDefaultNetworkSignature = connectivityManager.defaultNetworkSignature()
      started = true
    }

    connectivityManager.registerDefaultNetworkCallback(
      object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
          handleDefaultNetworkChanged(appContext, connectivityManager, network)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
          handleDefaultNetworkChanged(appContext, connectivityManager, network, networkCapabilities)
        }

        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
          handleDefaultNetworkChanged(appContext, connectivityManager, network, linkProperties = linkProperties)
        }

        override fun onLost(network: Network) {
          if (connectivityManager.activeNetwork == null) {
            synchronized(lock) {
              lastDefaultNetworkSignature = null
            }
            WhitelistWorkScheduler.cancelNetworkChangeCheck(appContext)
          }
        }
      },
    )
  }

  fun currentDefaultNetworkSignature(context: Context): String? {
    val connectivityManager =
      context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    return connectivityManager.defaultNetworkSignature()
  }

  private fun handleDefaultNetworkChanged(
    context: Context,
    connectivityManager: ConnectivityManager,
    network: Network,
    networkCapabilities: NetworkCapabilities? = null,
    linkProperties: LinkProperties? = null,
  ) {
    val signature =
      connectivityManager.defaultNetworkSignature(network, networkCapabilities, linkProperties) ?: return
    val changed =
      synchronized(lock) {
        if (signature == lastDefaultNetworkSignature) {
          false
        } else {
          lastDefaultNetworkSignature = signature
          true
        }
      }

    if (!changed) return

    scope.launch {
      val settings = TrackerRepository(context).settingsSnapshot()
      if (settings.monitoringEnabled && isLatestDefaultNetworkSignature(signature)) {
        WhitelistWorkScheduler.runAfterNetworkChange(context, signature)
      }
    }
  }

  private fun isLatestDefaultNetworkSignature(signature: String): Boolean =
    synchronized(lock) { signature == lastDefaultNetworkSignature }

  private fun ConnectivityManager.defaultNetworkSignature(
    network: Network? = activeNetwork,
    networkCapabilities: NetworkCapabilities? = null,
    linkProperties: LinkProperties? = null,
  ): String? {
    val activeNetwork = activeNetwork ?: return null
    if (network != null && network != activeNetwork) return null

    val capabilities = networkCapabilities ?: getNetworkCapabilities(activeNetwork) ?: return null
    if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return null

    val properties = linkProperties ?: getLinkProperties(activeNetwork)
    return buildList {
        add(activeNetwork.toString())
        add(capabilities.transportSignature())
        add("validated=${capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
        add("iface=${properties?.interfaceName.orEmpty()}")
        add("subs=${capabilities.subscriptionSignature()}")
      }
      .joinToString("|")
  }

  private fun NetworkCapabilities.transportSignature(): String =
    buildList {
        if (hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) add("wifi")
        if (hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) add("cellular")
        if (hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) add("ethernet")
        if (hasTransport(NetworkCapabilities.TRANSPORT_VPN)) add("vpn")
      }
      .joinToString(",")

  private fun NetworkCapabilities.subscriptionSignature(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      subscriptionIds.sorted().joinToString(",")
    } else {
      ""
    }
}
