# 03 — Safety and Regulatory Position

> This document is binding on implementation. Where it conflicts with a product idea,
> this document wins.

---

## 1. What HugMun is

**A general-wellness application.** It supports mental sharpness, physical activity,
relaxation and sleep habits in a general, non-disease context.

## 2. What HugMun is not

- Not a medical device.
- Not a diagnostic, screening or triage tool.
- Not a treatment for Alzheimer's disease, dementia, mild cognitive impairment or any
  other condition.
- Not a substitute for a clinician.

### 2.1 The regulatory line we are staying behind

The FDA's general-wellness policy, reissued **6 January 2026** superseding the 2019
version, exempts products that are intended **solely** to promote a healthy lifestyle
or general wellness, present minimal risk, and make no disease-specific claims
[@fda2026wellness]. Two categories of acceptable claim are recognised: claims tied to
physical activity, sleep, relaxation or *mental sharpness* without reference to disease;
and claims describing a general association between a healthy lifestyle and reduced risk
of chronic conditions, **without measuring, diagnosing or treating** the condition.

In the EU, software acquires medical-device status — and therefore a CE-marking
obligation under MDR 2017/745 — as soon as a *medical purpose* is claimed. Regulators
have become markedly more assertive about anything that acts on the brain, and
non-invasive brain-stimulation devices have been reclassified upward [@eunibs2026]. The
UK MHRA's digital-mental-health guidance reaches the same conclusion by a different
route [@mhra2025dmht].

**Operational rule.** Every user-facing string that describes a benefit must pass this
test: *would it still be true and permissible if the reader had no disease?* If a
sentence only makes sense as a claim about dementia, it does not ship.

### 2.2 Concrete copy rules

| Forbidden | Permitted |
| --- | --- |
| "снижает риск деменции" | "тренировка скорости обработки информации — то, что изучалось в исследовании ACTIVE" |
| "лечит нарушения памяти" | "помогает удерживать в памяти важные для вас вещи" |
| "терапия 40 Гц" | "экспериментальная сенсорная стимуляция 40 Гц" |
| "ваш риск деменции: 34 %" | *(no risk score is ever produced)* |
| "по результатам теста у вас снижение" | "ваш показатель ниже вашего обычного уровня; это может значить многое, поговорите с врачом" |

Citations shown in-app describe **what a study found in its participants** — never what
the app will do for this user.

### 2.3 The escalation path

The app must never be the last word. Any of the following produce a calm, non-alarming
prompt to speak to a doctor, with no interpretation attached:

- a sustained decline beyond the reliable-change band across two consecutive monthly
  bursts;
- a positive mood screen;
- blood-pressure entries outside a wide safety range;
- a user-reported adverse event in any session.

---

## 3. Photosensitivity — the hard constraint on the visual channel

### 3.1 What the guidelines say

A flash is a potential hazard when **all** of the following hold
[@pse2024guidelines; @wcag231]:

- luminance change of **≥ 20 cd/m²** between states, **and**
- frequency **≥ 3 Hz**, **and**
- the flashing area subtends **≥ 0.006 steradians** of the visual field, **and**
- the darker state is below **160 cd/m²**.

Saturated **red** flashing is treated separately and more strictly: alternation between
large saturated red and blue fields at ~12 Hz is a documented trigger
[@pse2024guidelines]. WCAG 2.3.1 caps general and red flashes at three per second
[@wcag231].

### 3.2 Where that leaves a 40 Hz stimulus

Squarely outside the guidelines. A 40 Hz full-field flicker violates WCAG 2.3.1 by a
factor of more than thirteen. We state this plainly rather than burying it.

Two mitigating facts, neither of which is an excuse:

- The photoparoxysmal response peaks around **15–25 Hz**; 40 Hz is above the most
  provocative band, though not risk-free.
- The GENUS human studies were conducted with clinical screening, including EEG, and
  reported the stimulation as safe and well tolerated in their cohorts, which included
  patients with epilepsy in the Phase 1 safety arm [@chan2022plosone].

We cannot perform EEG screening. Therefore the visual channel is treated as a
**consented, opt-in stimulation session that is deliberately not WCAG 2.3.1 conformant**,
isolated from all interface content.

### 3.3 Mandatory engineering controls

Implemented in `:engine:visuals`; each is covered by a test.

