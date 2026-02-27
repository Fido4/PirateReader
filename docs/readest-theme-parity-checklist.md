# Readest Theme Parity Checklist (EPUB Reader)

Purpose:

- Track the exact reader theme presets offered by Readest and ensure PirateReader reaches parity during the EPUB phase.

Status:

- Implemented (Phase 2): exact Readest reader theme inventory captured from upstream `THEME_LIST`,
  with PirateReader IDs matching Readest theme keys and reader background/foreground values aligned.
- Device verification is still pending for Pixel 9 and Boox Note Air 3.

Source of truth used for inventory (captured February 26, 2026):

- Readest source: `apps/readest-app/src/services/constants.ts` (`THEME_LIST`)
  in `readest/readest` main branch.

## Theme Inventory Template

| Readest Theme | Variant / Notes | PirateReader Theme ID | Parity Status | Verified on Pixel 9 | Verified on Boox Note Air 3 |
|---|---|---|---|---|---|
| Light (`default`) | `#FBF6EE` bg / `#000000` fg | `default` | Implemented (bg/fg matched) | No | No |
| Gray (`gray`) | `#D4D4D4` bg / `#2A2A2A` fg | `gray` | Implemented (bg/fg matched) | No | No |
| Sepia (`sepia`) | `#F4ECD8` bg / `#5B4636` fg | `sepia` | Implemented (bg/fg matched) | No | No |
| Grass (`grass`) | `#D4E6D4` bg / `#2A4035` fg | `grass` | Implemented (bg/fg matched) | No | No |
| Cherry (`cherry`) | `#F7D4D4` bg / `#4A2A2A` fg | `cherry` | Implemented (bg/fg matched) | No | No |
| Eye Care (`eye`) | `#CDE4C7` bg / `#2B3A29` fg | `eye` | Implemented (bg/fg matched) | No | No |
| Night (`night`) | dark preset, `#1F1F1F` bg / `#D4D4D4` fg | `night` | Implemented (bg/fg matched) | No | No |
| Gold (`gold`) | dark preset, `#2C2A23` bg / `#E6D7B8` fg | `gold` | Implemented (bg/fg matched) | No | No |
| Berry (`berry`) | dark preset, `#2B1F2A` bg / `#E8D8E6` fg | `berry` | Implemented (bg/fg matched) | No | No |
| B&W (`bw`) | high-contrast light | `bw` | Implemented (bg/fg matched) | No | No |
| White on Black (`wbb`) | high-contrast dark | `wbb` | Implemented (bg/fg matched) | No | No |

Notes:

- PirateReader Phase 2 uses the Readest theme key as the stable internal `themeId` for each preset.
- PirateReader currently derives additional reader CSS tokens (`muted`, `link`, selection, panel/border)
  from the matched Readest foreground/background pair.

## Acceptance Criteria

- Every reader theme visible in Readest is represented in PirateReader
- Matching theme captures core reading surface/chrome contrast intent
- Text/link/selection colors remain readable on both OLED and e-ink devices
- Theme switching applies without reader state loss
- PirateReader theme picker exposes all inventoried presets in both scroll and paginated modes

## Validation Notes

- Check paginated and scroll mode separately
- Check chapter transitions and overlay/chrome colors
- Check low-refresh e-ink behavior (avoid flashy transitions on theme change)
