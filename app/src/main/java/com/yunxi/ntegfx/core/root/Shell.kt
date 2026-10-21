package com.yunxi.ntegfx.core.root

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 一次 root shell 执行的完整结果。
 * 规格 8.3：所有命令通过 su -c 执行时必须设置超时、捕获 stderr、对路径做引号转义。
 */
data class ShellResult(
    val command: String,
    val code: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val durationMs: Long = 0L,
) {
    val ok: Boolean get() = !timedOut && code == 0
    val message: String
        get() = when {
            timedOut -> "执行超时"
            code != 0 -> stderr.ifBlank { stdout }.trim().ifBlank { "退出码 $code" }
            else -> stdout.trim()
        }
}

object Shell {

    /**
     * KernelSU / Magisk 的 `su -M` 会把进程切到全局 mount namespace。
     * 部分 ColorOS 版本按应用隔离了 /data/user 的挂载视图，普通 su 只能看到自身目录，
     * 表现为"其他应用的 data 目录不存在"。探测一次并缓存。
     */
    @Volatile
    private var mountMasterSupported: Boolean? = null

    /** 单引号转义，路径含中文、空格、$ 时均安全。 */
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun run(command: String, timeoutMs: Long = 10_000L): ShellResult {
        val useMountMaster = supportsMountMaster()
        return execute(command, timeoutMs, useMountMaster)
    }

    /** 强制在全局 namespace 下执行，用于跨应用数据目录的诊断。 */
    fun runGlobal(command: String, timeoutMs: Long = 15_000L): ShellResult =
        execute(command, timeoutMs, supportsMountMaster())

    private fun supportsMountMaster(): Boolean {
        mountMasterSupported?.let { return it }
        val probe = execute("id", 15_000L, useMountMaster = true)
        val supported = !probe.timedOut && probe.code == 0 && probe.stdout.contains("uid=0")
        mountMasterSupported = supported
        return supported
    }

    private fun execute(command: String, timeoutMs: Long, useMountMaster: Boolean): ShellResult {
        val started = System.currentTimeMillis()
        var process: Process? = null
        try {
            // 显式套一层 sh -c：部分 ROM 的 su 只 exec 单个命令而不启动 shell，
            // 直接传脚本会导致 if/for 静默失败，表现为"文件不存在"。
            val wrapped = "sh -c " + quote(command)
            val argv = if (useMountMaster) arrayOf("su", "-M", "-c", wrapped) else arrayOf("su", "-c", wrapped)
            process = ProcessBuilder(*argv)
                .redirectErrorStream(false)
                .start()

            val out = StringBuilder()
            val err = StringBuilder()
            val outThread = thread(isDaemon = true, name = "su-out") {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { out.append(it).append('\n') }
                    }
                }
            }
            val errThread = thread(isDaemon = true, name = "su-err") {
                runCatching {
                    process.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { err.append(it).append('\n') }
                    }
                }
            }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                outThread.join(500)
                errThread.join(500)
                return ShellResult(
                    command = command,
                    code = -1,
                    stdout = out.toString(),
                    stderr = err.toString(),
                    timedOut = true,
                    durationMs = System.currentTimeMillis() - started,
                )
            }
            outThread.join(1_000)
            errThread.join(1_000)
            return ShellResult(
                command = command,
                code = process.exitValue(),
                stdout = out.toString(),
                stderr = err.toString(),
                durationMs = System.currentTimeMillis() - started,
            )
        } catch (t: Throwable) {
            process?.destroyForcibly()
            return ShellResult(
                command = command,
                code = -1,
                stdout = "",
                stderr = t.message ?: t.javaClass.simpleName,
                durationMs = System.currentTimeMillis() - started,
            )
        }
    }

    /** 不经 root，直接读取本应用可访问的文件（用于日志、偏好等私有目录）。 */
    fun readLocal(file: File): String = runCatching {
        if (file.exists() && file.length() <= MAX_TEXT_BYTES) file.readText() else ""
    }.getOrDefault("")

    const val MAX_TEXT_BYTES = 4L * 1024 * 1024
}
