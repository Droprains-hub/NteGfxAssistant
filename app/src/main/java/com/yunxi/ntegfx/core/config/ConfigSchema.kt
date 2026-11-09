package com.yunxi.ntegfx.core.config

import com.yunxi.ntegfx.data.ConfigState

/** 规格 3.4：Base64 密文特征判定，完全不依赖哈希库与云端。 */
object ConfigClassifier {

    private val sectionRegex = Regex("""(?m)^[ \t]*\[[^\]]+]""")
    private val entryRegex = Regex("""(?m)^[ \t]*[A-Za-z0-9_.|]+\s*=""")

    fun classify(text: String?): ConfigState {
        if (text.isNullOrBlank()) return ConfigState.NOT_INITIALIZED

        val hasSection = sectionRegex.containsMatchIn(text)
        val hasEntry = entryRegex.containsMatchIn(text)
        if (hasSection && hasEntry) return ConfigState.APPLIED

        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return ConfigState.NOT_INITIALIZED

        val base64Count = compact.count { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val ratio = base64Count.toDouble() / compact.length
        val hasMarker = text.contains('+') || text.contains('/') ||
            Regex("""=[ \t]*(\r?\n|$)""").containsMatchIn(text)

        return if (!hasSection && ratio >= 0.95 && hasMarker) ConfigState.ORIGINAL else ConfigState.UNKNOWN
    }

    fun isPlainText(text: String?): Boolean = classify(text) == ConfigState.APPLIED

    /** 三份文件是否都已就位且为明文——这是"已初始化"的唯一判据。 */
    fun isInitialized(gameUser: String?, device: String?, engine: String?): Boolean =
        isPlainText(gameUser) && isPlainText(device) && isPlainText(engine)
}

/**
 * 三份配置文件的真实键位，依据用户提供的 Android.zip 校正。
 *
 * GameUserSettings 的主段是游戏私有的 HTGame 类；Engine 走 RendererSettings；
 * DeviceProfiles 是 156 段 GPU 机型表，同一组 CVar 在每个段重复出现。
 */
object ConfigSchema {
    const val SECTION_GAME_USER = "/Script/HTGame.HTGameUserSettings"
    const val SECTION_ENGINE = "/Script/Engine.RendererSettings"
    const val SECTION_SCALABILITY = "ScalabilityGroups"
    const val SECTION_SETTING = "Setting"

    /** 游戏自读的锐化强度，位于 GameUserSettings.ini 的 [Setting] 段；原值 0.1。 */
    const val KEY_SHARPEN = "Graphics_Sharpen"

    const val KEY_FRAME_RATE = "FrameRateLimit"
    const val KEY_VSYNC = "bUseVSync"
    const val KEY_DYNAMIC_RES = "bUseDynamicResolution"
    const val KEY_SCREEN_PERCENT = "ScreenPercentage"
    const val KEY_RES_X = "ResolutionSizeX"
    const val KEY_RES_Y = "ResolutionSizeY"
    const val KEY_QUALITY = "GraphicQualityLevel"

    /**
     * NPC / 车流密度。驱动 MassCrowd 与 MassTraffic 的生成比例
     * （对应 `HTMassCrowdAudienceSpawnRatioByQuality`），游戏设置里的"交通系统"档位即此键。
     * 它独立于画质预设，不受 `GraphicQualityLevel` 覆盖。
     */
    const val KEY_NPC_QUALITY = "NPCQuality"
    const val KEY_BRIGHTNESS = "BrightnessSetting"
    const val KEY_AA_METHOD = "MobileAAMethod"

    const val KEY_AFME = "MobileAFME"
    const val KEY_HWFG = "bMobileHWFG"
    const val KEY_MFRC = "bMobileMFRC"
    const val KEY_FSR_FG = "bFSRFrameGeneration"

    const val KEY_CONTENT_SCALE = "r.MobileContentScaleFactor"
    const val KEY_SECONDARY_PERCENT = "r.SecondaryScreenPercentage.GameViewport"
    const val KEY_R_SCREEN_PERCENT = "r.ScreenPercentage"

    const val KEY_COLOR_MIN = "r.Color.Min"
    const val KEY_COLOR_MID = "r.Color.Mid"
    const val KEY_COLOR_MAX = "r.Color.Max"

    /** 防伪标记键：非游戏 CVar，仅用于辨认"本工具生成"的文件。 */
    const val KEY_MARKER = "Slate.YunxiIndices"

    // ------------------------------------------------ ANF（AIFF 插件）

    /** ANF 超分开关，属于 HTGameUserSettings 段。 */
    const val KEY_ANF_SR = "bANFSR"

    /** ANF 插帧开关，属于 HTGameUserSettings 段。 */
    const val KEY_ANF_FRC = "bANFFRC"

    /** ANF 的总闸门 CVar 所在段。游戏自身从不注册它，必须由外部 ini 造出来。 */
    const val SECTION_CONSOLE_VARS = "ConsoleVariables"

    /** 缺失时 SupportSR() 恒返回 0，UI 选项不会出现。 */
    const val KEY_AIFF_ALLOWED = "r.AIFF.Runtime.Allowed"

    /** 缺失时 ApplyANFSR() 会提前返回并把 bANFSR 写回 False。 */
    const val KEY_AIFF_ENABLED = "r.AIFF.Enabled"

    /** 全局渲染分辨率百分比。写进 [SECTION_CONSOLE_VARS] 作为设备档案之外的兜底。 */
    const val KEY_R_SCREEN_PERCENT_GLOBAL = "r.ScreenPercentage"

    /** 同上，作用于 GameViewport 的次级百分比。 */
    const val KEY_R_SECONDARY_PERCENT_GLOBAL = "r.SecondaryScreenPercentage.GameViewport"

    const val FILE_GAME_USER = "GameUserSettings.ini"
    const val FILE_DEVICE = "DeviceProfiles.ini"
    const val FILE_ENGINE = "Engine.ini"

    /**
     * 只读锁定的目标权限。
     *
     * 必须是 444 而非 400：文件属主被改为 root 后，游戏进程以自身 UID 运行属于 other，
     * 400 会让游戏连读取都失败（配置不生效）。444 允许所有人读、仅 root 可写，
     * 既保证游戏能加载配置，又阻止其退出时回写。
     */
    const val LOCK_MODE = 444

    /** DeviceProfiles 中需要全量同步的三组 CVar。 */
    val DEVICE_CVARS = listOf(KEY_CONTENT_SCALE, KEY_SECONDARY_PERCENT, KEY_R_SCREEN_PERCENT)

    /**
     * 本作 libUnreal.so 中查不到的 CVar。
     * 原模板把它们写进了 DeviceProfiles 的每个机型段，实际不会生效，
     * 应用时会从已有文件里剔除，避免继续发送无效配置。
     */
    val DEVICE_DEAD_CVARS = listOf(
        "r.Shadow.PerObjectShadowMapResolution",
        "r.TextureQuality",
        "r.MaxTextureResolution",
        "r.Mobile.EnableHighQualityMaterials",
        "sg.Mobile.EnableHighQualityMaterials",
        "r.imp.SSMbScaleLod0",
        "r.imp.SSMbScaleLod1",
    )
}
