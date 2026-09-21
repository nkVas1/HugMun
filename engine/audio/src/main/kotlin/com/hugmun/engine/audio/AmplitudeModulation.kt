/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The 40 Hz stimulus, synthesised sample by sample.
 *
 * ### Why amplitude modulation and not binaural beats
 *
 * The GENUS stimulus is sound that *physically pulses* forty times a second — an
 * amplitude-modulated carrier. Consumer "40 Hz gamma" tracks are overwhelmingly binaural
 * beats, which are an illusion constructed inside the auditory system from two slightly
 * detuned tones, and which drive a weaker and less reliable steady-state response. They
 * are not the intervention that was studied, and HugMun does not ship them.
 *
 * ### Why this needs no low-latency audio stack
 *
 * The stimulus is defined by its modulation *frequency*, and the modulation is baked into
 * the sample values: at 48 kHz one 40 Hz cycle is exactly 1200 samples. Output latency
 * shifts the stimulus in time but cannot change its frequency, so Oboe's latency
 * advantage buys nothing here and an NDK dependency would put a second language on the
 * measurement path. See ADR 0003.
 *
 * Everything in this file is pure Kotlin and is tested on the JVM by measuring the
 * modulation frequency out of the rendered signal.
 */
public class AmplitudeModulatedSynth(
    public val sampleRate: Int,
    public val carrier: Carrier,
    public val modulationHz: Double = GAMMA_HZ,
    modulationDepth: Double = FULL_DEPTH,
    public val amplitude: Double = DEFAULT_AMPLITUDE,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(modulationHz > 0.0 && modulationHz < sampleRate / 2.0) {
            "modulationHz must be below Nyquist"
        }
        require(modulationDepth in 0.0..1.0) { "modulationDepth must lie in [0, 1]" }
        require(amplitude in 0.0..1.0) { "amplitude must lie in [0, 1]" }
    }

    /**
     * Modulation depth, from 0 (a steady carrier) to 1 (the envelope reaches silence).
     *
     * GENUS uses full depth. It is exposed because the app ramps it from zero at the
     * start of a session rather than switching the stimulus on abruptly.
     */
    public var modulationDepth: Double = modulationDepth
        set(value) {
            require(value in 0.0..1.0) { "modulationDepth must lie in [0, 1]" }
            field = value
        }

    private var sampleIndex: Long = 0L

    /** Samples in one modulation cycle. Exactly 1200 at 48 kHz and 40 Hz. */
    public val samplesPerCycle: Double get() = sampleRate / modulationHz

    /**
     * Renders the next [buffer].size samples, continuing the phase from the previous call.
     *
     * Phase continuity across buffers is not a nicety: a discontinuity at every buffer
     * boundary would inject a broadband click at the buffer rate, which is both audible
     * and a second, uncontrolled stimulus.
     */
    public fun render(buffer: FloatArray) {
        val omega = TWO_PI * modulationHz / sampleRate
        val depth = modulationDepth

        for (i in buffer.indices) {
            val phase = omega * sampleIndex
            // Raised cosine: at full depth the envelope sweeps 0 → 1 → 0 each cycle,
            // which is 100 % modulation without ever going negative (a negative envelope
            // would invert the carrier and halve the effective modulation frequency).
            val envelope = 1.0 - depth + depth * (1.0 - cos(phase)) / 2.0
            buffer[i] = (carrier.next() * envelope * amplitude).toFloat()
            sampleIndex++
        }
    }

    /** Restarts the modulation phase. Only for starting a new session. */
    public fun reset() {
        sampleIndex = 0L
        carrier.reset()
    }

    /** The modulation envelope at a given sample, for tests and for the visual channel. */
    public fun envelopeAt(sample: Long): Double {
        val phase = TWO_PI * modulationHz * sample / sampleRate
        return 1.0 - modulationDepth + modulationDepth * (1.0 - cos(phase)) / 2.0
    }

    public companion object {
        /** The frequency the GENUS work is built on. */
        public const val GAMMA_HZ: Double = 40.0

        public const val FULL_DEPTH: Double = 1.0

        /**
         * Headroom below full scale.
         *
         * Leaves room for the unmodulated comfort bed to be mixed in without clipping,
         * and keeps the output well inside the level cap described in SAFETY.md.
         */
        public const val DEFAULT_AMPLITUDE: Double = 0.35

        private const val TWO_PI = 2.0 * PI
    }
}

/** What is being modulated. */
public interface Carrier {
    /** The next carrier sample, nominally in [-1, 1]. */
    public fun next(): Double

    public fun reset()
}

