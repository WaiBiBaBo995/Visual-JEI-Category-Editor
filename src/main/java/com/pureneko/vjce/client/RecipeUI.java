package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.sync.bindings.impl.SupplierDataSource;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.integration.xei.jei.JEIUIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeSlotHandler;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.IngredientAccess;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.TagParser;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.ingredients.ITypedIngredient;
import java.util.*;

public final class RecipeUI {
    private RecipeUI() {}
    public static final net.minecraft.resources.ResourceLocation CANVAS_STYLES = net.minecraft.resources.ResourceLocation.parse("vjce:lss/recipe.lss");
    public static UITemplate canvasTemplate(UITemplate template) {
        var styles = template.getStylesheets();
        if (!styles.contains(com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager.MC)) styles.addFirst(com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager.MC);
        if (!styles.contains(CANVAS_STYLES)) styles.add(Math.min(1, styles.size()), CANVAS_STYLES);
        return template;
    }
    public static UITemplate savedTemplate(CategoryDocument document) {
        try { return canvasTemplate(UITemplate.CODEC.parse(NbtOps.INSTANCE, TagParser.parseTag(document.layout)).getOrThrow()); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid layout for " + document.id, e); }
    }
    public static UITemplate template(CategoryDocument document, CategoryDocument.Recipe recipe) {
        if (!document.layout.isBlank()) return savedTemplate(document);
        if (!document.target.isBlank()) {
            com.pureneko.vjce.integration.NativePage page;
            try { page = com.pureneko.vjce.integration.NativePage.preview(document, recipe); }
            catch (RuntimeException failure) { page = com.pureneko.vjce.integration.NativePage.uneditedPreview(document); }
            if (page != null) return page.template(document);
            return canvasTemplate(UITemplate.of(new UIElement().layout(l -> { l.width(document.width); l.height(document.height); })
                    .addChild(new Label().setText("vjce.source.empty"))));
        }
        var root = new UIElement().layout(l -> { l.width(document.width); l.height(document.height); });
        root.style(s -> s.backgroundTexture(com.lowdragmc.lowdraglib2.gui.ui.styletemplate.MCSprites.RECT));
        for (int i = 0; i < recipe.slots.size(); i++) {
            var spec = recipe.slots.get(i);
            if (spec.invisible) continue;
            var element = new IngredientSlot();
            element.setId(recipe.slotIdentifier(i));
            int x = spec.x, y = spec.y;
            element.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(x); l.top(y); });
            root.addChild(element);
        }
        var duration = new Label().setText("Duration");
        duration.setId("duration");
        duration.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(4); l.top(Math.max(0, document.height - 12)); });
        root.addChild(duration);
        for (int i = 0; i < document.fields.size(); i++) {
            var field = document.fields.get(i);
            int y = Math.max(0, document.height - 28 - i * 14);
            var label = new Label().setText(field.label);
            label.setId("field_" + field.key);
            label.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(4); l.top(y); });
            label.textStyle(s -> s.textColor(field.color));
            if (!field.icon.isBlank()) {
                var icon = new UIElement().setId("icon_" + field.key);
                icon.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(4); l.top(y); l.width(10); l.height(10); });
                icon.style(s -> s.backgroundTexture(com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture.of(net.minecraft.resources.ResourceLocation.parse(field.icon))));
                label.layout(l -> l.left(16)); root.addChild(icon);
            }
            var bar = new ProgressBar(); bar.setId("bar_" + field.key);
            bar.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(document.width - 64); l.top(y); l.width(60); l.height(10); });
            root.addChildren(label, bar);
        }
        return canvasTemplate(UITemplate.of(root));
    }

    public static ModularUI create(CategoryDocument document, CategoryDocument.Recipe recipe) {
        var ui = template(document, recipe).createUI();
        bindSource(ui, document, recipe);
        bind(ui, document, recipe);
        return new ModularUI(ui);
    }
    public static void bindSource(UI ui, CategoryDocument document, CategoryDocument.Recipe recipe) {
        if (document.target.isBlank()) return;
        com.pureneko.vjce.integration.NativePage page;
        try { page = com.pureneko.vjce.integration.NativePage.preview(document, recipe); }
        catch (RuntimeException failure) { page = com.pureneko.vjce.integration.NativePage.uneditedPreview(document); }
        if (page != null) page.bind(ui, document);
    }

    public static void bind(UI ui, CategoryDocument document, CategoryDocument.Recipe recipe) {
        bind(ui, document, recipe, false);
    }

    public static boolean isSlot(UIElement element) {
        return element instanceof IngredientSlot || element instanceof ItemSlot || element instanceof FluidSlot
                || element instanceof NativePartElement part && part.part.startsWith("slot_");
    }

    public static UIElement slotElement(UIElement root, String identifier) {
        return root.selectId(identifier).filter(RecipeUI::isSlot).findFirst().orElse(null);
    }

    static void updateSlotPreview(UIElement root, CategoryDocument.Recipe recipe, String identifier) {
        var element = slotElement(root, identifier);
        if (element == null || element instanceof NativePartElement) return;
        var slot = recipe.boundSlot(identifier);
        var ingredients = slot == null ? List.<ITypedIngredient<?>>of() : IngredientAccess.decode(slot);
        showSlot(element, slot, ingredients);
    }

    public static void bind(UI ui, CategoryDocument document, CategoryDocument.Recipe recipe, boolean nativeSlots) {
        for (int i = 0; i < recipe.slots.size(); i++) {
            var slot = recipe.slots.get(i);
            if (slot.invisible) continue;
            var element = slotElement(ui.rootElement, recipe.slotIdentifier(i));
            if (element == null || element instanceof NativePartElement) continue;
            if (nativeSlots) {
                updateIngredient(element, null);
                continue;
            }
            var ingredients = IngredientAccess.decode(slot);
            showSlot(element, slot, ingredients);
            element.style(s -> s.tooltips("Chance: " + Math.round(slot.chance * 100) + "%"));
            element.addEventListener(JEIUIEvents.RECIPE_BINDING, event -> {
                if (event.customData instanceof JEIRecipeSlotHandler handler) {
                    handler.addSlot(element, RecipeIngredientRole.valueOf(slot.role), ingredients,
                            new JEIRecipeSlotHandler.SlotUpdater(value -> updateIngredient(element, value)));
                }
            });
        }
        var variables = document.variables(recipe);
        ui.rootElement.selectId("duration", Label.class).forEach(label -> label.setText(String.format(Locale.ROOT, "%.2f s", recipe.duration / 20.0)));
        ui.rootElement.selectId("progress", ProgressBar.class).forEach(bar -> {
            bar.label.setText("");
            bar.bindDataSource(SupplierDataSource.of(() ->
                (float) ((System.currentTimeMillis() % Math.max(50, (long) (recipe.duration * 50))) / Math.max(50.0, recipe.duration * 50))));
        });
        for (var field : document.fields) {
            double value = variables.getOrDefault(field.key, field.initial);
            ui.rootElement.selectId("field_" + field.key, Label.class).forEach(label -> label.setText(value + " " + field.unit));
            ui.rootElement.selectId("bar_" + field.key, ProgressBar.class).forEach(bar -> {
                bar.setProgress((float) (value / Math.max(1, field.maximum)));
                bar.label.setText(Math.round(100 * value / Math.max(1, field.maximum)) + "%");
            });
        }
        for (var binding : document.bindings) {
            double value = Expression.evaluate(binding.expression, variables);
            ui.rootElement.selectId(binding.element).forEach(element -> {
                switch (binding.property) {
                    case "visible" -> element.setDisplay(value != 0);
                    case "text" -> {
                        if (element instanceof Label label) label.setText(String.format(Locale.ROOT, binding.format, value));
                        if (element instanceof NativePartElement part) part.replacementText = String.format(Locale.ROOT, binding.format, value);
                    }
                    case "progress" -> { if (element instanceof ProgressBar bar) { bar.setProgress((float) value); bar.label.setText(Math.round(value * 100) + "%"); } }
                    case "color" -> {
                        if (element instanceof Label label) label.textStyle(style -> style.textColor((int) (long) value));
                        if (element instanceof NativePartElement part) { part.overrideTextColor = true; part.textColor = (int) (long) value; }
                    }
                    default -> throw new IllegalArgumentException("Unknown binding property: " + binding.property);
                }
            });
        }
    }
    private static void showSlot(UIElement element, CategoryDocument.Slot slot, List<ITypedIngredient<?>> ingredients) {
        updateIngredient(element, ingredients.isEmpty() ? null : ingredients.getFirst());
        if (element instanceof IngredientSlot generic) {
            generic.amount = slot == null || slot.ingredients.isEmpty() ? 1 : slot.ingredients.getFirst().amount;
        }
    }

    private static void updateIngredient(UIElement element, ITypedIngredient<?> typed) {
        if (element instanceof IngredientSlot generic) generic.ingredient = typed;
        else if (element instanceof ItemSlot item) item.setValue(typed != null && typed.getIngredient() instanceof net.minecraft.world.item.ItemStack stack ? stack : net.minecraft.world.item.ItemStack.EMPTY, false);
        else if (element instanceof FluidSlot fluid) fluid.setValue(typed != null && typed.getIngredient() instanceof net.neoforged.neoforge.fluids.FluidStack stack ? stack : net.neoforged.neoforge.fluids.FluidStack.EMPTY, false);
    }
}
