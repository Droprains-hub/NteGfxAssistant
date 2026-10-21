package com.yunxi.ntegfx.core.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.util.Locale

/** 首页展示所需的设备事实，全部来自系统公开 API，不需要 root。 */
object DeviceInfo {

    fun processor(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else ""
        val hardware = Build.HARDWARE.replace('_', ' ')
        val cores = Runtime.getRuntime().availableProcessors()
        val name = when {
            soc.isNotBlank() && !soc.equals("unknown", true) -> soc
            else -> hardware
        }
        return "$name · $cores 核"
    }

    fun kernel(): String {
        val raw = System.getProperty("os.version").orEmpty()
        return raw.ifBlank { "-" }
    }

    fun abi(): String = Build.SUPPORTED_ABIS.firstOrNull() ?: "-"

    fun manufacturerModel(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun androidVersion(): String = "${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）"

    /** 返回 已用 / 总计（字节）。 */
    fun memory(context: Context): Pair<Long, Long> {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        val used = (info.totalMem - info.availMem).coerceAtLeast(0)
        return used to info.totalMem
    }

    fun display(context: Context): String {
        val metrics = context.resources.displayMetrics
        val width = maxOf(metrics.widthPixels, metrics.heightPixels)
        val height = minOf(metrics.widthPixels, metrics.heightPixels)
        val refresh = runCatching {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.refreshRate
        }.getOrDefault(0f)
        val hz = if (refresh > 0f) String.format(Locale.US, " · %.0fHz", refresh) else ""
        return "$width × $height$hz"
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 GB"
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return String.format(Locale.US, "%.1f GB", gb)
    }
}
