#pragma once

#include <openxr/openxr.h>
#include <cmath>

namespace spatial_ui {

inline XrVector3f front(XrQuaternionf q) {
    return {2.f * (q.x*q.z + q.w*q.y), 2.f * (q.y*q.z - q.w*q.x),
            1.f - 2.f * (q.x*q.x + q.y*q.y)};
}

/** Quad +Z faces the viewer. Only yaw may change; local +Y stays world +Y. */
inline XrQuaternionf uprightYaw(XrQuaternionf orientation) {
    const XrVector3f direction = front(orientation);
    const float horizontal = std::hypot(direction.x, direction.z);
    if (!std::isfinite(horizontal)) return {0.f, 0.f, 0.f, 1.f};
    float yaw;
    if (horizontal < 0.0001f) {
        // Looking straight up/down has no projected forward. Retain the right-axis bearing.
        const float rightX = 1.f - 2.f*(orientation.y*orientation.y + orientation.z*orientation.z);
        const float rightZ = 2.f*(orientation.x*orientation.z - orientation.w*orientation.y);
        if (!std::isfinite(rightX) || !std::isfinite(rightZ) || std::hypot(rightX, rightZ) < 0.0001f)
            return {0.f, 0.f, 0.f, 1.f};
        yaw = std::atan2(-rightZ, rightX);
    } else yaw = std::atan2(direction.x, direction.z);
    const float halfYaw = 0.5f * yaw;
    return {0.f, std::sin(halfYaw), 0.f, std::cos(halfYaw)};
}

inline XrQuaternionf faceTowardUpright(XrVector3f panel, XrVector3f eye,
                                      XrQuaternionf fallback) {
    const float x = eye.x - panel.x, z = eye.z - panel.z;
    const float horizontal = std::hypot(x, z);
    if (!std::isfinite(horizontal) || horizontal < 0.01f) return uprightYaw(fallback);
    const float halfYaw = 0.5f * std::atan2(x, z);
    return {0.f, std::sin(halfYaw), 0.f, std::cos(halfYaw)};
}

/** Right Grip orbit: keep the Quad's local +Z aimed at the head in both axes. */
inline XrQuaternionf faceTowardHead(XrVector3f panel, XrVector3f eye,
                                   XrQuaternionf fallback) {
    const float x = eye.x - panel.x, y = eye.y - panel.y, z = eye.z - panel.z;
    const float horizontal = std::hypot(x, z);
    const float radius = std::hypot(horizontal, y);
    if (!std::isfinite(radius) || radius < 0.01f) return fallback;
    const float halfYaw = 0.5f * std::atan2(x, z);
    const float halfPitch = -0.5f * std::atan2(y, horizontal);
    const float sy = std::sin(halfYaw), cy = std::cos(halfYaw);
    const float sp = std::sin(halfPitch), cp = std::cos(halfPitch);
    return {cy*sp, sy*cp, -sy*sp, cy*cp};
}

/** Keep the initial menu at eye height and a horizontal distance in front of the head. */
inline XrPosef anchorUpright(XrPosef head, float distance) {
    XrPosef panel = head;
    panel.orientation = uprightYaw(head.orientation);
    const XrVector3f direction = front(panel.orientation);
    panel.position.x -= direction.x * distance;
    panel.position.z -= direction.z * distance;
    return panel;
}

} // namespace spatial_ui
