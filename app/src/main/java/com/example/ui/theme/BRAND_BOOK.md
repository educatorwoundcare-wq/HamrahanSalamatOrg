# DESIGN SYSTEM & BRAND GUIDELINES
## همراهان سلامت — Hamrahan Salamat
### Official Brand Book & Enterprise Design Token Specification

---

## 1. BRAND PHILOSOPHY & IDENTITY
**همراهان سلامت (Hamrahan Salamat)** is an enterprise healthcare management platform engineered to support human-centric, high-trust digital clinical operations. It bridges clinical workflows, finance, and human resources for home care centers, medical offices, clinics, and healthcare networks.

Our brand identity balances **clinical security** and **human warmth**. Unlike cold, complex administrative tools (ERPs), or futuristic dashboards with visual fatigue, Hamrahan Salamat is a calm, peaceful visual environment designed for managers and healthcare workers who spend hours inside the interface every day.

### Core Pillars
1. **Clinical Trust (اطمینان بالینی):** Absolute visual reliability and cleanliness. No decorative noise.
2. **Human Care (مراقبت انسانی):** Soft angles, calm background contrast, and spacious visual density that reduces cognitive load.
3. **Smart Management (مدیریت هوشمند):** Highly organized, clear hierarchy, and immediate scanning of statuses, metrics, and actions.
4. **Safety & Serenity (آرامش و ایمنی):** A color palette and spacing scheme inspired by modern healing environments.

---

## 2. THE VISUAL BRAND IDENTIFIER (LOGO)
The Hamrahan Salamat logo is flat, geometric, scalable, and highly memorable. It represents protection, collaboration, healthcare, and human warmth.

### Logo Geometry & Concept
The logo features an abstract clinical geometry, pairing three symbolic elements inside a single, continuous, elegant icon:
1. **The Shield (حفاظت):** Outer curves forming a soft, sheltering shield representing protection and structural support.
2. **The Caring Hand (دست حامی):** A stylized inner crescent cradling the focal point, expressing professional human care.
3. **The Leaf & Pulse (رویش و تپش):** A central organic leaf merged with a subtle vertical heartbeat diagonal, representing vitality, recovery, and smart digital health.

### Logo Rules
- **Flat Vector Representation Only:** No gradients, 3D shadows, glow effects, or glassmorphism.
- **Colorways:**
  - **Light Mode Primary:** Deep Teal (`#0F766E`) with Turquoise (`#14B8A6`) accents.
  - **Dark Mode Primary:** Turquoise (`#14B8A6`) on Surface Deep Slate (`#112A2D`).
  - **Monochrome/Solid:** Solid Deep Teal or Solid White on Dark background.
- **Grid & Sizing:**
  - **Micro-Icon (24px):** Rendered as a high-contrast geometric outline of the shield-heartbeat only.
  - **App Launcher / Brand Mark (48px - 96px):** Full geometric representation.
  - **Large Brand Assets (512px):** Pristine vector curves with standard 16% inner padding.

---

## 3. COLOR SYSTEM & DESIGN TOKENS
Our palette uses a calming, medical, light-teal foundation with generous negative space to minimize optical fatigue.

### Light Theme Tokens
| Token Name | Hex Code | Equivalent Role | Usage Guidelines |
| :--- | :--- | :--- | :--- |
| **Primary** | `#0F766E` | Deep Teal | Headers, primary buttons, branding marks |
| **PrimaryContainer** | `#E0F2F1` | Soft Sage | Light backgrounds of primary selections, active tab states |
| **Secondary** | `#14B8A6` | Turquoise | Accent metrics, active chips, minor action triggers |
| **SecondaryContainer** | `#CCFBF1` | Soft Turquoise | Highlight pills, secondary active backgrounds |
| **Accent** | `#7DD3FC` | Medical Blue | Focus indications, diagnostic highlights, telemetry |
| **Background** | `#F5FBFD` | Calm Ice Blue | The standard background. Cold-white minimized |
| **Surface** | `#FFFFFF` | Crisp White | All cards, list records, dialog panels, interactive input sheets |
| **SurfaceVariant** | `#ECFDF5` | Light Mint | Selected tables, subtle row dividers, info banners |
| **Outline** | `#D1D5DB` | Cool Grey | Borders, input field states, separator lines |
| **OutlineVariant** | `#E5E7EB` | Soft Grey | Non-interactive dividers, disabled component borders |

