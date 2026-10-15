package com.example.aivo.tools

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.aivo.sdk.core.model.ParamType
import com.aivo.sdk.core.model.ToolRisk
import com.aivo.sdk.core.port.Tool
import com.aivo.sdk.core.port.tool

/**
 * Production custom tools accessing Android device hardware & system services.
 */
object AndroidDeviceTools {

    /**
     * Low-risk hardware inspection tool.
     */
    fun createDeviceInfoTool(): Tool = tool(
        name = "get_device_hardware",
        description = "Retrieves Android device hardware details, brand, model, and OS version."
    ) {
        risk(ToolRisk.LOW)
        execute { _ ->
            val brand = Build.MANUFACTURER
            val model = Build.MODEL
            val sdkInt = Build.VERSION.SDK_INT
            val release = Build.VERSION.RELEASE
            """{"brand": "$brand", "model": "$model", "sdkInt": $sdkInt, "androidRelease": "$release"}"""
        }
    }

    /**
     * Low-risk battery status tool.
     */
    fun createBatteryStatusTool(context: Context): Tool = tool(
        name = "get_battery_status",
        description = "Queries current battery percentage and charging state."
    ) {
        risk(ToolRisk.LOW)
        execute { _ ->
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val isCharging = bm?.isCharging ?: false
            """{"batteryPercentage": $level, "isCharging": $isCharging}"""
        }
    }

    /**
     * High-risk tool that requires explicit user confirmation before execution.
     */
    fun createClearCacheTool(context: Context): Tool = tool(
        name = "clear_cache_directory",
        description = "Permanently removes cached files to free up disk space."
    ) {
        risk(ToolRisk.HIGH)
        requiresConfirmation(true) // Triggers ConfirmationHandler

        param(
            name = "older_than_days",
            description = "Delete files older than N days (0 for all files)",
            type = ParamType.Int,
            required = true
        )

        execute { args ->
            val days = args.int("older_than_days")
            val cacheDir = context.cacheDir
            var deletedCount = 0
            val cutoff = System.currentTimeMillis() - (days * 24L * 60 * 60 * 1000)

            cacheDir.listFiles()?.forEach { file ->
                if (days == 0 || file.lastModified() < cutoff) {
                    if (file.deleteRecursively()) deletedCount++
                }
            }
            """{"status": "success", "filesDeleted": $deletedCount, "cachePath": "${cacheDir.absolutePath}"}"""
        }
    }
}
