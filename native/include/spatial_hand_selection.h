#pragma once
#include <initializer_list>

namespace spatial_ui {

// preferred: 0 left, 1 right, 2 auto. A hand already gripping or pressing
// keeps priority; otherwise a hand aimed at visible UI beats an idle hand.
inline constexpr int selectPointerHand(const bool tracked[2], const bool grip[2],
                             const bool active[2], const bool pointing[2],
                             int dragging, int preferred) {
    if (dragging >= 0 && dragging < 2 && tracked[dragging]) return dragging;
    const int first = preferred == 0 ? 0 : 1;
    const int second = 1 - first;
    for (const bool* candidates : {grip, active, pointing, tracked}) {
        if (tracked[first] && candidates[first]) return first;
        if (tracked[second] && candidates[second]) return second;
    }
    return -1;
}

} // namespace spatial_ui
