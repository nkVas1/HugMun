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

A change is reported only when it exceeds a **Reliable Change Index** band
[@jacobson1991rci], computed from the user's *own* within-burst variability rather than
from a population norm we do not have.

```
  S_diff = √2 · SEM ,  where SEM = SD_within-person · √(1 − r_tt)
  RCI    = (x₂ − x₁) / S_diff
```

- `|RCI| < 1.96` → "в пределах вашей обычной изменчивости". This is the normal case and
  the app says it plainly rather than drawing a dramatic slope.
- `|RCI| ≥ 1.96` → a change worth noticing; the app **repeats the burst** before saying
  anything.
- Confirmed decline across two consecutive bursts → a calm prompt to talk to a doctor,
  with no interpretation attached. See
  [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md) §2.3.

`r_tt` is initialised from the ARC-reported reliabilities (conservatively, 0.85) and
re-estimated from the user's own data once ≥ 5 bursts exist.

---

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
