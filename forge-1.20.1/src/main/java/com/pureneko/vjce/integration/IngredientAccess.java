package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;

import com.mojang.serialization.JsonOps;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.forge.ForgeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
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
                    : decodeOther(type, spec.data);
            value = withAmount(value, spec.amount);
            return List.of((ITypedIngredient<?>) manager.createTypedIngredient(type, value).orElseThrow());
        }
        var result = new ArrayList<ITypedIngredient<?>>();
        if (!spec.tag.isBlank()) {
            var id = new ResourceLocation(spec.tag);
            if (!spec.fluid.isBlank()) {
                BuiltInRegistries.FLUID.getTag(TagKey.create(Registries.FLUID, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(ForgeTypes.FLUID_STACK, new FluidStack(holder.value(), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add)));
            } else {
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).ifPresent(set -> set.forEach(holder ->
                        manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(holder, (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add)));
            }
        } else if (!spec.item.isBlank()) {
            var id = new ResourceLocation(spec.item);
            if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Missing item: " + id);
            manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BuiltInRegistries.ITEM.get(id), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add);
        } else if (!spec.fluid.isBlank()) {
            var id = new ResourceLocation(spec.fluid);
            if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalArgumentException("Missing fluid: " + id);
            manager.createTypedIngredient(ForgeTypes.FLUID_STACK, new FluidStack(BuiltInRegistries.FLUID.get(id), (int) Math.min(Integer.MAX_VALUE, spec.amount))).ifPresent(result::add);
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
                    : encodeOther(type, value);
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

    @SuppressWarnings({"unchecked","rawtypes"})
    private static com.google.gson.JsonElement encodeOther(IIngredientType type,Object value) {
        var data=new com.google.gson.JsonObject();
        if(value instanceof FluidStack fluid) data.addProperty("nbt",fluid.writeToNBT(new net.minecraft.nbt.CompoundTag()).toString());
        else data.addProperty("uid",manager.getIngredientHelper(type).getUniqueId(value,mezz.jei.api.ingredients.subtypes.UidContext.Ingredient));
        return data;
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static Object decodeOther(IIngredientType type,com.google.gson.JsonElement encoded) {
        var data=encoded.getAsJsonObject();
        if(type==ForgeTypes.FLUID_STACK)try {return FluidStack.loadFluidStackFromNBT(net.minecraft.nbt.TagParser.parseTag(data.get("nbt").getAsString()));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Invalid fluid NBT",e);}
        var uid=data.get("uid").getAsString();
        var typed=manager.getTypedIngredientByUid(type,uid).orElse(null);if(typed==null)throw new IllegalArgumentException("Missing JEI ingredient: "+uid);
        return manager.getIngredientHelper(type).copyIngredient(((ITypedIngredient)typed).getIngredient());
    }
    private static boolean isChemical(Object value) { return value.getClass().getName().startsWith("mekanism.api.chemical.") && value.getClass().getSimpleName().endsWith("Stack"); }
    private static Object withAmount(Object value, long amount) {
        if (value instanceof ItemStack item) return item.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
        if (value instanceof FluidStack fluid) { var copy = fluid.copy(); copy.setAmount((int) Math.min(Integer.MAX_VALUE, amount)); return copy; }
        if (isChemical(value)) {
            try { var copy=value.getClass().getMethod("copy").invoke(value); copy.getClass().getMethod("setAmount",long.class).invoke(copy,amount); return copy; }
            catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot restore Mekanism chemical amount", e); }
        }
        return value;
    }
}
