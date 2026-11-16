# Extraction record

Source: `laiyangli001/moonlight-xr-native-openxr-ui`, commit
`c47e420882e756dc4c8f415707d9b917a16a76ed`.

This is the independently buildable extraction. Runtime limitations are documented in VALIDATION.md.
The owner selected MIT on 2026-11-16. That selection covers the self-authored
extraction, not third-party dependencies or unreviewed product assets.

## Boundaries

- `ui`: Compose components, theme palettes, geometry, ray routing, layer budget,
  asynchronous presentation registry, popup/extension coordination, text selection,
  virtual keyboard and scroll coordination. Existing public parameter defaults remain.
- `sample`: a small Android component gallery, using `ui` as a separate library.
  This is a 2D preview and cannot prove spatial rendering or OpenXR interaction.
- `upstream`: source staging only, with checksums in `extraction-manifest.json`.
  Original staging is excluded from Git; public sources are mapped in source-map.json.
- `MenuUiState`: UI state extracted from the product menu; streaming discovery,
  network credentials, AI inference and model management are not imported.

The code implements app-owned spatial UI behavior using Android Jetpack Compose
and OpenXR. It does not include or require the PICO Spatial UI private runtime.
Names such as `PicoNative` describe the existing palette selection contract and do
not indicate an official PICO library or an automatically available system palette.

## Images

`images/integrated-settings.png` is an existing original-project UI texture capture
from `qa/grouped-settings-pico-main.png`, visually inspected on 2026-11-16.
It shows settings cards, sidebar navigation and help affordances. No credentials,
hostnames, network addresses or personal content were visible. It is not a capture
of this standalone sample or the final headset compositor. Product feature names
in the screenshot do not mean this UI library includes those product features.
Three existing Fluent Emoji images are retained with the Microsoft MIT notice.
No custom fonts, environments, controller models, home illustrations, AI payloads,
QNN headers or prebuilt proprietary binaries have been copied into `ui`.

## Validation so far

Original product baseline passed `:app:testDebugUnitTest :app:assembleDebug` with
the documented custom Filament 1.80.0 Multiview SDK. The official SDK baseline
failed in native Vulkan compilation; no source modification was needed to obtain
the documented custom-SDK pass. Log: original workspace
`build/spatial-export-baseline.log` (not intended for public distribution).

Independent build commands under development:

```powershell
./gradlew.bat :ui:testDebugUnitTest :sample:assembleDebug
```

The command passed from a clean independent checkout.

## Final boundary

The core library includes reusable controls, menu layouts, source production,
geometry, input and layer coordination. Product streaming/AI settings screens are
excluded because they are business integrations. The optional Filament renderer
compiles with `-PwithFilament=true`; assets remain caller-provided. Vulkan adapter
source is preserved under `adapters/vulkan` with its unresolved host dependencies
documented there. No whole-product or Vulkan acceptance is implied.

A clean independent checkout passed the documented default build. See
`VALIDATION.md` for the 125-test result, build outputs and runtime limits.
