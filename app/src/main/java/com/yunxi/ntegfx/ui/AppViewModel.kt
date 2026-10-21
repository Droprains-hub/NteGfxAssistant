package com.yunxi.ntegfx.ui

import android.app.Application
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yunxi.ntegfx.BuildConfig
import com.yunxi.ntegfx.core.log.Logx
import com.yunxi.ntegfx.core.net.Announcement
import com.yunxi.ntegfx.core.net.AnnouncementResult
import com.yunxi.ntegfx.core.net.RemoteApi
import com.yunxi.ntegfx.core.net.TelemetryEvent
import com.yunxi.ntegfx.core.net.UpdateCheck
import com.yunxi.ntegfx.core.prefs.AppPrefs
import com.yunxi.ntegfx.core.prefs.AccentColor
import com.yunxi.ntegfx.core.prefs.AppSettings
import com.yunxi.ntegfx.core.prefs.DarkMode
import com.yunxi.ntegfx.core.prefs.Language
import com.yunxi.ntegfx.core.prefs.ThemeStyle
import com.yunxi.ntegfx.core.root.RootDetector
import com.yunxi.ntegfx.core.root.RootStatus
import com.yunxi.ntegfx.data.ApplyProgress
import com.yunxi.ntegfx.data.ApplyStage
import com.yunxi.ntegfx.data.Channel
import com.yunxi.ntegfx.data.ConfigSnapshot
import com.yunxi.ntegfx.data.GfxParams
import com.yunxi.ntegfx.domain.GfxRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)
    private val repo = GfxRepository(application)
    private val api = RemoteApi()

    var settings by mutableStateOf(AppSettings())
        private set
    var root by mutableStateOf(RootStatus())
        private set
    var channel by mutableStateOf(Channel.OFFICIAL)
        private set
    var snapshot by mutableStateOf<ConfigSnapshot?>(null)
        private set
    var baseline by mutableStateOf(GfxParams())
        private set
    var editing by mutableStateOf(GfxParams())
        private set
    var checking by mutableStateOf(true)
        private set
    var applying by mutableStateOf(false)
        private set
    var resetting by mutableStateOf(false)
        private set
    var applyProgress by mutableStateOf(ApplyProgress())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var announcement by mutableStateOf<Announcement?>(null)
        private set
    var announcementFailed by mutableStateOf(false)
        private set
    var announcementLoading by mutableStateOf(false)
        private set
    var updateUpToDate by mutableStateOf(false)
        private set
    var updateState by mutableStateOf<UpdateCheck>(UpdateCheck.NotConfigured)
        private set
    var updateChecking by mutableStateOf(false)
        private set

    val dirtyCount: Int get() = editing.diffFrom(baseline).size

    val canEdit: Boolean
        get() = root.granted && snapshot?.state == com.yunxi.ntegfx.data.ConfigState.APPLIED

    init {
        viewModelScope.launch {
            settings = prefs.settings.first()
            channel = settings.selectedChannel
            api.baseUrl = settings.apiBaseUrl
            refresh().join()
            writeSessionSnapshot()
            loadAnnouncement()
            checkUpdate()
            reportTelemetry(
                event = "app_launch",
                result = if (root.granted) "root_ok" else "no_root",
            )
            prefs.settings.collect { updated ->
                settings = updated
                api.baseUrl = updated.apiBaseUrl
            }
        }
    }

    fun refresh(requestRoot: Boolean = false): kotlinx.coroutines.Job {
        return viewModelScope.launch {
            checking = true
            val status = withContext(Dispatchers.IO) {
                if (requestRoot) RootDetector.requestAccess()
                RootDetector.detect()
            }
            root = status
            val snap = repo.detect(channel, status.granted)
            snapshot = snap
            baseline = snap.params ?: GfxParams()
            editing = baseline
            checking = false
            if (snap.state == com.yunxi.ntegfx.data.ConfigState.UNKNOWN) {
                message = "配置文件格式异常，建议先执行一键恢复"
            }
        }
    }

    fun selectChannel(target: Channel) {
        if (channel == target) return
        channel = target
        viewModelScope.launch { prefs.setChannel(target) }
        refresh()
    }

    fun updateParams(transform: (GfxParams) -> GfxParams) {
        editing = transform(editing)
    }

    fun resetEditing() {
        editing = baseline
    }

    fun applyConfig(onSuccess: () -> Unit = {}) {
        if (applying) return
        viewModelScope.launch {
            applying = true
            applyProgress = ApplyProgress(ApplyStage.VERIFY_ENV, 0, "准备中")
            val result = repo.apply(
                channel = channel,
                prev = baseline,
                target = editing,
                ownerUid = snapshot?.ownerUid,
                onProgress = { applyProgress = it },
            )
            applying = false
            message = result.message
            reportTelemetry(
                event = "config_applied",
                result = if (result.success) "success" else "failure",
            )
            if (result.success) {
                onSuccess()
                kotlinx.coroutines.delay(600)
                applyProgress = ApplyProgress()
                refresh()
            } else {
                applyProgress = ApplyProgress(ApplyStage.FAILED, 100, result.message)
            }
        }
    }

    fun clearApplyProgress() {
        applyProgress = ApplyProgress()
    }

    fun initializeConfig() {
        if (applying) return
        viewModelScope.launch {
            applying = true
            applyProgress = ApplyProgress(ApplyStage.VERIFY_ENV, 0, "准备初始化")
            val result = repo.initialize(channel, editing) { applyProgress = it }
            applying = false
            message = result.message
            reportTelemetry(
                event = "config_initialized",
                result = if (result.success) "success" else "failure",
            )
            if (result.success) {
                delay(700)
                applyProgress = ApplyProgress()
                refresh()
            } else {
                applyProgress = ApplyProgress(ApplyStage.FAILED, 100, result.message)
            }
        }
    }

    fun resetConfig() {
        if (resetting) return
        viewModelScope.launch {
            resetting = true
            val result = repo.reset(channel, snapshot?.ownerUid)
            resetting = false
            message = result.message
            reportTelemetry(
                event = "config_reset",
                result = if (result.success) "success" else "failure",
            )
            refresh()
        }
    }

    /** 匿名上报：常开、失败静默，不阻塞任何本地流程。 */
    private fun reportTelemetry(event: String, result: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val uid = prefs.deviceUid()
                api.report(
                    uid,
                    TelemetryEvent(
                        event = event,
                        channel = channel.name.lowercase(),
                        result = result,
                        rootManager = if (event == "app_launch" && root.granted) {
                            root.manager.displayName
                        } else {
                            null
                        },
                    ),
                )
            }.onFailure { Logx.w(Logx.Module.NET, "遥测上报失败", it.message) }
        }
    }

    fun checkUpdate(manual: Boolean = false) {
        if (updateChecking) return
        viewModelScope.launch {
            updateChecking = true
            val state = api.checkUpdate(BuildConfig.VERSION_CODE, channel.name.lowercase())
            updateState = state
            updateChecking = false
            prefs.setLastUpdateCheck(System.currentTimeMillis())
            if (state is UpdateCheck.UpToDate && manual) updateUpToDate = true
        }
    }

    fun consumeUpToDate() {
        updateUpToDate = false
    }

    /** 拉取公告；进入设置页时主动刷新，避免只依赖启动那一次。 */
    fun loadAnnouncement() {
        if (announcementLoading) return
        viewModelScope.launch {
            announcementLoading = true
            announcementFailed = false
            val uid = runCatching { prefs.deviceUid() }.getOrDefault("")
            when (val result = api.announcement(BuildConfig.VERSION_CODE, channel.name.lowercase(), uid)) {
                is AnnouncementResult.Ready -> {
                    announcement = result.value
                    announcementFailed = false
                }
                AnnouncementResult.Empty -> {
                    announcement = null
                    announcementFailed = false
                }
                AnnouncementResult.Failed -> {
                    announcementFailed = true
                }
            }
            announcementLoading = false
        }
    }

    fun consumeMessage() {
        message = null
    }

    /** 关闭更新弹窗（稍后再说），保持 updateChecking 状态可再次检查。 */
    fun consumeUpdate() {
        updateState = UpdateCheck.NotConfigured
    }

    fun setApiBaseUrl(url: String) = viewModelScope.launch {
        prefs.setApiBaseUrl(url.trim())
        refreshServerState()
    }

    /** 地址变更后立刻重拉公告与版本，让用户马上看到结果。 */
    private suspend fun refreshServerState() {
        settings = prefs.settings.first()
        api.baseUrl = settings.apiBaseUrl
        loadAnnouncement()
        checkUpdate()
    }

    fun setThemeStyle(style: ThemeStyle) = viewModelScope.launch { prefs.setTheme(style) }

    fun setAccentColor(accent: AccentColor) = viewModelScope.launch { prefs.setAccent(accent) }

    fun setDarkMode(mode: DarkMode) = viewModelScope.launch { prefs.setDarkMode(mode) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { prefs.setDynamicColor(enabled) }

    fun setLanguage(language: Language) = viewModelScope.launch { prefs.setLanguage(language) }


    fun setSkipCountdown(skip: Boolean) = viewModelScope.launch { prefs.setSkipCountdown(skip) }

    fun completeOnboarding() = viewModelScope.launch { prefs.setOnboardingDone(true) }

    fun logDirPath(): String = Logx.logDir()?.absolutePath ?: "-"

    fun isInstalled(channel: Channel): Boolean = repo.isInstalled(channel)

    fun currentLogName(): String = Logx.currentFileName()

    fun logTail(): String = Logx.readTail()

    fun deviceSummary(): String = buildString {
        appendLine("版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("设备: ${com.yunxi.ntegfx.core.device.DeviceInfo.manufacturerModel()}")
        appendLine("处理器: ${com.yunxi.ntegfx.core.device.DeviceInfo.processor()}")
        appendLine("架构: ${com.yunxi.ntegfx.core.device.DeviceInfo.abi()}")
        appendLine("内核: ${com.yunxi.ntegfx.core.device.DeviceInfo.kernel()}")
        appendLine("Root: ${if (root.granted) "已授权" else "未授权"} · ${root.manager.displayName} ${root.version.orEmpty()}")
        appendLine("SELinux: ${root.selinux ?: "-"}")
        appendLine("区服: ${channel.pkg}")
        appendLine("目录: ${channel.configDir}")
        appendLine("目录存在: ${snapshot?.dirExists ?: false}")
        appendLine("状态: ${snapshot?.state ?: "-"}")
        appendLine("详情: ${snapshot?.detail ?: "-"}")
        appendLine("日志: ${logDirPath()}")
    }

    private suspend fun writeSessionSnapshot() {
        withContext(Dispatchers.IO) {
            Logx.init(getApplication())
            val entries = buildList {
                add("应用版本" to "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                add("Android 版本与 API" to "${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}")
                add("设备品牌与型号" to "${Build.MANUFACTURER} / ${Build.MODEL}")
                add("Root 管理器与版本" to runCatching { "${root.manager.displayName} ${root.version.orEmpty()}" }.getOrDefault("-"))
                add("SELinux 状态" to (root.selinux ?: "-"))
                add("游戏包名" to channel.pkg)
                add("配置目录" to channel.configDir)
            }
            Logx.snapshot(entries)
        }
    }
}
