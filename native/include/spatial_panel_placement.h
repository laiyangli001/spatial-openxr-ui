#pragma once

#include <openxr/openxr.h>
#include <algorithm>
#include <cmath>
#include "upright_ui_pose.h"

namespace spatial_ui {

constexpr float kMinimumPanelDistance = 1.f;
constexpr float kMaximumPanelDistance = 10.f;
// The lateral Grip limit is independent of the panel's forward distance.
constexpr float kMaximumPlanarDragOffset = 10.f;

/** Stick deflection and hold time both accelerate scrolling from the legacy base speed. */
inline float joystickScrollUnits(float axis,float elapsed,float heldSeconds=0.f) {
    if(!std::isfinite(axis) || !std::isfinite(elapsed) || elapsed<=0) return 0;
    const float amount=std::clamp((std::fabs(axis)-.20f)/.80f,0.f,1.f);
    const float heldRamp=1.f+std::clamp(heldSeconds,0.f,1.25f)/1.25f;
    return std::copysign(amount*amount*45.f*heldRamp*std::min(elapsed,.05f),axis);
}

inline float distance(XrVector3f a, XrVector3f b) {
    return std::sqrt((a.x-b.x)*(a.x-b.x) + (a.y-b.y)*(a.y-b.y) + (a.z-b.z)*(a.z-b.z));
}

/** Android XR's panel rule: fixed size through 1.75 m, then +0.5 size/m. */
inline float distanceScale(float meters) {
    const float distanceMeters = std::clamp(meters, kMinimumPanelDistance, kMaximumPanelDistance);
    return 1.f + 0.5f * std::max(0.f, distanceMeters - 1.75f);
}

/** Keep a head-facing child beside the parent's visible right edge after yaw rotation. */
inline float adjacentChildCenterX(float localCenterX, float childWidth,
        float scale, float rightAxisProjection) {
    const float halfWidth = childWidth * scale * 0.5f;
    const float rightEdgeAndGap = (localCenterX - childWidth * 0.5f) * scale;
    return rightEdgeAndGap + halfWidth * std::clamp(rightAxisProjection, 0.f, 1.f);
}

/** Keep the rail docked beside a moved/orbited screen without stripping its pitch. */
inline XrPosef sidebarPoseFacingHead(XrPosef parent, XrPosef rail, XrVector3f head,
        float localCenterX, float childWidth, float scale) {
    const auto right = [](XrQuaternionf q) -> XrVector3f {
        return {1.f-2.f*(q.y*q.y+q.z*q.z), 2.f*(q.x*q.y+q.w*q.z),
                2.f*(q.x*q.z-q.w*q.y)};
    };
    const auto dot = [](XrVector3f a, XrVector3f b) {
        return a.x*b.x + a.y*b.y + a.z*b.z;
    };
    const XrVector3f parentRight = right(parent.orientation);
    rail.orientation = faceTowardHead(rail.position, head, rail.orientation);
    for (int iteration = 0; iteration < 3; ++iteration) {
        const float desired = -adjacentChildCenterX(-localCenterX, childWidth,
            scale, dot(parentRight, right(rail.orientation)));
        const XrVector3f relative{rail.position.x-parent.position.x,
            rail.position.y-parent.position.y, rail.position.z-parent.position.z};
        const float correction = desired - dot(relative, parentRight);
        rail.position.x += correction*parentRight.x;
        rail.position.y += correction*parentRight.y;
        rail.position.z += correction*parentRight.z;
        rail.orientation = faceTowardHead(rail.position, head, rail.orientation);
    }
    return rail;
}

inline XrVector3f clampPanelDistance(XrVector3f eye, XrVector3f panel) {
    const float d = distance(eye, panel);
    if (!std::isfinite(d) || d < 0.001f) return {eye.x, eye.y, eye.z - kMinimumPanelDistance};
    const float limited = std::clamp(d, kMinimumPanelDistance, kMaximumPanelDistance);
    const float factor = limited / d;
    return {eye.x + (panel.x-eye.x)*factor, eye.y + (panel.y-eye.y)*factor,
        eye.z + (panel.z-eye.z)*factor};
}

inline XrVector3f movePanelDistance(XrVector3f eye, XrVector3f panel, float deltaMeters) {
    const float d = distance(eye, panel);
    if (!std::isfinite(d) || d < 0.001f) return clampPanelDistance(eye, panel);
    const float target = std::clamp(d + deltaMeters, kMinimumPanelDistance, kMaximumPanelDistance);
    const float factor = target / d;
    return {eye.x + (panel.x-eye.x)*factor, eye.y + (panel.y-eye.y)*factor,
        eye.z + (panel.z-eye.z)*factor};
}

/** Place a freshly opened free menu on the triggering laser, within the UI depth limits. */
inline XrVector3f menuPositionOnRay(XrVector3f eye, XrVector3f aimOrigin,
        XrVector3f aimDirection, float rayMeters = 1.85f) {
    return clampPanelDistance(eye, {aimOrigin.x + aimDirection.x*rayMeters,
        aimOrigin.y + aimDirection.y*rayMeters,
        aimOrigin.z + aimDirection.z*rayMeters});
}

inline float manualPanelScale(float current, float stickX, float elapsedSeconds) {
    return std::clamp(current * std::exp(stickX * std::clamp(elapsedSeconds, 0.f, 0.05f) * 0.85f),
        0.5f, 2.5f);
}

inline float gripAdjustedDistance(float currentMeters, float stickY, float elapsedSeconds) {
    return std::clamp(currentMeters * std::exp(std::clamp(stickY, -1.f, 1.f) *
        0.9f * std::clamp(elapsedSeconds, 0.f, 0.05f)),
        kMinimumPanelDistance, kMaximumPanelDistance);
}

/** Holding the stick ramps depth speed without a discontinuity at press time. */
inline float gripAdjustedDistanceAccelerated(float currentMeters, float stickY,
        float elapsedSeconds, float heldSeconds) {
    const float ramp = 1.f + 2.f*std::clamp(heldSeconds, 0.f, 2.f)/2.f;
    return std::clamp(currentMeters * std::exp(std::clamp(stickY, -1.f, 1.f) *
        0.9f*ramp*std::clamp(elapsedSeconds, 0.f, 0.05f)),
        kMinimumPanelDistance, kMaximumPanelDistance);
}

/** Preserve the laser hit in the panel's local coordinates, as in D2S. */
inline bool planarGrabPoint(XrVector3f panel, XrVector3f right, XrVector3f up,
        XrVector3f normal, XrVector3f rayOrigin, XrVector3f rayDirection,
        float& grabX, float& grabY) {
    const auto dot = [](XrVector3f a, XrVector3f b) {
        return a.x*b.x + a.y*b.y + a.z*b.z;
    };
    const float denom = dot(rayDirection, normal);
    if (denom >= -0.00001f) return false;
    const XrVector3f toPanel{panel.x-rayOrigin.x, panel.y-rayOrigin.y,
        panel.z-rayOrigin.z};
    const float t = dot(toPanel, normal)/denom;
    if (t <= 0.05f || !std::isfinite(t)) return false;
    const XrVector3f relative{rayOrigin.x + rayDirection.x*t - panel.x,
        rayOrigin.y + rayDirection.y*t - panel.y,
        rayOrigin.z + rayDirection.z*t - panel.z};
    grabX = dot(relative, right);
    grabY = dot(relative, up);
    return true;
}

/** Bound planar travel without reducing it as the panel moves farther away. */
inline XrVector3f clampPlanarPanelDistance(XrVector3f eye, XrVector3f planePoint,
        XrVector3f candidate, XrVector3f normal, XrVector3f dragAnchor) {
    const auto dot = [](XrVector3f a, XrVector3f b) {
        return a.x*b.x + a.y*b.y + a.z*b.z;
    };
    // Project the initial grip position onto the current plane. This keeps the
    // same travel in either direction even when the panel is yawed or moved in depth.
    const XrVector3f fromAnchor{planePoint.x-dragAnchor.x,
        planePoint.y-dragAnchor.y, planePoint.z-dragAnchor.z};
    const float shift = dot(fromAnchor, normal);
    if (!std::isfinite(shift)) return clampPanelDistance(eye, candidate);
    const XrVector3f projectedAnchor{dragAnchor.x + normal.x*shift,
        dragAnchor.y + normal.y*shift, dragAnchor.z + normal.z*shift};
    const XrVector3f lateral{candidate.x-projectedAnchor.x,
        candidate.y-projectedAnchor.y, candidate.z-projectedAnchor.z};
    const float lateralLength = distance(candidate, projectedAnchor);
    if (!std::isfinite(lateralLength)) return clampPanelDistance(eye, planePoint);
    if (lateralLength <= kMaximumPlanarDragOffset) return candidate;
    const float scale = kMaximumPlanarDragOffset/lateralLength;
    return {projectedAnchor.x + lateral.x*scale,
        projectedAnchor.y + lateral.y*scale, projectedAnchor.z + lateral.z*scale};
}

/** Grip+stick changes forward depth; lateral travel must not count as depth. */
inline float planarPanelDepth(XrVector3f eye, XrVector3f panel, XrVector3f normal) {
    return (eye.x-panel.x)*normal.x + (eye.y-panel.y)*normal.y +
        (eye.z-panel.z)*normal.z;
}

inline XrVector3f movePlanarPanelDepth(XrVector3f panel, XrVector3f normal,
        float deltaMeters) {
    return {panel.x-normal.x*deltaMeters, panel.y-normal.y*deltaMeters,
        panel.z-normal.z*deltaMeters};
}

/** Left Grip intersects the panel plane each frame and retains the grab point. */
inline XrVector3f gripDraggedPlanarRayPosition(XrVector3f eye,
        XrVector3f planePoint,
        XrVector3f currentPanel,
        XrVector3f right, XrVector3f up, XrVector3f normal,
        XrVector3f rayOrigin, XrVector3f rayDirection,
        float grabX, float grabY, const XrVector3f* dragAnchor = nullptr) {
    const XrVector3f anchor = dragAnchor ? *dragAnchor : planePoint;
    const auto dot = [](XrVector3f a, XrVector3f b) {
        return a.x*b.x + a.y*b.y + a.z*b.z;
    };
    const float denom = dot(rayDirection, normal);
    if (denom >= -0.00001f)
        return clampPlanarPanelDistance(eye, planePoint, currentPanel, normal, anchor);
    const XrVector3f toPanel{planePoint.x-rayOrigin.x, planePoint.y-rayOrigin.y,
        planePoint.z-rayOrigin.z};
    const float t = dot(toPanel, normal)/denom;
    if (t <= 0.05f || !std::isfinite(t))
        return clampPlanarPanelDistance(eye, planePoint, currentPanel, normal, anchor);
    const XrVector3f center{rayOrigin.x + rayDirection.x*t - right.x*grabX - up.x*grabY,
        rayOrigin.y + rayDirection.y*t - right.y*grabX - up.y*grabY,
        rayOrigin.z + rayDirection.z*t - right.z*grabX - up.z*grabY};
    return clampPlanarPanelDistance(eye, planePoint, center, normal, anchor);
}

/** Orbit the grabbed panel around the head as the laser rotates, like the old UI. */
inline XrVector3f gripDraggedPosition(XrVector3f eye, XrVector3f startEye,
        XrVector3f startPanel,
        XrVector3f startRay, XrVector3f currentRay,
        float desiredMeters) {
    auto normalized = [](XrVector3f v) {
        const float n = std::sqrt(v.x*v.x + v.y*v.y + v.z*v.z);
        return n > 0.00001f ? XrVector3f{v.x/n, v.y/n, v.z/n} : XrVector3f{0.f, 0.f, -1.f};
    };
    const XrVector3f a = normalized(startRay);
    const XrVector3f b = normalized(currentRay);
    const XrVector3f axis{a.y*b.z-a.z*b.y, a.z*b.x-a.x*b.z, a.x*b.y-a.y*b.x};
    const float cosine = std::clamp(a.x*b.x + a.y*b.y + a.z*b.z, -1.f, 1.f);
    XrVector3f radial{startPanel.x-startEye.x, startPanel.y-startEye.y,
        startPanel.z-startEye.z};
    if (cosine < -0.9999f) {
        const XrVector3f perpendicular = std::abs(a.y) < 0.9f
            ? XrVector3f{0.f, 1.f, 0.f} : XrVector3f{1.f, 0.f, 0.f};
        const XrVector3f n = normalized({a.y*perpendicular.z-a.z*perpendicular.y,
            a.z*perpendicular.x-a.x*perpendicular.z,
            a.x*perpendicular.y-a.y*perpendicular.x});
        const float projection = n.x*radial.x + n.y*radial.y + n.z*radial.z;
        radial = {2.f*projection*n.x-radial.x, 2.f*projection*n.y-radial.y,
            2.f*projection*n.z-radial.z};
    } else {
        // Rodrigues rotation using the shortest arc from the initial to current aim.
        const float inv = 1.f / (1.f + cosine);
        const XrVector3f cross{axis.y*radial.z-axis.z*radial.y,
            axis.z*radial.x-axis.x*radial.z, axis.x*radial.y-axis.y*radial.x};
        const float along = axis.x*radial.x + axis.y*radial.y + axis.z*radial.z;
        radial = {radial.x*cosine + cross.x + axis.x*along*inv,
            radial.y*cosine + cross.y + axis.y*along*inv,
            radial.z*cosine + cross.z + axis.z*along*inv};
    }
    const XrVector3f direction = normalized(radial);
    const float meters = std::clamp(desiredMeters, kMinimumPanelDistance,
        kMaximumPanelDistance);
    return {eye.x + direction.x*meters, eye.y + direction.y*meters,
        eye.z + direction.z*meters};
}

} // namespace spatial_ui
