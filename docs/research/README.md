# HugMun Research Dossier

This directory is the scientific backbone of HugMun. Every feature in the application
traces back to a claim in these documents, and every claim traces back to a citation.

**Rule of the project: no module ships without an evidence card.**
If we cannot state honestly what the evidence says — including where it is weak —
the feature does not get built.

## Contents

| Document | Purpose |
| --- | --- |
| [`01-evidence-base.md`](01-evidence-base.md) | Graded review of interventions that plausibly preserve cognition in late life |
| [`02-intervention-specs.md`](02-intervention-specs.md) | Translation of each finding into an implementable, parameterised protocol |
| [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md) | Photosensitivity limits, audio exposure, wellness-vs-medical-device boundary |
| [`04-prior-art.md`](04-prior-art.md) | Open-source and commercial landscape; what we reuse, what we reject |
| [`05-measurement.md`](05-measurement.md) | Psychometrics: what we measure, how, and what a change actually means |
| [`references.bib`](references.bib) | BibTeX for every source cited across the dossier |

## How we grade evidence

We use a five-tier scheme adapted from GRADE, deliberately conservative. The tier is
shown to the user inside the app, in plain language, on every exercise.

| Tier | Meaning | In-app wording (RU) |
| --- | --- | --- |
| **A** | Multiple RCTs or a large RCT with long-term, real-world outcomes; effect replicated | «Подтверждено в длительных исследованиях» |
| **B** | Consistent RCT evidence for intermediate outcomes; long-term transfer unproven | «Хорошо изучено» |
| **C** | Small RCTs / early-phase trials; promising but not established in humans at scale | «Изучается, результаты обнадёживают» |
| **D** | Mechanistic or animal evidence; human data preliminary | «Экспериментально» |
| **✗** | Claimed benefit not supported; included only as scaffolding or rejected outright | (not shipped as a benefit claim) |

## Standing caveats

1. **Cochrane is not on our side by default.** Reviews of *generic* computerised
   cognitive training in healthy older adults and in MCI conclude the certainty of
   evidence is **low to very low** [@gates2020healthy; @gates2019mci]. This is the single
   most important fact in this dossier. It means a generic "brain games" app is not a
   defensible product. What survives scrutiny is *specific paradigms with specific
   dosing*, plus multidomain lifestyle work.
2. **Near transfer is easy, far transfer is hard.** People reliably get better at the
   task they practise. Generalisation to untrained abilities and to daily life is the
   exception, not the rule [@sala2019secondorder].
3. **Dose and schedule are part of the intervention.** The clearest example in this
   dossier is ACTIVE: the same training, with and without booster sessions, produced
   HR 0.75 and HR 1.01 respectively at 20 years [@coe2026active]. Scheduling is not a
   UX nicety here. It is the active ingredient.
4. **We are a wellness product, not a treatment.** See
   [`03-safety-and-regulatory.md`](03-safety-and-regulatory.md).

## Updating this dossier

Evidence moves. When a source is added, changed or retired:

1. Add/replace the entry in `references.bib`.
2. Update the relevant section and its tier.
3. If a tier changes, update `EvidenceTier` in `:core:model` and the in-app evidence card.
4. Record the change in an ADR if it alters what we ship.
