# 🎨 09 — Design system: the "Vault" palette

This document is the contract between the design and the code. Anything visual — colour,
type, spacing, radius, elevation, iconography — that ships in the Fortress app should be
expressible in the tokens defined here. If a screen reaches for a hex literal or a
hand-tuned size, that's a bug.

The "Vault" palette is the lavender-led light theme inspired by the Bankio / Streamline UI
reference. Dark mode adapts the same palette to ink-black surfaces so system-level dark
mode is supported without a brand reskin.

---

## 1. Tone, principles, and what we don't do

Fortress is a security-first fintech. The visual system has three jobs:

- **Calm by default.** A wallet app is read more than it's touched. The page background is
  always a soft, low-chroma surface (`Cloud50`) and high-contrast accents are reserved for
  one thing per screen — the balance, a CTA, the active tab.
- **Money first.** Currency amounts get their own typography pair (`MoneyDisplay` + tail).
  Trailing decimals are muted so the value reads at a glance and the cent fraction can be
  scanned secondarily.
- **Surface hierarchy is colour, not elevation.** Material 3 elevation is reserved for the
  card carousel and bottom nav (the only floating elements). Everything else differentiates
  through `surface` / `surfaceContainer` / `primaryContainer`. Drop shadows are avoided
  outside the card swatches.

What we don't do: long shadows, gradients on UI chrome, decorative iconography on buttons,
emojis as visual elements, more than one accent colour per screen.

---

## 2. Colour tokens

All raw colours live in
[`Color.kt`](../app/src/main/java/com/umain/fortress/ui/theme/Color.kt). Material 3
`ColorScheme` wiring is in
[`Theme.kt`](../app/src/main/java/com/umain/fortress/ui/theme/Theme.kt), and extended
tokens that don't fit the M3 scheme (gradients, money tail grey, danger/warning surfaces)
are exposed via `FortressTheme.colors` (a typed [`FortressColors`] bag).

### Lavender — primary accent (identity, focus, selected states)

| Token | Hex | Usage |
|---|---|---|
| `Lavender50`  | `#F5F0FF` | Primary container surface in light mode |
| `Lavender100` | `#E9DEFF` | Section chip backgrounds |
| `Lavender200` | `#D7C4FF` | Empty-state fills (Add Card swatch) |
| `Lavender300` | `#BEA1FF` | Primary accent in dark mode |
| `Lavender400` | `#A988F2` | Disabled-but-tonal states |
| `Lavender500` | `#8E6BE6` | **Primary in light mode** — CTA fill, dot indicators, focus rings |
| `Lavender600` | `#724FD0` | Pressed / hover state for primary |
| `Lavender700` | `#5635AE` | Primary container content; deep card gradients |

### Ink — deep surfaces (bottom nav, scan modal, dark theme)

| Token | Hex | Usage |
|---|---|---|
| `Ink950`            | `#06070B` | Scrim, scan-mask shadow base |
| `InkSurfaceDark`    | `#0E1018` | Dark theme background |
| `InkSurfaceElevated`| `#161A26` | Dark theme `surface`, ink card variants |
| `InkSurfaceHigh`    | `#1F2435` | Dark theme `surfaceVariant`, dividers |
| `Ink500`            | `#323A55` | Subtle text on ink surfaces |

### Cloud — off-white surfaces (light theme)

| Token | Hex | Usage |
|---|---|---|
| `Cloud0`   | `#FFFFFF` | Light theme `surface` (cards, sheets) |
| `Cloud50`  | `#FAF7FF` | Light theme `background` (subtle violet wash) |
| `Cloud100` | `#F1ECFA` | `surfaceContainer` (text-field fill, keypad keys) |
| `Cloud200` | `#E3DBF1` | `surfaceContainerHigh` (divider strokes) |
| `Cloud300` | `#CFC3E2` | `outline`, dot-indicator inactive |

### Mist — low-emphasis text + outlines

| Token | Hex | Usage |
|---|---|---|
| `MistText300` | `#A9A3BD` | Dark-mode `onSurfaceVariant` |
| `MistText500` | `#6E6883` | Light-mode `onSurfaceVariant`, money tail grey |
| `MistText700` | `#3D3852` | High-emphasis secondary text on light surfaces |

