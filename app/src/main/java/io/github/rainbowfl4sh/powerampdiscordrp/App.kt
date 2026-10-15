package io.github.rainbowfl4sh.powerampdiscordrp

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // settings, engine and saved flags are needed right after this by whatever started the process
        Graph.warmUp()
    }
}
