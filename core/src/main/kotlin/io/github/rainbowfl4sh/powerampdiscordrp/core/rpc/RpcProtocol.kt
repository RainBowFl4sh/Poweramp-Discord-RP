package io.github.rainbowfl4sh.powerampdiscordrp.core.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** User reported by Discord's READY dispatch (the Android app may report a placeholder user). */
data class DiscordUser(
    val id: String = "",
    val username: String = "",
    val globalName: String = "",
    val avatar: String = "",
)

/** A frame received from Discord, already interpreted. */
sealed interface RpcMessage {
    /** DISPATCH / READY: the connection can be used. */
    data class Ready(val user: DiscordUser?) : RpcMessage

    /** Answer to SET_ACTIVITY. [largeImage] is how Discord stores the image (e.g. "mp:external/..." for a URL). */
    data class ActivitySet(val nonce: String?, val largeImage: String?) : RpcMessage

    /** evt = ERROR (e.g. invalid payload, rate limit). */
    data class Error(val nonce: String?, val code: Int, val message: String) : RpcMessage

    /** Anything else (kept for logging). */
    data class Other(val cmd: String?, val evt: String?) : RpcMessage
}

/**
 * JSON frames of Discord's RPC protocol, as used by the Social SDK over the Android binder service
 * (com.discord.socialsdk.rpc.IDiscordRpcService) - the same JSON as the desktop IPC.
 */
object RpcProtocol {
    /** Close code / error code Discord uses when it does not know the application ID. */
    const val CLOSE_INVALID_CLIENT_ID = 4000

    /** {"cmd":"SET_ACTIVITY","args":{"pid":pid,"activity":activity},"nonce":nonce}; activity null = clear the presence. */
    fun setActivity(activity: JsonObject?, pid: Int, nonce: String): String = buildJsonObject {
        put("cmd", "SET_ACTIVITY")
        putJsonObject("args") {
            put("pid", pid)
            // like the AIMP plugin (and Discord's own SDKs): clearing = "activity" left out
            if (activity != null) put("activity", activity)
        }
        put("nonce", nonce)
    }.toString()

    /** Interprets one received frame; never throws (malformed JSON -> Other(null, null)). */
    fun parse(frame: String): RpcMessage {
        val root = try {
            Json.parseToJsonElement(frame) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return RpcMessage.Other(null, null)

        val cmd = root.string("cmd")
        val evt = root.string("evt")
        val nonce = root.string("nonce")
        val data = root.obj("data")
        return when {
            evt == "READY" -> RpcMessage.Ready(data?.obj("user")?.let { u ->
                DiscordUser(
                    id = u.string("id").orEmpty(),
                    username = u.string("username").orEmpty(),
                    globalName = u.string("global_name").orEmpty(),
                    avatar = u.string("avatar").orEmpty(),
                )
            })
            evt == "ERROR" -> RpcMessage.Error(
                nonce = nonce,
                code = (data?.get("code") as? JsonPrimitive)?.intOrNull ?: 0,
                message = data?.string("message").orEmpty(),
            )
            cmd == "SET_ACTIVITY" -> RpcMessage.ActivitySet(nonce, largeImage(data) ?: largeImage(root.obj("args")?.obj("activity")))
            else -> RpcMessage.Other(cmd, evt)
        }
    }

    /** assets.large_image of an activity: Discord answers with the activity as stored in "data" (some bridges echo "args"). */
    private fun largeImage(activity: JsonObject?): String? = activity?.obj("assets")?.string("large_image")

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    /** String (or number) value of [key]; null if missing, JSON null or not a primitive. */
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
