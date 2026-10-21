package com.yunxi.ntegfx.data

import kotlin.math.roundToInt

/** 规格 3.2：区服即包名，配置目录按包名拼接。 */
enum class Channel(
    val pkg: String,
    val shortName: String,
    val fullNameZh: String,
    val fullNameEn: String,
) {
    OFFICIAL("com.hottagames.yh.laohu", "官服", "官服", "Official server"),
    BILI("com.hottagames.yh.bilibili", "B服", "哔哩哔哩服", "bilibili server"),
    GLOBAL("tw.com.iwplay.nte", "台服", "台服", "TW server");

    /** 规格 3.1：统一使用 /data/user/0 展开形式。 */
    val configDir: String
        get() = "/data/user/0/$pkg/files/UnrealGame/HT/HT/Saved/Config/Android"

    companion object {
        fun ofPackage(pkg: String?): Channel? = entries.firstOrNull { it.pkg == pkg }
    }
}

/** 规格 3.4：由文件内容判定的三态，外加"目录不存在"。 */
enum class ConfigState {
    NOT_INITIALIZED,
    ORIGINAL,
    APPLIED,
    UNKNOWN,
}

enum class ParamKey {
    FRAME_RATE,
    VSYNC,
    RENDER_SCALE,
    FRAME_GENERATION,
    COLOR_MIN,
    COLOR_MID,
    COLOR_MAX,
    QUALITY_PRESET,
    AA_METHOD;

    val isBoolean: Boolean
        get() = this == VSYNC || this == FRAME_GENERATION
}

data class ParamDiff(
    val key: ParamKey,
    val from: Float,
    val to: Float,
)

/** 抗锯齿算法，对应 `MobileAAMethod`。 */
enum class AaMethod(val iniValue: String) {
    GSR("GSR"),
    TAA("TAA");

    companion object {
        fun from(value: String?): AaMethod =
            entries.firstOrNull { it.iniValue.equals(value?.trim(), ignoreCase = true) } ?: TAA
    }
}

/** 游戏画质预设，对应 `GraphicQualityLevel` 的 0–4。 */
enum class QualityPreset(val level: Int) {
    PERFORMANCE(0),
    SMOOTH(1),
    BALANCED(2),
    EXTREME(3),
    ORIGINAL(4);

    companion object {
        fun from(value: Int?): QualityPreset =
            entries.firstOrNull { it.level == value } ?: ORIGINAL
    }
}

/** 界面暴露的全部可调参数。 */
data class GfxParams(
    val frameRateLimit: Int = 120,
    val useVSync: Boolean = true,
    val renderScale: Float = 2.0f,
    val frameGeneration: Boolean = true,
    val colorMin: Float = 0.00f,
    val colorMid: Float = 0.60f,
    val colorMax: Float = 1.20f,
    val qualityPreset: Int = 4,
    val antiAliasing: AaMethod = AaMethod.TAA,
) {
    fun diffFrom(other: GfxParams): List<ParamDiff> {
        val list = mutableListOf<ParamDiff>()
        if (frameRateLimit != other.frameRateLimit) {
            list += ParamDiff(ParamKey.FRAME_RATE, other.frameRateLimit.toFloat(), frameRateLimit.toFloat())
        }
        if (useVSync != other.useVSync) {
            list += ParamDiff(ParamKey.VSYNC, if (other.useVSync) 1f else 0f, if (useVSync) 1f else 0f)
        }
        if (renderScale != other.renderScale) {
            list += ParamDiff(ParamKey.RENDER_SCALE, other.renderScale, renderScale)
        }
        if (frameGeneration != other.frameGeneration) {
            list += ParamDiff(
                ParamKey.FRAME_GENERATION,
                if (other.frameGeneration) 1f else 0f,
                if (frameGeneration) 1f else 0f,
            )
        }
        if (colorMin != other.colorMin) {
            list += ParamDiff(ParamKey.COLOR_MIN, other.colorMin, colorMin)
        }
        if (colorMid != other.colorMid) {
            list += ParamDiff(ParamKey.COLOR_MID, other.colorMid, colorMid)
        }
        if (colorMax != other.colorMax) {
            list += ParamDiff(ParamKey.COLOR_MAX, other.colorMax, colorMax)
        }
        if (qualityPreset != other.qualityPreset) {
            list += ParamDiff(ParamKey.QUALITY_PRESET, other.qualityPreset.toFloat(), qualityPreset.toFloat())
        }
        if (antiAliasing != other.antiAliasing) {
            list += ParamDiff(
                ParamKey.AA_METHOD,
                other.antiAliasing.ordinal.toFloat(),
                antiAliasing.ordinal.toFloat(),
            )
        }
        return list
    }
}

