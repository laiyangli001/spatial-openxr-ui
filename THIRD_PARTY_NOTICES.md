# Third-party notices and resource provenance

The project's MIT license applies to its self-authored extraction and sample.
It does not replace the licenses of external dependencies.

| Material | Origin | License / handling |
| --- | --- | --- |
| Gradle wrapper scripts/JAR | Gradle 9.3.1, copied from original wrapper | Apache-2.0; see `docs/licenses/Apache-2.0.txt` |
| AndroidX / Jetpack Compose | Maven dependencies pinned in Gradle | Apache-2.0; resolved dependencies retain upstream notices |
| OpenXR Android loader 1.1.53 | Khronos Maven prefab dependency | Apache-2.0 OR MIT upstream; not vendored as source/binary in this repository |
| Optional Filament 1.80.0 | Google Maven artifacts | Apache-2.0, with bundled dependency notices; not vendored here |
| JUnit 4.13.2 | Test-only Maven dependency | EPL-1.0; not shipped in application runtime |
| Kotlin / coroutines runtime | Resolved by the build | Apache-2.0; retain dependency licenses when redistributing binaries |
| `ui/src/main/res/drawable-nodpi/home_{gallery,streaming,settings}.png` | Existing Microsoft Fluent Emoji assets from original project | MIT; full original notice in `docs/FLUENT-EMOJI-LICENSE.txt` |
| Three `menu_*.xml` vector drawables | Original project's app-owned vector assets | MIT under this extraction |
| UI strings, controls, geometry, shaders | Original project's self-authored UI implementation | MIT, owner authorized 2026-11-16 |
| `docs/images/integrated-settings.png` | Original-project UI texture screenshot | Owner-authorized documentation use; shows app-owned UI, no private content observed |

No custom fonts are bundled: text uses the Android/Compose font system. Unicode
emoji characters in the hex menu are text, not a redistributed emoji font.
No environment/home illustrations, GLB models, controller models, Unity assets,
AI models, QNN headers/libraries or private PICO SDK payloads are included.
An optional stereo model renderer requires the consuming app to supply an asset
it has permission to use; the original default model path is only a legacy API
default and does not imply the model is included.

Before distributing a built APK/AAR, preserve notices for the actual resolved
dependencies and native payloads in that binary. This source inventory is not a
substitute for the upstream dependency distribution notices.
