# 01 — Evidence Base

> Scope: non-pharmacological, phone-deliverable interventions plausibly able to
> preserve or improve memory, reasoning, attention and processing speed in adults
> aged 65+. Target user of the first deployment: a man born in 1943.

---

## 0. The uncomfortable starting point

Two Cochrane reviews set the floor for honesty in this project.

- **Healthy older adults, ≥12 weeks of computerised cognitive training (CCT):** there is
  a *lack of high-quality evidence* that it maintains cognitive function; overall
  certainty **low to very low** [@gates2020healthy].
- **Mild cognitive impairment:** 8 RCTs, 660 participants, 12 weeks to 18 months. The
  available evidence *does not allow a determination* of whether CCT prevents dementia
  or maintains cognition; certainty **very low** [@gates2019mci].

A second-order meta-analysis of cognitive training reaches the same structural
conclusion from another direction: near transfer is common, far transfer is small and
frequently disappears under better controls [@sala2019secondorder].

**Design consequence.** HugMun does not sell "brain training". It delivers a small
number of protocols with unusually specific evidence, wraps them in the multidomain
lifestyle scaffolding that *does* have positive trials behind it, and tells the user
the truth about each one. Everything else in the app is measurement and adherence.

---

## 1. Tier A — Speed-of-processing training (the ACTIVE paradigm)

### What was done

ACTIVE (Advanced Cognitive Training for Independent and Vital Elderly) randomised
~2,800 initially healthy older adults to one of three training arms — memory, reasoning,
or **speed of processing** — or to a no-contact control. Initial training was 10 sessions
over ~5–6 weeks; subsets received booster sessions at 11 and 35 months
[@ball2002active; @edwards2017speed].

The speed arm trained the **Useful Field of View** (UFOV): a dual-task paradigm in which
the participant identifies a briefly presented central target *and* localises a
simultaneous peripheral target, with the presentation duration adaptively driven down to
the participant's threshold. The measure is a *perceptual threshold in milliseconds*, not
a reaction time [@ball1993ufov; @edwards2005ufov].

### What was found

| Outcome | Result |
| --- | --- |
| Dementia at 10 years, speed arm vs control | **HR 0.71** (95% CI 0.50–0.998), p = .049 [@edwards2017speed] |
| Per additional speed session | **HR 0.90** (95% CI 0.85–0.95), p < .001 [@edwards2017speed] |
| Diagnosed ADRD at 20 years, speed **with ≥1 booster** | **HR 0.75** (95% CI 0.59–0.95) [@coe2026active] |
| Diagnosed ADRD at 20 years, speed **without booster** | **HR 1.01** (95% CI 0.81–1.27) — no effect [@coe2026active] |
| Memory arm, reasoning arm | No significant reduction in dementia risk [@edwards2017speed; @coe2026active] |

Absolute numbers at 20 years: 105/264 (40%) in speed-with-boosters vs 239/491 (49%) in
control [@coe2026active].

### Reading it honestly

- The 10-year result is *borderline* (upper CI 0.998). It should not be oversold.
- The 20-year analysis uses **claims-based** diagnoses linked to Medicare, which is a
  different and noisier outcome than adjudicated dementia.
- It is a **secondary/long-term** analysis of a trial not powered for dementia incidence.
- But the dose–response relationship and the booster contrast are internally consistent
  and mechanistically coherent, and no other cognitive-training paradigm has anything
  comparable. A systematic review and meta-analysis of UFOV training specifically found
  reliable improvement on trained and related measures, including real-world driving
  outcomes [@edwards2017ufovmeta; @roenker2003driving].

### What HugMun takes from it

1. **Speed of processing is the core module, not one game among many.**
2. **Boosters are mandatory architecture.** A training app that lets the user grind daily
   and then stop has, per this data, built the arm that did nothing. The scheduler must
   enforce spaced reactivation for years, not weeks.
3. **Threshold, not score.** The primary metric is a presentation threshold in
   milliseconds, obtained by an adaptive staircase. This is also what makes progress
   legible and honest.
