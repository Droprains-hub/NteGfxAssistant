package com.yunxi.ntegfx.data

import kotlin.math.abs
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
    GLOBAL("tw.com.iwplay.nte", "台服", "台服", "TW server"),
    INTL("com.hottagames.nte", "国际服", "国际服", "International server");

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
    ANF_SR,
    ANF_FRC,
    SHARPEN,
    TONE_PRESET,
    COLOR_MIN,
    COLOR_MID,
    COLOR_MAX,
    QUALITY_PRESET,
    NPC_QUALITY,
    AA_METHOD;

    val isBoolean: Boolean
        get() = this == VSYNC || this == FRAME_GENERATION || this == ANF_SR || this == ANF_FRC
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

/**
 * 色调曲线一键预设，绑定 `r.Color.Min/Mid/Max` 三元组。
 * 数值取自实测文档：Min=0 保持最深黑场；Mid 决定整体明度（0.9 明显偏亮、0.2 偏黑）；
 * Max 控制高光压缩程度。所有取值均落在 ParamSpec 的 0.05 网格与钳位区间内。
 */
enum class TonePreset(
    val colorMin: Float,
    val colorMid: Float,
    val colorMax: Float,
) {
    /** 游戏原版曲线：0.00 / 0.60 / 1.20。 */
    STANDARD(0.00f, 0.60f, 1.20f),
    /** 抬黑位、提中间调，画面更柔更亮，适合暗场景。 */
    SOFT(0.05f, 0.70f, 1.25f),
    /** 压中间调、抬白点，对比更强、画面更通透。 */
    PUNCHY(0.00f, 0.50f, 1.40f),
    /** 整体提亮，适合户外强光下看清暗部。 */
    BRIGHT(0.00f, 0.75f, 1.30f),
    /** 压中间调与高光，暗部略抬，接近电影调色。 */
    CINEMATIC(0.05f, 0.50f, 1.10f);

    /** 与给定参数是否一致（半步容差，滑条步进为 0.05）。 */
    fun matches(params: GfxParams): Boolean =
        abs(params.colorMin - colorMin) < 0.03f &&
            abs(params.colorMid - colorMid) < 0.03f &&
            abs(params.colorMax - colorMax) < 0.03f

    companion object {
        /** 当前参数不匹配任何预设时返回 null（界面显示为自定义）。 */
        fun currentOf(params: GfxParams): TonePreset? = entries.firstOrNull { it.matches(params) }
    }
}

/** 界面暴露的全部可调参数。 */
/**
 * `[ScalabilityGroups]` 里可逐项调节的分组。
 *
 * 只有在 `GraphicQualityLevel = -1`（自定义模式）时这些值才生效；
 * 档位为 0–4 时游戏会用预设覆盖它们。
 *
 * 其中 REFLECTION / SHADOW / GI / EFFECTS / LANDSCAPE 五组在游戏界面里没有入口，
 * 只能通过配置文件修改。
 */
enum class ScalabilityGroup(val iniKey: String, val hasGameUi: Boolean) {
    VIEW_DISTANCE("sg.ViewDistanceQuality", true),
    FOLIAGE("sg.FoliageQuality", true),
    TEXTURE("sg.TextureQuality", true),
    SHADING("sg.ShadingQuality", true),
    POST_PROCESS("sg.PostProcessQuality", true),
    REFLECTION("sg.ReflectionQuality", false),
    SHADOW("sg.ShadowQuality", false),
    GI("sg.GlobalIlluminationQuality", false),
    EFFECTS("sg.EffectsQuality", false),
    LANDSCAPE("sg.LandscapeQuality", false);

    fun of(p: GfxParams): Int = when (this) {
        VIEW_DISTANCE -> p.sgViewDistance
        FOLIAGE -> p.sgFoliage
        TEXTURE -> p.sgTexture
        SHADING -> p.sgShading
        POST_PROCESS -> p.sgPostProcess
        REFLECTION -> p.sgReflection
        SHADOW -> p.sgShadow
        GI -> p.sgGlobalIllumination
        EFFECTS -> p.sgEffects
        LANDSCAPE -> p.sgLandscape
    }

