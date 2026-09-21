# HugMun Design Language — «Рассветный воздух»

> *«Hugin ok Munin fljúga hverjan dag Jörmungrund yfir…»*
> Huginn and Muninn fly out **at dawn** over the wide earth and return by breakfast.
> Odin says he fears for Huginn — but trembles more for Muninn.
>
> — *Grímnismál*, 20

That stanza is the whole product in four lines: a daily flight out and back, and a
particular fear of losing **memory**. The design language is built on the *dawn flight*,
not on ravens as decoration. The birds appear once, as a single ink stroke. The dawn is
everywhere.

---

## 1. Positioning

The base direction is **«Рассветный воздух»** — quiet light, warm paper, generous air.
Two other directions contribute, in strictly bounded ways:

| Source | Contributes | Bounded to |
| --- | --- | --- |
| **Рассветный воздух** (base) | palette, spatial rhythm, typography, tone of voice | everything |
| **Гнездо** (craft) | real tactile depth, enamel-like materiality, weight under the thumb | **primary controls only** — the things a finger presses |
| **Обсерватория** (instrument) | hairline rules, engraved tick marks, honest scales, tabular figures | **data displays only** — charts, thresholds, trends |

The bounding is the whole trick. Tactility everywhere becomes skeuomorphic kitsch;
instrument precision everywhere becomes cold and clinical. Each belongs to exactly one
layer of the interface.

### What we are consciously not doing

Cartoon brains. Glowing neural networks. Jigsaw pieces. Lightbulbs. Confetti. Trophies.
Streak flames. Blue-to-purple "medtech" gradients. Cheerful sound effects. Any visual
that would read as *a game for old people*.

---

## 2. Principles

**1. Light is the material.**
Surfaces are separated by luminance and warmth, not by borders, cards-on-cards and
drop shadows. The screen should feel like paper under morning light.

**2. Light mode is the default, and that is a clinical decision.**
Ageing eyes need *more* light, and intraocular scatter from lens changes makes
light-text-on-dark-ground bloom and smear. The fashionable choice would be a dark app;
the correct one for an 83-year-old is a bright, warm, high-contrast page. Dark theme
exists as a preference and is forced only inside «Ритм» sessions, where a dark surround
is part of the stimulus.

**3. Air is structure, not emptiness.**
Whitespace carries the hierarchy. When a screen feels too empty, the answer is to remove
something else, not to fill it.

**4. One thing at a time.**
Every screen has exactly one primary action. If a screen needs two, it is two screens.

**5. Tactility where fingers touch.**
The primary button is a physical object: warm enamel, a genuine pressed state with
travel, a top highlight and a soft warm shadow. Nothing else in the interface pretends
to be physical.

**6. Precision where data lives.**
Charts are engraved instruments. Hairline rules, real tick marks, tabular figures,
honest zero-baselines, visible uncertainty. No gradient fills, no glow, no rounded
"friendly" bars that misrepresent a scale.

**7. Dignity is a design constraint.**
Address an adult. No praise for existing, no cartoon encouragement, no loss framing, no
comparison to other people. A missed day is stated in one neutral sentence and the plan
adjusts.

**8. The thread stays thin.**
The mythology is atmosphere, not theme. It appears in: the icon, one watermark on the
completion screen, the names of the two indices, and the About screen. Nowhere else.

---

## 3. Colour

### 3.1 The idea

A single warm light source moving across paper. The palette is derived from dawn on
unbleached paper: bone and oat for ground, iron-gall ink for text, terracotta for the
sun, and a cool slate-teal as the only cold note — the sky opposite the sunrise.

Pure `#FFFFFF` is deliberately absent from large areas: at high device brightness it
glares, and for an eye with any lens opacity it scatters.

### 3.2 Light theme — «Рассвет» (default)

| Token | Hex | Role |
| --- | --- | --- |
| `surfaceSunk` | `#EDE5D9` | recessed wells, track backgrounds |
| `surfaceBase` | `#FBF7F1` | the page |
| `surfaceRaised` | `#FFFDF9` | cards |
| `surfaceEdge` | `#E3D9C9` | hairlines, 1dp rules |
| `ink` | `#1E1B16` | body and headings |
| `inkMuted` | `#554D42` | secondary text |
| `inkFaint` | `#7D7467` | tertiary, captions, axis labels |
| `dawn` | `#A8481C` | primary accent — text-safe |
| `dawnBright` | `#C4622D` | primary fills, large elements |
| `dawnWash` | `#F6E7DA` | tinted containers |
| `sky` | `#1F4A59` | second data series, informational |
| `skyWash` | `#E2ECEF` | tinted containers |
| `moss` | `#3A5F42` | steady / on-track |
| `clay` | `#8E2F26` | attention (never called "error") |
| `ravenInk` | `#15161B` | the raven mark only — never text |

### 3.3 Dark theme — «Ночь»

