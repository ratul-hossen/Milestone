package com.milestone.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.milestone.app.R
import com.milestone.app.ui.theme.BodyLarge
import com.milestone.app.ui.theme.BodyMedium
import com.milestone.app.ui.theme.CardBorder
import com.milestone.app.ui.theme.Error
import com.milestone.app.ui.theme.LabelLarge
import com.milestone.app.ui.theme.LabelMedium
import com.milestone.app.ui.theme.LabelSmall
import com.milestone.app.ui.theme.OnSurface
import com.milestone.app.ui.theme.OnSurfaceVariant
import com.milestone.app.ui.theme.Outline
import com.milestone.app.ui.theme.OutlineText
import com.milestone.app.ui.theme.OutlineVariant
import com.milestone.app.ui.theme.Primary
import com.milestone.app.ui.theme.SecondaryContainer
import com.milestone.app.ui.theme.SubjectColor
import com.milestone.app.ui.theme.SurfaceContainerHigh
import com.milestone.app.ui.theme.TitleLarge
import com.milestone.app.ui.theme.TitleMedium

/** Figma "Top App Bar": 64dp, title 20/28 bold, optional back arrow and one action. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_left), contentDescription = "Back", tint = OnSurface)
            }
        }
        Text(title, style = TitleLarge, color = OnSurface, modifier = Modifier.weight(1f).semantics { heading() })
        actions()
    }
}

/** 8dp rounded progress bar with the fill coloured per subject. */
@Composable
fun ProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    track: Color = SurfaceContainerHigh,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), label = "progress")
    Box(modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(track)) {
        if (animated > 0f) {
            Box(Modifier.fillMaxWidth(animated).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        }
    }
}

/** Letter avatar on a 12% tint of the subject colour (40dp, 12dp corners). */
@Composable
fun SubjectAvatar(name: String, color: SubjectColor, size: Dp = 40.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(color.tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.trim().take(1).uppercase().ifEmpty { "?" }, style = TitleMedium, color = color.text)
    }
}

/** White card with the Figma 1dp border and 16dp radius. */
@Composable
fun MilestoneCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, CardBorder),
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Figma "Subject Card": avatar, name, progress line, overflow menu with Edit / Delete. */
@Composable
fun SubjectCard(
    name: String,
    subtitle: String,
    progress: Float,
    color: SubjectColor,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    MilestoneCard(onClick = onClick) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SubjectAvatar(name, color)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = TitleMedium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = BodyMedium, color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OverflowMenu(
                    contentDescription = "More options for $name",
                    items = listOf("Edit subject" to onEdit, "Delete subject" to onDelete),
                )
            }
            ProgressBar(progress, color.accent)
        }
    }
}

@Composable
fun OverflowMenu(contentDescription: String, items: List<Pair<String, () -> Unit>>, tint: Color = OnSurfaceVariant) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(R.drawable.ic_more), contentDescription = contentDescription, tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Color.White) {
            items.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = BodyMedium, color = if (label.startsWith("Delete")) Error else OnSurface) },
                    onClick = { open = false; action() },
                )
            }
        }
    }
}

/** Figma "Session Card": 4dp colour bar, subject, note, time, duration in primary. */
@Composable
fun SessionCard(
    subject: String,
    note: String,
    time: String,
    duration: String,
    color: Color,
    onClick: () -> Unit,
) {
    MilestoneCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.width(4.dp).height(44.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(subject, style = TitleMedium, color = OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (note.isNotBlank()) {
                    Text(note, style = BodyMedium, color = OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(time, style = LabelMedium, color = OutlineText)
            }
            Text(duration, style = TitleMedium, color = Primary)
        }
    }
}

/** Figma "Filter Chip": outlined when idle, ink-filled when selected, optional dot / check. */
@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    dotColor: Color? = null,
    showCheck: Boolean = false,
    badge: String? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .then(if (selected) Modifier.background(SecondaryContainer) else Modifier.border(1.dp, OutlineVariant, shape))
            .selectable(selected, role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) { badge?.let { contentDescription = "$label, suggested" } }
            .heightIn(min = 34.dp)
            .padding(start = 12.dp, end = if (dotColor != null || showCheck) 16.dp else 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showCheck && selected) {
            Icon(painterResource(R.drawable.ic_check_18), null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        if (dotColor != null) {
            // White ring when selected, so a dark (Ink) subject dot stays visible on the ink chip.
            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor)
                .then(if (selected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier))
        }
        Text(label, style = LabelLarge, color = if (selected) Color.White else OnSurfaceVariant)
        if (badge != null) {
            Text(
                badge,
                style = LabelSmall,
                color = if (selected) Color.White else Primary,
            )
        }
    }
}

