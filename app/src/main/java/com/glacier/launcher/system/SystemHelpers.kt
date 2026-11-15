package com.glacier.launcher.system

import android.app.Activity
import android.app.AppOpsManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

object DefaultLauncher {
    /** True if the system's current Home app is GlacierLauncher. */
    fun isDefault(ctx: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val ri = ctx.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return ri?.activityInfo?.packageName == ctx.packageName
    }

    /**
     * Asks the user to pick GlacierLauncher as Home app through official system UI.
     * Android 10+: RoleManager ROLE_HOME. Older: Home settings screen. Never silent.
     */
    fun request(activity: Activity, launcher: ActivityResultLauncher<Intent>) {
        if (Build.VERSION.SDK_INT >= 29) {
            val rm = activity.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    try {
                        launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
                        return
                    } catch (e: ActivityNotFoundException) { /* fall through */ }
                } else return
            }
        }
        openHomeSettings(activity)
    }

    fun openHomeSettings(activity: Activity) {
        val candidates = listOf(
            Intent(Settings.ACTION_HOME_SETTINGS),
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (i in candidates) {
            try { activity.startActivity(i); return } catch (e: Exception) { /* try next */ }
        }
    }
}

object UsageAccess {
    @Suppress("DEPRECATION")
    fun has(ctx: Context): Boolean = try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }
}
