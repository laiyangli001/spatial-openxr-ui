#pragma once

#include <cstddef>
#include <set>
#include <string>
#include <vector>

namespace spatial_ui {

// Eye values match XrEyeVisibility: both=0, physical left=1, physical right=2.
struct SpatialEyeCandidate { std::string group; int eye = 0; };

/** Only rendered candidates are supplied. A stereo group costs two slots or is omitted entirely. */
inline std::vector<size_t> selectSpatialEyeLayers(const std::vector<SpatialEyeCandidate>& candidates,
                                                 size_t capacity) {
    std::vector<size_t> selected;
    std::set<std::string> visited;
    for (size_t index = 0; index < candidates.size(); ++index) {
        const auto& candidate = candidates[index];
        if (candidate.group.empty()) {
            if (candidate.eye == 0 && selected.size() < capacity) selected.push_back(index);
            continue;
        }
        if (!visited.insert(candidate.group).second) continue;
        size_t left = candidates.size(), right = candidates.size(), count = 0;
        for (size_t other = 0; other < candidates.size(); ++other) {
            if (candidates[other].group != candidate.group) continue;
            ++count;
            if (candidates[other].eye == 1) left = other;
            if (candidates[other].eye == 2) right = other;
        }
        if (count == 2 && left < candidates.size() && right < candidates.size() &&
            capacity - selected.size() >= 2) {
            selected.push_back(left);
            selected.push_back(right);
        }
    }
    return selected;
}

} // namespace spatial_ui
