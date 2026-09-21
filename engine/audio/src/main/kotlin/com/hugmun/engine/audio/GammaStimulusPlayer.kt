/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays the 40 Hz amplitude-modulated stimulus.
 *
 * The synthesis lives in [AmplitudeModulatedSynth] and is tested on the JVM; this class
 * is only the plumbing that gets those samples to the speaker, plus the two things that
 * genuinely need the platform: the device's native sample rate, and the audio clock that
 * the visual channel phase-locks to.
 *
 * Safety behaviour that is part of the contract, not an afterthought:
 *
 * - **Depth ramps from zero** over [rampSeconds]. The stimulus never starts abruptly.
 * - **[stop] ramps down** rather than cutting, unless [immediate] is set — which is what
 *   the abort control uses.
 * - Output level is capped in the synth's amplitude, and the app additionally tracks
 *   cumulative listening time.
 */
public class GammaStimulusPlayer(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    public val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    public val targetDepth: Double = AmplitudeModulatedSynth.FULL_DEPTH,
    public val rampSeconds: Double = DEFAULT_RAMP_SECONDS,
    carrierFactory: (Int) -> Carrier = { BandLimitedNoiseCarrier(it) },
) {
    private val synth = AmplitudeModulatedSynth(
        sampleRate = sampleRate,
        carrier = carrierFactory(sampleRate),
        modulationDepth = 0.0,
    )

    private var track: AudioTrack? = null
    private var job: Job? = null

    /** Whether the stimulus is currently being written to the output. */
    public val isPlaying: Boolean get() = job?.isActive == true

    /**
     * The audio clock, for aligning the visual channel.
     *
     * Returns the nanosecond system time at which a given audio frame reaches the output,
     * or null when the platform cannot say. This — not low latency — is how audio–visual
     * phase alignment is achieved; see ADR 0003.
     */
    public fun audioClock(): AudioEpoch? {
        val current = track ?: return null
        val timestamp = AudioTimestamp()
        if (!current.getTimestamp(timestamp)) return null
        return AudioEpoch(
            framePosition = timestamp.framePosition,
            nanoTime = timestamp.nanoTime,
            sampleRate = sampleRate,
        )
    }

    public fun start() {
        if (isPlaying) return

        val bufferSize = bufferSizeInBytes()
        val created = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // MEDIA rather than a game or notification usage: this is content the
                    // user chose to listen to, and it should follow the media volume they
                    // are used to adjusting.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferSize)
            // Unconditional: PERFORMANCE_MODE_LOW_LATENCY arrived in API 26, which is
            // this project's minimum. Low latency is not needed for a 40 Hz modulation
            // (see the README), but a shorter buffer makes the ramp smoother.
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        synth.reset()
        synth.modulationDepth = 0.0
        track = created
        created.play()

        job = scope.launch(dispatcher) {
            val frames = bufferSize / BYTES_PER_FLOAT / RAMP_BUFFER_DIVISOR
            val buffer = FloatArray(frames.coerceAtLeast(MIN_BUFFER_FRAMES))
            var written = 0L

            // The writer loop allocates nothing and takes no locks, per Oboe's callback
            // discipline — the same rules apply to an AudioTrack writer thread.
            while (coroutineContext.isActive) {
                coroutineContext.ensureActive()
                synth.modulationDepth = rampedDepth(written)
                synth.render(buffer)
                val result = created.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
                if (result < 0) break
                written += buffer.size
            }
        }
    }

    /**
     * Stops the stimulus.
     *
     * @param immediate true for the abort control, which must silence output within one
     *   buffer and never waits for a fade.
     */
    public suspend fun stop(immediate: Boolean = false) {
        val current = track
        val running = job

        if (!immediate && current != null && running?.isActive == true) {
            val steps = RAMP_DOWN_STEPS
            val startDepth = synth.modulationDepth
            for (step in 1..steps) {
                synth.modulationDepth = startDepth * (1.0 - step.toDouble() / steps)
                delay(RAMP_DOWN_STEP_MILLIS)
            }
        }

        running?.cancel()
        job = null

        current?.runCatching {
            pause()
            flush()
            stop()
            release()
        }
        track = null
    }

    private fun rampedDepth(framesWritten: Long): Double {
        val rampFrames = (rampSeconds * sampleRate).toLong().coerceAtLeast(1L)
        if (framesWritten >= rampFrames) return targetDepth
        val progress = framesWritten.toDouble() / rampFrames
        // Smoothstep rather than linear: the onset should have no perceptible corner.
        val eased = progress * progress * (SMOOTHSTEP_A - SMOOTHSTEP_B * progress)
        return targetDepth * eased
    }

    private fun bufferSizeInBytes(): Int {
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val safe = if (minimum > 0) minimum else sampleRate * BYTES_PER_FLOAT / FALLBACK_DIVISOR
        return min(safe * BUFFER_MULTIPLE, sampleRate * BYTES_PER_FLOAT)
    }

    /** A point at which a known audio frame reaches the output. */
    public data class AudioEpoch(
        public val framePosition: Long,
        public val nanoTime: Long,
        public val sampleRate: Int,
    ) {
        /**
         * The modulation phase, in turns, at a given system time.
         *
         * The visual channel uses this to stay locked to the audio rather than to its own
         * clock, which is what makes the two channels the combined stimulus that was
         * studied rather than two independent ones that drift apart.
         */
        public fun phaseAt(systemNanos: Long, modulationHz: Double): Double {
            val elapsedSeconds = (systemNanos - nanoTime) / NANOS_PER_SECOND
            val framesSinceEpoch = framePosition + elapsedSeconds * sampleRate
            val cycles = framesSinceEpoch * modulationHz / sampleRate
            return cycles - kotlin.math.floor(cycles)
        }

        private companion object {
            const val NANOS_PER_SECOND = 1_000_000_000.0
        }
    }

    public companion object {
        /**
         * 48 kHz unless the device says otherwise.
         *
         * Chosen because 48 000 / 40 is exactly 1200, so one modulation cycle is a whole
         * number of samples and the phase never accumulates rounding error. Callers
         * should pass the device's native rate when it differs; the synthesis is correct
         * either way, but an exact division is tidier.
         */
        public const val DEFAULT_SAMPLE_RATE: Int = 48_000

        /** Onset ramp. SAFETY.md requires at least 20 s for the visual channel. */
        public const val DEFAULT_RAMP_SECONDS: Double = 20.0

        /** Reads the device's preferred output sample rate, falling back to 48 kHz. */
        public fun nativeSampleRate(audioManager: AudioManager): Int =
            audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_SAMPLE_RATE

        private const val BYTES_PER_FLOAT = 4
        private const val BUFFER_MULTIPLE = 2
        private const val RAMP_BUFFER_DIVISOR = 2
        private const val MIN_BUFFER_FRAMES = 256
        private const val FALLBACK_DIVISOR = 10
        private const val RAMP_DOWN_STEPS = 20
        private const val RAMP_DOWN_STEP_MILLIS = 50L

        /** Coefficients of the classic smoothstep, 3t^2 - 2t^3. */
        private const val SMOOTHSTEP_A = 3.0
        private const val SMOOTHSTEP_B = 2.0
    }
}