### Dark Theme Tokens (Low-Fatigue Clinical Interface)
| Token Name | Hex Code | Equivalent Role | Usage Guidelines |
| :--- | :--- | :--- | :--- |
| **Primary** | `#14B8A6` | Bright Turquoise | Primary labels, prominent buttons, active statuses |
| **Secondary** | `#0F766E` | Safe Deep Teal | Accent metrics, secondary buttons |
| **Background** | `#0B1E21` | Soft Deep Slate Teal | Dark mode canvas, preventing screen glare |
| **Surface** | `#112A2D` | Deep Slate Teal | Cards, dialog frames, input sheets |
| **SurfaceVariant** | `#163C3E` | Mint-Slate | Highlight states, list rows, headers |
| **Outline** | `#374151` | Dark Cool Grey | Border dividers, outlines |

### Semantic System Feedback
- **Success:** `#22C55E` (Safe Green) — Used for fully cleared payments, submitted workflows, healthy sync.
- **Warning:** `#F59E0B` (Caution Gold) — For pending settlements, incomplete records, unsynced changes.
- **Danger:** `#DC2626` (Alert Red) — Deleted indicators, critical integrity alerts, failed system errors.

---

## 4. TYPOGRAPHY SYSTEM
To support clean local and international reading, we employ **Vazirmatn** for Persian layouts, **Plus Jakarta Sans** for English headers/interfaces, and **JetBrains Mono** for numerical values and system timestamps.

### Typography Scales
| Scale | Size (sp) | Weight | Line Height (sp) | Font Family |
| :--- | :---: | :--- | :---: | :--- |
| **Display Large** | 36 | Bold (700) | 44 | Plus Jakarta Sans / Vazirmatn |
| **Headline Medium** | 24 | Medium (500) | 32 | Plus Jakarta Sans / Vazirmatn |
| **Title Large** | 20 | Semi-Bold (600) | 28 | Plus Jakarta Sans / Vazirmatn |
| **Body Large** | 16 | Normal (400) | 24 | Vazirmatn |
| **Body Medium** | 14 | Normal (400) | 20 | Vazirmatn |
| **Label Large** | 14 | Medium (500) | 18 | Vazirmatn |
| **Label Small** | 11 | Medium (500) | 16 | Vazirmatn |
| **Numeric Large** | 22 | Normal (400) | 28 | JetBrains Mono |
| **Numeric Medium**| 14 | Normal (400) | 18 | JetBrains Mono |

---

## 5. SPACING & GRID LAYOUT
We utilize a robust **8dp grid** spacing structure. No arbitrary margin or padding scales are permitted.

### The 8dp Spacing Scale
- **4dp (XS):** Compact padding inside small status chips or labels.
- **8dp (S):** Spacing between consecutive text lines, or interior paddings of compact lists.
- **12dp (SM):** Padding inside list cells, sub-grouped icon structures.
- **16dp (M):** Standard padding for inner margins, buttons, text fields, and structural offsets.
- **20dp (ML):** Spacing between list entries and cards.
- **24dp (L):** Outer margins for mobile frames, padding of interactive bottom sheets.
- **32dp (XL):** Spacing between different sections of the dashboard.
- **40dp / 48dp / 64dp (XXL):** Extended negative space for hero card gutters and clean onboarding.