4. **Frame accuracy is a scientific requirement.** A threshold expressed in milliseconds
   is meaningless if the actual on-screen duration is unknown. See
   [`02-intervention-specs.md`](02-intervention-specs.md) §1.

**Tier: A** (long-term, real-world outcome, replicated across two analyses of the same
cohort; downgraded from "definitive" by the caveats above).

---

## 2. Tier A — Multidomain lifestyle intervention (FINGER → US POINTER)

### FINGER

A 2-year RCT in Finland (N = 1,260, aged 60–77, at elevated dementia risk) combining
dietary counselling, exercise, cognitive training and vascular/metabolic risk monitoring
improved global cognition versus general health advice [@ngandu2015finger].

The model has since been replicated and adapted through the **World-Wide FINGERS**
network, now spanning 60+ countries, including LatAm-FINGERS and FINGER-NL
[@kivipelto2020wwfingers; @latamfingers2026].

### US POINTER

The largest and most directly relevant replication: a 2-year, 5-site RCT in **2,111**
older adults at elevated risk, comparing a **structured** multidomain lifestyle
intervention against a **self-guided** one. The structured arm showed significantly
greater improvement in global cognition. Benefits were consistent across age, sex,
ethnicity, cardiovascular health and *APOE*-ε4 status [@baker2025pointer].

### Reading it honestly

The comparator in US POINTER was an active self-guided intervention, not a do-nothing
control — which makes the result more impressive, not less, but also means the absolute
effect of "doing something" is larger than the between-arm difference implies. The
between-arm effect size is modest.

### What HugMun takes from it

**Structure, accountability and intensity are the active ingredients** — not any single
domain. This directly justifies the app's opinionated daily ritual, streak-free but
schedule-driven adherence design, and the presence of non-cognitive modules
(movement, sleep, blood pressure, social contact) alongside the cognitive ones.

**Tier: A.**

---

## 3. Tier A — Modifiable risk factors (Lancet Commission 2024)

The 2024 report of the Lancet standing Commission identifies **14 modifiable risk
factors** across the life course, together accounting for a population-attributable
fraction of **~45%** of dementia cases [@livingston2024lancet]:

> less education · hearing loss · high LDL cholesterol · depression · traumatic brain
> injury · physical inactivity · diabetes · smoking · hypertension · obesity · excessive
> alcohol · social isolation · air pollution · untreated vision loss

Two were added in 2024: **high LDL-C in midlife** and **untreated vision loss**.

### What HugMun takes from it

A **personal risk compass**: a non-alarmist, non-diagnostic checklist of the factors the
user can actually act on at 83 — hypertension control, hearing, vision, physical
activity, social contact, sleep, low mood, alcohol. Each factor links to a concrete next
action, not a score that induces anxiety.

Note on age-appropriateness: several factors (education, midlife LDL, midlife obesity)
are **not** actionable at 83 and are deliberately excluded from the user-facing compass.
Showing an 83-year-old an unmodifiable risk factor is harm without benefit.

**Tier: A** (for the association and PAF; the *intervention* evidence varies per factor —
see §4 for the clearest cautionary example).

---

## 4. Tier B — Hearing: the ACHIEVE cautionary tale

ACHIEVE randomised 977 adults aged 70–84 with untreated mild-to-moderate hearing loss to
best-practice hearing intervention vs health education, over 3 years [@lin2023achieve].

- **Primary outcome in the full cohort: null.** No reduction in 3-year cognitive decline.
- **In the ARIC subgroup** (older, more risk factors): decline **48% slower**.
- In the top quartile of predicted risk: **61.6% slower** [@achieve2025secondary].

### What HugMun takes from it

This is the model for how the app should talk about *every* risk factor: the honest
statement is "this matters more the higher your baseline risk, and the trial in
generally-healthy volunteers was null." We surface hearing prominently — an 83-year-old
is very likely in the higher-risk stratum — while refusing to claim a guaranteed effect.

**Tier: B.**

---

## 5. Tier B — Physical exercise, by modality

A set of 2025–2026 network meta-analyses converge on modality-specific effects
[@nma2025exercise; @nma2025comparative; @jia2025mci]:

