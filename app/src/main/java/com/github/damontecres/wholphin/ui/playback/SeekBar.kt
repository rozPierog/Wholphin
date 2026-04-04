package com.github.damontecres.wholphin.ui.playback

/*
 * Modified from https://github.com/android/tv-samples
 *
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration

/**
 * This is a seek bar which seeks by a percentage of the duration instead of a fixed amount of time
 *
 * For example, if [intervals] is 10, then each seek will be 10% of total media duration
 */
@Composable
fun SteppedSeekBarImpl(
    progressProvider: () -> Float,
    durationMs: Long,
    bufferedProgressProvider: () -> Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    modifier: Modifier = Modifier,
    intervals: Int = 10,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    val offset = 1f / intervals

    SeekBarDisplay(
        enabled = enabled,
        progressProvider = {
            if (isFocused && hasSeeked) seekProgress else progressProvider()
        },
        bufferedProgressProvider = bufferedProgressProvider,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            val current = if (hasSeeked) seekProgress else progressProvider()
            seekProgress = (current - offset * multiplier).coerceAtLeast(0f)
            hasSeeked = true
            onSeek((seekProgress * durationMs).toLong())
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            val current = if (hasSeeked) seekProgress else progressProvider()
            seekProgress = (current + offset * multiplier).coerceAtMost(1f)
            hasSeeked = true
            onSeek((seekProgress * durationMs).toLong())
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

/**
 * A seek (or scrubber) bar which seeks forward or back a fixed amount of time per move
 */
@OptIn(FlowPreview::class)
@Composable
fun IntervalSeekBarImpl(
    progressProvider: () -> Float,
    durationMs: Long,
    bufferedProgressProvider: () -> Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    SeekBarDisplay(
        enabled = enabled,
        progressProvider = {
            if (isFocused && hasSeeked) {
                val d = durationMs.coerceAtLeast(1L)
                (seekPositionMs.toDouble() / d).toFloat()
            } else {
                progressProvider()
            }
        },
        bufferedProgressProvider = bufferedProgressProvider,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            val currentPos =
                if (hasSeeked) seekPositionMs else (progressProvider() * durationMs).toLong()
            seekPositionMs =
                (currentPos - seekBack.inWholeMilliseconds * multiplier).coerceAtLeast(0L)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            val currentPos =
                if (hasSeeked) seekPositionMs else (progressProvider() * durationMs).toLong()
            seekPositionMs =
                (currentPos + seekForward.inWholeMilliseconds * multiplier).coerceAtMost(durationMs)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

/**
 * Actually renders the seek bar. It has callbacks for when the user moves to the left or right
 *
 * @see IntervalSeekBarImpl
 * @see SteppedSeekBarImpl
 */
@Composable
private fun SeekBarDisplay(
    progressProvider: () -> Float,
    bufferedProgressProvider: () -> Float,
    durationMs: Long,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.border
    val onSurface = MaterialTheme.colorScheme.onSurface

    val isFocused by interactionSource.collectIsFocusedAsState()
    var leftHandledByRepeat by remember { mutableStateOf(false) }
    var rightHandledByRepeat by remember { mutableStateOf(false) }

    // Animate drawing properties instead of layout to prevent relayouts and clipping
    val animatedThickness by animateDpAsState(
        targetValue = if (isFocused) 12.dp else 6.dp,
        label = "SeekBarThickness",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(24.dp) // Fixed height to avoid clipping and relayout
                    .padding(horizontal = 4.dp)
                    .onPreviewKeyEvent { event ->
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                                when (event.type) {
                                    KeyEventType.KeyDown -> {
                                        val repeatCount = event.nativeKeyEvent.repeatCount
                                        if (repeatCount > 0) {
                                            leftHandledByRepeat = true
                                            onLeft.invoke(
                                                calculateSeekAccelerationMultiplier(
                                                    repeatCount = repeatCount,
                                                    durationMs = durationMs,
                                                ),
                                            )
                                        } else {
                                            leftHandledByRepeat = false
                                        }
                                    }

                                    KeyEventType.KeyUp -> {
                                        if (!leftHandledByRepeat) {
                                            onLeft.invoke(1)
                                        }
                                        leftHandledByRepeat = false
                                    }

                                    else -> {
                                        return@onPreviewKeyEvent false
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                                when (event.type) {
                                    KeyEventType.KeyDown -> {
                                        val repeatCount = event.nativeKeyEvent.repeatCount
                                        if (repeatCount > 0) {
                                            rightHandledByRepeat = true
                                            onRight.invoke(
                                                calculateSeekAccelerationMultiplier(
                                                    repeatCount = repeatCount,
                                                    durationMs = durationMs,
                                                ),
                                            )
                                        } else {
                                            rightHandledByRepeat = false
                                        }
                                    }

                                    KeyEventType.KeyUp -> {
                                        if (!rightHandledByRepeat) {
                                            onRight.invoke(1)
                                        }
                                        rightHandledByRepeat = false
                                    }

                                    else -> {
                                        return@onPreviewKeyEvent false
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }.focusable(enabled = enabled, interactionSource = interactionSource),
            onDraw = {
                val yOffset = size.height / 2
                val progress = progressProvider()
                val buffered = bufferedProgressProvider()
                val thicknessPx = animatedThickness.toPx()

                // Background
                drawLine(
                    color = onSurface.copy(alpha = 0.25f),
                    start = Offset(0f, yOffset),
                    end = Offset(size.width, yOffset),
                    strokeWidth = thicknessPx,
                    cap = StrokeCap.Round,
                )
                // Buffered
                drawLine(
                    color = onSurface.copy(alpha = 0.65f),
                    start = Offset(0f, yOffset),
                    end = Offset(size.width * buffered, yOffset),
                    strokeWidth = thicknessPx,
                    cap = StrokeCap.Round,
                )
                // Progress
                drawLine(
                    color = color,
                    start = Offset(0f, yOffset),
                    end = Offset(size.width * progress, yOffset),
                    strokeWidth = thicknessPx,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.White,
                    radius = thicknessPx + 2,
                    center = Offset(size.width * progress, yOffset),
                )
            },
        )
    }
}
