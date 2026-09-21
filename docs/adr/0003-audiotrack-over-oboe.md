# ADR 0003 — AudioTrack over Oboe for 40 Hz synthesis

**Status:** Accepted · 2026-09-21

## Context

The «Ритм» practice emits a 40 Hz amplitude-modulated stimulus. The reflex answer for
precise audio on Android is Oboe: a C++/NDK library that gets you the lowest achievable
output latency.

## Decision

Generate the waveform sample-by-sample in Kotlin and write it to a streaming
`AudioTrack` through a lock-free ring buffer. No NDK.

## Reasoning

The stimulus is defined by its **modulation frequency**, and the modulation is baked into
the sample values. At 48 kHz, one 40 Hz cycle is exactly 1200 samples — the frequency is
correct by construction. Output latency shifts the stimulus in time; it does not alter
its frequency. So Oboe's latency advantage buys nothing for the stimulus itself, while
an NDK dependency costs build complexity and puts a second language on the measurement
path.

Latency *does* matter for audio–visual phase alignment when both channels run. That is
solved more exactly and more cheaply by reading `AudioTrack.getTimestamp()` — which gives
a framePosition/nanoTime pair — and phase-locking the visual channel to the audio clock,
rather than by trying to minimise latency and hoping.

Oboe's callback discipline still applies to our writer thread: no allocation, no locks,
no blocking in the audio path.

## Consequences

- Pure-Kotlin, single-language codebase.
- Measured audio–visual phase error is logged per session; sustained error above a
  quarter cycle disables the visual channel and tells the user why.
- If a future stimulus were defined by its *onset latency* rather than its frequency —
  for example an auditory reaction-time measure — this decision would not hold for it,
  and that measure would need a different approach.
