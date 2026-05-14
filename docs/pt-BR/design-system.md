# Design system: a paleta "Vault"

Este documento é o contrato entre design e código. Qualquer coisa visual — cor, tipografia,
spacing, radius, elevação, iconografia — que shipa no app Fortress deveria ser expressável
nos tokens definidos aqui. Se uma tela alcança um literal hex ou um tamanho hand-tuned, é bug.

A paleta "Vault" é o tema light lavender-led inspirado pela referência de UI Bankio /
Streamline. O dark mode adapta a mesma paleta para superfícies ink-black para suporte de
dark mode no nível do sistema sem reskin de marca.

---

## 1. Tom, princípios, e o que a gente não faz

Fortress é um fintech security-first. O sistema visual tem três trabalhos:

- **Calmo por default.** Um app de wallet é mais lido que tocado. O background da página é
  sempre uma superfície soft, de baixa cromaticidade (`Cloud50`) e accents de alto contraste
  são reservados para uma coisa por tela — o saldo, um CTA, a tab ativa.
- **Dinheiro primeiro.** Valores monetários têm o próprio par de tipografia (`MoneyDisplay`
  + tail). Decimais finais são apagados para o valor ser lido num lance d'olho e a fração de
  cents poder ser scanneada secundariamente.
- **Hierarquia de superfície é cor, não elevação.** Elevação Material 3 é reservada para o
  card carousel e o bottom nav (os únicos elementos flutuantes). Tudo o mais diferencia
  através de `surface` / `surfaceContainer` / `primaryContainer`. Drop shadows são evitadas
  fora dos card swatches.

O que a gente não faz: long shadows, gradientes em UI chrome, iconografia decorativa em
botões, emojis como elementos visuais, mais de uma accent color por tela.

---

## 2. Tokens de cor

Todas as cores raw vivem em
[`Color.kt`](../../app/src/main/java/com/umain/fortress/ui/theme/Color.kt). O wiring de
`ColorScheme` Material 3 fica em
[`Theme.kt`](../../app/src/main/java/com/umain/fortress/ui/theme/Theme.kt), e tokens
estendidos que não cabem no scheme M3 (gradientes, money tail grey, surfaces de
danger/warning) são expostos via `FortressTheme.colors` (um saco tipado [`FortressColors`]).

### Lavender — accent primária (identidade, foco, estados selecionados)

| Token | Hex | Uso |
|---|---|---|
| `Lavender50`  | `#F5F0FF` | Surface de primary container em light mode |
| `Lavender100` | `#E9DEFF` | Backgrounds de chips de seção |
| `Lavender200` | `#D7C4FF` | Empty-state fills (swatch Add Card) |
| `Lavender300` | `#BEA1FF` | Accent primária em dark mode |
| `Lavender400` | `#A988F2` | Hover / accent secundária |
| `Lavender500` | `#8E6BE6` | **Brand primária em light mode** — CTAs, focus rings |
| `Lavender600` | `#724FD0` | Border de focused text field |
| `Lavender700` | `#5635AE` | Primary container em dark mode, ícones de high-contrast |

### Ink — surfaces deep (bottom nav, scan modal, card backgrounds, dark theme)

| Token | Hex | Uso |
|---|---|---|
| `Ink950` | `#06070B` | Background da página em dark mode |
| `InkSurfaceDark` | `#0E1018` | Background quando dark theme é ativo |
| `InkSurfaceElevated` | `#161A26` | Cards em dark mode |
| `InkSurfaceHigh` | `#1F2435` | Surface variant em dark mode |
| `Ink500` | `#323A55` | Linhas de divisor em dark mode |

A família Ink é também a fonte do "always-ink card" — o cartão único high-contrast usado pelo
modal de Scan QR e pelo bottom navigation, independente do system theme.

### Cloud — surfaces off-white (background da página, surface de card, dividers)

| Token | Hex | Uso |
|---|---|---|
| `Cloud0` | `#FFFFFF` | Surface de card em light mode |
| `Cloud50` | `#FAF7FF` | Background da página em light mode (subtle lavender wash) |
| `Cloud100` | `#F1ECFA` | Surface variant em light mode |
| `Cloud200` | `#E3DBF1` | Surface container high em light mode |
| `Cloud300` | `#CFC3E2` | Outline em light mode |

