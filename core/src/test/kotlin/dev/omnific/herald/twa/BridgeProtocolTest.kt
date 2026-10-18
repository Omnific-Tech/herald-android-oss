package dev.omnific.herald.twa

import dev.omnific.herald.client.HeraldHttpException
import dev.omnific.herald.client.Subscription
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeProtocolTest {

    private val channels = setOf("alerts", "system")

    private fun parse(message: String) = parseBridgeCommand(message, channels)

    private fun set(subscriptions: String, id: String = "r1") =
        parse("""{"type":"herald.subscriptions.set","id":"$id","subscriptions":$subscriptions}""")

    @Test
    fun `parses a get`() {
        assertEquals(BridgeCommand.GetSubscriptions("r1"), parse("""{"type":"herald.subscriptions.get","id":"r1"}"""))
    }

    @Test
    fun `parses a set, defaulting the topic to the whole channel`() {
        assertEquals(
            BridgeCommand.SetSubscriptions(
                "r1",
                listOf(Subscription("alerts", "earthquake.jakarta"), Subscription("system")),
            ),
            set("""[{"channel":"alerts","topic":"earthquake.jakarta"},{"channel":"system"}]"""),
        )
    }

    @Test
    fun `accepts an empty list, which unsubscribes from everything`() {
        assertEquals(BridgeCommand.SetSubscriptions("r1", emptyList()), set("[]"))
    }

    /** A newer page may send fields this APK does not know yet. */
    @Test
    fun `ignores unknown keys`() {
        assertEquals(
            BridgeCommand.SetSubscriptions("r1", listOf(Subscription("alerts"))),
            parse("""{"type":"herald.subscriptions.set","id":"r1","v":2,"subscriptions":[{"channel":"alerts","label":"x"}]}"""),
        )
    }

    /** Without a usable id there is nothing a reply could be matched to. */
    @Test
    fun `ignores messages it cannot answer`() {
        val ignored = listOf(
            "not json",
            "[]",
            "\"herald.subscriptions.get\"",
            """{"type":"herald.unknown","id":"r1"}""",
            """{"id":"r1"}""",
            """{"type":"herald.subscriptions.get"}""",
            """{"type":"herald.subscriptions.get","id":""}""",
            """{"type":"herald.subscriptions.get","id":7}""",
            """{"type":"herald.subscriptions.get","id":"${"x".repeat(65)}"}""",
            """{"type":"herald.subscriptions.set","id":"${"x".repeat(65)}","subscriptions":[]}""",
        )

        ignored.forEach { message -> assertNull("ignoring $message", parse(message)) }
    }

    @Test
    fun `accepts ids of 1 to 64 characters`() {
        listOf("x", "x".repeat(64)).forEach { id ->
            assertEquals(BridgeCommand.GetSubscriptions(id), parse("""{"type":"herald.subscriptions.get","id":"$id"}"""))
        }
    }

    @Test
    fun `rejects a malformed set that carries an id`() {
        val malformed = listOf(
            """{"type":"herald.subscriptions.set","id":"r1"}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":null}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":{}}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":[{"topic":"x"}]}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":[{"channel":"alerts","topic":null}]}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":[{"channel":1}]}""",
            """{"type":"herald.subscriptions.set","id":"r1","subscriptions":["alerts"]}""",
        )

        malformed.forEach { message -> assertEquals("rejecting $message", BridgeCommand.Rejected("r1"), parse(message)) }
    }

    @Test
    fun `accepts all 343 Aeon earthquake haze and system subscriptions`() {
        val provinces = listOf(
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "21",
            "31", "32", "33", "34", "35", "36", "51", "52", "53",
            "61", "62", "63", "64", "65", "71", "72", "73", "74", "75", "76",
            "81", "82", "91", "92", "93", "94", "95", "96",
        )
        val earthquakes = provinces.flatMap { province ->
            listOf(0, 3, 4, 5, 6).map { band -> Subscription("alerts", "earthquake.province.$province.m$band") }
        }
        val haze = provinces.flatMap { province ->
            listOf("15_6", "55_5", "150_5", "250_5").map { band -> Subscription("alerts", "haze.province.$province.p$band") }
        }
        val subscriptions = earthquakes + haze + Subscription("system")
        val encoded = subscriptions.joinToString(",", "[", "]") {
            """{"channel":"${it.channel}","topic":"${it.topic}"}"""
        }

        assertEquals(343, subscriptions.size)
        assertEquals(BridgeCommand.SetSubscriptions("r1", subscriptions), set(encoded))
    }

    @Test
    fun `accepts 500 subscriptions`() {
        fun list(count: Int) = (0 until count).joinToString(",", "[", "]") { """{"channel":"alerts","topic":"t$it"}""" }

        assertEquals(250, (set(list(250)) as BridgeCommand.SetSubscriptions).subscriptions.size)
        assertEquals(251, (set(list(251)) as BridgeCommand.SetSubscriptions).subscriptions.size)
        assertEquals(500, (set(list(500)) as BridgeCommand.SetSubscriptions).subscriptions.size)
    }

    @Test
    fun `rejects 501 subscriptions`() {
        val subscriptions = (0 until 501).joinToString(",", "[", "]") {
            """{"channel":"alerts","topic":"t$it"}"""
        }

        assertEquals(BridgeCommand.Rejected("r1"), set(subscriptions))
    }

    @Test
    fun `rejects a channel the product does not configure`() {
        assertEquals(BridgeCommand.Rejected("r1"), set("""[{"channel":"alerts"},{"channel":"marketing"}]"""))
    }

    @Test
    fun `accepts topics of at most 200 characters`() {
        assertEquals(
            BridgeCommand.SetSubscriptions("r1", listOf(Subscription("alerts", "t".repeat(200)))),
            set("""[{"channel":"alerts","topic":"${"t".repeat(200)}"}]"""),
        )
        assertEquals(BridgeCommand.Rejected("r1"), set("""[{"channel":"alerts","topic":"${"t".repeat(201)}"}]"""))
    }

    /** An omitted topic and an empty one are the same subscription. */
    @Test
    fun `rejects duplicates`() {
        assertEquals(BridgeCommand.Rejected("r1"), set("""[{"channel":"alerts","topic":"a"},{"channel":"alerts","topic":"a"}]"""))
        assertEquals(BridgeCommand.Rejected("r1"), set("""[{"channel":"alerts"},{"channel":"alerts","topic":""}]"""))
    }

    @Test
    fun `reports Herald refusing the list as invalid`() {
        listOf(400, 404, 422).forEach { code ->
            assertEquals(
                BridgeError.INVALID_SUBSCRIPTIONS,
                bridgeErrorCode(HeraldHttpException(code, "", path = "/v1/subscriptions")),
            )
        }
    }

    @Test
    fun `reports everything else as Herald being unavailable`() {
        val transient = listOf(
            HeraldHttpException(429, "", path = "/v1/subscriptions"),
            HeraldHttpException(503, "", path = "/v1/subscriptions"),
            HeraldHttpException(401, "", path = "/v1/subscriptions"),
            HeraldHttpException(404, "", path = "/v1/devices/register"),
            IOException("timeout"),
            IllegalStateException("could not persist Herald subscriptions"),
        )

        transient.forEach { error -> assertEquals("for $error", BridgeError.HERALD_UNAVAILABLE, bridgeErrorCode(error)) }
    }

    @Test
    fun `encodes the ready message`() {
        assertEquals(
            """{"type":"herald.ready","subscriptions":[{"channel":"alerts","topic":"news.breaking"},{"channel":"system","topic":""}],"saved":true,"notifications":"granted"}""",
            encodeBridgeReply(
                BridgeReply.ready(listOf(Subscription("alerts", "news.breaking"), Subscription("system")), saved = true, notificationsEnabled = true),
            ),
        )
    }

    @Test
    fun `encodes results`() {
        assertEquals(
            """{"type":"herald.subscriptions.result","id":"r1","ok":true}""",
            encodeBridgeReply(BridgeReply.result("r1")),
        )
        assertEquals(
            """{"type":"herald.subscriptions.result","id":"r1","ok":true,"subscriptions":[{"channel":"alerts","topic":""}]}""",
            encodeBridgeReply(BridgeReply.result("r1", subscriptions = listOf(Subscription("alerts")))),
        )
        assertEquals(
            """{"type":"herald.subscriptions.result","id":"r1","ok":false,"error":"notifications_denied"}""",
            encodeBridgeReply(BridgeReply.failure("r1", BridgeError.NOTIFICATIONS_DENIED)),
        )
    }
}

