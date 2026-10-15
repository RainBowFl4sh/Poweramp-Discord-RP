package io.github.rainbowfl4sh.powerampdiscordrp.core.assets

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Name and art assets of a Discord application. [images]: asset key ("play") -> picture URL on Discord's CDN. */
data class AppInfo(
    val name: String = "",
    val images: Map<String, String> = emptyMap(),
)

/** What the presence engine and the live preview need (faked in tests). */
interface AppInfoSource {
    /** Cached info, never touches the network; null = not loaded (yet). */
    fun peek(appId: String): AppInfo?

    /** Loads and caches (blocking - call from a background thread); null if it could not be loaded. */
    fun fetch(appId: String): AppInfo?

    companion object {
        val NONE = object : AppInfoSource {
            override fun peek(appId: String): AppInfo? = null
            override fun fetch(appId: String): AppInfo? = null
        }
    }
}

/**
 * Looks up a Discord application's name and art assets through Discord's public API (no login needed):
 *   GET {api}/oauth2/applications/{id}/assets  -> [{"id":"1559...","type":1,"name":"play"}, ...]
 *   GET {api}/applications/{id}/rpc            -> {"name":"Poweramp", ...}
 *
 * Why: the Discord Android app does not turn asset keys ("play") sent through its Social SDK RPC service into pictures -
 * the small image stays an empty circle - but it loads image URLs (the covers work). So the keys are sent as the URL of
 * the asset's picture, {cdn}/app-assets/{appId}/{assetId}.png. The live preview shows the same pictures and name.
 *
 * Info is kept for [maxAgeMs] and then fetched again (the old one stays in use meanwhile); after a failed lookup the
 * next one is made [retryMs] later at the earliest.
 */
class DiscordAppInfo(
    private val http: OkHttpClient,
    private val clock: Clock = Clock.System,
    private val log: Logger = Logger.NONE,
    private val apiBase: String = "https://discord.com/api/v10",
    private val cdnBase: String = "https://cdn.discordapp.com",
    private val maxAgeMs: Long = 6 * 60 * 60 * 1000L,
    private val retryMs: Long = 10 * 60 * 1000L,
) : AppInfoSource {
    private class Entry(val info: AppInfo?, val loadedAtMs: Long, val failedAtMs: Long?)

    private val entries = HashMap<String, Entry>()

    override fun peek(appId: String): AppInfo? = synchronized(entries) { entries[appId]?.info }

    override fun fetch(appId: String): AppInfo? {
        if (!isId(appId)) return null
        val now = clock.nowMs()
        synchronized(entries) {
            val e = entries[appId]
            if (e?.info != null && now - e.loadedAtMs < maxAgeMs) return e.info
            val failed = e?.failedAtMs
            if (failed != null && now - failed < retryMs) return e.info   // a recent failure: no new request yet
        }
        val images = assets(appId)
        val info = images?.let { AppInfo(name(appId).orEmpty(), it) }
        synchronized(entries) {
            val old = entries[appId]
            entries[appId] = if (info != null) {
                Entry(info, clock.nowMs(), null)
            } else {
                Entry(old?.info, old?.loadedAtMs ?: 0, clock.nowMs())
            }
        }
        if (info != null) {
            log.log("Discord application $appId: \"${info.name}\", art assets " + info.images.keys.sorted().joinToString(", ").ifEmpty { "none" })
        }
        return info ?: peek(appId)
    }

    /** Asset key -> picture URL; null if the list could not be loaded. */
    private fun assets(appId: String): Map<String, String>? {
        val body = get("$apiBase/oauth2/applications/$appId/assets") ?: return null
        val list = parse(body) as? JsonArray ?: run {
            log.log("Discord application $appId: unexpected answer for its art assets")
            return null
        }
        val images = LinkedHashMap<String, String>()
        for (item in list) {
            val o = item as? JsonObject ?: continue
            val key = o.string("name") ?: continue
            val id = o.string("id")?.takeIf { isId(it) } ?: continue
            images[key] = "$cdnBase/app-assets/$appId/$id.png"
        }
        return images
    }

    /** The application's name as Discord shows it ("Listening to <name>"); null if unknown. */
    private fun name(appId: String): String? =
        get("$apiBase/applications/$appId/rpc")?.let { (parse(it) as? JsonObject)?.string("name") }

    private fun get(url: String): String? = try {
        val call = http.newCall(Request.Builder().url(url).header("Accept", "application/json").build())
        call.timeout().timeout(15, TimeUnit.SECONDS)
        call.execute().use { r ->
            if (r.isSuccessful) {
                r.body.source().let { src ->
                    src.request(MAX_BODY)   // an asset list is a few hundred bytes; never read an endless body
                    src.buffer.readUtf8(minOf(src.buffer.size, MAX_BODY))
                }
            } else {
                log.log("Discord application lookup failed: HTTP ${r.code} for $url")
                null
            }
        }
    } catch (e: Exception) {
        log.log("Discord application lookup failed: $e")
        null
    }

    private fun parse(body: String) = try {
        Json.parseToJsonElement(body)
    } catch (_: Exception) {
        null
    }

    private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private companion object {
        const val MAX_BODY = 256L * 1024

        fun isId(s: String) = s.length in 17..20 && s.all { it in '0'..'9' }
    }
}