### Mist — texto low-emphasis + outlines sutis

| Token | Hex | Uso |
|---|---|---|
| `MistText300` | `#A9A3BD` | Texto on-surface-variant em dark mode |
| `MistText500` | `#6E6883` | Texto on-surface-variant em light mode, money tail |
| `MistText700` | `#3D3852` | Texto on-surface em legacy call sites |

### Accents semânticos

| Token | Hex | Uso |
|---|---|---|
| `Sage500` | `#2EB37A` | Credit / positivo — labels de Receive, valores incoming |
| `Sage100` | `#D6F2E5` | Surface "ok" para chips de risk score |
| `Coral500` | `#E5484D` | Error / debit warning / labels de high-risk |
| `Coral100` | `#FDE2E2` | Surface "danger" para chips de risk score |
| `AmberStatus500` | `#E9A23B` | Medium risk, warnings |
| `AmberStatus100` | `#FFE9B8` | Surface "warning" para chips de risk score |

### Tokens estendidos (`FortressTheme.colors`)

```kotlin
data class FortressColors(
    val pageGradientTop: Color,
    val pageGradientBottom: Color,
    val cardSurface: Color,           // off-white em light, deep ink em dark
    val cardInk: Color,                // variante always-ink (Scan, Bottom nav)
    val cardInkContent: Color,         // texto/ícones no card always-ink
    val moneyTail: Color,              // grey para a cauda .24 no money display
    val successSurface: Color,
    val successOn: Color,
    val warningSurface: Color,
    val warningOn: Color,
    val dangerSurface: Color,
    val dangerOn: Color,
    val divider: Color,
    val isLight: Boolean,
)
```

Use `FortressTheme.colors.successSurface` em vez de chumbar `Sage100` — o token resolve
diferente entre light e dark theme.

---

## 3. Tipografia

Fontes de referência: **Inter** (sans, body + headlines de UI) e **JetBrains Mono** (money /
IDs). Até as assets de fonte chegarem em `res/font`, fallback para sans/mono da plataforma.

### Escala (em [`Type.kt`](../../app/src/main/java/com/umain/fortress/ui/theme/Type.kt))

| Estilo | Size / line-height | Uso |
|---|---|---|
| `displayLarge` | 52 / 60, Bold, -1 letter spacing | Cabeçalho de Onboarding |
| `displayMedium` | 40 / 48, Bold, -0.5 letter spacing | Login title, balance card |
| `displaySmall` | 32 / 40, SemiBold | Subtítulos de Onboarding |
| `headlineLarge` | 28 / 36, SemiBold | Top bars de tela |
| `headlineMedium` | 22 / 28, SemiBold | Section headers grandes |
| `headlineSmall` | 18 / 24, Medium | Top bars de tela secundária |
| `titleLarge` | 16 / 22, Medium | Section headers padrão |
| `titleMedium` | 14 / 20, Medium | Card titles, transaction descriptions |
| `bodyLarge` | 16 / 24, Normal | Body de tela principal |
| `bodyMedium` | 14 / 20, Normal | Body padrão |
| `bodySmall` | 12 / 16, Normal | Body de detalhe |
| `labelLarge` | 14 / 20, SemiBold | Texto de botão CTA |
| `labelMedium` | 12 / 16, Medium | Chips, badges |
| `labelSmall` | 11 / 14, Medium | Captions, sub-rows |

### Tipografia de dinheiro

Estilos paired (`MoneyDisplay` + `MoneyDisplayTail`, `MoneyLarge` + `MoneyLargeTail`, etc.)
do [`Type.kt`](../../app/src/main/java/com/umain/fortress/ui/theme/Type.kt) renderizam
montantes como `**$31,180**.24`:

- A cabeça (`MoneyDisplay`) usa Mono, bold, full-contrast.
- A cauda (`MoneyDisplayTail`) usa Mono, regular weight, color
  `FortressTheme.colors.moneyTail` (apagada).

