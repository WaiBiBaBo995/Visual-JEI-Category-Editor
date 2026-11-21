package com.pureneko.vjce.integration;
import com.pureneko.vjce.data.*;
import net.minecraft.world.item.*;
import net.minecraft.nbt.*;
import mezz.jei.api.constants.VanillaTypes;
import java.util.function.BiConsumer;
public final class CompatibilityAudit {
    public static boolean enabled(){return net.fabricmc.loader.api.FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("vjce.compatTest");}
    public static void register(mezz.jei.api.registration.IRecipeRegistration registration){}
    public static void run(BiConsumer<String,Boolean> check){
        var stack=new ItemStack(Items.DIAMOND_PICKAXE,7);stack.setDamageValue(23);stack.setHoverName(net.minecraft.network.chat.Component.literal("VJCE NBT regression"));
        var data=new CompoundTag();data.putByte("byte",(byte)3);data.putInt("int",3);data.putLong("long",3);data.putByteArray("bytes",new byte[]{1,2,3});data.putIntArray("ints",new int[]{1,2,3});data.putLongArray("longs",new long[]{1,2,3});stack.getOrCreateTag().put("vjce",data);
        var typed=IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK,stack).orElseThrow();var encoded=IngredientAccess.encode(typed);var saved=ProjectStore.JSON.toJson(encoded);var restored=ProjectStore.JSON.fromJson(saved,CategoryDocument.Ingredient.class);var decoded=(ItemStack)IngredientAccess.decode(restored).get(0).getIngredient();
        check.accept("item NBT round trip",ItemStack.isSameItemSameTags(stack,decoded));check.accept("item count round trip",decoded.getCount()==7);check.accept("NBT numeric types preserved",data.equals(decoded.getTagElement("vjce")));
        var large=IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK,new ItemStack(Items.STONE,128)).orElseThrow();check.accept("large display amount",((ItemStack)IngredientAccess.decode(IngredientAccess.encode(large)).get(0).getIngredient()).getCount()==128);
    }
}
