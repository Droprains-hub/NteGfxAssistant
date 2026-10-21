package com.yunxi.ntegfx.ui.text

import com.yunxi.ntegfx.data.Channel
import com.yunxi.ntegfx.data.ConfigState
import com.yunxi.ntegfx.data.ParamDiff
import com.yunxi.ntegfx.data.ParamKey

/** 富文本片段：strong = 标红加粗，用于使用说明中的风险提示。 */
data class RichPart(val text: String, val strong: Boolean = false)

data class RichBlock(val parts: List<RichPart>)

/** 规格 7.2：关键文案双语对照。 */
class UiStrings(val english: Boolean) {

    private fun t(zh: String, en: String): String = if (english) en else zh

    // 通用
    val appName get() = t("异环画质助手", "NTE Graphics Assistant")
    val confirm get() = t("确认", "Confirm")
    val cancel get() = t("取消", "Cancel")
    val close get() = t("关闭", "Close")
    val gotIt get() = t("知道了", "Got it")
    val continueText get() = t("继续", "Continue")
    val next get() = t("下一步", "Next")
    val back get() = t("返回", "Back")
    val loading get() = t("正在检测…", "Checking…")
    val notConfigured get() = t("暂未开放", "Not available yet")
    val unknown get() = t("未知", "Unknown")

    // 导航
    val tabHome get() = t("主页", "Home")
    val tabConfig get() = t("配置", "Config")
    val tabSettings get() = t("设置", "Settings")

    // 主页
    val rootGrantedWith: (String) -> String = { manager ->
        if (english) "$manager authorized" else "$manager 已授权"
    }
    val rootNotGranted get() = t("未获得 Root 权限", "Root access not granted")
    val rootInstalledNotGranted: (String) -> String = { manager ->
        if (english) "$manager installed, access not granted" else "已安装 $manager，但未授予本应用权限"
    }
    val goAuthorize get() = t("去授权", "Authorize")
    val tapToRecheck get() = t("点击状态卡可重新检测", "Tap the status card to check again")
    val selinuxLabel get() = t("SELinux", "SELinux")
    val kernelLabel get() = t("内核版本", "Kernel")
    val abiLabel get() = t("架构", "ABI")
    val rootManagerLabel get() = t("Root 管理器", "Root manager")
    val deviceAndGame get() = t("设备与游戏", "Device & game")
    val deviceInfo get() = t("设备信息", "Device")
    val systemInfo get() = t("系统", "System")
    val androidVersion get() = t("安卓版本", "Android")
    val deviceModel get() = t("设备型号", "Model")
    val processorLabel get() = t("处理器", "Processor")
    val memoryLabel get() = t("内存", "Memory")
    val screenLabel get() = t("屏幕", "Display")
    val gameVersion get() = t("游戏版本", "Game version")
    val currentChannel get() = t("当前区服", "Server")
    val configStatus get() = t("安装状态", "Install state")
    val notInstalled get() = t("未安装", "Not installed")
    val installed get() = t("已安装", "Installed")
    val userGuide get() = t("使用文档", "Documentation")
    val guideTitle get() = t("使用说明", "How to use")
    val guideSteps: List<String>
        get() = if (english) listOf(
            "Install and open the game once, so it generates its config folder.",
            "Grant root access when the app asks for it.",
            "Pick the server that matches your installed game package.",
            "Adjust frame rate, render scale, frame generation and brightness.",
            "Tap Confirm changes. The config is written and locked read-only.",
            "If anything goes wrong, use Reset config and launch the game again.",
        ) else listOf(
            "先安装并启动一次游戏，让它生成配置目录。",
            "应用请求时授予 Root 权限。",
            "在配置页选择与已安装包名一致的区服。",
            "调整帧率、渲染分辨率、帧生成与画面亮度。",
            "点击确认修改，配置写入后被锁定为只读。",
            "出现异常时执行一键恢复，再启动游戏重新生成。",
        )

    // 状态
    val stateOriginal get() = t("当前为游戏原始配置", "Game defaults")
    val stateApplied get() = t("配置已生效", "Applied")
    val stateUnknown get() = t("配置格式异常", "Unexpected format")
    val stateNoRoot get() = t("需要 Root 权限", "Root required")
    val emptyHint get() = t("未检测到配置文件，请先启动一次游戏", "No config found. Launch the game once first.")

