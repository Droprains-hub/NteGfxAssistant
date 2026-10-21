package com.yunxi.ntegfx.core.root

import android.util.Base64
import com.yunxi.ntegfx.core.config.ConfigSchema

data class FileProbe(
    val exists: Boolean = false,
    val permissionText: String = "",
    val mode: String = "",
    val owner: String = "",
    val group: String = "",
    val size: Long = 0,
    val raw: String = "",
)

data class OpStep(
    val name: String,
    val command: String,
    val code: Int,
    val stderr: String,
    val durationMs: Long,
) {
    val ok: Boolean get() = code == 0
}

data class FileOpResult(
    val success: Boolean,
    val steps: List<OpStep>,
    val message: String = "",
) {
    fun failedStep(): OpStep? = steps.firstOrNull { !it.ok }
}

/** 一次 su 会话读到的配置目录全貌，用于降低多次提权带来的超时与不确定性。 */
data class ConfigDirSnapshot(
    val dirExists: Boolean = false,
    val files: Map<String, String?> = emptyMap(),
    val listing: String = "",
    val raw: String = "",
    val error: String? = null,
) {
    fun content(name: String): String? = files[name]
    fun present(name: String): Boolean = files.containsKey(name) && files[name] != null
}

/**
 * 规格 8.3：所有文件操作收敛于此。
 *
 * 存在性判断统一使用 POSIX `test` 内建（`[ -e ]` / `[ -d ]` / `[ -f ]`）与命令退出码，
 * 不再依赖 `ls -Z` 或文本标记，避免不同 ROM 的 toybox 裁剪导致误判为"文件不存在"。
 */
class RootFileOperator {

    // ------------------------------------------------------------ 探测

    fun probe(path: String): FileProbe {
        val q = Shell.quote(path)
        val script = "if [ -e $q ]; then echo '@@EXISTS'; " +
            "(stat -c '%a|%U|%G|%s' $q 2>/dev/null) || true; " +
            "(ls -ld $q 2>/dev/null) || true; " +
            "else echo '@@ABSENT'; fi"
        val result = Shell.run(script, timeoutMs = 20_000)
        val out = result.stdout
        if (!out.contains("@@EXISTS")) {
            return FileProbe(exists = false, raw = (out + result.stderr).trim())
        }

        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        var mode = ""
        var owner = ""
        var group = ""
        var size = 0L

        val statLine = lines.firstOrNull { line ->
            val parts = line.split('|')
            parts.size >= 4 && parts[0].toIntOrNull(8) != null
        }
        if (statLine != null) {
            val parts = statLine.split('|')
            mode = parts[0]
            owner = parts.getOrElse(1) { "" }
            group = parts.getOrElse(2) { "" }
            size = parts.getOrElse(3) { "0" }.toLongOrNull() ?: 0L
        }

        if (owner.isEmpty()) {
            val lsLine = lines.firstOrNull { it.startsWith("-") || it.startsWith("d") }
            if (lsLine != null) {
                val tokens = lsLine.split(Regex("\\s+"))
                if (tokens.size >= 5) {
                    mode = permissionToMode(tokens[0])
                    owner = tokens[2]
                    group = tokens[3]
                }
            }
        }

        return FileProbe(
            exists = true,
            permissionText = if (mode.isNotEmpty()) "-" + mode else "",
            mode = mode,
            owner = owner,
            group = group,
            size = size,
            raw = lines.joinToString("\n"),
        )
    }

    fun isDirectory(path: String): Boolean {
        val q = Shell.quote(path)
        val result = Shell.run("if [ -d $q ]; then echo 1; else echo 0; fi", timeoutMs = 20_000)
        return result.stdout.trim().startsWith("1")
    }

    fun readText(path: String): String? {
        val q = Shell.quote(path)
        val result = Shell.run(
            "if [ -f $q ]; then cat $q 2>/dev/null; else exit 7; fi",
            timeoutMs = 20_000,
        )
        if (result.timedOut) return null
        if (result.code != 0) return null
        return result.stdout
    }

