package com.milestone.app.ui.stats

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.R
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.SectionHeader
import com.milestone.app.ui.components.SubjectAvatar
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.DisplayLarge
import com.milestone.app.ui.theme.InverseSurface
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.LabelSmall
import com.milestone.app.ui.theme.OnInverseMuted
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.OutlineText
import com.milestone.app.ui.theme.OutlineVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.TitleLarge
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.util.dayMonthLabel
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.shortLabel
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun StatsScreen(onLogSession: () -> Unit, vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory)) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        TopBar("Progress")
        if (!s.loading && !s.hasData) {
            EmptyState(R.drawable.ic_chart, "No progress yet", "Log a few sessions and your trends, streaks and averages appear here.",
                "Log a session", onLogSession)
            return@Column
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Ticker(s)
            Chart(s)
            RangeTabs(s.range, vm::setRange)
            Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineVariant))
            Tiles(s)
            Watchlist(s)
        }
    }
}

@Composable
private fun Ticker(s: StatsState) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Primary))
            Text("STUDY TIME · " + if (s.range == StatsRange.ALL) "ALL TIME" else "LAST ${s.rangeDays} DAYS",
                style = LabelSmall, color = OnSurfaceVariant)
        }
        Text(formatMinutes(s.total), style = DisplayLarge, color = OnSurface)
        val prev = s.previousTotal
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (prev != null && prev > 0) {
                val pct = (s.total - prev) * 100.0 / prev
                DeltaBadge(pct, "%.1f%%".format(abs(pct)))
                Text("${if (s.total >= prev) "+" else "−"}${formatMinutes(abs(s.total - prev))} vs previous ${s.rangeDays} days",
                    style = LabelMedium, color = OnSurfaceVariant)
            } else {
                Text(if (s.range == StatsRange.ALL) "since your first session" else "No earlier period to compare yet",
                    style = LabelMedium, color = OnSurfaceVariant)
            }
        }
    }
}

/** Red "▲" when up, ink "▼" when down — same convention as the Figma watchlist. */
@Composable
private fun DeltaBadge(change: Double, text: String) {
    val up = change >= 0
    Text(
        (if (up) "▲ " else "▼ ") + text,
        style = LabelMedium, color = Color.White,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(if (up) Primary else InverseSurface)
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = (if (up) "up " else "down ") + text },
    )
}

