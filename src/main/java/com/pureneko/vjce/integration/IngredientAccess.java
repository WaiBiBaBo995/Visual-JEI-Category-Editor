package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.fabric.ingredients.fluids.JeiFluidIngredient;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponentPatch;
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
        if (!spec.fluid.isBlank() && spec.tag.isBlank()) return List.of(decodeFluid(spec));
        if (spec.data != null && spec.type != null && !spec.type.isBlank()) {
            IIngredientType type = manager.getIngredientTypeForUid(spec.type).orElseThrow(() -> new IllegalArgumentException("Missing JEI ingredient type: " + spec.type));
            Object value = type == VanillaTypes.ITEM_STACK ? ItemPresentationCodec.decode(spec.data)
                    : manager.getIngredientCodec(type).parse(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), spec.data).getOrThrow();
            value = withAmount(value, spec.amount);
            return List.of((ITypedIngredient<?>) manager.createTypedIngredient(type, value).orElseThrow());
        }
        var result = new ArrayList<ITypedIngredient<?>>();
        if (!spec.tag.isBlank()) {
            var id = Identifier.parse(spec.tag);
            if (!spec.fluid.isBlank()) {
                BuiltInRegistries.FLUID.get(TagKey.create(Registries.FLUID, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(FabricTypes.FLUID_STACK, new JeiFluidIngredient(FluidVariant.of(holder.value()), nativeFluidAmount(spec.amount))).ifPresent(result::add)));
            } else {
                BuiltInRegistries.ITEM.get(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(holder, (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add)));
            }
        } else if (!spec.item.isBlank()) {
            var id = Identifier.parse(spec.item);
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing item: " + id);
            manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BuiltInRegistries.ITEM.getValue(id), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add);

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
            spec.data = value instanceof IJeiFluidIngredient fluid ? encodeFluid(fluid)
                    : type == VanillaTypes.ITEM_STACK ? ItemPresentationCodec.encode((ItemStack) value)
                    : (com.google.gson.JsonElement) manager.getIngredientCodec(type).encodeStart(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE), value).getOrThrow();
        } catch (RuntimeException e) {
            String name = value instanceof ItemStack stack ? String.valueOf(BuiltInRegistries.ITEM.getKey(stack.getItem())) : value.getClass().getName();
            throw new IllegalArgumentException("Cannot serialize JEI display ingredient " + spec.type + " / " + name + ": " + e.getMessage(), e);
        }
        if (value instanceof ItemStack stack) { spec.item = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(); spec.amount = stack.getCount(); }
        else if (value instanceof IJeiFluidIngredient stack) {
            spec.fluid = BuiltInRegistries.FLUID.getKey(stack.getFluidVariant().getFluid()).toString();
            spec.amount = Math.max(1, stack.getAmount() / (FluidConstants.BUCKET / 1000));
        }
        else if (isChemical(value)) {
            try { spec.amount = chemicalAmount(value); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot read Mekanism chemical amount", e); }
        }
        return spec;
    }

    private static ITypedIngredient<?> decodeFluid(CategoryDocument.Ingredient spec) {
        var id = Identifier.parse(spec.fluid);
        var fluid = BuiltInRegistries.FLUID.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Missing fluid: " + id));
        var patch = DataComponentPatch.EMPTY;
        if (spec.data != null && spec.data.isJsonObject() && spec.data.getAsJsonObject().has("components")) {
            patch = DataComponentPatch.CODEC.parse(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE),
                    spec.data.getAsJsonObject().get("components")).getOrThrow();
        }
        return manager.createTypedIngredient(FabricTypes.FLUID_STACK,
                new JeiFluidIngredient(FluidVariant.of(fluid, patch), nativeFluidAmount(spec.amount)), false).orElseThrow();
    }

    private static com.google.gson.JsonObject encodeFluid(IJeiFluidIngredient stack) {
        var variant = stack.getFluidVariant();
        var json = new com.google.gson.JsonObject();
        json.addProperty("id", BuiltInRegistries.FLUID.getKey(variant.getFluid()).toString());
        if (!variant.getComponentsPatch().isEmpty()) {
            json.add("components", DataComponentPatch.CODEC.encodeStart(Platform.getFrozenRegistry().createSerializationContext(JsonOps.INSTANCE),
                    variant.getComponentsPatch()).getOrThrow());
        }
        return json;
    }

    // Project amounts remain in millibuckets so NeoForge and Fabric can share the same files.
    private static long nativeFluidAmount(long millibuckets) {
        long factor = FluidConstants.BUCKET / 1000;
        return Math.min(millibuckets, Long.MAX_VALUE / factor) * factor;
    }

    private static boolean isChemical(Object value) { return value.getClass().getName().equals("mekanism.api.chemical.ChemicalStack"); }

    private static long chemicalAmount(Object stack) throws ReflectiveOperationException {
        try {
            return ((Number) stack.getClass().getMethod("amount").invoke(stack)).longValue();
        } catch (NoSuchMethodException legacyApi) {
            return ((Number) stack.getClass().getMethod("getAmount").invoke(stack)).longValue();
        }
    }

    private static Object copyChemical(Object stack, long amount) throws ReflectiveOperationException {
        try {
            return stack.getClass().getMethod("copyWithAmount", int.class).invoke(stack, (int) Math.min(Integer.MAX_VALUE, amount));
        } catch (NoSuchMethodException legacyApi) {
            return stack.getClass().getMethod("copyWithAmount", long.class).invoke(stack, amount);
        }
    }

    private static Object withAmount(Object value, long amount) {
        if (value instanceof ItemStack item) return item.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
        if (value instanceof IJeiFluidIngredient fluid) return new JeiFluidIngredient(fluid.getFluidVariant(), nativeFluidAmount(amount));
        if (isChemical(value)) {
            try { return copyChemical(value, amount); }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot restore Mekanism chemical amount", e); }
        }
        return value;
    }
}