### Semantic accents

| Token | Hex | Pair | Usage |
|---|---|---|---|
| `Sage500`         | `#2EB37A` | `Sage100` | Credit / positive transactions, success chips |
| `Coral500`        | `#E5484D` | `Coral100` | Errors, debit warning, untrusted shield |
| `AmberStatus500`  | `#E9A23B` | `AmberStatus100` | Medium-risk transactions, "Limited" verdict |

The semantic accents are also surfaced via `FortressTheme.colors.successSurface`/`successOn`,
`warningSurface`/`warningOn`, `dangerSurface`/`dangerOn` so callers don't reach past the
theme abstraction.

### Gradients

There's one gradient in the system — the page wash used by Splash and Onboarding. It runs
from `FortressTheme.colors.pageGradientTop` to `pageGradientBottom`. In light mode that's
`Cloud50 → Lavender100`. In dark mode it's `InkSurfaceDark → Ink950`. Other gradients
(card swatches, scan-line shimmer) are local and not part of the public token set.

---

## 3. Typography

All type lives in
[`Type.kt`](../app/src/main/java/com/umain/fortress/ui/theme/Type.kt). Reference faces:
**Inter** for sans, **JetBrains Mono** for IDs / account numbers (not money). Until the
font assets ship under `app/src/main/res/font/`, the implementation falls back to
`FontFamily.SansSerif` / `FontFamily.Monospace`, which keeps the build clean and the visual
defaults close.

### Display, headline, title, body, label

The Material 3 type roles are wired against Inter:

| Role | Size / line / weight | Used for |
|---|---|---|
| `displayLarge`  | 52 / 60 / Bold      | Marketing-grade hero numbers (unused in-app today) |
| `displayMedium` | 40 / 48 / Bold      | Splash / Onboarding wordmark |
| `displaySmall`  | 32 / 40 / SemiBold  | Onboarding slide titles |
| `headlineLarge` | 28 / 36 / SemiBold  | Section banners |
| `headlineMedium`| 22 / 28 / SemiBold  | Modal page titles |
| `headlineSmall` | 18 / 24 / SemiBold  | Top-bar page titles ("Your accounts") |
| `titleLarge`    | 16 / 22 / SemiBold  | "Hi, Jack", "Total Balance" |
| `titleMedium`   | 14 / 20 / Medium    | Account display names |
| `titleSmall`    | 12 / 16 / Medium    | Card brand stamps |
| `bodyLarge`     | 16 / 24 / Regular   | Transaction description, primary body |
| `bodyMedium`    | 14 / 20 / Regular   | Secondary copy, helper text |
| `bodySmall`     | 12 / 16 / Regular   | Captions, "Welcome", masked numbers |
| `labelLarge`    | 14 / 20 / SemiBold  | Button text, pill labels |
| `labelMedium`   | 12 / 16 / Medium    | Chip text, dotted-list labels |
| `labelSmall`    | 11 / 14 / Medium    | Card "CARDHOLDER" / "EXPIRES" stamps |

### Money typography — the head/tail pair

Currency amounts use a paired style: a large-weight head ("`$31,180`") and a muted small
tail ("`.24`"). The tail is rendered in `FortressTheme.colors.moneyTail` (`#6E6883`). Four
size pairs exist:

| Size | Head | Tail | Used for |
|---|---|---|---|
| `Display` | `MoneyDisplay` 44/52 Bold | `MoneyDisplayTail` 22/28 SemiBold | Dashboard total balance, Analytics total |
| `Large`   | `MoneyLarge` 28/36 Bold    | `MoneyLargeTail` 16/20 SemiBold   | Transfer review screen |
| `Medium`  | `MoneyMedium` 18/24 SemiBold | `MoneyMediumTail` 14/20 Medium  | Account list rows, monthly payments |
| `Small`   | `MoneySmall` 14/20 SemiBold | `MoneySmallTail` 11/14 Medium     | Transaction rows |

