# OpenXR extraction review — 2026-11-16

## Metadata

Scope: independent `sample/src/main/cpp/xr_sample.cpp`, Kotlin `XrActivity`,
Compose producer and GLES bridge. API baseline: explicit `XR_API_VERSION_1_0`.
Target: Android ARM64, GLES 3, primary stereo configuration. Independent runtime
acceptance: **unverified**, no headset connected. No validation-layer logs.
This is a static source review, not an OpenXR conformance certification.

Sources consulted in the original workspace's pinned local references:

- `OpenXR-SDK/include/openxr/openxr.h`: frame, action, swapchain and Quad structures.
- `OpenXR-SDK/include/openxr/openxr_platform.h`: Android instance/loader initialization,
  `XrGraphicsBindingOpenGLESAndroidKHR`, GLES graphics requirements.
- `OpenXR-Docs/specification/sources/chapters/session.adoc`: session READY/STOPPING,
  frame loop and `shouldRender`.
- `OpenXR-Docs/specification/sources/chapters/rendering.adoc`: swapchain acquire,
  wait/release and most recently released image semantics.
- `OpenXR-SDK-Source/specification/registry/xr.xml`: Android create-instance extension
  revision 3 and GLES-enable extension revision 10, both ratified in this snapshot.

The local source snapshots are not a claim about the newest upstream release.

## Compliance findings

- The sample queries graphics requirements before creating its GLES session and
  checks the actual GLES version against the runtime's range.
- Android/platform and GLES extensions are enumerated before enabling. Missing
  requirements produce an explicit error. These are architecture requirements of
  this Android GLES host; it does not offer a desktop/Vulkan fallback.
- Static frame path is wait → begin → optional render → end. It uses the same
  predicted time for aim location and submission. `shouldRender=false` submits zero
  layers. Exception handling attempts to end a begun frame with zero layers.
- Normal render path acquires, waits indefinitely, copies and releases before
  submission. Uninitialized source content produces zero submitted layers.
  Fatal runtime errors tear down the session; recovery from device/runtime loss
  requires relaunch. Failure injection and validation-layer verification are pending.
- Input requires focused session, resumed Activity, active action and valid pose
  flags. Cancellation goes through the router on the UI thread.
- A stale/held press must not be treated as a new press after focus loss. The
  extracted routers have behavioral coverage; physical controller tests are pending.
- Stop/join runs off the Android main thread. Producer cleanup is acknowledged on
  the main thread before EGL teardown; OES textures are deleted on the GL thread.

## Technology adoption

No new optional vendor extension is recommended or enabled in this minimal host.
Simple-controller aim/select is the baseline. Vendor controller profiles and
advanced rendering remain separate adaptation work. No AppSW, multiview,
passthrough, foveation or system theme support is claimed by this sample.

## Performance

UI uses a single compositor Quad, 1200×800 pixels, 1.5×1.0 meters at local z=-2 m.
Compose is latched when a buffer is available; the latest valid source is retained.
The sample favors clarity over optimal redraw policy and copies to an acquired
image every renderable frame. No device timing/thermal measurements are available.

## Compatibility and portability

The host enumerates blend modes and requires an sRGB RGBA swapchain format.
Unsupported requirements fail with a log message under `SpatialOpenXR`.
Desktop XR, Android XR and Quest have no standalone acceptance evidence. A PICO
manifest category is a launch hint, not proof of runtime compatibility. Runtime
controller remapping for the simple-controller profile must be verified on device.

## Unverified release limitations

Native startup, final compositor output, session transitions under OS interruptions,
physical trigger ownership, visual correctness and teardown under runtime loss are
unverified. Build success and JVM tests do not prove these behaviors. Deploy the
APK, launch `.XrActivity`, inspect `adb -s <ID> logcat -s SpatialOpenXR MoonlightSpatialUI`,
and exercise pause/resume, both empty and populated frames, controller tracking
loss, and Activity destruction before asserting runtime acceptance.
