package com.milestone.app.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

/** "2h 45m", "3h 05m", "2h", "45m". */
fun formatMinutes(totalMinutes: Int): String {
    val m = totalMinutes.coerceAtLeast(0)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0 -> "${rest}m"
        rest == 0 -> "${h}h"
        else -> "${h}h %02dm".format(rest)
    }
}

/** Weekly goals are whole hours in the UI: "8h". */
fun formatGoal(minutes: Int): String = formatMinutes(minutes)

fun percent(part: Int, whole: Int): Int =
    if (whole <= 0) 0 else (part * 100f / whole).roundToInt()

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val fullDate = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
private val shortDate = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val headerDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val time12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

fun LocalDate.fullLabel(): String = format(fullDate)          // Sat, 10 Oct 2026
fun LocalDate.shortLabel(): String = format(shortDate)        // Sat, 10 Oct
fun LocalDate.dayMonthLabel(): String = format(dayMonth)      // 10 Oct
fun LocalDate.headerLabel(): String = format(headerDate)      // Saturday, 10 October
fun LocalTime.label(): String = format(time12)                // 4:30 PM

/** "4 – 10 Oct" or "28 Sep – 4 Oct". */
fun rangeLabel(start: LocalDate, end: LocalDate): String =
    if (start.month == end.month && start.year == end.year) "${start.dayOfMonth} – ${end.format(dayMonth)}"
    else "${start.format(dayMonth)} – ${end.format(dayMonth)}"

/** Section headers in History: TODAY, YESTERDAY, THU, 8 OCT. */
fun dayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "TODAY"
    today.minusDays(1) -> "YESTERDAY"
    else -> date.shortLabel().uppercase(Locale.ENGLISH)
}

/** "today", "yesterday", "2 days ago", "never". */
fun relativeDay(date: LocalDate?, today: LocalDate): String = when {
    date == null -> "not studied yet"
    date == today -> "today"
    date == today.minusDays(1) -> "yesterday"
    else -> "${today.toEpochDay() - date.toEpochDay()} days ago"
}

fun LocalDate.startOfWeek(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun greeting(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