**Always use the `MoneyText` composable** (see [`MoneyText.kt`](../app/src/main/java/com/
umain/fortress/ui/components/MoneyText.kt)) rather than concatenating the head and tail by
hand — it handles locale-aware grouping, currency-symbol vs ISO-code rendering, and the
"eye toggle" `hidden` variant (digits replaced with bullet glyphs, sign and symbol
preserved).

### Mono

`MonoCaption` (12sp JetBrains Mono / Monospace fallback) is reserved for **identifiers
that are not money**: masked account numbers, IBANs, transaction IDs, PAN reveals. Money
itself uses the sans head/tail pair above so digits in the balance feel typographic, not
data-rowed.

---

## 4. Spacing, radius, elevation

### Spacing scale

Compose `dp` values, used directly rather than wrapped in tokens (the Compose ecosystem
norm). The conventional rungs are **4, 8, 10, 12, 14, 16, 20, 24, 28, 32 dp**. Page
content inset is always 20dp. Sections within a page are separated by 20dp. Items inside
a row are separated by 12dp.

### Radius scale (Shapes)

| Token | Radius | Used for |
|---|---|---|
| `extraSmall` | 10 dp | Risk badges (rare) |
| `small`      | 14 dp | Tiny chips |
| `medium`     | 18 dp | Text-field corners |
| `large`      | 24 dp | Account rows, card swatches, keypad keys |
| `extraLarge` | 32 dp | Balance card, bottom nav, scan frame, money tip card |
| `FortressPillShape` | 50% | Send/Receive pills, all primary CTAs |

### Elevation

We use only two elevations:

- `0 dp` — every static surface. Hierarchy is conveyed through colour.
- `tonalElevation = 0 dp, shadowElevation = 6 dp` — the bottom tab bar (lifted off the
  page) and the card carousel swatches (mock raised cards).

No general drop shadows on lists, headers, or buttons.

---

## 5. Component anatomy

All components live under `com.umain.fortress.ui.components` and only reach into the
theme + icon registry. Their anatomy:

### `BalanceCard`

Sits directly on the page background (no surface fill). Children:
1. "Total Balance" label — `titleMedium`, `onSurfaceVariant`.
2. `MoneyText` head/tail pair — `MoneySize.Display`, `onBackground` head + `moneyTail`
   tail.
3. Eye toggle icon (`FortressIcons.Eye` / `EyeOff`) — flips an internal `hidden` flag so
   the digits render as bullet glyphs.
4. Account display name + masked number footnote — `bodySmall`, `onSurfaceVariant`.

### `CardCarousel`

A horizontal `LazyRow` of 168×104dp swatches. Each swatch has a brand-coloured gradient,
the brand stamp top-right (`labelSmall` SemiBold, white 85%), and the last-four PAN + holder
name bottom-left. A trailing 64×104dp pastel "+" slot opens "Add Card".

### `QuickActionPill` / `QuickActionSquare` / `QuickActionTile`

Three flavours of the same action vocabulary:
- **Pill** — leading 36dp lavender circle + sans label, pill outline.
- **Square** — 52dp rounded-large surface with a single 20dp icon, used for "more / grid".
- **Tile** — vertical icon-above-label, used in dense grids like the Cards screen.

### `MoneyText` (component, not just a token)

The only correct way to render currency in the app. Renders the paired head/tail in matched
sizes; supports `hidden = true` for the eye toggle; supports `useSymbol = true/false` for
`$31,180.24` vs `31,180.24 USD`. Pre-split overload (`MoneyText(parts: MoneyParts, …)`)
exists so transaction rows don't reformat on every recomposition.

### `TransactionRow`

40dp circle with directional icon (`FortressIcons.Receive` for credit,
`FortressIcons.Send` for debit), description on `bodyLarge`, counterparty on `bodySmall`,
`MoneyText` aligned to the trailing edge tinted with the success colour on credit and the
on-surface ink on debit, optional risk badge below.

### `BottomTabBar`

Pill-shaped 64dp-tall ink-black surface, 5 tabs spaced evenly. Each tab is a 44dp tap
target; the selected tab gets a lavender filled circle around its icon. Always padded by
`navigationBarsPadding()` so it sits above the OS gesture bar.