/**
 * Figma "Text Field": label above, 56dp white box with 12dp corners, leading icon, optional
 * trailing icon and supporting/error text. Error state uses a 2dp error border.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    @DrawableRes leadingIcon: Int,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    supporting: String? = null,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minHeight: Dp = 56.dp,
    @DrawableRes trailingIcon: Int? = null,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val isError = error != null
    var focused by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = LabelMedium, color = if (isError) Error else OnSurfaceVariant)
        val shape = RoundedCornerShape(12.dp)
        val boxModifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(Color.White)
            // Idle border #8C8C8C (3.36:1 on white) so the field is identifiable; 2dp ink when focused.
            .border(
                if (isError || focused) 2.dp else 1.dp,
                when { isError -> Error; focused -> OnSurface; else -> Outline },
                shape,
            )
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
        val content: @Composable (@Composable () -> Unit) -> Unit = { inner ->
            Row(
                boxModifier.padding(horizontal = 16.dp, vertical = if (singleLine) 0.dp else 16.dp),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(painterResource(leadingIcon), null, tint = if (isError) Error else OnSurfaceVariant,
                    modifier = Modifier.padding(top = if (singleLine) 0.dp else 0.dp).size(24.dp))
                Box(Modifier.weight(1f).then(if (singleLine) Modifier.padding(vertical = 16.dp) else Modifier)) {
                    if (value.isEmpty()) Text(placeholder, style = BodyLarge, color = OutlineText)
                    inner()
                }
                when {
                    isError -> Icon(painterResource(R.drawable.ic_alert), null, tint = Error, modifier = Modifier.size(24.dp))
                    trailingIcon != null -> Icon(painterResource(trailingIcon), null, tint = OnSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }
        if (readOnly) {
            content { Text(value, style = BodyLarge, color = OnSurface) }
        } else {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = BodyLarge.copy(color = OnSurface),
                singleLine = singleLine,
                keyboardOptions = keyboardOptions,
                cursorBrush = SolidColor(Primary),
                decorationBox = { inner -> content(inner) },
                modifier = Modifier.semantics { contentDescription = label }.onFocusChanged { focused = it.isFocused },
            )
        }
        val helper = error ?: supporting
        if (helper != null) Text(helper, style = LabelSmall, color = if (isError) Error else OnSurfaceVariant)
    }
}

// ---------------------------------------------------------------- buttons (Figma "Button" set)

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LabelLarge)
    }
}

@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Primary,
    borderColor: Color = Outline,
    @DrawableRes icon: Int? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        border = BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LabelLarge)
    }
}

@Composable
fun PlainButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Primary) {
    TextButton(onClick = onClick, modifier = modifier.heightIn(min = 40.dp), colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(text, style = LabelLarge)
    }
}

/** Figma "FAB Extended": primary, 16dp radius, plus icon + label. */
@Composable
fun FabExtended(text: String, onClick: () -> Unit, @DrawableRes icon: Int = R.drawable.ic_plus) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Primary,
        contentColor = Color.White,
        modifier = Modifier.shadow(6.dp, RoundedCornerShape(16.dp), spotColor = Color(0x38172921)),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(painterResource(icon), null, modifier = Modifier.size(24.dp))
            Text(text, style = LabelLarge)
        }
    }
}

/** Centered empty state used on every list screen. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    title: String,
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(painterResource(icon), null, tint = Primary, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(title, style = TitleMedium, color = OnSurface)
        Text(message, style = BodyMedium, color = OnSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(8.dp))
            PrimaryButton(action, onAction)
        }
    }
}

/** Small uppercase section label ("TODAY", "SUGGESTED"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, style = LabelMedium.copy(letterSpacing = LabelMedium.letterSpacing * 3), color = OutlineText,
        modifier = modifier.semantics { heading() })
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TitleLarge, color = OnSurface, modifier = Modifier.weight(1f).semantics { heading() })
        trailing()
    }
}
