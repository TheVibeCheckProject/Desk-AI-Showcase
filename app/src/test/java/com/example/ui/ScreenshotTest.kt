package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.data.model.AgentTaskItem
import com.example.data.model.ChatMessage
import com.example.data.model.DaemonStats
import com.example.ui.components.AgentStatusBar
import com.example.ui.components.ChatInputBar
import com.example.ui.components.AtmosphereBackground
import com.example.ui.components.EmptyState
import com.example.ui.components.MessageBubble
import com.example.ui.components.SectionCard
import com.example.ui.components.SectionLabel
import com.example.ui.components.SheetHeader
import com.example.ui.components.StatusPill
import com.example.ui.components.TagChip
import com.example.ui.components.deskSheet
import com.example.ui.components.phaseColor
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app's key screens as pictures (2026-10-27, docs/archive/FEED_AND_DESIGN_PLAN_2026-10-27.md 4.2): the look gets reviewed
 * like code. `gradle recordRoborazziDebug` writes the PNGs in app/src/test/screenshots; `verifyRoborazziDebug` fails when
 * a change alters one, and the diff image shows where.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun capture(name: String, content: @Composable () -> Unit) {
        // The status bar's beacon pulses forever: run the clock by hand so the test never waits for it to settle
        compose.mainClock.autoAdvance = false
        // Sheets live in a Material surface, which gives their text its color; so does this
        compose.setContent {
            MyApplicationTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurface, content = content)
            }
        }
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun chat() = capture("chat") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            Column(Modifier.fillMaxSize()) {
                // The real top row, as on Chat: menu and new chat, the switch, the tasks pill, quick actions
                com.example.ui.today.UnifiedTopBar(
                    showingToday = false, onSelectTab = {}, onOpenDrawer = {}, tasksInProgress = 1,
                    isRunningTask = true, isConnected = true, onOpenTasks = {}, onOpenQuickActions = {},
                    hasUpdate = true, onNewChat = {}
                )
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    SAMPLE_MESSAGES.forEach { MessageBubble(message = it) }
                    MessageBubble(message = ChatMessage(id = "9", sessionId = "s", role = "user", content = "Thanks!"))
                }
                ChatInputBar(text = "", onTextChange = {}, onSend = {}, isStreaming = false, onStopStreaming = {})
            }
        }
    }

    @Test
    fun sheetParts() = capture("sheet_parts") {
        Column(
            Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground).padding(12.dp)
                .deskSheet().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SheetHeader(title = "Tasks", icon = Icons.Default.Inbox, subtitle = "231 done · 1 running", onClose = {})
            SectionLabel("Running", count = 1)
            SectionCard(accent = phaseColor("in_progress"), onClick = {}) {
                Text("Render tonight's short video and post it at 20:00")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    StatusPill("Running", phaseColor("in_progress"))
                    TagChip("task-232")
                    TagChip("schedule #3", active = true)
                }
            }
            SectionLabel("Done", count = 2)
            SectionCard(accent = phaseColor("completed")) {
                Text("Weekly growth review: 14% more Pinterest impressions")
                Row(Modifier.padding(top = 6.dp)) { StatusPill("Done", phaseColor("completed")) }
            }
            SectionCard(accent = phaseColor("failed")) {
                Text("Post 2 pins to Pinterest (rate limited, retrying at 21:00)")
                Row(Modifier.padding(top = 6.dp)) { StatusPill("Failed", phaseColor("failed")) }
            }
            EmptyState(icon = Icons.Default.Memory, title = "Nothing waiting on you",
                message = "Questions from the agent show up here.", modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun today() {
        // Golden hour, fixed so the picture doesn't depend on when the test runs. The sky places the sun between
        // the weather's sunrise and sunset, so the sample has both (today, on this machine's clock).
        com.example.ui.today.SkyPreview.apply("hour=17.42")      // ~30 min before the sample sunset
        try { todayScreen() } finally { com.example.ui.today.SkyPreview.apply("off") }
    }

    private fun todayScreen() = capture("today") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            com.example.ui.today.WeatherAtmosphere(SAMPLE_TODAY.header.weather, null)
            Column(Modifier.fillMaxSize()) {
                com.example.ui.today.UnifiedTopBar(
                    showingToday = true,
                    onSelectTab = {},
                    onOpenDrawer = {},
                    tasksInProgress = 0,
                    isRunningTask = false,
                    isConnected = true,
                    onOpenTasks = {},
                    onOpenQuickActions = {}
                )
                com.example.ui.today.TodayScreen(
                    state = com.example.ui.today.TodayUiState(page = SAMPLE_TODAY),
                    onRefresh = {}, onReact = { _, _ -> }, onAsk = {}, onAnswer = { _, _ -> true }, onSaveRecipe = { _, _ -> },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Pop-up windows (sheets, the menu) live outside the root view: capture the whole screen for those
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    private fun captureScreen(name: String, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MyApplicationTheme {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides
                        androidx.compose.material3.MaterialTheme.colorScheme.onSurface, content = content)
            }
        }
        compose.mainClock.advanceTimeBy(1200)
        com.github.takahirom.roborazzi.captureScreenRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    fun report() = capture("report") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            Column(Modifier.fillMaxSize()) {
                com.example.ui.today.UnifiedTopBar(
                    showingToday = false, onSelectTab = {}, onOpenDrawer = {}, tasksInProgress = 0,
                    isRunningTask = false, isConnected = true, onOpenTasks = {}, onOpenQuickActions = {},
                    onNewChat = {}
                )
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    MessageBubble(message = ChatMessage(id = "r0", sessionId = "s", role = "user",
                        content = "How did the posts do this week?"))
                    MessageBubble(message = SAMPLE_REPORT_MESSAGE, showHeader = true, onOpenCanvas = {})
                    com.example.ui.components.TaskRow(task = SAMPLE_REPORT_TASK, onClick = {})
                }
                ChatInputBar(text = "", onTextChange = {}, onSend = {}, isStreaming = false, onStopStreaming = {})
            }
        }
    }

    @Test
    fun taskDetails() = captureScreen("task_details") {
        Box(Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground)) {
            com.example.ui.dialogs.TaskDetailsSheet(task = SAMPLE_REPORT_TASK, serverBaseUrl = "", authToken = "",
                onDismiss = {})
        }
    }

    @Test
    fun commandDeck() = captureScreen("command_deck") {
        Box(Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground)) {
            com.example.ui.dialogs.CommandPaletteDialog(
                serverUrl = "https://agent-mini.example.ts.net", isConnected = true, daemonStatus = "idle",
                onOpenSettings = {}, onOpenMedia = {}, onOpenMemory = {}, onOpenTasks = {}, onOpenUpdater = {},
                stats = DaemonStats(tasksCompleted = 231, tasksInProgress = 1, tasksBacklog = 0),
                onFetchRetasks = { Result.success(SAMPLE_RETASKS) },
                onFetchSchedules = { Result.success(SAMPLE_SCHEDULES) },
                onSelectCommandTemplate = {}, onExecuteCommand = {}, onDismiss = {}
            )
        }
    }

    @Test
    fun schedules() = captureScreen("schedules") {
        Box(Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground)) {
            com.example.ui.dialogs.SchedulesSheet(
                onDismiss = {}, onFetch = { Result.success(SAMPLE_SCHEDULES) },
                onPreview = { _, _ -> Result.failure(Exception("not in a test")) },
                onCreate = { Result.failure(Exception("not in a test")) },
                onUpdate = { _, _ -> Result.failure(Exception("not in a test")) },
                onDelete = { Result.failure(Exception("not in a test")) },
                onRunNow = { Result.failure(Exception("not in a test")) },
                onFetchWatches = { Result.success(com.example.data.model.WatchList(SAMPLE_WATCHES, 5)) },
                onCreateWatch = { Result.failure(Exception("not in a test")) },
                onStopWatch = { Result.success(Unit) }
            )
        }
    }

    private fun screen(name: String): ByteArray =
        ScreenshotTest::class.java.getResourceAsStream("/screens/$name.png")!!.readBytes()   // made-up desktops

    @Test
    fun needsYou() = captureScreen("needs_you") {
        Box(Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground)) {
            com.example.ui.dialogs.NeedsYouSheet(
                requests = listOf(com.example.data.model.ComputerRequest(
                    id = "req-12", kind = "secret",
                    question = "Example Mail is asking for the password for agent@example.com. Type it below: it goes " +
                        "straight into the field on the PC and is never saved in chat.",
                    url = "https://mail.example.com/signin", screenshotPath = "/api/computer/requests/req-12/screenshot")),
                onDismiss = {}, onRefresh = {}, onAnswer = { _, _ -> Result.success("ok") },
                onScreenshot = { screen("signin") }, onTakeOver = {}
            )
        }
    }

    @Test
    fun takeOver() = captureScreen("take_over") {
        com.example.ui.dialogs.TakeOverScreen(
            requestId = null, viewOnly = false, onClose = {},
            call = { _, _ -> Result.success(org.json.JSONObject()) },
            fetchFrame = { screen("notepad") },
            fetchActivity = { WATCH_ACTIVITY }
        )
    }

    // View only is made for a phone on its side: the PC's screen fills it, the narration says what's happening
    @Test
    @Config(qualifiers = "+land")
    fun watchViewOnly() = captureScreen("view_only") {
        com.example.ui.dialogs.TakeOverScreen(
            requestId = null, viewOnly = true, onClose = {},
            call = { _, _ -> Result.success(org.json.JSONObject()) },
            fetchFrame = { screen("weather") },
            fetchActivity = { WEATHER_ACTIVITY }
        )
    }

    /** The Watches tab beside Schedules (2026-11-03), with the watches the owner really has. */
    @Test
    fun watches() = capture("watches") {
        Column(
            Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground).padding(12.dp)
                .deskSheet().padding(16.dp)
        ) {
            SheetHeader(title = "Watches", icon = Icons.Default.Email, accent = com.example.ui.theme.AmberPending,
                subtitle = "Tells you when something happens", onClose = {})
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
            com.example.ui.dialogs.ScheduleTabs(1) {}
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
            com.example.ui.dialogs.WatchesBody(
                modifier = Modifier.weight(1f), showForm = false, onShowForm = {}, refreshKey = 0,
                onFetch = { Result.success(com.example.data.model.WatchList(SAMPLE_WATCHES, 5)) },
                onCreate = { Result.failure(Exception("not in a screenshot")) },
                onStop = { Result.success(Unit) }
            )
        }
    }

    // ------------------------------------------------ Today's decks (2026-11-03 preview: trivia, on this day)
    @Composable
    private fun TodayWithDecks(blurred: Boolean) {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            Column(
                Modifier.fillMaxSize().then(if (blurred) Modifier.blur(18.dp) else Modifier).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 70.dp))
                Text("Tuesday, November 3", style = com.example.ui.theme.DeskType.headline,
                    color = com.example.ui.theme.DeskColors.TextPrimary)
                com.example.ui.today.BigCard(DECK_STORY, "", "", { _, _ -> }, {})
                com.example.ui.today.DeckRow(listOf(com.example.ui.today.Deck.Trivia to SAMPLE_TRIVIA,
                    com.example.ui.today.Deck.OnThisDay to SAMPLE_ON_THIS_DAY)) { _, _ -> }
                com.example.ui.today.BigCard(DECK_STORY_2, "", "", { _, _ -> }, {})
            }
        }
    }

    /** Today with its two decks in the feed, instead of a new trivia and "on this day" card every day. */
    @Test
    fun todayDecks() = capture("today_decks") { TodayWithDecks(blurred = false) }

    /** The trivia deck opened: Today blurred behind, swipe between the questions. */
    @Test
    fun triviaDeckOpen() = capture("today_deck_trivia_open") {
        Box(Modifier.fillMaxSize()) {
            TodayWithDecks(blurred = true)
            com.example.ui.today.DeckViewer(com.example.ui.today.Deck.Trivia, SAMPLE_TRIVIA, onClose = {}) { item ->
                com.example.ui.today.TriviaCard(item, right = 3, answered = 4, onReact = { _, _ -> }, showLabel = false)
            }
        }
    }

    /** Partway through opening: the trivia card on its way from the deck (left, mid-screen) to the centre. */
    @Test
    fun triviaDeckOpening() = capture("today_deck_trivia_opening") {
        Box(Modifier.fillMaxSize()) {
            TodayWithDecks(blurred = true)
            com.example.ui.today.DeckViewer(com.example.ui.today.Deck.Trivia, SAMPLE_TRIVIA, onClose = {},
                origin = androidx.compose.ui.geometry.Offset(280f, 1060f), previewProgress = 0.45f) { item ->
                com.example.ui.today.TriviaCard(item, right = 3, answered = 4, onReact = { _, _ -> }, showLabel = false)
            }
        }
    }

    /** The "on this day" deck opened. */
    @Test
    fun onThisDayDeckOpen() = capture("today_deck_onthisday_open") {
        Box(Modifier.fillMaxSize()) {
            TodayWithDecks(blurred = true)
            com.example.ui.today.DeckViewer(com.example.ui.today.Deck.OnThisDay, SAMPLE_ON_THIS_DAY, onClose = {}) { item ->
                com.example.ui.today.BigCard(item, "", "", { _, _ -> }, {}, fullText = true)
            }
        }
    }

    /** The real Today screen given trivia and "on this day" cards: they become the two decks, not feed cards. */
    @Test
    fun todayScreenWithDecks() = capture("today_screen_decks") {
        Box(Modifier.fillMaxSize()) {
            AtmosphereBackground()
            com.example.ui.today.TodayScreen(
                state = com.example.ui.today.TodayUiState(page = SAMPLE_TODAY.copy(
                    items = listOf(DECK_STORY) + SAMPLE_TRIVIA + SAMPLE_ON_THIS_DAY + DECK_STORY_2)),
                onRefresh = {}, onReact = { _, _ -> }, onAsk = {}, onAnswer = { _, _ -> true }, onSaveRecipe = { _, _ -> }
            )
        }
    }

    /** The New watch form with "Web page" picked (build 108: watches for anything). */
    @Test
    fun watchForm() = capture("watch_form") {
        val form = Modifier.fillMaxSize().background(com.example.ui.theme.SlateDarkBackground).padding(12.dp)
            .deskSheet().padding(16.dp)
        Column(form) {
            SheetHeader(title = "New watch", icon = Icons.Default.Email, accent = com.example.ui.theme.AmberPending,
                subtitle = "Tells you when something happens", onClose = {})
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 10.dp))
            com.example.ui.dialogs.WatchForm(Modifier.weight(1f), onCancel = {}, onSave = { null }, initialKind = "page")
        }
    }

    private companion object {
        fun item(id: Long, card: String, title: String, body: String, site: String, state: String = "seen") =
            com.example.data.model.TodayItem(id, card, title, body, "https://example.com/$id", site,
                "Something interesting", state, null)

        fun trivia(id: Long, q: String, answer: String, wrong: List<String>, category: String, state: String) =
            item(id, "trivia", q, org.json.JSONObject().put("answer", answer)
                .put("choices", org.json.JSONArray((wrong + answer).sorted())).put("category", category)
                .put("difficulty", "easy").toString(), "Trivia", state)

        // The real ones from Oct 30 - Nov 3; the two newest not answered yet
        val SAMPLE_TRIVIA = listOf(
            trivia(51, "Who was not in the band \"The Smiths\"?", "Ian Curtis",
                listOf("Morrissey", "Johnny Marr", "Andy Rourke"), "Music", "new"),
            trivia(52, "What color is a Steller's Jay?", "Blue", listOf("Red", "Green", "Yellow"), "Animals", "new"),
            trivia(53, "How many playable characters are there in Yakuza 5?", "5", listOf("3", "4", "6"),
                "Video games", "right"),
            trivia(54, "Which planet is the largest?", "Jupiter", listOf("Saturn", "Neptune", "Earth"), "Science", "wrong"),
            trivia(55, "What is the capital of Australia?", "Canberra", listOf("Sydney", "Melbourne", "Perth"),
                "Geography", "right"),
        )
        val SAMPLE_ON_THIS_DAY = listOf(
            item(61, "story", "On this day in 1996", "Abdullah Çatlı, a leader of the ultra-nationalist Grey Wolves, " +
                "was killed in a car crash near Susurluk, Turkey, sparking a scandal that exposed ties between " +
                "the government, the security forces and organized crime.", "Wikipedia · on this day", "new"),
            item(62, "story", "On this day in 1917", "The Balfour Declaration, supporting a national home for the " +
                "Jewish people in Palestine, was issued by the British government.", "Wikipedia · on this day"),
            item(63, "story", "On this day in 1972", "A transistor radio was taken on a test flight.", "Wikipedia · on this day"),
        )
        val DECK_STORY = item(71, "story", "D2R Patch 3.4 – Ethereal Edge, Veil of Steel & Eternity",
            "Blizzard's next ladder season brings changes to unique items and runewords.", "maxroll.gg", "new")
        val DECK_STORY_2 = item(72, "story", "The Major Cut @ The World's Oldest Course",
            "Bob Does Sports posted a new video.", "youtube.com")

        val SAMPLE_WATCHES = listOf(
            com.example.data.model.MailWatch(5, "zoho", true, "", "", "", false, true, 3,
                "Zoho Mail, every new inbox email (newsletters, promotions and automated notifications skipped), every time",
                "2026-11-03T00:47:14-06:00", "2026-11-02T21:10:00-06:00", null, null, null),
            com.example.data.model.MailWatch(8, "page", false, "", "", "", false, true, 0,
                "the web page https://permits.example.gov/springfield/26-01234, when its text " +
                    "changes, every time, checked every 1 h",
                "2026-11-03T01:00:00-06:00", null, null, null, null, kind = "page",
                target = "https://permits.example.gov/springfield/26-01234"),
            com.example.data.model.MailWatch(9, "page", false, "", "", "", true, true, 0,
                "the web page https://shop.example.com/trail-boots, when \"In stock\" appears, if the change is: " +
                    "size 11 is available (an AI check), once, checked every 15 min",
                "2026-11-03T01:00:00-06:00", null, null, null, null, kind = "page",
                target = "https://shop.example.com/trail-boots", ask = "size 11 is available", everyMinutes = 15),
            com.example.data.model.MailWatch(10, "search", false, "", "", "", false, true, 2,
                "new web results for \"Diablo 2 ladder season 16\" (past week), every time, checked every 6 h",
                "2026-11-03T01:00:00-06:00", "2026-11-03T01:00:00-06:00", null, null, null, kind = "search",
                target = "Diablo 2 ladder season 16"),
            com.example.data.model.MailWatch(11, "calendar", false, "", "", "inspection", false, true, 0,
                "Google Calendar, 60 min before events matching \"inspection\", every time",
                "2026-11-03T01:00:00-06:00", null, null, null, null, kind = "calendar", leadMinutes = 60),
            com.example.data.model.MailWatch(7, "both", false, "", "", "invoice paid", false, true, 0,
                "Gmail and Zoho Mail, mentions 'invoice paid', every time",
                "2026-11-03T00:40:00-06:00", null, "Zoho Mail: invalid oauth token", null, null),
        )

        val MIDNIGHT: Long = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
        }.timeInMillis / 1000

        val WEATHER_ACTIVITY = org.json.JSONObject("""{"running": true, "step": 4,
            "goal": "Find today's high temperature in Springfield, IL",
            "doing": "Step 4 · Reading today's high on the forecast",
            "log": [
              {"t": "19:40:52", "step": 1, "text": "Opened Edge in a window of its own", "level": "info", "kind": "open"},
              {"t": "19:40:58", "step": 2, "text": "Typed \"Springfield IL weather\" and pressed Enter", "level": "info", "kind": "type"},
              {"t": "19:41:06", "step": 3, "text": "Clicked the Example Weather forecast for Springfield, IL", "level": "info", "kind": "click"},
              {"t": "19:41:12", "step": 4, "text": "Reading the forecast: \"Today's high 64°F\", the outlined number", "level": "info", "kind": "plan"}
            ]}""")

        val WATCH_ACTIVITY = org.json.JSONObject(
            """{"running": true, "goal": "Open Notepad and write a haiku about the night, then save it",""" +
                """ "doing": "Typing the second line", "step": 4}""")

        // The weekly report as it really arrives (2026-11-01, from the live app), with made-up numbers
        val SAMPLE_REPORT_TASK = AgentTaskItem(
            id = "task-258", title = "Weekly report", prompt = "Put together the numbers for the last 7 days",
            phase = "completed", engine = "weekly_report", priority = "high",
            createdAt = "2026-11-01T18:00:03-06:00", startedAt = "2026-11-01T18:00:03-06:00",
            completedAt = "2026-11-01T18:00:27-06:00", source = "schedule #5 'Sunday numbers'",
            engineUsed = "weekly_report", retryCount = 0, maxRetries = 2, exitCode = 0,
            outputSummary = "📊 Alex's weekly growth review: 31 cards sent (+12), 214 visits, and 12,480 total " +
                "TikTok views (+2,150).\n\nThe full review is in the canvas: \"Growth review: week to Nov 1\".\n\n" +
                "📊 Week of Oct 25 – Nov 1\n\nWebsite\n" +
                "- Visits: 214 (Search 96, Social 71, Direct 47)\n- Top sources: google.com 88, tiktok.com 52\n" +
                "- Cards sent: +12 (31 in total)\n- Daily-email subscribers: 46 (+9)\n\nTikTok\n" +
                "- Views: +2,150 (12,480 in total)\n- New posts: 14, with 2,040 views\n- Followers: 118 (+23)\n" +
                "- Most viewed quote: \"Small steps still move you.\" (412 views)\n\nPinterest\n" +
                "- 640 impressions · 31 clicks to the site · 12 saves\n\nProactive\n" +
                "- 17 sent, 9 answered, 1 👎; 116 ideas not sent (repeats 86, discussed 17, ranker 13)"
        )

        val SAMPLE_REPORT_MESSAGE = ChatMessage(id = "r1", sessionId = "s", role = "assistant",
            modelUsed = "gemini-3.5-flash-lite", linkedTaskId = null,
            content = "Alex's weekly growth review: 31 cards sent (+12), 214 visits, and 12,480 total TikTok views " +
                "(+2,150). Open the draft to read it, or ask me to change anything.",
            canvasJson = """{"id": 7, "title": "Growth review: week to Nov 1", "kind": "document", "version": 1}""")

        val SAMPLE_RETASKS = List(5) { i ->
            com.example.data.model.Retask("r$i", "Open Notepad and save today's notes", "task", 3, i, 0, null, null)
        }

        val SAMPLE_SCHEDULES = com.example.data.model.ScheduleList(listOf(
            com.example.data.model.AgentSchedule(1, "Twice daily short video", "video", "0 8,20 * * *",
                "every day at 08:00 and 20:00", true, "2026-11-01T20:00:00-06:00", "2026-11-01T08:00:00-06:00",
                "started task-256", 64, true, null, null, null),
            com.example.data.model.AgentSchedule(2, "Pinterest pins (twice daily)", "task", "0 12,21 * * *",
                "every day at 12:00 and 21:00", true, "2026-11-01T21:00:00-06:00", "2026-11-01T12:00:00-06:00",
                "started task-257", 40, false, null, "Post 1 pin to Pinterest", null),
            com.example.data.model.AgentSchedule(5, "Sunday numbers", "task", "0 18 * * 0",
                "every Sunday at 18:00", true, "2026-11-08T18:00:00-06:00", "2026-11-01T18:00:00-06:00",
                "started task-258", 6, false, null, "Put together the numbers for the last 7 days", null),
            com.example.data.model.AgentSchedule(4, "Weekly guide", "reminder", "0 10 * * 1",
                "every Monday at 10:00", true, "2026-11-02T10:00:00-06:00", "2026-10-26T10:00:00-06:00",
                "reminder sent to 1 device(s)", 5, false, "Guide day! Write the next website guide.", null, null)
        ), "America/Chicago")

        val SAMPLE_TODAY = com.example.data.model.TodayPage(
            header = com.example.data.model.TodayHeader("2026-10-28",
                com.example.data.model.TodayWeather(58, "Partly cloudy", "Springfield", true, code = 2, cloudCover = 40, windMph = 7.0, windDir = 200, sunrise = MIDNIGHT + 6 * 3600 + 1800, sunset = MIDNIGHT + 17 * 3600 + 54 * 60),
                "It is 07:40 on Wednesday, October 28.\n" +
                    "Mail: GitHub, Google Cloud, the hackathon team\n" +
                    "Today's posts: 08:00 short video, 12:00 Pinterest pins", "Springfield, IL",
                upNext = listOf("08:00" to "Short video", "12:00" to "Pinterest pins"),
                mail = listOf("GitHub" to "Your pull request was merged", "Google Cloud" to "Free tier usage: 12%",
                    "Hackathon team" to "Deadline moved to Nov 14")),
            edition = com.example.data.model.TodayEdition(1, "2026-10-28T07:57:08-05:00", 5, "2026-10-28T13:57:08-05:00"),
            recipe = com.example.data.model.TodayRecipe(text = "More AI research and space news, no crypto.",
                more = listOf("AI research", "space"), never = listOf("crypto"), location = "Springfield, IL"),
            items = listOf(
                com.example.data.model.TodayItem(1, "story", "A small open model now runs agents on a laptop",
                    "A new 4B model scored within 3 points of last year's frontier models on tool-use benchmarks.",
                    "https://news.example.org/small-model", "AI research", "You follow AI research", "new",
                    "https://agent-mini.example.ts.net/api/today/items/1/image"),
                com.example.data.model.TodayItem(2, "story", "Free Gemini credits for indie developers this month",
                    "Google opened a new round of free API credits for small projects, no card needed.",
                    "https://news.example.org/credits", "news.example.org", "You build with free tiers", "saved", null),
                com.example.data.model.TodayItem(3, "settled", "Settled",
                    "The hackathon you were waiting on moved its deadline to Nov 14.", "", "", "", "new", null),
                com.example.data.model.TodayItem(4, "done", "4 finished while you were away",
                    "Post 1 pin to Pinterest ×2\nWeekly growth review\nMemory check: 2 stale facts retired", "", "", "", "new", null),
                com.example.data.model.TodayItem(5, "question", "A question",
                    "Which project do you most want to finish this month?", "", "", "", "new", null)
            )
        )

        val SAMPLE_MESSAGES = listOf(
            ChatMessage(id = "1", sessionId = "s", role = "assistant", inboxKind = "notice",
                why = "you were waiting on the hackathon's new date",
                content = "While you were away I checked on the hackathon you were waiting to hear about: " +
                    "**the deadline moved to Nov 14**, and solo entries are allowed now."),
            ChatMessage(id = "2", sessionId = "s", role = "user",
                content = "Nice! Put it on my calendar and remind me two days before."),
            ChatMessage(id = "3", sessionId = "s", role = "assistant", modelUsed = "gemini-3.5-flash-lite",
                content = "Done, with one thing waiting for your tap:\n\n" +
                    "- **Reminder:** Nov 12 at 9:00\n" +
                    "- **Watching** the hackathon page, I'll tell you if anything changes\n" +
                    "- **Calendar event:** ready below, added when you tap Run",
                proposalsJson = """[{"id":"p1","token":"t1","kind":"calendar_event","state":"pending",""" +
                    """"instruction":"Add to Google Calendar: Hackathon deadline\nWhen: Fri Nov 14, all day"}]"""),
        )
    }
}
