package com.fazz.agent.core.tools.impl

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.serialization.json.JsonObject
import java.util.Locale

/** TOOL 5: non-identifying device information. No IMEI, serial, accounts or location. */
class DeviceInfoTool(private val context: Context) : AgentTool {
    override val name = "get_device_info"
    override val description = "Get basic phone status: model, Android version, battery level and charging state, network type, free storage, language."
    override val activityLabel = "Mengecek status HP"
    override val parameters = schema()

    override suspend fun execute(args: JsonObject): ToolResult {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val stat = StatFs(Environment.getDataDirectory().path)
        val freeGb = stat.availableBytes / 1_000_000_000.0
        val totalGb = stat.totalBytes / 1_000_000_000.0

        val data = jsonOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT,
            "battery_percent" to pct,
            "charging" to charging,
            "network" to networkType(),
            "storage_free_gb" to "%.1f".format(Locale.US, freeGb),
            "storage_total_gb" to "%.1f".format(Locale.US, totalGb),
            "language" to Locale.getDefault().toLanguageTag(),
        )
        return ToolResult.ok("${Build.MODEL}, baterai ${pct ?: "?"}%", data)
    }

    private fun networkType(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "unknown"
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "offline"
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }
}
