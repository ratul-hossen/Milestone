package com.milestone.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.milestone.app.R

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun jakarta(weight: Int) = Font(
    R.font.plus_jakarta_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Plus Jakarta Sans (OFL), bundled as a variable font so the app works offline. */
val Jakarta = FontFamily(jakarta(400), jakarta(500), jakarta(600), jakarta(700), jakarta(800))

private fun style(size: Int, weight: Int, line: Int, tracking: Double) = TextStyle(
    fontFamily = Jakarta,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = (tracking / size).em,
)

// Figma text styles.
val DisplayLarge = style(40, 800, 48, -1.0)
val HeadlineMedium = style(26, 700, 32, -0.5)
val HeadlineSmall = style(22, 700, 32, -0.5)
val TitleLarge = style(20, 700, 28, 0.0)
val TitleMedium = style(16, 600, 24, 0.0)
val BodyLarge = style(16, 400, 24, 0.0)
val BodyMedium = style(14, 400, 20, 0.0)
val LabelLarge = style(14, 600, 20, 0.1)
val LabelMedium = style(12, 600, 16, 0.3)
val LabelSmall = style(11, 500, 16, 0.4)

val MilestoneTypography = Typography(
    displayLarge = DisplayLarge,
    headlineMedium = HeadlineMedium,
    headlineSmall = HeadlineSmall,
    titleLarge = TitleLarge,
    titleMedium = TitleMedium,
    bodyLarge = BodyLarge,
    bodyMedium = BodyMedium,
    labelLarge = LabelLarge,
    labelMedium = LabelMedium,
    labelSmall = LabelSmall,
)
