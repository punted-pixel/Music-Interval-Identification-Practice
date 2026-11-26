package com.example.musicintervaltrainer

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.musicintervaltrainer.ui.theme.MusicIntervalTrainerTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

internal enum class Interval(val semitones: Int, @get:StringRes val label: Int) {
    UNISON(0, R.string.unison),
    MINOR_SECOND(1, R.string.minor_second),
    MAJOR_SECOND(2, R.string.major_second),
    MINOR_THIRD(3, R.string.minor_third),
    MAJOR_THIRD(4, R.string.major_third),
    PERFECT_FOURTH(5, R.string.perfect_fourth),
    TRITONE(6, R.string.tritone),
    PERFECT_FIFTH(7, R.string.perfect_fifth),
    MINOR_SIXTH(8, R.string.minor_sixth),
    MAJOR_SIXTH(9, R.string.major_sixth),
    MINOR_SEVENTH(10, R.string.minor_seventh),
    MAJOR_SEVENTH(11, R.string.major_seventh),
    OCTAVE(12, R.string.octave)
}

internal enum class Direction(@get:StringRes val label: Int) {
    UP(R.string.direction_up),
    DOWN(R.string.direction_down),
    MIXED(R.string.direction_mixed)
}

internal data class Question(val firstNote: Int, val interval: Interval, val ascending: Boolean) {
    val secondNote: Int
        get() = if (ascending) firstNote + interval.semitones else firstNote - interval.semitones

    companion object {
        val Saver = listSaver(
            save = { question: Question -> listOf(question.firstNote, question.interval.name, question.ascending) },
            restore = { Question(it[0] as Int, Interval.valueOf(it[1] as String), it[2] as Boolean) }
        )

        fun random(
            direction: Direction,
            previous: Question? = null,
            random: Random = Random.Default
        ): Question {
            var question: Question
            do {
                val ascending = when (direction) {
                    Direction.UP -> true
                    Direction.DOWN -> false
                    Direction.MIXED -> random.nextBoolean()
                }
                question = Question(random.nextInt(60, 73), Interval.entries.random(random), ascending)
            } while (
                previous != null &&
                question.firstNote == previous.firstNote &&
                question.secondNote == previous.secondNote
            )
            return question
        }
    }
}

internal data class PracticeScore(val answered: Int = 0, val correct: Int = 0) {
    fun recordAnswer(question: Question, selected: Interval): PracticeScore = PracticeScore(
        answered = answered + 1,
        correct = if (question.interval == selected) correct + 1 else correct
    )

    companion object {
        val Saver = listSaver(
            save = { score: PracticeScore -> listOf(score.answered, score.correct) },
            restore = { PracticeScore(answered = it[0], correct = it[1]) }
        )
    }
}

