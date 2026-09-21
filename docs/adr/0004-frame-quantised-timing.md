# ADR 0004 — Frame-quantised stimulus timing

**Status:** Accepted · 2026-09-21

## Context

The UFOV threshold is a presentation duration in milliseconds. It is the primary output
of the highest-evidence practice in the app, and it is the number the user watches over
months.

A display cannot present a stimulus for an arbitrary duration. It can only present it for
an integer number of frames. Asking for 45 ms on a 60 Hz panel gets you 50 ms, and asking
again on a 120 Hz panel gets you 41.7 ms. Every browser-based implementation we surveyed
ignores this.

## Decision

1. Durations are held internally as **frame counts**, not milliseconds. Conversion
   happens only for display, through the measured refresh rate of the active mode.
2. The staircase level is `log10(frames)` and is quantised to whole frames at
   presentation time. The **presented** frame count, not the requested one, is what
   enters the threshold estimate.
3. Presentation is driven from `Choreographer` frame callbacks, and the actual interval
   is recorded from frame timestamps.
4. A trial whose stimulus interval dropped a frame is `DISCARDED`: stored as a quality
   metric, but not allowed to move the staircase. A trial that did not present correctly
   did not measure anything.
5. Every stored session records the display it was measured on, and a threshold inside
   the bottom quantisation bin is flagged as floor-limited.

## Consequences

- Thresholds are comparable within a device and are explicitly annotated across devices.
- The measurable floor is refresh-dependent (16.7 ms at 60 Hz, 8.3 ms at 120 Hz), and the
  app says so rather than letting a faster screen look like improvement.
- Sessions on a struggling device yield fewer scored trials rather than corrupted ones.
- The same `DisplayTiming` type gates the 40 Hz photic channel, since that too depends on
  the refresh rate being a workable multiple.
