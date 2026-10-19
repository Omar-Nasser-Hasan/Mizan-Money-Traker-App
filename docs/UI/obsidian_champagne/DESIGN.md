---
name: Obsidian & Champagne
colors:
  surface: '#292A2D'
  surface-dim: '#121316'
  surface-bright: '#38393c'
  surface-container-lowest: '#0d0e11'
  surface-container-low: '#1a1b1e'
  surface-container: '#1e2022'
  surface-container-high: '#292a2d'
  surface-container-highest: '#343538'
  on-surface: '#e3e2e6'
  on-surface-variant: '#cec6b5'
  inverse-surface: '#e3e2e6'
  inverse-on-surface: '#2f3033'
  outline: '#5B5A54'
  outline-variant: '#4b463a'
  surface-tint: '#dcc582'
  primary: '#f6de98'
  on-primary: '#2D291E'
  primary-container: '#d9c27f'
  on-primary-container: '#5f4f18'
  inverse-primary: '#6e5d24'
  secondary: '#c5c6cb'
  on-secondary: '#2e3034'
  secondary-container: '#47494d'
  on-secondary-container: '#b7b8bd'
  tertiary: '#7ef3e7'
  on-tertiary: '#003733'
  tertiary-container: '#60d6cb'
  on-tertiary-container: '#005b55'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#f9e19b'
  primary-fixed-dim: '#dcc582'
  on-primary-fixed: '#231b00'
  on-primary-fixed-variant: '#55450e'
  secondary-fixed: '#e2e2e7'
  secondary-fixed-dim: '#c5c6cb'
  on-secondary-fixed: '#191c1f'
  on-secondary-fixed-variant: '#45474b'
  tertiary-fixed: '#81f6ea'
  tertiary-fixed-dim: '#63d9ce'
  on-tertiary-fixed: '#00201d'
  on-tertiary-fixed-variant: '#00504b'
  background: '#121316'
  on-background: '#e3e2e6'
  surface-variant: '#343538'
  accent-text: '#E6D29A'
  background-oled: '#000000'
  surface-raised: '#34363A'
  text: '#E9E6DE'
  text-dim: '#BEB9AF'
  income: '#58CFC4'
  expense: '#F4806E'
  transfer: '#B5C7D8'
  investment: '#C0A5E8'
  warning: '#F0BD76'
  light-background: '#F5F2E9'
  light-surface: '#FFFCF5'
  light-surface-raised: '#EBE6DA'
  light-outline: '#938A78'
  light-text: '#2E2B25'
  light-text-dim: '#5D574C'
  light-primary: '#8B6D2B'
  light-on-primary: '#FFF8E7'
  light-accent-text: '#77591D'
  light-income: '#0C7466'
  light-expense: '#B03A14'
  light-transfer: '#4C6378'
  light-investment: '#6F4A9E'
  light-warning: '#875A15'
  account-azure: '#188ABB'
  account-blue: '#0785DB'
  account-indigo: '#717BCB'
  account-violet: '#9A6EC2'
  account-magenta: '#C45AAC'
  account-pink: '#CE588B'
  account-red: '#DA535F'
  account-orange: '#CA642F'
  account-amber: '#AA7818'
  account-olive: '#7A882F'
  account-green: '#3A924F'
  account-teal: '#059093'
typography:
  display-hero:
    fontFamily: Manrope
    fontSize: 44px
    fontWeight: '500'
    lineHeight: 46px
    letterSpacing: -0.02em
  display-hero-mobile:
    fontFamily: Manrope
    fontSize: 36px
    fontWeight: '500'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: '500'
    lineHeight: 32px
  title:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body:
    fontFamily: Manrope
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-small:
    fontFamily: Manrope
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
  label-caps:
    fontFamily: Manrope
    fontSize: 11px
    fontWeight: '600'
    lineHeight: 14px
    letterSpacing: 0.08em
  amount-large:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 32px
  amount-medium:
    fontFamily: Manrope
    fontSize: 17px
    fontWeight: '600'
    lineHeight: 20px
  amount-small:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 17px
  keypad:
    fontFamily: Manrope
    fontSize: 30px
    fontWeight: '500'
    lineHeight: 30px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 0.75rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.25rem
  space-xxl: 1.5rem
  space-section: 2rem
