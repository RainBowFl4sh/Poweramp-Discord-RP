package io.github.rainbowfl4sh.powerampdiscordrp.core.engine

import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverResult
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.CoverSource
import io.github.rainbowfl4sh.powerampdiscordrp.core.cover.Probe
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.Config
import io.github.rainbowfl4sh.powerampdiscordrp.core.model.TrackInfo
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.RpcTransport
import io.github.rainbowfl4sh.powerampdiscordrp.core.rpc.TransportError
import io.github.rainbowfl4sh.powerampdiscordrp.core.util.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

/** Monotonic time = the test scheduler's virtual time; wall time runs alongside from a fixed base. */
class FakeClock(private val scheduler: TestCoroutineScheduler) : Clock {
    override fun nowMs(): Long = scheduler.currentTime
    override fun wallMs(): Long = WALL_BASE + scheduler.currentTime

    companion object {
        const val WALL_BASE = 1_700_000_000_000L
    }
}

/**
 * Runs the engine's background work (cover lookups, proxy checks) only when the test says so,
 * so "while resolving" states can be observed.
 */
class ManualDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        synchronized(queue) { queue.addLast(block) }
    }

    val pending: Int get() = synchronized(queue) { queue.size }

    /** Runs everything queued (also what the tasks queue themselves); returns the number of tasks run. */
    fun runAll(): Int {
        var n = 0
        while (true) {
            val task = synchronized(queue) { queue.removeFirstOrNull() } ?: return n
            task.run()
            n++
        }
    }
}

/** Discord stand-in: records connects and frames; the test plays READY / close / answers. */
class FakeTransport : RpcTransport {
    @Volatile
    override var isReady = false
        private set
    var listener: RpcTransport.Listener? = null
        private set
    val connects = mutableListOf<Long>()
    var disconnects = 0
        private set
    val frames = mutableListOf<String>()
    val frameTimes = mutableListOf<Long>()   // when each of [frames] was sent, by [now]
    var now: () -> Long = { 0L }
    var failSends = false

    override fun setListener(listener: RpcTransport.Listener?) {
        this.listener = listener
    }

    override fun connect(applicationId: Long) {
        connects += applicationId
    }

    override fun disconnect() {
        disconnects++
        isReady = false
    }

    override fun send(frame: String): Boolean {
        if (!isReady || failSends) return false
        frames += frame
        frameTimes += now()
        return true
    }

    fun ready(userName: String = "tester") {
        isReady = true
        listener!!.onReady(
            """{"cmd":"DISPATCH","evt":"READY","data":{"v":1,"user":{"id":"42","username":"$userName","global_name":"","avatar":""}}}""",
        )
    }

    fun close(error: TransportError, code: Int = -1, reason: String = "") {
        isReady = false
        listener!!.onClosed(error, code, reason)
    }

    fun answer(frame: String) = listener!!.onFrame(frame)

    /** The connection broke without a close callback (only a failing send tells). */
    fun dropSilently() {
        isReady = false
    }

    /** Discord's answer to a SET_ACTIVITY frame we sent: it stores a URL image as "mp:external/...". */
    fun answerWithProxy(frame: String, proxyKey: String) =
        answer("""{"cmd":"SET_ACTIVITY","data":{"assets":{"large_image":"mp:external/$proxyKey"}},"evt":null,"nonce":"${nonceOf(frame)}"}""")

    /** Discord took a frame we sent (answer without a URL image). */
    fun accept(frame: String) = answer("""{"cmd":"SET_ACTIVITY","data":{},"evt":null,"nonce":"${nonceOf(frame)}"}""")

    /** Discord rejected a frame we sent ([message] without quotes). */
    fun reject(frame: String, message: String, code: Int = 4002) =
        answer("""{"cmd":"SET_ACTIVITY","evt":"ERROR","data":{"code":$code,"message":"$message"},"nonce":"${nonceOf(frame)}"}""")

    // ---- reading what was sent

    /** The activity of a frame; null = the presence was cleared. */
    fun activityOf(frame: String): JsonObject? = root(frame).getValue("args").jsonObject["activity"]?.jsonObject

    fun nonceOf(frame: String): String = root(frame).getValue("nonce").jsonPrimitive.content

    fun pidOf(frame: String): Int = root(frame).getValue("args").jsonObject.getValue("pid").jsonPrimitive.content.toInt()

    val activities: List<JsonObject?> get() = frames.map(::activityOf)
    val lastActivity: JsonObject? get() = activityOf(frames.last())

    private fun root(frame: String): JsonObject = Json.parseToJsonElement(frame).jsonObject
}

fun JsonObject.text(key: String): String? = this[key]?.jsonPrimitive?.content

fun JsonObject.largeImage(): String? = this["assets"]?.jsonObject?.text("large_image")
fun JsonObject.smallImage(): String? = this["assets"]?.jsonObject?.text("small_image")

/** Cover resolver stand-in keyed by track title. */
class FakeCovers : CoverSource {
    val cache = mutableMapOf<String, CoverResult>()
    val found = mutableMapOf<String, CoverResult>()     // what resolve() finds
    val resolveCalls = mutableListOf<String>()
    val forgotten = mutableListOf<String>()
    val checked = mutableListOf<String>()
    var reachableAnswer: (String) -> Boolean = { true }
    var probeAnswer: ((String) -> Probe)? = null   // null = derived from reachableAnswer
    var cleared = 0

    override fun peekCache(track: TrackInfo): CoverResult = cache[track.title] ?: CoverResult()

    override fun resolve(track: TrackInfo, config: Config): CoverResult {
        resolveCalls += track.title
        val r = found[track.title] ?: CoverResult()
        if (!r.isEmpty) cache[track.title] = r
        return r
    }

    override fun forget(track: TrackInfo) {
        forgotten += track.title
        cache.remove(track.title)
    }

    override fun clearCache() {
        cleared++
        cache.clear()
    }

    override fun reachable(url: String): Boolean {
        checked += url
        return reachableAnswer(url)
    }

    override fun probe(url: String): Probe {
        checked += url
        return probeAnswer?.invoke(url) ?: if (reachableAnswer(url)) Probe.OK else Probe.BROKEN
    }
}