@Composable
private fun Chart(s: StatsState) {
    val points = s.points
    if (points.isEmpty()) return
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val maxMinutes = points.maxOf { it.minutes }
    // Y axis in whole hours, at least 1h, 4 gridlines like the Figma.
    val topHours = ceil(maxOf(maxMinutes, 60) / 60.0 / 4).toInt().coerceAtLeast(1) * 4
    val bestIndex = points.indices.maxByOrNull { points[it].minutes }?.takeIf { maxMinutes > 0 }
    val left = with(density) { 16.dp.toPx() }
    val right = with(density) { 52.dp.toPx() }
    val top = with(density) { 12.dp.toPx() }
    val bottom = with(density) { 28.dp.toPx() }
    fun xFor(i: Int, w: Float) = left + (w - left - right) * (if (points.size == 1) 0.5f else i / (points.size - 1f))
    fun yFor(m: Int, h: Float) = top + (h - top - bottom) * (1f - m / (topHours * 60f))
    fun indexAt(x: Float) = (((x - left) / (size.width - left - right)) * (points.size - 1)).roundToInt().coerceIn(0, points.lastIndex)

    Box(
        Modifier.fillMaxWidth().height(230.dp).onSizeChanged { size = it }
            .pointerInput(points) { detectTapGestures { selected = indexAt(it.x) } }
            .pointerInput(points) { detectDragGestures(onDragEnd = {}) { change, _ -> selected = indexAt(change.position.x) } }
            .semantics {
                contentDescription = "Study time chart, ${points.size} ${if (s.weekly) "weeks" else "days"}. " +
                    "Best: ${bestIndex?.let { formatMinutes(points[it].minutes) } ?: "none"}. Total ${formatMinutes(s.total)}."
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width.toFloat(); val h = size.height.toFloat()
            for (k in 0..4) {
                val y = top + (h - top - bottom) * k / 4f
                drawLine(OutlineVariant, Offset(left, y), Offset(w - right, y), strokeWidth = 1.dp.toPx())
            }
            val line = Path()
            points.forEachIndexed { i, p ->
                val x = xFor(i, w); val y = yFor(p.minutes, h)
                if (i == 0) line.moveTo(x, y) else line.lineTo(x, y)
            }
            val area = Path().apply {
                addPath(line)
                lineTo(xFor(points.lastIndex, w), yFor(0, h)); lineTo(xFor(0, w), yFor(0, h)); close()
            }
            drawPath(area, Brush.verticalGradient(listOf(Primary.copy(alpha = 0.22f), Primary.copy(alpha = 0f)), top, h - bottom))
            drawPath(line, Primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Latest value marker + dashed guide (Figma "price tag").
            val lx = xFor(points.lastIndex, w); val ly = yFor(points.last().minutes, h)
            if (points.last().minutes > 0) {
                drawLine(Primary.copy(alpha = 0.5f), Offset(left, ly), Offset(w - right, ly), 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            drawCircle(Primary.copy(alpha = 0.2f), 8.dp.toPx(), Offset(lx, ly))
            drawCircle(Primary, 4.dp.toPx(), Offset(lx, ly))
            selected?.let { i ->
                val x = xFor(i, w); val y = yFor(points[i].minutes, h)
                drawLine(OnSurface, Offset(x, top), Offset(x, h - bottom), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
                drawCircle(Color.White, 6.dp.toPx(), Offset(x, y))
                drawCircle(Primary, 4.dp.toPx(), Offset(x, y))
            }
        }
        // Y labels
        for (k in 0..4) {
            val hours = topHours * (4 - k) / 4
            val y = with(density) { (top + (size.height - top - bottom) * k / 4f).toDp() }
            Text("${hours}h", style = LabelSmall, color = OutlineText,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-8).dp, y = y - 8.dp))
        }
        // X labels: first (left-aligned), middle (centred) and last (right-aligned) so none is clipped.
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 16.dp, end = 52.dp, bottom = 4.dp)) {
            Text(points.first().start.dayMonthLabel(), style = LabelSmall, color = OutlineText)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (points.size > 2) Text(points[points.lastIndex / 2].start.dayMonthLabel(), style = LabelSmall, color = OutlineText)
            }
            if (points.size > 1) Text(points.last().start.dayMonthLabel(), style = LabelSmall, color = OutlineText)
        }
        // Tooltip
        selected?.let { i ->
            val p = points[i]
            val xPx = xFor(i, size.width.toFloat())
            Column(
                Modifier.offset { IntOffset((xPx - 64.dp.toPx()).roundToInt().coerceIn(0, (size.width - 136.dp.toPx()).roundToInt().coerceAtLeast(0)), 0) }
                    .clip(RoundedCornerShape(8.dp)).background(InverseSurface).padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(if (s.weekly) "Week of ${p.start.shortLabel()}" else p.start.shortLabel(), style = LabelSmall, color = OnInverseMuted)
                Text(formatMinutes(p.minutes) + if (i == bestIndex) " · Best ${if (s.weekly) "week" else "day"}" else "",
                    style = LabelMedium, color = Color.White)
            }
        }
    }
}

@Composable
private fun RangeTabs(current: StatsRange, onPick: (StatsRange) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).selectableGroup(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatsRange.entries.forEach { r ->
            val sel = r == current
            Text(
                r.label, style = LabelLarge, color = if (sel) Color.White else OnSurfaceVariant,
                modifier = Modifier.minimumInteractiveComponentSize().clip(RoundedCornerShape(999.dp))
                    .background(if (sel) InverseSurface else Color.Transparent)
                    .selectable(sel, role = Role.Tab) { onPick(r) }.padding(horizontal = 12.dp, vertical = 6.dp)
                    .semantics { contentDescription = when (r) { StatsRange.ALL -> "All time"; else -> r.label } },
            )
        }
    }
}

