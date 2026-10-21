package com.yunxi.ntegfx.core.log

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/**
 * 规格 5.6：结构化行日志，按会话切分，单文件 2 MB，滚动保留 15 份。
 * 日志只记录被修改的键，不记录配置文件全文，不采集任何用户标识。
 */
object Logx {

    enum class Level { DEBUG, INFO, WARN, ERROR }

    enum class Module { ROOT, FILE, CONFIG, NET, UI }

    private const val MAX_FILE_BYTES = 2L * 1024 * 1024
    private const val MAX_FILES = 15

    private val sessionId: String by lazy {
        SimpleDateFormat("HHmmss", Locale.US).format(Date())
    }

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    private var dir: File? = null
    private var writer: BufferedWriter? = null
    private var currentFile: File? = null
    private var written = 0L
    private var partIndex = 1
    private val lineCount = AtomicLong(0)

    @Synchronized
    fun init(context: Context) {
        if (dir != null) return
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val logDir = File(base, "logs").apply { mkdirs() }
        dir = logDir
        rotateIfNeeded()
        openNewFile()
        trimOldFiles()
    }

    fun sessionId(): String = sessionId

    fun logDir(): File? = dir

    fun d(module: Module, event: String, detail: String? = null, result: String? = null, durationMs: Long? = null) =
        write(Level.DEBUG, module, event, detail, result, durationMs)

    fun i(module: Module, event: String, detail: String? = null, result: String? = null, durationMs: Long? = null) =
        write(Level.INFO, module, event, detail, result, durationMs)

    fun w(module: Module, event: String, detail: String? = null, result: String? = null, durationMs: Long? = null) =
        write(Level.WARN, module, event, detail, result, durationMs)

    fun e(module: Module, event: String, detail: String? = null, result: String? = null, durationMs: Long? = null) =
        write(Level.ERROR, module, event, detail, result, durationMs)

    /** 冷启动环境快照，便于还原当时的运行条件。 */
    @Synchronized
    fun snapshot(entries: List<Pair<String, String>>) {
        i(Module.UI, "会话启动", "会话 $sessionId")
        entries.forEach { (key, value) ->
            appendRaw("[${timeFormat.format(Date())}][INFO][SNAPSHOT][$sessionId] $key: ${value.sanitize()}")
        }
    }

    @Synchronized
    private fun write(
        level: Level,
        module: Module,
        event: String,
        detail: String?,
        result: String?,
        durationMs: Long?,
    ) {
        val sb = StringBuilder()
        sb.append('[').append(timeFormat.format(Date())).append(']')
        sb.append('[').append(level.name).append(']')
        sb.append('[').append(module.name).append(']')
        sb.append('[').append(sessionId).append(']')
        sb.append(' ').append(event)
        detail?.takeIf { it.isNotBlank() }?.let { sb.append("  ").append(it.sanitize()) }
        result?.takeIf { it.isNotBlank() }?.let { sb.append("  → ").append(it.sanitize()) }
        durationMs?.let { sb.append("  (").append(it).append("ms)") }
        appendRaw(sb.toString())
    }

    @Synchronized
    private fun appendRaw(line: String) {
        val target = dir ?: return
        runCatching {
            if (written >= MAX_FILE_BYTES) {
                closeWriter()
                partIndex += 1
                openNewFile()
            }
            val w = writer ?: return
            w.write(line)
            w.newLine()
            w.flush()
            written += line.toByteArray(Charsets.UTF_8).size + 1
            lineCount.incrementAndGet()
        }
    }

    private fun openNewFile() {
        val target = dir ?: return
        val stamp = fileStamp.format(Date())
        val name = if (partIndex > 1) "session-$stamp-$partIndex.log" else "session-$stamp.log"
        val file = File(target, name)
        currentFile = file
        writer = BufferedWriter(FileWriter(file, true))
        written = file.length()
    }

    private fun closeWriter() {
        runCatching { writer?.flush() }
        runCatching { writer?.close() }
        writer = null
    }

    private fun rotateIfNeeded() {
        // 若当天已有当日旧会话文件不处理；仅保证数量上限由 trimOldFiles 控制
        partIndex = 1
    }

    @Synchronized
    fun trimOldFiles() {
        val target = dir ?: return
        val files = target.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedBy { it.lastModified() }
            ?: return
        if (files.size > MAX_FILES) {
            files.take(files.size - MAX_FILES).forEach { it.delete() }
        }
    }

    fun listLogFiles(): List<File> {
        val target = dir ?: return emptyList()
        return target.listFiles { f -> f.isFile && f.name.endsWith(".log") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun totalLines(): Long = lineCount.get()

    fun currentFileName(): String = currentFile?.name ?: "-"

    fun readTail(maxChars: Int = 20_000): String {
        val file = currentFile ?: return ""
        return runCatching {
            val text = file.readText()
            if (text.length <= maxChars) text else text.takeLast(maxChars)
        }.getOrDefault("")
    }

    private fun String.sanitize(): String = replace('\n', ' ').replace('\r', ' ')
}
