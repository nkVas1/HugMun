## What this changes

<!-- One or two sentences. What is different after this PR? -->

## Why this and not the obvious alternative

<!-- The reasoning a reviewer cannot reconstruct from the diff. -->

## Checklist

- [ ] `./gradlew qualityCheck` passes locally
- [ ] Commits are atomic and the build passes at each one
- [ ] No assistant attribution trailers in commit messages

### If this touches the measurement path (`:engine:*`, reliable-change code)

- [ ] Covered by a unit test
- [ ] Any constant that affects a reported number is named and its origin documented

### If this touches user-facing copy

- [ ] Every benefit claim traces to `docs/research/references.bib`
- [ ] Wording passes `docs/research/03-safety-and-regulatory.md` §2.2
- [ ] No emoji; «вы», not «ты»

### If this touches the UI

- [ ] Touch targets ≥ 56 dp with ≥ 12 dp between them
- [ ] Readable at 200 % system font size without clipping
- [ ] Nothing flashes more than three times per second (outside «Ритм»)
- [ ] Reduce-motion honoured
- [ ] Every gesture has a visible button equivalent

### If this changes a protocol, dose or evidence tier

- [ ] `docs/research` updated first
- [ ] Citation added with a **verified** DOI or URL
- [ ] ADR written if it changes what ships
