/*
 * Copyright 2026 HugMun Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.hugmun.engine.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stimulus is only the studied stimulus if it actually pulses at 40 Hz.
 *
 * So rather than asserting on the code that generates it, these tests **measure the
 * rendered signal**: they pull the envelope out of the waveform and look for energy at
 * 40 Hz using a Goertzel filter. If someone changed the synthesis and the modulation
 * frequency drifted, nothing else in the app would notice.
 */
class AmplitudeModulationTest {

    private val sampleRate = 48_000

    private fun synth(depth: Double = 1.0, modulationHz: Double = 40.0, carrierHz: Double = 2_000.0) =
        AmplitudeModulatedSynth(
            sampleRate = sampleRate,
            carrier = ToneCarrier(sampleRate, carrierHz),
            modulationHz = modulationHz,
            modulationDepth = depth,
            amplitude = 1.0,
        )

    @Test
    fun `at 48 kHz one 40 Hz cycle is exactly 1200 samples`() {
        assertEquals(1_200.0, synth().samplesPerCycle, 1e-12)
    }

    /**
     * The test that matters: the modulation frequency measured out of the signal.
     *
     * The envelope is recovered by rectifying the waveform, then a Goertzel filter
     * compares energy at 40 Hz against neighbouring frequencies.
     */
    @Test
    fun `the rendered signal is modulated at 40 Hz and not at its neighbours`() {
        val samples = renderSeconds(synth(), seconds = 1.0)
        val envelope = FloatArray(samples.size) { abs(samples[it]) }

        val at40 = goertzelMagnitude(envelope, sampleRate, 40.0)
        val at20 = goertzelMagnitude(envelope, sampleRate, 20.0)
        val at30 = goertzelMagnitude(envelope, sampleRate, 30.0)
        val at60 = goertzelMagnitude(envelope, sampleRate, 60.0)
        val at80 = goertzelMagnitude(envelope, sampleRate, 80.0)

        assertTrue("40 Hz ($at40) must dominate 20 Hz ($at20)", at40 > at20 * 10)
        assertTrue("40 Hz ($at40) must dominate 30 Hz ($at30)", at40 > at30 * 10)
        assertTrue("40 Hz ($at40) must dominate 60 Hz ($at60)", at40 > at60 * 10)
        assertTrue("40 Hz ($at40) must dominate 80 Hz ($at80)", at40 > at80 * 5)
    }

    /**
     * A raised-cosine envelope never goes negative.
     *
     * A sinusoidal envelope that crossed zero would invert the carrier on alternate
     * half-cycles, and the *perceived* pulse rate would be 80 Hz rather than 40 Hz — the
     * classic way to ship a stimulus at twice the intended frequency without noticing.
     */
    @Test
    fun `the envelope never inverts the carrier`() {
        val s = synth(depth = 1.0)
        for (sample in 0L until 10_000L) {
            val envelope = s.envelopeAt(sample)
            assertTrue("envelope went negative at $sample: $envelope", envelope >= 0.0)
            assertTrue("envelope exceeded unity at $sample: $envelope", envelope <= 1.0 + 1e-12)
        }
    }

    @Test
    fun `full depth reaches silence and unity within one cycle`() {
        val s = synth(depth = 1.0)
        val perCycle = s.samplesPerCycle.toLong()
        val values = (0L until perCycle).map { s.envelopeAt(it) }

        assertEquals("full depth must reach silence", 0.0, values.min(), 1e-9)
        assertEquals("full depth must reach unity", 1.0, values.max(), 1e-6)
    }

    @Test
    fun `zero depth is an unmodulated carrier`() {
        val s = synth(depth = 0.0)
        for (sample in 0L until 5_000L) {
            assertEquals(1.0, s.envelopeAt(sample), 1e-12)
        }
    }

    @Test
    fun `partial depth reduces modulation without removing it`() {
        val shallow = renderSeconds(synth(depth = 0.35), seconds = 0.5)
        val full = renderSeconds(synth(depth = 1.0), seconds = 0.5)

        val shallowEnergy = goertzelMagnitude(FloatArray(shallow.size) { abs(shallow[it]) }, sampleRate, 40.0)
        val fullEnergy = goertzelMagnitude(FloatArray(full.size) { abs(full[it]) }, sampleRate, 40.0)

        assertTrue("shallow modulation should carry less 40 Hz energy", shallowEnergy < fullEnergy)
        assertTrue("but it should still be modulated", shallowEnergy > 0.0)
    }

