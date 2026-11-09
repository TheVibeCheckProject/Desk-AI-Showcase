package com.example.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MailWatch
import com.example.data.model.WatchList
import com.example.ui.components.EmptyState
import com.example.ui.components.LoadingState
import com.example.ui.components.StatusNote
import com.example.ui.components.StatusPill
import com.example.ui.components.deskCard
import com.example.ui.theme.AmberPending
import com.example.ui.theme.DeepCard
import com.example.ui.theme.DeepPanel
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.EmeraldConnected
import com.example.ui.theme.RoseError
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate300
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/*
 * The Watches tab, beside Schedules in the same sheet (Alex, 2026-11-03: "a watches viewer, and a way to add a
 * watch manually ... under the schedules card"). Since build 108 a watch is anything ("watches should be for anything
 * I want"): email, a web page, a web search, the calendar (the agent's /api/watches; plain checks, an AI check on a
 * page only if asked). He's told in chat, with a push. Changing a watch stays in chat.
 */

/** Amber: watches are about mail arriving, apart from the schedules' cyan. */
private val WatchAccent = AmberPending

private fun when_(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(iso.take(19))
        SimpleDateFormat("EEE, MMM d HH:mm", Locale.getDefault()).format(parsed!!)
    } catch (_: Exception) {
        iso.take(16).replace('T', ' ')
    }
}

private fun mailbox(source: String) = when (source) {
    "gmail" -> "Gmail"
    "zoho" -> "Zoho"
    else -> "Gmail + Zoho"
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "page" -> Icons.Default.Public
    "search" -> Icons.Default.Search
    "calendar" -> Icons.Default.Event
    else -> Icons.Default.Email
}

/** The card's title after "Watch #N": the mailbox, the site, the search, or the calendar. */
private fun title(w: MailWatch): String = when (w.kind) {
    "page" -> (w.target ?: "a web page").removePrefix("https://").removePrefix("http://").removePrefix("www.")
    "search" -> "Search: ${w.target ?: ""}"
    "calendar" -> "Calendar"
    else -> mailbox(w.source)
}