| Modality | Best for | Effect | Typical protocol in the evidence |
| --- | --- | --- | --- |
| **Resistance** | Global cognition; inhibitory control | SMD 0.55 (global); SMD 0.31, SUCRA 82.1 (inhibition) | 12 wk, 2–3×/wk, 45 min |
| **Mind–body** (tai chi, qigong, yoga) | Executive function; task switching; working memory | SMD −0.58 task switching (SUCRA 85.1) | high frequency, moderate duration |
| **Aerobic** | Memory | SMD 0.42 | ~21 wk, 2×/wk, 60 min |
| **Multicomponent** | Global cognition **in MCI** | SUCRA 76.5 (highest in MCI) | mixed |

### What HugMun takes from it

The app is not a fitness tracker and will not pretend to supervise resistance training.
What it *can* do responsibly: a short, safe, **seated-and-standing multicomponent**
routine (balance, sit-to-stand, band/bodyweight resistance, mobility) with voice pacing
and deconditioning-aware progression, plus prompts that pair movement with the cognitive
session — the combined motor–cognitive literature is favourable [@motorcognitive2025].

**Tier: B** for the cognitive benefit of exercise generally; **C** for what a phone app
can reliably cause someone to do.

---

## 6. Tier B/C — Heart-rate-variability biofeedback (resonance breathing)

Slow breathing at the individual **resonance frequency** — typically ~0.1 Hz, i.e.
**5.5–6.5 breaths per minute** — maximally amplifies respiratory sinus arrhythmia and
baroreflex gain [@lehrer2020resonance; @lehrer2013methods].

In older adults, 5 weeks of HRV biofeedback aimed at increasing heart-rate oscillations
increased the physiological influence on BOLD signal in **prefrontal cortex and thalamus**,
interpreted as improved cerebrovascular health [@nashiro2023hrvb].

**But:** effects of slow-paced breathing on *cognitive performance itself* are
inconsistent, and single-session protocols in particular are unproven
[@hrvb2022cognition; @hrvb2025inhibitory].

### What HugMun takes from it

A resonance-breathing module that:

- **finds the user's own resonance frequency** by sweeping 4.5–7.0 br/min and measuring
  RSA amplitude — this is the actual clinical protocol, and skipping it (as most consumer
  breathing apps do) is a plausible reason consumer implementations underperform;
- uses **camera PPG** for real biofeedback rather than an open-loop animation;
- is framed as *autonomic regulation, calm and cerebrovascular health* — **not** as a
  memory intervention.

**Tier: B** for autonomic/cerebrovascular outcomes; **C** for cognition. The in-app card
says exactly that.

---

## 7. Tier C/D — 40 Hz gamma sensory stimulation (GENUS)

### Preclinical

Optogenetic driving of fast-spiking PV interneurons at **40 Hz — and not at other
frequencies** — reduced Aβ40/Aβ42 in a mouse model. A non-invasive 40 Hz *light flicker*
regime reproduced the amyloid reduction in visual cortex and reduced plaque load in aged
mice [@iaccarino2016nature]. Seven days of **auditory** 40 Hz GENUS improved spatial and
recognition memory and reduced amyloid in auditory cortex and hippocampus; combined
audio-visual stimulation produced broader effects than either alone [@martorell2019cell].

### Human

- **Phase 1 feasibility + Phase 2A pilot** (cognitively normal n = 25; mild AD n = 16;
  epilepsy patients; pilot n = 15, 3 months daily): the stimulation was safe and
  tolerated. The treated group showed less ventricular dilation and hippocampal atrophy,
  increased default-mode-network functional connectivity, better face–name delayed
  recall, and improved rest–activity rhythmicity [@chan2022plosone].
- **Open-label extension (2025/2026):** 40 Hz EEG entrainment declined in early-onset AD
  but increased in late-onset AD; the late-onset group showed improvement in MMSE, CDR
  and FAS, and a reduction in **plasma pTau217** — the first such demonstration for a
  non-invasive intervention [@genus2026extension].

### Reading it honestly

