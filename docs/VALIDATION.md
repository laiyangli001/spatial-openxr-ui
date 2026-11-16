# Validation — 2026-11-16

## Independent source build

Windows / JDK 17 / Android SDK 37 / NDK 30.0.16248370 / CMake 3.22.1.

```powershell
./gradlew.bat :ui:testDebugUnitTest :ui:assembleDebug :sample:assembleDebug -PwithFilament=true
```

Passed: 125 tests, 20 suites, zero failures. Both the UI AAR and the sample APK
(including the native GLES OpenXR host) were generated. The Filament adapter
was included through the Android Kotlin source set and compiled against Maven
artifacts, not the original product's local AAR directory.

Behavioral tests cover geometry and ray intersection, cross-surface press ownership,
popup/extension admission and stale callbacks, modal routing, scrolling, input
stepping, themes, hex hit regions, stereo geometry and eye camera projection.
They do not establish actual OpenXR runtime behavior or final headset pixels.

## Product regression baseline

The unchanged source product passed `:app:testDebugUnitTest :app:assembleDebug`
using its documented custom Filament Multiview SDK. The attempt with the official
native SDK failed in the product's Vulkan host; using the documented custom SDK
resolved the build without changing product code.

## Runtime availability

`pico-cli device list`, with Android platform-tools on PATH, returned no devices.
No native execution, physical controller, final compositor, pause/resume or
standalone stereo-model acceptance is claimed. See `OPENXR-AUDIT.md` for static
review evidence and reproducible device checks. Desktop and other headset targets
remain untested.

## Clean checkout gate

Passed from a fresh independent Git clone with no copied build outputs: 77 tasks
executed, `BUILD SUCCESSFUL` in 33 seconds. The default build uses Maven dependencies
and standard Android tools, with no original-product SDK/AAR paths.

The same clean checkout also passed `-PwithFilament=true` in 34 seconds.
The final public stereo renderer API export was then rebuilt and retested successfully.
