package com.milestone.app.ui.theme

import androidx.compose.ui.graphics.Color

// Tokens from the Figma file "Milestone — Study Progress Tracker".
val Primary = Color(0xFFE11D2A)
val OnPrimary = Color.White
val PrimaryContainer = Color(0xFFFFE2E2)
val OnPrimaryContainer = Color(0xFF5C0007)
val SecondaryContainer = Color(0xFF111111)
val OnSecondaryContainer = Color.White
val Surface = Color(0xFFFAFAFA)
val OnSurface = Color(0xFF0D0D0D)
val OnSurfaceVariant = Color(0xFF5B5B5B)
val SurfaceContainerLowest = Color.White
val SurfaceContainerLow = Color(0xFFF4F4F4)
val SurfaceContainer = Color.White
val SurfaceContainerHigh = Color(0xFFEDEDED)
val OutlineVariant = Color(0xFFDEDEDE)
val Error = Color(0xFFC1121F)
val ErrorContainer = Color(0xFFFFE2E2)
val OnErrorContainer = Color(0xFF5C0007)
val InverseSurface = Color(0xFF0D0D0D)

/**
 * Figma uses #8C8C8C for small captions ("TODAY", times). On #FAFAFA that is only 3.2:1, below
 * WCAG AA (4.5:1) for 11–12sp text, so text uses this darker grey (4.89:1). The original value is
 * kept for non-text strokes.
 */
val Outline = Color(0xFF8C8C8C)
val OutlineText = Color(0xFF6E6E6E)

/** Card stroke rgba(192,201,195,0.7) from Figma. */
val CardBorder = Color(0xB3C0C9C3)

/** Muted text on the dark Today card (#A6A6A6 on #0D0D0D = 7.98:1). */
val OnInverseMuted = Color(0xFFA6A6A6)

/**
 * Subject colour tags (contrast checked with WCAG 2.1 relative luminance):
 * - [accent] fills bars, dots and swatches.
 * - [text] is for letters/labels on the 12% [tint]; every value is ≥ 5.1:1. Amber and grey from the
 *   Figma palette were only 2.13:1 and 2.41:1 on their tint, so they get darker text shades.
 * - [onAccent] is for icons/letters drawn on a solid [accent]; white where it passes, ink otherwise.
 * - Every [accent] is ≥ 3:1 against the #EDEDED progress track and white (WCAG non-text contrast), so
 *   Figma's amber #FF8A00 (2.02:1) and grey #9E9E9E (2.29:1) were darkened to #C76A00 and #757575.
 * - There is no plain "red" subject: red is reserved for the primary brand colour and for errors, so a
 *   subject can never be confused with an alert. Green takes that slot.
 */
data class SubjectColor(val name: String, val accent: Color, val text: Color, val onAccent: Color = Color.White) {
    val tint: Color get() = accent.copy(alpha = 0.12f)
}

val SubjectColors = listOf(
    SubjectColor("Indigo", Color(0xFF3D5AFE), Color(0xFF2A44D6)),
    SubjectColor("Green", Color(0xFF2E7D32), Color(0xFF1B5E20)),
    SubjectColor("Ink", Color(0xFF111111), Color(0xFF111111)),
    SubjectColor("Amber", Color(0xFFC76A00), Color(0xFF9A4F00), Color(0xFF111111)),
    SubjectColor("Violet", Color(0xFF7C4DFF), Color(0xFF5E35D6)),
    SubjectColor("Grey", Color(0xFF757575), Color(0xFF5B5B5B)),
    SubjectColor("Teal", Color(0xFF00897B), Color(0xFF00695C)),
    SubjectColor("Pink", Color(0xFFD81B60), Color(0xFFAD1457)),
)

fun subjectColor(index: Int): SubjectColor = SubjectColors[index.mod(SubjectColors.size)]