@Composable
fun WatchesBody(
    modifier: Modifier,
    showForm: Boolean,
    onShowForm: (Boolean) -> Unit,
    refreshKey: Int,
    onFetch: suspend () -> Result<WatchList>,
    onCreate: suspend (JSONObject) -> Result<MailWatch>,
    onStop: suspend (Int) -> Result<Unit>
) {
    val scope = rememberCoroutineScope()
    var watches by remember { mutableStateOf<List<MailWatch>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        onFetch().onSuccess { watches = it.watches }
            .onFailure { status = "⚠️ ${it.message}" to false }
        isLoading = false
    }

    Column(modifier = modifier.fillMaxWidth()) {
        status?.let { (msg, ok) ->
            StatusNote(msg, modifier = Modifier.fillMaxWidth(), color = if (ok) EmeraldConnected else RoseError)
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (showForm) {
            WatchForm(
                modifier = Modifier.weight(1f),
                onCancel = { onShowForm(false) },
                onSave = { fields ->
                    val res = onCreate(fields)
                    res.onSuccess {
                        watches = watches + it
                        status = "✅ Watch #${it.id} set: ${it.description}" to true
                        onShowForm(false)
                    }
                    res.exceptionOrNull()?.message
                }
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading && watches.isEmpty() -> LoadingState("Loading watches…", color = WatchAccent)
                    watches.isEmpty() -> EmptyState(
                        icon = Icons.Default.Email,
                        title = "No watches yet",
                        message = "Watch email, a web page, a search or your calendar. Tap New watch below, or ask " +
                                "in chat, like \"tell me when those boots are back in stock\" or \"keep an eye on " +
                                "my building permit\".",
                        accent = WatchAccent
                    )
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(watches, key = { it.id }) { w ->
                            WatchCard(w, onStop = {
                                scope.launch {
                                    onStop(w.id)
                                        .onSuccess { watches = watches.filter { it.id != w.id }; status = "🛑 Stopped watch #${w.id}" to true }
                                        .onFailure { status = "⚠️ ${it.message}" to false }
                                }
                            })
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(DeskShapes.control)
                    .background(Brush.horizontalGradient(listOf(WatchAccent, Color(0xFFFFC857))))
                    .clickable { status = null; onShowForm(true) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NEW WATCH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun WatchCard(w: MailWatch, onStop: () -> Unit) {
    var confirmStop by remember(w.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .deskCard(borderColor = if (w.lastError != null) RoseError.copy(alpha = 0.45f) else WatchAccent.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(30.dp).clip(CircleShape).background(WatchAccent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(kindIcon(w.kind), contentDescription = w.kind, tint = WatchAccent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                // Two lines: a search's words or a page's address are what the watch is (the description doesn't repeat them)
                Text("Watch #${w.id} · ${title(w)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(w.description.substringAfter(", ", w.description).replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp, color = WatchAccent, maxLines = 3)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val verb = if (w.kind == "mail") "Found" else "Told"
            StatusPill("$verb ${w.matches}", if (w.matches > 0) EmeraldConnected else Slate400, showDot = false)
            if (w.once) StatusPill("Once", Slate300, showDot = false)
            if (w.deadline != null) StatusPill("Follow-up until ${when_(w.deadline)}", WatchAccent, showDot = false)
        }
        w.note?.let { Text("Waiting on: $it", fontSize = 11.sp, color = Slate300) }
        Text("Last check: ${when_(w.lastCheck)}" + (w.lastMatch?.let { " · last found ${when_(it)}" } ?: ""),
            fontSize = 10.sp, color = Slate500, fontFamily = com.example.ui.theme.JetBrainsMono, maxLines = 2)
        w.lastError?.let { Text("⚠️ Last check failed: $it", fontSize = 11.sp, color = RoseError, maxLines = 3) }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { if (confirmStop) onStop() else confirmStop = true }) {
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = RoseError, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (confirmStop) "Tap again to stop" else "Stop", fontSize = 11.sp, color = RoseError)
            }
        }
    }
}

@Composable
internal fun WatchForm(modifier: Modifier, onCancel: () -> Unit, onSave: suspend (JSONObject) -> String?,
                       initialKind: String = "mail") {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(initialKind) }
    var source by remember { mutableStateOf("zoho") }
    var everyEmail by remember { mutableStateOf(false) }
    var sender by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var words by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var gone by remember { mutableStateOf("") }
    var ask by remember { mutableStateOf("") }
    var every by remember { mutableStateOf(0) }           // 0: the kind's default
    var lead by remember { mutableStateOf(30) }
    var once by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canSave = !saving && when (kind) {
        "mail" -> everyEmail || sender.isNotBlank() || subject.isNotBlank() || words.isNotBlank()
        "page", "search" -> target.isNotBlank()
        else -> true                                          // calendar: every event if no words
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = DeepCard, unfocusedContainerColor = DeepPanel,
        focusedBorderColor = WatchAccent, unfocusedBorderColor = Slate800,
        focusedTextColor = Color.White, unfocusedTextColor = Slate200
    )

    @Composable
    fun label(text: String) = Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Slate400, letterSpacing = 0.8.sp)

    @Composable
    fun chip(text: String, selected: Boolean, onClick: () -> Unit) {
        Text(
            text = text, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (selected) Color.Black else Slate300,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (selected) WatchAccent else Slate800)
                .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }

    @Composable
    fun toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Slate800)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                Text(detail, fontSize = 10.sp, color = if (checked) WatchAccent else Slate400)
            }
            Switch(checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = WatchAccent, checkedThumbColor = Color.White))
        }
    }

    @Composable
    fun field(value: String, onChange: (String) -> Unit, hint: String, max: Int = 120) = OutlinedTextField(
        value = value, onValueChange = { onChange(it.take(max)) }, colors = fieldColors, singleLine = true,
        placeholder = { Text(hint, color = Slate500, fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)
    )

    @Composable
    fun choices(options: List<Pair<String, Int>>, value: Int, onPick: (Int) -> Unit) =
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (name, v) -> chip(name, value == v) { onPick(v) } }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            label("WHAT TO WATCH")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mail" to "Email", "page" to "Web page", "search" to "Search", "calendar" to "Calendar")
                    .forEach { (k, name) -> chip(name, kind == k) { kind = k; every = 0; error = null } }
            }
            when (kind) {
                "mail" -> {
                    label("MAILBOX")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chip("Zoho", source == "zoho") { source = "zoho" }
                        chip("Gmail", source == "gmail") { source = "gmail" }
                        chip("Both", source == "both") { source = "both" }
                    }
                    toggle("Every new email",
                        if (everyEmail) "Everything new in the inbox; newsletters, promos and automated mail skipped"
                        else "Only mail that matches what you type below",
                        everyEmail) { everyEmail = it }
                    label(if (everyEmail) "NARROW IT (OPTIONAL)" else "MATCH ON (AT LEAST ONE)")
                    field(sender, { sender = it }, "From: part of the address or name, e.g. pinterest", 80)
                    field(subject, { subject = it }, "Subject contains…", 80)
                    field(words, { words = it }, "Words in the subject or preview (all of them)")
                }
                "page" -> {
                    label("THE PAGE")
                    field(target, { target = it }, "Web address, e.g. shop.com/boots or a permit status page", 500)
                    label("TELL ME WHEN (OPTIONAL; NEITHER: ANY REAL CHANGE)")
                    field(words, { words = it }, "This appears, e.g. In stock")
                    field(gone, { gone = it }, "This disappears, e.g. Sold out")
                    label("ONLY IF (OPTIONAL, A SMALL AI CHECKS EACH CHANGE)")
                    field(ask, { ask = it }, "e.g. the price drops under \$50")
                    label("HOW OFTEN")
                    choices(listOf("15 min" to 15, "Hourly" to 0, "6 h" to 360, "Daily" to 1440), every) { every = it }
                }
                "search" -> {
                    label("SEARCH FOR")
                    field(target, { target = it }, "e.g. Diablo 2 ladder season, or a person's name", 300)
                    Text("Tells you when a new result (past week) shows up.", fontSize = 11.sp, color = Slate400)
                    label("HOW OFTEN")
                    choices(listOf("Hourly" to 60, "6 h" to 0, "Daily" to 1440), every) { every = it }
                }
                else -> {
                    label("EVENTS (OPTIONAL)")
                    field(words, { words = it }, "Only events with this in the title, e.g. inspection")
                    label("TELL ME BEFORE")
                    choices(listOf("10 min" to 10, "30 min" to 30, "1 h" to 60, "1 day" to 1440), lead) { lead = it }
                }
            }
            toggle("Only once", if (once) "Stops after the first time" else "Tells you every time", once) { once = it }
            Text(if (kind == "calendar") "Reads your Google Calendar." else "Only what happens after you save it counts.",
                fontSize = 11.sp, color = Slate400)
            error?.let { Text("⚠️ $it", fontSize = 11.sp, color = RoseError) }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Slate800).clickable(onClick = onCancel)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("CANCEL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Slate400) }
            Box(
                modifier = Modifier.weight(2f).clip(RoundedCornerShape(10.dp))
                    .background(if (canSave) WatchAccent else Slate700)
                    .clickable(enabled = canSave) {
                        scope.launch {
                            saving = true
                            error = null
                            val fields = JSONObject().put("kind", kind).put("once", once)
                            when (kind) {
                                "mail" -> fields.put("source", source).put("every_email", everyEmail)
                                    .put("sender", sender.trim()).put("subject", subject.trim()).put("words", words.trim())
                                "page" -> fields.put("target", target.trim()).put("words", words.trim())
                                    .put("gone", gone.trim()).put("ask", ask.trim())
                                "search" -> fields.put("target", target.trim())
                                else -> fields.put("words", words.trim()).put("lead_minutes", lead)
                            }
                            if (every > 0 && kind in listOf("page", "search")) fields.put("every_minutes", every)
                            error = onSave(fields)
                            saving = false
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("SAVE WATCH", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (canSave) Color.Black else Slate400)
            }
        }
    }
}
