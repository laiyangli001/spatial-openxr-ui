# Original Vulkan UI integration sources

These app-owned sources preserve the original Vulkan UI integration, parameters,
surface-request routing, color conversion and input-layer behavior for migration.
They are deliberately outside the standalone Gradle build: they are host adapters,
not an independently linkable Vulkan engine.

They require the original host's Filament 1.80.0 native setup, compatible Vulkan
headers/material packages, JNI surface callbacks, controller state, and additional
host-side headers referenced by their includes. Those dependencies are not
implicitly downloaded or bundled. The new sample uses GLES and does not claim
Vulkan acceptance. Integrating these files requires adapting the host boundary;
copying them into an unrelated OpenXR application is insufficient.

For a self-contained starting point, use `sample/src/main/cpp/xr_sample.cpp` with
`ComposeSurfaceProducer` and `NativeGlesBridge`. The renderer-independent UI layer,
geometry, admission registry and input routers are shared with this integration.
The original product remains the reference for the Vulkan call sequence at the
source commit recorded in `docs/EXTRACTION.md`.
