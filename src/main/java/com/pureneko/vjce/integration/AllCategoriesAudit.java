package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.pureneko.vjce.client.*;
import com.pureneko.vjce.data.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import java.util.*;
import java.nio.file.*;

/** Exercises actual third-party categories, rather than treating ingredient codecs as machine coverage. */
public final class AllCategoriesAudit {
    @SuppressWarnings({"rawtypes", "unchecked", "removal"}) public static Map<String, Object> run() throws Exception {
        var manager = VjceJeiPlugin.runtime.getRecipeManager(); var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var rows = new TreeMap<String, Object>(); var errors = new ArrayList<String>(); int edited = 0, empty = 0, limited = 0, mekanism = 0;
        for (var entry : VjceJeiPlugin.originals.entrySet()) {
            String id = entry.getKey(); IRecipeCategory original = entry.getValue();
            if (id.startsWith("mekanism:")) mekanism++;
            var row = new LinkedHashMap<String, Object>(); rows.put(id, row);
            if (original instanceof DisplayCategory<?>) { row.put("status", "vjce-project"); continue; }
            Object raw = manager.createRecipeLookup(original.getRecipeType()).get().findFirst().orElse(null);
            if (raw == null) { row.put("status", "empty"); empty++; continue; }
            try {
                var data = CapturedCategory.capture(original, raw);
                var source = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(original, raw, focus).orElseThrow();
                source.setPosition(0, 0);
                var document = new CategoryDocument(); document.id = VjceEditor.overrideId(original.getRecipeType().getUid()).toString(); document.target = id;
                document.width = Math.max(18, original.getWidth()); document.height = Math.max(18, original.getHeight());
                NativePage.preview(document, data); // Detect a declared read-only capability before making an editable template.
                var template = RecipeUI.template(document, data); var content = template.createUI();
                var first = content.rootElement.selectId("slot_0").findFirst().orElse(null);
                int x = 0, y = 0;
                if (first != null) { x = data.slots.getFirst().x + 6; y = data.slots.getFirst().y + 3; int px = x, py = y; first.layout(l -> { l.left(px); l.top(py); }); }
                var saved = content.toTemplate(); saved.copyStylesFrom(template);
                document.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow().toString();
                var adapter = new CapturedCategory(original, document);
                var actual = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(adapter, raw, focus).orElseThrow(); actual.setPosition(0, 0);
                ((com.pureneko.vjce.mixin.RecipeLayoutAccess) actual).vjce$createExtras();
                var before = source.getRecipeSlotsView().getSlotViews(); var after = actual.getRecipeSlotsView().getSlotViews();
                if (before.size() != after.size()) throw new IllegalStateException("Slot count changed: " + before.size() + " -> " + after.size());
                if (first != null && !after.isEmpty()) {
                    var slot = (IRecipeSlotDrawable) after.getFirst();
                    if (slot.getRect().getX() != x || slot.getRect().getY() != y) throw new IllegalStateException("Saved placement was not applied");
                    if (actual.getRecipeSlotUnderMouse(x + slot.getRect().getWidth() / 2.0, y + slot.getRect().getHeight() / 2.0).orElse(null) != slot) throw new IllegalStateException("Edited slot hit-test lost identity");
                }
                if (adapter.blocksTransfer(raw)) throw new IllegalStateException("Layout-only edit disabled transfers");
                row.put("status", "edited"); row.put("slots", after.size()); row.put("recipeClass", raw.getClass().getName()); edited++;
            } catch (RuntimeException failure) {
                row.put("status", "read-only"); row.put("reason", failure.toString()); limited++;
                try {
                    var doc = new CategoryDocument(); doc.target = id;
                    var before = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(original, raw, focus).orElseThrow();
                    var after = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(new CapturedCategory(original, doc), raw, focus).orElseThrow();
                    ((com.pureneko.vjce.mixin.RecipeLayoutAccess) before).vjce$createExtras();
                    ((com.pureneko.vjce.mixin.RecipeLayoutAccess) after).vjce$createExtras();
                    boolean intact = before.getRecipeSlotsView().getSlotViews().size() == after.getRecipeSlotsView().getSlotViews().size()
                            && ((com.pureneko.vjce.mixin.RecipeLayoutAccess) before).vjce$widgets().size() == ((com.pureneko.vjce.mixin.RecipeLayoutAccess) after).vjce$widgets().size();
                    row.put("originalPagePreserved", intact); if (!intact) errors.add(id + ": fallback lost original contents");
                } catch (RuntimeException fallback) { errors.add(id + ": fallback failed: " + fallback); }
                if (!(failure instanceof NativePage.UnsupportedLayout) && (id.startsWith("mekanism:") || id.startsWith("minecraft:") || id.endsWith(":shared"))) errors.add(id + ": " + failure);
            }
        }
        var report = new LinkedHashMap<String, Object>(); report.put("categories", rows); report.put("edited", edited); report.put("empty", empty);
        report.put("limited", limited); report.put("mekanismCategories", mekanism); report.put("errors", errors); report.put("passed", errors.isEmpty());
        Files.writeString(net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("vjce-category-audit.json"), ProjectStore.JSON.toJson(report));
        return report;
    }
}
