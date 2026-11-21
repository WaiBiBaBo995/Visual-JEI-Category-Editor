package com.pureneko.vjce.client;
import com.pureneko.vjce.client.canvas.*;
import java.util.*;
final class SceneEditor {
    private static final List<SceneEditingView> opened=new ArrayList<>();
    static void playing(CanvasUI ui,boolean playing){ui.rootElement.select("*").forEach(e->{if(e instanceof BlockSceneElement s)s.clock.playing=playing;if(e instanceof CreateAnimationElement c)c.clock.playing=playing;});}
    static void reset(CanvasElement e){if(e instanceof BlockSceneElement s){s.clock.reset();s.release();}if(e instanceof CreateAnimationElement c)c.clock.reset();}
    static void open(RecipeLayoutView owner,BlockSceneElement scene){var view=new SceneEditingView(owner,scene);opened.add(view);owner.getEditor().getTabPages().addTab(null,scene==null?"vjce.scene.add":"vjce.scene.configure",view,null,null,view::close);owner.getEditor().getTabPages().switchTag(view);}
    static void closed(SceneEditingView view){opened.remove(view);}
    static void closeFor(RecipeLayoutView owner){for(var v:List.copyOf(opened))if(v.owner==owner)v.close();}
    static void copySettings(BlockSceneElement from,BlockSceneElement to){to.cameraYaw=from.cameraYaw;to.cameraPitch=from.cameraPitch;to.cameraRange=from.cameraRange;to.autoFrame=from.autoFrame;to.cameraOffsetX=from.cameraOffsetX;to.cameraOffsetY=from.cameraOffsetY;to.cameraOffsetZ=from.cameraOffsetZ;to.motion=from.motion;to.speed=from.speed;to.period=from.period;to.phase=from.phase;to.distance=from.distance;to.animateBlockEntities=from.animateBlockEntities;to.animateEntities=from.animateEntities;}
    static BlockSceneElement copy(BlockSceneElement from){var copy=new BlockSceneElement();copy.blocks=from.blocks.copy();copySettings(from,copy);return copy;}
    static void restore(BlockSceneElement from,BlockSceneElement to){to.blocks=from.blocks.copy();copySettings(from,to);to.release();}
}