---

## Brand & Style

This design system delivers a serene, private wealth-tracking experience modeled after a jeweler’s matte black velvet display tray. It rejects transient tech trends—avoiding neon accents, glassmorphic sheen, 3D coin renders, playful mascots, and emoji. The aesthetic is calm, authoritative, and jewel-like, engineered for precision personal finance management in high-density environments.

Champagne serves as the solitary brand accent, introduced with disciplined restraint against an obsidian ground. Data and capital amounts are treated as precision instruments: high contrast, unadorned, and legible. The interface communicates silent confidence and technical exactitude, balancing the tactile density of Android handheld ergonomics with the bespoke quietude of a private vault.

## Colors

The palette is anchored by a deep matte obsidian canvas (`#202124`) paired with champagne (`#D9C27F`). Champagne is reserved strictly for focal interactions: primary actions, the FAB trigger, active tab indicators, and balanced budget indicators. At most one champagne element may appear in any single card view.

Semantic functional colors hold invariant definitions across the interface:
- **Income (`#58CFC4`):** Pure teal. Associated strictly with positive capital movements and inflow validations.
- **Expense (`#F4806E`):** Soft coral. Reserved strictly for capital outflow, debt, and depleted limits.
- **Transfer (`#B5C7D8`):** Steel blue-gray. Explicitly neutral; never styled with gain or loss colors.
- **Investment (`#C0A5E8`):** Muted amethyst. Applied exclusively to portfolio assets, gold, and equity holdings.
- **Warning (`#F0BD76`):** Warm amber. Flags burn rate acceleration or approaching category thresholds.

Account indicators leverage the dedicated twelve-step categorical spectrum for asset segregation across donut rings and timeline graphs. Outline strokes (`#5B5A54`) are rendered exclusively as structural hairlines at 40% opacity.

## Typography

The typographic hierarchy relies on Manrope’s modern geometric precision across all structural UI layers.

Numeric integrity is primary:
- All computational figures, account balances, and ledger entries must enforce tabular lining figures (`font-variant-numeric: tabular-nums; font-feature-settings: "tnum" 1;`).
- Amounts align flush right in tables and transaction feeds, formatted with western Arabic numerals, standard currency prefixes, and consistent two-decimal precision (e.g., `EGP 12,580.00`).
- Signage is mandatory: outflows display a minus (`−`), inflows display a plus (`+`), transfers omit signs in favor of directional pairing, and investments append a dedicated classification tag.
- Labels utilize `label-caps`: uppercase transformations with `0.08em` tracking and dimmed luminosity (`#BEB9AF`).

## Layout & Spacing

Designed specifically around the handheld Android baseline (412 × 915 dp):
- **Grid & Margins:** Fluid card columns configured with fixed horizontal boundary padding of 20px (`1.25rem`) and an internal gutter spacing of 12px (`0.75rem`).
- **Vertical Hierarchy:** Section blocks are spaced by 32px (`2rem`) rhythm units, while card modules stack with 12px gaps.
- **Ergonomic Distribution:** Primary interaction zones and transactional action blocks are anchored in the lower 35% of the viewport to enable effortless single-hand thumb execution.
- **System Offsets:** Strict enforcement of Android system UI inset boundaries, providing top offsets for camera cutouts and bottom clearance for standard gesture navigation pills.

## Elevation & Depth

