# 02 — Intervention Specifications

Each section turns a finding from [`01-evidence-base.md`](01-evidence-base.md) into a
protocol precise enough to implement and to argue about. Parameters that the evidence
fixes are marked **[fixed]**; parameters we chose are marked **[chosen]** with a reason.

---

## 1. «Зоркость» — Speed of processing (UFOV paradigm)

### 1.1 Trial structure

```
  ┌ fixation ┐ ┌── stimulus ──┐ ┌── mask ──┐ ┌──── response ────┐
  │  500 ms  │ │  T ms (adap) │ │  300 ms  │ │  untimed, 2 taps │
  └──────────┘ └──────────────┘ └──────────┘ └──────────────────┘
```

1. **Fixation** — central cross, 500 ms **[chosen]**, jittered ±100 ms to prevent
   temporal prediction.
2. **Stimulus** — presented for `T` milliseconds, where `T` is under adaptive control:
   - **central target**: one of two silhouettes (raven / owl) inside a fixation box;
   - **peripheral target**: a single ring-shaped marker at one of 8 radial directions,
     at one of 3 eccentricities;
   - **distractors** (level 3 only): 23 additional ring markers filling the field.
3. **Mask** — high-spatial-frequency noise across the whole stimulus field, 300 ms
   **[chosen]**. The mask is what makes `T` the *perceptual* variable rather than an
   afterimage-limited one. Without it the threshold is uninterpretable.
4. **Response** — untimed, two taps: which silhouette (2AFC), then which direction
   (8AFC). Untimed is **[fixed]** by the paradigm: UFOV measures a presentation
   threshold, not a reaction time [@ball1993ufov].

### 1.2 Levels

| Level | Central task | Peripheral task | Trains |
| --- | --- | --- | --- |
| 1 | identify silhouette | — | processing speed |
| 2 | identify silhouette | localise 1 target | divided attention |
| 3 | identify silhouette | localise 1 target among 23 distractors | selective attention |

Level 2 is the workhorse and the primary progress metric, mirroring the ACTIVE
speed-training arm [@ball2002active].

### 1.3 Adaptive procedure **[chosen]**

Weighted up–down staircase (Kaernbach) converging on **75 % correct**, which is the
UFOV criterion **[fixed]**.

- Step in **log duration**, base step `Δ = 0.10 log₁₀ units`.
- After a correct response: decrease by `Δ_down = 0.2845 · Δ`.
- After an error: increase by `Δ_up = Δ`.
- Ratio `Δ_down/Δ_up = 0.2845` places the convergence point at p = 0.75.
- Terminate after **8 reversals** or 60 trials, whichever comes first.
- Threshold = geometric mean of the last **6** reversal points.
- Both subtasks must be correct for the trial to count as correct.

Rationale for a weighted up–down over 3-down-1-up: 3-down-1-up converges on 79.4 %, not
75 %, and needs more trials — a real cost when the session budget for an 83-year-old is
about 12 minutes.

### 1.4 Timing accuracy — a scientific requirement, not a polish item

A threshold reported in milliseconds is only meaningful if the stimulus was actually on
screen for that long.

- Query the active display mode's refresh rate at session start; re-query on
  configuration change.
- Request a display mode whose refresh rate is the highest available, via
  `Surface.setFrameRate` / `preferredDisplayModeId`.
- **Quantise `T` to an integer number of frames.** At 120 Hz the quantum is 8.33 ms; at
  60 Hz it is 16.67 ms. The staircase operates on frame counts internally and converts
  to milliseconds only for display.
- Drive presentation from a `Choreographer` frame callback and **record the actual
  presentation interval** from frame timestamps.
- If a frame is dropped during a stimulus interval, the trial is **discarded, not
  scored**, and the staircase does not advance. Dropped-trial rate is stored as a
  quality metric per session.
- A floor of 1 frame means the measurable threshold floor is refresh-dependent. This is
  disclosed in the session record, and thresholds are never compared across different
  refresh rates without noting it.

### 1.5 Dose and the booster schedule — **the most important spec in this document**

ACTIVE at 20 years: speed training **with** boosters HR 0.75; **without** boosters
HR 1.01 [@coe2026active]. The schedule is the intervention.

| Phase | ACTIVE **[fixed]** | HugMun implementation **[chosen]** |
| --- | --- | --- |
| Core block | 10 sessions over 5–6 weeks, 60–75 min each | 10 sessions over 5 weeks, 2×/week, **12–15 min of trials** each |
| Booster 1 | 4 sessions at ~11 months | 4 sessions across weeks 46–50 |
| Booster 2 | 4 sessions at ~35 months | 4 sessions across weeks 148–152 |
| Maintenance | *(not in ACTIVE)* | 1 session/week for 8 weeks, then 1/month indefinitely |