Sample sizes are tiny (n = 15 in the pilot). The extension is **open-label**, so
expectancy effects are uncontrolled. Positive results cluster in a subgroup identified
post hoc. This is early-stage, and larger trials are explicitly what the investigators
call for. It is *not* established therapy.

### Critical implementation detail

The MIT stimulus is **amplitude-modulated / isochronic**: light that physically flickers
and sound that physically pulses 40 times per second. It is **not** a binaural beat,
which is an illusion generated inside the auditory system from two tones and which
entrains less reliably [@baa2021review]. Consumer "40 Hz gamma" tracks are
overwhelmingly binaural beats and are therefore **not** the intervention that was
studied. HugMun synthesises a true amplitude-modulated carrier.

### What HugMun takes from it

A **clearly labelled experimental module**, off by default, behind a safety screen,
implementing a faithful 40 Hz amplitude-modulated audio channel (always available) and a
frame-locked 40 Hz visual channel (only on displays ≥ 80 Hz; honestly disabled
otherwise). No therapeutic claims. See
[`03-safety-and-regulatory.md`](03-safety-and-regulatory.md) for the photosensitivity
limits that constrain the visual channel.

**Tier: C** (human, early phase) / **D** (mechanism, animal).

---

## 8. Tier B — Spaced retrieval and errorless learning (practical memory)

Distinct from "cognitive training": this is **memory rehabilitation** — teaching specific,
personally important information so it is actually retained.

- **Spaced retrieval (SR)** — recalling target information over expanding intervals — is
  an evidence-based intervention in mild-to-moderate dementia and MCI
  [@camp1996spaced; @sr2023meta; @sr2024review].
- Combining SR with **errorless learning** (preventing the incorrect response from ever
  being produced) improves outcomes further [@srel2015jpts; @el2013geriatrics].
- Proposed mechanisms: the spacing effect, ecologically valid priming, conditioning, and
  the avoidance of error-strengthening [@sr2024review].

Caveat: many studies are single-case or very small, and long follow-up is rare
[@sr2024review].

### What HugMun takes from it

The **«Якорь» (Anchor)** module: the user (or a family member) stores things that
genuinely matter — names and faces of grandchildren, where the keys live, the medication
schedule, the way home — and the app schedules expanding-interval retrieval with
errorless scaffolding (cue first, never let a wrong answer be produced, fade cues as
retention grows).

Scheduling uses **FSRS** (Free Spaced Repetition Scheduler), an open-source DSR
(difficulty–stability–retrievability) memory model fitted on hundreds of millions of
reviews, which outperforms SM-2 at equal retention [@fsrs]. FSRS is parameterised for
healthy learners, so HugMun clamps it to short, conservative intervals appropriate to
an older adult and to errorless practice.

This is also the module with the highest *immediate quality-of-life* value, independent
of whether any far transfer ever occurs.

**Tier: B.**

---

## 9. Tier B — Novelty and sustained productive engagement

The **Synapse Project** randomised 221 adults aged 60–90 to 15 hours/week for 3 months of
either high-challenge *new skill learning* (digital photography, quilting) or
low-challenge activity (socialising, familiar cognitive tasks). Only the high-challenge
groups improved in **episodic memory**, with associated changes in neural function and
some benefit persisting at one year [@park2014synapse].

### What HugMun takes from it

Difficulty must remain genuinely challenging — an adaptive engine that keeps the user at
threshold rather than in a comfortable, high-scoring plateau. It also justifies a
**novelty rotation**: task variants change rather than repeating identically, and the app
nudges toward real-world learning outside the phone.

**Tier: B.**

---

## 10. Tier B — Light, circadian rhythm and sleep

Bright light in the morning improves sleep efficiency, total sleep time and circadian
rhythm amplitude in older adults with dementia [@light2021ambient], with some reports of
paired sleep–wake and cognitive improvement in AD after ~5 weeks [@light2021circadian].
Effects on cognition per se are weaker and slower than effects on sleep
[@light2025pilot].

Notably, the GENUS pilot also reported improved **rest–activity rhythmicity**
[@chan2022plosone], consistent with a circadian route.

