package com.example.musicintervaltrainer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class IntervalQuizTest {
    @Test
    fun intervalsCoverEverySemitoneFromUnisonToOctave() {
        assertEquals((0..12).toList(), Interval.entries.map { it.semitones })
    }

    @Test
    fun secondPitchIsOffsetByTheIntervalInTheChosenDirection() {
        assertEquals(75, Question(69, Interval.TRITONE, ascending = true).secondNote)
        assertEquals(61, Question(69, Interval.MINOR_SIXTH, ascending = false).secondNote)
        assertEquals(79, Question(69, Interval.MINOR_SEVENTH, ascending = true).secondNote)
        assertEquals(58, Question(69, Interval.MAJOR_SEVENTH, ascending = false).secondNote)
        assertEquals(69, Question(69, Interval.UNISON, ascending = true).secondNote)
        assertEquals(69, Question(69, Interval.UNISON, ascending = false).secondNote)
    }

    @Test
    fun randomQuestionsRespectThePracticeDirection() {
        val random = Random(42)
        repeat(32) {
            val up = Question.random(Direction.UP, random = random)
            val down = Question.random(Direction.DOWN, random = random)
            assertTrue(up.secondNote >= up.firstNote)
            assertTrue(down.secondNote <= down.firstNote)
        }

        val mixed = List(32) { Question.random(Direction.MIXED, random = random) }
        assertTrue(mixed.any { it.secondNote > it.firstNote })
        assertTrue(mixed.any { it.secondNote < it.firstNote })
    }

    @Test
    fun randomQuestionRedrawsAnExactRepeatOfThePreviousPitches() {
        val previous = Question.random(Direction.UP, random = Random(42))
        // The identical seed makes the first draw match the previous question.
        val next = Question.random(Direction.UP, previous = previous, random = Random(42))

        assertNotEquals(previous.firstNote to previous.secondNote, next.firstNote to next.secondNote)
    }

    @Test
    fun scoreCountsEveryAnswerAndOnlyCreditsCorrectAnswers() {
        val question = Question(69, Interval.MAJOR_THIRD, ascending = true)
        val right = PracticeScore().recordAnswer(question, Interval.MAJOR_THIRD)
        val wrong = right.recordAnswer(question, Interval.MINOR_THIRD)

        assertEquals(PracticeScore(answered = 1, correct = 1), right)
        assertEquals(PracticeScore(answered = 2, correct = 1), wrong)
    }
}
