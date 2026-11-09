package com.example.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.example.data.model.TodayItem
import com.example.ui.components.glass
import com.example.ui.components.todayGlassCard
import com.example.ui.theme.DeskColors
import com.example.ui.theme.DeskShapes
import com.example.ui.theme.DeskSpace
import com.example.ui.theme.DeskType
import kotlin.math.absoluteValue

/*
 * Today's decks (Alex, 2026-11-03): trivia and "on this day" came as a new card every day and took over Today (14 of
 * its 20 cards were the daily extras). "One for trivia, one for on this day": each is a small fanned deck in the feed,
 * and tapping it opens a focused view (the feed blurred behind) to swipe through them, newest first. What's waiting
 * shows on the deck itself (trivia not answered yet, an event not opened yet).
 */

/** Which daily cards a deck holds. */
internal enum class Deck(val label: String, val accent: Color) {
    Trivia("Trivia", DeskColors.Agent),
    OnThisDay("On this day", DeskColors.Emerald);

    fun holds(item: TodayItem): Boolean = when (this) {
        Trivia -> item.card == "trivia"
        OnThisDay -> item.card == "story" && item.site.endsWith("on this day")
    }

    /** "2 to answer" / "1 new": what's waiting in it, or null when nothing is. */
    fun waiting(items: List<TodayItem>): String? = when (this) {
        Trivia -> items.count { it.state !in listOf("right", "wrong") }.takeIf { it > 0 }?.let { "$it to answer" }
        OnThisDay -> items.count { it.state == "new" }.takeIf { it > 0 }?.let { "$it new" }
    }
}

/**
 * A deck as it sits in the feed: small (DECK_HEIGHT), up to two cards fanned out behind the top one to the right only,
 * turned from their bottom-left corner like a hand of cards (Alex, 2026-11-03: "smaller and no fan out both sides ...
 * only fan out one way"). The fan stays inside the deck's own width (FAN_ROOM), so nothing pokes off the screen.
 * onOpen gets the deck's centre on the screen: the opened card flies out from there.
 */
