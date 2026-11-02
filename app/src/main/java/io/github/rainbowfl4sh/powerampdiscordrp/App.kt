package io.github.rainbowfl4sh.powerampdiscordrp

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.rainbowfl4sh.powerampdiscordrp.ui.components.RemoteImages

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        // settings, engine and saved flags are needed right after this by whatever started the process
        Graph.warmUp()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // The presence service keeps the process alive for hours after the screens are gone: their pictures are not
        // kept that long (loaded again when the UI comes back)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) RemoteImages.clear()
    }
}