    fun apply(p: GfxParams, level: Int): GfxParams = when (this) {
        VIEW_DISTANCE -> p.copy(sgViewDistance = level)
        FOLIAGE -> p.copy(sgFoliage = level)
        TEXTURE -> p.copy(sgTexture = level)
        SHADING -> p.copy(sgShading = level)
        POST_PROCESS -> p.copy(sgPostProcess = level)
        REFLECTION -> p.copy(sgReflection = level)
        SHADOW -> p.copy(sgShadow = level)
        GI -> p.copy(sgGlobalIllumination = level)
        EFFECTS -> p.copy(sgEffects = level)
        LANDSCAPE -> p.copy(sgLandscape = level)
    }
}

data class GfxParams(
    val frameRateLimit: Int = 120,
    val useVSync: Boolean = true,
    val renderScale: Float = 2.0f,
    val frameGeneration: Boolean = true,
    /** ANF 超分（AIFF SR），需要配合 libUnreal.so 补丁才会真正生效。 */
    val anfSr: Boolean = false,
    /** ANF 插帧（AIFF FRC）。开启 ANF 超分时游戏会自动关闭它。 */
    val anfFrc: Boolean = false,
    val sharpen: Float = 0.10f,
    val colorMin: Float = 0.00f,
    val colorMid: Float = 0.60f,
    val colorMax: Float = 1.20f,
    val qualityPreset: Int = 4,
    /** 自定义档位下逐项生效的 [ScalabilityGroup] 值（0–4），仅 qualityPreset == -1 时使用。 */
    val sgViewDistance: Int = 4,
    val sgFoliage: Int = 4,
    val sgTexture: Int = 4,
    val sgShading: Int = 4,
    val sgPostProcess: Int = 4,
    val sgReflection: Int = 4,
    val sgShadow: Int = 4,
    val sgGlobalIllumination: Int = 4,
    val sgEffects: Int = 4,
    val sgLandscape: Int = 4,
    /** NPC / 车流密度档位（0–4），对应 NPCQuality。 */
    val npcQuality: Int = 3,
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
        if (anfSr != other.anfSr) {
            list += ParamDiff(ParamKey.ANF_SR, if (other.anfSr) 1f else 0f, if (anfSr) 1f else 0f)
        }
        if (anfFrc != other.anfFrc) {
            list += ParamDiff(ParamKey.ANF_FRC, if (other.anfFrc) 1f else 0f, if (anfFrc) 1f else 0f)
        }
        if (sharpen != other.sharpen) {
            list += ParamDiff(ParamKey.SHARPEN, other.sharpen, sharpen)
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
        if (npcQuality != other.npcQuality) {
            list += ParamDiff(ParamKey.NPC_QUALITY, other.npcQuality.toFloat(), npcQuality.toFloat())
        }
        // 自定义档位下才比较各分组，避免预设模式下刷一堆无意义的差异；
        // 只比较软件里实际开放的分组，隐藏的 5 项不参与，防止产生看不见的差异。
        if (qualityPreset < 0 && other.qualityPreset < 0) {
            ScalabilityGroup.entries.filter { it.hasGameUi }.forEach { g ->
                val a = g.of(other)
                val b = g.of(this)
                if (a != b) list += ParamDiff(ParamKey.QUALITY_PRESET, a.toFloat(), b.toFloat())
            }
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

    /** 锐化区间：原值 0.1，实测建议 0.4–0.8；超过 0.8 白边明显，不开放。 */
    const val SHARPEN_MIN = 0.00f
    const val SHARPEN_MAX = 0.80f
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

    fun clampSharpen(value: Float): Float =
        ((value.coerceIn(SHARPEN_MIN, SHARPEN_MAX) * 20f).roundToInt() / 20f)

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
