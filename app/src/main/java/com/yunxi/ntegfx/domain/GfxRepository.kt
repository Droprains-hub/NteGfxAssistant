package com.yunxi.ntegfx.domain

import android.content.Context
import com.yunxi.ntegfx.core.config.ConfigCipher
import com.yunxi.ntegfx.core.config.ConfigClassifier
import com.yunxi.ntegfx.core.config.ConfigCodec
import com.yunxi.ntegfx.core.config.ConfigSchema
import com.yunxi.ntegfx.core.config.GameIniCipher
import com.yunxi.ntegfx.core.log.Logx
import com.yunxi.ntegfx.core.root.AnfPatcher
import com.yunxi.ntegfx.core.root.FileOpResult
import com.yunxi.ntegfx.core.root.RootDetector
import com.yunxi.ntegfx.core.root.RootFileOperator
import com.yunxi.ntegfx.data.ApplyProgress
import com.yunxi.ntegfx.data.ApplyResult
import com.yunxi.ntegfx.data.ApplyStage
import com.yunxi.ntegfx.data.Channel
import com.yunxi.ntegfx.data.ConfigSnapshot
import com.yunxi.ntegfx.data.ConfigState
import com.yunxi.ntegfx.data.GfxParams
import com.yunxi.ntegfx.data.ParamSpec
import com.yunxi.ntegfx.data.ScalabilityGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 业务编排：检测 → 读取 → 应用 → 校验 → 一键恢复。
 * 所有 root 操作串行执行，失败时按规格 5.4 回滚已写入文件的权限。
 */
class GfxRepository(private val context: Context) {

    private val ops = RootFileOperator()

    // ------------------------------------------------------------ 环境

    fun isInstalled(channel: Channel): Boolean = runCatching {
        context.packageManager.getPackageInfo(channel.pkg, 0)
    }.isSuccess

    fun gameVersion(channel: Channel): String? = runCatching {
        context.packageManager.getPackageInfo(channel.pkg, 0).versionName
    }.getOrNull()

    fun gameUid(channel: Channel): Int? = runCatching {
        context.packageManager.getApplicationInfo(channel.pkg, 0).uid
    }.getOrNull()

    fun screenSize(): Pair<Int, Int> {
        val metrics = context.resources.displayMetrics
        val long = maxOf(metrics.widthPixels, metrics.heightPixels)
        val short = minOf(metrics.widthPixels, metrics.heightPixels)
        return long to short
    }

    // ------------------------------------------------------------ 检测

