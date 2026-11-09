package com.example.data.remote

import com.example.data.model.ActivityState
import com.example.data.model.InboxMessage
import com.example.data.model.MemoryFactItem
import com.example.data.model.OpenLoopItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProactiveParsingTest {

    private val client = AlwaysOnAgentClient()

    @Test
    fun parseWatch_readsTheAgentsWatch() {
        // As GET /api/watches sends it (2026-11-03); "null" strings and missing fields become nulls
        val w = client.parseWatch(JSONObject("""
            {"id": 5, "source": "zoho", "every_email": true, "sender": "", "subject": "", "words": "", "once": false,
             "active": true, "matches": 3, "description": "Zoho Mail, every new inbox email, every time",
             "last_check": "2026-11-03T00:47:14-06:00", "last_match": null, "last_error": "null", "deadline": null}
        """.trimIndent()))
        assertEquals(5, w.id)
        assertTrue(w.everyEmail)
        assertEquals(3, w.matches)
        assertEquals("Zoho Mail, every new inbox email, every time", w.description)
        assertNull(w.lastMatch)
        assertNull(w.lastError)
        assertNull(w.note)
        assertEquals("2026-11-03T00:47:14-06:00", w.lastCheck)
    }

    @Test
    fun parseActivityJson_activeState() {
        val json = JSONObject("""
            {
                "active": true,
                "state": "step",
                "label": "Away shift",
                "step": "researching AI opportunities",
                "started_at": 1728860000.0,
                "finished_at": null,
                "found": 4
            }
        """.trimIndent())

        val state = client.parseActivityJson(json)
        assertTrue(state.active)
        assertEquals("step", state.state)
        assertEquals("Away shift", state.label)
        assertEquals("researching AI opportunities", state.step)
        assertEquals(1728860000.0, state.startedAt ?: 0.0, 0.001)
        assertNull(state.finishedAt)
        assertEquals(4, state.found)
    }

    @Test
    fun parseActivityJson_idleState() {
        val json = JSONObject("""
            {
                "active": false,
                "state": "finished",
                "label": "",
                "step": "",
                "started_at": 1728860000.0,
                "finished_at": 1728861200.0,
                "found": 2
            }
        """.trimIndent())

        val state = client.parseActivityJson(json)
        assertFalse(state.active)
        assertEquals("finished", state.state)
        assertEquals("", state.label)
        assertEquals(1728861200.0, state.finishedAt ?: 0.0, 0.001)
        assertEquals(2, state.found)
    }

    @Test
    fun parseInboxMessage_withWhyAndFeedback() {
        val m = JSONObject("""
            {
                "id": 42,
                "kind": "question",
                "text": "How did the golf outing at North Hills go?",
                "created_at": "2026-10-13T14:30:00",
                "why": "Follow-up on your golf schedule from Tuesday",
                "feedback": "thumbs_up"
            }
        """.trimIndent())

        val msg = InboxMessage(
            id = m.optLong("id"),
            kind = m.optString("kind", "notice"),
            text = m.optString("text", ""),
            createdAt = m.optString("created_at", ""),
            canvasJson = m.optJSONObject("canvas")?.toString(),
            why = m.optString("why", "").takeIf { it.isNotBlank() && it != "null" },
            feedback = m.optString("feedback", "").takeIf { it.isNotBlank() && it != "null" }
        )

        assertEquals(42L, msg.id)
        assertEquals("question", msg.kind)
        assertEquals("How did the golf outing at North Hills go?", msg.text)
        assertEquals("Follow-up on your golf schedule from Tuesday", msg.why)
        assertEquals("thumbs_up", msg.feedback)
    }

    @Test
    fun parseInboxMessage_omittedWhyAndFeedback() {
        val m = JSONObject("""
            {
                "id": 43,
                "kind": "notice",
                "text": "Backup completed successfully.",
                "created_at": "2026-10-13T15:00:00"
            }
        """.trimIndent())

        val msg = InboxMessage(
            id = m.optLong("id"),
            kind = m.optString("kind", "notice"),
            text = m.optString("text", ""),
            createdAt = m.optString("created_at", ""),
            canvasJson = m.optJSONObject("canvas")?.toString(),
            why = m.optString("why", "").takeIf { it.isNotBlank() && it != "null" },
            feedback = m.optString("feedback", "").takeIf { it.isNotBlank() && it != "null" }
        )

        assertEquals(43L, msg.id)
        assertNull(msg.why)
        assertNull(msg.feedback)
    }

    @Test
    fun parseOpenLoopsAndPastFacts() {
        val loopJson = JSONObject("""
            {
                "fact_id": 105,
                "content": "Waiting on the city's response to the permit application",
                "waiting_on": "City decision on the permit",
                "check_after": "2026-10-15T09:00:00",
                "checked_at": "2026-10-13T08:00:00",
                "asked_at": null
            }
        """.trimIndent())

        val loop = OpenLoopItem(
            factId = loopJson.optInt("fact_id"),
            content = loopJson.optString("content", ""),
            waitingOn = loopJson.optString("waiting_on", ""),
            checkAfter = loopJson.optString("check_after", "").takeIf { it.isNotBlank() && it != "null" },
            checkedAt = loopJson.optString("checked_at", "").takeIf { it.isNotBlank() && it != "null" },
            askedAt = loopJson.optString("asked_at", "").takeIf { it.isNotBlank() && it != "null" }
        )

        assertEquals(105, loop.factId)
        assertEquals("Waiting on the city's response to the permit application", loop.content)
        assertEquals("City decision on the permit", loop.waitingOn)
        assertEquals("2026-10-15T09:00:00", loop.checkAfter)
        assertNull(loop.askedAt)

        val pastJson = JSONObject("""
            {
                "id": 99,
                "content": "Old address in Decatur",
                "category": "personal",
                "importance": 4,
                "pinned": false,
                "updated_at": "2026-09-01T12:00:00",
                "status": "replaced"
            }
        """.trimIndent())

        val past = MemoryFactItem(
            id = pastJson.optInt("id"),
            content = pastJson.optString("content"),
            category = pastJson.optString("category", "fact"),
            importance = pastJson.optInt("importance", 5),
            pinned = pastJson.optBoolean("pinned", false),
            updatedAt = pastJson.optString("updated_at", ""),
            source = pastJson.optString("source", "")
        )

        assertEquals(99, past.id)
        assertEquals("Old address in Decatur", past.content)
        assertEquals("personal", past.category)
    }
}
