#pragma once
#include <cmath>
#include <cstddef>

namespace spatial_ui {
struct UiPointerTarget {
    float distance;
    int rank;
    bool main=false,homeForeground=false,sidebar=false,overlay=false;
};

/** Match SpatialUiLayerPlan.hit: ordinary depth, overlay order, and transparent Home exclusions. */
inline int selectUiPointerTarget(const UiPointerTarget* targets,std::size_t count,
                                 bool modalOpen=false,int modalCandidate=-1) {
    // A miss outside the current modal must never fall through to an underlying surface.
    if(modalOpen) {
        if(modalCandidate<0 || static_cast<std::size_t>(modalCandidate)>=count) return -1;
        const float distance=targets[modalCandidate].distance;
        return std::isfinite(distance) && distance>0?modalCandidate:-1;
    }
    bool overlays=false,sidebar=false;
    for(std::size_t i=0;i<count;++i) {
        if(!std::isfinite(targets[i].distance) || targets[i].distance<=0) continue;
        overlays|=targets[i].overlay; sidebar|=targets[i].sidebar;
    }
    int selected=-1;
    for(std::size_t i=0;i<count;++i) {
        const auto& item=targets[i];
        if(!std::isfinite(item.distance) || item.distance<=0 ||
            (overlays && item.main) || (sidebar && item.homeForeground)) continue;
        if(selected<0 || (overlays && item.rank>targets[selected].rank) ||
            ((!overlays || item.rank==targets[selected].rank) && item.distance<targets[selected].distance))
            selected=static_cast<int>(i);
    }
    return selected;
}
}