    /**
     * 单次提权读取整份配置目录。返回每个文件的内容（null 表示存在但读取失败）、
     * 目录列表与原始输出，便于在日志中定位失败环节。
     */
    fun readConfigDir(dir: String): ConfigDirSnapshot {
        val q = Shell.quote(dir)
        val script = buildString {
            appendLine("if [ -d $q ]; then echo '@@DIR:1'; else echo '@@DIR:0'; fi")
            appendLine("for f in ${ConfigSchema.FILE_GAME_USER} ${ConfigSchema.FILE_DEVICE} ${ConfigSchema.FILE_ENGINE}; do")
            appendLine("  echo \"@@FILE:${'$'}f\"")
            appendLine("  p=\"$dir/${'$'}f\"")
            appendLine("  if [ -f \"${'$'}p\" ]; then")
            appendLine("    echo \"@@SZ:${'$'}(stat -c %s \"${'$'}p\" 2>/dev/null)\"")
            appendLine("    echo '@@B'")
            appendLine("    cat \"${'$'}p\" 2>/dev/null")
            appendLine("    echo ''")
            appendLine("    echo '@@E'")
            appendLine("  else")
            appendLine("    echo '@@NA'")
            appendLine("  fi")
            appendLine("done")
            appendLine("echo '@@LIST'")
            appendLine("ls -la $q 2>/dev/null")
        }

        val result = Shell.run(script, timeoutMs = 25_000)
        val raw = result.stdout

        if (result.timedOut) {
            return ConfigDirSnapshot(error = "读取配置目录超时：root 授权可能未完成")
        }
        if (raw.isBlank()) {
            val err = result.stderr.trim().ifBlank { "退出码 ${result.code}" }
            return ConfigDirSnapshot(error = "无法执行 root 命令：$err")
        }

        val dirExists = raw.contains("@@DIR:1")
        val files = mutableMapOf<String, String?>()
        val content = StringBuilder()
        var currentFile: String? = null
        var collecting = false

        raw.lineSequence().forEach { line ->
            when {
                line.startsWith("@@FILE:") -> {
                    currentFile = line.removePrefix("@@FILE:").trim()
                    collecting = false
                    content.clear()
                }
                line.startsWith("@@SZ:") -> Unit
                line == "@@NA" -> {
                    currentFile?.let { files.putIfAbsent(it, null) }
                    collecting = false
                }
                line == "@@B" -> {
                    collecting = true
                    content.clear()
                }
                line == "@@E" -> {
                    collecting = false
                    currentFile?.let { name ->
                        if (!files.containsKey(name)) files[name] = content.toString()
                    }
                }
                line == "@@LIST" -> collecting = false
                collecting -> {
                    if (content.isNotEmpty()) content.append('\n')
                    content.append(line)
                }
            }
        }

        val listing = raw.substringAfter("@@LIST", "").trim()
        return ConfigDirSnapshot(
            dirExists = dirExists,
            files = files,
            listing = listing,
            raw = raw.take(4_000),
        )
    }

    // ------------------------------------------------------------ 写入

    fun mkdirs(dir: String): FileOpResult {
        val step = exec("创建目录", "mkdir -p ${Shell.quote(dir)}")
        return FileOpResult(step.ok, listOf(step))
    }

    fun writeAtomic(
        target: String,
        content: String,
        finalMode: Int = ConfigSchema.LOCK_MODE,
        owner: String = "root:root",
    ): FileOpResult {
        val steps = mutableListOf<OpStep>()
        val dir = target.substringBeforeLast('/')
        val tmp = "$target.tmp"

        val mkdir = exec("创建目录", "mkdir -p ${Shell.quote(dir)}")
        steps += mkdir
        if (!mkdir.ok) return FileOpResult(false, steps, "无法创建配置目录")

        val existing = probe(target)
        val contextRef = if (existing.exists) target else "$dir/${ConfigSchema.FILE_GAME_USER}"

        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val write = exec(
            "写入临时文件",
            "printf '%s' '$encoded' | base64 -d > ${Shell.quote(tmp)}",
            timeoutMs = 25_000,
        )
        steps += write
        if (!write.ok) {
            exec("清理临时文件", "rm -f ${Shell.quote(tmp)}")
            return FileOpResult(false, steps, "写入临时文件失败")
        }

        val sync = exec("落盘", "sync")
        steps += sync

        // 必须在 rename 之前复制上下文，否则目标已被覆盖，参考对象消失。
        // 部分 ROM 的 toybox 不支持 --reference，失败后由同目录 mv 继承父目录上下文兜底。
        val chcon = exec(
            "复制 SELinux 上下文",
            "chcon --reference=${Shell.quote(contextRef)} ${Shell.quote(tmp)} 2>/dev/null || " +
                "chcon \$(stat -c %C ${Shell.quote(contextRef)} 2>/dev/null) ${Shell.quote(tmp)} 2>/dev/null || true",
        )
        steps += chcon

        val move = exec("原子替换", "mv -f ${Shell.quote(tmp)} ${Shell.quote(target)}")
        steps += move
        if (!move.ok) {
            exec("清理临时文件", "rm -f ${Shell.quote(tmp)}")
            return FileOpResult(false, steps, "替换失败，配置文件可能被占用")
        }

        val chown = exec("修改属主", "chown $owner ${Shell.quote(target)}")
        steps += chown
        val chmod = exec("设置只读权限", "chmod $finalMode ${Shell.quote(target)}")
        steps += chmod

        val locked = chown.ok && chmod.ok
        val message = when {
            !locked -> "已写入但只读锁定失败"
            else -> "已写入并锁定"
        }
        return FileOpResult(locked, steps, message)
    }