Apresentação real pelo [`MoneyText`](../../app/src/main/java/com/umain/fortress/ui/components/MoneyText.kt)
composable, que aceita um `MoneySize` (`Display`, `Large`, `Medium`, `Small`) e devolve a
`AnnotatedString` correta com os styles paired.

---

## 4. Shape e radius

Definido em [`Shapes.kt`](../../app/src/main/java/com/umain/fortress/ui/theme/Shapes.kt):

| Token | Radius | Uso |
|---|---|---|
| `extraSmall` | 10 dp | Chips pequenos |
| `small` | 14 dp | Badges |
| `medium` | 18 dp | Text fields |
| `large` | 24 dp | Cards padrão, surfaces de transação |
| `extraLarge` | 32 dp | Cartões virtuais, painéis de feature |
| `FortressPillShape` | 50% | CTAs pill (Send, Receive, Confirm com biometria) |

A linguagem é macia. Não use radius < 10 dp para nada que não seja uma badge minúscula.

---

## 5. Spacing

Sem token formal — escala 4-pt aplicada à mão. Convenções de uso comum:

| Caso | Spacing |
|---|---|
| Padding de borda da tela | 20–24 dp horizontal |
| Gap entre cards / sections | 16 dp (`Arrangement.spacedBy(16.dp)`) |
| Gap dentro de uma seção | 8–12 dp |
| Padding interno de card | 16 dp horizontal × 14 dp vertical (ou 24 dp para painéis grandes) |
| Padding de bottom nav | 12 dp vertical |
| Sticky CTA inferior padding | 16 dp horizontal + 12 dp vertical |

---

## 6. Componentes

Cada componente reusável em [`ui/components/`](../../app/src/main/java/com/umain/fortress/ui/components/).

### Botões

- **`PrimaryButton`** — pill, fill `primaryContainer`, content `onPrimaryContainer`. Usado
  para "Review", "Confirm", "Send money".
- **`InkButton`** — pill, fill `secondary` (ink black), content `onSecondary` (white).
  Usado para "Get started" no Onboarding e empty-state CTAs full-page.
- **`SecondaryButton`** — pill, outlined, content default. Usado para "Edit", "Skip",
  "Reset", "Sign out".

### Inputs

- **`FortressTextField`** — `OutlinedTextField` com border `large`-shape, fill
  `surfaceContainer` quando idle, fill `surface` + border `primary` quando focused. Erros
  herdam tokens M3 padrão.

### Lista e linhas

- **`TransactionRow`** — ícone Send/Receive à esquerda, descrição + counterparty no meio,
  `MoneyText` (size `Small`) + `RiskBadge` à direita.
- **`AccountListRow`** — ícone tipo (Account / Savings / TrendingUp), display name +
  masked number, balance via `MoneyText`.
- **`SectionHeader`** — título + action label opcional (e.g. "View all"). Action label
  estilizada como Text clicável, não `TextButton`.

### Surfaces de feature

- **`BalanceCard`** — flat (sem fill de surface). Label "Total Balance" + `MoneyText` size
  `Display` + ícone Eye toggle de hide/show. Sem chrome em volta.
- **`VirtualCardView`** — virtual card pictórico. Gradient brand-driven; frozen overlay com
  ícone ice; PAN/CVV override via params (revelado depois do step-up).
- **`SecurityChip`** — pill colorido pelo verdict. Successo → `successSurface`, Limited →
  `warningSurface`, Untrusted → `dangerSurface`. Tap não wired ainda.
- **`QuickActionPill`** — pills Send / Receive / Scan na dashboard. Linhagem stack-vertical:
  ícone redondo + label.
- **`CardCarousel`** — `HorizontalPager` para virtual cards. Cada page é `VirtualCardView`;
  dots de pager com bind opcional.
- **`BottomTabBar`** — tab bar floating, fill `cardInk`. Cinco destinations
  (Home/Cards/Scan/Analytics/Profile) com label e estado selecionado.

---

## 7. Iconografia

Toda iconografia roteada através de
[`FortressIcons`](../../app/src/main/java/com/umain/fortress/ui/icons/FortressIcons.kt). É
uma object com properties tipadas em `ImageVector`.