    suspend fun detect(channel: Channel, rootGranted: Boolean): ConfigSnapshot = withContext(Dispatchers.IO) {
        val pkgInstalled = isInstalled(channel)
        val version = gameVersion(channel)
        val dir = channel.configDir

        if (!rootGranted) {
            return@withContext ConfigSnapshot(
                channel = channel,
                installed = pkgInstalled,
                gameVersion = version,
                detail = "root_denied",
            )
        }

        val dirSnapshot = ops.readConfigDir(dir)

        if (dirSnapshot.error != null) {
            Logx.e(Logx.Module.FILE, "读取配置目录失败", dirSnapshot.error)
            return@withContext ConfigSnapshot(
                channel = channel,
                installed = pkgInstalled,
                gameVersion = version,
                detail = dirSnapshot.error,
            )
        }

        if (!dirSnapshot.dirExists) {
            Logx.w(Logx.Module.FILE, "配置目录不存在", dir)
            runCatching {
                ops.diagnose(channel.pkg).forEachIndexed { index, line ->
                    Logx.i(Logx.Module.FILE, "诊断#$index", line.take(240))
                }
            }
            return@withContext ConfigSnapshot(
                channel = channel,
                installed = pkgInstalled,
                dirExists = false,
                gameVersion = version,
                detail = "配置目录不存在：$dir",
            )
        }

        // 游戏落盘是逐行密文，也可能（历史版本/用户手动）是明文，统一先解码再解析。
        val gameRaw = dirSnapshot.content(ConfigSchema.FILE_GAME_USER)
        val deviceRaw = dirSnapshot.content(ConfigSchema.FILE_DEVICE)
        val engineRaw = dirSnapshot.content(ConfigSchema.FILE_ENGINE)
        val gameText = GameIniCipher.decodeIfNeeded(gameRaw)
        val deviceText = GameIniCipher.decodeIfNeeded(deviceRaw)
        val engineText = GameIniCipher.decodeIfNeeded(engineRaw)

        Logx.i(
            Logx.Module.FILE,
            "配置目录读取",
            "文件=${dirSnapshot.files.keys.joinToString(",")}",
            "GameUserSettings=${gameRaw?.length ?: -1}B(密文=${GameIniCipher.looksEncrypted(gameRaw)}) " +
                "DeviceProfiles=${deviceRaw?.length ?: -1}B Engine=${engineRaw?.length ?: -1}B",
        )
        if (dirSnapshot.listing.isNotBlank()) {
            Logx.i(Logx.Module.FILE, "目录列表", dirSnapshot.listing.replace('\n', '｜').take(600))
        }

        // 防伪标记：本工具写出的 Engine.ini 一定带 Slate.YunxiIndices，
        // 游戏自己的文件不会有。密文状态下这是唯一可靠的区分手段。
        val ours = engineText?.contains(ConfigSchema.KEY_MARKER) == true
        val initialized = ConfigClassifier.isInitialized(gameText, deviceText, engineText)
        val state = when {
            initialized && ours -> ConfigState.APPLIED
            initialized -> ConfigState.ORIGINAL
            ConfigClassifier.isPlainText(gameText) -> ConfigState.UNKNOWN
            gameText == null && deviceText == null && engineText == null -> ConfigState.NOT_INITIALIZED
            else -> ConfigState.NOT_INITIALIZED
        }
        val params = if (state == ConfigState.APPLIED) {
            ConfigCodec.parseParams(gameText, deviceText, engineText)
        } else {
            null
        }

        val missing = buildList {
            if (!ConfigClassifier.isPlainText(gameText)) add(ConfigSchema.FILE_GAME_USER)
            if (!ConfigClassifier.isPlainText(deviceText)) add(ConfigSchema.FILE_DEVICE)
            if (!ConfigClassifier.isPlainText(engineText)) add(ConfigSchema.FILE_ENGINE)
        }

        Logx.i(
            Logx.Module.CONFIG,
            "检测完成",
            "区服=${channel.pkg} 状态=$state",
            if (initialized) "渲染倍率=${ConfigCodec.readCvar(deviceText, ConfigSchema.KEY_CONTENT_SCALE)} CVar段数=${ConfigCodec.countCvar(deviceText, ConfigSchema.KEY_CONTENT_SCALE)}"
            else "待初始化=${missing.joinToString(",")}",
        )

        ConfigSnapshot(
            channel = channel,
            installed = true,
            dirExists = true,
            state = state,
            params = params,
            gameVersion = version,
            ownerUid = gameUid(channel),
            detail = when {
                initialized -> null
                state == ConfigState.UNKNOWN -> "配置文件格式异常，建议先执行一键恢复"
                else -> "待初始化：${missing.joinToString("、")}"
            },
        )
    }

    // ------------------------------------------------------------ 初始化

    /**
     * 首次接管：把内置的三份明文配置写入游戏目录并锁定。
     * 仅在缺失或密文状态下由 UI 触发，已有明文文件的用户不会被覆盖。
     */
    suspend fun initialize(
        channel: Channel,
        params: GfxParams,
        onProgress: (ApplyProgress) -> Unit,
    ): ApplyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val root = RootDetector.detect()
        if (!root.granted) {
            return@withContext ApplyResult(false, "Root 权限已失效，请重新授权", ApplyStage.VERIFY_ENV)
        }