Deviations and why:

- **Session length.** ACTIVE's 60–75 minutes included group instruction and
  non-training content. 12–15 minutes of pure adaptive trials is a defensible
  equivalent for the active ingredient, and it is what an 83-year-old will actually
  complete. This is an explicit, logged deviation.
- **Maintenance track.** Not tested in ACTIVE. Added because a 34-month gap between
  booster 1 and booster 2 is behaviourally implausible for a consumer app, and because
  the per-session dose–response (HR 0.90 per session) points the same way
  [@edwards2017speed]. It is labelled in-app as an extrapolation.
- The scheduler is implemented in `:engine:scheduling` as a declarative protocol so the
  schedule can be revised when better evidence arrives, without touching the task.

### 1.6 Novelty rotation

Per @park2014synapse, the task must stay genuinely challenging. Silhouette pairs,
mask textures and peripheral-target geometry rotate across a fixed catalogue so that
no two consecutive sessions are visually identical, while the psychophysics is held
constant.

---

## 2. «Дыхание» — Resonance-frequency breathing with HRV biofeedback

### 2.1 Resonance frequency assessment **[fixed by protocol]**

Run once at onboarding, re-run every 6 months. Five epochs, 2 minutes each, in
descending order, with 1 minute of free breathing between [@lehrer2020resonance]:

```
  6.5 → 6.0 → 5.5 → 5.0 → 4.5  breaths per minute
```

For each epoch compute:

- **RSA amplitude** — mean peak-to-trough heart-rate excursion per breath cycle;
- **LF power** — spectral power in 0.04–0.15 Hz;
- **phase coherence** — cross-correlation between the breathing pacer and the
  heart-rate signal at zero lag.

The resonance frequency is the epoch maximising a normalised composite of the three.
Ties resolve toward the *higher* rate, which is easier to sustain.

If no usable PPG signal is obtained, fall back to **5.5 br/min** **[chosen]** — the
modal resonance frequency in adults — and say so in the UI rather than silently
pretending the assessment happened.

### 2.2 PPG acquisition

- Rear camera + torch, fingertip over the lens; CameraX `ImageAnalysis` at 30 fps,
  lowest usable resolution, fixed exposure/white balance/focus (auto-exposure would
  cancel the very signal we measure).
- Signal = spatial mean of the **red** channel over a central ROI (red carries the
  strongest pulsatile component in transmission through the fingertip under a torch).
- Detrend, band-pass 0.7–3.5 Hz (42–210 bpm), then adaptive-threshold peak detection
  with a refractory period of 300 ms.
- Inter-beat intervals → artefact correction (reject IBIs deviating > 20 % from the
  local median; interpolate at most 2 consecutive).
- Signal-quality index per 10 s window; below threshold the UI says "не вижу пульс"
  and the pacer continues without biofeedback rather than showing fabricated numbers.

### 2.3 Session

- 10 minutes at the resonance frequency **[chosen]**; 20 minutes offered as an option.
- Inhale : exhale = **40 : 60** **[chosen]** — a longer exhale increases vagal tone and
  is more comfortable at low rates.
- Feedback: the breathing guide is the primary display; heart-rate oscillation amplitude
  is shown as a second, slower curve beneath it, so the user can *see* the two lock
  together. That visible coupling is the actual biofeedback.
- **Framing [fixed by evidence]:** autonomic regulation, calm, cerebrovascular health.
  Never "improves memory" — effects on cognition are inconsistent
  [@hrvb2022cognition; @hrvb2025inhibitory].

### 2.4 Safety

Slow-paced breathing is low-risk, but: stop immediately on dizziness or air hunger; do
not run while standing; never instruct breath-holding; explicitly not for someone acutely
unwell. An in-session "мне нехорошо" control ends the session and logs the event.

---

## 3. «Ритм» — 40 Hz amplitude-modulated sensory stimulation

**Status: experimental (Tier C/D). Off by default. Gated behind screening.**

### 3.1 Audio channel — always available

- **Modulation: 40 Hz sinusoidal amplitude modulation, depth 100 %** **[fixed]** —
  this is the GENUS stimulus. Not binaural beats [@martorell2019cell; @baa2021review].