Spatial separation is achieved entirely through tonal layering rather than drop shadows:
- **Base Layer (Canvas):** Obsidian background (`#202124`) or true black (`#000000`) on OLED panels.
- **Level 1 (Cards):** Surface tone (`#292A2D`) bound by a single 1px hairline perimeter stroke (`#5B5A54` at 40% alpha). No drop shadow.
- **Level 2 (Sheets, Modals, Keypad Keys):** Raised surface tone (`#34363A`) bordered by a 1px hairline stroke (`#5B5A54` at 40% alpha). Modals dim the underlying canvas with a uniform 60% neutral black scrim (`rgba(0, 0, 0, 0.60)`).
- **Hero Ambient Glow:** The net-worth overview allows a single soft champagne radial diffusion gradient at 6% to 8% opacity centered behind the primary headline figure. No other surface or component may employ glows, drop shadows, or exterior blurs.
- **Chart Fills:** Monochromatic volume areas below linear trend strokes fade from 24% opacity at the vector boundary down to 0% at the baseline.

## Shapes

The geometric framework applies measured radii corresponding to architectural scales:
- **Containers & Primary Buttons:** 20px (`1.25rem`) corner radius applied to exterior structural cards, full-width action buttons, and modal dialogs.
- **Inner Modules & Tiles:** 12px (`0.75rem`) corner radius for sub-cards, nested ledger groups, and keypad input tiles.
- **Utility Items & Tags:** 8px (`0.5rem`) corner radius for budget track indicators, status markers, and micro-metrics.
- **Pills & Circular Targets:** Full roundedness (`999px`) applied to filtering chips, selection pills, 40px icon containers, and the primary 60px action trigger.
- **Icon Treatments:** Material Symbols Rounded (outlined, weight 300, 24px) centered inside 40px circular viewports tinted at 16% opacity of the associated category token.

## Components

### Buttons
- **Primary Button:** Height 52px, 20px radius. Solid champagne background (`#D9C27F`), dark text (`#2D291E`), weight 600. No border. Single instance per screen.
- **Secondary Button:** Height 52px, 20px radius. Surface-raised background (`#34363A`), text (`#E9E6DE`), 1px hairline border (`#5B5A54` at 40%).
- **Floating Action Button (FAB):** 60px circle anchored bottom-right (16px above the navigation bar). Champagne fill with dark centered plus glyph (`#2D291E`, weight 300).

### Chips & Filters
- **Standard Chip:** Height 36px, full pill radius. Surface background (`#292A2D`), dimmed label (`#BEB9AF`), 12px horizontal padding.
- **Selected Chip:** Solid champagne background (`#D9C27F`), dark label (`#2D291E`), weight 600.

### Lists & Ledger Rows
- **List Row:** Standard 64px height, horizontal margins aligned with screen bounds. 40px circular category glyph on the far left.
- **Content:** Two-line vertical stack containing the title (`#E9E6DE`, weight 600) and metadata (`#BEB9AF`, 13px).
- **Amount Field:** Flush right, tabular figures (`#E9E6DE`). Signs are mandatory: coral with minus (`−`) for expenses, teal with plus (`+`) for income, blue-gray with directional glyph for transfers.

### Form Inputs & Keypad
- **Input Fields:** 56px height, surface-raised background (`#34363A`), 12px radius, 1px outline stroke (`#5B5A54` at 40%). Active states highlight the perimeter in pure champagne (`#D9C27F`).
- **Keypad Matrix:** Four-column grid, 64px key height, 12px corner radius, zero stroke borders. Numerical keys sit on surface (`#292A2D`); operators, decimal points, and deletion keys sit on raised surface (`#34363A`).

### Navigation & Structural Shells
- **Bottom Navigation Bar:** Height 72px, surface background (`#292A2D`), 1px hairline border along top edge. Outlined 24px icons with dimmed text. Selected tab displays a filled icon, a 4px champagne indicator dot above the label, and champagne text.
- **Budget Tracking Bars:** 6px height with full round caps. Baseline track in surface-raised. Active fill in champagne (`#D9C27F`) under normal pace, warning amber (`#F0BD76`) when pace is accelerated, and expense coral (`#F4806E`) once the ceiling is exceeded.
- **Bottom Sheets:** Raised surface (`#34363A`), 28px top corner radii, 20px internal padding, and a centered 4 × 36px drag handle (`#5B5A54` at 60%).