        onProgress(ApplyProgress(ApplyStage.VERIFY_ENV, 6, "读取内置配置模板"))
        val templateGame = readTemplate(ConfigSchema.FILE_GAME_USER)
        val templateDevice = readTemplate(ConfigSchema.FILE_DEVICE)
        val templateEngine = readTemplate(ConfigSchema.FILE_ENGINE)
        if (templateGame == null || templateDevice == null || templateEngine == null) {
            Logx.e(Logx.Module.CONFIG, "内置模板缺失")
            return@withContext ApplyResult(false, "内置配置模板缺失，请重新安装应用", ApplyStage.VERIFY_ENV)
        }

        val dir = channel.configDir
        val mkdir = ops.mkdirs(dir)
        if (!mkdir.success) {
            return@withContext ApplyResult(false, "无法创建配置目录", ApplyStage.VERIFY_ENV)
        }

        val (width, height) = screenSize()
        val (gameContent, deviceContent, engineContent) = ConfigCodec.prepareTemplate(
            gameUser = templateGame,
            device = templateDevice,
            engine = templateEngine,
            params = params,
            screenWidth = width,
            screenHeight = height,
        )

        onProgress(ApplyProgress(ApplyStage.WRITE_TEMP, 28, "写入 GameUserSettings.ini"))
        val gameResult = ops.writeAtomic("$dir/${ConfigSchema.FILE_GAME_USER}", GameIniCipher.encrypt(gameContent))
        logOp("初始化 GameUserSettings.ini", gameResult)
        if (!gameResult.success) {
            return@withContext ApplyResult(false, "写入 GameUserSettings.ini 失败：${gameResult.message}", ApplyStage.WRITE_TEMP)
        }

        onProgress(ApplyProgress(ApplyStage.ATOMIC_REPLACE, 58, "写入 DeviceProfiles.ini"))
        val deviceResult = ops.writeAtomic("$dir/${ConfigSchema.FILE_DEVICE}", GameIniCipher.encrypt(deviceContent))
        logOp("初始化 DeviceProfiles.ini", deviceResult)
        if (!deviceResult.success) {
            return@withContext ApplyResult(false, "写入 DeviceProfiles.ini 失败：${deviceResult.message}", ApplyStage.ATOMIC_REPLACE)
        }

        onProgress(ApplyProgress(ApplyStage.LOCK, 82, "写入 Engine.ini 并锁定"))
        val engineResult = ops.writeAtomic("$dir/${ConfigSchema.FILE_ENGINE}", GameIniCipher.encrypt(engineContent))
        logOp("初始化 Engine.ini", engineResult)
        if (!engineResult.success) {
            return@withContext ApplyResult(false, "写入 Engine.ini 失败：${engineResult.message}", ApplyStage.LOCK)
        }

        onProgress(ApplyProgress(ApplyStage.VERIFY_RESULT, 94, "校验三份文件"))
        val verify = verify(
            "$dir/${ConfigSchema.FILE_GAME_USER}",
            "$dir/${ConfigSchema.FILE_DEVICE}",
            "$dir/${ConfigSchema.FILE_ENGINE}",
            params,
        )
        val duration = System.currentTimeMillis() - started
        if (!verify) {
            Logx.e(Logx.Module.CONFIG, "初始化校验未通过", null, null, duration)
            return@withContext ApplyResult(false, "初始化写入完成但校验未通过，请检查日志", ApplyStage.VERIFY_RESULT, duration)
        }

