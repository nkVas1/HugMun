/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.visuals

import androidx.compose.runtime.withFrameNanos
import com.hugmun.engine.psychophysics.DisplayTiming
import kotlin.math.roundToInt

/**
 * Presents something for an exact number of display frames, and reports honestly on
 * whether it managed to.
 *
 * `withFrameNanos` is Compose's own hook into `Choreographer`, so the timestamps here are
 * the platform's frame times rather than anything we have invented. Counting frames — not
 * sleeping for a duration — is the only way to present a stimulus for a known length of
 * time, because a display can only ever show something for a whole number of frames.
 *
 * The presenter does not draw anything. The caller flips a state, this counts frames, and
 * the verdict comes back with enough timing detail for the session record to be audited
 * later. See ADR 0004.
 */
public class FramePresenter(
    private val timing: DisplayTiming,
    /**
     * How much longer than one frame period an interval may run before the frame counts
     * as dropped.
     *
     * 1.5 sits between a frame that arrived a little late and one that was genuinely
     * missed. Tighter than this and ordinary jitter on a busy device would discard most
     * trials; looser and a real drop would slip through as a valid measurement.
     */
    private val dropToleranceFactor: Double = DEFAULT_DROP_TOLERANCE,
) {
    /**
     * Suspends for exactly [frames] display frames.
     *
     * @param onFirstFrame invoked with the presentation time of the first frame, for
     *   callers that need to align something else — for example the audio clock — to the
     *   moment the stimulus appeared.
     */
    public suspend fun presentFrames(frames: Int, onFirstFrame: ((Long) -> Unit)? = null): Presentation {
        require(frames >= 1) { "frames must be at least 1, was $frames" }

        val frameNanos = (timing.frameMillis * NANOS_PER_MILLI).toLong()
        val tolerance = (frameNanos * dropToleranceFactor).toLong()

        var shown = 0
        var previous = 0L
        var firstFrameNanos = 0L
        var lastFrameNanos = 0L
        var longestIntervalNanos = 0L
        var droppedFrames = 0

        while (shown < frames) {
            withFrameNanos { now ->
                if (shown == 0) {
                    firstFrameNanos = now
                    onFirstFrame?.invoke(now)
                } else {
                    val interval = now - previous
                    if (interval > longestIntervalNanos) longestIntervalNanos = interval
                    if (interval > tolerance) {
                        // Integer division: an interval of 2.4 frame periods means one
                        // frame was missed, not 1.4 of one.
                        droppedFrames += ((interval / frameNanos) - 1).toInt().coerceAtLeast(1)
                    }
                }
                previous = now
                lastFrameNanos = now
                shown++
            }
        }

        // The stimulus remains on screen through the frame that follows the last one we
        // counted, so the true visible span ends one frame period after the last callback.
        val elapsedNanos = (lastFrameNanos - firstFrameNanos) + frameNanos

        return Presentation(
            requestedFrames = frames,
            observedFrames = shown,
            droppedFrames = droppedFrames,
            actualMillis = elapsedNanos / NANOS_PER_MILLI,
            requestedMillis = timing.framesToMillis(frames),
            longestIntervalMillis = longestIntervalNanos / NANOS_PER_MILLI,
            firstFrameNanos = firstFrameNanos,
        )
    }

    /**
     * Suspends for approximately [millis], aligned to frame boundaries.
     *
     * Used for fixation and mask intervals, where a frame either way does not matter and
     * accuracy is not claimed.
     */
    public suspend fun presentApproximately(millis: Int): Presentation {
        val frames = (millis / timing.frameMillis).roundToInt().coerceAtLeast(1)
        return presentFrames(frames)
    }

    /** What actually happened on screen. */
    public data class Presentation(
        public val requestedFrames: Int,
        public val observedFrames: Int,
        public val droppedFrames: Int,
        public val actualMillis: Double,
        public val requestedMillis: Double,
        public val longestIntervalMillis: Double,
        /** Frame time of the first presented frame, for cross-modal alignment. */
        public val firstFrameNanos: Long,
    ) {
        /**
         * Whether this presentation is fit to be scored.
         *
         * A trial that failed this did not measure anything, and the staircase must not
         * be allowed to move on it.
         */
        public val isAccurate: Boolean get() = droppedFrames == 0

        public val errorMillis: Double get() = actualMillis - requestedMillis
    }

    public companion object {
        public const val DEFAULT_DROP_TOLERANCE: Double = 1.5
        private const val NANOS_PER_MILLI = 1_000_000.0
    }
}
