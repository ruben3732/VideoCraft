package com.videocraft.editor

import android.app.Application
import com.videocraft.editor.data.api.ApiClient

class App : Application() {

    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        ApiClient.init(this)
    }
}
