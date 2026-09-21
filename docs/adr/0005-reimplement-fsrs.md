# ADR 0005 — Reimplement FSRS rather than depend on a port

**Status:** Accepted · 2026-09-21

## Context

FSRS is an open-source spaced-repetition scheduler with default parameters fitted on
hundreds of millions of reviews, and it outperforms SM-2 at equal retention. Official
ports exist in many languages, including Kotlin. Taking one as a dependency would be the
normal choice.

## Decision

Implement FSRS-6 in `:engine:scheduling` from the published specification.

## Reasoning

1. **We need clamps the reference implementations deliberately omit.** Target retention
   0.95 rather than 0.90, because for a learner a lapse is a useful signal and for an
   83-year-old trying to hold on to a grandchild's name it is distressing. Maximum
   interval 45 days rather than years. One clamp is ours alone and is marked as such in
   the source: post-lapse stability may never exceed pre-lapse stability, so forgetting
   can never buy a longer gap.
2. **The input contract differs.** Our grade is derived from cue level and response
   latency, not self-reported, because self-rating is an extra metacognitive demand and
   is unreliable in this population.
3. **It sits on the measurement path.** Nothing there may be a black box.

Algorithms are not copyrightable and no licence obligation attaches, but attribution to
Open Spaced Repetition appears in `NOTICE`, in the source file, and in the in-app
licences screen regardless, because it is their work.

## Consequences

- One more piece of maths to maintain, covered by unit tests including the definitional
  invariant `R(S, S) = 0.9`.
- The published formulas are reproduced verbatim in `docs/research/04-prior-art.md` §3 so
  the implementation can be checked against them without reading Kotlin.
- Upstream improvements to FSRS must be ported deliberately rather than arriving with a
  version bump. Given that this scheduler decides when someone is reminded of their
  grandchildren's names, deliberate is the right default.