    // 初始化
    val initTitle get() = t("尚未初始化", "Not initialized")
    val initBody get() = t(
        "首次使用软件或更新游戏后需要重新初始化配置，初始化过后才可正常使用",
        "Re-initialize after first install or a game update. The app works only after initialization.",
    )
    val initAction get() = t("初始化配置", "Initialize")
    val initConfirmTitle get() = t("开始初始化？", "Initialize now?")
    val initConfirmBody get() = t(
        "将写入 GameUserSettings.ini、DeviceProfiles.ini、Engine.ini 三份文件并锁定为只读。已有明文配置不会被覆盖。",
        "GameUserSettings.ini, DeviceProfiles.ini and Engine.ini will be written and locked read-only. Existing plain-text configs will not be overwritten.",
    )
    val initDone get() = t("初始化完成，配置已写入并锁定", "Initialized. Configs written and locked.")
    val rewriteConfig get() = t("重写配置文件", "Rewrite config files")
    val initHintHome get() = t("尚未初始化，前往配置页完成初始化", "Not initialized yet. Go to Config to initialize.")
    val initRequired get() = t("完成初始化后才能调整参数", "Initialize before adjusting parameters")

    // 配置页
    val configTitle get() = t("配置", "Config")
    val serverPicker get() = t("选择区服", "Select server")
    val inUse get() = t("当前使用", "In use")
    val notInstalledCannotSelect get() = t("当前区服未安装，无法选择", "This server is not installed")
    val serverHelp get() = t(
        "如需补充其他区服，请加群提供信息给开发者。",
        "To add another server, join the group and send the details to the developer.",
    )
    val groupPerformance get() = t("性能", "Performance")
    val groupGraphics get() = t("画质", "Graphics")
    val frameRate get() = t("帧率上限", "Frame rate limit")
    val vsync get() = t("垂直同步", "V-Sync")
    val vsyncNote get() = t("垂直同步（部分设备不生效）", "V-Sync (may not work on some devices)")
    val renderResolution get() = t("渲染分辨率", "Render resolution")
    val supersample get() = t("超采样倍率", "Supersampling scale")
    val frameGeneration get() = t("帧生成", "Frame generation")
    val frameGenerationHint get() = t("同时控制 MEMC、硬件帧生成与 FSR 帧生成", "Controls MEMC, hardware frame generation and FSR together")
    val colorMid get() = t("中间调", "Midtones")
    val colorMin get() = t("暗部", "Shadows")
    val colorHigh get() = t("高光", "Highlights")
    val pictureBrightness get() = t("画面亮度", "Picture brightness")
    val toneMapping get() = t("色调映射", "Tone mapping")
    val brightnessHint get() = t("本项不修改游戏内亮度设置", "Does not change the in-game brightness setting")
    val confirmChanges get() = t("确认修改", "Confirm changes")
    val noChanges get() = t("暂无修改", "No changes")
    val restoreDefaults get() = t("一键恢复", "Reset config")
    val custom get() = t("自定义", "Custom")
    val on get() = t("开", "On")
    val off get() = t("关", "Off")
    val native get() = t("原生 1.0×", "Native 1.0×")
    val hd get() = t("高清 2.0×（默认）", "HD 2.0× (default)")
    val supersampleDefault get() = t("超采样 3.5×", "Supersample 3.5×")
    val highScaleWarning get() = t("更高倍率将显著增加 GPU 负载与发热", "Higher scale significantly increases GPU load and heat")
    val renderScaleRange get() = t("范围 1.0–4.0，步进 0.1", "Range 1.0–4.0, step 0.1")

    // 游戏设置
    val groupGameSettings get() = t("游戏设置", "Game settings")
    val qualityPreset get() = t("画质预设", "Quality preset")
    val qualityPresetHelp get() = t(
        "游戏自身的画质预设档位，从低到高共五档：性能、流畅、平衡、极致、原画。切换后会联动材质、植被、视距、后处理等全部子项，等同于在游戏「画面」页选择对应的整体档位。",
        "The game's overall quality preset, five levels from Performance to Original. Switching it drives material, foliage, view distance and post processing together, equivalent to the in-game graphics preset.",
    )
    val antiAliasing get() = t("抗锯齿", "Anti-aliasing")
    val antiAliasingHelp get() = t(
        "画面边缘抗锯齿算法。GSR 为高通 Game Super Resolution，边缘更锐利、GPU 开销略低；TAA 为时间抗锯齿，运动画面更平滑，但快速转向时可能出现轻微拖影。",
        "Edge anti-aliasing algorithm. GSR is Qualcomm Game Super Resolution: sharper edges at slightly lower GPU cost. TAA is temporal anti-aliasing: smoother in motion, with slight ghosting on fast turns.",
    )
    val comingSoon get() = t("敬请期待", "Coming soon")

