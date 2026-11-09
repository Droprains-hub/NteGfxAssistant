package com.yunxi.ntegfx

import android.app.Application
import com.yunxi.ntegfx.core.log.Logx
import java.io.File

class NteGfxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Logx.init(this)
        installCrashHandler()
        Logx.i(Logx.Module.UI, "应用启动", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }

    /**
     * 记录未捕获异常，供下次启动补报。
     *
     * 崩溃路径上不做网络请求：进程已被系统回收，任何阻塞都可能让用户
     * 多盯几秒黑屏。这里只把堆栈落到私有目录，由 AppViewModel 在下一次
     * 启动时读取、上报一次 crash 事件，然后删除。
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                Logx.e(Logx.Module.UI, "未捕获异常", error.stackTraceToString())
                File(filesDir, CRASH_FILE).writeText(error.stackTraceToString().take(4000))
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        private const val CRASH_FILE = "crash-pending.txt"

        /** 读取并清除崩溃记录；没有则返回 null。 */
        fun consumeCrashMarker(app: Application): String? {
            val file = File(app.filesDir, CRASH_FILE)
            if (!file.isFile) return null
            val text = runCatching { file.readText() }.getOrNull()
            runCatching { file.delete() }
            return text?.takeIf { it.isNotBlank() }
        }
    }
}
