package io.github.rainbowfl4sh.powerampdiscordrp.core.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Newest GitHub release of this project. [apkUrl] = first attached .apk ("" if none); its SHA-256 checksum is
 * [apkSha256] (GitHub's "digest" of the asset, lower-case hex) or else in the file at [checksumUrl] (the release's
 * "<apk>.sha256", written by the build workflow) - both "" if unknown.
 */
data class ReleaseInfo(
    val version: String,
    val pageUrl: String,
    val apkUrl: String,
    val notes: String,
    val apkSha256: String = "",
    val checksumUrl: String = "",
)

/** Outcome of [UpdateChecker.check]. */
sealed interface UpdateResult {
    data class Found(val release: ReleaseInfo) : UpdateResult

    /** No release; [detail] is for the log (HTTP status, exception). */
    data class Failed(val reason: Reason, val detail: String) : UpdateResult

    enum class Reason {
        OFFLINE,        // GitHub could not be reached (no connection, DNS, timeout)
        RATE_LIMITED,   // GitHub answered, but limits requests right now (60 an hour per IP address without an account)
        NO_RELEASE,     // the project has no published release (yet)
        BAD_ANSWER,     // any other answer, or one without a usable release
    }
}

/**
 * Asks GitHub for the newest release (api.github.com/repos/<repo>/releases/latest). Nothing else is sent. When GitHub's
 * limit for requests without an account is used up (60 an hour per address - VPN, carrier NAT), the release feed of
 * github.com ([webBase]/<repo>/releases.atom, no such limit) names the newest release instead (like the AIMP plugin).
 */
