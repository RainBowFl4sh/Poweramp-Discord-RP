package io.github.rainbowfl4sh.powerampdiscordrp.core.cover

import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The resolver's cover cache on disk (AIMP: DiscordRPC_covers.tsv): one line per cover,
 * `key TAB url TAB source TAB storedWallMs`, appended when a cover is stored (for uploads storedWallMs is when it was
 * last verified: uploaded, or found still there by a later check). A later line for the same key wins;
 * a line with an empty URL removes the key (written by [remove], so a dropped cover does not come back after a restart).
 * A line with the URL [MISS_URL] is a [Miss]: no source knew a cover for the key (`key TAB - TAB settings TAB storedWallMs`);
 * older versions skip such lines (no usable URL).
 * Lines that cannot be read are skipped. More than [MAX_LINES] lines -> the file is rewritten with the newest [KEEP_LINES].
 * Keys are the resolver's track keys (16 hex digits) and its picture keys of uploads ("u<sha-1>-<size>").
 *
 * Not synchronized: the resolver guards every call with its lock.
 */
internal class CoverCache(private val file: File, private val log: Logger) {
    private val entries = LinkedHashMap<String, CoverResult>()   // oldest first (an updated key moves to the end)
    private val misses = LinkedHashMap<String, Miss>()           // same order
    private var fileLines = 0

    init {
        load()
    }

    val size: Int get() = entries.size

    operator fun get(key: String): CoverResult? = entries[key]

    /**
     * Every source answered "nothing found" for a key at [storedWallMs], with the lookup settings [settings] (a hash: other
     * settings may find something).
     */
    data class Miss(val storedWallMs: Long, val settings: String)

    fun miss(key: String): Miss? = misses[key]

    /** Records a [Miss] (it replaces a cover of the key). */
    fun putMiss(key: String, miss: Miss) {
        entries.remove(key)
        misses.remove(key)
        misses[key] = miss
        append(missLine(key, miss))
    }

    fun put(key: String, result: CoverResult) {
        misses.remove(key)
        entries.remove(key)
        entries[key] = result
        append(line(key, result))
    }

    /** Drops [key]; [persist] also records that in the file. */
    fun remove(key: String, persist: Boolean) {
        val had = entries.remove(key) != null
        val hadMiss = misses.remove(key) != null
        if ((had || hadMiss) && persist) append(line(key, CoverResult()))
    }

    /** Drops every key whose cover is [url] (one upload can serve several keys); [persist] as for [remove]. */
    fun removeUrl(url: String, persist: Boolean) {
        val keys = entries.filterValues { it.url == url }.keys.toList()
        for (key in keys) remove(key, persist)
    }

    /** Marks every key whose cover is [url] as verified at [wallMs] (stored in the file too). */
    fun touchUrl(url: String, wallMs: Long) {
        val hits = entries.filterValues { it.url == url }
        for ((key, result) in hits) put(key, result.copy(storedWallMs = wallMs))
    }

    fun clear() {
        entries.clear()
        misses.clear()
        fileLines = 0
        if (file.exists() && !file.delete()) log.log("Cover cache: cannot delete ${file.name}")
    }

    private fun load() {
        if (!file.isFile) return
        if (file.length() > MAX_FILE_BYTES) {   // not ours / damaged: start again
            log.log("Cover cache: ${file.name} is too big (${file.length()} bytes) - discarded")
            file.delete()
            return
        }
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            log.log("Cover cache: cannot read ${file.name} (${e.message})")
            return
        }
        for (raw in text.split('\n')) {
            val (key, result) = parse(raw.removeSuffix("\r")) ?: continue
            fileLines++
            entries.remove(key)
            misses.remove(key)
            when {
                result.url == MISS_URL -> misses[key] = Miss(result.storedWallMs, result.source)
                !result.isEmpty -> entries[key] = result
            }
        }
        if (fileLines > MAX_LINES) compact()
    }

    private fun append(line: String) {
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { it.write(line.toByteArray(Charsets.UTF_8)) }
            fileLines++
        } catch (e: IOException) {
            log.log("Cover cache: cannot write ${file.name} (${e.message})")
            return
        }
        if (fileLines > MAX_LINES) compact()
    }

    /**
     * Keeps the file small: rewritten with the newest [KEEP_LINES] covers and [KEEP_MISSES] misses (written to a
     * temporary file first).
     */
    private fun compact() {
        val keep = entries.entries.toList().takeLast(KEEP_LINES).map { (k, r) -> line(k, r) } +
            misses.entries.toList().takeLast(KEEP_MISSES).map { (k, m) -> missLine(k, m) }
        val tmp = File(file.path + ".tmp")
        try {
            tmp.writeText(keep.joinToString(""), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.delete()
                if (!tmp.renameTo(file)) throw IOException("cannot replace ${file.name}")
            }
            fileLines = keep.size
        } catch (e: IOException) {
            tmp.delete()
            log.log("Cover cache: cannot rewrite ${file.name} (${e.message})")
        }
    }

    companion object {
        const val MAX_LINES = 3000
        const val KEEP_LINES = 2000
        const val KEEP_MISSES = 500

        /** URL field of a [Miss] line (no URL an older version would take). */
        const val MISS_URL = "-"
        private const val MAX_FILE_BYTES = 8L shl 20

        fun line(key: String, r: CoverResult) = "$key\t${r.url}\t${r.source}\t${r.storedWallMs}\n"

        fun missLine(key: String, m: Miss) = "$key\t$MISS_URL\t${m.settings}\t${m.storedWallMs}\n"

        /** null for lines that are not cache entries (empty URL = removal marker, [MISS_URL] = a [Miss]). */
        fun parse(line: String): Pair<String, CoverResult>? {
            val parts = line.split('\t')
            val key = parts[0]
            if (parts.size < 2 || key.isEmpty() || key.any { it.isWhitespace() }) return null
            val url = parts[1]
            if (url.isNotEmpty() && url != MISS_URL && !isUsableUrl(url)) return null
            val source = parts.getOrElse(2) { "" }
            val stored = parts.getOrNull(3)?.trim()?.toLongOrNull() ?: 0
            return key to CoverResult(url, source, stored)
        }

        /** An http(s) URL Discord accepts as image (max. 256 characters) that also fits into one cache line. */
        fun isUsableUrl(url: String): Boolean =
            url.length <= MAX_URL_LENGTH && (url.startsWith("https://") || url.startsWith("http://")) &&
                url.length > 8 && url.none { it.isWhitespace() || it < ' ' }

        const val MAX_URL_LENGTH = 256
    }
}
