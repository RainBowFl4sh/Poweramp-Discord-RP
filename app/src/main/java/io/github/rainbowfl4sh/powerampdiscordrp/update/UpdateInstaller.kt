package io.github.rainbowfl4sh.powerampdiscordrp.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.core.content.pm.PackageInfoCompat
import io.github.rainbowfl4sh.powerampdiscordrp.Graph
import io.github.rainbowfl4sh.powerampdiscordrp.poweramp.PowerampBridge
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Hands a downloaded APK to Android's PackageInstaller (no browser, no file manager). */
object UpdateInstaller {
    internal const val ACTION_STATUS = "io.github.rainbowfl4sh.powerampdiscordrp.action.UPDATE_STATUS"
    internal const val EXTRA_VERSION = "version"

    /**
     * Null if [apk] is a newer version of this very app, signed with the same key; otherwise why not. Android would
     * refuse another key anyway, but only after the user confirmed - and with a message nobody understands.
     */
    fun verify(context: Context, apk: File): String? {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = try {
            pm.getPackageArchiveInfo(apk.path, flags)
        } catch (e: RuntimeException) {
            null
        } ?: return "not an APK"
        if (archive.packageName != context.packageName) return "another app (${archive.packageName})"
        val own = try {
            pm.getPackageInfo(context.packageName, flags)
        } catch (e: PackageManager.NameNotFoundException) {
            return "own package not found"
        }
        if (PackageInfoCompat.getLongVersionCode(archive) <= PackageInfoCompat.getLongVersionCode(own)) {
            return "not newer (version code ${PackageInfoCompat.getLongVersionCode(archive)})"
        }
        val theirs = signers(archive)
        if (theirs.isEmpty() || theirs != signers(own)) return "signed with another key"
        return null
    }

    /** SHA-256 of each signing certificate. */
    private fun signers(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val s = info.signingInfo ?: return emptySet()
            if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return emptySet()
        val sha = MessageDigest.getInstance("SHA-256")
        return certs.mapNotNull { it?.toByteArray() }.map { bytes -> sha.digest(bytes).joinToString("") { "%02x".format(it) } }.toSet()
    }

    /**
     * Writes [apk] into an installer session and commits it; the outcome arrives at [UpdateStatusReceiver]. From
     * Android 12 on no confirmation is asked once this app installed itself before (Android decides). Null = committed.
     */
    fun commit(context: Context, apk: File, version: String): String? {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        var sessionId = -1
        return try {
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = Intent(context, UpdateStatusReceiver::class.java)
                    .setAction(ACTION_STATUS)
                    .putExtra(EXTRA_VERSION, version)
                // mutable: the installer adds the outcome to it
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(context, sessionId, status, flags).intentSender)
            }
            null
        } catch (e: IOException) {
            abandon(installer, sessionId)
            e.toString()
        } catch (e: RuntimeException) {   // SecurityException, IllegalStateException
            abandon(installer, sessionId)
            e.toString()
        }
    }

    private fun abandon(installer: PackageInstaller, sessionId: Int) {
        if (sessionId < 0) return
        try {
            installer.abandonSession(sessionId)
        } catch (_: RuntimeException) {
        }
    }
}

/** The installer session's outcome. */
class UpdateStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        val version = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION).orEmpty()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                AppUpdater.onNeedsConfirmation(context, version, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
            PackageInstaller.STATUS_SUCCESS -> AppUpdater.onInstalled(version)
            else -> AppUpdater.onInstallFailed(
                context, version,
                aborted = status == PackageInstaller.STATUS_FAILURE_ABORTED,
                message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $status",
            )
        }
    }
}

/**
 * The app was replaced by a new version (from here or elsewhere): the old process is gone, so a presence that was
 * shown comes back at once instead of with the next track.
 */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        try {
            AppUpdater.onUpdated(context)
            PowerampBridge.syncReceiverComponent(context)
            PowerampBridge.sync(context)
        } catch (e: RuntimeException) {
            Graph.log.log("After the update: $e")
        }
    }
}
