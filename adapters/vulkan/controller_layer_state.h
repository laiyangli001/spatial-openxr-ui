#pragma once
#include "scene_frames.h"
#include "controller_overlay_pose.h"
#include <string>

namespace moonlight::xr {
// One predicted-frame snapshot. Panel placement never changes the model's Grip pose.
struct ControllerLayerState {
    HandFrame hands[2];
    XrVector3f centers[2]{},extents[2]{},bAnchor{};
    bool boundsValid[2]{},bAnchorValid=false,hintVisible=false,focused=false;
};
inline bool controllerLayerParent(const std::string& id) {
    return id=="controller-left" || id=="controller-right" ||
        id=="controller-b-hint" || id=="controller-b-leader";
}
class ControllerLayerPlacement {
    ControllerLayerState frame;
    XrPosef head{},card{};
    XrVector3f anchor{};
    bool hint=false;
    spatial_ui::ControllerHintFacing facing;
public:
    void update(const ControllerLayerState& current,const XrPosef& headPose) {
        frame=current; head=headPose; hint=false;
        if(!frame.focused) { facing={}; return; }
        if(!frame.hintVisible || !frame.hands[1].gripValid || !frame.bAnchorValid ||
           !spatial_ui::controllerHintNearHead(frame.hands[1].grip.position,head.position)) return;
        const auto offset=spatial_ui::overlayRotate(frame.hands[1].grip.orientation,frame.bAnchor);
        const auto origin=frame.hands[1].grip.position;
        anchor={origin.x+offset.x,origin.y+offset.y,origin.z+offset.z};
        card=facing.pose(anchor,head.position,.18333333f,head.orientation); hint=true;
    }
    bool pose(const std::string& parent,float height,XrPosef& result,float& leaderWidth) const {
        leaderWidth=0;
        if(!frame.focused) return false;
        if(parent=="controller-left" || parent=="controller-right") {
            const int hand=parent=="controller-left"?0:1;
            const auto& input=frame.hands[hand];
            return input.gripValid && input.aimValid && frame.boundsValid[hand] &&
                spatial_ui::controllerReadableOverlayPose(input.grip,input.aim,height,height+.055f,head.position,result) &&
                spatial_ui::controllerPanelOnTop(input.grip,head.position,frame.centers[hand],frame.extents[hand],height,result);
        }
        if(!hint) return false;
        if(parent=="controller-b-hint") { result=card; return true; }
        if(parent!="controller-b-leader") return false;
        const auto edge=spatial_ui::overlayRotate(card.orientation,{-.18333333f*.28f,-.005f,0});
        const XrVector3f end{card.position.x+edge.x,card.position.y+edge.y,card.position.z+edge.z};
        return spatial_ui::controllerHintLeaderPose(anchor,end,head.position,result,leaderWidth);
    }
};
}
