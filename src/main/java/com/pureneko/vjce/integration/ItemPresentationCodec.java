package com.pureneko.vjce.integration;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** 1.20.1 display items retain their complete typed NBT; amounts are stored separately. */
public final class ItemPresentationCodec {
    private ItemPresentationCodec() {}
    public static JsonElement encode(ItemStack stack) {
        if(stack.isEmpty())throw new IllegalArgumentException("Cannot save an empty item");
        var data=new JsonObject(); data.addProperty("id",BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if(stack.hasTag())data.addProperty("nbt",stack.getTag().toString());
        return data;
    }
    public static ItemStack decode(JsonElement encoded) {
        if(encoded!=null&&encoded.isJsonPrimitive()) {var data=new JsonObject();data.add("id",encoded);encoded=data;}
        if(encoded==null||!encoded.isJsonObject())throw new IllegalArgumentException("Invalid item display data");
        var data=encoded.getAsJsonObject();
        if(data.has("components"))throw new IllegalArgumentException("1.21 item components are not supported by a 1.20.1 project");
        var id=new ResourceLocation(data.get("id").getAsString());
        if(!BuiltInRegistries.ITEM.containsKey(id))throw new IllegalArgumentException("Missing item: "+id);
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(id));
        if(stack.isEmpty())throw new IllegalArgumentException("Invalid display item: "+id);
        if(data.has("nbt"))try {stack.setTag(TagParser.parseTag(data.get("nbt").getAsString()));}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Invalid item NBT",e);}
        return stack;
    }
}
