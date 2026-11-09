package com.yunxi.ntegfx.core.config

import com.yunxi.ntegfx.data.AaMethod
import com.yunxi.ntegfx.data.GfxParams
import com.yunxi.ntegfx.data.ParamSpec
import com.yunxi.ntegfx.data.ScalabilityGroup
import java.util.Locale

/**
 * 参数与三份配置文件之间的双向映射。
 *
 * 依据实际文件校正后的规则：
 * - 渲染倍率以 DeviceProfiles 的 `CVars=r.MobileContentScaleFactor` 为唯一来源；
 * - 屏幕百分比 = 倍率 × 100，同时写入 DeviceProfiles 全部机型段与 GameUserSettings；
 * - DeviceProfiles 的 156 个机型段必须全量替换，否则游戏按 GPU 匹配到哪段就读哪段的值。
 */
object ConfigCodec {


    private fun cvarRegex(key: String) =
        Regex("""(?m)^([ \t]*CVars[ \t]*=[ \t]*${Regex.escape(key)}[ \t]*=)(.*)$""")

    private fun cvarReadRegex(key: String) =
        Regex("""(?m)^[ \t]*CVars[ \t]*=[ \t]*${Regex.escape(key)}[ \t]*=[ \t]*(.+?)[ \t]*$""")

    // ---------------------------------------------------------------- 读取

