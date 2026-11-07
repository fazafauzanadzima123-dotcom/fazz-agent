package com.fazz.agent.core.screen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.fazz.agent.core.common.AppVisibility

/**
 * Shortest path to enabling screen control. Android does not let apps enable accessibility themselves,
 * so the user flips one switch; we open Fazz's own page directly (Android 13+) and come back automatically.
 */
object ScreenSetup {
    fun component(context: Context) = ComponentName(context, FazzAccessibilityService::class.java)

    fun open(context: Context) {
        AppVisibility.returnAfterAccessibilitySetup = true
        val flat = component(context).flattenToString()
        val direct = if (Build.VERSION.SDK_INT >= 33) {
            // Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS (hidden in the public SDK): opens Fazz's own switch.
            Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").putExtra(Intent.EXTRA_COMPONENT_NAME, flat)
        } else null
        // Fallback: the accessibility list, with Fazz highlighted where the Settings app supports it.
        val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", flat)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", flat) })
        val ok = direct?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false
        if (!ok) runCatching { context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
