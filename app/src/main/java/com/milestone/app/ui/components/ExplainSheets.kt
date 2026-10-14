package com.milestone.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.milestone.app.data.db.SessionEntity
import com.milestone.app.ml.DurationSuggester
import com.milestone.app.ml.SubjectRecommender
import com.milestone.app.ml.WeeklyForecaster
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.LabelSmall
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.OutlineText
import com.milestone.app.ui.theme.OutlineVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.SecondaryContainer
import com.milestone.app.ui.theme.SurfaceContainerHigh
import com.milestone.app.ui.theme.SurfaceContainerLow
import com.milestone.app.ui.theme.TitleLarge
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.shortLabel
import java.time.LocalDate
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExplainSheet(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title, style = TitleLarge, color = OnSurface)
            content()
        }
    }
}

/** A labelled horizontal bar; positive values grow right in ink, negative ones in grey. */
@Composable
private fun ContributionRow(label: String, detail: String, value: Double, max: Double, valueText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label, style = LabelLarge, color = OnSurface)
                Text(detail, style = LabelSmall, color = OnSurfaceVariant)
            }
            Text(valueText, style = LabelLarge, color = if (value >= 0) OnSurface else OnSurfaceVariant)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(SurfaceContainerHigh)) {
            val f = if (max <= 0) 0f else (abs(value) / max).toFloat().coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth(f).height(6.dp).clip(RoundedCornerShape(3.dp))
                .background(if (value >= 0) SecondaryContainer else OutlineText))
        }
    }
}

@Composable
private fun HowItWorks(text: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(androidx.compose.ui.graphics.Color.White).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("HOW THIS WORKS", style = LabelMedium, color = OutlineText)
        Text(text, style = BodyMedium, color = OnSurfaceVariant)
    }
}

@Composable
fun ForecastExplainSheet(forecast: WeeklyForecaster.Forecast, onDismiss: () -> Unit) {
    ExplainSheet("How the forecast is made", onDismiss) {
        Text(
            "You've studied ${formatMinutes(forecast.soFarMinutes)} this week. " +
                "Milestone expects about ${formatMinutes(forecast.predictedTotalMinutes - forecast.soFarMinutes)} more " +
                "by Sunday, so ${formatMinutes(forecast.predictedTotalMinutes)} in total" +
                if (forecast.goalMinutes > 0) " against your ${formatMinutes(forecast.goalMinutes)} goal." else ".",
            style = BodyMedium, color = OnSurface,
        )
        val max = forecast.contributions.maxOf { abs(it.minutes) }
        forecast.contributions.forEach {
            ContributionRow(it.label, it.explanation, it.minutes, max,
                (if (it.minutes >= 0) "adds " else "removes ") + formatMinutes(abs(it.minutes).toInt()))
        }
        HowItWorks(
            "A ridge-regression model adds up the signals above, each weighted by how much it mattered for about " +
                "20,000 real Open University students (measured in clicks; only the proportions carry over to minutes). " +
                if (forecast.personalised) "It has been re-fitted to your own ${forecast.weeksUsed} weeks of history."
                else "Once you have enough weeks of history it re-fits itself to you. Everything runs on your phone."
        )
    }
}

@Composable
fun SuggestionExplainSheet(s: SubjectRecommender.Suggestion, onDismiss: () -> Unit) {
    ExplainSheet("Why ${s.subject.name}?", onDismiss) {
        Text(
            "${(s.goalLeft * 100).toInt()}% of its weekly goal is still left, and on a day like today your habits suggest " +
                "only about a ${(s.probability * 100).toInt()}% chance you'd get to it without a nudge.",
            style = BodyMedium, color = OnSurface,
        )
        Text("WHAT SHAPES THAT CHANCE", style = LabelMedium, color = OutlineText)
        val max = s.factors.maxOf { abs(it.effect) }.coerceAtLeast(0.01)
        s.factors.forEach {
            ContributionRow(it.label, it.value, it.effect, max,
                when {
                    abs(it.effect) < 0.01 -> "no effect"
                    it.effect > 0 -> "more likely"
                    else -> "less likely"
                })
        }
        HorizontalDivider(color = OutlineVariant)
        Text("ALL SUBJECTS TODAY (HIGHEST NEED FIRST)", style = LabelMedium, color = OutlineText)
        s.ranking.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.subject.name, style = BodyMedium, color = OnSurface, modifier = Modifier.weight(1f))
                Text(if (r.probability >= 1.0) "studied today" else "goal left ${(r.goalLeft * 100).toInt()}% · chance ${(r.probability * 100).toInt()}%",
                    style = LabelSmall, color = OnSurfaceVariant)
            }
        }
        HowItWorks(
            "A logistic-regression model, trained on 412 real Open University students who took several courses at once " +
                "(their activity was measured in clicks, not minutes), estimates how likely you are to study each subject today. Milestone suggests the one with the most goal left that " +
                "you're least likely to get to on your own: need = goal left × (1 − chance)." +
                if (s.personalised) " The model has adapted to your own history." else ""
        )
    }
}

@Composable
fun DurationExplainSheet(
    suggestion: DurationSuggester.Suggestion,
    subjectId: Long?,
    subjectName: (Long) -> String,
    onDismiss: () -> Unit,
) {
    ExplainSheet("Why ${formatMinutes(suggestion.minutes)}?", onDismiss) {
        if (!suggestion.fromHistory) {
            Text("You haven't logged enough sessions yet, so Milestone starts with a common 45-minute block. " +
                "After a few sessions it'll learn your own rhythm.", style = BodyMedium, color = OnSurface)
        } else {
            val sameSubject = subjectId != null && suggestion.basedOn.all { it.subjectId == subjectId }
            Text(
                if (sameSubject) "It's the middle value (median) of your past sessions for this subject, so one unusually " +
                    "long or short day doesn't skew it. Based on ${suggestion.basedOn.size} session${if (suggestion.basedOn.size == 1) "" else "s"}:"
                else "You haven't logged this subject yet, so it's the middle value (median) of all your recent sessions:",
                style = BodyMedium, color = OnSurface)
            suggestion.basedOn.take(8).forEach { s: SessionEntity ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(4.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(Primary))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(subjectName(s.subjectId), style = LabelLarge, color = OnSurface)
                        Text("${LocalDate.ofEpochDay(s.dateEpochDay).shortLabel()} · started around ${s.startHour}:00",
                            style = LabelSmall, color = OnSurfaceVariant)
                    }
                    Text(formatMinutes(s.durationMinutes), style = LabelLarge, color = OnSurface)
                }
            }
            if (suggestion.basedOn.size > 8) Text("+ ${suggestion.basedOn.size - 8} more", style = LabelSmall, color = OnSurfaceVariant)
        }
        HowItWorks("On simulated study logs we compared several approaches (k-nearest-neighbours using time of day, " +
            "weekday and recency) with this simple per-subject median. The median was the most accurate, so that's what Milestone uses. " +
            "It only ever looks at your own data on this phone.")
    }
}
