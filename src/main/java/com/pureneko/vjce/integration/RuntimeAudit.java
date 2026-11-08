package com.pureneko.vjce.integration;

import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.*;
import java.nio.file.*;
import java.util.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;

/** Explicit in-client diagnostic: exercises the real JEI layout and ingredient codecs. */
public final class RuntimeAudit {
    public static final Set<String> VANILLA = Set.of("crafting", "stonecutting", "smelting", "smoking", "blasting", "campfire_cooking", "smithing", "compostable", "fuel", "brewing", "anvil", "grindstone");
    private RuntimeAudit() {}
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Path run() throws Exception {
        if (VjceJeiPlugin.runtime == null) throw new IllegalStateException("JEI runtime is unavailable");
        var result = new LinkedHashMap<String, Object>();
        var errors = new ArrayList<String>();
        var manager = VjceJeiPlugin.runtime.getRecipeManager();
        var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        int layouts = 0;
        int focusedQueries = 0;
        for (var entry : VjceJeiPlugin.originals.entrySet()) {
            if (!entry.getKey().startsWith("minecraft:") || !VANILLA.contains(entry.getValue().getRecipeType().getUid().getPath())) continue;
            try {
                IRecipeCategory original = entry.getValue();
                var samples = manager.createRecipeLookup(original.getRecipeType()).get().limit(5).toList();
                var doc = new CategoryDocument(); doc.id = "vjce:audit/" + original.getRecipeType().getUid().getPath();
                doc.target = entry.getKey(); doc.width = original.getWidth(); doc.height = original.getHeight();
                if (!samples.isEmpty()) {
                    var captured = CapturedCategory.capture(original, samples.getFirst());
                    var template = com.pureneko.vjce.client.RecipeUI.template(doc, captured);
                    doc.layout = com.lowdragmc.lowdraglib2.gui.ui.UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, template).getOrThrow().toString();
                }
                var adapter = new CapturedCategory(original, doc);
                int count = 0;
                for (Object sample : samples) {
                    var captured = CapturedCategory.capture(original, sample);
                    if (captured.slots.isEmpty()) throw new IllegalStateException("No captured slots");
                    var imported = (mezz.jei.api.gui.IRecipeLayoutDrawable<?>) manager.createRecipeLayoutDrawable(adapter, sample, focus).orElseThrow();
                    var source = (mezz.jei.api.gui.IRecipeLayoutDrawable<?>) manager.createRecipeLayoutDrawable(original, sample, focus).orElseThrow();
                    var beforeSlots = source.getRecipeSlotsView().getSlotViews(); var afterSlots = imported.getRecipeSlotsView().getSlotViews();
                    if (beforeSlots.size() != afterSlots.size()) throw new IllegalStateException("Saved template loses recipe-dependent slots");
                    for (int i = 0; i < beforeSlots.size(); i++) {
                        var before = ((mezz.jei.api.gui.ingredient.IRecipeSlotDrawable) beforeSlots.get(i)).getAreaIncludingBackground();
                        var after = ((mezz.jei.api.gui.ingredient.IRecipeSlotDrawable) afterSlots.get(i)).getAreaIncludingBackground();
                        if (before.getX() != after.getX() || before.getY() != after.getY() || before.getWidth() != after.getWidth() || before.getHeight() != after.getHeight())
                            throw new IllegalStateException("Saved template changes source slot geometry at index " + i);
                    }
                    for (var slot : captured.slots) {
                        if (slot.invisible || slot.ingredients.isEmpty() || !(slot.role.equals("INPUT") || slot.role.equals("OUTPUT"))) continue;
                        var decodedSlot = IngredientAccess.decode(slot);
                        if (decodedSlot.isEmpty()) continue;
                        var typed = decodedSlot.getFirst();
                        var ingredientFocus = VjceJeiPlugin.helpers.getFocusFactory().createFocus(RecipeIngredientRole.valueOf(slot.role), typed);
                        var matches = manager.createRecipeLookup(original.getRecipeType()).limitFocus(List.of(ingredientFocus)).get().limit(3).toList();
                        if (matches.isEmpty()) throw new IllegalStateException("No " + slot.role + " lookup for a displayed ingredient");
                        for (Object focused : matches) if (manager.createRecipeLayoutDrawable(adapter, focused, VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of(ingredientFocus))).isEmpty()) throw new IllegalStateException("Focused layout failed");
                        focusedQueries++;
                    }
                    // Display overrides must disable transfers; pure layout changes must not.
                    if (adapter.blocksTransfer(sample)) throw new IllegalStateException("Untouched original blocks transfer");
                    captured.overrideIngredients = true; doc.recipes.add(captured);
                    if (!adapter.blocksTransfer(sample) && original.getRegistryName(sample) != null) throw new IllegalStateException("Changed display allows transfer");
                    if (entry.getKey().equals("minecraft:crafting")) {
                        var player = net.minecraft.client.Minecraft.getInstance().player;
                        var menu = new net.minecraft.world.inventory.CraftingMenu(0, player.getInventory());
                        var handler = VjceJeiPlugin.runtime.getRecipeTransferManager().getRecipeTransferHandler(menu, adapter).orElseThrow();
                        if (!(handler instanceof GuardedTransferHandler)) throw new IllegalStateException("Transfer protection not installed");
                        var layout = (mezz.jei.api.gui.IRecipeLayoutDrawable<?>) manager.createRecipeLayoutDrawable(adapter, sample, focus).orElseThrow();
                        var error = ((mezz.jei.api.recipe.transfer.IRecipeTransferHandler) handler).transferRecipe(menu, sample, layout.getRecipeSlotsView(), player, false, false);
                        if (error == null || error.getType() != mezz.jei.api.recipe.transfer.IRecipeTransferError.Type.USER_FACING) throw new IllegalStateException("Modified display transfer not blocked");
                    }
                    doc.recipes.clear();
                    count++; layouts++;
                }
                result.put(entry.getKey(), count);
            } catch (Throwable e) { errors.add(entry.getKey() + ": " + e); }
        }
        var ingredientResults = new LinkedHashMap<String, Object>();
        for (var type : IngredientAccess.manager.getRegisteredIngredientTypes()) {
            try {
                var ingredients = IngredientAccess.manager.getAllTypedIngredients(type);
                if (!ingredients.isEmpty()) {
                    var original = ingredients.iterator().next();
                    var encoded = IngredientAccess.encode(original);
                    if (original.getIngredient().getClass().getName().equals("mekanism.api.chemical.ChemicalStack")) encoded.amount = 4321;
                    var decoded = IngredientAccess.decode(encoded);
                    var restored = IngredientAccess.encode(decoded.getFirst());
                    if (restored.amount != encoded.amount) throw new IllegalStateException("Amount lost during round trip");
                    ingredientResults.put(type.getUid(), "PASS");
                }
            } catch (Throwable e) { errors.add(type.getUid() + ": " + e); }
        }
        result.put("ingredientTypes", ingredientResults); result.put("layouts", layouts); result.put("errors", errors);
        result.put("focusedQueries", focusedQueries);
        result.put("registeredOverrides", VjceJeiPlugin.adapters.size());
        for (var entry : VjceJeiPlugin.adapters.entrySet()) {
            try {
                CapturedCategory registered = entry.getValue();
                if (manager.getRecipeCategory(registered.getRecipeType()) != registered) throw new IllegalStateException("Original category not replaced");
                var live = manager.createRecipeLookup(registered.getRecipeType()).get().toList();
                for (var data : registered.document.recipes) {
                    if (data.hidden && !data.source.isBlank()) {
                        if (live.stream().anyMatch(raw -> data.source.equals(String.valueOf(registered.getRegistryName(raw))))) throw new IllegalStateException("Hidden display remains visible");
                        if (data.source.equals("minecraft:stick") && net.minecraft.client.Minecraft.getInstance().level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(data.source)).isEmpty()) throw new IllegalStateException("Actual recipe was modified");
                    }
                    if (data.source.isBlank() && !data.hidden) {
                        Object added = live.stream().filter(raw -> data.id.equals(String.valueOf(registered.getRegistryName(raw)))).findFirst().orElseThrow(() -> new IllegalStateException("Added recipe missing: " + data.id));
                        var codec = registered.getCodec(VjceJeiPlugin.helpers.getCodecHelper(), manager);
                        if (codec != null) {
                            var ops = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                            Object encoded = codec.encodeStart(ops, added).getOrThrow();
                            Object decoded = codec.parse(ops, encoded).getOrThrow();
                            if (!data.id.equals(String.valueOf(registered.getRegistryName(decoded)))) throw new IllegalStateException("Bookmark ID lost");
                        }
                    }
                }
            } catch (Throwable e) { errors.add(entry.getKey() + " integration: " + e); }
        }
        for (var document : VjceJeiPlugin.documents()) if (document.target.isBlank() && !document.recipes.isEmpty()) {
            try {
                var type = VjceJeiPlugin.helpers.getRecipeType(net.minecraft.resources.ResourceLocation.parse(document.id)).orElseThrow();
                IRecipeCategory category = manager.getRecipeCategory(type);
                var recipe = manager.createRecipeLookup(type).get().findFirst().orElseThrow();
                if (manager.createRecipeLayoutDrawable(category, recipe, focus).isEmpty()) throw new IllegalStateException("New category layout failed");
                var codec = category.getCodec(VjceJeiPlugin.helpers.getCodecHelper(), manager);
                var ops = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
                var restored = codec.parse(ops, codec.encodeStart(ops, recipe).getOrThrow()).getOrThrow();
                if (!Objects.equals(category.getRegistryName(recipe), category.getRegistryName(restored))) throw new IllegalStateException("New category bookmark failed");
            } catch (Throwable e) { errors.add(document.id + " registration: " + e); }
        }
        result.put("passed", errors.isEmpty() && result.keySet().stream().filter(key -> key.startsWith("minecraft:")).count() == 12);
        var path = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("vjce-audit.json");
        Files.writeString(path, ProjectStore.JSON.toJson(result));
        VisualJeiCategoryEditor.LOG.info("VJCE runtime audit: {}", result);
        return path;
    }
}
