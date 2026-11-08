package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import java.util.*;

/** Uses the preview's actual render transform for hit testing, including zoom and pan. */
final class RecipeCanvasSelection {
    private RecipeCanvasSelection() {}

    static UIElement component(UIElement element) {
        while (element != null && element.isInternalUI() && element.getParent() != null) element = element.getParent();
        return element;
    }

    static List<UIElement> hits(ModularUI preview, float screenX, float screenY, boolean includeNativeOverlay) {
        var flags = new IdentityHashMap<UIElement, Boolean>();
        var hits = new ArrayList<UIElement>();
        try {
            if (includeNativeOverlay) preview.ui.rootElement.select("*").filter(NativePartElement.class::isInstance).map(NativePartElement.class::cast)
                    .filter(part -> part.part.equals("overlay")).forEach(part -> { flags.put(part, part.isAllowHitTest()); part.setAllowHitTest(true); });
            UIElement hit;
            while ((hit = preview.hitTestAtScreen(screenX, screenY)) != null) {
                if (flags.containsKey(hit) && !hit.isAllowHitTest()) break;
                flags.putIfAbsent(hit, hit.isAllowHitTest());
                hit.setAllowHitTest(false);
                var target = component(hit);
                if (target instanceof NativePartElement part && !part.useSourceDrawing) continue;
                if (target != null && !hits.contains(target)) hits.add(target);
            }
            return hits;
        } finally { flags.forEach(UIElement::setAllowHitTest); }
    }
}
