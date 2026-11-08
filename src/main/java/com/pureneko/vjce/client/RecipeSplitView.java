package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.elements.SplitView;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

/** Preserve the user's ratio across layout clamping; save once at the end of a divider drag. */
final class RecipeSplitView extends SplitView.Horizontal {
    private final VjceEditorSettings settings;
    private float preferred;
    private boolean changed;

    RecipeSplitView(VjceEditorSettings settings) {
        this.settings = settings; preferred = settings == null ? 25 : settings.recipeBrowserPercentage();
        setId("vjce_recipe_split"); setBorderSize(5); super.setPercentage(preferred);
        first.layout(l -> l.paddingRight(5));
        addEventListener(UIEvents.MOUSE_UP, e -> remember(), true);
        addEventListener(UIEvents.DRAG_END, e -> remember());
    }
    @Override protected void onLayoutChanged() {
        super.onLayoutChanged();
        float width = getSizeWidth(); if (width <= 0) return;
        float min = Math.min(40, 160 / width * 100);
        float max = Math.max(min, 100 - Math.min(50, 240 / width * 100));
        setMinPercentage(min); setMaxPercentage(max);
        if (Math.abs(getPercentage()-Math.clamp(preferred,min,max)) > .01f) super.setPercentage(preferred);
    }
    @Override protected void onDragSourceUpdate(UIEvent event) {
        float before = getPercentage(); super.onDragSourceUpdate(event);
        if (Math.abs(before-getPercentage()) > .01f) { preferred=getPercentage(); changed=true; }
    }
    private void remember() {
        if (changed && settings != null) settings.rememberRecipeBrowser(preferred);
        changed=false;
    }
    @Override protected void onRemoved() { remember(); super.onRemoved(); }
}