    /**
     * A phase discontinuity at each buffer boundary would inject a broadband click at the
     * buffer rate — audible, and a second uncontrolled stimulus on top of the first.
     */
    @Test
    fun `phase is continuous across buffer boundaries`() {
        val chunked = synth()
        val whole = synth()

        val bufferSize = 317 // deliberately not a divisor of the cycle length
        val total = sampleRate / 2
        val fromChunks = FloatArray(total)
        var written = 0
        while (written < total) {
            val size = minOf(bufferSize, total - written)
            val buffer = FloatArray(size)
            chunked.render(buffer)
            buffer.copyInto(fromChunks, written)
            written += size
        }

        val fromOneCall = FloatArray(total)
        whole.render(fromOneCall)

        for (i in 0 until total) {
            assertEquals("divergence at sample $i", fromOneCall[i], fromChunks[i], 1e-6f)
        }
    }

    @Test
    fun `output stays within full scale`() {
        val samples = renderSeconds(
            AmplitudeModulatedSynth(
                sampleRate = sampleRate,
                carrier = BandLimitedNoiseCarrier(sampleRate),
                amplitude = 1.0,
            ),
            seconds = 2.0,
        )
        val peak = samples.maxOf { abs(it) }
        assertTrue("peak $peak exceeded full scale", peak <= 1.0f)
    }

    @Test
    fun `the noise carrier keeps its energy inside the intended band`() {
        val carrier = BandLimitedNoiseCarrier(sampleRate)
        val samples = FloatArray(sampleRate) { carrier.next().toFloat() }

        val inBand = goertzelMagnitude(samples, sampleRate, 2_000.0)
        val wellBelow = goertzelMagnitude(samples, sampleRate, 120.0)
        val wellAbove = goertzelMagnitude(samples, sampleRate, 14_000.0)

        assertTrue("in-band ($inBand) should exceed 120 Hz ($wellBelow)", inBand > wellBelow * 3)
        assertTrue("in-band ($inBand) should exceed 14 kHz ($wellAbove)", inBand > wellAbove * 3)
    }

    @Test
    fun `reset restarts both the modulation and the carrier`() {
        val s = synth()
        val first = FloatArray(1_000).also { s.render(it) }
        s.reset()
        val second = FloatArray(1_000).also { s.render(it) }
        assertTrue("reset should reproduce the same samples", first.contentEquals(second))
    }

    @Test
    fun `invalid configuration is rejected rather than clamped`() {
        val tooDeep = runCatching {
            AmplitudeModulatedSynth(sampleRate, ToneCarrier(sampleRate, 1_000.0), modulationDepth = 1.5)
        }.exceptionOrNull()
        assertTrue(tooDeep is IllegalArgumentException)

        val aboveNyquist = runCatching {
            AmplitudeModulatedSynth(1_000, ToneCarrier(1_000, 100.0), modulationHz = 800.0)
        }.exceptionOrNull()
        assertTrue(aboveNyquist is IllegalArgumentException)
    }

    // --- helpers -------------------------------------------------------------------

    private fun renderSeconds(synth: AmplitudeModulatedSynth, seconds: Double): FloatArray {
        val buffer = FloatArray((sampleRate * seconds).toInt())
        synth.render(buffer)
        return buffer
    }

    /**
     * Goertzel: the energy of one frequency bin, without computing a whole FFT.
     *
     * Exactly the right tool here — we know which frequency we care about, and a
     * twenty-line filter is easier to trust than a transform library.
     */
    private fun goertzelMagnitude(samples: FloatArray, sampleRate: Int, frequencyHz: Double): Double {
        val omega = 2.0 * PI * frequencyHz / sampleRate
        val coefficient = 2.0 * cos(omega)

        // A Hann window first: without it, spectral leakage from the (very loud) in-band
        // energy dominates every out-of-band bin and the comparison measures nothing.
        var s1 = 0.0
        var s2 = 0.0
        val n = samples.size
        for (index in 0 until n) {
            val window = 0.5 - 0.5 * cos(2.0 * PI * index / (n - 1))
            val s0 = samples[index] * window + coefficient * s1 - s2
            s2 = s1
            s1 = s0
        }

        val real = s1 - s2 * cos(omega)
        val imaginary = s2 * sin(omega)
        return sqrt(real * real + imaginary * imaginary) / samples.size
    }
}