    fun qualityPresetName(level: Int): String = when (level) {
        0 -> t("性能", "Performance")
        1 -> t("流畅", "Smooth")
        2 -> t("平衡", "Balanced")
        3 -> t("极致", "Extreme")
        else -> t("原画", "Original")
    }

    // 应用确认
    val confirmApplyTitle get() = t("确认应用以下修改？", "Apply these changes?")
    val confirmApplyCountdown get() = t("%d 秒后可确认", "Available in %ds")
    val doNotAskAgain get() = t("不再提示", "Don't ask again")
    val applying get() = t("正在应用…", "Applying…")
    val applySuccess get() = t("配置已生效并锁定", "Applied and locked")
    val applyFailed get() = t("应用失败", "Apply failed")
    val undoChangesTitle get() = t("有 %d 项修改尚未应用，是否放弃？", "%d unapplied changes. Discard them?")
    val unsavedTitle get() = t("尚未应用的修改", "Unapplied changes")
    val discard get() = t("放弃", "Discard")
    val keepEditing get() = t("继续编辑", "Keep editing")

    // 恢复
    val resetTitle get() = t("清空配置目录？", "Clear the config folder?")
    val resetBody get() = t(
        "将清空配置目录内的所有文件，游戏下次启动时会重新生成。此操作不可撤销。",
        "This will delete every file in the config folder. The game will regenerate them on next launch. This cannot be undone.",
    )
    val resetRunning get() = t("正在清空…", "Clearing…")

    // 阶段
    fun stage(stage: com.yunxi.ntegfx.data.ApplyStage): String = when (stage) {
        com.yunxi.ntegfx.data.ApplyStage.VERIFY_ENV -> t("校验环境", "Verifying environment")
        com.yunxi.ntegfx.data.ApplyStage.WRITE_TEMP -> t("写入临时文件", "Writing temp file")
        com.yunxi.ntegfx.data.ApplyStage.ATOMIC_REPLACE -> t("原子替换", "Replacing atomically")
        com.yunxi.ntegfx.data.ApplyStage.LOCK -> t("只读锁定", "Locking read-only")
        com.yunxi.ntegfx.data.ApplyStage.VERIFY_RESULT -> t("校验生效", "Verifying result")
        com.yunxi.ntegfx.data.ApplyStage.DONE -> t("完成", "Done")
        com.yunxi.ntegfx.data.ApplyStage.FAILED -> t("失败", "Failed")
        com.yunxi.ntegfx.data.ApplyStage.IDLE -> t("待机", "Idle")
    }

    // 设置
    val appearance get() = t("外观", "Appearance")
    val themeStyle get() = t("主题风格", "Theme style")
    val themeAurora get() = t("极光", "Aurora")
    val accentColor get() = t("强调色", "Accent color")
    val accentDefault get() = t("默认", "Default")
    val accentBlue get() = t("湛蓝", "Blue")
    val accentGreen get() = t("翠绿", "Green")
    val accentOrange get() = t("暖橙", "Orange")
    val darkMode get() = t("明暗模式", "Appearance mode")
    val followSystem get() = t("跟随系统", "Follow system")
    val light get() = t("浅色", "Light")
    val dark get() = t("深色", "Dark")
    val dynamicColor get() = t("动态取色", "Dynamic color")
    val dynamicColorHint get() = t("仅 MD3 主题在 Android 12 及以上可用", "MD3 only, Android 12+")
    val language get() = t("语言", "Language")
    val simplifiedChinese get() = t("简体中文", "简体中文")
    val englishLabel get() = t("English", "English")
    val announcement get() = t("软件公告", "Announcements")
    val announcementLoading get() = t("正在获取公告…", "Loading announcement…")
    val announcementEmpty get() = t("暂无公告", "No announcements")
    val announcementFailed get() = t("公告获取失败", "Failed to load announcement")
    val announcementRetry get() = t("点击重试", "Tap to retry")
    val upToDateTitle get() = t("已是最新版本", "Up to date")
    val upToDateBody get() = t("当前版本 v%s 已是最新，无需更新。", "You're on v%s, the latest version.")
    val runningLog get() = t("运行日志", "Logs")
    val exportLog get() = t("导出日志", "Export logs")
    val aboutSupport get() = t("关于与支持", "About")
    val versionLabel get() = t("版本号", "Version")
    val sourceCode get() = t("查看源代码", "Source code")
    val licenses get() = t("开放源代码许可", "Open source licenses")
    val checkUpdate get() = t("获取更新", "Check for updates")
    val lastChecked get() = t("最近检查：%s", "Last checked: %s")
    val debugPanel get() = t("调试信息", "Debug info")
    val exportDone get() = t("日志已导出", "Logs exported")
    val updateAvailable get() = t("发现新版本", "Update available")
    val updateGoDownload get() = t("去下载", "Download")
    val updateLater get() = t("稍后再说", "Later")
    val announcementDetail get() = t("公告详情", "Announcement")

