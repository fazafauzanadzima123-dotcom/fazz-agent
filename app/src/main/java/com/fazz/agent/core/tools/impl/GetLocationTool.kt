package com.fazz.agent.core.tools.impl

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.fazz.agent.core.common.jsonOf
import com.fazz.agent.core.tools.AgentTool
import com.fazz.agent.core.tools.ErrorCodes
import com.fazz.agent.core.tools.ToolResult
import com.fazz.agent.core.tools.schema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/** Approximate current location (and address) so Fazz can answer "dekat sini". Never stored. */
class GetLocationTool(private val context: Context) : AgentTool {
    override val name = "get_location"
    override val description = "Get the user's current approximate location and address. Use only when the request depends on where the user is " +
        "(nearby places, directions from here, local weather). Do not use otherwise."
    override val activityLabel = "Mengecek lokasi"
    override val parameters = schema { }
    override val requiredPermissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    override val permissionRationale = "Fazz butuh lokasimu sekarang untuk menjawab permintaan ini. Lokasi tidak disimpan."
    override val timeoutMs = 20_000L

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun execute(args: JsonObject): ToolResult {
        val lm = context.getSystemService(LocationManager::class.java)
        val providers = buildList {
            add(LocationManager.NETWORK_PROVIDER)
            if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.PASSIVE_PROVIDER)
        }.filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Layanan lokasi mati. Nyalakan Lokasi di pengaturan HP.")

        val now = System.currentTimeMillis()
        var best: Location? = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < 10 * 60_000L }.maxByOrNull { it.time }
        if (best == null && Build.VERSION.SDK_INT >= 30) {
            val exec = Executors.newSingleThreadExecutor()
            best = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine { cont ->
                    lm.getCurrentLocation(providers.first(), null, exec) { cont.resume(it) }
                }
            }
            exec.shutdown()
        }
        val loc = best ?: return ToolResult.error(ErrorCodes.EXECUTION_FAILED, "Lokasi belum tersedia. Coba lagi di tempat terbuka.")

        val address = withContext(Dispatchers.IO) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale("id", "ID")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let {
                    listOfNotNull(it.thoroughfare, it.subLocality, it.locality, it.adminArea).joinToString(", ")
                }
            }.getOrNull()
        }
        return ToolResult.ok(
            address ?: "Lokasi ditemukan",
            jsonOf(
                "latitude" to loc.latitude, "longitude" to loc.longitude,
                "accuracy_meters" to loc.accuracy.toInt(), "address" to (address ?: ""),
            ),
        )
    }
}
