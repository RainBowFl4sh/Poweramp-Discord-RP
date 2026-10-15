package io.github.rainbowfl4sh.powerampdiscordrp.core.rpc

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class RpcProtocolTest {
    private val activity = buildJsonObject {
        put("name", "Poweramp")
        put("type", 2)
        put("details", "Söng \"quoted\" \\ 😀")
        putJsonObject("assets") { put("large_image", "https://img/x.jpg") }
    }

    private fun root(frame: String): JsonObject = Json.parseToJsonElement(frame).jsonObject

    @Test
    fun `set activity frame`() {
        val frame = root(RpcProtocol.setActivity(activity, 4242, "n-1"))
        assertEquals("SET_ACTIVITY", frame.getValue("cmd").jsonPrimitive.content)
        assertEquals("n-1", frame.getValue("nonce").jsonPrimitive.content)
        val args = frame.getValue("args").jsonObject
        assertEquals(4242, args.getValue("pid").jsonPrimitive.int)
        assertEquals(activity, args.getValue("activity"))
    }

    @Test
    fun `clearing leaves the activity out like the AIMP plugin`() {
        val frame = root(RpcProtocol.setActivity(null, 7, "n-2"))
        val args = frame.getValue("args").jsonObject
        assertEquals(setOf("pid"), args.keys)
        assertEquals(7, args.getValue("pid").jsonPrimitive.int)
        assertEquals("SET_ACTIVITY", frame.getValue("cmd").jsonPrimitive.content)
    }

    @Test
    fun `own frame parses back as SET_ACTIVITY with its nonce`() {
        val parsed = RpcProtocol.parse(RpcProtocol.setActivity(activity, 1, "abc"))
        assertEquals(RpcMessage.ActivitySet("abc", "https://img/x.jpg"), parsed)
        assertEquals(RpcMessage.ActivitySet("abc", null), RpcProtocol.parse(RpcProtocol.setActivity(null, 1, "abc")))
    }

    @Test
    fun `ready with user`() {
        val frame = """{"cmd":"DISPATCH","evt":"READY","data":{"v":1,"config":{"cdn_host":"cdn.discordapp.com"},
            "user":{"id":"123456789012345678","username":"rainbow","global_name":"Rainbow","avatar":"abc","bot":false}},"nonce":null}"""
        assertEquals(
            RpcMessage.Ready(DiscordUser(id = "123456789012345678", username = "rainbow", globalName = "Rainbow", avatar = "abc")),
            RpcProtocol.parse(frame),
        )
    }

    @Test
    fun `ready without user or with partial user`() {
        assertEquals(RpcMessage.Ready(null), RpcProtocol.parse("""{"cmd":"DISPATCH","evt":"READY","data":{"v":1}}"""))
        assertEquals(RpcMessage.Ready(null), RpcProtocol.parse("""{"cmd":"DISPATCH","evt":"READY"}"""))
        assertEquals(
            RpcMessage.Ready(DiscordUser(id = "1", username = "u")),
            RpcProtocol.parse("""{"cmd":"DISPATCH","evt":"READY","data":{"user":{"id":1,"username":"u","global_name":null}}}"""),
        )
    }

    @Test
    fun `activity answer with Discord's media proxy key`() {
        val frame = """{"cmd":"SET_ACTIVITY","data":{"name":"Poweramp","type":2,
            "assets":{"large_image":"mp:external/AbC/https/img/x.jpg","small_image":"123"}},"evt":null,"nonce":"n-9"}"""
        assertEquals(RpcMessage.ActivitySet("n-9", "mp:external/AbC/https/img/x.jpg"), RpcProtocol.parse(frame))
        assertEquals(RpcMessage.ActivitySet(null, null), RpcProtocol.parse("""{"cmd":"SET_ACTIVITY","data":null}"""))
    }

    @Test
    fun `error answer`() {
        val frame = """{"cmd":"SET_ACTIVITY","evt":"ERROR","data":{"code":4002,"message":"child \"activity\" fails"},"nonce":"n-3"}"""
        assertEquals(RpcMessage.Error("n-3", 4002, "child \"activity\" fails"), RpcProtocol.parse(frame))
        assertEquals(RpcMessage.Error(null, 0, ""), RpcProtocol.parse("""{"evt":"ERROR"}"""))
        assertEquals(RpcMessage.Error(null, 4000, "x"), RpcProtocol.parse("""{"evt":"ERROR","data":{"code":"4000","message":"x"}}"""))
    }

    @Test
    fun `other frames`() {
        assertEquals(RpcMessage.Other("DISPATCH", "ACTIVITY_JOIN"), RpcProtocol.parse("""{"cmd":"DISPATCH","evt":"ACTIVITY_JOIN","data":{}}"""))
        assertEquals(RpcMessage.Other(null, null), RpcProtocol.parse("{}"))
    }

    @Test
    fun `malformed input never throws`() {
        for (bad in listOf("", "   ", "not json", "{", "[1,2]", "null", "42", "\"READY\"", """{"cmd":}""", "\u0000")) {
            assertEquals(bad, RpcMessage.Other(null, null), RpcProtocol.parse(bad))
        }
        // wrong types inside a valid object
        assertEquals(RpcMessage.ActivitySet(null, null), RpcProtocol.parse("""{"cmd":"SET_ACTIVITY","data":[1],"nonce":{"a":1}}"""))
        assertEquals(RpcMessage.Ready(null), RpcProtocol.parse("""{"evt":"READY","data":{"user":"me"}}"""))
        assertEquals(RpcMessage.Error(null, 0, ""), RpcProtocol.parse("""{"evt":"ERROR","data":{"code":true,"message":["x"]}}"""))
    }
}