        Logx.i(
            Logx.Module.CONFIG,
            "初始化完成",
            "倍率=${ConfigCodec.trimFloat(params.renderScale)} 百分比=${ParamSpec.screenPercentage(params.renderScale)} CVar段数=${ConfigCodec.countCvar(deviceContent, ConfigSchema.KEY_CONTENT_SCALE)}",
            "成功",
            duration,
        )
        onProgress(ApplyProgress(ApplyStage.DONE, 100, "初始化完成"))
        ApplyResult(true, "初始化完成，配置已写入并锁定", null, duration)
    }

    /** 模板以 AES-GCM 密文随包分发，读入时解密（见 ConfigCipher）。 */
    private fun readTemplate(name: String): String? = runCatching {
        val bin = name.replace(".ini", ".bin")
        ConfigCipher.read(context, bin).removePrefix("\uFEFF")
    }.getOrNull()

    // ------------------------------------------------------------ 应用

    suspend fun apply(
        channel: Channel,
        prev: GfxParams?,
        target: GfxParams,
        ownerUid: Int?,
        onProgress: (ApplyProgress) -> Unit,
    ): ApplyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val uid = ownerUid ?: gameUid(channel)

        onProgress(ApplyProgress(ApplyStage.VERIFY_ENV, 4, "校验 Root 与配置目录"))
        val root = RootDetector.detect()
        if (!root.granted) {
            Logx.e(Logx.Module.ROOT, "应用中止", "Root 未授权")
            return@withContext ApplyResult(false, "Root 权限已失效，请重新授权", ApplyStage.VERIFY_ENV)
        }

        val dir = channel.configDir
        if (ops.isProcessRunning(channel.pkg)) {
            Logx.e(Logx.Module.FILE, "应用中止", "游戏进程运行中", channel.pkg)
            return@withContext ApplyResult(
                false,
                "请先完全退出游戏再应用配置，否则改动会在退出时被覆盖",
                ApplyStage.VERIFY_ENV,
            )
        }

        // ANF 补丁现在由界面上的独立开关控制，这里只做状态提示，不再自动打补丁。
        var anfNote: String? = null
        if (target.anfSr || target.anfFrc) {
            val patchState = AnfPatcher.status(channel.pkg)
            if (patchState.state != AnfPatcher.State.PATCHED) {
                anfNote = "尚未启用 ANF 补丁，游戏里可能看不到 ANF 开关"
            }
            Logx.i(Logx.Module.FILE, "ANF 补丁状态", patchState.state.name, channel.pkg)
        }
        val dirSnapshot = ops.readConfigDir(dir)
        if (!dirSnapshot.dirExists) {
            Logx.e(Logx.Module.FILE, "应用中止", "配置目录不存在", dir)
            return@withContext ApplyResult(
                false,
                "未检测到配置目录，请先启动一次游戏",
                ApplyStage.VERIFY_ENV,
            )
        }

        val gamePath = "$dir/${ConfigSchema.FILE_GAME_USER}"
        val devicePath = "$dir/${ConfigSchema.FILE_DEVICE}"
        val enginePath = "$dir/${ConfigSchema.FILE_ENGINE}"

        // 读回来的可能是密文，必须先解码；否则 ConfigClassifier.isPlainText() 判否，
        // build* 会退回内置模板，把用户已有的设置全部冲掉。
        val existingGame = GameIniCipher.decodeIfNeeded(dirSnapshot.content(ConfigSchema.FILE_GAME_USER))
        val existingDevice = GameIniCipher.decodeIfNeeded(dirSnapshot.content(ConfigSchema.FILE_DEVICE))
        val existingEngine = GameIniCipher.decodeIfNeeded(dirSnapshot.content(ConfigSchema.FILE_ENGINE))
        val (width, height) = screenSize()

        val written = mutableListOf<String>()
        fun rollback() {
            if (uid == null) return
            written.forEach { path ->
                val probe = ops.probe(path)
                if (probe.exists && probe.owner == "root") {
                    ops.unlock(path, uid)
                }
            }
            if (written.isNotEmpty()) {
                Logx.e(Logx.Module.FILE, "锁定失败，已回滚至原始权限", written.joinToString(", "))
            }
        }

        onProgress(ApplyProgress(ApplyStage.WRITE_TEMP, 18, "写入 GameUserSettings.ini"))
        val gameContent = ConfigCodec.buildGameUserSettings(existingGame, target, width, height)
        val gameResult = ops.writeAtomic(gamePath, GameIniCipher.encrypt(gameContent))
        logOp("GameUserSettings.ini", gameResult)
        if (!gameResult.success) {
            rollback()
            return@withContext ApplyResult(
                false,
                "写入 GameUserSettings.ini 失败：${gameResult.message}",
                gameResult.failedStep()?.let { stageOf(it.name) } ?: ApplyStage.WRITE_TEMP,
                System.currentTimeMillis() - started,
            )
        }
        written += gamePath

        onProgress(ApplyProgress(ApplyStage.ATOMIC_REPLACE, 48, "写入 DeviceProfiles.ini"))
        val deviceContent = ConfigCodec.buildDeviceProfiles(existingDevice, target)
        val deviceResult = ops.writeAtomic(devicePath, GameIniCipher.encrypt(deviceContent))
        logOp("DeviceProfiles.ini", deviceResult)
        if (!deviceResult.success) {
            rollback()
            return@withContext ApplyResult(
                false,
                "写入 DeviceProfiles.ini 失败：${deviceResult.message}",
                ApplyStage.ATOMIC_REPLACE,
                System.currentTimeMillis() - started,
            )
        }
        written += devicePath

        onProgress(ApplyProgress(ApplyStage.LOCK, 72, "写入 Engine.ini 并锁定"))
        val engineContent = ConfigCodec.buildEngine(existingEngine, target)
        val engineResult = ops.writeAtomic(enginePath, GameIniCipher.encrypt(engineContent))
        logOp("Engine.ini", engineResult)
        if (!engineResult.success) {
            rollback()
            return@withContext ApplyResult(
                false,
                "写入 Engine.ini 失败：${engineResult.message}",
                ApplyStage.LOCK,
                System.currentTimeMillis() - started,
            )
        }
        written += enginePath

        onProgress(ApplyProgress(ApplyStage.VERIFY_RESULT, 90, "校验写入结果"))
        val verify = verify(gamePath, devicePath, enginePath, target)
        val duration = System.currentTimeMillis() - started

        val diffSummary = prev?.let { p ->
            target.diffFrom(p).joinToString(", ") { "${it.key}: ${it.from} -> ${it.to}" }
        } ?: "首次应用"
        Logx.i(Logx.Module.CONFIG, "应用配置", diffSummary, if (verify) "成功" else "校验未通过", duration)

        if (!verify) {
            onProgress(ApplyProgress(ApplyStage.FAILED, 100, "校验未通过"))
            return@withContext ApplyResult(
                false,
                "配置可能未生效，请重启游戏后再确认",
                ApplyStage.VERIFY_RESULT,
                duration,
            )
        }

        onProgress(ApplyProgress(ApplyStage.DONE, 100, "已完成"))
        val finalMessage = if (anfNote != null) "配置已生效并锁定；$anfNote" else "配置已生效并锁定"
        ApplyResult(true, finalMessage, null, duration)
    }

    // ------------------------------------------------------------ 恢复

    suspend fun reset(channel: Channel, ownerUid: Int?): ApplyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val root = RootDetector.detect()
        if (!root.granted) {
            return@withContext ApplyResult(false, "Root 权限已失效，请重新授权", ApplyStage.VERIFY_ENV)
        }
        val dir = channel.configDir
        if (!ops.probe(dir).exists) {
            return@withContext ApplyResult(false, "配置目录不存在，无需恢复", ApplyStage.VERIFY_ENV)
        }

        val uid = ownerUid ?: gameUid(channel)
        val files = ops.listFiles(dir).filter { it.endsWith(".ini") }
        Logx.i(Logx.Module.FILE, "一键恢复开始", "文件数=${files.size}")

        // 一键恢复要把 ANF 补丁一并撤掉，否则"恢复"之后游戏还带着被改过的 libUnreal.so。
        var anfNote: String? = null
        if (ops.isProcessRunning(channel.pkg)) {
            return@withContext ApplyResult(false, "请先完全退出游戏再恢复", ApplyStage.VERIFY_ENV)
        }
        runCatching { AnfPatcher.restore(channel.pkg) }.fold(
            onSuccess = { anfNote = "ANF 补丁：$it" },
            onFailure = { anfNote = "ANF 补丁未还原（${it.message}）" },
        )
        Logx.i(Logx.Module.FILE, "ANF 还原", anfNote ?: "", channel.pkg)

        if (uid != null) {
            files.forEach { name ->
                val path = "$dir/$name"
                val probe = ops.probe(path)
                if (probe.exists && probe.owner == "root") {
                    val unlocked = ops.unlock(path, uid)
                    logOp("解锁 $name", unlocked)
                }
            }
        }

        val cleared = ops.clearDir(dir)
        logOp("清空目录", cleared)
        val remaining = ops.listFiles(dir)
        val success = cleared.success && remaining.isEmpty()
        val duration = System.currentTimeMillis() - started

        if (success) {
            Logx.i(Logx.Module.FILE, "一键恢复完成", "目录已清空", "成功", duration)
            val msg = buildString {
                append("配置已清空，请启动游戏以重新生成")
                if (anfNote != null) append("；").append(anfNote)
            }
            ApplyResult(true, msg, null, duration)
        } else {
            val detail = if (remaining.isEmpty()) cleared.message else "仍有文件未能删除：${remaining.joinToString(", ")}"
            Logx.e(Logx.Module.FILE, "一键恢复失败", detail, null, duration)
            ApplyResult(false, detail, ApplyStage.FAILED, duration)
        }
    }

    // ------------------------------------------------------------ 校验

    private fun verify(
        gamePath: String,
        devicePath: String,
        enginePath: String,
        expected: GfxParams,
    ): Boolean {
        val game = GameIniCipher.decodeIfNeeded(ops.readText(gamePath)) ?: return false
        val device = GameIniCipher.decodeIfNeeded(ops.readText(devicePath)) ?: return false
        val engine = GameIniCipher.decodeIfNeeded(ops.readText(enginePath)) ?: return false

        if (!ConfigClassifier.isPlainText(game)) return false
        val parsed = ConfigCodec.parseParams(game, device, engine)
        val valuesMatch = parsed.frameRateLimit == expected.frameRateLimit &&
            ParamSpec.screenPercentage(parsed.renderScale) == ParamSpec.screenPercentage(expected.renderScale) &&
            abs(parsed.sharpen - expected.sharpen) < 0.01f &&
            abs(parsed.colorMin - expected.colorMin) < 0.01f &&
            abs(parsed.colorMid - expected.colorMid) < 0.01f &&
            abs(parsed.colorMax - expected.colorMax) < 0.01f &&
            parsed.qualityPreset == expected.qualityPreset &&
            parsed.antiAliasing == expected.antiAliasing &&
            // 以下三项此前没纳入比对，写失败也会报"成功"
            parsed.useVSync == expected.useVSync &&
            parsed.frameGeneration == expected.frameGeneration &&
            parsed.anfSr == expected.anfSr &&
            parsed.anfFrc == expected.anfFrc &&
            parsed.npcQuality == expected.npcQuality &&
            // 自定义档位下 10 个分组必须逐一核对
            (expected.qualityPreset >= 0 ||
                ScalabilityGroup.entries.all { it.of(parsed) == it.of(expected) })

        Logx.i(
            Logx.Module.CONFIG,
            "键值回读",
            "倍率=${parsed.renderScale} 帧率=${parsed.frameRateLimit} " +
                "档位=${parsed.qualityPreset} NPC=${parsed.npcQuality} " +
                "ANF=${parsed.anfSr}/${parsed.anfFrc}",
        )

        val expectedMode = ConfigSchema.LOCK_MODE.toString()
        val perms = listOf(gamePath, devicePath, enginePath).all { path ->
            val probe = ops.probe(path)
            probe.exists && probe.mode == expectedMode && probe.owner == "root"
        }
        if (!valuesMatch) Logx.w(Logx.Module.CONFIG, "键值校验不一致", parsed.toString())
        if (!perms) Logx.w(Logx.Module.CONFIG, "权限校验不通过")
        return valuesMatch && perms
    }

    // ------------------------------------------------------------ ANF 补丁

    /** 只读探测 ANF 补丁状态，不改动任何文件。 */
    suspend fun anfStatus(channel: Channel): AnfPatcher.Status = withContext(Dispatchers.IO) {
        AnfPatcher.status(channel.pkg)
    }

    /** 打 ANF 补丁。界面上的独立开关调用它。 */
    suspend fun patchAnf(channel: Channel): ApplyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (!RootDetector.detect().granted) {
            return@withContext ApplyResult(false, "Root 权限已失效，请重新授权", ApplyStage.VERIFY_ENV)
        }
        if (ops.isProcessRunning(channel.pkg)) {
            return@withContext ApplyResult(false, "请先完全退出游戏", ApplyStage.VERIFY_ENV)
        }
        val r = AnfPatcher.apply(channel.pkg)
        val duration = System.currentTimeMillis() - started
        r.fold(
            onSuccess = {
                Logx.i(Logx.Module.FILE, "ANF 补丁完成", it, "成功", duration)
                ApplyResult(true, "ANF补丁注入成功", null, duration)
            },
            onFailure = {
                Logx.e(Logx.Module.FILE, "ANF 补丁失败", it.message ?: "未知错误", null)
                ApplyResult(false, it.message ?: "补丁失败", ApplyStage.FAILED, duration)
            },
        )
    }

    /** 还原 ANF 补丁（用 /data/local/tmp 下的原始 8 字节备份）。 */
    suspend fun restoreAnfPatch(channel: Channel): ApplyResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        if (!RootDetector.detect().granted) {
            return@withContext ApplyResult(false, "Root 权限已失效，请重新授权", ApplyStage.VERIFY_ENV)
        }
        if (ops.isProcessRunning(channel.pkg)) {
            return@withContext ApplyResult(false, "请先完全退出游戏", ApplyStage.VERIFY_ENV)
        }
        val r = AnfPatcher.restore(channel.pkg)
        val duration = System.currentTimeMillis() - started
        r.fold(
            onSuccess = {
                Logx.i(Logx.Module.FILE, "ANF 还原完成", it, "成功", duration)
                ApplyResult(true, "已移除ANF补丁", null, duration)
            },
            onFailure = {
                Logx.e(Logx.Module.FILE, "ANF 还原失败", it.message ?: "未知错误", null)
                ApplyResult(false, it.message ?: "还原失败", ApplyStage.FAILED, duration)
            },
        )
    }

    private fun logOp(name: String, result: FileOpResult) {
        result.steps.forEach { step ->
            val level = if (step.ok) Logx.Level.INFO else Logx.Level.ERROR
            val line = "命令=${step.command.take(160)} 退出码=${step.code}"
            when (level) {
                Logx.Level.INFO -> Logx.i(Logx.Module.FILE, "$name · ${step.name}", line, "ok", step.durationMs)
                else -> Logx.e(Logx.Module.FILE, "$name · ${step.name}", line, step.stderr.ifBlank { "failed" }, step.durationMs)
            }
        }
    }

    private fun stageOf(stepName: String): ApplyStage = when {
        stepName.contains("目录") -> ApplyStage.VERIFY_ENV
        stepName.contains("临时") -> ApplyStage.WRITE_TEMP
        stepName.contains("替换") -> ApplyStage.ATOMIC_REPLACE
        stepName.contains("属主") || stepName.contains("权限") -> ApplyStage.LOCK
        else -> ApplyStage.WRITE_TEMP
    }
}