| Token | Hex | Role |
| --- | --- | --- |
| `surfaceSunk` | `#0C0C0A` | |
| `surfaceBase` | `#141412` | the page |
| `surfaceRaised` | `#1D1C19` | cards |
| `surfaceEdge` | `#302E29` | hairlines |
| `ink` | `#F2ECE1` | body |
| `inkMuted` | `#B3AA9C` | secondary |
| `inkFaint` | `#867E71` | tertiary |
| `dawn` | `#E8A268` | primary accent |
| `dawnBright` | `#F0B77F` | |
| `dawnWash` | `#2B2018` | |
| `sky` | `#8FC0CE` | |
| `moss` | `#8FBE99` | |
| `clay` | `#E58C81` | |

### 3.4 Contrast is enforced, not asserted

`:core:designsystem` ships `ContrastTest`, a unit test that computes WCAG 2.x relative
luminance for **every** foreground/background token pair the theme actually uses and
fails the build below target:

| Pair class | Required |
| --- | --- |
| Body text on any surface | **≥ 7.0:1** (AAA) |
| Large text (≥ 24sp) and icons | ≥ 4.5:1 |
| Control boundaries and focus rings | ≥ 3.0:1 |

If a colour is changed and a ratio drops, the build breaks. Contrast is not a review
item here; it is a test.

### 3.5 Colour is never the only signal

Blue–yellow discrimination declines with age, and colour vision deficiency is common.
Every state that uses colour also carries a shape, a glyph, a position or a word.
Trend direction uses an arrow *and* a sign *and* a word, never a red/green fill alone.

### 3.6 Light that moves with the day

The page's warmth shifts very slightly with local solar time — cooler and paler at
dawn, fuller at midday, deeper and more amber at dusk. The total excursion is small
(a few percent in luminance and a few degrees in hue): it should be felt rather than
noticed, and it must never reduce contrast below §3.4. It is disabled outright under
reduce-motion and under a high-contrast system setting.

This is the atmosphere the brief asked for, achieved without a single illustration.

---

## 4. Typography

Two families, both open-licensed (SIL OFL) and both with genuinely good Cyrillic —
which rules out most "characterful" display faces.

| Role | Family | Why |
| --- | --- | --- |
| Display / editorial | **Literata** | A serif designed for extended screen reading, with a warm, bookish colour that carries the manuscript thread without costuming. Variable weight. Strong Cyrillic. |
| UI / body / data | **Golos Text** | A humanist sans drawn specifically for Russian legibility at working sizes, with open apertures and unambiguous letterforms. Tabular figures for data. |

Latin and Cyrillic are set from the same families, so the two localisations look like
the same product.

### 4.1 Scale

Base body is **20 sp** — larger than a general-audience app, because the primary user is
83. The scale is modest (ratio ≈ 1.2) so that large default sizes do not force display
text off-screen.

| Token | Size / line | Family | Use |
| --- | --- | --- | --- |
| `displayL` | 44 / 48 | Literata | session titles, single-word moments |
| `displayM` | 34 / 40 | Literata | screen titles |
| `titleL` | 26 / 32 | Golos Text SemiBold | section heads |
| `titleM` | 22 / 28 | Golos Text SemiBold | card heads |
| `bodyL` | **20 / 30** | Golos Text | **default body** |
| `bodyM` | 18 / 26 | Golos Text | secondary body |
| `label` | 16 / 20 | Golos Text Medium | never load-bearing |
| `numericXL` | 56 / 56 | Golos Text, tabular | the one big number |
| `numericM` | 24 / 28 | Golos Text, tabular | table and axis figures |

### 4.2 Rules

- Everything scales with the system font setting to **200 %** without clipping or
  truncation. Layouts reflow; they do not shrink text to fit.
- Line length is capped at ~60 characters.
- No all-caps for anything longer than a two-word label — capitals hurt word-shape
  recognition, which matters more with age.
- No italic for body text.
- Numbers in any table, axis or comparison use tabular figures, always.

---

## 5. Space, shape and material

### 5.1 Spacing

A 4 dp base grid; layout uses the 8 dp subset. Screen gutter **24 dp**. Vertical rhythm
between distinct blocks **32 dp** — generous on purpose.

### 5.2 Touch targets

**Minimum 56 × 56 dp**, above the 48 dp guideline, with **≥ 12 dp** between adjacent
targets. Primary actions are considerably larger: a full-width control 72 dp tall.

### 5.3 Shape

| Element | Radius |
| --- | --- |
| Card | 20 dp |
| Primary control | 28 dp |
| Input, small control | 14 dp |
| Chip, badge | full |
| Stimulus surfaces | 0 — task screens are unstyled by design |

### 5.4 Material — the enamel control

Reserved for the single primary action on a screen.

```
   ┌───────────────────────────────────────┐   ← 1dp inner top highlight, #FFFFFF @ 40%
   │                                       │
   │              Начать                   │      fill: dawnBright
   │                                       │      label: surfaceBase, titleL
   └───────────────────────────────────────┘
      soft warm shadow: y+6, blur 20, dawn @ 18%

   pressed: translate y+2, shadow → y+2 blur 8, fill darkens 6%
```