class UpdateChecker(
    private val http: OkHttpClient,
    private val repo: String,
    private val apiBase: String = "https://api.github.com",
    private val webBase: String = "https://github.com",
) {
    /** Blocking; null on any error. The version is taken from the tag / title ("v1.2.3" -> "1.2.3"). */
    fun latest(): ReleaseInfo? = (check() as? UpdateResult.Found)?.release

    /** Blocking, like [latest], but says why there is no release. */
    fun check(): UpdateResult {
        var code = 0   // HTTP status once GitHub answered (for the log)
        return try {
            val request = Request.Builder()
                .url("${apiBase.trimEnd('/')}/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val call = http.newCall(request)
            call.timeout().timeout(TIMEOUT_SEC, TimeUnit.SECONDS)
            call.execute().use { response ->
                code = response.code
                if (code != 200) {
                    val failed = failure(code, response.header("X-RateLimit-Remaining"), response.header("Retry-After"))
                    if (failed.reason != UpdateResult.Reason.RATE_LIMITED) return failed
                    return fromFeed()?.let { UpdateResult.Found(it) } ?: failed
                }
                val body = readLimited(response.body.source(), MAX_ANSWER_BYTES)
                    ?: return UpdateResult.Failed(UpdateResult.Reason.BAD_ANSWER, "HTTP 200, answer too large")
                parseRelease(body, repo)?.let { UpdateResult.Found(it) }
                    ?: UpdateResult.Failed(UpdateResult.Reason.BAD_ANSWER, "HTTP 200 without a release version")
            }
        } catch (e: IOException) {   // offline, DNS, timeout, connection lost (also while reading the answer)
            UpdateResult.Failed(UpdateResult.Reason.OFFLINE, if (code == 0) e.toString() else "HTTP $code: $e")
        } catch (e: Exception) {   // malformed answer
            UpdateResult.Failed(UpdateResult.Reason.BAD_ANSWER, "HTTP $code: $e")
        }
    }

    /** The newest release named by the release feed; null if the feed did not answer with one. */
    private fun fromFeed(): ReleaseInfo? = try {
        val request = Request.Builder()
            .url("${webBase.trimEnd('/')}/$repo/releases.atom")
            .header("Accept", "application/atom+xml")
            .get()
            .build()
        val call = http.newCall(request)
        call.timeout().timeout(TIMEOUT_SEC, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (response.code != 200) return null
            val body = readLimited(response.body.source(), MAX_ANSWER_BYTES) ?: return null
            parseFeed(body, repo)
        }
    } catch (e: Exception) {   // offline, malformed answer: the API's own failure is reported
        null
    }

    companion object {
        private const val TIMEOUT_SEC = 15L

        /** The APK name the build workflow gives a release ("PowerampDiscordRP-v1.3.0.apk"). */
        fun apkName(tag: String): String = "PowerampDiscordRP-$tag.apk"

        private val FEED_TAG = Regex("""/releases/tag/([0-9A-Za-z._-]{1,64})""")
        private val HEX64 = Regex("""^[0-9a-fA-F]{64}$""")

        /**
         * The newest release of the release feed (entries come newest first; a tag with "-" is a pre-release, which the
         * build workflow publishes as such and the API's "latest" never names). The feed has no assets: the APK and its
         * checksum file are named like the build workflow names them.
         */
        internal fun parseFeed(xml: String, repo: String): ReleaseInfo? {
            for (entry in xml.split("<entry").drop(1)) {
                val tag = FEED_TAG.find(entry)?.groupValues?.get(1) ?: continue
                if ('-' in tag) continue
                val version = DOTTED_VERSION.find(tag)?.value ?: PLAIN_VERSION.find(tag)?.groupValues?.get(1) ?: continue
                val apk = "https://github.com/$repo/releases/download/$tag/${apkName(tag)}"
                return ReleaseInfo(
                    version = version,
                    pageUrl = "https://github.com/$repo/releases/tag/$tag",
                    apkUrl = apk,
                    notes = "",
                    checksumUrl = "$apk.sha256",
                )
            }
            return null
        }

        /** The SHA-256 in GitHub's asset digest ("sha256:<hex>") or a checksum file ("<hex>  name"); "" if none. */
        fun sha256Of(text: String): String {
            val t = text.trim().removePrefix("sha256:").substringBefore(' ').substringBefore('\t').trim()
            return if (HEX64.matches(t)) t.lowercase() else ""
        }
        private const val MAX_ANSWER_BYTES = 2L shl 20
        private val JSON = Json { isLenient = true; ignoreUnknownKeys = true }

        /**
         * GitHub's answer without a release: 404 = nothing published yet; its rate limit answers 429, or 403 with no
         * requests left / a time to wait (docs.github.com, "Rate limits for the REST API").
         */
        private fun failure(code: Int, remaining: String?, retryAfter: String?): UpdateResult.Failed {
            val limited = code == 429 || (code == 403 && (remaining?.trim() == "0" || retryAfter != null))
            val reason = when {
                limited -> UpdateResult.Reason.RATE_LIMITED
                code == 404 -> UpdateResult.Reason.NO_RELEASE
                else -> UpdateResult.Reason.BAD_ANSWER
            }
            return UpdateResult.Failed(reason, "HTTP $code")
        }

        /**
         * First dotted number in a text with its pre-release part ("v1.5.0" -> "1.5.0", "v1.1.0-beta1" -> "1.1.0-beta1",
         * "Release 1.3r" -> "1.3"), like the AIMP plugin's FindVersion.
         */
        private val DOTTED_VERSION = Regex("""(?<![\d.])\d+(?:\.\d+)+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?""")
        private val PLAIN_VERSION = Regex("""^\s*[vV]?(\d+)\s*$""")
        private val RUNS = Regex("""\d+|\D+""")   // digit / non-digit runs of a pre-release identifier

        /**
         * Compares like semantic versioning: the dotted numbers numerically ("1.10.0" > "1.9.2"); with equal numbers a
         * pre-release comes before its release ("1.1.0-beta1" < "1.1.0") and pre-releases are compared part by part,
         * numbers in them numerically ("1.1.0-beta2" > "1.1.0-beta1", "-beta10" > "-beta9"). Build metadata ("+5") is ignored.
         */
        fun isNewer(candidate: String, current: String): Boolean {
            val (aCore, aPre) = split(candidate)
            val (bCore, bPre) = split(current)
            val a = numbers(aCore)
            val b = numbers(bCore)
            if (a.isEmpty()) return false
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0L }
                val y = b.getOrElse(i) { 0L }
                if (x != y) return x > y
            }
            return comparePreRelease(aPre, bPre) > 0
        }

        /** "v1.2.3-beta.1+5" -> ("1.2.3", "beta.1"); "" = no pre-release (a release). */
        private fun split(version: String): Pair<String, String> {
            val v = version.trim().removePrefix("v").removePrefix("V").substringBefore('+').trim()
            return v.substringBefore('-').trim() to v.substringAfter('-', "").trim().lowercase()
        }

        /** "1.2.3" -> [1, 2, 3]; a part without leading digits counts as 0. */
        private fun numbers(core: String): List<Long> {
            if (core.isEmpty()) return emptyList()
            return core.split('.').map { part -> part.takeWhile { it.isDigit() }.take(18).toLongOrNull() ?: 0L }
        }

        /** Precedence of two pre-release parts: "" (a release) is highest, then part by part ("beta.2" < "beta.10" < "rc1"). */
        private fun comparePreRelease(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return 1
            if (b.isEmpty()) return -1
            val x = a.split('.', '-')
            val y = b.split('.', '-')
            for (i in 0 until minOf(x.size, y.size)) {
                val c = compareIdentifier(x[i], y[i])
                if (c != 0) return c
            }
            return x.size.compareTo(y.size)   // "beta" < "beta.1"
        }

        /** Runs of digits compare as numbers, everything else as text ("beta2" < "beta10", "alpha" < "beta" < "rc"). */
        private fun compareIdentifier(a: String, b: String): Int {
            val x = RUNS.findAll(a).map { it.value }.toList()
            val y = RUNS.findAll(b).map { it.value }.toList()
            for (i in 0 until minOf(x.size, y.size)) {
                val p = x[i]
                val q = y[i]
                val c = if (p[0].isDigit() && q[0].isDigit()) {
                    val m = p.trimStart('0')
                    val n = q.trimStart('0')
                    if (m.length != n.length) m.length.compareTo(n.length) else m.compareTo(n)
                } else {
                    p.compareTo(q)
                }
                if (c != 0) return c
            }
            return x.size.compareTo(y.size)
        }

        /** The release from GitHub's JSON answer; null without a version. */
        internal fun parseRelease(json: String, repo: String): ReleaseInfo? {
            val root = JSON.parseToJsonElement(json) as? JsonObject ?: return null
            val tag = root.str("tag_name")
            val version = DOTTED_VERSION.find(tag)?.value
                ?: DOTTED_VERSION.find(root.str("name"))?.value
                ?: PLAIN_VERSION.find(tag)?.groupValues?.get(1)
                ?: return null
            // only an APK straight from this project's releases (GitHub's download links look like that)
            val prefix = "https://github.com/$repo/".lowercase()
            val assets: List<JsonElement> = root["assets"] as? JsonArray ?: emptyList()
            val apk = assets
                .mapNotNull { it as? JsonObject }
                .firstOrNull { asset ->
                    asset.str("name").lowercase().endsWith(".apk") &&
                        asset.str("browser_download_url").lowercase().startsWith(prefix)
                }
            val page = root.str("html_url").ifEmpty { "https://github.com/$repo/releases/latest" }
            val apkUrl = apk?.str("browser_download_url").orEmpty()
            // "<apk>.sha256" next to it (the build workflow's checksum file)
            val checksum = if (apk == null) null else assets
                .mapNotNull { it as? JsonObject }
                .firstOrNull { asset ->
                    asset.str("name").equals(apk.str("name") + ".sha256", ignoreCase = true) &&
                        asset.str("browser_download_url").lowercase().startsWith(prefix)
                }
            return ReleaseInfo(
                version = version,
                pageUrl = page,
                apkUrl = apkUrl,
                notes = root.str("body").replace("\r\n", "\n").trim(),
                apkSha256 = apk?.let { sha256Of(it.str("digest")) }.orEmpty(),
                checksumUrl = checksum?.str("browser_download_url").orEmpty(),
            )
        }

        private fun JsonObject.str(key: String): String =
            (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content ?: ""

        private fun readLimited(source: okio.BufferedSource, maxBytes: Long): String? {
            val buffer = Buffer()
            while (source.read(buffer, 8192) != -1L) {
                if (buffer.size > maxBytes) return null
            }
            return buffer.readUtf8()
        }
    }
}
