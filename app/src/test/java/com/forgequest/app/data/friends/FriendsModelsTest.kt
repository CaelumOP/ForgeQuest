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
                "code": "ABC234",
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
        assertEquals("ABC234", view.me.code)
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

        assertEquals(listOf(Person("u4", "Noor", null)), view.incoming)
        assertEquals(listOf(Person("u5", "Sami", "https://example.com/u5.jpg")), view.outgoing)
        assertEquals(listOf(Nudge("u2", "Omar", 20500L)), view.nudges)
    }

    @Test
    fun decodeAuthGoogle() {
        val payload = """
            {
              "session": "tok_abc123",
              "user": {
                "id": "u1",
                "code": "XYZ789",
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
        assertEquals("XYZ789", auth.user.code)
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
}