| Control | Requirement |
| --- | --- |
| Screening gate | Negative answers to every item in the photosensitivity screen, stored and re-confirmed every 90 days |
| Separate consent | Distinct from onboarding consent; plain-language risk text; revocable in one tap |
| Colour | Amber-white only. Saturated red is unreachable — enforced at the shader/colour-source level, not by convention |
| Waveform | Sinusoidal; square-wave modulation is not implemented |
| Area | Central disc with radial falloff; never full-screen |
| Onset | Depth ramps 0 → target over ≥ 20 s; symmetric ramp-down |
| Default depth | 35 %; higher depths unlocked only after a completed lower-depth session |
| Abort | Any touch anywhere stops output within one frame. No dialog |
| Daily cap | 60 minutes; enforced in the domain layer, not the UI |
| Refresh gate | `refresh mod 40 ≈ 0` required (80/120/160/240 Hz). 60 Hz devices: channel unavailable |
| Ambient light | Session refuses to start in a very dark room (light sensor), where relative contrast is highest |
| Adverse-event log | One-tap "мне стало нехорошо" during and after the session; a single report disables the channel until re-consent |

### 3.4 Interface content

The rest of the application is conformant: **no interface element anywhere flashes more
than three times per second**, and `Settings.Global.ANIMATOR_DURATION_SCALE` /
reduce-motion preferences are honoured throughout.

---

## 4. Audio safety

- Output level is capped, with the modulated channel targeted at ≈ 65 dB(A) at nominal
  volume.
- Cumulative daily listening time is tracked and surfaced; long sessions at high volume
  produce a warning.
- The app warns against use with the device at high system volume and against
  headphone use at levels where a conversation is inaudible.
- Users with hearing aids or cochlear implants are advised that the amplitude-modulated
  stimulus may interact with their device's processing and to consult their audiologist.

---

## 5. Physical-activity safety

- Every balance exercise names a physical support before it begins.
- The routine is entirely seated-or-supported by default; unsupported variants require
  an explicit opt-in.
- A pre-exercise readiness check runs at onboarding and is re-asked after any reported
  fall, dizziness, chest pain or new shortness of breath.
- Any adverse report ends the session and suppresses the module until acknowledged.

---

## 6. Data protection and dignity

| Principle | Implementation |
| --- | --- |
| Local-first | All personal data — photos, names, health logs, raw PPG — stays on the device. There is no server |
| No account | No sign-up, no email, no phone number |
| No analytics by default | Telemetry is opt-in, aggregate, and never includes content |
| Encryption at rest | Room database encrypted with a key held in the Android Keystore |
| Export and delete | Full export to a readable archive; one-tap irreversible delete of everything |
| Camera | Used only during an active breathing session; frames are analysed in memory and never written to storage |
| Caregiver sharing | Explicit, per-export, user-initiated. No background sharing, ever |

**Dignity rules**, which are safety rules for this population:

- No infantilising language, imagery, praise or sound.
- No streaks, no loss framing, no "you're falling behind".
- A missed session is stated neutrally and the schedule re-plans around it.
- Performance is never shown as a rank against other people.
- The user can always see, in plain Russian, exactly what the app believes about them
  and why.

---

## 7. Adverse events

Every module exposes a one-tap adverse-event report. Reports are stored locally with the
session context, surfaced in the user's own history, and included in exports so a
clinician can see them. Certain events auto-disable their module pending re-consent:

| Event | Effect |
| --- | --- |
| Any visual discomfort, aura, or unwellness during «Ритм» | Visual channel disabled; re-consent required |
| Dizziness or faintness during «Дыхание» | Session ends; module paused for 24 h |
| Fall, chest pain, or breathlessness during «Движение» | Module disabled until readiness re-check |

---

## 8. Accessibility baseline (binding)

Derived from touchscreen guidelines for older adults [@touchscreen2017older;
@nngroup_seniors] and WCAG 2.2.

| Requirement | Value |
| --- | --- |
| Minimum touch target | **56 × 56 dp** (above the 48 dp guideline; this user has shaky hands) |
| Minimum spacing between targets | 12 dp |
| Body text | 20 sp default, scaling to 200 % system font size without clipping |
| Contrast | ≥ 7:1 for body text (WCAG AAA), ≥ 4.5:1 for large text and controls |
| Primary navigation | Persistent bottom bar. **No navigation drawer, no hamburger** |
| Gestures | Every gesture has a visible button equivalent. No swipe-only actions |
| Timeouts | None anywhere in the interface. Time limits exist only inside a task, where they are the measurement |
| Motion | Honours reduce-motion; no parallax, no auto-playing motion |
| Screen reader | Every interactive element labelled; task screens announce state changes |
| Language | Plain Russian, short sentences, no jargon, no English loanwords where a Russian word exists |
| Colour | Never the sole carrier of meaning; blue–yellow discrimination declines with age, so status uses shape and text too |
| Default theme | **Light.** Older eyes need more light, and intraocular scatter makes light-on-dark text harder, not easier. Dark theme exists but is not the default |

---

## 9. Review triggers

This document is re-reviewed when any of the following occur:

- a new FDA general-wellness or EU MDR guidance is published;
- new evidence changes a module's tier;
- an adverse event of a kind not anticipated here is reported;
- a new stimulation modality is proposed.
