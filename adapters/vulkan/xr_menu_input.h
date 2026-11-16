#pragma once
#include <openxr/openxr.h>
#include <memory>
#include <array>
#include "scene_frames.h"

namespace moonlight::xr {
struct ControllerRayFrame {
    XrVector3f origin{},direction{};
    float scroll=0,stickX=0,stickY=0;
    bool valid=false,pressed=false,grip=false;
    moonlight::HandFrame model;
    bool motionActive=false,secondaryActive=false;
};
class XrMenuInput {
public:
    XrMenuInput(XrInstance,XrSession,bool picoControllerProfiles);
    ~XrMenuInput();
    bool syncActions();
    void feedback(const std::array<ControllerRayFrame,2>&,const std::array<bool,2>&);
    void pulse(int hand);
    bool takeKeyboardToggle();
    std::array<ControllerRayFrame,2> locate(XrTime,XrSpace,float seconds);
private:
    struct State;
    std::unique_ptr<State> state;
};
}
