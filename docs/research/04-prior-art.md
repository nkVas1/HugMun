# 04 — Prior Art

> What already exists, what we reuse, what we deliberately rewrite, and why.
>
> Principle: reuse an algorithm when it is well specified and well validated; reuse a
> *library* only when its behaviour is boring and its licence is clean. Anything on the
> measurement path we own, because we have to be able to explain every number.

---

## 1. Commercial landscape

| Product | What it does well | Why we do not simply copy it |
| --- | --- | --- |
| **BrainHQ** (Posit Science) | Contains *Double Decision*, the commercial descendant of the ACTIVE speed-training exercise, developed with Roenker's involvement and validated against the defining elements — divided attention, adaptivity, intensity, feedback | Closed source, subscription, English-first, visually loud, and it wraps the one Tier-A paradigm in a large catalogue of exercises with far weaker support |
| **Lumosity** | Excellent onboarding and retention design | FTC settled advertising claims about real-world benefit; the retention mechanics (streaks, scores, rank) are exactly what this population does not need |
| **CogniFit, NeuroNation, Peak** | Broad batteries, polished | Generic CCT — the category Cochrane rates low/very-low certainty [@gates2020healthy]; strong engagement design, weak evidence hygiene |
| **Cognito Therapeutics (Spectris)** | The 40 Hz GENUS stimulus as a regulated device, with a headset delivering controlled light and sound | A phone cannot replicate controlled photic delivery; we implement a deliberately reduced, consented approximation and say so |
| **Constant Therapy, Tactus** | Clinician-linked rehabilitation | Prescription/clinician model; different product category |

**The gap HugMun occupies:** an open-source, local-first, Russian-language application
built around the *specific* paradigms that survive scrutiny — with the booster schedule
that turned out to be the active ingredient [@coe2026active] — for a user who is 83 and
deserves to be addressed as an adult.

---

## 2. Open-source cognitive task implementations

Surveyed: `freefocusgames` / `brain-training-games` (Next.js), `DriftLab` (browser tasks
+ drift-diffusion fitting in Python), `ebal/brain` (Vue 3 PWA with Stroop, Schulte,
N-back, sequence memory, switch trail), OpenSesame task collections, PsychoPy/jsPsych
paradigm libraries, `joncompton/field-vision-trainer` (browser UFOV trainer).

**Verdict: read, do not depend.**

Reasons, in order of importance:

1. **Timing.** Every browser implementation inherits browser timing. A UFOV threshold is
   a *presentation duration*; `requestAnimationFrame` plus compositor variability does
   not give us a defensible millisecond value, and none of these projects quantise to
   frames or discard trials with dropped frames.
2. **Psychophysics.** Most use fixed difficulty ladders or naïve N-up/N-down. None
   implement a weighted up–down converging on the 75 % criterion the UFOV literature
   specifies.
3. **Population.** All are designed for a young, self-motivated, screen-fluent user.

**What we did take:** the stimulus-design intuitions (mask timing, distractor density,
response mapping) and, from `DriftLab`, the idea of separating processing speed from
decision threshold — reflected in our decision to record trial-level data so that
drift-diffusion analysis is possible offline from the export.

Design principles for the tasks themselves come from the primary psychophysics
literature, not from these repositories [@ball1993ufov; @edwards2005ufov].

---

## 3. FSRS — reused, reimplemented

**Algorithm: reused. Implementation: ours.**

FSRS is an open-source DSR memory model with default parameters fitted on roughly
727 million reviews, outperforming SM-2 at equal retention [@fsrs]. Official ports exist
in many languages, including an official Kotlin implementation.

We implement FSRS-6 in `:engine:scheduling` from the published specification rather than
taking a dependency, for three concrete reasons:

1. We need clamps the reference implementations intentionally do not have — target
   retention 0.95, maximum interval 45 days, minimum interval 1 day (see
   [`02-intervention-specs.md`](02-intervention-specs.md) §4.3).
2. Our grade is *derived* from cue level and response latency, not self-reported, so the
   input contract differs.
3. The scheduler sits on the measurement path, and we will not have a number we cannot
   derive on a whiteboard.

The published FSRS-6 formulas we implement, for traceability:

```
  Initial stability      S₀(G)      = w[G−1]
  Initial difficulty     D₀(G)      = w₄ − e^(w₅·(G−1)) + 1
  Difficulty update      ΔD(G)      = −w₆·(G−3)
                         D′         = D + ΔD·(10−D)/9
                         D″         = w₇·D₀(4) + (1−w₇)·D′
  Forgetting curve       R(t,S)     = (1 + factor·t/S)^(−w₂₀),
                         factor     = 0.9^(−1/w₂₀) − 1
  Interval               I(r,S)     = S/factor · (r^(−1/w₂₀) − 1)
  Stability on recall    S′ᵣ        = S·(e^w₈·(11−D)·S^(−w₉)·(e^(w₁₀·(1−R))−1)
                                        ·w₁₅[G=2]·w₁₆[G=4] + 1)
  Stability on lapse     S′f        = w₁₁·D^(−w₁₂)·((S+1)^w₁₃ − 1)·e^(w₁₄·(1−R))
  Same-day review        S′(S,G)    = S·e^(w₁₇·(G−3+w₁₈))·S^(−w₁₉)
```

