<div align="center">

# HugMun

**Evidence-graded cognitive training and sensory neuromodulation for older adults.**

Native Android · local-first · no account, no server, no telemetry by default

[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![AGP](https://img.shields.io/badge/AGP-9.4.1-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com/build)
[![minSdk](https://img.shields.io/badge/minSdk-26-555.svg)](https://developer.android.com)

[Русский](README.ru.md) · [Research dossier](docs/research) · [Design language](docs/design/design-language.md) · [Safety policy](docs/research/03-safety-and-regulatory.md)

</div>

---

> *Huginn and Muninn fly out at dawn over the wide earth and return by breakfast. Odin
> says he fears for Huginn — but trembles more for Muninn.*
> — Grímnismál, 20

Huginn is *thought*; Muninn is *memory*. The stanza is the product in four lines: a daily
flight out and back, and a particular fear of losing memory. HugMun is built for the
person waiting for Muninn to come home.

---

## What this is

HugMun is a **general-wellness** Android application that delivers a small number of
cognitive and physiological practices with unusually specific scientific support, on the
schedule the evidence actually says matters.

It is **not** a medical device, not a diagnostic tool, and not a treatment for dementia
or any other condition. See [`SAFETY.md`](SAFETY.md).

### Why it exists

The first deployment target is one person: a man born in 1943, using an Android phone,
reading Russian. Everything in the project — the 20 sp base type, the 56 dp touch
targets, the light-by-default theme, the refusal to use streaks or confetti — follows
from designing for him rather than for a screenshot.

---

## The uncomfortable premise

Two Cochrane reviews rate the certainty of evidence for *generic* computerised cognitive
training in older adults as **low to very low**
([healthy adults](https://www.cochrane.org/evidence/CD012277_computerised-cognitive-training-maintaining-cognitive-function-cognitively-healthy-people-late-life),
[MCI](https://www.cochrane.org/evidence/CD012279_computerised-cognitive-training-preventing-dementia-people-with-mild-cognitive-impairment)).
A generic brain-games app is not a defensible product.

What survives scrutiny is *specific paradigms at specific doses*, plus multidomain
lifestyle work. So that is all HugMun contains.

### The single most important finding in the dossier

In the 20-year analysis of the **ACTIVE** trial, speed-of-processing training:

| Arm | Hazard ratio for diagnosed dementia |
| --- | --- |
| Speed training **with** at least one booster block | **0.75** (95% CI 0.59–0.95) |
| Speed training **without** boosters | **1.01** (95% CI 0.81–1.27) — no effect |
| Memory training, reasoning training | no significant effect |

*Coe et al., 2026, [doi:10.1002/trc2.70197](https://doi.org/10.1002/trc2.70197)*

The schedule is not packaging around the exercise. **The schedule is the intervention.**
An app that lets someone train hard for six weeks and then drift away has faithfully
reproduced the arm that did nothing — which is why the booster protocol lives in
[`:engine:scheduling`](engine/scheduling) with a test that simulates four years of
adherence and asserts the user actually arrives at both booster blocks.

---

## What is in the app

Every practice carries an **evidence card** stating what was studied, in whom, what was
found, and what the limitation is. The tier is shown to the user in plain language.

| Practice | | Tier | Basis |
| --- | --- | :---: | --- |
| **Зоркость** | Speed of processing (UFOV paradigm) | **A** | ACTIVE trial; adaptive staircase on presentation duration |
| **Якорь** | Practical memory: names, places, routines | **B** | Spaced retrieval + errorless learning; FSRS-6 across days |
| **Дыхание** | Resonance-frequency breathing with HRV biofeedback | **B/C** | Individual resonance sweep; camera PPG |
| **Движение** | Multicomponent movement | **B** | Exercise network meta-analyses |
| **Ритм** | 40 Hz amplitude-modulated sensory stimulation | **C/D** | GENUS; **experimental, off by default, safety-gated** |
| **Замер** | Measurement battery | **A** (method) | Burst sampling; reliable change, not raw deltas |

Full reasoning, including what we deliberately **refused** to build and why, is in
[`docs/research/01-evidence-base.md`](docs/research/01-evidence-base.md).

---

## Engineering notes worth reading

A few decisions that are not obvious and are not the usual defaults.

### Frame-quantised stimulus timing

A UFOV threshold is a *presentation duration in milliseconds*. That number is meaningless
if the stimulus was not actually on screen that long, so nothing in
[`:engine:psychophysics`](engine/psychophysics) expresses a duration in milliseconds
without passing through the display's real frame period. Trials that drop a frame are
`DISCARDED` — recorded as a quality metric, never allowed to move the staircase.

### The staircase converges where the literature says it should

Weighted up–down (Kaernbach), with the step ratio derived from the zero-drift condition
`p·Δ_down = (1−p)·Δ_up`, giving exactly **1/3** at the UFOV 75 % criterion. A Monte Carlo
test recovers a known threshold from a simulated observer to within 20 %.

### 40 Hz is amplitude modulation, not binaural beats

The GENUS stimulus is light that physically flickers and sound that physically pulses.
Consumer "40 Hz gamma" tracks are overwhelmingly binaural beats, which are a different
thing and entrain less reliably. HugMun synthesises a true amplitude-modulated carrier.

The visual channel is **unavailable on 60 Hz and 90 Hz displays**, because 40 Hz cannot
be rendered there without beat artefacts, and a wrong-frequency flicker is both useless
and less safe than none.

### Contrast is a build gate

`ColorContrastTest` computes WCAG relative luminance for every foreground/background pair
the theme uses and fails the build below target. It caught a real defect on its first run.

### AudioTrack, deliberately not Oboe

The stimulus is defined by its modulation frequency, and the modulation is baked into the
sample values — at 48 kHz one 40 Hz cycle is exactly 1200 samples. Output latency shifts
the stimulus in time; it does not change its frequency. Low-latency audio buys nothing
here, so there is no NDK dependency. Audio–visual phase alignment is solved by reading
`AudioTrack.getTimestamp()` and locking the visual channel to the audio clock.

---

## Architecture

```
:app                    application shell, navigation, dependency graph
:core:model             pure Kotlin domain types
:core:common            dispatchers, time, result
:core:designsystem      the «Рассветный воздух» design language
:core:database          Room 3 (KSP, coroutines)
:core:datastore         preferences
:core:data              repository implementations
:core:domain            repository interfaces and use cases
:engine:psychophysics   adaptive staircases, thresholds, display timing
:engine:scheduling      FSRS-6 and the ACTIVE booster protocol
:engine:signal          PPG extraction, HRV metrics
:engine:audio           40 Hz amplitude-modulated synthesis
:engine:visuals         frame-locked photic stimulation
:feature:*              one module per screen group
```

The `:engine:*` modules are the measurement path and are **pure Kotlin wherever
possible**, so every number the app reports can be unit-tested on the JVM in
milliseconds, with no emulator and no Android framework in the way.

### Stack

Kotlin 2.4.20 · AGP 9.4.1 with built-in Kotlin · Gradle 9.7.1 · Jetpack Compose
(BOM 2026.09.00) · Material 3 · Navigation 3 · Room 3 · DataStore · WorkManager ·
CameraX · coroutines/Flow · Detekt · Spotless/ktlint · Baseline Profiles

No dependency-injection framework: the graph is small, explicit and hand-written, which
keeps codegen off the measurement path and keeps the wiring readable.
See [`docs/adr`](docs/adr).

---

## Building

```bash
git clone https://github.com/nkVas1/HugMun.git
cd HugMun

# Point at your SDK (or set ANDROID_HOME)
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug
./gradlew qualityCheck      # spotless + detekt + lint + unit tests
```

Requires JDK 17+ (the build uses a JDK 21 toolchain and targets bytecode 17).

---

## Privacy

There is no server. There is no account. Photos, names, health logs and raw PPG frames
stay on the device; the camera is used only during an active breathing session and frames
are analysed in memory and never written to storage. Telemetry is opt-in and never
includes content. Everything can be exported in a readable archive or deleted in one tap.

---

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md). The short version: claims need citations,
modules need evidence cards, and anything on the measurement path needs a test.

## Licence

[Apache 2.0](LICENSE). Bundled fonts (Golos Text, Literata) are SIL OFL 1.1 — see
[`third_party/fonts`](third_party/fonts). The FSRS algorithm is the work of
[Open Spaced Repetition](https://github.com/open-spaced-repetition); see
[`NOTICE`](NOTICE).