    // 开屏
    val obTitle get() = t("使用说明", "Before you start")
    val obAgree get() = t("我已阅读并同意上述内容", "I have read and agree to the above")
    val obContinue get() = t("继续", "Continue")

    val obParagraphs: List<RichBlock>
        get() = if (english) listOf(
            RichBlock(
                listOf(
                    RichPart("Welcome to NTE Graphics Assistant, a root-level graphics config tool for "),
                    RichPart("Neverness to Everness", true),
                    RichPart(" on Android. It replaces manual editing of Unreal Engine config files with a visual interface. "),
                    RichPart("The app is free and open source. To avoid tampered builds, download it only from the author's cloud drive or official channels.", true),
                    RichPart(" Builds from unknown sources may carry security risks and could damage your device or data."),
                ),
            ),
            RichBlock(
                listOf(
                    RichPart("This software is for ", false),
                    RichPart("testing and technical exchange only", true),
                    RichPart(". Device model, system environment and game version differences may cause "),
                    RichPart("device or game anomalies", true),
                    RichPart(". Please understand the risks and "),
                    RichPart("make a backup", true),
                    RichPart(" before continuing. Continuing means you accept these risks."),
                ),
            ),
            RichBlock(
                listOf(
                    RichPart("Do not mention this software on any official social media account of the game. ", true),
                    RichPart("Any consequence caused by using this software is borne by the user.", true),
                ),
            ),
        ) else listOf(
            RichBlock(
                listOf(
                    RichPart("欢迎使用异环画质助手，一款面向《异环》Android 玩家的 "),
                    RichPart("Root 级画质配置工具", true),
                    RichPart("，用可视化界面替代手工编辑 Unreal Engine 配置文件以提升游戏的特定画质属性。"),
                    RichPart("软件开源免费，为防止安装包被恶意篡改，请仅从作者提供的网盘或其他官方发布渠道下载。", true),
                    RichPart("未知来源的安装包可能存在安全风险，并可能损害您的设备或数据。"),
                ),
            ),
            RichBlock(
                listOf(
                    RichPart("本软件"),
                    RichPart("仅供测试与技术交流使用", true),
                    RichPart("。受设备型号、系统环境及游戏版本差异影响，使用过程中可能出现"),
                    RichPart("设备或游戏异常", true),
                    RichPart("。请在充分了解相关风险并"),
                    RichPart("做好备份", true),
                    RichPart("后继续使用；继续使用即表示您已知悉并接受上述风险。"),
                ),
            ),
            RichBlock(
                listOf(
                    RichPart("使用此软件", false),
                    RichPart("请勿以任何形式在异环官方社媒账号提及", true),
                    RichPart("，因使用此软件造成的后果"),
                    RichPart("由您本人承担", true),
                    RichPart("。"),
                ),
            ),
        )

    // 验证
    val verifyTitle get() = t("安全验证", "Verification")
    val verifyHint get() = t("请计算下列算式，用于确认操作由真人完成", "Solve the expression below to continue")
    val verifyWrong get() = t("答案不正确，请重试", "Incorrect answer, try again")
    val verifyRefresh get() = t("换一题", "New question")
    val verifyPlaceholder get() = t("输入答案", "Answer")

    // 动态
    fun channelName(channel: Channel): String = if (english) channel.fullNameEn else channel.fullNameZh

    fun channelState(state: ConfigState, hasRoot: Boolean, isInstalled: Boolean): String = when {
        !isInstalled -> notInstalled
        !hasRoot -> stateNoRoot
        state == ConfigState.APPLIED -> inUse
        else -> installed
    }

    fun paramLabel(key: ParamKey): String = when (key) {
        ParamKey.FRAME_RATE -> frameRate
        ParamKey.VSYNC -> vsync
        ParamKey.RENDER_SCALE -> renderResolution
        ParamKey.FRAME_GENERATION -> frameGeneration
        ParamKey.COLOR_MIN -> colorMin
        ParamKey.COLOR_MID -> colorMid
        ParamKey.COLOR_MAX -> colorHigh
        ParamKey.QUALITY_PRESET -> qualityPreset
        ParamKey.AA_METHOD -> antiAliasing
    }

