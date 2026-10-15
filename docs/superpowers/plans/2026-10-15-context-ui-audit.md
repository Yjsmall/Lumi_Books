# Context UI alignment

User requested reviewing and aligning the recent Context UI with the current project style. README.en.md advertises three themes; the binding implementation rules are in devdocs/lumi-ui-motion-design-system.md (priority over historical UI documents).

## Scope

- Context card: title-bar settings/close icon buttons, two equal footer actions, source sentence in a secondary surface, shared anchored Continue menu that does not resize the card.
- Context settings: one grouped list with internal divider, shared typography/spacing/radius, accessible switch targets.
- Preserve automatic saving, AI requests and note metadata behavior.
- Verify Lumi/Material/Glass, dark/light, reduced motion, EInk, compact large text and RTL; use shared controls and retain glass surfaces.

## Progress

- [x] Read current norms and inspect existing screenshots/components.
- [x] RED: new menu no-reflow and compact header action tests fail on previous UI.
- [x] Implement initial UI and opt-in content-sized shared menu host.
- [x] Resolve menu idle failure and accessibility review findings.
- [x] Render and inspect screenshots across themes.
- [x] Run project-required tests and Debug/AndroidTest assembly; review final changes.

## Baseline

Before this UI change, full `:app:testDebugUnitTest` ran 1616 tests with one failure and one skip. The failure is `ContinuousScrollReaderTest.comicImagePixelsUpdateWithoutMovingOrResizingThePage`, recorded in app/build/context-ui-baseline.log. No device is connected.

## Final verification

- Full regression: 1630 tests, one failure and one skip. The failure is the same pre-existing comic scroll test recorded above. The `--continue` run successfully built both `:app:assembleDebug` and `:app:assembleDebugAndroidTest`; no installation was attempted because no device is connected.
- Final focused UI/menu/settings verification passed; evidence: `app/build/context-ui-focused.log` and `app/build/test-results/testDebugUnitTest/`.
- Rendered and inspected screenshots in `app/build/reports/context-ui/`, plus `app/build/reports/translation/context-settings-lumi.png`. Covered Lumi, Material 3, Liquid Glass without HDR, light/dark, EInk, reduced motion, RTL, compact large text and API 28 fallback. Hardware optics and physical-device performance remain unverified.
- Regressions verify menu opening does not resize the card, Back closes the menu first, controls remain reachable with large text, switch targets are at least 44dp, and both row activation and a direct track tap toggle once.
- Content-sized menu hosts publish dimensions from final `onGloballyPositioned` coordinates; provisional platform Dialog measurements no longer cause a layout feedback loop. Temporary diagnostics were removed.
- Reduced motion and EInk show loading text without an animated spinner. An independent scoped review found no remaining issues.

## Physical-device follow-up

- Installed the main ARM64 Debug APK in place on V2545A (Android 17) on 2026-10-15 and launched successfully, retaining existing app data.
- Captured the live Context card with its selected word, source sentence, contextual meaning and automatic-save status. The user reported that current behavior works normally.
- Device screenshots are in `app/build/reports/device-context/`. Hardware optics and performance were not quantitatively assessed.
