package com.pureneko.vjce.integration;

import com.google.gson.*;
import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;

/** Lossless display data. JEI's strict item codec also validates gameplay component combinations. */
public final class ItemPresentationCodec {
    private ItemPresentationCodec() {}

    public static JsonElement encode(ItemStack stack) {
        var provider = Platform.getFrozenRegistry();
        var data = new JsonObject();
        data.add("id", ItemStack.ITEM_NON_AIR_CODEC.encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), stack.getItemHolder()).getOrThrow());
        if (!stack.getComponentsPatch().isEmpty()) {
            // SNBT retains byte/int/long and array types in custom data, as JEI's codec does.
            var patch = DataComponentPatch.CODEC.encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack.getComponentsPatch()).getOrThrow();
            data.addProperty("components", patch.toString());
        }
        // Count lives in CategoryDocument.Ingredient.amount, independently of stack limits.
        return data;
    }

    public static ItemStack decode(JsonElement encoded) {
        if (encoded != null && encoded.isJsonPrimitive() && encoded.getAsJsonPrimitive().isString()) {
            var legacy = new JsonObject();
            legacy.add("id", encoded);
            encoded = legacy;
        }
        if (encoded == null || !encoded.isJsonObject()) throw new IllegalArgumentException("Invalid item display data");
        var provider = Platform.getFrozenRegistry();
        var data = encoded.getAsJsonObject();
        var holder = ItemStack.ITEM_NON_AIR_CODEC.parse(provider.createSerializationContext(JsonOps.INSTANCE), data.get("id")).getOrThrow();
        var patch = DataComponentPatch.EMPTY;
        if (data.has("components")) {
            var components = data.get("components");
            if (components.isJsonPrimitive() && components.getAsJsonPrimitive().isString()) {
                try {
                    patch = DataComponentPatch.CODEC.parse(provider.createSerializationContext(NbtOps.INSTANCE), TagParser.parseTag(components.getAsString())).getOrThrow();
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new IllegalArgumentException("Invalid item component SNBT", e);
                }
            } else {
                patch = DataComponentPatch.CODEC.parse(provider.createSerializationContext(JsonOps.INSTANCE), components).getOrThrow();
            }
        }
        return new ItemStack(holder, 1, patch);
    }
}
