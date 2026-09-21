# ADR 0001 — Evidence-first product scope

**Status:** Accepted · 2026-09-21

## Context

The obvious product is a catalogue of cognitive mini-games with a progress screen. That
is what the market sells, it is easy to build, and it demos well.

It is also not defensible. Two Cochrane reviews rate the certainty of evidence for
generic computerised cognitive training in older adults as low to very low, and a
second-order meta-analysis finds far transfer small and fragile. Building a large
catalogue would mean either making claims we cannot support, or making no claims at all
and letting the user infer them — which is worse, because it is deniable.

## Decision

The product contains a small, closed set of practices. Each one must survive
`docs/research/01-evidence-base.md`, carry an `EvidenceCard`, and display its tier to the
user in plain language including the caveat.

`EvidenceCard` requires a non-blank `caveat` at construction, so the honesty requirement
is enforced by the type system rather than by review discipline.

Corollary decisions that follow from this and are not separately negotiable:

- The ACTIVE booster schedule is part of the intervention, not packaging (see the
  20-year hazard ratios in the README).
- No streaks, confetti, leaderboards, coins or lives. They optimise for engagement rather
  than for the dose the evidence supports, and they are infantilising for this user.
- No composite risk score, no "cognitive age", no percentile against a population we have
  not sampled.

## Consequences

- The app looks sparse next to competitors. That is the intended result.
- Adding a practice is expensive: it requires research, a tier, a card and citations.
  This is a feature.
- Marketing copy is constrained by `docs/research/03-safety-and-regulatory.md` §2.2.

## What would change our mind

New high-quality evidence for a paradigm we currently exclude. The dossier is versioned
so that a tier change is a visible, reviewable event.
