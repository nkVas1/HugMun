# ADR 0006 — Light theme by default

**Status:** Accepted · 2026-09-21

## Context

A dark, low-contrast interface is the current aesthetic default, and it is what a
contemporary design would reach for. For the user this app was built for, it is the
wrong choice.

## Decision

The default theme is **«Рассвет»**: a warm, high-luminance light palette. A dark theme
exists as a preference and is forced only inside «Ритм», where a dark surround is part
of the stimulus rather than a taste.

## Reasoning

Two age-related changes in the eye, both ordinary rather than pathological:

- **Reduced retinal illuminance.** The pupil is smaller and the lens transmits less
  light, so an older eye needs more light for the same effective contrast.
- **Increased intraocular scatter.** Lens changes scatter light inside the eye, which
  makes bright glyphs on a dark ground bloom and smear. Light-on-dark is often *harder*
  to read with age, not easier — the opposite of the common assumption.

Two further constraints follow and are enforced by `ColorContrastTest`:

- Body text is held to WCAG **AAA (7:1)**, not AA, since measured display contrast
  overstates effective retinal contrast for this reader.
- Large areas are never pure white. `#FFFFFF` at high brightness glares and scatters; the
  page is warm bone instead.

## Consequences

- The app looks unlike most 2026 health software. That is a consequence, not a goal.
- Anyone "modernising" the palette to a dark default, or cleaning the background up to
  pure white, will break a test with a comment explaining why.
- The dark palette still has to pass the same contrast targets, so it is a real
  alternative rather than a decorative mode.
