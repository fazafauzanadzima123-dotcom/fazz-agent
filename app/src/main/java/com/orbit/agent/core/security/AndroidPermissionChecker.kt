package com.orbit.agent.core.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.orbit.agent.core.tools.PermissionChecker

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
