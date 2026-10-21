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

    const val KEY_FRAME_RATE = "FrameRateLimit"
    const val KEY_VSYNC = "bUseVSync"
    const val KEY_DYNAMIC_RES = "bUseDynamicResolution"
    const val KEY_SCREEN_PERCENT = "ScreenPercentage"
    const val KEY_RES_X = "ResolutionSizeX"
    const val KEY_RES_Y = "ResolutionSizeY"
    const val KEY_QUALITY = "GraphicQualityLevel"
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
}