- **Carrier [chosen]:** band-limited noise centred on 1–4 kHz rather than a pure tone.
  A 100 %-modulated pure tone is fatiguing over 30–60 minutes; band-limited noise
  drives a comparable auditory steady-state response while being tolerable for an
  older listener with presbycusis-shaped hearing.
- **Optional ambient bed [chosen]:** an *unmodulated* low-level layer for comfort. It
  is mixed at −18 dB relative to the modulated channel and does not attenuate the
  modulated component. The UI states that the bed is decorative and the modulated
  channel is the stimulus.
- **Synthesis [chosen]:** generated sample-by-sample in Kotlin at the device's native
  sample rate and written to a streaming `AudioTrack` through a lock-free ring buffer.

  Sample-accurate by construction: at 48 kHz, one 40 Hz cycle is exactly 1200 samples.
  Output latency is irrelevant to the *modulation frequency* — it only shifts phase —
  so we do not need NDK/Oboe for the audio channel itself. Latency matters only for
  audio–visual phase alignment (§3.3).
- **Level [chosen]:** default output targeting ≈ 65 dB(A) at nominal volume, capped, with
  a duration-aware warning. Long daily sessions make cumulative exposure a real concern
  even at moderate levels.

### 3.2 Visual channel — conditionally available

40 Hz whole-field flicker is, by any reading of WCAG 2.3.1, far above the three-flashes
threshold [@wcag231]. We do not pretend otherwise. It is an **opt-in stimulation
session**, not interface content, and it is engineered down to the lowest risk we can
achieve while remaining faithful to the paradigm. See
[`03-safety-and-regulatory.md`](03-safety-and-regulatory.md).

Enablement requires **all** of:

1. Safety screening passed (no epilepsy/seizures, no migraine with visual aura, no
   photosensitivity, no first-degree family history of photosensitive epilepsy).
2. Explicit, separate consent with a plain-language risk statement.
3. A display mode whose refresh rate satisfies `refresh mod 40 ≈ 0` — i.e. **80, 120,
   160 or 240 Hz**. On a 144 Hz panel we request a 120 Hz mode; if none exists, the
   visual channel stays off.
4. **60 Hz devices: permanently unavailable.** 60/40 = 1.5 frames per cycle cannot be
   rendered without beat artefacts, and a wrong-frequency flicker is both useless and
   less safe than none.

Stimulus design **[chosen, risk-reducing]**:

- **Sinusoidal** luminance modulation, not square-wave — fewer harmonics, less harsh.
- **Amber-white**; saturated red is categorically excluded, being the most provocative
  colour for photosensitive responses [@pse2024guidelines].
- **Bounded area**: a central disc, not the full screen, with a soft radial falloff. This
  keeps the stimulated solid angle far closer to the guideline threshold than a
  full-screen flash, at some cost in entrainment strength — a deliberate trade.
- **Modulation depth ramps from 0 to the target over 20 s** and ramps down at the end.
  No abrupt onset.
- **Default depth 35 %**, user-adjustable up to 100 % only after a completed session at
  a lower depth.
- **Any tap stops the session instantly** — no confirmation dialog, no animation.
- Hard session cap of 60 min/day; the app refuses more.

### 3.3 Audio–visual alignment

When both channels run, they must be phase-aligned to be the combined stimulus studied
in @martorell2019cell.

- Audio is the clock. `AudioTrack.getTimestamp()` gives a framePosition/nanoTime pair.
- Each `Choreographer` frame callback carries the intended presentation time.
- Visual phase = `2π · 40 Hz · (presentationTimeNs − audioEpochNs) / 1e9`.
- Measured phase error is logged per session; > 25 % of a cycle sustained ⇒ the visual
  channel disables itself and tells the user why.

### 3.4 Claims

In-app copy, verbatim policy: describe what was studied, in whom, at what size, and
state that it is not established therapy. No claim of treating, preventing or slowing
any disease. See [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md) §4.

---

## 4. «Якорь» — Practical memory via spaced retrieval + errorless learning

### 4.1 Content

User- or family-authored items that matter in daily life:

| Kind | Example | Prompt form |
| --- | --- | --- |
| Face–name | photo of a grandson → "Митя" | photo, choose among 4 names |
| Location | "ключи — в синей вазе" | question → choose among 4 |
| Routine | "таблетка от давления — после завтрака" | question → choose among 4 |
| Personal fact | phone number of a daughter | progressive cue completion |

Photos never leave the device.

### 4.2 Errorless scaffolding **[fixed by evidence]**

The defining property of errorless learning is that the incorrect response is *never
produced* [@srel2015jpts; @el2013geriatrics]. Implementation:

