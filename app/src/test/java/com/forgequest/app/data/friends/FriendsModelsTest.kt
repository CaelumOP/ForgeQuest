package com.forgequest.app.data.friends

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FriendsModelsTest {
    private val json = FriendsApi.json

    @Test
    fun decodeFriendsView() {
        val payload = """
            {
              "me": {
                "id": "u1",
                "username": "tariq_99",
                "name": "Tariq",
                "photoUrl": "https://example.com/u1.jpg",
                "sharePhoto": true,
                "sharePrs": true,
                "joined": true
              },
              "today": 20500,
              "weekStart": 20497,
              "friends": [
                {
                  "id": "u2",
                  "name": "Omar",
                  "photoUrl": null,
                  "snapshot": {
                    "weekStart": 20497,
                    "done": 3,
                    "due": 4,
                    "score": 75,
                    "badge": "on_track",
                    "streakWeeks": 5,
                    "proteinDays": 4,
                    "prs": [
                      { "exercise": "Bench Press", "kg": 100.5, "reps": 5 }
                    ],
                    "updatedAt": 1700000123
                  }
                },
                {
                  "id": "u3",
                  "name": "Zaid",
                  "photoUrl": "https://example.com/u3.jpg",
                  "snapshot": null
                }
              ],
              "incoming": [
                { "id": "u4", "name": "Noor", "photoUrl": null }
              ],
              "outgoing": [
                { "id": "u5", "name": "Sami", "photoUrl": "https://example.com/u5.jpg" }
              ],
              "nudges": [
                { "fromId": "u2", "name": "Omar", "day": 20500 }
              ]
            }
        """.trimIndent()

        val view = json.decodeFromString(FriendsView.serializer(), payload)
        assertEquals("u1", view.me.id)
        assertEquals("tariq_99", view.me.username)
        assertTrue(view.me.joined)
        assertEquals(20500L, view.today)
        assertEquals(20497L, view.weekStart)

        assertEquals(2, view.friends.size)
        val omar = view.friends[0]
        assertEquals("Omar", omar.name)
        assertNull(omar.photoUrl)
        val snap = omar.snapshot!!
        assertEquals(75, snap.score)
        assertEquals("on_track", snap.badge)
        assertEquals(5, snap.streakWeeks)
        assertEquals(4, snap.proteinDays)
        assertEquals(1700000123L, snap.updatedAt)
        assertEquals(1, snap.prs!!.size)
        assertEquals("Bench Press", snap.prs!![0].exercise)
        assertEquals(100.5, snap.prs!![0].kg, 0.001)
        assertEquals(5, snap.prs!![0].reps)

        val zaid = view.friends[1]
        assertEquals("Zaid", zaid.name)
        assertNull(zaid.snapshot)

        assertEquals(listOf(Person("u4", name = "Noor", photoUrl = null)), view.incoming)
        assertEquals(listOf(Person("u5", name = "Sami", photoUrl = "https://example.com/u5.jpg")), view.outgoing)
        assertEquals(listOf(Nudge("u2", "Omar", day = 20500L)), view.nudges)
    }

    @Test
    fun decodeAuthGoogle() {
        val payload = """
            {
              "session": "tok_abc123",
              "user": {
                "id": "u1",
                "username": null,
                "name": "",
                "photoUrl": null,
                "sharePhoto": true,
                "sharePrs": false,
                "joined": false
              }
            }
        """.trimIndent()

        val auth = json.decodeFromString(AuthResponse.serializer(), payload)
        assertEquals("tok_abc123", auth.session)
        assertEquals("u1", auth.user.id)
        assertEquals(null, auth.user.username)
        assertNull(auth.user.photoUrl)
        assertTrue(auth.user.sharePhoto)
        assertFalse(auth.user.sharePrs)
        assertFalse(auth.user.joined)
    }

    @Test
    fun encodeSnapshotExplicitNullPrs() {
        val snapshot = Snapshot(
            weekStart = 20497L,
            done = 2,
            due = 2,
            score = 100,
            badge = "trained",
            streakWeeks = 3,
            proteinDays = 2,
            prs = null,
        )
        val encoded = json.encodeToString(Snapshot.serializer(), snapshot)
        assertTrue(encoded.contains("\"prs\":null"))

        val obj = json.parseToJsonElement(encoded).jsonObject
        val expectedKeys = setOf("weekStart", "done", "due", "score", "badge", "streakWeeks", "proteinDays", "prs")
        assertTrue(obj.keys.containsAll(expectedKeys))
        assertEquals("trained", obj["badge"]!!.jsonPrimitive.content)
    }

    @Test
    fun cached40ViewDecodesWithV4Defaults() {
        // Exact shape a 4.0 client cached: no inbox, no muted, no v4 flags, old display names.
        val cached = """
            {"me":{"id":"u1","username":"tariq_99","name":"Tariq Old","photoUrl":null,"sharePhoto":true,"sharePrs":false,"joined":true},
             "today":20500,"weekStart":20497,
             "friends":[{"id":"u2","username":"omar","name":"Omar A","photoUrl":null,"snapshot":null,"sharedStreak":3}],
             "incoming":[{"id":"u3","username":"karim","name":"Karim","photoUrl":null}],
             "outgoing":[],
             "nudges":[{"fromId":"u2","name":"Omar A","username":"omar","day":20500}]}
        """.trimIndent()
        val view = json.decodeFromString(FriendsView.serializer(), cached)
        assertTrue(view.me.alertsOn)
        assertTrue(view.me.shareWorkouts)
        assertTrue(view.me.pingsOn)
        assertFalse(view.friends.single().muted)
        assertTrue(view.inbox.pings.isEmpty())
        assertTrue(view.inbox.activity.isEmpty())
        // One name only: the username wins over an old display name.
        assertEquals("tariq_99", view.me.shownName)
        assertEquals("omar", view.friends.single().shownName)
        // Badge = incoming requests + unseen pings (none in a 4.0 cache).
        assertEquals(1, view.badgeCount)
    }

    @Test
    fun decodeV4InboxAndBadge() {
        val payload = """
            {"me":{"id":"u1","username":"tariq","name":"tariq","sharePhoto":true,"sharePrs":true,"joined":true,
                   "alertsOn":false,"shareWorkouts":true,"pingsOn":false},
             "today":20500,"weekStart":20497,
             "friends":[{"id":"u2","username":"omar","name":"omar","snapshot":null,"sharedStreak":0,"muted":true}],
             "incoming":[],"outgoing":[],"nudges":[],
             "inbox":{"pings":[{"fromId":"u2","name":"omar","username":"omar","day":20500,"createdAt":1,"seen":false},
                               {"fromId":"u2","name":"omar","username":"omar","day":20499,"createdAt":0,"seen":true}],
                      "activity":[{"userId":"u2","name":"omar","username":"omar","at":5}]}}
        """.trimIndent()
        val view = json.decodeFromString(FriendsView.serializer(), payload)
        assertFalse(view.me.alertsOn)
        assertFalse(view.me.pingsOn)
        assertTrue(view.friends.single().muted)
        assertEquals(2, view.inbox.pings.size)
        assertEquals(1, view.inbox.activity.size)
        // Activity never counts toward the badge; only the one unseen ping does.
        assertEquals(1, view.badgeCount)
    }
}