### What HugMun takes from it

A phone screen cannot deliver therapeutic-intensity bright light (thousands of lux at the
cornea) and HugMun will not pretend otherwise. Instead: a **daylight prompt** — a
scheduled nudge to get outdoors within the first hours after waking — plus sleep–wake
logging that feeds the insights view. Honest framing: the intervention is daylight, the
app is only the reminder.

**Tier: B** (for sleep/circadian outcomes); the app's *delivery mechanism* is Tier C.

---

## 11. Measurement — unsupervised smartphone cognitive assessment is viable

This is what makes a self-tracking app defensible.

- **ARC** (Ambulatory Research in Cognition): brief tests of associative memory,
  processing speed and working memory, 4×/day for 7 days, self-administered on personal
  devices. In 268 cognitively normal older adults (65–97) and 22 with very mild dementia:
  between-person reliability and 6-month/1-year test–retest all **> 0.85**; composite
  correlated **r = 0.53** with conventional cognitive measures; correlated with AD
  biomarker burden comparably to in-clinic tests; 86.5% enrolment, 80.4% adherence,
  4.8% dropout [@nicosia2023arc].
- **Mobile Toolbox**: self-administered smartphone tests derived from the NIH Toolbox
  Cognition Battery, validated against in-clinic testing and AD biomarkers
  [@jutten2025mtb].

### What HugMun takes from it

Frequent, short, unsupervised measurement on the user's own phone is **reliable enough to
track an individual over time**, provided we (a) use burst sampling rather than single
sessions, (b) model practice effects, and (c) report change with a defensible
reliable-change criterion rather than raw score deltas. See
[`05-measurement.md`](05-measurement.md).

**Tier: A** for feasibility/reliability of the method; the specific battery we ship is
our own and is **not** a validated instrument — stated plainly in the app.

---

## 12. What we deliberately do **not** build

| Rejected | Why |
| --- | --- |
| Dual n-back as a headline "IQ training" feature | Far-transfer claims are among the most contested in the field; meta-analytically small and confounded [@sala2019secondorder]. Retained only as one working-memory *measure*, never advertised as fluid-intelligence training. |
| Binaural-beat "gamma" audio | Not the stimulus studied in GENUS; less reliable entrainment [@baa2021review]. |
| MMSE / MoCA screening | Copyright- and licence-restricted; also, unsupervised self-administered screening invites both false reassurance and false alarm. HugMun tracks performance; it does not screen for dementia. |
| Any diagnostic, triage or "your dementia risk is X%" output | Regulatory and ethical. See [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md). |
| Streaks, confetti, leaderboards, lives, coins | Infantilising for the target user, and they optimise for engagement rather than for the dose the evidence supports. Adherence is handled by schedule and meaning, not by slot-machine mechanics. |
| tDCS / tACS integration | Non-invasive brain-stimulation devices are being reclassified and tightened under EU MDR; out of scope for a wellness app [@eunibs2026]. |

---

## 13. Summary: what actually goes in the app

| Module | Working name | Tier | Primary justification |
| --- | --- | --- | --- |
| Speed of processing (UFOV) | **Зоркость** | A | @edwards2017speed, @coe2026active |
| Spaced booster scheduling | *(engine, not a screen)* | A | @coe2026active |
| Practical memory, spaced retrieval | **Якорь** | B | @sr2023meta, @fsrs |
| Resonance breathing + HRV biofeedback | **Дыхание** | B/C | @lehrer2020resonance, @nashiro2023hrvb |
| Multicomponent movement | **Движение** | B | @nma2025exercise |
| Risk compass (Lancet-14, age-filtered) | **Компас** | A | @livingston2024lancet |
| 40 Hz AM audio (+ gated visual) | **Ритм** | C/D | @chan2022plosone, @martorell2019cell |
| Daylight & sleep rhythm | **Рассвет** | B | @light2021ambient |
| Assessment battery | **Замер** | A (method) | @nicosia2023arc, @jutten2025mtb |

Overall product framing follows US POINTER: **structure, accountability, intensity**
[@baker2025pointer].
