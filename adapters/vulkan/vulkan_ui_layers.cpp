#include "vulkan_ui_layers.h"
#include "vulkan_surface_requests.h"
#include "upright_ui_pose.h"
#include "panel_manipulation.h"
#include "panel_attachment.h"
#include "runtime_menu_settings.h"
#include "pointer_hit_stabilizer.h"
#include "ui_pointer_target.h"
#include <math/mat4.h>
#include <math/quat.h>
#include <algorithm>
#include <cmath>
#include <map>
#include <set>
#include <sstream>
#include <stdexcept>

namespace moonlight::xr {
using namespace filament::math;
namespace {
XrPosef childPose(const XrPosef& parent,const XrPosef& child) {
    const quatf p(parent.orientation.w,parent.orientation.x,parent.orientation.y,parent.orientation.z);
    const quatf q(child.orientation.w,child.orientation.x,child.orientation.y,child.orientation.z);
    const auto offset=mat4f(p)*float4{child.position.x,child.position.y,child.position.z,0};
    const auto rotation=p*q;
    return {{rotation.x,rotation.y,rotation.z,rotation.w},
        {parent.position.x+offset.x,parent.position.y+offset.y,parent.position.z+offset.z}};
}
int rank(const std::string& id,const VulkanSurfaceRequest& request) {
    if(controllerLayerParent(request.parent)) return 8;
    if(id=="main") return 0;
    if(id=="home-zz-foreground") return 1;
    if(id=="category-menu") return 3;
    if(id=="extension") return 4;
    if(id=="panel-adjustment") return 7;
    if(id=="parameter-help") return 5;
    if(request.modal || id=="popup") return 6;
    return 2;
}
float rayDistance(const ControllerRayFrame& ray,const XrCompositionLayerQuad& quad) {
    const auto& p=quad.pose;
    const quatf inverse(p.orientation.w,-p.orientation.x,-p.orientation.y,-p.orientation.z);
    const auto origin=mat4f(inverse)*float4{ray.origin.x-p.position.x,ray.origin.y-p.position.y,ray.origin.z-p.position.z,0};
    const auto direction=mat4f(inverse)*float4{ray.direction.x,ray.direction.y,ray.direction.z,0};
    if(direction.z>=-1e-5f) return -1;
    const float t=-origin.z/direction.z;
    return t>0 && std::abs(origin.x+direction.x*t)<=quad.size.width*.5f &&
        std::abs(origin.y+direction.y*t)<=quad.size.height*.5f?t:-1;
}
}
struct VulkanUiLayers::State {
    struct Node {
        VulkanSurfaceRequest request;
        std::unique_ptr<VulkanComposeSurface> surface;
        bool announced=false;
    };
    JNIEnv* env; jobject activity; VkDevice device; VkPhysicalDevice physical; VkQueue queue; uint32_t family;
    XrSession session; uint32_t maximum,projectionLayers=1; bool capture;
    uint64_t epoch=0;
    Reporter report;
    std::map<std::string,Node> nodes;
    std::vector<XrCompositionLayerQuad> submitted;
    std::vector<std::string> ids;
    std::vector<int64_t> generations;
    std::vector<float> geometry;
    std::array<float,25> rays{};
    XrPosef mainPose{},headPose{},menuPose{};
    bool menuAnchored=false,mainAnchored=false,draggingMenu=false;
    float mainManualScale=1,menuManualScale=1,mainScale=1,menuScale=1;
    spatial_ui::PanelManipulation manipulation;
    spatial_ui::TriggerDistanceManipulation triggerDistance;
    spatial_ui::PointerHitStabilizer stabilizers[2];
    spatial_ui::PanelFollow mainFollow,menuFollow;
    float mainDistanceScale=1,menuDistanceScale=1,videoAspect=0;
    std::string adjustedTarget="main";
    ControllerLayerPlacement controllers;
    std::string lastSubmitted;
    State(JNIEnv* e,jobject a,VkDevice d,VkPhysicalDevice p,VkQueue q,uint32_t f,XrSession s,
          uint32_t max,bool c,Reporter reporter)
        :env(e),activity(a),device(d),physical(p),queue(q),family(f),session(s),maximum(max),capture(c),report(std::move(reporter)) {}
    ~State() {
        endVulkanUiSession(epoch);
        // Stop child producers before the root tears down the registry.
        for(auto it=nodes.begin();it!=nodes.end();) {
            if(it->first=="main") { ++it; continue; }
            it=nodes.erase(it);
        }
        nodes.clear();
    }
    void failed(const VulkanSurfaceRequest& request) {
        jclass cls=env->GetObjectClass(activity); jstring id=env->NewStringUTF(request.config.id.c_str());
        env->CallVoidMethod(activity,env->GetMethodID(cls,"onNativeVulkanSurfaceFailed","(Ljava/lang/String;J)V"),
            id,static_cast<jlong>(request.config.generation));
        env->DeleteLocalRef(id); env->DeleteLocalRef(cls);
    }
    void apply() {
        for(auto& request:takeVulkanSurfaceRequests(epoch)) {
            auto old=nodes.find(request.config.id);
            if(request.remove) {
                if(old!=nodes.end() && old->second.request.config.generation==request.config.generation) {
                    report("UI release id="+request.config.id+" generation="+std::to_string(request.config.generation));
                    nodes.erase(old);
                    if(request.config.id=="category-menu") {
                        // A temporary diagnostic surface can retire the producer
                        // without closing navigation. Keep pose and manual scale;
                        // a genuine new opening supplies a fresh ray below.
                        if(draggingMenu) manipulation.cancel();
                    }
                }
                continue;
            }
            if(old!=nodes.end()) nodes.erase(old);
            if(nodes.size()>=maximum-projectionLayers) { failed(request); continue; }
            if(request.config.id=="category-menu") {
                const auto ray=takeVulkanMenuAnchor(epoch);
                if(ray || !menuAnchored) {
                    XrPosef offset{{0,0,0,1},{-1.1f,0,.15f}}; menuPose=childPose(mainPose,offset);
                    if(ray) {
                        menuPose.position={ray->origin.x+ray->direction.x*2.f,ray->origin.y+ray->direction.y*2.f,ray->origin.z+ray->direction.z*2.f};
                        menuPose.orientation=spatial_ui::faceTowardUpright(menuPose.position,headPose.position,mainPose.orientation);
                    }
                    menuAnchored=true;
                    menuManualScale=1;
                    menuDistanceScale=spatial_ui::distanceScale(spatial_ui::distance(headPose.position,menuPose.position))/spatial_ui::distanceScale(2.f);
                    menuFollow.reset();
                }
            }
            try {
                Node node; node.request=request;
                node.surface=std::make_unique<VulkanComposeSurface>(env,activity,device,physical,queue,family,session,capture,request.config);
                nodes.emplace(request.config.id,std::move(node));
                report("UI allocate id="+request.config.id+" generation="+std::to_string(request.config.generation)+
                    " size="+std::to_string(request.config.width)+"x"+std::to_string(request.config.height));
            } catch(const std::exception& error) { report("UI allocation failed id="+request.config.id+" "+error.what()); failed(request); }
        }
    }
    float scale(const std::string& id,std::set<std::string>& visiting) const {
        if(controllerLayerParent(id)) return 1;
        if(id=="main") return mainScale;
        if(id=="free-menu") return menuScale;
        const auto it=nodes.find(id);
        if(it==nodes.end() || !visiting.insert(id).second) return 1;
        return scale(it->second.request.parent,visiting);
    }
    float scale(const std::string& id) const { std::set<std::string> visiting; return scale(id,visiting); }
    int hit(const ControllerRayFrame& ray) const {
        if(!ray.valid) return -1;
        std::vector<spatial_ui::UiPointerTarget> targets;
        for(size_t i=0;i<submitted.size();++i) {
            auto it=nodes.find(ids[i]);
            if(it==nodes.end()) { targets.push_back({-1,0});continue; }
            const auto& request=it->second.request;
            const bool overlay=request.modal || ids[i]=="category-menu" || ids[i]=="extension" || ids[i]=="popup" || ids[i]=="parameter-help";
            targets.push_back({request.focusable?rayDistance(ray,submitted[i]):-1,rank(ids[i],request),
                ids[i]=="main",ids[i]=="home-zz-foreground",ids[i]=="side-navigation",overlay});
        }
        return spatial_ui::selectUiPointerTarget(targets.data(),targets.size());
    }
    void manipulate(const std::array<ControllerRayFrame,2>& input,float seconds) {
        bool modal=false;
        for(const auto& [id,node]:nodes) modal|=node.request.modal;
        if(modal) { manipulation.cancel(); triggerDistance.cancel(); return; }
        if(manipulation.hand()<0) for(int hand=0;hand<2;++hand) {
            const auto& ray=input[hand];
            if(!ray.valid || !ray.grip) continue;
            std::vector<spatial_ui::UiPointerTarget> targets;
            for(size_t i=0;i<submitted.size();++i) {
                const auto found=nodes.find(ids[i]);
                if(found==nodes.end()) { targets.push_back({-1,0}); continue; }
                const auto& request=found->second.request;
                const bool overlay=request.modal || ids[i]=="category-menu" || ids[i]=="extension" || ids[i]=="popup" || ids[i]=="parameter-help";
                targets.push_back({request.focusable?rayDistance(ray,submitted[i]):-1,rank(ids[i],request),
                    ids[i]=="main",ids[i]=="home-zz-foreground",ids[i]=="side-navigation",overlay});
            }
            const int hit=spatial_ui::selectUiPointerTarget(targets.data(),targets.size());
            if(hit<0) continue;
            // Any descendant of the free menu manipulates the same root anchor.
            draggingMenu=menuAnchored && scaleParent(ids[hit])=="free-menu";
            adjustedTarget=draggingMenu?(ids[hit]=="extension"?"extension":"category-menu"):"main";
            manipulation.begin(hand,draggingMenu?menuPose:mainPose,headPose.position,ray.origin,ray.direction);
            report("Vulkan Grip begin hand="+std::to_string(hand)+" root="+(draggingMenu?"free-menu":"main"));
            break;
        }
        const int hand=manipulation.hand();
        if(hand<0) return;
        const auto& ray=input[hand];
        const float priorDistance=spatial_ui::distance(headPose.position,(draggingMenu?menuPose:mainPose).position);
        const bool moved=manipulation.update(ray.valid,ray.grip,draggingMenu?menuPose:mainPose,
            draggingMenu?menuManualScale:mainManualScale,headPose.position,ray.origin,ray.direction,ray.stickX,ray.stickY,seconds);
        if(moved) {
            if(std::abs(ray.stickY)>.2f) {
                auto& factor=draggingMenu?menuDistanceScale:mainDistanceScale;
                factor=spatial_ui::adjustedPanelSize(factor,priorDistance,spatial_ui::distance(headPose.position,(draggingMenu?menuPose:mainPose).position));
            }
            // Cancel UI capture while a panel is being manipulated. Resizing must not
            // also drag a slider or synthesize a Trigger click on its new hit plane.
            for(int h=0;h<2;++h) rays[h*9+8]=0;
        }
        if(manipulation.hand()>=0) { triggerDistance.cancel(); return; }
        bool distanceMoved=false;
        for(int hand=0;hand<2;++hand) {
            const auto& ray=input[hand];
            const int hitIndex=ray.valid?hit(ray):-1;
            const bool menuTarget=hitIndex>=0 && menuAnchored &&
                scaleParent(ids[hitIndex])=="free-menu";
            const float before=spatial_ui::distance(headPose.position,
                (triggerDistance.menuTarget()?menuPose:mainPose).position);
            const int previousHand=triggerDistance.hand();
            if(!triggerDistance.update(hand,ray.pressed,hitIndex>=0,menuTarget,
                    mainPose,menuPose,headPose.position,ray.stickY,seconds)) continue;
            if(previousHand<0) report("Vulkan Trigger distance begin hand="+std::to_string(hand)+
                " root="+(triggerDistance.menuTarget()?"free-menu":"main"));
            auto& factor=triggerDistance.menuTarget()?menuDistanceScale:mainDistanceScale;
            factor=spatial_ui::adjustedPanelSize(factor,before,
                spatial_ui::distance(headPose.position,
                    (triggerDistance.menuTarget()?menuPose:mainPose).position));
            distanceMoved=true;
        }
        if(distanceMoved) for(int h=0;h<2;++h) rays[h*9+8]=0;
    }
    std::string scaleParent(std::string id) const {
        std::set<std::string> seen;
        while(id!="main" && id!="free-menu") {
            const auto found=nodes.find(id);
            if(found==nodes.end() || !seen.insert(id).second) return "main";
            id=found->second.request.parent;
        }
        return id;
    }
    bool pose(const std::string& id,XrPosef& result,std::set<std::string>& visiting) {
        if(id=="main") { result=mainPose; return true; }
        if(id=="free-menu") { result=menuPose; return menuAnchored; }
        auto it=nodes.find(id); if(it==nodes.end() || !it->second.surface->ready() || !visiting.insert(id).second) return false;
        XrPosef parent{};
        const auto& request=it->second.request;
        float leaderWidth=0;
        if(controllerLayerParent(request.parent)) {
            if(!controllers.pose(request.parent,request.config.heightMeters,parent,leaderWidth)) return false;
        } else if(!pose(request.parent,parent,visiting)) return false;
        XrPosef local=request.local; const float parentScale=scale(request.parent);
        local.position.x*=parentScale; local.position.y*=parentScale; local.position.z*=parentScale;
        result=childPose(parent,local);
        if(request.faceHead && !controllerLayerParent(request.parent)) result.orientation=spatial_ui::faceTowardUpright(result.position,headPose.position,result.orientation);
        if(id=="virtual-keyboard") result.orientation=spatial_ui::faceTowardHead(result.position,headPose.position,result.orientation);
        if(id=="side-navigation") result=spatial_ui::sidebarPoseFacingHead(parent,result,headPose.position,
            request.local.position.x,request.config.widthMeters,parentScale);
        // Keep the compact FPS/guide HUD panels 20 cm farther from the face
        // than their controller attachment would place them.
        if(id=="controller-performance" || id=="controller-guide") {
            const float dx=result.position.x-headPose.position.x;
            const float dy=result.position.y-headPose.position.y;
            const float dz=result.position.z-headPose.position.z;
            const float len=std::sqrt(dx*dx+dy*dy+dz*dz);
            if(len>1e-4f) {
                const float k=.05f/len;
                result.position.x+=dx*k;
                result.position.y+=dy*k;
                result.position.z+=dz*k;
            }
        }
        visiting.erase(id); return true;
    }
};
VulkanUiLayers::VulkanUiLayers(JNIEnv* env,jobject activity,VkDevice device,VkPhysicalDevice physical,
        VkQueue queue,uint32_t family,XrSession session,uint32_t maximum,bool capture,Reporter reporter,uint32_t projectionLayers)
        :state(std::make_unique<State>(env,activity,device,physical,queue,family,session,maximum,capture,std::move(reporter))) {
    auto& s=*state;
    reportPanelAdjustment(0);
    if(maximum<projectionLayers+1) throw std::runtime_error("Insufficient Projection plus UI layer budget");
    s.projectionLayers=projectionLayers;
    s.epoch=beginVulkanUiSession(maximum-projectionLayers-1);
    jclass cls=env->GetObjectClass(activity);
    env->CallVoidMethod(activity,env->GetMethodID(cls,"onNativeVulkanUiSessionReady","(JII)V"),static_cast<jlong>(s.epoch),static_cast<jint>(maximum),static_cast<jint>(projectionLayers));
    env->DeleteLocalRef(cls);
    State::Node main;
    main.surface=std::make_unique<VulkanComposeSurface>(env,activity,device,physical,queue,family,session,capture);
    s.nodes.emplace("main",std::move(main)); s.submitted.reserve(maximum-1);
}
VulkanUiLayers::~VulkanUiLayers()=default;
void VulkanUiLayers::prepare(XrSpace world,const XrPosef& main,const XrPosef& head,
                            const std::array<ControllerRayFrame,2>& physicalRays,float seconds,const ControllerLayerState& controllers) {
    auto& s=*state;
    const auto rays=vulkanTestInput(s.epoch).value_or(physicalRays);
    if(!s.mainAnchored) { s.mainPose=main; s.mainPose.orientation=spatial_ui::uprightYaw(main.orientation); s.mainAnchored=true;
        s.mainDistanceScale=spatial_ui::distanceScale(spatial_ui::distance(head.position,main.position))/spatial_ui::distanceScale(2.f); }
    s.headPose=head;
    s.controllers.update(controllers,head);
    const float tracking[]{head.position.x,head.position.y,head.position.z,head.orientation.x,head.orientation.y,head.orientation.z,head.orientation.w};
    std::copy(std::begin(tracking),std::end(tracking),s.rays.begin()+18);
    for(int hand=0;hand<2;++hand) {
        const auto& ray=rays[hand]; const int offset=hand*9;
        s.rays[offset]=ray.origin.x; s.rays[offset+1]=ray.origin.y; s.rays[offset+2]=ray.origin.z;
        s.rays[offset+3]=ray.direction.x; s.rays[offset+4]=ray.direction.y; s.rays[offset+5]=ray.direction.z;
        s.rays[offset+6]=ray.pressed; s.rays[offset+7]=ray.scroll; s.rays[offset+8]=ray.valid;
    }
    s.apply();
    const bool follow=panelFollowRequested.load();
    const bool held=s.manipulation.hand()>=0 || std::any_of(rays.begin(),rays.end(),[](const auto& r){return r.valid && r.grip;});
    s.mainFollow.update(s.mainPose,head.position,follow,held,seconds);
    if(s.menuAnchored && s.nodes.count("category-menu")) s.menuFollow.update(s.menuPose,head.position,follow,held,seconds);
    s.manipulate(rays,seconds);
    s.mainScale=s.mainDistanceScale*s.mainManualScale*screenSizeMultiplier.load();
    s.menuScale=s.menuDistanceScale*s.menuManualScale;
    bool reportedAdjustment=false;
    for(auto& [id,node]:s.nodes) {
        if(node.surface->update() && id=="main" && (node.surface->updates()==1 || node.surface->updates()%60==0))
            s.report("Compose Vulkan Quad updates="+std::to_string(node.surface->updates())+" size=1600x900 cpuPixelReadback=0");
    }
    std::vector<std::string> ordered;
    for(const auto& [id,node]:s.nodes) if(node.surface->ready()) ordered.push_back(id);
    std::stable_sort(ordered.begin(),ordered.end(),[&](const auto& a,const auto& b) {
        return rank(a,s.nodes.at(a).request)<rank(b,s.nodes.at(b).request);
    });
    s.submitted.clear(); s.ids.clear(); s.generations.clear(); s.geometry.clear();
    for(const auto& id:ordered) {
        auto& node=s.nodes.at(id); XrPosef pose{}; std::set<std::string> visiting;
        if(!s.pose(id,pose,visiting)) continue;
        auto quad=node.surface->layer(world,pose); const float scale=s.scale(id);
        quad.size.width*=scale; quad.size.height*=scale;
        if(node.request.parent=="controller-b-leader") {
            XrPosef unused{}; float width=0;
            if(!s.controllers.pose(node.request.parent,quad.size.height,unused,width)) continue;
            quad.size.width=width;
        }
        s.submitted.push_back(quad);
        s.ids.push_back(id); s.generations.push_back(node.request.config.generation);
        const float values[]{pose.position.x,pose.position.y,pose.position.z,pose.orientation.x,pose.orientation.y,pose.orientation.z,pose.orientation.w,quad.size.width,quad.size.height};
        s.geometry.insert(s.geometry.end(),std::begin(values),std::end(values));
        if(s.manipulation.hand()>=0 && id==s.adjustedTarget) {
            const float visible=id=="main" && s.nodes.count("home-zz-foreground")?.8f:1.f;
            float width=quad.size.width*visible,height=quad.size.height*visible;
            if(id=="main" && s.videoAspect>0) {
                width=std::min(quad.size.width,quad.size.height*s.videoAspect);
                height=width/s.videoAspect;
            }
            reportPanelAdjustment(id=="main"?1:id=="extension"?3:2,
                spatial_ui::distance(head.position,pose.position),width,height,height/scale);
            reportedAdjustment=true;
        }
    }
    if(!reportedAdjustment) reportPanelAdjustment(0);
}
std::array<bool,2> VulkanUiLayers::stabilizeInput(std::array<ControllerRayFrame,2>& input,double time) {
    auto& s=*state;std::array<bool,2> hits{};
    for(int hand=0;hand<2;++hand) {
        auto& ray=input[hand];const int hit=s.hit(ray);hits[hand]=hit>=0;
        if(hit<0 || ray.grip || s.manipulation.hand()>=0) {s.stabilizers[hand].reset();continue;}
        float distance=rayDistance(ray,s.submitted[hit]);
        const auto aim=s.stabilizers[hand].apply(ray.model.aim,s.ids[hit],s.submitted[hit].pose,
            s.submitted[hit].size,distance,time,pointerSteady.load(),false);
        ray.model.aim=aim;
        const auto direction=spatial_ui::overlayRotate(aim.orientation,{0,0,-1});
        ray.direction=direction;
    }
    return hits;
}
void VulkanUiLayers::setVideoAspect(float aspect) { state->videoAspect=aspect; }
const std::vector<XrCompositionLayerQuad>& VulkanUiLayers::layers() const { return state->submitted; }
bool VulkanUiLayers::hasBackground() const { return !state->ids.empty() && state->ids.front()=="main"; }
void VulkanUiLayers::recenterMain() { state->mainAnchored=false; state->mainManualScale=1; state->mainFollow.reset(); }
bool VulkanUiLayers::adjusting() const {
    return state->manipulation.hand()>=0 || state->triggerDistance.hand()>=0;
}
bool VulkanUiLayers::controllerOverlay(size_t index) const {
    return index<state->ids.size() && controllerLayerParent(state->nodes.at(state->ids[index]).request.parent);
}
bool VulkanUiLayers::submitted(const std::string& id) const {
    return std::find(state->ids.begin(),state->ids.end(),id)!=state->ids.end();
}
void VulkanUiLayers::writeHudDiagnostics(std::ostream& out) const {
    out<<"["; bool first=true;
    for(size_t i=0;i<state->ids.size();++i) if(controllerOverlay(i)) {
        if(!first) out<<","; first=false;
        const auto& q=state->submitted[i]; const auto& p=q.pose;
        out<<"{\"id\":\""<<state->ids[i]<<"\",\"generation\":"<<state->generations[i]
            <<",\"size\":["<<q.size.width<<","<<q.size.height<<"],\"pixels\":["
            <<q.subImage.imageRect.extent.width<<","<<q.subImage.imageRect.extent.height<<"],\"pose\":["
            <<p.position.x<<","<<p.position.y<<","<<p.position.z<<","<<p.orientation.x<<","<<p.orientation.y<<","<<p.orientation.z<<","<<p.orientation.w<<"]}";
    }
    out<<"]";
}
int VulkanUiLayers::iconFrames(moonlight::IconFrame (&icons)[5]) const {
    const auto placements=vulkanIconPlacements(state->epoch); int mask=0;
    for(int i=0;i<5;++i) {
        auto local=placements[i].local; const float scale=state->mainScale;
        local.position.x*=scale; local.position.y*=scale; local.position.z*=scale;
        icons[i]={placements[i].visible,childPose(state->mainPose,local),placements[i].size*scale};
        if(icons[i].visible) mask|=1<<i;
    }
    return mask;
}
void VulkanUiLayers::presentedFrame() {
    auto& s=*state;
    jclass cls=s.env->GetObjectClass(s.activity); jclass strings=s.env->FindClass("java/lang/String");
    jobjectArray ids=s.env->NewObjectArray(s.ids.size(),strings,nullptr);
    std::ostringstream names;
    for(size_t i=0;i<s.ids.size();++i) {
        jstring id=s.env->NewStringUTF(s.ids[i].c_str()); s.env->SetObjectArrayElement(ids,i,id); s.env->DeleteLocalRef(id);
        if(i) names<<","; names<<s.ids[i];
        auto& node=s.nodes.at(s.ids[i]);
        if(!node.announced) { node.announced=true; s.report("UI first submitted id="+s.ids[i]+" generation="+std::to_string(node.request.config.generation)); }
    }
    jlongArray generations=s.env->NewLongArray(s.generations.size());
    std::vector<jlong> longs(s.generations.begin(),s.generations.end());
    s.env->SetLongArrayRegion(generations,0,longs.size(),longs.data());
    jfloatArray geometry=s.env->NewFloatArray(s.geometry.size()); s.env->SetFloatArrayRegion(geometry,0,s.geometry.size(),s.geometry.data());
    jfloatArray rays=s.env->NewFloatArray(s.rays.size()); s.env->SetFloatArrayRegion(rays,0,s.rays.size(),s.rays.data());
    s.env->CallVoidMethod(s.activity,s.env->GetMethodID(cls,"onNativeVulkanUiFrame","([Ljava/lang/String;[J[F[F)V"),ids,generations,geometry,rays);
    s.env->DeleteLocalRef(ids); s.env->DeleteLocalRef(generations); s.env->DeleteLocalRef(geometry); s.env->DeleteLocalRef(rays);
    s.env->DeleteLocalRef(strings); s.env->DeleteLocalRef(cls);
    if(names.str()!=s.lastSubmitted) { s.lastSubmitted=names.str(); s.report("Vulkan UI submitted="+s.lastSubmitted); }
}
}
