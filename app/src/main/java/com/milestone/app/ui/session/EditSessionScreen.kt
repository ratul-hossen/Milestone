package com.milestone.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.R
import com.milestone.app.ui.components.DurationExplainSheet
import com.milestone.app.ui.components.EmptyState
import com.milestone.app.ui.components.FilterChip
import com.milestone.app.ui.components.FormField
import com.milestone.app.ui.components.PlainButton
import com.milestone.app.ui.components.PrimaryButton
import com.milestone.app.ui.components.SubjectAvatar
import com.milestone.app.ui.components.TopBar
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.LabelSmall
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.subjectColor
import com.milestone.app.util.formatMinutes
import com.milestone.app.util.fullLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val QUICK = listOf(25, 45, 60, 90, 120)

@Composable
fun EditSessionScreen(
    onDone: () -> Unit,
    onOpenTimer: (Long?) -> Unit,
    onAddSubject: () -> Unit,
    vm: EditSessionViewModel = viewModel(factory = EditSessionViewModel.Factory),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    var subjectMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }

    LaunchedEffect(s.saved) { if (s.saved) onDone() }

    Column(Modifier.fillMaxSize().imePadding()) {
        TopBar(if (s.isEdit) "Edit session" else "Log study session", onBack = onDone)
        if (!s.loading && s.subjects.isEmpty()) {
            EmptyState(R.drawable.ic_book, "No subjects yet", "Add a subject first, then log time against it.",
                "Add subject", onAddSubject)
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            val subject = s.subjects.firstOrNull { it.id == s.subjectId }
            Box {
                FormField(
                    label = "Subject",
                    value = subject?.name.orEmpty(),
                    onValueChange = {},
                    leadingIcon = R.drawable.ic_book_field,
                    placeholder = "Select a subject",
                    error = s.subjectError,
                    trailingIcon = R.drawable.ic_chevron_down,
                    readOnly = true,
                    onClick = { subjectMenu = true },
                )
                DropdownMenu(subjectMenu, { subjectMenu = false }, containerColor = Color.White) {
                    s.subjects.forEach { sub ->
                        DropdownMenuItem(
                            text = { Text(sub.name, style = BodyMedium, color = OnSurface) },
                            leadingIcon = { SubjectAvatar(sub.name, subjectColor(sub.colorIndex), 28.dp) },
                            onClick = { vm.selectSubject(sub.id); subjectMenu = false },
                        )
                    }
                }
            }
            FormField(
                label = "Duration (minutes)",
                value = s.duration,
                onValueChange = vm::setDuration,
                leadingIcon = R.drawable.ic_timer,
                placeholder = "e.g. 45",
                supporting = "Quick pick or type your own",
                error = s.durationError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            val suggested = s.suggestion?.takeIf { !s.isEdit }?.minutes
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val chips = (QUICK + listOfNotNull(suggested)).distinct().sorted()
                items(chips) { m ->
                    FilterChip(formatMinutes(m), s.duration == m.toString(), { vm.setDuration(m.toString()) },
                        badge = if (m == suggested) "✦" else null)
                }
            }
            if (suggested != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.suggestion!!.fromHistory) "✦ Your usual length: ${formatMinutes(suggested)}. Tap the chip to use it"
                        else "✦ ${formatMinutes(suggested)} is a common block until Milestone learns your rhythm",
                        style = LabelSmall, color = OnSurfaceVariant, modifier = Modifier.weight(1f),
                    )
                    PlainButton("Why?", { explain = true })
                }
            }
            FormField(
                label = "Date",
                value = s.date.fullLabel(),
                onValueChange = {},
                leadingIcon = R.drawable.ic_calendar,
                error = s.dateError,
                readOnly = true,
                onClick = { datePicker = true },
            )
            FormField(
                label = "Notes (optional)",
                value = s.notes,
                onValueChange = vm::setNotes,
                leadingIcon = R.drawable.ic_notes,
                placeholder = "What did you cover?",
                supporting = "${s.notes.length} / ${EditSessionViewModel.MAX_NOTES}",
                singleLine = false,
                minHeight = 104.dp,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (!s.isEdit) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Studying right now?", style = BodyMedium, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                    PlainButton("Use focus timer", { onOpenTimer(s.subjectId) })
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (s.isEdit) "Save changes" else "Save session", { vm.save() }, Modifier.fillMaxWidth(), enabled = !s.loading)
            PlainButton("Cancel", onDone, Modifier.fillMaxWidth())
        }
    }

    if (datePicker) SessionDatePicker(s.date, onPick = { vm.setDate(it); datePicker = false }, onDismiss = { datePicker = false })
    if (explain) s.suggestion?.let { sug ->
        DurationExplainSheet(sug, s.subjectId, { id -> s.subjects.firstOrNull { it.id == id }?.name ?: "Subject" }) { explain = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionDatePicker(current: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
            override fun isSelectableYear(year: Int) = year <= LocalDate.now().year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PlainButton("OK", {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            })
        },
        dismissButton = { PlainButton("Cancel", onDismiss) },
    ) {
        DatePicker(state)
    }
}
