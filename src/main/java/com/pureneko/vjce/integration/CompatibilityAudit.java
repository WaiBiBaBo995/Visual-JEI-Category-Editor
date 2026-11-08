package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.JsonOps;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.mixin.RecipeLayoutAccess;
import mezz.jei.api.constants.*;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiConsumer;

/** Development-only regression fixtures for the reported login disconnection. */
public final class CompatibilityAudit {
    private static final ResourceLocation RECIPE_ID = ResourceLocation.parse("vjce:qa/damageable_stackable");
    private CompatibilityAudit() {}

    public static void register(IRecipeRegistration registration) {
        if (!enabled()) return;
        var ingredients = NonNullList.<Ingredient>create(); ingredients.add(Ingredient.of(Items.STONE));
        RecipeHolder<CraftingRecipe> recipe = new RecipeHolder<>(RECIPE_ID, new ShapelessRecipe("", CraftingBookCategory.MISC, incompatibleStack(), ingredients));
        registration.addRecipes(RecipeTypes.CRAFTING, List.of(recipe));
    }

    public static boolean enabled() { return !net.neoforged.fml.loading.FMLEnvironment.production && Boolean.getBoolean("vjce.compatTest"); }

    private static ItemStack incompatibleStack() {
        var stack = new ItemStack(Items.DIAMOND_PICKAXE, 7);
        stack.set(DataComponents.MAX_STACK_SIZE, 64); stack.setDamageValue(23);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("VJCE compatibility regression"));
        var tag = new CompoundTag(); tag.putByte("byte", (byte) 3); tag.putInt("int", 3); tag.putLong("long", 3L);
        tag.putByteArray("bytes", new byte[]{1, 2, 3}); tag.putIntArray("ints", new int[]{1, 2, 3}); tag.putLongArray("longs", new long[]{1, 2, 3});
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static void run(BiConsumer<String, Boolean> check) {
        var manager = IngredientAccess.manager;
        var stack = incompatibleStack(); var snapshot = stack.copy();
        var strict = manager.getIngredientCodec(VanillaTypes.ITEM_STACK).encodeStart(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), stack);
        check.accept("reproduces reported strict-codec error", strict.error().map(error -> error.message().contains("Item cannot be both damageable and stackable")).orElse(false));
        var typed = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, stack, false).orElseThrow();
        var encoded = IngredientAccess.encode(typed);
        var saved = ProjectStore.JSON.toJson(encoded);
        var restored = ProjectStore.JSON.fromJson(saved, CategoryDocument.Ingredient.class);
        var decoded = (ItemStack) IngredientAccess.decode(restored).getFirst().getIngredient();
        check.accept("incompatible components preserved without repair", decoded.getMaxStackSize() == 64 && decoded.getMaxDamage() == snapshot.getMaxDamage() && decoded.getDamageValue() == 23);
        check.accept("item components and name survive JSON round trip", ItemStack.isSameItemSameComponents(snapshot, decoded));
        check.accept("component NBT numeric types survive", snapshot.get(DataComponents.CUSTOM_DATA).copyTag().equals(decoded.get(DataComponents.CUSTOM_DATA).copyTag()));
        check.accept("stack count survives independently of codec", decoded.getCount() == 7);
        check.accept("encoding leaves the original stack unchanged", ItemStack.isSameItemSameComponents(snapshot, stack) && snapshot.getCount() == stack.getCount());
        var large = manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.STONE, 128), false).orElseThrow();
        var largeRestored = (ItemStack) IngredientAccess.decode(IngredientAccess.encode(large)).getFirst().getIngredient();
        check.accept("display amounts above vanilla serialization limit survive", largeRestored.getCount() == 128);

        var valid = new ItemStack(Items.DIAMOND_SWORD); valid.setDamageValue(17);
        valid.set(DataComponents.CUSTOM_DATA, snapshot.get(DataComponents.CUSTOM_DATA));
        var previousFormat = new CategoryDocument.Ingredient(); previousFormat.type = VanillaTypes.ITEM_STACK.getUid();
        previousFormat.data = manager.getIngredientCodec(VanillaTypes.ITEM_STACK).encodeStart(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), valid).getOrThrow();
        check.accept("previous JEI component format remains readable", ItemStack.isSameItemSameComponents(valid, (ItemStack) IngredientAccess.decode(previousFormat).getFirst().getIngredient()));

        var runtime = VjceJeiPlugin.runtime;
        var added = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get().filter(recipe -> recipe.id().equals(RECIPE_ID)).findFirst().orElseThrow();
        check.accept("incompatible recipe survives real JEI startup indexing", runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING) instanceof CapturedCategory<?>);
        @SuppressWarnings("unchecked") var category = (IRecipeCategory<RecipeHolder<CraftingRecipe>>) VjceJeiPlugin.originals.get("minecraft:crafting");
        var captured = CapturedCategory.capture(category, added);
        var output = captured.slots.stream().filter(slot -> slot.role.equals("OUTPUT")).findFirst().orElseThrow();
        check.accept("incompatible recipe can be imported into editor data", ItemStack.isSameItemSameComponents(snapshot, (ItemStack) IngredientAccess.decode(output).getFirst().getIngredient()));
        var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var liveCategory = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING);
        check.accept("incompatible recipe retains live JEI page", runtime.getRecipeManager().createRecipeLayoutDrawable(liveCategory, added, focus).isPresent());

        var source = new NoProxyCategory(); var doc = new CategoryDocument(); doc.target = source.getRecipeType().getUid().toString();
        var adapter = new CapturedCategory<>(source, doc); var raw = new Object();
        check.accept("isHandled never captures untouched recipe", adapter.isHandled(raw) && source.calls == 0);
        try {
            VjceJeiPlugin.runtime = null;
            check.accept("startup delegates untouched layout directly", runtime.getRecipeManager().createRecipeLayoutDrawable(adapter, raw, focus).isPresent() && source.calls == 1);
        } finally { VjceJeiPlugin.runtime = runtime; }
        var fallback = runtime.getRecipeManager().createRecipeLayoutDrawable(adapter, raw, focus).orElseThrow();
        var access = (RecipeLayoutAccess) fallback; access.vjce$createExtras();
        check.accept("capture failure preserves original slots and extras", fallback.getRecipeSlotsView().getSlotViews().size() == 1 && access.vjce$widgets().size() == 1);
    }

    private static final class NoProxyCategory implements IRecipeCategory<Object> {
        int calls;
        final RecipeType<Object> type = new RecipeType<>(ResourceLocation.parse("vjce:qa/uncapturable"), Object.class);
        @Override public RecipeType<Object> getRecipeType() { return type; }
        @Override public Component getTitle() { return Component.literal("Regression fallback"); }
        @Override public int getWidth() { return 60; }
        @Override public int getHeight() { return 24; }
        @Override public mezz.jei.api.gui.drawable.IDrawable getIcon() { return null; }
        @Override public ResourceLocation getRegistryName(Object recipe) { return ResourceLocation.parse("vjce:qa/source"); }
        @Override public void setRecipe(IRecipeLayoutBuilder builder, Object recipe, IFocusGroup focus) {
            if (Proxy.isProxyClass(builder.getClass())) throw new IllegalArgumentException("Expected regression fixture: source cannot be captured");
            calls++; builder.addOutputSlot(1, 1).addItemStack(new ItemStack(Items.STONE));
        }
        @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, Object recipe, IFocusGroup focus) { builder.addText(Component.literal("Source"), 24, 9).setPosition(24, 2); }
    }
}