@Composable
internal fun FannedDeck(deck: Deck, items: List<TodayItem>, modifier: Modifier = Modifier, onOpen: (Offset) -> Unit) {
    val top = items.firstOrNull() ?: return
    val waiting = deck.waiting(items)
    var centre by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.height(DECK_HEIGHT)
            .onGloballyPositioned { centre = it.boundsInRoot().center }
            .clickable { onOpen(centre) }
    ) {
        // The cards behind: each a little further round to the right and up (turning the other way swung their
        // corners down into the next card, first preview), lit at the edge so the deck reads as a deck
        listOf(-4f to 8.dp, -2f to 4.dp).take((items.size - 1).coerceIn(0, 2)).forEach { (angle, step) ->
            Box(
                Modifier.matchParentSize().padding(top = FAN_HEADROOM, end = FAN_ROOM)
                    .graphicsLayer {
                        rotationZ = angle
                        translationX = step.toPx()
                        transformOrigin = TransformOrigin(0f, 1f)
                    }
                    .clip(DeskShapes.todayCard)
                    .background(DeskColors.TodayGlassFill)
                    .border(1.dp, DeskColors.TextMuted.copy(alpha = 0.45f), DeskShapes.todayCard)
            )
        }
        Column(Modifier.matchParentSize().padding(top = FAN_HEADROOM, end = FAN_ROOM).todayGlassCard().padding(DeskSpace.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(deck.label.uppercase(), style = DeskType.overline, color = deck.accent, modifier = Modifier.weight(1f))
                if (waiting != null) {
                    // Waiting on him: a lit pill, not a plain count (Alex: "a clear indication that it hasn't been done")
                    Row(
                        Modifier.clip(DeskShapes.pill).background(deck.accent.copy(alpha = 0.18f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(deck.accent))
                        Spacer(Modifier.width(5.dp))
                        Text(waiting, style = DeskType.caption, color = deck.accent)
                    }
                }
            }
            Spacer(Modifier.height(DeskSpace.xs))
            // On this day: the event is the interesting part ("1996 · Abdullah Çatlı, a leader of…"), not its title
            Text(
                if (deck == Deck.OnThisDay) top.title.removePrefix("On this day in ") + " · " + top.body else top.title,
                style = DeskType.label, color = DeskColors.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text("${items.size} ${if (items.size == 1) "card" else "cards"}", style = DeskType.caption,
                color = DeskColors.TextMuted)
        }
    }
}

internal val DECK_HEIGHT = 120.dp
private const val OPEN_MS = 380
private val FAN_ROOM = 14.dp            // the deck's right edge kept free for the fanned cards
private val FAN_HEADROOM = 12.dp        // and room above, where the turned cards' right corners rise

/** Trivia and "on this day" side by side, each half the width (a lone deck takes the full width). */
@Composable
internal fun DeckRow(decks: List<Pair<Deck, List<TodayItem>>>, onOpen: (Deck, Offset) -> Unit) {
    val shown = decks.filter { it.second.isNotEmpty() }
    if (shown.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(DeskSpace.m)) {
        shown.forEach { (deck, items) -> FannedDeck(deck, items, Modifier.weight(1f)) { onOpen(deck, it) } }
    }
}

/**
 * The focused view: drawn over Today (whose content the caller blurs), not in a Dialog (a Dialog never got the
 * keyboard's size on the owner's phone, 2026-10-06, and can't blur what's behind it here). Swipe between the cards,
 * newest first; tap outside a card, or the ✕, to close. `card` draws one card (the real Trivia or story card).
 */
@Composable
internal fun DeckViewer(
    deck: Deck, items: List<TodayItem>, onClose: () -> Unit, startAt: Int = 0,
    origin: Offset? = null,                       // the deck's centre on screen: the card flies out from there
    previewProgress: Float? = null,               // screenshots only: a frame of the opening, 0 (deck) to 1 (open)
    onShown: (TodayItem) -> Unit = {},            // each card as it comes into view ("seen": a deck's "1 new" clears)
    card: @Composable BoxScope.(TodayItem) -> Unit
) {
    val pager = rememberPagerState(initialPage = startAt.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    val noRipple = remember { MutableInteractionSource() }
    val scope = rememberCoroutineScope()
    // Opening: out of the deck to the centre of the screen (Alex: "animate open to the center of the page"); closing
    // plays it backwards before the viewer goes
    val progress = remember { Animatable(previewProgress ?: 0f) }
    LaunchedEffect(Unit) { if (previewProgress == null) progress.animateTo(1f, tween(OPEN_MS, easing = FastOutSlowInEasing)) }
    val close: () -> Unit = {
        scope.launch {
            progress.animateTo(0f, tween(OPEN_MS * 3 / 4, easing = FastOutSlowInEasing))
            onClose()
        }
    }
    BackHandler(onBack = close)                   // the back gesture closes it the same way
    LaunchedEffect(pager.currentPage) { items.getOrNull(pager.currentPage)?.let(onShown) }
    var size by remember { mutableStateOf(Offset.Zero) }
    val p = progress.value
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { size = Offset(it.size.width.toFloat(), it.size.height.toFloat()) }
            .background(Color.Black.copy(alpha = 0.55f * p))
            .clickable(interactionSource = noRipple, indication = null, onClick = close)
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(vertical = DeskSpace.l)
                .graphicsLayer {
                    // From the deck (small, where it sits) to here (full size, centred)
                    val from = origin ?: (size / 2f)
                    translationX = (from.x - size.x / 2f) * (1f - p)
                    translationY = (from.y - size.y / 2f) * (1f - p)
                    val s = lerp(0.3f, 1f, p)
                    scaleX = s; scaleY = s
                    alpha = (p * 1.6f).coerceAtMost(1f)
                }
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = DeskSpace.xl), verticalAlignment = Alignment.CenterVertically) {
                Text(deck.label.uppercase(), style = DeskType.overline, color = deck.accent)
                Spacer(Modifier.width(DeskSpace.s))
                deck.waiting(items)?.let { Text("· $it", style = DeskType.caption, color = DeskColors.TextSecondary) }
                Spacer(Modifier.weight(1f))
                Text("${pager.currentPage + 1} of ${items.size}", style = DeskType.mono, color = DeskColors.TextSecondary)
                Spacer(Modifier.width(DeskSpace.m))
                Box(
                    Modifier.size(36.dp).glass(CircleShape, DeskColors.GlassStrong).clickable(onClick = close),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Close, contentDescription = "Close", tint = DeskColors.TextPrimary, modifier = Modifier.size(18.dp)) }
            }
            Spacer(Modifier.height(DeskSpace.l))
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = DeskSpace.m,
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) { page ->
                // The neighbours sit a little smaller and dimmer, so it reads as a deck being swiped
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                // Centred in the page (a long card scrolls); a tap on the card itself isn't "close"
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth()
                            .graphicsLayer {
                                val s = lerp(1f, 0.92f, offset)
                                scaleX = s; scaleY = s
                                alpha = lerp(1f, 0.55f, offset)
                            }
                            .clickable(interactionSource = noRipple, indication = null) {}
                            .verticalScroll(rememberScrollState()),
                        contentAlignment = Alignment.Center
                    ) { card(items[page]) }
                }
            }
            Spacer(Modifier.height(DeskSpace.m))
            // Where you are in the deck
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                items.indices.take(12).forEach { i ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                            .background(if (i == pager.currentPage) deck.accent else DeskColors.TextMuted.copy(alpha = 0.5f))
                    )
                }
            }
        }
    }
}
