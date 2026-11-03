package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.util.Date
import kotlin.math.roundToInt

internal fun batteryPercentage(level: Int, scale: Int): Int? =
    if (level < 0 || scale <= 0) null else (level.toDouble() * 100 / scale).roundToInt().coerceIn(0, 100)

/** Current device status, visible only alongside video controls. No network name is collected. */
@Composable
internal fun PlayerDeviceStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    var clock by remember(context) { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    var battery by remember(context) { mutableStateOf<Int?>(null) }
    var wifi by remember(context) { mutableStateOf<Boolean?>(null) }

    DisposableEffect(context) {
        val main = Handler(Looper.getMainLooper())
        var active = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                    battery = batteryPercentage(
                        intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                        intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    )
                } else {
                    clock = DateFormat.getTimeFormat(context).format(Date())
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        val registered = runCatching {
            val sticky = ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            if (sticky != null) receiver.onReceive(context, sticky)
        }.isSuccess
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        fun refreshWifi() {
            wifi = connectivity?.let { manager ->
                runCatching { manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                    .getOrNull()
            }
        }
        refreshWifi()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                main.post { if (active) wifi = isWifi }
            }
            override fun onLost(network: Network) {
                main.post { if (active) refreshWifi() }
                main.postDelayed({ if (active) refreshWifi() }, 250)
            }
        }
        val networkRegistered = connectivity != null && runCatching { connectivity.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose {
            active = false
            main.removeCallbacksAndMessages(null)
            if (registered) runCatching { context.unregisterReceiver(receiver) }
            if (networkRegistered) runCatching { connectivity?.unregisterNetworkCallback(callback) }
        }
    }
    PlayerDeviceStatusContent(clock, battery, wifi, modifier)
}

@Composable
internal fun PlayerDeviceStatusContent(clock: String, battery: Int?, wifi: Boolean?, modifier: Modifier = Modifier) {
    val wifiLabel = when (wifi) { true -> "Wi-Fi in use"; false -> "Wi-Fi not in use"; null -> "Wi-Fi status unavailable" }
    Row(modifier.testTag("player_device_status"), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(clock, color = Color.White, fontSize = 12.sp, maxLines = 1)
        Icon(if (wifi == true) Icons.Default.Wifi else Icons.Default.WifiOff, wifiLabel,
            tint = Color.White, modifier = Modifier.size(18.dp))
        val batteryLabel = battery?.let { "$it%" } ?: "--%"
        Text(batteryLabel, color = Color.White, fontSize = 12.sp, maxLines = 1,
            modifier = Modifier.semantics { contentDescription = battery?.let { "Battery $it percent" } ?: "Battery status unavailable" })
    }
}
