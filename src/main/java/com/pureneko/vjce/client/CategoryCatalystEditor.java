package com.pureneko.vjce.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.integration.IngredientAccess;
import com.pureneko.vjce.integration.ItemPresentationCodec;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class CategoryCatalystEditor {
    private CategoryCatalystEditor() {}

    static void addTo(UIElement root, CategoryDocument document, Runnable refresh) {
        var status = new Label();
        root.addChildren(new Label().setText("vjce.field.catalysts"), Forms.row().addChildren(
                new Button().setText("vjce.catalyst.choose").setOnClick(event -> IngredientPicker.openCatalysts(root,
                        stack -> add(document, stack, status, refresh))).setId("vjce_choose_catalyst"),
                new Button().setText("vjce.catalyst.add_id").setOnClick(event -> Dialog.stringEditorDialog(
                        "vjce.catalyst.add_id", "", CategoryCatalystEditor::validItemId, value -> {
                            if (value != null && validItemId(value)) add(document,
                                    ItemPresentationCodec.decode(new JsonPrimitive(value.strip())), status, refresh);
                        }).show(root.getModularUI()))), status);
        for (int i = 0; i < document.catalysts.size(); i++) {
            int index = i;
            var encoded = document.catalysts.get(index);
            var row = Forms.row();
            var stack = decode(encoded);
            if (stack != null) {
                var icon = new IngredientSlot();
                if (IngredientAccess.manager != null) {
                    icon.ingredient = IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElse(null);
                    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    row.style(s -> s.tooltips(IngredientPicker.itemTooltip(stack, id)));
                }
                row.addChildren(icon, new Label().setText(stack.getHoverName()).layout(l -> l.flex(1)));
            } else {
                row.addChild(new Label().setText("vjce.catalyst.missing").layout(l -> l.flex(1)));
                row.style(s -> s.tooltips(String.valueOf(encoded)));
            }
            row.addChild(new Button().setText("vjce.catalyst.remove").setOnClick(event -> {
                document.catalysts.remove(index);
                refresh.run();
            }).addClass("__reject-button__").setId("vjce_remove_catalyst_" + index));
            root.addChild(row);
        }
    }

    private static boolean validItemId(String value) {
        var id = ResourceLocation.tryParse(value.strip());
        return id != null && BuiltInRegistries.ITEM.containsKey(id) && !id.equals(ResourceLocation.withDefaultNamespace("air"));
    }

    private static ItemStack decode(JsonElement encoded) {
        try { return ItemPresentationCodec.decode(encoded); }
        catch (RuntimeException invalid) { return null; }
    }

    private static void add(CategoryDocument document, ItemStack stack, Label status, Runnable refresh) {
        if (document.catalysts.stream().map(CategoryCatalystEditor::decode)
                .anyMatch(previous -> previous != null && ItemStack.isSameItemSameComponents(previous, stack))) {
            status.setText("vjce.catalyst.duplicate");
            return;
        }
        try {
            document.catalysts.add(ItemPresentationCodec.encode(stack));
            refresh.run();
        } catch (RuntimeException error) {
            status.setText("vjce.catalyst.invalid");
        }
    }
}