### Grid Constraints
- **Compact (Mobile):** Single column, 16dp outer screen margin.
- **Medium (Foldable/Small Tablet):** Dual-pane layout, 24dp screen margin.
- **Expanded (Tablet/DeX):** Side Navigation Rail (72dp wide) + Split-Screen layout (e.g., Patient List on left, details and timeline on right). Max-width container of 1200dp.

---

## 6. CORNER RADIUS SYSTEM
Every component has a rounded geometry to soften the clinical interface and enhance accessibility.

- **Buttons & Chips:** `16dp`
- **Text Inputs & Filter Bars:** `12dp`
- **Cards & Row Containers:** `20dp`
- **Dialogs & Action Modals:** `24dp`
- **Bottom Sheets:** `28dp` (top corners only)
- **FAB (Floating Action Button):** Circular

---

## 7. ELEVATION & SHADOW SYSTEM
We strictly avoid flat harsh borders or dark high-opacity shadows. Standard Material 3 light shadows are utilized:

- **Level 0 (Flat):** Outline variant borders (`#E5E7EB`). Used for standard list rows to reduce visual clutter.
- **Level 1 (Default Card):** `1dp` elevation, soft ambient shadow. Default state for patient and transactional record cards.
- **Level 2 (Hover/Active):** `3dp` elevation, Turquoise/Teal accent border indication.
- **Level 3 (FAB/Dialogs):** `6dp` elevation, distinct shadow cast for modals and key action steps.

---

## 8. REUSABLE COMPONENT SPECIFICATION

### A. Primary & Secondary Buttons
- **Primary Button:** Filled deep teal (`#0F766E`), 16dp corner radius, white text, 48dp minimum touch target.
- **Secondary Button:** Outlined deep teal with a transparent background, 16dp corner radius.
- **Feedback States:** Ripple effect always on. When disabled, uses surface variant with a grey outline and text.

### B. Interactive Cards
- Patient, employee, and transactional cards are bounded by a `20dp` radius.
- Standard background is `#FFFFFF`.
- Soft border of `1dp` outline `#D1D5DB` or level 1 shadow.

### C. Bottom Navigation Bar
- Modern flat floating navigation bar.
- Uses `Background` (`#F5FBFD`) under the navigation rail, and `#FFFFFF` for the bar itself.
- Features exactly 5 main tabs: **Home, Patients, Services, Finance, Reports**.
- **More** options open a modern grid containing: **Staff, Inventory, Synchronization, APK Sharing, Company, Backup, Settings, About**.

### D. System State Chips
- **Success Chip:** Soft light green container (`#DCFCE7`), deep green text (`#15803D`).
- **Warning Chip:** Soft light orange container (`#FEF3C7`), dark brown-gold text (`#B45309`).
- **Danger Chip:** Soft light red container (`#FEE2E2`), deep red text (`#B91C1C`).

---

## 9. TRANSITIONS & ANIMATION SYSTEM
Animations must never draw unnecessary attention. They exist solely to guide the eye and improve navigation.

- **Type of Motion:** Material Motion Standard easing (Fast Out, Slow In).
- **Core Transitions:**
  - **Fade Through:** Screen-to-screen navigation transitions.
  - **Shared Axis:** Carousel movements or step transitions (150ms - 250ms).
  - **Scale Out/In:** Used exclusively for Dialog confirmations.
- **Flashy items strictly forbidden:** No neon glows, looping geometric rotations, or parallax effects.

---

## 10. ACCESSIBILITY (WCAG AAA)
Hamrahan Salamat is designed for high accessibility in dynamic, stressful healthcare settings.

- **RTL Support:** Full native layout mirroring for Persian. Right-to-left layout priority.
- **Contrast Ratios:** Minimum contrast ratio of 4.5:1 for body copy (complies with WCAG AA) and 7:1 for critical system statuses (complies with WCAG AAA).
- **Interactive Sizing:** Touch targets are always a minimum of `48dp` x `48dp`.
- **Keyboard & Reader:** All vector assets include explicit, descriptive `contentDescription` attributes in the Compose layer.
