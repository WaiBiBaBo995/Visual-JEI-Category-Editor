package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.util.*;

public final class IngredientAccess {
    public static IIngredientManager manager;
    private IngredientAccess() {}

    public static List<ITypedIngredient<?>> decode(CategoryDocument.Slot slot) {
        var values = new ArrayList<ITypedIngredient<?>>();
        for (var ingredient : slot.ingredients) values.addAll(decode(ingredient));
        return values;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<ITypedIngredient<?>> decode(CategoryDocument.Ingredient spec) {
        if (spec.empty) return List.of();
        if (manager == null) return List.of();
        if (spec.data != null && spec.type != null && !spec.type.isBlank()) {
            IIngredientType type = manager.getIngredientTypeForUid(spec.type).orElseThrow(() -> new IllegalArgumentException("Missing JEI ingredient type: " + spec.type));
            Object value = type == VanillaTypes.ITEM_STACK ? ItemPresentationCodec.decode(spec.data)
                    : manager.getIngredientCodec(type).parse(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), spec.data).getOrThrow();
            value = withAmount(value, spec.amount);
            return List.of((ITypedIngredient<?>) manager.createTypedIngredient(type, value).orElseThrow());
        }
        var result = new ArrayList<ITypedIngredient<?>>();
        if (!spec.tag.isBlank()) {
            var id = ResourceLocation.parse(spec.tag);
            if (!spec.fluid.isBlank()) {
                BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, new FluidStack(holder, (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add)));
            } else {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(holder, (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add)));
            }
        } else if (!spec.item.isBlank()) {
            var id = ResourceLocation.parse(spec.item);
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing item: " + id);
            manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BuiltInRegistries.ITEM.get(id), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add);
        } else if (!spec.fluid.isBlank()) {
            var id = ResourceLocation.parse(spec.fluid);
            if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalArgumentException("Missing fluid: " + id);
            manager.createTypedIngredient(NeoForgeTypes.FLUID_STACK, new FluidStack(BuiltInRegistries.FLUID.get(id), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("Ingredient resolves to no registered values");
        return result;
    }

    public static List<Optional<ITypedIngredient<?>>> decodeChoices(CategoryDocument.Slot slot) {
        var result = new ArrayList<Optional<ITypedIngredient<?>>>();
        for (var ingredient : slot.ingredients) {
            if (ingredient.empty) result.add(Optional.empty());
            else decode(ingredient).forEach(value -> result.add(Optional.of(value)));
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static CategoryDocument.Ingredient encode(ITypedIngredient<?> typed) {
        var spec = new CategoryDocument.Ingredient();
        IIngredientType type = typed.getType();
        Object value = typed.getIngredient();
        spec.type = type.getUid();
        try {
            spec.data = type == VanillaTypes.ITEM_STACK ? ItemPresentationCodec.encode((ItemStack) value)
                    : (com.google.gson.JsonElement) manager.getIngredientCodec(type).encodeStart(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
        } catch (RuntimeException e) {
            String name = value instanceof ItemStack stack ? String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())) : value.getClass().getName();
            throw new IllegalArgumentException("Cannot serialize JEI display ingredient " + spec.type + " / " + name + ": " + e.getMessage(), e);
        }
        if (value instanceof ItemStack stack) { spec.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); spec.amount = stack.getCount(); }
        else if (value instanceof FluidStack stack) { spec.fluid = BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString(); spec.amount = stack.getAmount(); }
        else if (isChemical(value)) {
            try { spec.amount = ((Number) value.getClass().getMethod("getAmount").invoke(value)).longValue(); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot read Mekanism chemical amount", e); }
        }
        return spec;
    }

    private static boolean isChemical(Object value) { return value.getClass().getName().equals("mekanism.api.chemical.ChemicalStack"); }
    private static Object withAmount(Object value, long amount) {
        if (value instanceof ItemStack item) return item.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
        if (value instanceof FluidStack fluid) { var copy = fluid.copy(); copy.setAmount((int) Math.min(Integer.MAX_VALUE, amount)); return copy; }
        if (isChemical(value)) {
            try { return value.getClass().getMethod("copyWithAmount", long.class).invoke(value, amount); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot restore Mekanism chemical amount", e); }
        }
        return value;
    }
}
