package io.github.rainbowfl4sh.powerampdiscordrp.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.rainbowfl4sh.powerampdiscordrp.ui.logic.NotificationAsk

private const val PERMISSION = Manifest.permission.POST_NOTIFICATIONS

/** Whether this installation asked for the notification permission before (state of the screen, not a setting). */
class NotificationAskState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var asked: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ASKED, value).apply()

    companion object {
        const val PREFS_NAME = "ui"
        private const val KEY_ASKED = "notificationPermissionAsked"
    }
}

private fun isGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun rationale(activity: Activity?): Boolean =
    activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, PERMISSION)

/**
 * "Allow notifications": Android 13+ shows its dialog while it still does, otherwise (denied twice, or allowed but
 * switched off for the app) the app's notification settings open. [onAnswer] runs after the dialog.
 */
@Composable
fun rememberNotificationRequest(onAnswer: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = LocalActivity.current
    // read before the dialog opens; kept if the activity is recreated meanwhile
    var rationaleBefore by rememberSaveable { mutableStateOf(false) }
    var askedBefore by rememberSaveable { mutableStateOf(false) }
    var launchedAt by rememberSaveable { mutableStateOf(0L) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val answerMs = SystemClock.elapsedRealtime() - launchedAt
        if (NotificationAsk.openSettingsAfter(granted, rationaleBefore, rationale(activity), askedBefore, answerMs)) {
            context.startSafely(appNotificationSettingsIntent(context))
        }
        onAnswer()
    }
    return {
        if (NotificationAsk.isRuntimePermission(Build.VERSION.SDK_INT) && !isGranted(context)) {
            val state = NotificationAskState(context)
            askedBefore = state.asked
            rationaleBefore = rationale(activity)
            state.asked = true
            launchedAt = SystemClock.elapsedRealtime()
            launcher.launch(PERMISSION)
        } else {
            context.startSafely(appNotificationSettingsIntent(context))
        }
    }
}

/** Asks for the notification permission once per installation, on the first start (Android never asks by itself). */
@Composable
fun AskForNotificationsOnFirstStart(onAnswer: () -> Unit) {
    val context = LocalContext.current
    val request = rememberNotificationRequest(onAnswer)
    LaunchedEffect(Unit) {
        val asked = NotificationAskState(context).asked
        if (NotificationAsk.askOnStart(Build.VERSION.SDK_INT, isGranted(context), asked)) request()
    }
}
