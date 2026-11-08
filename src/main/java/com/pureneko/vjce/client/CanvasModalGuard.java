package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.pureneko.vjce.integration.NativePage;

/** LDLib's modal dialogs fill the host; allowInteraction dialogs use automatic dimensions. */
final class CanvasModalGuard {
    private CanvasModalGuard() {}
    static boolean blocked(ModularUI ui) {
        if (ui == null) return false;
        return ui.ui.rootElement.getChildren().stream().filter(Dialog.class::isInstance).anyMatch(element -> {
            if (!NativePage.visible(element)) return false;
            var width = element.getLayout().getValueImmediate(LayoutProperties.WIDTH);
            var height = element.getLayout().getValueImmediate(LayoutProperties.HEIGHT);
            return width.isPercent() && height.isPercent() && width.getValue() >= 1 && height.getValue() >= 1;
        });
    }
}
