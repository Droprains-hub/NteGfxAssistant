package com.yunxi.ntegfx.core.net

import android.net.Uri
import android.os.Build
import com.yunxi.ntegfx.BuildConfig
import com.yunxi.ntegfx.core.log.Logx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val level: String = "info",
    val forced: Boolean = false,
    val buttonText: String? = null,
    val url: String? = null,
)

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val url: String,
    val forced: Boolean = false,
    val md5: String? = null,
)

sealed interface AnnouncementResult {
    data object Empty : AnnouncementResult
    data object Failed : AnnouncementResult
    data class Ready(val value: Announcement) : AnnouncementResult
}

sealed interface UpdateCheck {
    data object NotConfigured : UpdateCheck
    data object UpToDate : UpdateCheck
    data object Failed : UpdateCheck
    data class Available(val info: UpdateInfo) : UpdateCheck
}

/** 一条匿名事件。设备字段由 [RemoteApi] 从 Build 补齐，调用方只关心业务维度。 */
data class TelemetryEvent(
    val event: String,
    val channel: String,
    val result: String? = null,
    val rootManager: String? = null,
)

/**
 * 规格 9.1：四个接口。服务端未配置时全部静默降级，不阻塞任何本地功能。
 */
class RemoteApi {

    @Volatile
    var baseUrl: String = ""

    /**
     * 拉取当前渠道的公告。必须带 [channel]，否则服务端按 `all` 处理，
     * 只匹配 channel 为 all 的记录，渠道专属公告会全部漏掉。
     *
     * 返回值区分「没有公告」与「请求失败」，避免网络异常时误显示成"暂无公告"。
     */
    suspend fun announcement(
        versionCode: Int,
        channel: String,
        uid: String = "",
    ): AnnouncementResult = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext AnnouncementResult.Failed
        val channelParam = if (channel.isBlank()) "" else "&channel=${Uri.encode(channel)}"
        val uidParam = if (uid.isBlank()) "" else "&uid=${Uri.encode(uid)}"
        val json = get("$base/api/v1/announcement?versionCode=$versionCode$channelParam$uidParam")
            ?: return@withContext AnnouncementResult.Failed
        runCatching {
            val obj = JSONObject(json)
            if (obj.optBoolean("empty", false)) return@runCatching AnnouncementResult.Empty
            AnnouncementResult.Ready(
                Announcement(
                    id = obj.optString("id", "0"),
                    title = obj.optString("title"),
                    body = obj.optString("body"),
                    level = obj.optString("level", "info"),
                    forced = obj.optBoolean("force", false),
                    buttonText = obj.optString("button").takeIf { it.isNotBlank() },
                    url = obj.optString("url").takeIf { it.isNotBlank() },
                ),
            )
        }.onFailure { Logx.w(Logx.Module.NET, "解析公告失败", it.message) }
            .getOrDefault(AnnouncementResult.Failed)
    }

    suspend fun checkUpdate(versionCode: Int, channel: String): UpdateCheck = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return@withContext UpdateCheck.NotConfigured
        val channelParam = if (channel.isBlank()) "" else "&channel=${Uri.encode(channel)}"
        val json = get("$base/api/v1/version/latest?versionCode=$versionCode$channelParam")
            ?: return@withContext UpdateCheck.Failed
        runCatching {
            val obj = JSONObject(json)
            val latest = obj.optInt("versionCode", 0)
            if (latest <= versionCode) {
                UpdateCheck.UpToDate
            } else {
                UpdateCheck.Available(
                    UpdateInfo(
                        versionCode = latest,
                        versionName = obj.optString("versionName"),
                        changelog = obj.optString("changelog"),
                        url = obj.optString("url"),
                        forced = obj.optBoolean("force", false),
                        md5 = obj.optString("md5").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }.onFailure { Logx.w(Logx.Module.NET, "解析版本信息失败", it.message) }
            .getOrDefault(UpdateCheck.Failed)
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NteGfxAssistant")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                Logx.w(Logx.Module.NET, "请求失败", url, "HTTP $code")
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            Logx.w(Logx.Module.NET, "网络异常", url, t.message ?: t.javaClass.simpleName)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 上报一条匿名事件。服务端按 (uid, event, ts, 版本, 结果, 耗时) 去重，
     * 失败时静默返回 false，不重试、不影响本地功能。
     */
    suspend fun report(uid: String, event: TelemetryEvent): Boolean = withContext(Dispatchers.IO) {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty() || uid.isBlank()) return@withContext false
        val ts = System.currentTimeMillis() / 1000
        val payload = runCatching {
            JSONObject().apply {
                put("uid", uid)
                put(
                    "events",
                    JSONArray().apply {
                        put(
                            JSONObject().apply {
                                put("uid", uid)
                                put("event", event.event)
                                put("app_version", BuildConfig.VERSION_NAME)
                                put("android_api", Build.VERSION.SDK_INT)
                                put("model", Build.MODEL)
                                event.rootManager?.takeIf { it.isNotBlank() }?.let { put("root_manager", it) }
                                put("channel", event.channel)
                                event.result?.let { put("result", it) }
                                put("ts", ts)
                            },
                        )
                    },
                )
            }.toString()
        }.getOrNull() ?: return@withContext false

        post("$base/api/v1/telemetry/event", payload) != null
    }

    private fun post(url: String, body: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8_000
                readTimeout = 8_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "NteGfxAssistant")
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                Logx.w(Logx.Module.NET, "上报失败", "HTTP $code")
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (t: Throwable) {
            Logx.w(Logx.Module.NET, "上报异常", t.message ?: t.javaClass.simpleName)
            null
        } finally {
            connection?.disconnect()
        }
    }
}
