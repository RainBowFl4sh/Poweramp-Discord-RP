package io.github.rainbowfl4sh.powerampdiscordrp.poweramp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives Poweramp's broadcasts. Declared in the manifest for the *_EXPLICIT actions (Poweramp sends them to every app
 * with such a manifest receiver, also when this app is not running); the presence service registers another instance
 * for the sticky broadcasts and TPOS_SYNC while it runs.
 */
class PowerampReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // The first delivery after registerReceiver() is the stored sticky value, possibly older than what we know
        PowerampBridge.onBroadcast(context, intent, replay = isInitialStickyBroadcast)
    }
}
