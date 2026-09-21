# Safety statement

**HugMun is a general-wellness application. It is not a medical device.**

It does not diagnose, treat, cure, mitigate or prevent any disease, and it is not a
substitute for a clinician. If you are worried about your memory or thinking, or about
someone else's, talk to a doctor. Nothing this app shows you is a medical opinion.

The full, binding policy — including the exact wording rules the product copy must
follow, the escalation paths, and the engineering controls listed below — is in
[`docs/research/03-safety-and-regulatory.md`](docs/research/03-safety-and-regulatory.md).

---

## What the app will never do

- Produce a dementia-risk score, a "cognitive age", or a percentile against a population.
- Tell you that you have, or are developing, any condition.
- Claim that using it will prevent or slow any disease.
- Compare your results against other people.
- Share anything off the device without you explicitly asking it to.

When a measurement moves beyond your own usual variability, the app says so plainly and
suggests talking to a doctor — with no interpretation attached.

---

## The 40 Hz visual channel: read this before enabling it

The «Ритм» practice is **experimental** (evidence tier C/D) and its visual component
carries a real, if small, risk.

A 40 Hz flicker is far above the WCAG 2.3.1 "three flashes per second" threshold. We do
not pretend otherwise. It is treated as a consented, opt-in stimulation session that is
**deliberately not WCAG 2.3.1 conformant**, kept completely separate from the rest of the
interface — no interface element anywhere else in HugMun flashes more than three times
per second.

**Do not use the visual channel if you or a close relative have ever had:**

- epilepsy, seizures or convulsions of any kind;
- migraine with visual aura;
- a photosensitive or photoparoxysmal response;
- any adverse reaction to flashing lights, strobes or patterned screens.

The app asks about all of these before the channel can be turned on, re-asks every 90
days, and keeps the audio channel available regardless — audio 40 Hz carries none of
these risks.

### Controls that are enforced in code, not by convention

| Control | Behaviour |
| --- | --- |
| Screening gate | Every question must be answered negatively; re-confirmed every 90 days |
| Separate consent | Distinct from onboarding, revocable in one tap |
| Colour | Amber-white only; saturated red is unreachable at the colour-source level |
| Waveform | Sinusoidal; square-wave modulation is not implemented |
| Area | A central disc with a soft falloff, never the full screen |
| Onset | Intensity ramps from zero over at least 20 s; symmetric ramp-down |
| Default intensity | 35 %; higher settings unlock only after a completed lower-intensity session |
| Abort | Any touch anywhere stops output within one frame. No confirmation dialog |
| Daily cap | 60 minutes, enforced in the domain layer |
| Display gate | Requires a refresh rate that is an integer multiple of 40 Hz (80/120/240 Hz). **On 60 Hz and 90 Hz devices the channel is permanently unavailable** |
| Dark-room check | Refuses to start in a very dark room, where relative contrast is highest |
| Adverse events | One report disables the channel until you re-consent |

**Stop immediately** and do not resume if you feel dizzy, nauseous, disoriented, develop
a headache or visual aura, or notice any twitching or altered awareness. Tell a doctor.

---

## Other practices

**Дыхание (breathing).** Slow-paced breathing is low-risk, but stop at once if you feel
dizzy or short of air. Do not do it standing up. The app never instructs breath-holding.
It is not for use while you are acutely unwell.

**Движение (movement).** Every balance exercise names a physical support before it
starts, and the routine is seated-or-supported by default. Falls are themselves a
dementia risk factor; an app that increases fall risk would be a net harm regardless of
any cognitive effect. Stop and speak to a doctor if you experience chest pain,
breathlessness, dizziness or a fall.

**Audio.** Output is level-capped, but if you use headphones, keep the volume low enough
to hold a conversation. If you wear hearing aids or a cochlear implant, ask your
audiologist before using the amplitude-modulated stimulus.

---

## Reporting a safety problem

Every practice screen has a one-tap way to report feeling unwell. Reports are stored on
your device, shown in your own history, and included in any export you give a clinician.

To report a **defect** in these safeguards — a control that did not fire, a claim that
overstates the evidence, a flashing element outside «Ритм» — please open a GitHub issue
using the safety template, or see [`SECURITY.md`](SECURITY.md) if it is a vulnerability.
Safety defects are treated as the highest-priority class of bug in this project.
