package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;
import java.util.function.Consumer;
final class SceneBlockImports {
    static boolean isBlock(ItemStack stack){return !stack.isEmpty()&&stack.getItem() instanceof BlockItem item&&!item.getBlock().defaultBlockState().isAir();}
    static void fromInventory(WidgetGroup host,Consumer<CompoundTag> selected){var mc=Minecraft.getInstance();if(mc.player==null)return;var dialog=new DialogWidget(host.getGui().mainGroup,true);var form=new Forms.Form(10,35,220,200);dialog.addWidget(form);for(var stack:mc.player.getInventory().items)if(isBlock(stack))form.button(stack.getHoverName().getString(),()->{selected.accept(snapshot(stack,mc.level.registryAccess()));dialog.close();});form.button("vjce.close",dialog::close);}
    static CompoundTag snapshot(ItemStack stack,RegistryAccess registries){if(!isBlock(stack))throw new IllegalArgumentException("A block item is required");var block=((BlockItem)stack.getItem()).getBlock();var state=block.defaultBlockState();var tag=stack.getTagElement("BlockStateTag");if(tag!=null)for(String name:tag.getAllKeys()){var property=state.getBlock().getStateDefinition().getProperty(name);if(property!=null)state=property(state,property,tag.getString(name));}var data=stack.getTagElement("BlockEntityTag");var asset=new CompoundTag();asset.putString("assetId",UUID.randomUUID().toString());asset.put("state",BlockState.CODEC.encodeStart(NbtOps.INSTANCE,state).getOrThrow(false,m->{}));asset.put("data",data==null?new CompoundTag():data.copy());return asset;}
    private static <T extends Comparable<T>> BlockState property(BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value){return property.getValue(value).map(v->state.setValue(property,v)).orElse(state);}
}