A referência visual é Streamline / Lucide (stroke fino 1.5px, viewport 24×24, optical
sizing). Espelhamos via Material Symbols **outlined**. Para fazer swap pelos SVGs exatos
depois, substitua cada property por um `ImageVector` construído a partir do path data.

Nenhuma tela pode alcançar `Icons.Default.*` diretamente. Adicione a property no
`FortressIcons` e use a property em toda chamada de tela.

---

## 8. Light + dark

Mesma estrutura, paletas diferentes. Toda tela tem que se renderizar legível sob ambas. O
`FortressTheme` composable detecta `isSystemInDarkTheme()` por default e provê `ColorScheme`
+ `FortressColors` correspondentes via `LocalFortressColors`.

Não preview screens só em light — todo preview deveria ter dois entries
(`uiMode = Configuration.UI_MODE_NIGHT_NO` e `UI_MODE_NIGHT_YES`).

---

## 9. Acessibilidade

- Contraste mínimo 4.5:1 para body, 3:1 para large text. Lavender500 em background Cloud50
  bate 4.6:1. Sage500 em Cloud0 (success label em fundo white card) bate só 3.2:1 — então
  texto Sage só é usado em fonte Medium 14 sp ou maior.
- Toque mínimo 48 dp de área tocável para todo controle interativo. CTAs pill usam
  `heightIn(min = 56.dp)`.
- Toda Icon clicável carrega `contentDescription` significativa. Decorativos passam `null`.
- O `MoneyText` é lido bem por screen readers porque a `AnnotatedString` mantém ordem
  natural (sinal → símbolo → cabeça → cauda).
- Switches usam `FortressSwitch` que carrega track / thumb colors do ColorScheme — sem
  hex hardcoded.

---

## 10. Como adicionar uma cor

1. Adicione o raw color em `Color.kt` sob o grupo de família apropriado (Lavender, Cloud,
   Ink, etc.).
2. Se for parte do ColorScheme Material 3, wireja em `Theme.kt` para `FortressLightColors`
   ou `FortressDarkColors`.
3. Se for um token estendido (gradient stop, status surface, etc.), adicione ao
   `FortressColors` em `Theme.kt` e ao `LightExtended` / `DarkExtended` correspondente.
4. Refaça os preview screens para verificar light + dark.

---

## 11. Como adicionar um ícone

1. Adicione a property ao `FortressIcons` apontando para o glyph Material Symbols outlined
   apropriado (ou um `ImageVector` from-path se você está integrando um glyph custom).
2. Toda screen que precisa dele importa via `com.umain.fortress.ui.icons.FortressIcons`.
3. Não importe `androidx.compose.material.icons.*` em screen code. Os únicos arquivos que
   importam disso são `FortressIcons.kt` e (legacy) componentes ainda não migrados.

---

## 12. Componentes que estão fora deste contrato

- **Modais `AlertDialog`** — usar M3 default. Sem styling custom por ora.
- **Pull-to-refresh** — usar o `M3 PullToRefreshBox` quando wireado.
- **`Snackbar`** — M3 default.
- **`NavigationBar`** (top-level) — substituído pelo `BottomTabBar` custom; M3 default não
  é usado.
- **`TopAppBar`** — substituído pelo Row + IconButton custom em toda tela com back. M3
  `TopAppBar` é usado apenas no Scaffold do Onboarding (única exceção, justificada pelo
  padrão de page-gradient).

---

## 13. Roadmap do design system

| Item | Status |
|---|---|
| Empacotar Inter + JetBrains Mono em `res/font` | 🚧 |
| Trocar ícones Material por SVGs Streamline em `ui/icons/svg/` | 🚧 |
| `PreviewParameterProvider` para dual light/dark em todo screen preview | 🚧 |
| Validar contraste com axe DevTools / Accessibility Scanner no CI | 🚧 |
| Documentar estados de motion (`AnimatedContent` transitions, sheet enter/exit) | 🚧 |
| `FortressIconButton` reusável para a regra de toque mínimo de 48 dp | 🚧 |
