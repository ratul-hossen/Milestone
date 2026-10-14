package com.milestone.app.ml

import com.milestone.app.data.db.SessionEntity
import com.milestone.app.data.db.SubjectEntity
import com.milestone.app.data.repo.StudyMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {
    private val today = LocalDate.of(2026, 10, 14) // a Wednesday
    private val physics = SubjectEntity(id = 1, name = "Physics", colorIndex = 0, weeklyGoalMinutes = 480, createdAt = 0)
    private val maths = SubjectEntity(id = 2, name = "Maths", colorIndex = 1, weeklyGoalMinutes = 420, createdAt = 0)

    private fun session(subject: Long, daysAgo: Long, minutes: Int, hour: Int = 18) = SessionEntity(
        id = 0, subjectId = subject, durationMinutes = minutes,
        dateEpochDay = today.minusDays(daysAgo).toEpochDay(), startHour = hour, loggedAt = daysAgo,
    )

    @Test fun `linear solver solves a 3x3 system`() {
        val x = LinearAlgebra.solve(
            arrayOf(doubleArrayOf(2.0, 1.0, -1.0), doubleArrayOf(-3.0, -1.0, 2.0), doubleArrayOf(-2.0, 1.0, 2.0)),
            doubleArrayOf(8.0, -11.0, -3.0),
        )!!
        assertEquals(2.0, x[0], 1e-9); assertEquals(3.0, x[1], 1e-9); assertEquals(-1.0, x[2], 1e-9)
    }

    @Test fun `singular system returns null instead of crashing`() {
        assertNull(LinearAlgebra.solve(arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(2.0, 4.0)), doubleArrayOf(1.0, 2.0)))
    }

    @Test fun `forecast is never below what is already logged`() {
        val sessions = listOf(session(1, 0, 90), session(1, 1, 60), session(2, 9, 30), session(2, 20, 30))
        val f = WeeklyForecaster(ForecastConfig()).forecast(StudyHistory(listOf(physics, maths), sessions, today), 900)!!
        assertEquals(150, f.soFarMinutes)
        assertTrue(f.predictedTotalMinutes >= f.soFarMinutes)
    }

    @Test fun `forecast waits for 14 days of history`() {
        val recent = listOf(session(1, 0, 60), session(1, 5, 60))
        assertNull(WeeklyForecaster(ForecastConfig()).forecast(StudyHistory(listOf(physics), recent, today), 480))
    }

    @Test fun `steady studier is not told they are behind before logging today`() {
        // 60 min every day for 8 weeks; Wednesday morning, today not logged yet. True week total will be 420.
        val sessions = (1L..56L).map { session(1, it, 60) }
        val f = WeeklyForecaster(ForecastConfig(doubleArrayOf(0.4242, 0.1051, 0.1737), 30.0, 6))
            .forecast(StudyHistory(listOf(physics), sessions, today), 420)!!
        assertEquals(120, f.soFarMinutes)
        assertTrue("forecast ${f.predictedTotalMinutes} should be near 420", f.predictedTotalMinutes in 360..480)
    }

    @Test fun `subject already studied today is not suggested`() {
        val s = SubjectRecommender(RecommenderConfig()).suggest(StudyHistory(listOf(physics, maths), listOf(session(2, 0, 30)), today))!!
        assertEquals("Physics", s.subject.name)
        assertEquals(1.0, s.ranking.first { it.subject.id == 2L }.probability, 1e-9)
    }

    @Test fun `forecast is null without any history`() {
        assertNull(WeeklyForecaster(ForecastConfig()).forecast(StudyHistory(listOf(physics), emptyList(), today), 480))
    }

    @Test fun `shipped duration config behaves as the per-subject median`() {
        // Same as assets/ml/duration_knn.json after the notebook picked the subject-median baseline.
        val cfg = DurationConfig(k = 1000, wSubject = 1e6, wHour = 0.0, wWeekday = 0.0, wAge = 0.0, aggregate = "median")
        val sessions = listOf(30, 40, 50, 200).mapIndexed { i, m -> session(1, i.toLong() + 1, m) } +
            (1..20).map { session(2, it.toLong(), 120) }    // many other-subject sessions must not outvote
        val s = DurationSuggester(cfg).suggest(sessions, 1, 18, today)
        assertEquals(45, s.minutes) // np.median(30, 40, 50, 200) = 45
        assertTrue(s.basedOn.all { it.subjectId == 1L })
    }

    @Test fun `odd count gives the middle value`() {
        val cfg = DurationConfig(k = 1000, wSubject = 1e6, wHour = 0.0, wWeekday = 0.0, wAge = 0.0, aggregate = "median")
        val sessions = listOf(25, 60, 35).mapIndexed { i, m -> session(1, i.toLong() + 1, m) }
        assertEquals(35, DurationSuggester(cfg).suggest(sessions, 1, 9, today).minutes)
    }

    @Test fun `duration falls back when there is too little history`() {
        val s = DurationSuggester(DurationConfig()).suggest(listOf(session(1, 1, 30)), 1, 18, today)
        assertEquals(45, s.minutes)
        assertTrue(!s.fromHistory)
    }

    @Test fun `recommender skips subjects whose weekly goal is met`() {
        val done = listOf(session(1, 0, 480)) // physics goal met today
        val s = SubjectRecommender(RecommenderConfig()).suggest(StudyHistory(listOf(physics, maths), done, today))
        assertNotNull(s)
        assertEquals("Maths", s!!.subject.name)
    }

    @Test fun `no suggestion when every goal is met`() {
        val done = listOf(session(1, 0, 480), session(2, 0, 420))
        assertNull(SubjectRecommender(RecommenderConfig()).suggest(StudyHistory(listOf(physics, maths), done, today)))
    }

    @Test fun `streak survives until the day ends`() {
        val h = StudyHistory(listOf(physics), listOf(session(1, 1, 30), session(1, 2, 30), session(1, 4, 30)), today)
        assertEquals(2, StudyMath.currentStreak(h))
        assertEquals(2, StudyMath.longestStreak(h.sessions))
    }
}