    fun paramHelp(key: ParamKey): String = when (key) {
        ParamKey.FRAME_RATE -> t(
            "限制游戏渲染线程每秒输出的最大帧数。上限越高，高负载场景下越不容易掉帧，但 GPU 持续满载会带来更高的功耗与发热；超过屏幕刷新率的部分不会产生额外可见收益，建议与设备刷新率对齐。",
            "Caps the maximum frames the render thread outputs per second. Higher limits reduce stutter in heavy scenes at the cost of power and heat; values above the display refresh rate bring no visible benefit.",
        )
        ParamKey.VSYNC -> t(
            "将渲染帧的提交时机与屏幕刷新周期对齐，消除画面上下撕裂。开启后帧输出受刷新率节流，负载突增时可能出现台阶式掉帧；部分设备或游戏版本未实现该开关，开启后无实际效果。",
            "Aligns frame submission with the display refresh cycle to remove tearing. Frame output is throttled to the refresh rate; some devices or game builds ignore this switch.",
        )
        ParamKey.RENDER_SCALE -> t(
            "设置游戏内部渲染分辨率相对屏幕物理分辨率的比例，可调 0.35×–3.5×。低于 1.0× 为降采样，能显著提升帧率但画面会变软；1.0× 为原生分辨率；高于 1.0× 为超采样，边缘与远景更锐利，GPU 光栅化负载近似按面积增长。",
            "Scales the internal render resolution against the physical display, range 0.35×–3.5×. Below 1.0× renders fewer pixels for higher frame rate but looks softer; 1.0× is native; above 1.0× supersamples for sharper edges at higher GPU cost.",
        )
        ParamKey.FRAME_GENERATION -> t(
            "同时接管 MEMC 运动补偿、硬件帧生成与 FSR 帧生成三项插帧技术。开启后可在相同渲染帧率下提升视觉流畅度，代价是增加约一帧的输入延迟，快速转向时可能出现轻微残影。",
            "Drives MEMC, hardware frame generation and FSR together. Raises perceived smoothness at the same render rate, at the cost of roughly one frame of input latency and slight ghosting on fast turns.",
        )
        ParamKey.COLOR_MIN -> t(
            "调整色调映射曲线的暗部锚点，决定画面最暗区域的黑位。保持 0 可获得最深黑场与最强对比；提高会抬升暗部亮度、显出更多暗处细节，代价是整体发灰、通透感下降。",
            "Moves the black anchor of the tone mapping curve. Keep 0 for the deepest blacks and highest contrast; raising it lifts shadows and reveals more dark detail at the cost of a washed-out image.",
        )
        ParamKey.COLOR_MID -> t(
            "调整色调映射曲线中间段的锚点，直接决定画面整体明度与中间调对比。数值偏高会提亮暗部、画面偏灰；偏低则对比更强、暗部细节减少。",
            "Moves the mid anchor of the tone mapping curve, controlling overall brightness and midtone contrast. Higher values lift shadows and flatten the image; lower values deepen contrast.",
        )
        ParamKey.COLOR_MAX -> t(
            "调整色调映射曲线的高光上限，控制最亮区域的压缩程度。数值越高，天空与灯光等高光区域保留的细节越多；过高会导致整体泛白、眩光扩散。",
            "Sets the highlight ceiling of the tone mapping curve. Higher values preserve more detail in skies and lights; too high washes the image out and spreads glare.",
        )
        ParamKey.QUALITY_PRESET -> qualityPresetHelp
        ParamKey.AA_METHOD -> antiAliasingHelp
    }

    fun formatParam(key: ParamKey, value: Float): String = when (key) {
        ParamKey.FRAME_RATE -> "${value.toInt()}"
        ParamKey.VSYNC, ParamKey.FRAME_GENERATION -> if (value >= 0.5f) on else off
        ParamKey.RENDER_SCALE -> com.yunxi.ntegfx.data.ParamSpec.scaleLabel(value)
        ParamKey.COLOR_MIN, ParamKey.COLOR_MID, ParamKey.COLOR_MAX -> String.format(java.util.Locale.US, "%.2f", value)
        ParamKey.QUALITY_PRESET -> qualityPresetName(value.toInt())
        ParamKey.AA_METHOD -> if (value < 0.5f) "GSR" else "TAA"
    }

    fun diffLine(diff: ParamDiff): String =
        "${paramLabel(diff.key)}  ${formatParam(diff.key, diff.from)} → ${formatParam(diff.key, diff.to)}"
}
