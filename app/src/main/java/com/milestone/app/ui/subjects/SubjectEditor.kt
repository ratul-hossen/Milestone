package com.milestone.app.ui.subjects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.milestone.app.MilestoneApp
import com.milestone.app.R
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.ui.UiMessage
import com.milestone.app.ui.appViewModel
import com.milestone.app.ui.components.FormField
import com.milestone.app.ui.components.OutlineButton
import com.milestone.app.ui.components.PlainButton
import com.milestone.app.ui.components.PrimaryButton
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.Error
import com.milestone.app.ui.theme.HeadlineSmall
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.SubjectColors
import com.milestone.app.ui.theme.SurfaceContainerHigh
import com.milestone.app.ui.theme.SurfaceContainerLow
import com.milestone.app.ui.theme.TitleLarge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class SubjectForm(
    val editing: SubjectEntity? = null,
    val name: String = "",
    val colorIndex: Int = 0,
    val goalHours: String = "",
    val nameError: String? = null,
    val goalError: String? = null,
)

data class DeleteRequest(val subject: SubjectEntity, val sessionCount: Int)

/** Add / edit / delete subjects. Students define their own subjects, colours and weekly goals. */
class SubjectEditorViewModel(private val app: MilestoneApp) : ViewModel() {
    val form = MutableStateFlow<SubjectForm?>(null)
    val deleteRequest = MutableStateFlow<DeleteRequest?>(null)

    fun add() = viewModelScope.launch {
        val used = app.repository.subjects.first().map { it.colorIndex }.toSet()
        // Pick the first colour not used yet so new subjects are easy to tell apart.
        val color = SubjectColors.indices.firstOrNull { it !in used } ?: 0
        form.value = SubjectForm(colorIndex = color)
    }

    fun edit(subject: SubjectEntity) {
        form.value = SubjectForm(subject, subject.name, subject.colorIndex, hoursText(subject.weeklyGoalMinutes))
    }

    private fun hoursText(minutes: Int): String = (minutes / 60.0).let {
        if (it % 1.0 == 0.0) it.toInt().toString() else "%.2f".format(java.util.Locale.US, it).trimEnd('0')
    }

    fun update(transform: (SubjectForm) -> SubjectForm) = form.update { it?.let(transform) }
    fun dismiss() { form.value = null }

    private var saving = false

    fun save() = viewModelScope.launch {
        if (saving) return@launch
        saving = true
        try { doSave() } finally { saving = false }
    }

    private suspend fun doSave() {
        val f = form.value ?: return
        val name = f.name.trim().replace(Regex("\\s+"), " ")
        val hours = f.goalHours.replace(',', '.').toDoubleOrNull()
        val existing = app.repository.subjects.first()
        val nameError = when {
            name.isEmpty() -> "Give the subject a name"
            name.length > 40 -> "Keep it under 40 characters"
            existing.any { it.id != f.editing?.id && it.name.equals(name, ignoreCase = true) } -> "You already have “$name”"
            else -> null
        }
        val goalError = if (hours == null || hours < 0.5 || hours > 40) "Enter between 0.5 and 40 hours" else null
        if (nameError != null || goalError != null) {
            form.value = f.copy(nameError = nameError, goalError = goalError)
            return
        }
        // Keep the exact stored goal if the user didn't touch the field (display rounding must not change data).
        val minutes = if (f.editing != null && f.goalHours == hoursText(f.editing.weeklyGoalMinutes)) f.editing.weeklyGoalMinutes
        else (hours!! * 60).roundToInt()
        val entity = f.editing?.copy(name = name, colorIndex = f.colorIndex, weeklyGoalMinutes = minutes)
            ?: SubjectEntity(name = name, colorIndex = f.colorIndex, weeklyGoalMinutes = minutes)
        try {
            app.repository.saveSubject(entity)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            form.value = f.copy(nameError = "You already have “$name”")
            return
        }
        form.value = null
        app.messages.show(UiMessage(if (f.editing == null) "$name added" else "$name updated"))
    }

    fun requestDelete(subject: SubjectEntity) = viewModelScope.launch {
        if (app.repository.getTimer()?.subjectId == subject.id) {
            app.messages.show(UiMessage("Stop the focus timer for ${subject.name} before deleting it", isError = true))
            return@launch
        }
        deleteRequest.value = DeleteRequest(subject, app.repository.sessionCount(subject.id))
    }

