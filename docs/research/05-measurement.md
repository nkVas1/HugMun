# 05 — Measurement

> What we measure, how, and what a change actually means.
>
> The core problem: an individual's cognitive performance varies enormously day to day.
> Naïve apps show a wiggly line and let the user infer a trend that is not there. That
> is not a neutral design failure — for an 83-year-old watching for signs of decline, a
> false negative is complacency and a false positive is fear.

---

## 1. Why burst sampling

@nicosia2023arc established that brief, unsupervised, self-administered smartphone tests
in adults aged 65–97 reach between-person reliability and 6-month/1-year test–retest
above **0.85**, correlate **r = 0.53** with conventional neuropsychological measures, and
track AD biomarker burden about as well as in-clinic testing — *when administered as
repeated bursts* (4×/day for 7 days in their protocol) rather than as single sessions.

The reason is statistical, not technological. A burst of short sessions yields:

1. a **more precise central estimate** (averaging away state noise: fatigue, caffeine,
   time of day, distraction);
2. a direct estimate of **within-person variability**, which is the denominator we need
   to say whether a later change is real.

HugMun uses a lighter burst than ARC, because the goal is longitudinal self-tracking
rather than research-grade sampling:

| | Protocol |
| --- | --- |
| Baseline | 3 sessions across 2 days |
| Routine | 3 sessions across 2 days, monthly |
| After a flagged change | repeat burst within 7 days before any message is shown |

---

## 2. The battery

All paradigms are public domain or original to this project. No MMSE, MoCA or SDMT —
see [`01-evidence-base.md`](01-evidence-base.md) §12.

| Task | Domain | Primary measure | Notes |
| --- | --- | --- | --- |
| **Отклик** — choice reaction time | Processing speed | median RT of correct trials; accuracy as a gate | 2-choice; 40 trials; excludes RT < 150 ms as anticipations |
| **Соответствие** — symbol–shape matching | Processing speed / attention | items correct in 90 s | original symbol set; key re-randomised per session |
| **Лица и имена** — face–name paired associates | Episodic memory | recognition d′ after a filled delay | mirrors the outcome that moved in the GENUS pilot [@chan2022plosone] |
| **Путь** — Corsi block span | Visuospatial working memory | two-error span threshold | adaptive span, not fixed list length |
| **Череда** — alternating-sequence trail task | Executive / switching | completion time, error-corrected | Trail-Making-*inspired*; original stimuli and sequence |

Every task records: trial-level raw data, device refresh rate, dropped-frame count,
touch latency, time of day, and whether the session was interrupted. Sessions with
quality flags are retained but excluded from trend fitting, and the exclusion is visible
to the user.

---

## 3. Practice effects

Practice effects are large in repeated cognitive testing and are the single most common
source of a spurious "improvement" in self-tracking apps.

Controls:

1. **Alternate forms.** Each task has ≥ 12 stimulus sets in rotation; a set is not
   reused until the pool is exhausted.
2. **Explicit modelling.** The trend model includes a practice term that saturates:

   ```
   y(t) = β₀ + β₁·t + β₂·(1 − e^(−k·n))  + ε
            ^trend      ^practice, n = session index
   ```

   The reported trend is **β₁**, after the practice component is accounted for.
3. **Warm-up trials** at the start of each task are excluded from scoring.
4. The first burst is labelled as a *familiarisation* burst; the baseline used for
   comparison is the second burst.

---

## 4. Reliable change, not raw deltas

A change is reported only when it falls outside a band computed from the user's **own**
recent variability. Raw deltas are never shown as if they meant something.

### Why not the textbook Reliable Change Index

The classic RCI [@jacobson1991rci] uses
`S_diff = √2 · SD_norm · √(1 − r_tt)`, where `SD_norm` is the **between-person** standard
deviation of a normative sample. It is designed for the case where you have two scores
and a published norm.

Our situation is the reverse. We have no normative sample for our own battery — stated
plainly in §2 — but burst sampling gives us many measurements of *this* person.
Substituting a within-person SD into the RCI formula is a category error: within-person
SD already contains measurement error *plus* genuine day-to-day state variation, so
multiplying it by `√(1 − r_tt)` shrinks the error term and makes the test far too eager
to declare a change.

This is not a hypothetical. The first implementation in `:core:domain` did exactly that,
and the false-alarm simulation in `ReliableChangeTest` caught it.

### What we compute instead

The question our data can actually answer is: *is today's value unusual for this person,
against their own recent spread?* That is a prediction interval for a new observation
relative to the mean of `n` previous ones:

```
  SE_pred = SD_within · √(1 + 1/n)
  index   = (x_new − mean) / SE_pred      ~ t(n − 1) under the null
```

- The critical value is **Student's t**, not 1.96. At n = 6 the two-tailed 95 % point is
  2.571; using the normal quantile would flag roughly twice as many stable people.
- The sample SD uses Bessel's correction. At n = 6 the population formula understates
  spread by about 9 %, which would narrow the band.
- Fewer than **5** prior observations ⇒ no judgement at all. "Not enough data yet" is
  the honest output.

### What the user is told

- Inside the band → «в пределах вашей обычной изменчивости». This is the normal case,
  and the app says it plainly rather than drawing a dramatic slope.
- Outside the band → a change worth noticing; the app **repeats the burst** before saying
  anything further.
- Confirmed across two consecutive bursts → a calm prompt to talk to a doctor, with no
  interpretation attached. See
  [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md) §2.3.

### Verified behaviour

Two simulations run as unit tests, because the error rates *are* the specification:

| Property | Requirement | Simulation |
| --- | --- | --- |
| False alarms in a stable person | < 10 % | 4,000 runs, n = 6, SD = 8 |
| Detection of a real 3-SD decline | > 60 % | 2,000 runs, n = 8 |

## 5. Training metrics vs assessment metrics

These are kept strictly separate, because conflating them is how brain-training apps
end up claiming improvement that is pure task learning.

| | Training («Зоркость» threshold) | Assessment (battery) |
| --- | --- | --- |
| Purpose | drive the adaptive dose | detect real change |
| Expected to improve | **yes**, that is the point | not necessarily |
| Shown as progress | yes, labelled "результат тренировки" | shown as trend with RCI band |
| Used for medical inference | never | never |

The app states, in the results screen: improvement in the trained task is expected and
is *not* by itself evidence of improved memory or thinking. This sentence is
non-negotiable product copy.

---

## 6. What is never produced

- A "cognitive age".
- A percentile against a population HugMun has not sampled.
- A dementia-risk probability.
- A pass/fail, a grade, or a colour-coded "your brain health" badge.
- Any comparison against other users.

---

## 7. Data model implications

Everything above requires trial-level persistence, not summary rows:

- `AssessmentTrial` — one row per trial with stimulus parameters, response, latency in
  nanoseconds, and frame-timing metadata.
- `AssessmentSession` → `AssessmentBurst` → `DomainEstimate` (with SE).
- `TrainingTrial` / `StaircaseState` for «Зоркость», enabling offline re-analysis and
  re-derivation of thresholds if the estimator ever changes.

This is also what makes the export genuinely useful to a clinician or a researcher:
the archive contains the raw trials, not just the app's opinion about them.
