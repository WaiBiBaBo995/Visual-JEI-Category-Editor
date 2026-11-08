package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import java.util.*;

/** JEI caches layouts without an immediate removal callback. Release idle scene resources independently. */
public final class SceneResources {
    private static final Map<UIElement,Long> ACTIVE=new IdentityHashMap<>();
    private SceneResources(){}
    static void touch(UIElement element){ACTIVE.put(element,System.nanoTime());}
    static void forget(UIElement element){ACTIVE.remove(element);}
    public static int activeCount(){return ACTIVE.size();}
    public static void sweep(){long now=System.nanoTime();for(var entry:List.copyOf(ACTIVE.entrySet()))if(now-entry.getValue()>2_000_000_000L)release(entry.getKey());}
    private static void release(UIElement element){ACTIVE.remove(element);if(element instanceof BlockSceneElement scene){scene.release();scene.clock.reset();}if(element instanceof CreateAnimationElement scene)scene.clock.reset();}
    public static void releaseAll(){for(var element:List.copyOf(ACTIVE.keySet()))release(element);}
}