### `SecurityChip`

CircleShape-rounded pill with leading status icon + label. Three states from
`IntegrityVerdict`:

| State | Icon | Surface | Content |
|---|---|---|---|
| `Trusted`   | `FortressIcons.ShieldGood` | `Sage100`        | `Sage500` |
| `Limited`   | `FortressIcons.ShieldMaybe`| `AmberStatus100` | `#8A5300` |
| `Untrusted` | `FortressIcons.ShieldBad`  | `Coral100`       | `Coral500` |

### `FortressTextField`

`OutlinedTextField` with `shapes.large` corners, `surfaceContainer` fill at rest, `surface`
fill when focused, and a `primary` border + label on focus. Disabled state strips the
border and keeps the container fill at 0% emphasis.

---

## 6. Iconography

Reference set: **"Free UI Icons — Open Source Vector Icon Set"** from the Streamline /
Lucide lineage on the Figma Community
([file 1063138616574654762](https://www.figma.com/community/file/1063138616574654762/free-ui-icons-open-source-vector-icon-set-svg)).
Visual fingerprint: 24×24 viewport, 1.5px stroke, round caps + joins, optical sizing.

Implementation: every icon used in the app routes through the
[`FortressIcons`](../app/src/main/java/com/umain/fortress/ui/icons/FortressIcons.kt)
registry, which exposes named `ImageVector` properties. Today the registry returns
**Material Symbols (Outlined variant)** — close enough in stroke weight + geometry that
the app reads as Lucide-styled without shipping a second SVG bundle.

To swap in the exact Streamline / Lucide SVGs, export each glyph from the Figma file
(or pull them from the [lucide.dev](https://lucide.dev) MIT mirror), drop the `pathData`
into `ImageVector.Builder { ... }` calls, and replace the property bodies inside
`FortressIcons`. No caller needs to change.

Caller rule: **never** reach for `Icons.Default.*` directly. Always go through
`FortressIcons.X`. That keeps the icon swap a single PR.

### Coverage table

| Name | Used for | Material fallback today |
|---|---|---|
| `Back`              | Back chevron | `AutoMirrored.Outlined.ArrowBack` |
| `Logout`            | Sign-out icon row | `AutoMirrored.Outlined.Logout` |
| `Notifications`     | Bell in dashboard header | `Outlined.NotificationsNone` |
| `Settings`          | Settings row in Profile | `Outlined.Settings` |
| `More`              | Overflow dots | `Outlined.MoreHoriz` |
| `Home` / `Cards` / `Scan` / `Analytics` / `Profile` | Bottom-tab icons | `Outlined.Home` / `Wallet` / `QrCodeScanner` / `PieChart` / `AccountCircle` |
| `Send` / `Receive`  | Quick-action pills, transaction rows | `Outlined.TrendingUp` / `Outlined.SwapHoriz` |
| `Grid`              | "All accounts" square button | `Outlined.GridView` |
| `Savings` / `Investment` / `Card` | Account list icons | `Outlined.Savings` / `ShowChart` / `CreditCard` |
| `ShieldGood` / `ShieldMaybe` / `ShieldBad` | `SecurityChip` states | `Outlined.GppGood` / `GppMaybe` / `GppBad` |
| `ShieldVerified`    | Onboarding zero-trust slide | `Outlined.VerifiedUser` |
| `Fingerprint` / `Lock` / `Bolt` | Onboarding security slides | `Outlined.Fingerprint` / `Lock` / `Bolt` |
| `Eye` / `EyeOff`    | Balance redact toggle | `Outlined.Visibility` / `VisibilityOff` |
| `Check`             | Transfer success | `Outlined.CheckCircle` |
| `Insights` / `Document` | Profile rows | `Outlined.Insights` / `Description` |

---

## 7. Light / dark adaptation

Light mode is the canonical reference: lavender accents on a soft violet-washed off-white
background with ink-black secondary surfaces (bottom nav, scan modal).

Dark mode keeps the brand identity by:

- Promoting `Lavender300` to `primary` (legibility on dark surfaces).
- Setting `background` to `InkSurfaceDark`, `surface` to `InkSurfaceElevated`,
  `surfaceVariant` to `InkSurfaceHigh`.
- Inverting the secondary axis: `secondary = Cloud0` so the same "ink card" affordance
  reads as an off-white card on a dark page (still high contrast, still a "popped"
  surface).
- The money-tail token shifts from `#6E6883` (light) to `MistText300` (dark) so the
  contrast ratio against the head colour stays in the same ballpark.

System status / navigation bar icons follow the appearance via
`WindowCompat.getInsetsController(...).isAppearanceLight*Bars = !darkTheme`.

---

## 8. Accessibility

- **Contrast.** Primary content on background is `Ink900` on `Cloud50` in light (≈14:1)
  and `Cloud0` on `InkSurfaceDark` in dark (≈16:1). The secondary `onSurfaceVariant`
  (`MistText500`) on `Cloud50` is ≈4.7:1 — clears WCAG 2.2 AA for body text. Money tails
  on `Cloud50` are ≈4.7:1 (small, but never sole carrier of meaning — they always pair
  with the head).
- **Hit targets.** Every interactive element is at least 44×44dp. The bottom-tab slot is
  exactly 44dp; quick-action pills are 56dp tall; the eye toggle has a 22dp icon inside a
  full row tap area.
- **Reading order.** Money is read **head → tail → currency**. The `MoneyText` composable
  builds an `AnnotatedString` in that order so TalkBack hears "thirty-one thousand one
  hundred eighty point twenty-four dollars" rather than reading the styled chunks
  out of order.
- **Visibility toggle.** Balance redaction uses a button with both an icon swap and a
  distinct `contentDescription` ("Show balance" / "Hide balance"). The bullet glyph used
  is U+2022, which TalkBack reads as "bullet" — adequate as the row is still labelled.
- **State without colour.** Transaction credit/debit also flips the leading icon
  (`Receive` vs `Send`) so colour is not the sole carrier.

---

## 9. Migration map (from the old Midnight/Emerald/Violet theme)

The old palette is kept as `@Deprecated` aliases inside `Color.kt` so the migration is
incremental. Map:

| Old token | New canonical token |
|---|---|
| `Midnight900` / `Midnight800` / `Midnight700` | `Lavender700` / `Lavender600` / `Lavender500` |
| `Midnight200` / `Midnight100` | `Lavender100` / `Lavender50` |
| `Emerald500` / `Emerald600` / `Emerald400` | `Sage500` |
| `Emerald100` | `Sage100` |
| `Violet500` / `Violet600` / `Violet400` / `Violet100` | `Lavender500` / `Lavender600` / `Lavender300` / `Lavender100` |
| `Vermilion500` / `Vermilion600` / `Vermilion400` | `Coral500` |
| `Amber500` / `Amber400` | `AmberStatus500` |
| `Slate500` / `Slate700` | `MistText500` / `MistText700` |
| `Ink900` / `Ink800` / `Ink700` | `InkSurfaceDark` / `InkSurfaceElevated` / `InkSurfaceHigh` |
| `Mist300` / `Mist100` | `MistText300` / `Cloud0` |

Once every caller has been migrated, the aliases can be deleted in a follow-up PR.

---

## 10. Source of truth and contribution rules

- The Figma reference for the system is the
  [Free UI Icons](https://www.figma.com/community/file/1063138616574654762/free-ui-icons-open-source-vector-icon-set-svg)
  file plus the Bankio-style dashboard mocks (kept in the design archive — not in this
  repository).
- The **code** under `app/src/main/java/com/umain/fortress/ui/theme/` and `…/ui/icons/`
  is the authoritative implementation. If the design changes, change the tokens here
  first, then update this document, then update callers.
- New screens **must** route exclusively through `MaterialTheme.colorScheme` /
  `FortressTheme.colors` / `MaterialTheme.typography` / `MaterialTheme.shapes` /
  `FortressIcons`. Direct hex literals or `Icons.Default.*` calls should fail review.