with the published 21-parameter FSRS-6 defaults. Attribution to Open Spaced Repetition
appears in `NOTICE`, in the source file, and in the in-app licences screen.

The within-session expanding-retrieval ladder is **not** FSRS — it is the Camp spaced
retrieval protocol [@camp1996spaced], which operates on seconds and minutes where FSRS
operates on days.

---

## 4. Camera PPG — reused ideas, own signal chain

Surveyed: `LensHRV/lenshrv-app`, `JanBancerewicz/PPGbetter`, `markolalovic/ppg-vitals`,
`kenxle/optical-heart-rate-monitor`, `YahyaOdeh/HealthWatcher`.

These establish that fingertip PPG over the rear camera with the torch on is a solved
enough problem to build on. They converge on the same pipeline we use: spatial mean of a
colour channel per frame → detrend → band-pass → peak detect → IBI series.

**What we do differently, and why it matters:**

| Common in surveyed projects | HugMun |
| --- | --- |
| Auto-exposure left enabled | Exposure, focus and white balance **locked** — auto-exposure actively cancels the pulsatile signal |
| Heart rate only | Full IBI series with artefact correction, RMSSD/SDNN, and 0.04–0.15 Hz LF power — required for the resonance sweep |
| No signal-quality gate | Per-window SQI; below threshold the UI says the pulse is not visible instead of showing a number |
| OpenCV dependency | Plain Kotlin over CameraX `ImageAnalysis` YUV planes. A spatial mean and a Butterworth band-pass do not justify a native CV dependency |

Rejected outright: any project estimating **blood pressure** or **SpO₂** from an RGB
camera. Those are not validated and are exactly the kind of claim that turns a wellness
app into an unapproved medical device.

---

## 5. Audio synthesis

Considered: Google **Oboe** (C++/NDK), Media3/ExoPlayer, raw `AudioTrack`.

**Chosen: `AudioTrack` with Kotlin-side synthesis.** The reasoning is worth stating
because it is counter-intuitive — low-latency audio is usually the reflex answer.

The stimulus is defined by its *modulation frequency*, and the modulation is baked into
the sample values. At 48 kHz one 40 Hz cycle is exactly 1200 samples. Output latency
shifts the stimulus in time; it does not alter its frequency. Therefore Oboe's latency
advantage buys nothing for the audio channel itself, and an NDK dependency would cost
build complexity and a second language on the measurement path.

Latency *does* matter for audio–visual phase alignment, and that is handled by reading
`AudioTrack.getTimestamp()` and phase-locking the visual channel to the audio clock (see
[`02-intervention-specs.md`](02-intervention-specs.md) §3.3) — a cheaper and more exact
solution than minimising latency.

Oboe's guidance on callback discipline still applies to our writer thread: no
allocation, no locks, no blocking in the audio path [@oboe].

---

## 6. Android platform techniques adopted

| Technique | Purpose | Source |
| --- | --- | --- |
| `Choreographer` frame callbacks with `frameTimeNanos` | Frame-accurate stimulus presentation and dropped-frame detection | Android frame-timing guidance |
| `Surface.setFrameRate` / `preferredDisplayModeId` | Selecting a refresh rate where `refresh mod 40 ≈ 0` | High-refresh-rate rendering guidance [@androidhighrefresh] |
| AGSL `RuntimeShader` | Luminance modulation and the grain/ink texture of the design language, on GPU | Android graphics |
| Baseline Profiles + `ProfileInstaller` | Cold-start and first-frame performance on a mid-range device | AndroidX Benchmark |
| `WorkManager` | The multi-year booster schedule, surviving reboots and Doze | AndroidX |
| Room 3 (`androidx.room3`) | Kotlin-first, KSP, coroutine-enforced persistence | Room 3.0 migration guide |
| Navigation 3 | Compose-first navigation with an explicit back stack | AndroidX |

---

## 7. Design references (not imitation)

The visual language is original. The references that informed it are deliberately
outside the health-app category:

- **Scientific instrument engraving** — tick marks, hairline rules, honest scales; the
  source of the data-display grammar.
- **Japanese sumi-e brushwork** — the single gestural raven mark; one confident stroke
  rather than an illustrated mascot.
- **Nordic manuscript colour** — parchment, iron-gall ink, cinnabar; the palette's
  emotional temperature.
- **Letterpress and enamel signage** — the tactile depth of primary controls, taken from
  the "Гнездо" direction.

Explicitly avoided, as clichés of the category: cartoon brains, glowing neural networks,
jigsaw pieces, lightbulbs, confetti, trophy cabinets, and the blue-gradient "medical
tech" palette.

---

## 8. Attribution policy

Any third-party code, algorithm or asset that reaches the repository is recorded in
`NOTICE` with its licence, and surfaced in the app's open-source licences screen. An
algorithm reimplemented from a published specification is credited to its authors even
where no licence obligation exists — @fsrs being the clearest example.
