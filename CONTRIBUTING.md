# Contributing to HugMun

Thank you for considering it. This project has a few rules that are stricter than usual,
and they exist because the software is used by an 83-year-old to make decisions about his
own brain.

---

## The three rules

### 1. Claims need citations

Any user-facing sentence that asserts a benefit must trace to an entry in
[`docs/research/references.bib`](docs/research/references.bib), and the practice it
belongs to must have an evidence card. If you cannot cite it, phrase it as a description
of what the app does rather than as a claim about what it will achieve.

The wording rules are not stylistic. See
[`docs/research/03-safety-and-regulatory.md`](docs/research/03-safety-and-regulatory.md)
§2.2 for the forbidden/permitted table.

### 2. Every module needs an evidence card — including an honest caveat

`EvidenceCard` requires a non-blank `caveat` at construction. That is deliberate: if
there is genuinely nothing to caveat, the evidence tier is wrong.

### 3. Anything on the measurement path needs a test

The measurement path is `:engine:psychophysics`, `:engine:scheduling`, `:engine:signal`,
and the parts of `:core:domain` that compute reliable change. A bug there does not crash
anything — it quietly produces a wrong number that someone acts on.

These modules are pure Kotlin on purpose so tests run on the JVM in milliseconds. There
is no excuse for an untested change to them.

Outside the measurement path, follow the normal standard: write a test when it is
genuinely cheaper than the alternative way of being sure, and skip it when a type check,
a build or a look at the screen would tell you the same thing faster.

---

## Getting set up

```bash
git clone https://github.com/nkVas1/HugMun.git
cd HugMun
echo "sdk.dir=/path/to/Android/sdk" > local.properties

./gradlew :app:assembleDebug
./gradlew qualityCheck          # exactly what CI runs
```

- **JDK 17 or newer.** The build uses a JDK 21 toolchain and targets bytecode 17.
- **No Android Studio required**, though it helps. Everything works from the CLI.
- `./gradlew spotlessApply` fixes formatting.

### Notes for people coming from older Android projects

- **`org.jetbrains.kotlin.android` is never applied.** AGP 9 has built-in Kotlin support
  and applies the Kotlin plugin itself. The Compose compiler plugin *is* still applied.
- **AGP 9's `CommonExtension` has no type parameters** and no lambda overloads, so shared
  build configuration in `build-logic` uses property access.
- **There is no DI framework.** The graph is hand-written. Add a dependency by adding it
  to the graph, not by adding an annotation.

---

## Code standards

- `allWarningsAsErrors` is on everywhere. This is not negotiable; warnings accumulate
  into ambiguity, and this codebase should be readable by someone who is auditing it
  rather than maintaining it.
- Detekt runs with `MagicNumber` enabled. A bare number in code that produces a value a
  person will act on must be a named constant with a comment saying where it came from.
- Spotless/ktlint owns formatting; detekt owns semantics. Run `spotlessApply` before
  pushing.
- Public API in library modules is explicit (`public` is written out). It makes "what can
  the rest of the app call?" answerable by reading.

### Comments

Explain *why*, especially where the reason is a scientific or clinical constraint rather
than a technical one. The comment that says "light theme is the default because
intraocular scatter makes light-on-dark text bloom for ageing eyes" is worth ten that
restate the code.

---

## Accessibility is a correctness requirement

Android lint runs with `warningsAsErrors` and accessibility checks promoted to fatal. In
addition, the following are hard rules — a PR that breaks one will not be merged:

- Minimum touch target **56 × 56 dp**, minimum 12 dp between targets.
- Body text 20 sp, scaling to 200 % system font size without clipping.
- Body-text contrast ≥ 7:1, enforced by `ColorContrastTest`.
- Persistent bottom navigation. **No drawer, no hamburger menu.**
- Every gesture has a visible button equivalent.
- No timeouts in the interface. Time limits exist only inside a task, where they are the
  measurement, and they are announced beforehand.
- **Nothing outside «Ритм» flashes more than three times per second.** Ever.
- Reduce-motion is honoured; no parallax, no auto-playing motion.

---

## Commits and pull requests

- Conventional commit subjects: `feat(scope):`, `fix(scope):`, `docs(scope):`,
  `build:`, `refactor(scope):`, `test(scope):`, `chore:`.
- Atomic commits. One idea per commit, and the build should pass at every commit.
- Write the body for a reviewer who was not in your head: what changed, and *why that
  and not the obvious alternative*.
- **No assistant attribution trailers.** No `Co-Authored-By` for tooling, no "generated
  with" lines.

A PR should say what it changes, what evidence or reasoning supports it, and what you
did to convince yourself it works.

---

## Changing the science

If you want to change a protocol parameter, a dose, a schedule or an evidence tier:

1. Update the relevant section of [`docs/research`](docs/research) first.
2. Add or update the citation in `references.bib`. A `doi` field goes in **only** if you
   verified it against the publisher or PubMed record — never inferred from a pattern.
3. If a tier changes, update `EvidenceTier` usage and the in-app card.
4. If it changes what ships, write an ADR in [`docs/adr`](docs/adr).

Disagreement with an interpretation in the dossier is welcome and is best raised as an
issue with the sources attached.

---

## Reporting problems

- **Safety defects** — a control that did not fire, a claim that overstates the evidence,
  a flashing element outside «Ритм» — are the highest-priority class of bug here. Use the
  safety issue template.
- **Security vulnerabilities**: see [`SECURITY.md`](SECURITY.md).
- Everything else: open an issue.