@Composable
internal fun IntervalTrainer(modifier: Modifier = Modifier) {
    var direction by rememberSaveable { mutableStateOf(Direction.MIXED) }
    var question by rememberSaveable(stateSaver = Question.Saver) {
        mutableStateOf(Question.random(direction))
    }
    var answer by rememberSaveable { mutableStateOf<Interval?>(null) }
    var heardNotes by rememberSaveable { mutableStateOf(false) }
    var score by rememberSaveable(stateSaver = PracticeScore.Saver) {
        mutableStateOf(PracticeScore())
    }
    var playing by remember { mutableStateOf(false) }
    var playbackError by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var playbackJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) playbackJob?.cancel()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            playbackJob?.cancel()
        }
    }

    fun startRound(nextDirection: Direction) {
        direction = nextDirection
        question = Question.random(nextDirection, previous = question)
        heardNotes = false
        answer = null
        playbackError = false
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.practice_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.practice_score, score.correct, score.answered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DirectionPicker(
                direction = direction,
                enabled = !playing,
                onSelect = { if (it != direction) startRound(it) }
            )

            ListenCard(
                heardNotes = heardNotes,
                playing = playing,
                playbackError = playbackError,
                onPlay = {
                    if (!playing && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                        val notes = question
                        playing = true
                        playbackError = false
                        playbackJob = scope.launch {
                            try {
                                playInterval(notes.firstNote, notes.secondNote)
                                heardNotes = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: RuntimeException) {
                                Log.w("IntervalTrainer", "Interval playback failed", failure)
                                playbackError = true
                            } finally {
                                playing = false
                            }
                        }
                    }
                }
            )

            AnswerChoices(
                enabled = heardNotes && !playing && answer == null,
                answer = answer,
                correctInterval = question.interval,
                onAnswer = { selected ->
                    if (answer == null && heardNotes && !playing) {
                        answer = selected
                        score = score.recordAnswer(question, selected)
                    }
                }
            )

            answer?.let { selected ->
                val resultRequester = remember { BringIntoViewRequester() }
                LaunchedEffect(resultRequester) {
                    resultRequester.bringIntoView()
                }
                Column(
                    modifier = Modifier.bringIntoViewRequester(resultRequester),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoundResult(question = question, wasCorrect = selected == question.interval)
                    Button(
                        onClick = {
                            startRound(direction)
                            scope.launch { scrollState.animateScrollTo(0) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        enabled = !playing
                    ) {
                        Text(stringResource(R.string.next_interval))
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectionPicker(direction: Direction, enabled: Boolean, onSelect: (Direction) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.note_direction),
            style = MaterialTheme.typography.labelLarge
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            Direction.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = direction == option,
                    onClick = { onSelect(option) },
                    enabled = enabled,
                    shape = SegmentedButtonDefaults.itemShape(index, Direction.entries.size),
                    label = { Text(stringResource(option.label)) }
                )
            }
        }
    }
}

@Composable
private fun ListenCard(
    heardNotes: Boolean,
    playing: Boolean,
    playbackError: Boolean,
    onPlay: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SoundWave(playing = playing)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.listen_title),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.listen_prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
            Button(onClick = onPlay, enabled = !playing, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(
                    stringResource(
                        when {
                            playing -> R.string.playing_notes
                            heardNotes -> R.string.replay_notes
                            else -> R.string.play_notes
                        }
                    )
                )
            }
            if (playbackError) {
                Text(
                    text = stringResource(R.string.playback_error),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                )
            }
        }
    }
}

private val waveHeights = listOf(2, 4, 8, 14, 9, 5, 2, 2, 5, 11, 16, 10, 5, 2)

@Composable
private fun SoundWave(playing: Boolean) {
    val color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (playing) 1f else 0.65f)
    Canvas(
        modifier = Modifier
            .width(114.dp)
            .height(36.dp)
    ) {
        val spacing = size.width / waveHeights.size
        waveHeights.forEachIndexed { index, height ->
            val x = (index + 0.5f) * spacing
            val amplitude = size.height * height / 36
            drawLine(
                color = color,
                start = Offset(x, center.y - amplitude),
                end = Offset(x, center.y + amplitude),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun AnswerChoices(
    enabled: Boolean,
    answer: Interval?,
    correctInterval: Interval,
    onAnswer: (Interval) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.choose_interval),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() }
        )
        Interval.entries.chunked(2).forEach { intervals ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                intervals.forEach { interval ->
                    val highlighted = answer != null && (interval == answer || interval == correctInterval)
                    OutlinedButton(
                        onClick = { onAnswer(interval) },
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        colors = if (highlighted && interval == correctInterval) {
                            ButtonDefaults.outlinedButtonColors(
                                disabledContainerColor = colors.secondaryContainer,
                                disabledContentColor = colors.onSecondaryContainer
                            )
                        } else if (highlighted) {
                            ButtonDefaults.outlinedButtonColors(
                                disabledContainerColor = colors.errorContainer,
                                disabledContentColor = colors.onErrorContainer
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors(disabledContentColor = colors.onSurfaceVariant)
                        }
                    ) {
                        Text(stringResource(interval.label), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundResult(question: Question, wasCorrect: Boolean) {
    val semitones = question.interval.semitones
    val distance = pluralStringResource(R.plurals.semitones, semitones, semitones)
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (wasCorrect) colors.secondaryContainer else colors.errorContainer,
            contentColor = if (wasCorrect) colors.onSecondaryContainer else colors.onErrorContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(
                    if (wasCorrect) R.string.answer_correct else R.string.answer_incorrect,
                    stringResource(question.interval.label)
                ),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    when {
                        question.interval == Interval.UNISON -> R.string.unison_interval
                        question.ascending -> R.string.ascending_interval
                        else -> R.string.descending_interval
                    },
                    distance
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 800)
@Composable
private fun IntervalTrainerPreview() {
    MusicIntervalTrainerTheme {
        Scaffold { padding ->
            IntervalTrainer(modifier = Modifier.padding(padding))
        }
    }
}
