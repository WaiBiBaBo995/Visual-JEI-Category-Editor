package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.DummyWorld;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import dev.vfyjxf.taffy.style.AlignContent;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

final class SceneBlockImports {
    private SceneBlockImports() {}

    static void fromJei(ResourceProviderContainer<CompoundTag> container) {
        IngredientPicker.openBlocks(container, SceneBlockImports::isBlock, stack -> add(container, stack));
    }

    static void fromInventory(ResourceProviderContainer<CompoundTag> container) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            Dialog.showNotification("vjce.asset.from_inventory", "vjce.enter_world", null).show(container.getModularUI());
            return;
        }
        var inventory = player.getInventory();
        var dialog = new Dialog().setTitle("vjce.asset.from_inventory");
        dialog.setId("vjce_block_inventory");
        dialog.overlay.layout(l -> { l.width(222); l.maxWidthPercent(95); });
        for (int start : new int[]{9, 18, 27, 0}) {
            var row = Forms.row().layout(l -> {
                l.height(22); l.gapAll(1); l.justifyContent(AlignContent.CENTER);
                if (start == 0) l.marginTop(4);
            });
            for (int index = start; index < start + 9; index++) {
                var stack = inventory.getItem(index).copy();
                var button = new Button().noText();
                button.setId("vjce_block_inventory_" + index);
                button.layout(l -> { l.width(21); l.height(21); l.flexShrink(0); });
                if (!stack.isEmpty()) {
                    button.addPreIcon(new ItemStackTexture(stack));
                    button.style(s -> s.tooltips(stack.getHoverName()));
                }
                if (isBlock(stack)) button.setOnClick(e -> { dialog.close(); add(container, stack); });
                else button.setActive(false);
                row.addChild(button);
            }
            dialog.addContent(row);
        }
        dialog.addButton(new Button().setText("vjce.close").setOnClick(e -> dialog.close()));
        dialog.show(container.getModularUI());
    }

    static boolean isBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem item && !item.getBlock().defaultBlockState().isAir();
    }

    private static void add(ResourceProviderContainer<CompoundTag> container, ItemStack stack) {
        var level = Minecraft.getInstance().level;
        if (level == null || container.getModularUI() == null || !container.resourceProvider.supportAdd()) return;
        try {
            var asset = snapshot(stack, level.registryAccess());
            var id = BuiltInRegistries.BLOCK.getKey(((BlockItem) stack.getItem()).getBlock());
            container.addNewResource(asset, id.getNamespace() + "_" + id.getPath().replace('/', '_'));
            SceneAssets.changed();
        } catch (RuntimeException failure) {
            VisualJeiCategoryEditor.LOG.warn("Cannot import scene block resource from {}", stack, failure);
            Dialog.showNotification("editor.error", "vjce.asset.import_failed", null).show(container.getModularUI());
        }
    }

    static CompoundTag snapshot(ItemStack stack, RegistryAccess registries) {
        if (!isBlock(stack)) throw new IllegalArgumentException("A block item is required");
        var block = ((BlockItem) stack.getItem()).getBlock();
        var state = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).apply(block.defaultBlockState());
        var asset = new CompoundTag();
        asset.putString("assetId", UUID.randomUUID().toString());
        asset.put("state", BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow());
        var custom = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();
        if (state.hasBlockEntity()) {
            // Modded entities may require a level even when only loading or saving item data.
            var world = new DummyWorld(registries);
            try {
                world.setBlock(BlockPos.ZERO, state, Block.UPDATE_CLIENTS);
                var tile = world.getBlockEntity(BlockPos.ZERO);
                if (tile == null) throw new IllegalStateException("Cannot create block entity for " + block);
                if (custom.isEmpty()) {
                    tile.applyComponentsFromItemStack(stack);
                } else {
                    var merged = tile.saveCustomOnly(registries);
                    merged.merge(custom);
                    tile.loadCustomOnly(merged, registries);
                    // Component readers can clear fields absent from the stack. Supply decoded NBT
                    // as defaults so an explicit stack patch overrides it without erasing other fields.
                    var defaults = DataComponentMap.composite(stack.getPrototype(), tile.collectComponents());
                    tile.applyComponents(defaults, stack.getComponentsPatch());
                }
                custom = tile.saveWithoutMetadata(registries);
                try {
                    var update = tile.getUpdateTag(registries);
                    if (!update.isEmpty()) asset.put("update", update);
                } catch (RuntimeException updateFailure) {
                    // Some modded update tags require additional context. The saved data can still load.
                    VisualJeiCategoryEditor.LOG.debug("No preview update tag for {}", block, updateFailure);
                }
            } finally {
                world.removeBlockEntity(BlockPos.ZERO);
            }
        }
        asset.put("data", custom);
        return asset;
    }
}
