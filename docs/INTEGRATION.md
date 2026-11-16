# Android / OpenXR integration

This guide describes the extracted GLES producer. The host owns OpenXR.

## Creation and threads

Create an EGL context compatible with the runtime's queried GLES requirements.
On its render thread call `NativeGlesBridge.createExternalTexture()`.
On the Android main thread construct `ComposeSurfaceProducer(activity, texture,
width, height, content = { ... })`. Content is mandatory in the standalone library:
the product-specific default menu was removed. Pixel density remains 240 dpi
(1.5 pixels/dp). Keep the owning Activity lifecycle alive for its Presentation.

`outputSurface` allows a caller-owned Surface; in that mode there is no OES
SurfaceTexture to latch. Do not pass it when following the GLES OES path below.

## Frame ownership

The application creates/destroys OpenXR instance, session, reference/action spaces
and swapchains. This producer does not perform any of those calls. The OpenXR
host uses its normal `xrWaitFrame`, `xrBeginFrame`, rendering, `xrEndFrame` flow.
Use the same predicted display time and reference space for submitted poses and
input location. Respect `shouldRender`; end begun frames even without UI content.

With the GLES context current, call `producer.latchFrameOnGlThread(transform)`.
When a new source buffer exists it updates the SurfaceTexture and its 16-float
transform. Preserve the previously valid texture when no new buffer is available.
Acquire and successfully wait for a runtime swapchain image before writing it.
Call `NativeGlesBridge.copyToAcquiredSwapchain(...)` with that image's GLES name,
the actual texture dimensions and the source transform. Release it before
submitting its `XrCompositionLayerQuad` in the host's one frame submission.
Do not submit an image that has never been initialized. Select a swapchain format
from runtime enumeration; the bridge converts source sRGB to linear premultiplied
RGB. Its format/blend behavior must match the host's attachment and composition
flags; changing gamma blindly causes double conversion.

## Input and surfaces

Use `UiQuad` and `QuadInput.intersect` to turn a located aim ray into source-pixel
coordinates. Build a `QuadPointerRouter(producer)` and call its update method on
the main thread. `SpatialRayRouter` prevents a held press from leaking between
independent surfaces. Call `cancel()` whenever tracking/focus is lost or a surface
is removed. The pose, scale, width, height and eye visibility used for hit testing
must correspond to the actual submitted plane.

For multiple independent panels, implement `SpatialLayerBackend`. `request` must
acknowledge the first presented frame, not merely allocation. Deliver callbacks
on the UI thread. Construct `SpatialLayerRegistry(backend)` and a
`SpatialLayerBudgetHost(maxLayerCount, reservedLayerCount, registry)`. Reserve
all projection/video/system layers before UI admission. A failed request returns
to same-surface content; stereo requests must be admitted/retired as a pair.
Generation checks prevent late callbacks from reviving removed panels.

## Teardown

Stop native rendering and cancel pointer streams. Close the layer registry so
backend surfaces are retired. On the main thread call each producer's `close()`;
it dismisses the Presentation and releases the display, ImageReader, Surface and
SurfaceTexture. Delete OES textures on their owning GL thread/context, then release
framebuffers/programs, OpenXR swapchains, action/reference spaces, session and
instance in the host's dependency order. Never call SurfaceTexture update after
close or destroy its context while it is still being used by rendering.

## Validation boundaries

The standalone sample provides an Android gallery and native GLES OpenXR host.
Their builds are verified, but headset execution is not. No connected
headset was found by PICO CLI on 2026-11-16. Independent XR composition, physical
controller input, final compositor color and stereo eye assignment remain unverified.
The original project's headset history cannot substitute for those checks.
