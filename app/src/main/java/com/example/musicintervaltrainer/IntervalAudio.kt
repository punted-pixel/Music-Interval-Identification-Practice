package com.example.musicintervaltrainer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

private const val SAMPLE_RATE = 24_000
private const val NOTE_MS = 520
private const val GAP_MS = 180
private const val NOTE_SAMPLES = SAMPLE_RATE * NOTE_MS / 1_000
private const val GAP_SAMPLES = SAMPLE_RATE * GAP_MS / 1_000

internal suspend fun playInterval(firstNote: Int, secondNote: Int) = withContext(Dispatchers.IO) {
    val samples = ShortArray(2 * NOTE_SAMPLES + GAP_SAMPLES)
    synthesizeNote(samples, start = 0, midiNote = firstNote)
    synthesizeNote(samples, start = NOTE_SAMPLES + GAP_SAMPLES, midiNote = secondNote)
    ensureActive()

    val track = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        .setBufferSizeInBytes(samples.size * Short.SIZE_BYTES)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()

    try {
        check(track.write(samples, 0, samples.size) == samples.size) {
            "Could not load both notes into AudioTrack"
        }
        ensureActive()
        track.play()
        // Leave a little time for the output buffer to drain after the last note.
        delay((2 * NOTE_MS + GAP_MS + 120).milliseconds)
    } finally {
        track.release()
    }
}

private fun synthesizeNote(samples: ShortArray, start: Int, midiNote: Int) {
    val frequency = 440.0 * 2.0.pow((midiNote - 69) / 12.0)
    val phaseIncrement = 2 * PI * frequency / SAMPLE_RATE
    for (index in 0 until NOTE_SAMPLES) {
        val time = index.toDouble() / SAMPLE_RATE
        val remaining = (NOTE_SAMPLES - index - 1).toDouble() / SAMPLE_RATE
        val attack = (time / 0.012).coerceAtMost(1.0)
        val release = (remaining / 0.06).coerceAtMost(1.0)
        val envelope = attack * release * exp(-3 * time)
        val phase = phaseIncrement * index
        val tone = sin(phase) + 0.28 * sin(2 * phase) + 0.1 * sin(3 * phase)
        samples[start + index] = (tone * envelope * Short.MAX_VALUE * 0.42).toInt().toShort()
    }
}
