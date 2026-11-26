package com.example.musicintervaltrainer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class IntervalAudioTest {
    @Test
    fun waveformContainsTwo520MillisecondNotesAnd180MillisecondsOfSilence() {
        val samples = synthesizeInterval(69, 76)

        assertEquals(2 * noteFrames + gapFrames, samples.size)
        assertTrue((0 until noteFrames).any { samples[it] != 0.toShort() })
        assertTrue((noteFrames until secondNoteStart).all { samples[it] == 0.toShort() })
        assertTrue((secondNoteStart until samples.size).any { samples[it] != 0.toShort() })
    }

    @Test
    fun waveformPlaysPitchesInTheSuppliedOrder() {
        val samples = synthesizeInterval(81, 69)

        assertEquals(880.0, estimateFrequency(samples, noteStart = 0), 4.0)
        assertEquals(440.0, estimateFrequency(samples, noteStart = secondNoteStart), 4.0)
    }

    @Test
    fun unisonProducesTwoIdenticalNotes() {
        val samples = synthesizeInterval(69, 69)

        assertArrayEquals(
            samples.copyOfRange(0, noteFrames),
            samples.copyOfRange(secondNoteStart, samples.size)
        )
    }

    @Test
    fun noteStartsAndEndsWithAShortFade() {
        val samples = synthesizeInterval(69, 69)

        assertEquals(0.toShort(), samples.first())
        assertEquals(0.toShort(), samples[noteFrames - 1])
        assertTrue(peak(samples, 0 until 24) < peak(samples, 288 until 576))
        assertTrue(
            peak(samples, noteFrames - 240 until noteFrames) <
                peak(samples, noteFrames - 1_440 until noteFrames - 1_200)
        )
    }

    private fun estimateFrequency(samples: ShortArray, noteStart: Int): Double {
        // Count positive-going zero crossings over a quarter second, after the attack.
        val windowStart = noteStart + sampleRate / 10
        val windowFrames = sampleRate / 4
        val crossings = (windowStart until windowStart + windowFrames).count { index ->
            samples[index - 1] <= 0 && samples[index] > 0
        }
        return crossings * sampleRate.toDouble() / windowFrames
    }

    private fun peak(samples: ShortArray, frames: IntRange): Int =
        frames.maxOf { abs(samples[it].toInt()) }

    private companion object {
        const val sampleRate = 24_000
        const val noteFrames = 12_480
        const val gapFrames = 4_320
        const val secondNoteStart = noteFrames + gapFrames
    }
}