/**
 * A pure tone.
 *
 * Simple and exact, but a 100 %-modulated pure tone is fatiguing over a 30–60 minute
 * session. Kept mainly for tests, where a known spectrum makes the analysis clean.
 */
public class ToneCarrier(private val sampleRate: Int, private val frequencyHz: Double) : Carrier {
    private var index = 0L

    override fun next(): Double {
        val value = sin(2.0 * PI * frequencyHz * index / sampleRate)
        index++
        return value
    }

    override fun reset() {
        index = 0L
    }
}

/**
 * Band-limited noise, centred where presbycusic hearing still has usable sensitivity.
 *
 * Chosen over a pure tone for tolerability: it drives a comparable auditory steady-state
 * response while being far easier to sit with for the length of a session. The band is
 * deliberately below the region where age-related high-frequency loss is steepest, so
 * the stimulus does not become quieter for exactly the listener it is meant for.
 */
public class BandLimitedNoiseCarrier(
    sampleRate: Int,
    seed: Long = DEFAULT_SEED,
    lowHz: Double = DEFAULT_LOW_HZ,
    highHz: Double = DEFAULT_HIGH_HZ,
) : Carrier {
    private val initialSeed = seed
    private var source = Random(seed)

    private val centreHz = sqrt(lowHz * highHz)
    private val q = centreHz / (highHz - lowHz)

    // Two cascaded band-pass sections: a single biquad has skirts shallow enough that a
    // meaningful amount of energy lands outside the intended band.
    private val first = Biquad.bandPass(sampleRate, centreHz, q)
    private val second = Biquad.bandPass(sampleRate, centreHz, q)

    /**
     * Make-up gain, measured rather than guessed.
     *
     * Band-pass filtering removes most of the energy of white noise, so the result needs
     * lifting — but a fixed constant would either waste headroom or clip. Clipping is not
     * a cosmetic problem here: it generates broadband harmonics, which would put energy
     * outside the intended band and add an uncontrolled component to a stimulus whose
     * whole point is a controlled spectrum. So the gain is calibrated at construction by
     * running the filter chain over a deterministic warm-up and measuring the peak.
     */
    private val makeUpGain: Double = calibrateGain(sampleRate)

    override fun next(): Double = second.process(first.process(source.nextDouble(-1.0, 1.0))) * makeUpGain

    override fun reset() {
        source = Random(initialSeed)
        first.reset()
        second.reset()
    }

    private fun calibrateGain(sampleRate: Int): Double {
        val probeSource = Random(initialSeed)
        val probeFirst = Biquad.bandPass(sampleRate, centreHz, q)
        val probeSecond = Biquad.bandPass(sampleRate, centreHz, q)

        var peak = 0.0
        repeat(sampleRate) {
            val value = probeSecond.process(probeFirst.process(probeSource.nextDouble(-1.0, 1.0)))
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }

        return if (peak <= 0.0) 1.0 else TARGET_PEAK / peak
    }

    private companion object {
        const val DEFAULT_SEED = 20260921L
        const val DEFAULT_LOW_HZ = 1_000.0
        const val DEFAULT_HIGH_HZ = 4_000.0

        /** Just under full scale, leaving room for the modulation envelope on top. */
        const val TARGET_PEAK = 0.95
    }
}

/**
 * A second-order IIR section, direct form I.
 *
 * Written out rather than pulled in, because it is twenty lines and it sits on the
 * stimulus path. Coefficients follow the Audio EQ Cookbook.
 */
public class Biquad(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    public fun process(input: Double): Double {
        val output = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = input
        y2 = y1
        y1 = output
        return output
    }

    public fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    public companion object {
        /** Constant-skirt band-pass with unity peak gain. */
        public fun bandPass(sampleRate: Int, centreHz: Double, q: Double): Biquad {
            require(centreHz > 0.0 && centreHz < sampleRate / 2.0) { "centreHz must be below Nyquist" }
            require(q > 0.0) { "q must be positive" }

            val omega = 2.0 * PI * centreHz / sampleRate
            val alpha = sin(omega) / (2.0 * q)
            val a0 = 1.0 + alpha

            return Biquad(
                b0 = alpha / a0,
                b1 = 0.0,
                b2 = -alpha / a0,
                a1 = (-2.0 * cos(omega)) / a0,
                a2 = (1.0 - alpha) / a0,
            )
        }

        /** Single-pole low-pass, used for envelope smoothing. */
        public fun onePoleCoefficient(sampleRate: Int, timeConstantSeconds: Double): Double =
            exp(-1.0 / (sampleRate * timeConstantSeconds))
    }
}