    fun readCvar(text: String?, key: String): String? {
        if (text.isNullOrBlank()) return null
        return cvarReadRegex(key).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun countCvar(text: String?, key: String): Int {
        if (text.isNullOrBlank()) return 0
        return cvarReadRegex(key).findAll(text).count()
    }

    fun parseParams(gameUser: String?, device: String?, engine: String?): GfxParams {
        val g = gameUser?.takeIf { ConfigClassifier.isPlainText(it) }?.let(IniDocument::parse)
        val e = engine?.takeIf { ConfigClassifier.isPlainText(it) }?.let(IniDocument::parse)

        val frameRate = g?.get(ConfigSchema.KEY_FRAME_RATE)?.toFloatOrNull()?.toInt()
            ?.coerceIn(ParamSpec.frameRateRange) ?: 120
        val vsync = g?.get(ConfigSchema.KEY_VSYNC)?.toUeBool() ?: true

        // 倍率以 DeviceProfiles 为准；缺失时用 ScreenPercentage 反推（1:100）。
        val scaleFromDevice = readCvar(device, ConfigSchema.KEY_CONTENT_SCALE)?.toFloatOrNull()
        val scale = when {
            scaleFromDevice != null -> ParamSpec.clampScale(scaleFromDevice)
            else -> {
                val percent = readCvar(device, ConfigSchema.KEY_R_SCREEN_PERCENT)?.toFloatOrNull()
                    ?: g?.get(ConfigSchema.KEY_SCREEN_PERCENT)?.toFloatOrNull()
                if (percent != null) ParamSpec.clampScale(percent / 100f) else 2.0f
            }
        }

        val fgValues = listOf(
            g?.get(ConfigSchema.KEY_AFME),
            g?.get(ConfigSchema.KEY_HWFG),
            g?.get(ConfigSchema.KEY_MFRC),
            g?.get(ConfigSchema.KEY_FSR_FG),
        ).mapNotNull { it?.toUeBool() }
        val frameGeneration = if (fgValues.isEmpty()) true else fgValues.count { it } >= 2

        val min = e?.get(ConfigSchema.KEY_COLOR_MIN)?.toFloatOrNull()?.let(ParamSpec::clampMin) ?: 0.00f
        val mid = e?.get(ConfigSchema.KEY_COLOR_MID)?.toFloatOrNull()?.let(ParamSpec::clampMid) ?: 0.60f
        val max = e?.get(ConfigSchema.KEY_COLOR_MAX)?.toFloatOrNull()?.let(ParamSpec::clampMax) ?: 1.20f

        // 锐化只认 [Setting] 段的 Graphics_Sharpen；旧配置缺键时回到游戏原值 0.1。
        val sharpen = g?.get(ConfigSchema.KEY_SHARPEN, ConfigSchema.SECTION_SETTING)
            ?.toFloatOrNull()
            ?.let(ParamSpec::clampSharpen)
            ?: 0.10f

        // 自定义档位下逐项生效的分组值
        val sg = ScalabilityGroup.entries.associateWith { grp ->
            g?.get(grp.iniKey)?.toIntOrNull()?.coerceIn(0, 4) ?: 4
        }

        return GfxParams(
            frameRateLimit = frameRate,
            useVSync = vsync,
            renderScale = scale,
            frameGeneration = frameGeneration,
            sharpen = sharpen,
            colorMin = min,
            colorMid = mid,
            colorMax = max,
            // -1 是游戏自己的"自定义"标记：此时单项 sg.* 才生效，预设不再覆盖。
            // 必须原样保留，不能 coerce 到 0，否则一应用就把画质打成"性能"。
            qualityPreset = g?.get(ConfigSchema.KEY_QUALITY)?.toIntOrNull()?.coerceIn(-1, 4) ?: 4,
            sgViewDistance = sg.getValue(ScalabilityGroup.VIEW_DISTANCE),
            sgFoliage = sg.getValue(ScalabilityGroup.FOLIAGE),
            sgTexture = sg.getValue(ScalabilityGroup.TEXTURE),
            sgShading = sg.getValue(ScalabilityGroup.SHADING),
            sgPostProcess = sg.getValue(ScalabilityGroup.POST_PROCESS),
            sgReflection = sg.getValue(ScalabilityGroup.REFLECTION),
            sgShadow = sg.getValue(ScalabilityGroup.SHADOW),
            sgGlobalIllumination = sg.getValue(ScalabilityGroup.GI),
            sgEffects = sg.getValue(ScalabilityGroup.EFFECTS),
            sgLandscape = sg.getValue(ScalabilityGroup.LANDSCAPE),
            npcQuality = g?.get(ConfigSchema.KEY_NPC_QUALITY)?.toIntOrNull()?.coerceIn(0, 4) ?: 3,
            antiAliasing = AaMethod.from(g?.get(ConfigSchema.KEY_AA_METHOD)),
            // ANF 两个开关缺键时一律按"关闭"处理：它们需要 libUnreal.so 补丁才生效，
            // 不能让未打补丁的用户被默认打开。
            anfSr = g?.get(ConfigSchema.KEY_ANF_SR)?.toUeBool() ?: false,
            anfFrc = g?.get(ConfigSchema.KEY_ANF_FRC)?.toUeBool() ?: false,
        )
    }

    // ---------------------------------------------------------------- 写入

    fun buildGameUserSettings(
        existing: String?,
        params: GfxParams,
        screenWidth: Int,
        screenHeight: Int,
    ): String {
        val doc = if (existing != null && ConfigClassifier.isPlainText(existing)) {
            IniDocument.parse(existing)
        } else {
            IniDocument.parse(templateGameUserSettings(params, screenWidth, screenHeight))
        }
        val anchor = if (doc.hasSection(ConfigSchema.SECTION_GAME_USER)) ConfigSchema.SECTION_GAME_USER else null

        doc.set(ConfigSchema.KEY_FRAME_RATE, f6(params.frameRateLimit), anchor)
        doc.set(ConfigSchema.KEY_VSYNC, bool(params.useVSync), anchor)
        doc.set(ConfigSchema.KEY_DYNAMIC_RES, "False", anchor)
        doc.set(ConfigSchema.KEY_SCREEN_PERCENT, ParamSpec.screenPercentage(params.renderScale).toString(), anchor)
        doc.set(ConfigSchema.KEY_AFME, bool(params.frameGeneration), anchor)
        doc.set(ConfigSchema.KEY_HWFG, bool(params.frameGeneration), anchor)
        doc.set(ConfigSchema.KEY_MFRC, bool(params.frameGeneration), anchor)
        doc.set(ConfigSchema.KEY_FSR_FG, bool(params.frameGeneration), anchor)
        doc.set(ConfigSchema.KEY_QUALITY, params.qualityPreset.toString(), anchor)
        writeScalability(doc, params)
        doc.set(ConfigSchema.KEY_NPC_QUALITY, params.npcQuality.toString(), anchor)
        doc.set(ConfigSchema.KEY_AA_METHOD, params.antiAliasing.iniValue, anchor)
        doc.set(ConfigSchema.KEY_ANF_SR, bool(params.anfSr), anchor)
        doc.set(ConfigSchema.KEY_ANF_FRC, bool(params.anfFrc), anchor)
        doc.set(ConfigSchema.KEY_SHARPEN, trimFloat(params.sharpen), ConfigSchema.SECTION_SETTING)

        if (doc.get(ConfigSchema.KEY_RES_X, anchor) == null) {
            doc.set(ConfigSchema.KEY_RES_X, screenWidth.toString(), anchor)
            doc.set(ConfigSchema.KEY_RES_Y, screenHeight.toString(), anchor)
        }
        return doc.toText()
    }

    /** DeviceProfiles：把三组 CVar 在全部机型段中同步替换。 */
    fun buildDeviceProfiles(existing: String?, params: GfxParams): String {
        val source = existing?.takeIf { ConfigClassifier.isPlainText(it) }
            ?: return templateDeviceProfiles(params)
        val percent = ParamSpec.screenPercentage(params.renderScale).toString()
        val scale = trimFloat(params.renderScale)

        var text = source
        text = replaceCvar(text, ConfigSchema.KEY_CONTENT_SCALE, scale)
        text = replaceCvar(text, ConfigSchema.KEY_SECONDARY_PERCENT, percent)
        text = replaceCvar(text, ConfigSchema.KEY_R_SCREEN_PERCENT, percent)
        return stripDeadDeviceCvars(text)
    }

    /**
     * 删掉在本作里查不到消费方的 CVar 行。
     * 已有用户的 DeviceProfiles 是旧模板（每个机型段 12 条，其中 7 条无效），
     * 应用时顺手清掉，不必让用户先「一键恢复」再重新初始化。
     */
    private fun stripDeadDeviceCvars(input: String): String {
        var out = input
        for (key in ConfigSchema.DEVICE_DEAD_CVARS) {
            // 末行可能没有换行符，所以结尾允许用 $ 兜底
            val pattern = Regex("(?m)^[ \\t]*CVars=" + Regex.escape(key) + "=.*(\\r?\\n|\\z)")
            out = pattern.replace(out, "")
        }
        return out.replace(Regex("(\\r?\\n){3,}"), "\n\n")
    }

    /**
     * 自定义档位（qualityPreset == -1）时，把 10 个分组写进 `[ScalabilityGroups]`。
     * 预设档位下不写——写了也会被游戏用预设覆盖，反而让「应用后单项被重置」更容易发生。
     */
    private fun writeScalability(doc: IniDocument, params: GfxParams) {
        if (params.qualityPreset >= 0) return
        // 只写游戏界面里有对应入口的分组；无 UI 的几项先不动，等实测确认后再放开。
        ScalabilityGroup.entries.filter { it.hasGameUi }.forEach { group ->
            doc.set(group.iniKey, group.of(params).toString(), ConfigSchema.SECTION_SCALABILITY)
        }
    }

    fun buildEngine(existing: String?, params: GfxParams): String {        val doc = if (existing != null && ConfigClassifier.isPlainText(existing)) {
            IniDocument.parse(existing)
        } else {
            IniDocument.parse(templateEngine(params))
        }
        val anchor = if (doc.hasSection(ConfigSchema.SECTION_ENGINE)) ConfigSchema.SECTION_ENGINE else null
        doc.set(ConfigSchema.KEY_COLOR_MIN, trimFloat(params.colorMin), anchor)
        doc.set(ConfigSchema.KEY_COLOR_MID, trimFloat(params.colorMid), anchor)
        doc.set(ConfigSchema.KEY_COLOR_MAX, trimFloat(params.colorMax), anchor)
        applyAnfCvars(doc, params)
        return doc.toText()
    }

    /**
     * ANF 的两条 CVar 是"能不能用"的总闸门，游戏自身从不注册它们（全库只有 Find 没有 Register），
     * 只能靠 ini 造出来。关掉 ANF 时一并移除，让游戏界面里的选项随之消失。
     */
    private fun applyAnfCvars(doc: IniDocument, params: GfxParams) {
        // 渲染分辨率也写进 [ConsoleVariables]：设备档案只在该机型被匹配到时才执行，
        // 少数机型匹配不到就会完全失效；Engine.ini 是全局的，可作兜底。
        val percent = ParamSpec.screenPercentage(params.renderScale).toString()
        doc.set(ConfigSchema.KEY_R_SCREEN_PERCENT_GLOBAL, percent, ConfigSchema.SECTION_CONSOLE_VARS)
        doc.set(ConfigSchema.KEY_R_SECONDARY_PERCENT_GLOBAL, percent, ConfigSchema.SECTION_CONSOLE_VARS)

        if (params.anfSr || params.anfFrc) {
            doc.set(ConfigSchema.KEY_AIFF_ALLOWED, "1", ConfigSchema.SECTION_CONSOLE_VARS)
            doc.set(ConfigSchema.KEY_AIFF_ENABLED, "1", ConfigSchema.SECTION_CONSOLE_VARS)
        } else {
            doc.remove(ConfigSchema.KEY_AIFF_ALLOWED, ConfigSchema.SECTION_CONSOLE_VARS)
            doc.remove(ConfigSchema.KEY_AIFF_ENABLED, ConfigSchema.SECTION_CONSOLE_VARS)
        }
    }

    fun replaceCvar(text: String, key: String, value: String): String {
        val regex = cvarRegex(key)
        if (!regex.containsMatchIn(text)) return text
        return regex.replace(text) { match -> match.groupValues[1] + value }
    }

    // ---------------------------------------------------------------- 初始化模板处理

    /**
     * 把内置模板整理成可写入的初版：对齐屏幕百分比口径、修正布尔拼写、替换为本机分辨率。
     * 不改变段落结构与未知键。
     */
    fun prepareTemplate(
        gameUser: String,
        device: String,
        engine: String,
        params: GfxParams,
        screenWidth: Int,
        screenHeight: Int,
    ): Triple<String, String, String> {
        val percent = ParamSpec.screenPercentage(params.renderScale)

        var deviceText = device
        deviceText = replaceCvar(deviceText, ConfigSchema.KEY_CONTENT_SCALE, trimFloat(params.renderScale))
        deviceText = replaceCvar(deviceText, ConfigSchema.KEY_SECONDARY_PERCENT, percent.toString())
        deviceText = replaceCvar(deviceText, ConfigSchema.KEY_R_SCREEN_PERCENT, percent.toString())
        deviceText = stripDeadDeviceCvars(deviceText)

        val gDoc = IniDocument.parse(gameUser)
        val gAnchor = if (gDoc.hasSection(ConfigSchema.SECTION_GAME_USER)) {
            ConfigSchema.SECTION_GAME_USER
        } else {
            null
        }
        gDoc.set(ConfigSchema.KEY_FRAME_RATE, f6(params.frameRateLimit), gAnchor)
        gDoc.set(ConfigSchema.KEY_VSYNC, bool(params.useVSync), gAnchor)
        gDoc.set(ConfigSchema.KEY_DYNAMIC_RES, "False", gAnchor)
        gDoc.set(ConfigSchema.KEY_SCREEN_PERCENT, percent.toString(), gAnchor)
        gDoc.set(ConfigSchema.KEY_AFME, bool(params.frameGeneration), gAnchor)
        gDoc.set(ConfigSchema.KEY_HWFG, bool(params.frameGeneration), gAnchor)
        gDoc.set(ConfigSchema.KEY_MFRC, bool(params.frameGeneration), gAnchor)
        gDoc.set(ConfigSchema.KEY_FSR_FG, bool(params.frameGeneration), gAnchor)
        gDoc.set(ConfigSchema.KEY_QUALITY, params.qualityPreset.toString(), gAnchor)
        writeScalability(gDoc, params)
        gDoc.set(ConfigSchema.KEY_NPC_QUALITY, params.npcQuality.toString(), gAnchor)
        gDoc.set(ConfigSchema.KEY_AA_METHOD, params.antiAliasing.iniValue, gAnchor)
        gDoc.set(ConfigSchema.KEY_ANF_SR, bool(params.anfSr), gAnchor)
        gDoc.set(ConfigSchema.KEY_ANF_FRC, bool(params.anfFrc), gAnchor)
        gDoc.set(ConfigSchema.KEY_SHARPEN, trimFloat(params.sharpen), ConfigSchema.SECTION_SETTING)

        val width = if (screenWidth > 0) screenWidth else 2780
        val height = if (screenHeight > 0) screenHeight else 1264
        gDoc.set(ConfigSchema.KEY_RES_X, width.toString(), gAnchor)
        gDoc.set(ConfigSchema.KEY_RES_Y, height.toString(), gAnchor)
        gDoc.set("LastUserConfirmedResolutionSizeX", width.toString(), gAnchor)
        gDoc.set("LastUserConfirmedResolutionSizeY", height.toString(), gAnchor)
        gDoc.set("DesiredScreenWidth", width.toString(), gAnchor)
        gDoc.set("DesiredScreenHeight", height.toString(), gAnchor)

        val eDoc = IniDocument.parse(engine)
        val eAnchor = if (eDoc.hasSection(ConfigSchema.SECTION_ENGINE)) ConfigSchema.SECTION_ENGINE else null
        eDoc.set(ConfigSchema.KEY_COLOR_MIN, trimFloat(params.colorMin), eAnchor)
        eDoc.set(ConfigSchema.KEY_COLOR_MID, trimFloat(params.colorMid), eAnchor)
        eDoc.set(ConfigSchema.KEY_COLOR_MAX, trimFloat(params.colorMax), eAnchor)
        applyAnfCvars(eDoc, params)

        return Triple(gDoc.toText(), deviceText, eDoc.toText())
    }

    // ---------------------------------------------------------------- 兜底模板

    fun templateGameUserSettings(params: GfxParams, screenWidth: Int, screenHeight: Int): String =
        buildString {
            appendLine("[${ConfigSchema.SECTION_GAME_USER}]")
            appendLine("Version=5")
            appendLine("${ConfigSchema.KEY_FRAME_RATE}=${f6(params.frameRateLimit)}")
            appendLine("${ConfigSchema.KEY_VSYNC}=${bool(params.useVSync)}")
            appendLine("${ConfigSchema.KEY_DYNAMIC_RES}=False")
            appendLine("${ConfigSchema.KEY_SCREEN_PERCENT}=${ParamSpec.screenPercentage(params.renderScale)}")
            appendLine("${ConfigSchema.KEY_RES_X}=$screenWidth")
            appendLine("${ConfigSchema.KEY_RES_Y}=$screenHeight")
            appendLine("${ConfigSchema.KEY_AFME}=${bool(params.frameGeneration)}")
            appendLine("${ConfigSchema.KEY_HWFG}=${bool(params.frameGeneration)}")
            appendLine("${ConfigSchema.KEY_MFRC}=${bool(params.frameGeneration)}")
            appendLine("${ConfigSchema.KEY_FSR_FG}=${bool(params.frameGeneration)}")
            appendLine()
            appendLine("[${ConfigSchema.SECTION_SETTING}]")
            appendLine("${ConfigSchema.KEY_SHARPEN}=${trimFloat(params.sharpen)}")
        }

    fun templateDeviceProfiles(params: GfxParams): String {
        val percent = ParamSpec.screenPercentage(params.renderScale)
        return buildString {
            appendLine("[Android_Default DeviceProfile]")
            appendLine("CVars=${ConfigSchema.KEY_CONTENT_SCALE}=${trimFloat(params.renderScale)}")
            appendLine("CVars=${ConfigSchema.KEY_SECONDARY_PERCENT}=$percent")
            appendLine("CVars=${ConfigSchema.KEY_R_SCREEN_PERCENT}=$percent")
        }
    }

    fun templateEngine(params: GfxParams): String = buildString {
        appendLine("[${ConfigSchema.SECTION_ENGINE}]")
        appendLine("${ConfigSchema.KEY_COLOR_MIN}=${trimFloat(params.colorMin)}")
        appendLine("${ConfigSchema.KEY_COLOR_MID}=${trimFloat(params.colorMid)}")
        appendLine("${ConfigSchema.KEY_COLOR_MAX}=${trimFloat(params.colorMax)}")
        // 防伪标记：走兜底模板时也必须带上，否则内置资源与兜底产物不一致
        appendLine("${ConfigSchema.KEY_MARKER}=1")
    }

    // ---------------------------------------------------------------- 工具

    private fun String.toUeBool(): Boolean = when (trim().lowercase(Locale.US)) {
        "true", "1", "yes" -> true
        else -> false
    }

    private fun bool(value: Boolean): String = if (value) "True" else "False"

    private fun f6(value: Number): String = String.format(Locale.US, "%.6f", value.toFloat())

    fun trimFloat(value: Float): String {
        val text = String.format(Locale.US, "%.4f", value)
        return text.trimEnd('0').trimEnd('.')
    }
}
