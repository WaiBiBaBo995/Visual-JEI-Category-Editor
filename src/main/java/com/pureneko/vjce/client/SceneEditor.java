package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import java.util.*;

/** Scene drafts are local; resource library edits are independently global. */
final class SceneEditor {
    private static final List<SceneEditingView> opened=new ArrayList<>();
    static void playing(UI ui,boolean playing){ui.rootElement.select("*").forEach(e->{if(e instanceof BlockSceneElement s)s.clock.playing=playing;if(e instanceof CreateAnimationElement c)c.clock.playing=playing;});}
    static void reset(UIElement e){if(e instanceof BlockSceneElement s){s.clock.reset();s.release();}if(e instanceof CreateAnimationElement c)c.clock.reset();}
    static void open(RecipeLayoutView owner,BlockSceneElement scene){
        for(var view:List.copyOf(opened))if(view.owner==owner&&view.target==scene&&view.getViewContainer()!=null){view.getViewContainer().selectView(view);return;}
        var view=new SceneEditingView(owner,scene);opened.add(view);var editor=owner.getEditor();
        editor.placeView(view,()->editor.getCenterWindow().getLeftTop());view.getViewContainer().selectView(view);
    }
    static void closed(SceneEditingView view){opened.remove(view);}
    static void closeFor(RecipeLayoutView owner){for(var view:List.copyOf(opened))if(view.owner==owner)view.removeSelf();}
    static void copySettings(BlockSceneElement from,BlockSceneElement to){
        to.cameraYaw=from.cameraYaw;to.cameraPitch=from.cameraPitch;to.cameraRange=from.cameraRange;to.autoFrame=from.autoFrame;to.motion=from.motion;
        to.speed=from.speed;to.period=from.period;to.phase=from.phase;to.distance=from.distance;to.animateBlockEntities=from.animateBlockEntities;to.animateEntities=from.animateEntities;
    }
    static BlockSceneElement copy(BlockSceneElement from){var copy=new BlockSceneElement();copy.blocks=from.blocks.copy();copySettings(from,copy);return copy;}
    static void restore(BlockSceneElement source,BlockSceneElement target){target.blocks=source.blocks.copy();copySettings(source,target);target.release();}
}