    fun confirmDelete() = viewModelScope.launch {
        val req = deleteRequest.value ?: return@launch
        deleteRequest.value = null
        app.repository.deleteSubject(req.subject)
        app.messages.show(UiMessage("${req.subject.name} deleted"))
    }

    fun cancelDelete() { deleteRequest.value = null }

    companion object {
        val Factory = appViewModel { app, _ -> SubjectEditorViewModel(app) }
    }
}

@Composable
fun rememberSubjectEditor(): SubjectEditorViewModel = viewModel(factory = SubjectEditorViewModel.Factory)

/** Hosts the sheet and dialog; place once per screen that can edit subjects. */
@Composable
fun SubjectEditorHost(vm: SubjectEditorViewModel) {
    val form by vm.form.collectAsStateWithLifecycle()
    val delete by vm.deleteRequest.collectAsStateWithLifecycle()
    form?.let { SubjectSheet(it, vm) }
    delete?.let { req ->
        AlertDialog(
            onDismissRequest = vm::cancelDelete,
            // White, not #EDEDED: primary-red buttons are 4.76:1 on white but only 4.07:1 on #EDEDED.
            containerColor = androidx.compose.ui.graphics.Color.White,
            icon = { Icon(painterResource(R.drawable.ic_trash), null, tint = Error) },
            title = { Text("Delete ${req.subject.name}?", style = HeadlineSmall, color = OnSurface) },
            text = {
                Text(
                    if (req.sessionCount == 0) "This subject has no sessions yet."
                    else "Its ${req.sessionCount} logged session${if (req.sessionCount == 1) "" else "s"} will also be " +
                        "removed from your history and stats. This can’t be undone.",
                    style = BodyMedium, color = OnSurfaceVariant,
                )
            },
            confirmButton = { PlainButton("Delete", vm::confirmDelete, color = Error) },
            dismissButton = { PlainButton("Cancel", vm::cancelDelete) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectSheet(f: SubjectForm, vm: SubjectEditorViewModel) {
    ModalBottomSheet(
        onDismissRequest = vm::dismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = SurfaceContainerLow,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (f.editing == null) "New subject" else "Edit subject", style = TitleLarge, color = OnSurface,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = vm::dismiss) {
                    Icon(painterResource(R.drawable.ic_close), "Close", tint = OnSurfaceVariant)
                }
            }
            FormField(
                label = "Subject name",
                value = f.name,
                onValueChange = { v -> vm.update { it.copy(name = v.take(40), nameError = null) } },
                leadingIcon = R.drawable.ic_book_field,
                placeholder = "e.g. Physics",
                error = f.nameError,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Colour tag", style = LabelMedium, color = OnSurfaceVariant)
                SubjectColors.chunked(4).forEachIndexed { row, colors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        colors.forEachIndexed { i, c ->
                            val index = row * 4 + i
                            val selected = index == f.colorIndex
                            Box(
                                Modifier.size(48.dp).clip(CircleShape)
                                    .then(if (selected) Modifier.border(2.dp, c.accent, CircleShape) else Modifier)
                                    // Hairline on every swatch so light colours still read on the grey sheet.
                                    .selectable(selected, role = Role.RadioButton) { vm.update { it.copy(colorIndex = index) } }
                                    .semantics { contentDescription = c.name + if (selected) ", selected" else "" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(Modifier.size(if (selected) 36.dp else 38.dp).clip(CircleShape).background(c.accent)
                                    .border(1.dp, com.milestone.app.ui.theme.Outline.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center) {
                                    if (selected) Icon(painterResource(R.drawable.ic_check_20), null, tint = c.onAccent, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            FormField(
                label = "Weekly goal (hours)",
                value = f.goalHours,
                onValueChange = { v -> vm.update { it.copy(goalHours = v.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(4), goalError = null) } },
                leadingIcon = R.drawable.ic_target,
                placeholder = "e.g. 6",
                supporting = "Between 0.5 and 40 hours",
                error = f.goalError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlineButton("Cancel", vm::dismiss, Modifier.weight(1f))
                PrimaryButton(if (f.editing == null) "Save subject" else "Save changes", { vm.save() }, Modifier.weight(1f), icon = R.drawable.ic_check_18)
            }
        }
    }
}
