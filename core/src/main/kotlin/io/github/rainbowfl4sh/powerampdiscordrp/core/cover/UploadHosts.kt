package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Public image hosts for local covers (AIMP: X0Upload / CatboxUpload / ImgurUpload). Each upload returns the picture's
 * https URL or "" if the host failed ([Upload]); the caller still checks that the URL really delivers the picture.
 */
internal class UploadHosts(
    private val web: Web,
    private val endpoints: CoverEndpoints,
    private val log: Logger,
) {
    /** Display / source name of a [Config].UPLOAD_* host. */
    fun name(host: Int): String = when (host) {
        Config.UPLOAD_IMGUR -> "Imgur"
        Config.UPLOAD_X0 -> "x0.at"
        else -> "catbox.moe"
    }

    /** [url]: the picture's https URL, "" = failed; [networkError]: the host never answered (our own network trouble?). */
    class Upload(val url: String, val networkError: Boolean = false)

    fun upload(host: Int, image: ByteArray, imgurClientId: String): Upload = when (host) {
        Config.UPLOAD_IMGUR -> imgur(image, imgurClientId)
        Config.UPLOAD_X0 -> x0(image)
        else -> catbox(image)
    }

    private fun multipart(fields: List<Pair<String, String>>, fileField: String, image: ByteArray) =
        MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .apply { fields.forEach { (k, v) -> addFormDataPart(k, v) } }
            .addFormDataPart(fileField, "cover.${Images.extension(image)}", image.toRequestBody(Images.mimeType(image).toMediaType()))
            .build()

    private fun post(url: String, body: MultipartBody, what: String, vararg headers: Pair<String, String>): Reply =
        web.send(what, timeoutSec = Web.UPLOAD_TIMEOUT_SEC) {
            url(url)
            headers.forEach { (k, v) -> header(k, v) }
            post(body)
        }

    // x0.at: no account; keeps files from days (big) to months (small - covers are small). Answer: the file's URL.
    fun x0(image: ByteArray): Upload {
        val r = post(endpoints.x0, multipart(emptyList(), "file", image), "x0.at")
        val url = singleHttpsUrl(r.body)
        if (r.status != 200 || url == null) {
            log.log("x0.at upload failed (HTTP ${r.status}): ${answerForLog(r.body)}")
            return Upload("", r.networkError)
        }
        return Upload(url)
    }

    fun catbox(image: ByteArray): Upload {
        val fields = listOf("reqtype" to "fileupload", "userhash" to "")
        val r = post(endpoints.catbox, multipart(fields, "fileToUpload", image), "catbox.moe", "Accept" to "*/*")
        val url = singleHttpsUrl(r.body)
        if (r.status != 200 || url == null) {
            log.log("catbox upload failed (HTTP ${r.status}, ${image.size} bytes sent): ${answerForLog(r.body)}")
            // what the server said about itself (redirect target, length, ...) - helps to find the cause
            for ((name, value) in r.headers) {
                val n = name.lowercase()
                if (n in CATBOX_LOG_HEADERS || n.startsWith("x-error")) log.log("  catbox: $name: ${value.take(160)}")
            }
            return Upload("", r.networkError)
        }
        return Upload(url)
    }

    fun imgur(image: ByteArray, clientId: String): Upload {
        val r = post(endpoints.imgur, multipart(emptyList(), "image", image), "Imgur", "Authorization" to "Client-ID $clientId")
        if (r.status != 200) {
            log.log("Imgur upload failed (HTTP ${r.status})")
            return Upload("", r.networkError)
        }
        val url = singleHttpsUrl(parseJson(r.body).at("data", "link").text)
        if (url == null) {
            log.log("Imgur upload failed: no image link in the answer")
            return Upload("")
        }
        return Upload(url)
    }

    companion object {
        private val CATBOX_LOG_HEADERS = setOf("location", "server", "content-length", "content-type", "cf-ray", "retry-after")
        private val SINGLE_URL = Regex("""https://[^\s<>"]+""")

        /**
         * The host's answer as https URL ("http://" is upgraded); null unless it is exactly one URL that Discord takes
         * (max. 256 characters - a longer one counts as a failed upload, so the next host stands in).
         */
        fun singleHttpsUrl(answer: String): String? {
            val url = https(answer.trim())
            return url.takeIf { SINGLE_URL.matches(it) && it.length <= CoverCache.MAX_URL_LENGTH }
        }

        /** Start of an error answer in one line (e.g. catbox's error message). */
        private fun answerForLog(body: String): String {
            val t = body.trim().take(120).map { if (it < ' ') ' ' else it }.joinToString("")
            return t.ifEmpty { "no answer" }
        }
    }
}
