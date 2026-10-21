package com.yunxi.ntegfx.core.config

import com.yunxi.ntegfx.data.AaMethod
import com.yunxi.ntegfx.data.GfxParams
import com.yunxi.ntegfx.data.ParamSpec
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

        return GfxParams(
            frameRateLimit = frameRate,
            useVSync = vsync,
            renderScale = scale,
            frameGeneration = frameGeneration,
            colorMin = min,
            colorMid = mid,
            colorMax = max,
            qualityPreset = g?.get(ConfigSchema.KEY_QUALITY)?.toIntOrNull()
                ?.coerceIn(ParamSpec.QUALITY_PRESET_MIN, ParamSpec.QUALITY_PRESET_MAX) ?: 4,
            antiAliasing = AaMethod.from(g?.get(ConfigSchema.KEY_AA_METHOD)),
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
        doc.set(ConfigSchema.KEY_AA_METHOD, params.antiAliasing.iniValue, anchor)

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
        return text
    }

    fun buildEngine(existing: String?, params: GfxParams): String {
        val doc = if (existing != null && ConfigClassifier.isPlainText(existing)) {
            IniDocument.parse(existing)
        } else {
            IniDocument.parse(templateEngine(params))
        }
        val anchor = if (doc.hasSection(ConfigSchema.SECTION_ENGINE)) ConfigSchema.SECTION_ENGINE else null
        doc.set(ConfigSchema.KEY_COLOR_MIN, trimFloat(params.colorMin), anchor)
        doc.set(ConfigSchema.KEY_COLOR_MID, trimFloat(params.colorMid), anchor)
        doc.set(ConfigSchema.KEY_COLOR_MAX, trimFloat(params.colorMax), anchor)
        return doc.toText()
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
        gDoc.set(ConfigSchema.KEY_AA_METHOD, params.antiAliasing.iniValue, gAnchor)

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
        appendLine("${ConfigSchema.KEY_COLOR_MAX}=${trimFloat(params.colorMax)}")
        appendLine("${ConfigSchema.KEY_COLOR_MID}=${trimFloat(params.colorMid)}")
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