1. **First exposure** is always a study trial, never a test.
2. Retrieval trials present the answer options with the correct one **visibly
   highlighted** at cue level 0, the highlight fading across levels.
3. If the user does not respond within 6 s, the answer is revealed *before* an error can
   occur, and the trial is scored as "cued", not "wrong".
4. Distractors are drawn from semantically distant items to minimise interference.

Cue levels: `0 = answer shown` → `1 = first letter` → `2 = letter count` →
`3 = no cue`. A level is advanced only after two consecutive unaided successes.

### 4.3 Scheduling

Two nested schedules:

- **Within session — expanding retrieval [fixed]:** 20 s, 45 s, 90 s, 3 min, 6 min.
  Any failure resets to the previous interval. This is the classic Camp protocol
  [@camp1996spaced].
- **Across days — FSRS** [@fsrs]. We implement the DSR model (difficulty, stability,
  retrievability) directly from the published equations.

  Adaptations for this population **[chosen]**:
  - target retention **0.95** (FSRS default 0.90) — for a memory-support tool, failure
    is not a neutral learning signal, it is distressing;
  - **maximum interval 45 days** — the published long intervals assume a healthy adult
    learner and an acceptable lapse rate;
  - **minimum interval 1 day**;
  - grade mapping is derived from cue level and latency rather than self-rating, because
    self-rating is an extra cognitive demand and is unreliable here.

### 4.4 Why this module exists

It is the only module with unconditional day-one value: even if no transfer to general
cognition ever occurs, remembering a grandchild's name is worth having. It also provides
the app's emotional anchor — which is, not coincidentally, exactly what Odin feared
losing in Munin.

---

## 5. «Движение» — Multicomponent physical activity

**[chosen, constrained by responsibility]** The app does not supervise exercise. It
paces a short routine and records adherence.

- **Structure:** 4 blocks × ~3 min — mobility, balance, sit-to-stand strength, gentle
  aerobic — matching the multicomponent profile with the best SUCRA in older adults
  [@jia2025mci; @nma2025exercise].
- **Progression:** repetition-based, advancing only after two completed sessions
  without a reported difficulty flag.
- **Safety:** every balance exercise specifies a support (chair back, counter) and the
  app says so aloud. Falls are a dementia risk factor in their own right; an app that
  increases fall risk is a net negative regardless of its cognitive effect.
- **Pairing:** offered immediately before «Зоркость» when both are due, per the
  motor–cognitive literature [@motorcognitive2025].

---

## 6. «Компас» — Age-filtered modifiable risk factors

From the Lancet-14 [@livingston2024lancet], the subset that is actionable at 83:

| Factor | What the app does | Evidence note shown |
| --- | --- | --- |
| Hypertension | BP log with trend, reminder to discuss with a doctor | strong association; treatment decisions are the doctor's |
| Hearing loss | screening prompt; encourages audiology | ACHIEVE null overall, strong in high-risk [@lin2023achieve] |
| Vision loss | prompt for annual check; cataract surgery question | added in 2024 |
| Physical inactivity | «Движение» adherence | @nma2025exercise |
| Social isolation | contact log, nudge toward a call/visit | association |
| Depression / low mood | 2-item mood check with a clear escalation path | **not a diagnosis**; low score routes to "поговорите с врачом" |
| Sleep | sleep–wake log, daylight prompt | @light2021ambient |
| Smoking, alcohol | brief log | association |
| Diabetes | reminder about routine monitoring | association |

Excluded as not actionable at this age: education, midlife LDL, midlife obesity,
traumatic brain injury history, air pollution (not individually actionable here).

**No composite risk score is ever shown.** A single number is both statistically
indefensible for an individual and needlessly frightening.

---

## 7. «Замер» — Assessment battery

See [`05-measurement.md`](05-measurement.md) for psychometrics. Protocol summary:

- **Burst sampling [fixed by method]** [@nicosia2023arc]: 3 short sessions over 2 days,
  not one long session. Within-burst variability is what lets us estimate an individual
  standard error and therefore say whether a change is real.
- Cadence: baseline burst at onboarding, then monthly.
- Tasks: choice reaction time; symbol–shape matching (processing speed); face–name
  paired associates (episodic memory); Corsi block span (visuospatial working memory);
  alternating-sequence trail task (executive/switching).
- All paradigms are public-domain or original. No MMSE, no MoCA, no SDMT.
- Output: per-domain trend with a reliable-change band. Never a "cognitive age", never
  a percentile against a population we have not sampled.