    fun lock(path: String): FileOpResult {
        val quoted = Shell.quote(path)
        val steps = listOf(
            exec("修改属主", "chown root:root $quoted"),
            exec("设置只读权限", "chmod ${ConfigSchema.LOCK_MODE} $quoted"),
        )
        return FileOpResult(steps.all { it.ok }, steps, if (steps.all { it.ok }) "已锁定" else "锁定失败")
    }

    fun unlock(path: String, uid: Int): FileOpResult {
        val quoted = Shell.quote(path)
        val steps = listOf(
            exec("恢复属主", "chown $uid:$uid $quoted"),
            exec("恢复权限", "chmod 600 $quoted"),
        )
        return FileOpResult(steps.all { it.ok }, steps, if (steps.all { it.ok }) "已解锁" else "解锁失败")
    }

    fun chownTo(path: String, uid: Int): FileOpResult {
        val step = exec("恢复属主", "chown $uid:$uid ${Shell.quote(path)}")
        return FileOpResult(step.ok, listOf(step))
    }

    fun clearDir(dir: String): FileOpResult {
        val quoted = Shell.quote(dir)
        val step = exec(
            "清空配置目录",
            "if [ -d $quoted ]; then cd $quoted && find . -mindepth 1 -maxdepth 1 -exec rm -rf {} + 2>&1; fi",
            timeoutMs = 25_000,
        )
        val stderr = step.stderr.trim()
        return FileOpResult(
            step.ok && stderr.isEmpty(),
            listOf(step),
            when {
                !step.ok -> "清空失败"
                stderr.isNotEmpty() -> stderr
                else -> "已清空"
            },
        )
    }

    fun listFiles(dir: String): List<String> {
        val result = Shell.run(
            "if [ -d ${Shell.quote(dir)} ]; then ls -1A ${Shell.quote(dir)} 2>/dev/null; fi",
            timeoutMs = 20_000,
        )
        return result.stdout.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * 目录结构与配置文件位置诊断。仅在预期目录不存在时调用，
     * 用于确认真实路径（不同版本/渠道的 UnrealGame 层级可能不同）。
     */
    fun diagnose(packageName: String): List<String> {
        val script = buildString {
            appendLine("echo '@@COUNT'")
            appendLine("ls /data/user/0 2>/dev/null | wc -l")
            appendLine("echo '@@PKG_LIST_0'")
            appendLine("ls /data/user/0 2>/dev/null | grep -iE 'hottagames|iwplay|yh\\.' | head -10")
            appendLine("echo '@@PKG_LIST_999'")
            appendLine("ls /data/user/999 2>/dev/null | grep -iE 'hottagames|iwplay|yh\\.' | head -10")
            appendLine("echo '@@FIND_UG'")
            appendLine("find /data/user -maxdepth 6 -type d -name 'UnrealGame' 2>/dev/null | head -10")
            appendLine("echo '@@FIND_INI'")
            appendLine("find /data/user -maxdepth 9 -name 'GameUserSettings.ini' 2>/dev/null | head -10")
            appendLine("echo '@@PKGINFO'")
            appendLine("dumpsys package $packageName 2>/dev/null | grep -E 'userId=|versionName=' | head -4")
        }
        val result = Shell.run(script, timeoutMs = 60_000)
        val merged = (result.stdout + "\n" + result.stderr)
        return merged.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun exec(name: String, command: String, timeoutMs: Long = 20_000): OpStep {
        val result = Shell.run(command, timeoutMs)
        return OpStep(
            name = name,
            command = command,
            code = if (result.timedOut) -2 else result.code,
            stderr = if (result.timedOut) "timeout" else result.stderr.trim(),
            durationMs = result.durationMs,
        )
    }

    private fun permissionToMode(permission: String): String {
        if (permission.length < 10) return ""
        val bits = intArrayOf(400, 200, 100, 40, 20, 10, 4, 2, 1)
        var mode = 0
        for (i in 0 until 9) {
            val c = permission[i + 1]
            if (c != '-') mode += bits[i]
        }
        return mode.toString()
    }
}
