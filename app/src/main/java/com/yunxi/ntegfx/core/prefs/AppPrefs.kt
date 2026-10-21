package com.yunxi.ntegfx.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yunxi.ntegfx.data.Channel
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeStyle { MD3, AURORA }

/** 强调色：DEFAULT 表示跟随主题自身的品牌色（极光紫）。 */
enum class AccentColor { DEFAULT, BLUE, GREEN, PURPLE, ORANGE }

enum class DarkMode { SYSTEM, LIGHT, DARK }

enum class Language { SYSTEM, ZH, EN }

/**
 * 线上服务端地址。本仓库不内置服务端信息；
 * 自行部署后可在此填写默认地址，或在应用内调试面板覆盖。
 * 留空时公告 / 更新检查 / 匿名上报静默降级，不影响任何本地功能。
 */
const val DEFAULT_API_BASE_URL = ""

data class AppSettings(
    val themeStyle: ThemeStyle = ThemeStyle.AURORA,
    val accentColor: AccentColor = AccentColor.DEFAULT,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val language: Language = Language.SYSTEM,
    val onboardingDone: Boolean = false,
    val skipCountdown: Boolean = false,
    val lastUpdateCheck: Long = 0L,
    val selectedChannel: Channel = Channel.OFFICIAL,
    val apiBaseUrl: String = DEFAULT_API_BASE_URL,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "nte_gfx_settings")

class AppPrefs(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_style")
        val ACCENT = stringPreferencesKey("accent_color")
        val DARK = stringPreferencesKey("dark_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val SKIP_COUNTDOWN = booleanPreferencesKey("skip_countdown")
        val LAST_UPDATE = longPreferencesKey("last_update_check")
        val CHANNEL = stringPreferencesKey("selected_channel")
        val API = stringPreferencesKey("api_base_url")
        val DEVICE_UID = stringPreferencesKey("device_uid")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            themeStyle = runCatching { ThemeStyle.valueOf(prefs[Keys.THEME] ?: "") }.getOrDefault(ThemeStyle.AURORA),
            accentColor = runCatching { AccentColor.valueOf(prefs[Keys.ACCENT] ?: "") }.getOrDefault(AccentColor.DEFAULT),
            darkMode = runCatching { DarkMode.valueOf(prefs[Keys.DARK] ?: "") }.getOrDefault(DarkMode.SYSTEM),
            dynamicColor = prefs[Keys.DYNAMIC] ?: true,
            language = runCatching { Language.valueOf(prefs[Keys.LANGUAGE] ?: "") }.getOrDefault(Language.SYSTEM),
            onboardingDone = prefs[Keys.ONBOARDING] ?: false,
            skipCountdown = prefs[Keys.SKIP_COUNTDOWN] ?: false,
            lastUpdateCheck = prefs[Keys.LAST_UPDATE] ?: 0L,
            selectedChannel = runCatching { Channel.valueOf(prefs[Keys.CHANNEL] ?: "") }.getOrDefault(Channel.OFFICIAL),
            apiBaseUrl = prefs[Keys.API]?.takeIf { it.isNotBlank() } ?: DEFAULT_API_BASE_URL,
        )
    }

    suspend fun setTheme(style: ThemeStyle) = edit { it[Keys.THEME] = style.name }

    suspend fun setAccent(accent: AccentColor) = edit { it[Keys.ACCENT] = accent.name }

    suspend fun setDarkMode(mode: DarkMode) = edit { it[Keys.DARK] = mode.name }

    suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.DYNAMIC] = enabled }

    suspend fun setLanguage(language: Language) = edit { it[Keys.LANGUAGE] = language.name }

    suspend fun setOnboardingDone(done: Boolean) = edit { it[Keys.ONBOARDING] = done }


    suspend fun setSkipCountdown(skip: Boolean) = edit { it[Keys.SKIP_COUNTDOWN] = skip }

    suspend fun setLastUpdateCheck(time: Long) = edit { it[Keys.LAST_UPDATE] = time }

    suspend fun setChannel(channel: Channel) = edit { it[Keys.CHANNEL] = channel.name }

    suspend fun setApiBaseUrl(url: String) = edit { it[Keys.API] = url }

    /** 稳定设备标识：首次生成后持久化，用于公告送达回执与匿名遥测去重。 */
    /**
     * 设备标识。
     *
     * 用 ANDROID_ID 派生而非随机 UUID：随机值存在 DataStore 里，卸载即丢失，
     * 导致同一台设备在后台被算成两台。ANDROID_ID 在 Android 8+ 按
     * 「设备 + 应用签名 + 用户」生成，卸载重装、覆盖升级都不变。
     *
     * 不上报原始值，只上报 SHA-256 前 32 位十六进制，并加固定前缀做域隔离。
     * 取不到时退回随机 UUID（仅作兜底，这类设备的活跃统计仍会断层）。
     */
    @Volatile
    private var cachedUid: String? = null

    suspend fun deviceUid(): String {
        cachedUid?.let { return it }
        val result = resolveUid()
        cachedUid = result
        return result
    }

    private suspend fun resolveUid(): String {
        val androidId = runCatching {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID,
            )
        }.getOrNull()
        // 9774d56d682e549c 是旧版系统的已知错误常量，不能当标识用
        if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") {
            return digestUid("ntegfx:$androidId")
        }
        context.dataStore.data.first()[Keys.DEVICE_UID]?.let { if (it.isNotBlank()) return it }
        val fresh = UUID.randomUUID().toString()
        edit { it[Keys.DEVICE_UID] = fresh }
        return fresh
    }

    private fun digestUid(raw: String): String {
        val bytes = java.security.MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
