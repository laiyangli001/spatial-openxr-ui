#include "xr_menu_input.h"
#include "spatial_panel_placement.h"
#include "runtime_menu_settings.h"
#include <android/log.h>
#include <math/mat4.h>
#include <math/quat.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <stdexcept>
#include <string>
#include <vector>

namespace moonlight::xr {
using namespace filament::math;
namespace {
void require(const char* call,XrResult result) {
    if(result!=XR_SUCCESS) throw std::runtime_error(std::string(call)+": "+std::to_string(result));
}
mat4f matrix(const XrPosef& p) {
    return mat4f::translation(float3{p.position.x,p.position.y,p.position.z})*
        mat4f(quatf(p.orientation.w,p.orientation.x,p.orientation.y,p.orientation.z));
}
}
struct XrMenuInput::State {
    XrInstance instance; XrSession session;
    XrActionSet actions=XR_NULL_HANDLE;
    XrAction aim=XR_NULL_HANDLE,trigger=XR_NULL_HANDLE,select=XR_NULL_HANDLE,stick=XR_NULL_HANDLE,squeeze=XR_NULL_HANDLE;
    XrAction gripPose=XR_NULL_HANDLE,primary=XR_NULL_HANDLE,secondary=XR_NULL_HANDLE,stickPress=XR_NULL_HANDLE,squeezeClick=XR_NULL_HANDLE;
    bool gripLatched[2]{},previousTrigger[2]{};
    bool previousPrimary[2]{},keyboardToggleRequested=false;
    bool actionsSynced=false;
    float scrollHeld[2]{};
    XrAction haptic=XR_NULL_HANDLE;
    XrPath hands[2]{}; XrSpace spaces[2]{},gripSpaces[2]{};
    State(XrInstance i,XrSession s):instance(i),session(s) {}
    ~State() {
        for(auto space:spaces) if(space) xrDestroySpace(space);
        for(auto space:gripSpaces) if(space) xrDestroySpace(space);
        if(actions) xrDestroyActionSet(actions);
    }
    XrPath path(const std::string& name) {
        XrPath result=XR_NULL_PATH; require("input path",xrStringToPath(instance,name.c_str(),&result)); return result;
    }
    XrAction action(const char* name,XrActionType type) {
        XrActionCreateInfo info{XR_TYPE_ACTION_CREATE_INFO}; info.actionType=type;
        std::strncpy(info.actionName,name,sizeof(info.actionName)-1);
        std::strncpy(info.localizedActionName,name,sizeof(info.localizedActionName)-1);
        info.countSubactionPaths=2; info.subactionPaths=hands;
        XrAction result=XR_NULL_HANDLE; require(name,xrCreateAction(actions,&info,&result)); return result;
    }
};
XrMenuInput::XrMenuInput(XrInstance instance,XrSession session,bool picoProfiles):state(std::make_unique<State>(instance,session)) {
    auto& s=*state;
    XrActionSetCreateInfo set{XR_TYPE_ACTION_SET_CREATE_INFO};
    std::strcpy(set.actionSetName,"vulkan_menu"); std::strcpy(set.localizedActionSetName,"Vulkan menu");
    require("menu action set",xrCreateActionSet(instance,&set,&s.actions));
    s.hands[0]=s.path("/user/hand/left"); s.hands[1]=s.path("/user/hand/right");
    s.aim=s.action("menu_aim",XR_ACTION_TYPE_POSE_INPUT);
    s.trigger=s.action("menu_trigger",XR_ACTION_TYPE_FLOAT_INPUT);
    s.select=s.action("menu_select",XR_ACTION_TYPE_BOOLEAN_INPUT);
    s.stick=s.action("menu_scroll",XR_ACTION_TYPE_VECTOR2F_INPUT);
    s.squeeze=s.action("menu_grip",XR_ACTION_TYPE_FLOAT_INPUT);
    s.gripPose=s.action("model_grip_pose",XR_ACTION_TYPE_POSE_INPUT);
    s.primary=s.action("model_primary",XR_ACTION_TYPE_BOOLEAN_INPUT);
    s.secondary=s.action("model_secondary",XR_ACTION_TYPE_BOOLEAN_INPUT);
    s.stickPress=s.action("model_stick_press",XR_ACTION_TYPE_BOOLEAN_INPUT);
    s.squeezeClick=s.action("menu_grip_click",XR_ACTION_TYPE_BOOLEAN_INPUT);
    s.haptic=s.action("ui_haptic",XR_ACTION_TYPE_VIBRATION_OUTPUT);
    auto suggest=[&](const char* profile,bool simple,bool hasSqueezeClick=false) {
        std::vector<XrActionSuggestedBinding> bindings;
        for(const char* hand:{"left","right"}) {
            const std::string prefix=std::string("/user/hand/")+hand+"/input/";
            bindings.push_back({s.haptic,s.path(std::string("/user/hand/")+hand+"/output/haptic")});
            bindings.push_back({s.aim,s.path(prefix+"aim/pose")});
            bindings.push_back({s.gripPose,s.path(prefix+"grip/pose")});
            if(simple) bindings.push_back({s.select,s.path(prefix+"select/click")});
            else {
                bindings.push_back({s.trigger,s.path(prefix+"trigger/value")});
                bindings.push_back({s.stick,s.path(prefix+"thumbstick")});
                bindings.push_back({s.squeeze,s.path(prefix+"squeeze/value")});
                if(hasSqueezeClick) bindings.push_back({s.squeezeClick,s.path(prefix+"squeeze/click")});
                bindings.push_back({s.primary,s.path(prefix+(std::string(hand)=="left"?"x/click":"a/click"))});
                bindings.push_back({s.secondary,s.path(prefix+(std::string(hand)=="left"?"y/click":"b/click"))});
                bindings.push_back({s.stickPress,s.path(prefix+"thumbstick/click")});
            }
        }
        XrInteractionProfileSuggestedBinding info{XR_TYPE_INTERACTION_PROFILE_SUGGESTED_BINDING};
        info.interactionProfile=s.path(profile); info.countSuggestedBindings=bindings.size(); info.suggestedBindings=bindings.data();
        const auto result=xrSuggestInteractionProfileBindings(instance,&info);
        __android_log_print(ANDROID_LOG_INFO,"MoonlightVulkanXR","menu bindings %s result=%d",profile,result);
    };
    if(picoProfiles) {
        suggest("/interaction_profiles/bytedance/pico4s_controller",false,true);
        suggest("/interaction_profiles/bytedance/pico4_controller",false,true);
        suggest("/interaction_profiles/bytedance/pico_neo3_controller",false,true);
    }
    suggest("/interaction_profiles/oculus/touch_controller",false);
    suggest("/interaction_profiles/khr/simple_controller",true);
    for(int hand=0;hand<2;++hand) {
        XrActionSpaceCreateInfo info{XR_TYPE_ACTION_SPACE_CREATE_INFO}; info.action=s.aim;
        info.subactionPath=s.hands[hand]; info.poseInActionSpace.orientation.w=1;
        require("menu aim space",xrCreateActionSpace(session,&info,&s.spaces[hand]));
        info.action=s.gripPose;
        require("model grip space",xrCreateActionSpace(session,&info,&s.gripSpaces[hand]));
    }
    XrSessionActionSetsAttachInfo attach{XR_TYPE_SESSION_ACTION_SETS_ATTACH_INFO}; attach.countActionSets=1; attach.actionSets=&s.actions;
    require("attach menu actions",xrAttachSessionActionSets(session,&attach));
}
XrMenuInput::~XrMenuInput()=default;
bool XrMenuInput::syncActions() {
    auto& s=*state;
    XrActiveActionSet active{s.actions,XR_NULL_PATH};
    XrActionsSyncInfo sync{XR_TYPE_ACTIONS_SYNC_INFO};
    sync.countActiveActionSets=1; sync.activeActionSets=&active;
    s.actionsSynced=xrSyncActions(s.session,&sync)==XR_SUCCESS;
    return s.actionsSynced;
}
std::array<ControllerRayFrame,2> XrMenuInput::locate(XrTime time,XrSpace world,float seconds) {
    auto& s=*state;
    if(!s.actionsSynced) return {};
    const float elapsed=std::clamp(seconds,0.f,.05f);
    std::array<ControllerRayFrame,2> frames{};
    for(int hand=0;hand<2;++hand) {
        XrActionStateGetInfo get{XR_TYPE_ACTION_STATE_GET_INFO}; get.subactionPath=s.hands[hand]; get.action=s.aim;
        XrActionStatePose poseState{XR_TYPE_ACTION_STATE_POSE};
        if(xrGetActionStatePose(s.session,&get,&poseState)!=XR_SUCCESS || !poseState.isActive) {
            s.scrollHeld[hand]=0; continue;
        }
        XrSpaceLocation location{XR_TYPE_SPACE_LOCATION};
        if(xrLocateSpace(s.spaces[hand],world,time,&location)!=XR_SUCCESS) {
            s.scrollHeld[hand]=0; continue;
        }
        const auto flags=XR_SPACE_LOCATION_POSITION_VALID_BIT|XR_SPACE_LOCATION_ORIENTATION_VALID_BIT;
        if((location.locationFlags&flags)!=flags) {
            s.scrollHeld[hand]=0; continue;
        }
        auto aim=matrix(location.pose);
        const auto direction=aim*float4{0,0,-1,0};
        auto& frame=frames[hand]; frame.origin=location.pose.position;
        frame.direction={direction.x,direction.y,direction.z}; frame.valid=true;
        frame.model.aim=location.pose; frame.model.aimValid=true; frame.model.rayLength=.4f;
        get.action=s.gripPose; XrActionStatePose gripState{XR_TYPE_ACTION_STATE_POSE};
        XrSpaceVelocity velocity{XR_TYPE_SPACE_VELOCITY}; XrSpaceLocation gripLocation{XR_TYPE_SPACE_LOCATION}; gripLocation.next=&velocity;
        if(xrGetActionStatePose(s.session,&get,&gripState)==XR_SUCCESS && gripState.isActive &&
           xrLocateSpace(s.gripSpaces[hand],world,time,&gripLocation)==XR_SUCCESS && (gripLocation.locationFlags&flags)==flags) {
            frame.model.grip=gripLocation.pose; frame.model.gripValid=true;
            const auto& v=velocity.linearVelocity; const auto& a=velocity.angularVelocity;
            frame.motionActive=((velocity.velocityFlags&XR_SPACE_VELOCITY_LINEAR_VALID_BIT) && v.x*v.x+v.y*v.y+v.z*v.z>.0064f) ||
                ((velocity.velocityFlags&XR_SPACE_VELOCITY_ANGULAR_VALID_BIT) && a.x*a.x+a.y*a.y+a.z*a.z>.04f);
        }
        get.action=s.trigger; XrActionStateFloat trigger{XR_TYPE_ACTION_STATE_FLOAT};
        xrGetActionStateFloat(s.session,&get,&trigger);
        get.action=s.select; XrActionStateBoolean select{XR_TYPE_ACTION_STATE_BOOLEAN}; xrGetActionStateBoolean(s.session,&get,&select);
        frame.pressed=(trigger.isActive && trigger.currentState>.5f) || (select.isActive && select.currentState);
        get.action=s.stick; XrActionStateVector2f stick{XR_TYPE_ACTION_STATE_VECTOR2F}; xrGetActionStateVector2f(s.session,&get,&stick);
        if(stick.isActive) { frame.stickX=stick.currentState.x; frame.stickY=stick.currentState.y; }
        get.action=s.squeeze; XrActionStateFloat squeeze{XR_TYPE_ACTION_STATE_FLOAT};
        xrGetActionStateFloat(s.session,&get,&squeeze);
        frame.grip=squeeze.isActive && squeeze.currentState>=(s.gripLatched[hand]?.15f:.25f);
        s.gripLatched[hand]=frame.grip;
        frame.model.trigger=trigger.isActive?trigger.currentState:0;
        frame.model.squeeze=squeeze.isActive?squeeze.currentState:0;
        frame.model.stickX=frame.stickX; frame.model.stickY=frame.stickY;
        auto button=[&](XrAction action) { get.action=action; XrActionStateBoolean value{XR_TYPE_ACTION_STATE_BOOLEAN};
            return xrGetActionStateBoolean(s.session,&get,&value)==XR_SUCCESS && value.isActive && value.currentState?1.f:0.f; };
        frame.model.primary=button(s.primary); frame.model.secondary=button(s.secondary); frame.model.stickPress=button(s.stickPress);
        if(hand==0) {
            const bool x=frame.model.primary>0;
            if(x && !s.previousPrimary[0]) s.keyboardToggleRequested=true;
            s.previousPrimary[0]=x;
        }
        get.action=s.secondary; XrActionStateBoolean secondary{XR_TYPE_ACTION_STATE_BOOLEAN};
        frame.secondaryActive=xrGetActionStateBoolean(s.session,&get,&secondary)==XR_SUCCESS && secondary.isActive;
        if(button(s.squeezeClick)>0) { frame.grip=true; frame.model.squeeze=1; s.gripLatched[hand]=true; }
        if(!frame.grip && !frame.pressed && std::abs(frame.stickY)>.2f) {
            s.scrollHeld[hand]+=elapsed;
            frame.scroll=spatial_ui::joystickScrollUnits(
                frame.stickY,elapsed,s.scrollHeld[hand]);
        } else s.scrollHeld[hand]=0;
    }
    return frames;
}
void XrMenuInput::feedback(const std::array<ControllerRayFrame,2>& rays,const std::array<bool,2>& hits) {
    auto& s=*state;
    for(int hand=0;hand<2;++hand) {
        if(rays[hand].valid && rays[hand].pressed && !s.previousTrigger[hand] && hits[hand] && pointerHaptics.load()) {
            XrHapticVibration pulse{XR_TYPE_HAPTIC_VIBRATION};pulse.duration=XR_MIN_HAPTIC_DURATION;
            pulse.frequency=XR_FREQUENCY_UNSPECIFIED;pulse.amplitude=.35f*hapticAmplitude.load();
            XrHapticActionInfo info{XR_TYPE_HAPTIC_ACTION_INFO};info.action=s.haptic;info.subactionPath=s.hands[hand];
            const auto result=xrApplyHapticFeedback(s.session,&info,reinterpret_cast<const XrHapticBaseHeader*>(&pulse));
            if(XR_FAILED(result)) __android_log_print(ANDROID_LOG_WARN,"MoonlightVulkanXR","UI haptic hand=%d result=%d",hand,result);
        }
        s.previousTrigger[hand]=rays[hand].valid && rays[hand].pressed;
    }
}

bool XrMenuInput::takeKeyboardToggle() {
    auto& s=*state;
    const bool value=s.keyboardToggleRequested;
    s.keyboardToggleRequested=false;
    return value;
}

void XrMenuInput::pulse(int hand) {
    auto& s=*state;
    if(hand<0 || hand>1 || !pointerHaptics.load() || s.haptic==XR_NULL_HANDLE) return;
    XrHapticVibration vibration{XR_TYPE_HAPTIC_VIBRATION};
    vibration.duration=XR_MIN_HAPTIC_DURATION;
    vibration.frequency=XR_FREQUENCY_UNSPECIFIED;
    vibration.amplitude=.25f*hapticAmplitude.load();
    XrHapticActionInfo info{XR_TYPE_HAPTIC_ACTION_INFO};
    info.action=s.haptic; info.subactionPath=s.hands[hand];
    const auto result=xrApplyHapticFeedback(s.session,&info,
        reinterpret_cast<const XrHapticBaseHeader*>(&vibration));
    if(XR_FAILED(result)) __android_log_print(ANDROID_LOG_WARN,
        "MoonlightVulkanXR","hover haptic hand=%d result=%d",hand,result);
}

}
