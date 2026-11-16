#pragma once
#include "spatial_panel_placement.h"

namespace spatial_ui {
// State is independent of the graphics API. All formulas come from the GLES/D2S
// interaction path; the XR host owns the authoritative panel pose and scale.
class PanelManipulation {
    int owner=-1;
    XrVector3f initialHead{},initialPanel{},initialDirection{};
    XrQuaternionf plane{};
    float grabX=0,grabY=0,depth=2,held=0;
    bool grabbed=false;
    static XrVector3f rotate(XrQuaternionf q,XrVector3f v) {
        const XrVector3f t{2*(q.y*v.z-q.z*v.y),2*(q.z*v.x-q.x*v.z),2*(q.x*v.y-q.y*v.x)};
        return {v.x+q.w*t.x+q.y*t.z-q.z*t.y,v.y+q.w*t.y+q.z*t.x-q.x*t.z,v.z+q.w*t.z+q.x*t.y-q.y*t.x};
    }
public:
    int hand() const { return owner; }
    void cancel() { owner=-1; held=0; grabbed=false; }
    void begin(int hand,XrPosef& panel,XrVector3f head,XrVector3f origin,XrVector3f direction) {
        owner=hand; initialHead=head; initialPanel=panel.position; initialDirection=direction; held=0;
        panel.orientation=hand==0?faceTowardUpright(panel.position,head,panel.orientation):faceTowardHead(panel.position,head,panel.orientation);
        plane=panel.orientation; depth=distance(head,panel.position);
        grabbed=hand==0 && planarGrabPoint(panel.position,rotate(plane,{1,0,0}),rotate(plane,{0,1,0}),
            rotate(plane,{0,0,1}),origin,direction,grabX,grabY);
    }
    bool update(bool valid,bool grip,XrPosef& panel,float& independentScale,XrVector3f head,
                XrVector3f origin,XrVector3f direction,float stickX,float stickY,float seconds) {
        if(owner<0) return false;
        if(!valid || !grip) { cancel(); return false; }
        const float elapsed=std::clamp(seconds,0.f,.05f);
        const auto normal=rotate(plane,{0,0,1});
        const float prior=owner==0?planarPanelDepth(head,panel.position,normal):depth;
        depth=prior;
        if(std::abs(stickY)>.2f) {
            held+=elapsed;
            depth=gripAdjustedDistanceAccelerated(depth,stickY,elapsed,held);
        } else held=0;
        if(owner==0 && grabbed) panel.position=gripDraggedPlanarRayPosition(head,panel.position,panel.position,
            rotate(plane,{1,0,0}),rotate(plane,{0,1,0}),normal,origin,direction,grabX,grabY,&initialPanel);
        else if(owner==1) panel.position=gripDraggedPosition(head,initialHead,initialPanel,initialDirection,direction,depth);
        if(owner==0 && std::abs(stickY)>.2f) panel.position=movePlanarPanelDepth(panel.position,normal,depth-prior);
        panel.orientation=owner==0?plane:faceTowardHead(panel.position,head,panel.orientation);
        if(std::abs(stickX)>.2f) independentScale=manualPanelScale(independentScale,stickX,elapsed);
        return true;
    }
};

/** Trigger+stick depth control kept independent from Grip dragging. */
class TriggerDistanceManipulation {
    int owner=-1;
    bool menu=false;
    float targetDistance=2.f,held=0.f;
public:
    int hand() const { return owner; }
    bool menuTarget() const { return menu; }
    void cancel() { owner=-1; menu=false; held=0.f; }
    bool update(int candidate,bool trigger,bool overTarget,bool menuTarget,
                XrPosef& panel,XrPosef& menuPose,XrVector3f head,
                float stickY,float seconds) {
        if(!trigger || candidate<0 || (owner>=0 && owner!=candidate)) {
            if(owner>=0 && (!trigger || candidate!=owner)) cancel();
            return false;
        }
        if(owner<0) {
            // A plain Trigger press remains a click. Acquire distance control only
            // after the user deliberately pushes the stick outside the dead zone.
            if(!overTarget || std::abs(stickY)<=.2f) return false;
            owner=candidate;
            menu=menuTarget;
            targetDistance=distance(head,(menu?menuPose:panel).position);
            held=0.f;
        }
        if(std::abs(stickY)<=.2f) {
            held=0.f;
            return false;
        }
        const float elapsed=std::clamp(seconds,0.f,.05f);
        held+=elapsed;
        XrPosef& activePose=menu?menuPose:panel;
        const float current=distance(head,activePose.position);
        targetDistance=gripAdjustedDistanceAccelerated(
            targetDistance,stickY,elapsed,held);
        if(!std::isfinite(current) || std::abs(targetDistance-current)<.00001f)
            return false;
        activePose.position=movePanelDistance(
            head,activePose.position,targetDistance-current);
        activePose.orientation=faceTowardHead(
            activePose.position,head,activePose.orientation);
        return true;
    }
};

}
