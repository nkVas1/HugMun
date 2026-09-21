# ADR 0002 — No dependency-injection framework

**Status:** Accepted · 2026-09-21

## Context

Hilt is the Google-recommended default for Android DI and would be the unsurprising
choice. It brings a KSP processor into every module that participates in the graph.

Two things weigh against it here. First, the measurement path (`:engine:*`, parts of
`:core:domain`) is deliberately plain Kotlin so it can be reasoned about and tested
without Android; threading annotation processing through it works against that. Second,
the project's stated goal is auditability by people who are not Android developers, and
a generated graph is exactly the kind of thing that is hard to audit — "where does this
instance come from?" should be answerable by reading, not by opening generated sources.

## Decision

Hand-write the dependency graph in `:app` as an explicit container, constructed once and
passed down. ViewModels are created through a factory that reads from it. No annotations,
no generated code, no scoping DSL.

KSP is still used — but only for Room, where the alternative is writing SQL binding by
hand.

## Consequences

- Wiring is visible in one file and reads top to bottom.
- Build times are lower and one whole class of codegen failure disappears.
- Adding a dependency means editing the graph. With ~20 modules this is a few lines; at
  significantly larger scale it would become tedious.
- Feature modules depend on interfaces in `:core:domain`, never on implementations, so
  the absence of a framework does not weaken the boundaries.

## What would change our mind

If the module count or the number of scoped, request-lifetime dependencies grows to the
point where the container stops fitting comfortably in one screen, Hilt becomes the right
answer and this ADR should be superseded rather than quietly ignored.
