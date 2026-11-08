package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.pureneko.vjce.integration.NativePage;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import java.util.List;

/** An editable placement backed by a live JEI declaration; Java objects are never serialized. */
@LDLRegister(name = "vjce-jei-part", group = "vjce", registry = "ldlib2:ui_element")
public class NativePartElement extends UIElement {
    @Persisted public String part = "native";
    @Persisted @Configurable(name = "vjce.native.source", tips = "vjce.native.source.tip") public String sourceLabel = "JEI source drawing";
    @Persisted @Configurable(name = "vjce.native.draw", tips = "vjce.native.draw.tip") public boolean useSourceDrawing = true;
    @Persisted @Configurable(name = "vjce.native.text", tips = "vjce.native.text.tip") public String replacementText = "";
    @Persisted @Configurable(name = "vjce.native.overrideColor") public boolean overrideTextColor = false;
    @Persisted @Configurable(name = "vjce.native.color") public int textColor = 0xFF000000;

    @Persisted @Configurable(name="vjce.native.texture", tips="vjce.native.texture.tip") public String replacementTexture = "";
    @Persisted public float sourceX, sourceY, sourceWidth=1, sourceHeight=1;

    @SuppressWarnings({"unchecked", "rawtypes"}) public NativePartElement() {
        LDLibJEIPlugin.clickableIngredient(this, () -> {
            var page = NativePage.context(this); var slot = page == null ? null : page.previewSlot(this);
            return slot == null || !useSourceDrawing ? null : (ITypedIngredient) slot.getDisplayedIngredient().orElse(null);
        });
    }
    @Override @SuppressWarnings("removal") public HoverTooltips collectHoverTooltips() {
        var page = NativePage.context(this); var slot = page == null ? null : page.previewSlot(this);
        if (slot != null && useSourceDrawing) return new HoverTooltips(slot.getTooltip(), null, null, null);
        var nativeText = page == null ? null : page.drawing.tooltips.get(part);
        if (nativeText != null) return new HoverTooltips(List.of(nativeText), null, null, null);
        var hover = super.collectHoverTooltips();
        if (!part.startsWith("draw_")) return hover;
        // Older layouts serialized these editor diagnostics as ordinary tooltip lines.
        if (hover != null) {
            var texts = hover.tooltipTexts().stream().filter(text -> !sourceDiagnostic(text)).toList();
            if (texts.size() != hover.tooltipTexts().size()) {
                hover = texts.isEmpty() && hover.tooltipComponent() == null && hover.tooltipStack() == null
                        ? null : new HoverTooltips(texts, hover.tooltipComponent(), hover.tooltipFont(), hover.tooltipStack());
            }
        }
        if (page == null || page.runtime()) return hover;
        var piece = page.drawing.pieces.get(part);
        if (piece == null) return hover;
        if (hover == null) hover = HoverTooltips.empty();
        if (page.drawing.unstable.contains(part)) return hover.append(Component.translatable("vjce.native.unstable"));
        return hover.append(Component.literal(piece.label()), Component.translatable(piece.component()
                ? "vjce.native.component.tip" : "vjce.native.fragment.tip"));
    }

    private boolean sourceDiagnostic(Component text) {
        if (text.getContents() instanceof TranslatableContents translated) {
            String key = translated.getKey();
            return key.equals(sourceLabel) || key.equals("vjce.native.unstable")
                    || key.equals("vjce.native.fragment.tip") || key.equals("vjce.native.component.tip");
        }
        return text.getString().equals(sourceLabel);
    }

    @Override public void drawBackgroundAdditional(GUIContext context) {
        var page = NativePage.context(this);
        if (page == null || page.runtime() || !useSourceDrawing) return;
        page.drawElement(this, context);
    }
}