Nothing else in the interface carries a shadow. Secondary controls are outlined with a
1 dp `surfaceEdge` rule; tertiary actions are plain text with a generous hit area.

### 5.5 The engraved chart

Data displays borrow from instrument engraving:

- 1 dp hairlines in `surfaceEdge`; no gridlines heavier than the data.
- Real tick marks with labelled units. Axes always state what they measure.
- Zero baselines shown when the scale includes zero; truncated axes are marked as such.
- Uncertainty is drawn, not hidden: the reliable-change band from
  [`../research/05-measurement.md`](../research/05-measurement.md) is a visible shaded
  corridor, and points inside it are rendered as "no change".
- Tabular figures, right-aligned.
- Series are distinguished by **colour and dash pattern and direct labels** — never by
  a legend alone.

---

## 6. Motion

Motion in this app is slow, brief and never decorative.

| Purpose | Duration | Curve |
| --- | --- | --- |
| Screen transition ("settling") | 420 ms | `cubic-bezier(0.32, 0.72, 0, 1)` |
| Element enter | 320 ms | same |
| State change within a control | 180 ms | `cubic-bezier(0.4, 0, 0.2, 1)` |
| Breath pacer | period = 60/rf seconds | a sine-derived ease, asymmetric 40:60 |

**The settling motion** is the one signature: content arrives from 12 dp below with a
gentle deceleration, like a bird landing. It never overshoots, never bounces, never
springs. Bounce reads as playful; playful reads as childish.

**Reduce-motion is honoured absolutely.** With it enabled, every transition becomes a
120 ms cross-fade and the time-of-day light shift is disabled. Nothing in the interface
flashes more than three times per second, ever — the 40 Hz stimulus is not interface
content and lives behind the gate described in
[`../research/03-safety-and-regulatory.md`](../research/03-safety-and-regulatory.md).

---

## 7. The raven thread

Total budget: **four appearances**. Anything more and it becomes a mascot.

1. **The mark.** A single sumi-e brush stroke that reads as a bird in flight; its
   negative space forms a second bird. Used as the launcher icon and the splash mark.
   Drawn as a path, rendered in `ravenInk`.
2. **The watermark.** The same mark at 4 % opacity, low on the session-completion
   screen. Effectively subliminal.
3. **The two indices.** The long-term trend view has two: **«Мысль»** (reasoning, speed,
   attention) and **«Память»** (recall). Labelled in plain Russian. Their origin is
   explained only in About.
4. **The About screen.** Where the Grímnismál stanza is quoted, in Old Norse and in
   Russian, and where the name is explained.

The daily rhythm — a flight out at dawn, a return — is expressed structurally, through
the schedule and the light, rather than through imagery.

---

## 8. Voice

Russian, plain, short, respectful. Second person plural («вы»), never «ты».

| Do | Don't |
| --- | --- |
| «Сегодня — упражнение на скорость. 12 минут.» | «Готовы прокачать мозг? 💪» |
| «Вчера вы пропустили занятие. Я перенёс его на сегодня.» | «Вы потеряли серию из 7 дней!» |
| «Ваш результат — 84 мс. В прошлый раз было 91 мс.» | «Отлично! Вы супер! 🎉» |
| «Это изменение в пределах вашей обычной колебаний.» | «Ваш мозг стал моложе на 3 года!» |
| «Так было в исследовании. Про вас лично это ничего не говорит.» | «Доказано, что это снижает риск деменции.» |

No emoji anywhere in the product. No exclamation marks except in genuine safety copy.
No English loanwords where a Russian word exists («занятие», not «сессия», in
user-facing text).

Every exercise carries an **evidence card** written in this voice: what was studied, in
whom, how many people, what was found, and how confident we are. The honest version is
more persuasive than the marketing version, and it is the only version that respects the
reader.

---

## 9. Sound

- No success chimes, no failure buzzes, no ambient music.
- Sound exists in exactly three places: the breathing pacer (a soft, low, optional
  tone), the 40 Hz stimulus, and spoken pacing in «Движение».
- Every sound has an off switch that persists.
- Haptics: a single light tick on primary press. Nothing else.

---

## 10. Accessibility as design, not as audit

Everything in [`../research/03-safety-and-regulatory.md`](../research/03-safety-and-regulatory.md) §8
is treated as a design input rather than a compliance pass. In particular:

- Bottom navigation, always visible, three destinations maximum. No drawer.
- No gesture without a visible button equivalent.
- No timeouts in the interface. Timing exists only inside a task, where it *is* the
  measurement, and it is announced beforehand.
- Every screen is usable one-handed, with the primary action in the bottom third.
- TalkBack: task screens announce state transitions; charts expose a text summary of
  the trend, including the uncertainty band.