object ParamSpec {
    val frameRateOptions = listOf(30, 45, 60, 90, 120, 144)
    val frameRateRange = 30..240
    const val QUALITY_PRESET_MIN = 0
    const val QUALITY_PRESET_MAX = 4
    const val RENDER_SCALE_MIN = 0.35f
    const val RENDER_SCALE_MAX = 3.5f
    const val RENDER_SCALE_STEP = 0.05f
    const val COLOR_MIN_MIN = 0.00f
    const val COLOR_MIN_MAX = 2.00f
    const val COLOR_MID_MIN = 0.20f
    const val COLOR_MID_MAX = 2.40f
    const val COLOR_MAX_MIN = 0.60f
    const val COLOR_MAX_MAX = 4.80f

    /**
     * 渲染倍率与屏幕百分比的口径：以 DeviceProfiles 为准，1 : 100 线性对应。
     * 3.0× → 300，3.5× → 350，4.0× → 400。     */
    fun screenPercentage(scale: Float): Int = (scale * 100f).roundToInt()

    fun scaleLabel(scale: Float): String {
        val steps = (scale * 20f).roundToInt()
        val text = if (steps % 2 == 0) {
            String.format(java.util.Locale.US, "%.1f", scale)
        } else {
            String.format(java.util.Locale.US, "%.2f", scale)
        }
        return "$text×"
    }

    fun clampScale(scale: Float): Float =
        ((scale.coerceIn(RENDER_SCALE_MIN, RENDER_SCALE_MAX) * 20f).roundToInt() / 20f)

    fun clampMid(value: Float): Float =
        ((value.coerceIn(COLOR_MID_MIN, COLOR_MID_MAX) * 20f).roundToInt() / 20f)

    fun clampMin(value: Float): Float =
        ((value.coerceIn(COLOR_MIN_MIN, COLOR_MIN_MAX) * 20f).roundToInt() / 20f)

    fun clampMax(value: Float): Float =
        ((value.coerceIn(COLOR_MAX_MIN, COLOR_MAX_MAX) * 20f).roundToInt() / 20f)
}

/** 一次检测得到的完整现场信息。 */
data class ConfigSnapshot(
    val channel: Channel,
    val installed: Boolean = false,
    val dirExists: Boolean = false,
    val state: ConfigState = ConfigState.NOT_INITIALIZED,
    val params: GfxParams? = null,
    val gameVersion: String? = null,
    val ownerUid: Int? = null,
    val detail: String? = null,
    val checkedAt: Long = System.currentTimeMillis(),
)

/** 应用配置的分阶段进度，对应规格 5.4 进度表。 */
enum class ApplyStage {
    IDLE,
    VERIFY_ENV,
    WRITE_TEMP,
    ATOMIC_REPLACE,
    LOCK,
    VERIFY_RESULT,
    DONE,
    FAILED,
}

data class ApplyProgress(
    val stage: ApplyStage = ApplyStage.IDLE,
    val percent: Int = 0,
    val detail: String? = null,
)

data class ApplyResult(
    val success: Boolean,
    val message: String,
    val failedStage: ApplyStage? = null,
    val durationMs: Long = 0L,
)