@Composable
private fun Tiles(s: StatsState) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_flame_18, "${s.streak} day${if (s.streak == 1) "" else "s"}", "Current streak",
                "best ${s.bestStreak}", null, Modifier.weight(1f))
            Tile(R.drawable.ic_check_18, "${s.sessions}", "Sessions", delta(s.sessions, s.previousSessions)?.let { "%+d".format(s.sessions - s.previousSessions!!) },
                delta(s.sessions, s.previousSessions), Modifier.weight(1f))
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(R.drawable.ic_chart_18, formatMinutes(s.dailyAverage), "Daily average",
                s.previousDailyAverage?.let { (if (s.dailyAverage >= it) "+" else "−") + formatMinutes(abs(s.dailyAverage - it)) },
                delta(s.dailyAverage, s.previousDailyAverage), Modifier.weight(1f))
            Tile(R.drawable.ic_target_18, if (s.goalWeeks == 0) "–" else "${s.goalWeeksHit} / ${s.goalWeeks} wks", "Weeks at today's goal",
                if (s.goalWeeks == 0) "no full week yet" else null, null, Modifier.weight(1f))
        }
    }
}

private fun delta(now: Int, prev: Int?): Int? = prev?.let { now.compareTo(it) }

@Composable
private fun Tile(@DrawableRes icon: Int, value: String, label: String, note: String?, direction: Int?, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, OutlineVariant, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = Primary, modifier = Modifier.size(18.dp))
            }
            Box(Modifier.weight(1f))
            if (note != null) {
                val arrow = when (direction) { 1 -> "▲ "; -1 -> "▼ "; else -> "" }
                Text(arrow + note, style = LabelSmall, color = if (direction == -1) OnSurface else if (direction == 1) Primary else OnSurfaceVariant)
            }
        }
        Column {
            Text(value, style = TitleLarge, color = OnSurface)
            Text(label, style = LabelMedium, color = OnSurfaceVariant)
        }
    }
}

@Composable
private fun Watchlist(s: StatsState) {
    if (s.trends.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader("Subjects") { Text("This week", style = LabelMedium, color = OnSurfaceVariant) }
        s.trends.forEachIndexed { i, t ->
            val color = subjectColor(t.subject.colorIndex)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SubjectAvatar(t.subject.name, color)
                Column(Modifier.weight(1f)) {
                    Text(t.subject.name, style = LabelLarge, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.sessions} session${if (t.sessions == 1) "" else "s"}", style = LabelSmall, color = OnSurfaceVariant)
                }
                if (LocalDensity.current.fontScale <= 1.3f) Sparkline(t.spark, (t.changePct ?: 0.0) >= 0, Modifier.width(64.dp).height(28.dp))
                Column(Modifier.widthIn(min = 76.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(formatMinutes(t.weekMinutes), style = LabelLarge, color = OnSurface)
                    val pct = t.changePct
                    Text(
                        if (pct == null) (if (t.weekMinutes > 0) "new" else "–") else "%+.1f%%".format(pct).replace("-", "−"),
                        style = LabelSmall, color = Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(if (pct == null) OutlineText else if (pct >= 0) Primary else InverseSurface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            if (i < s.trends.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(OutlineVariant))
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, up: Boolean, modifier: Modifier) {
    // Colour follows the same week-over-week comparison as the badge next to it, so they never disagree.
    Canvas(modifier.semantics { contentDescription = "Minutes per day over the last 7 days" }) {
        val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.size - 1).coerceAtLeast(1)
            val y = size.height - 2.dp.toPx() - (size.height - 4.dp.toPx()) * v / max
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, if (up) Primary else OnSurface, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
