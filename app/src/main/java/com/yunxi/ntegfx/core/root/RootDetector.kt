package com.yunxi.ntegfx.core.root

import java.util.Locale

enum class RootManager(val displayName: String) {
    MAGISK("Magisk"),
    KERNELSU("KernelSU"),
    KERNELSU_NEXT("KernelSU Next"),
    SUKISU("SukiSU"),
    APATCH("APatch"),
    UNKNOWN("未知"),
    NONE("未检测到"),
}

data class RootStatus(
    val granted: Boolean = false,
    val manager: RootManager = RootManager.NONE,
    val version: String? = null,
    val selinux: String? = null,
    val installedNotGranted: Boolean = false,
    val raw: String = "",
) {
    val selinuxEnforcing: Boolean
        get() = selinux?.lowercase(Locale.US)?.contains("enforcing") == true
}

/**
 * 规格 5.3：特征优先、命令兜底。
 * 一次 su 会话内完成全部探测，避免多次弹授权。
 */
object RootDetector {

    private const val PROBE = """
        echo '@@id'; id
        echo '@@magisk'; (magisk -v 2>/dev/null) || true
        echo '@@ksud'; (ksud -V 2>/dev/null) || (ksud --version 2>/dev/null) || true
        echo '@@apd'; (apd -V 2>/dev/null) || true
        echo '@@paths'
        for p in /data/adb/ksu /data/adb/ap /data/adb/modules /data/adb/sukisu /sbin/magisk /debug_ramdisk/magisk /system/bin/magisk /system/bin/ksud /system/bin/apd; do
          [ -e "${'$'}p" ] && echo "P:${'$'}p"
        done
        true
        echo '@@selinux'; (getenforce 2>/dev/null) || (cat /sys/fs/selinux/enforce 2>/dev/null) || true
        echo '@@props'; getprop ro.build.version.release; getprop ro.build.version.sdk
    """

    fun detect(): RootStatus {
        val shell = Shell.run(PROBE.trimIndent(), timeoutMs = 12_000)
        val sections = parseSections(shell.stdout)

        val idOut = sections["id"].orEmpty()
        val granted = idOut.contains("uid=0")

        val magiskOut = sections["magisk"].orEmpty().trim()
        val ksudOut = sections["ksud"].orEmpty().trim()
        val apdOut = sections["apd"].orEmpty().trim()
        val paths = sections["paths"].orEmpty()

        val hasKsu = paths.contains("/data/adb/ksu") || ksudOut.isNotEmpty()
        val hasApatch = paths.contains("/data/adb/ap") || apdOut.isNotEmpty()
        val hasMagisk = paths.contains("magisk") || magiskOut.isNotEmpty()

        var manager = RootManager.UNKNOWN
        var version: String? = null

        when {
            hasKsu && (ksudOut.contains("SukiSU", true) || paths.contains("/data/adb/sukisu")) -> {
                manager = RootManager.SUKISU
                version = ksudOut.lines().firstOrNull()?.trim()
            }
            hasKsu && ksudOut.contains("Next", true) -> {
                manager = RootManager.KERNELSU_NEXT
                version = ksudOut.lines().firstOrNull()?.trim()
            }
            hasKsu -> {
                manager = RootManager.KERNELSU
                version = ksudOut.lines().firstOrNull()?.trim()
            }
            hasApatch -> {
                manager = RootManager.APATCH
                version = apdOut.lines().firstOrNull()?.trim()
            }
            hasMagisk -> {
                manager = RootManager.MAGISK
                version = magiskOut.lines().firstOrNull()?.trim()
            }
            granted -> {
                manager = RootManager.UNKNOWN
                version = null
            }
        }

        val selinux = sections["selinux"].orEmpty().trim().lineSequence().firstOrNull()
            ?.takeIf { it.isNotBlank() }

        return RootStatus(
            granted = granted,
            manager = if (manager == RootManager.UNKNOWN && !granted) RootManager.NONE else manager,
            version = version?.takeIf { it.isNotBlank() },
            selinux = selinux,
            installedNotGranted = !granted && manager != RootManager.NONE,
            raw = shell.stdout,
        )
    }

    fun requestAccess(): Boolean =
        Shell.run("id", timeoutMs = 30_000).let { it.ok && it.stdout.contains("uid=0") }

    private fun parseSections(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var key: String? = null
        val buffer = StringBuilder()
        raw.lineSequence().forEach { line ->
            if (line.startsWith("@@")) {
                key?.let { result[it] = buffer.toString() }
                buffer.clear()
                key = line.removePrefix("@@").trim()
            } else if (key != null) {
                buffer.appendLine(line)
            }
        }
        key?.let { result[it] = buffer.toString() }
        return result
    }
}
