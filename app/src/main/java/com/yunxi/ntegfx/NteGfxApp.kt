package com.yunxi.ntegfx

import android.app.Application
import com.yunxi.ntegfx.core.log.Logx

class NteGfxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Logx.init(this)
        Logx.i(Logx.Module.UI, "应用启动", "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    }
}
