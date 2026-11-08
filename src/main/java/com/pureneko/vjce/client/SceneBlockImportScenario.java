package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.resource.IResourcePath;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;

@LDLRegisterClient(name="vjce_block_import",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class SceneBlockImportScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("block resource imports",c->{
            var inventory=c.player().getInventory();
            c.put("oldItem",inventory.getItem(9).copy());
            c.put("oldHotbar",inventory.getItem(0).copy());
            var stack=new ItemStack(Items.PURPLE_SHULKER_BOX,2);
            stack.set(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY.with(ShulkerBoxBlock.FACING,Direction.EAST));
            var data=new CompoundTag(); data.putString("Lock","qa-key");
            stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(data));
            stack.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND,3))));
            inventory.setItem(9,stack);
            inventory.setItem(0,new ItemStack(Items.APPLE));
            c.put("fixtureItem",stack.copy());
            c.put("addedPaths",new ArrayList<IResourcePath>());
            var window=new EditorWindow(VjceEditor::new);
            var editor=window.getCurrentEditor();
            editor.loadProject(new VjceProject(),null);
            c.put("importEditor",editor);
            return new ModularUI(UI.of(window),c.player());
        }).awaitModularUI().step("open writable block resources",c->{
            var editor=editor(c);
            editor.resourceView.selectResourceInstance(SceneAssetResource.BLOCKS);
            var view=editor.resourceView.select("*").filter(ResourceContainer.class::isInstance).map(ResourceContainer.class::cast)
                    .filter(v->v.resourceInstance.resource==SceneAssetResource.BLOCKS).findFirst().orElseThrow();
            view.selectProvider(SceneAssetResource.BLOCKS.getResourceInstance().listWritableProviders().getFirst());
            @SuppressWarnings("unchecked") var container=(ResourceProviderContainer<CompoundTag>)view.getSelectedProviderContainer();
            c.put("importProvider",container);
        });
        menu(s,"vjce.asset.from_inventory");
        s.check("inventory picker retains hotbar position and rejects non-blocks",c->root(c).selectId("vjce_block_inventory_9").findFirst().orElseThrow().isActive()
                && !root(c).selectId("vjce_block_inventory_0").findFirst().orElseThrow().isActive())
                .screenshot("block_inventory_picker");
        pick(s,"vjce_block_inventory_9");
        s.step("inventory import restores state and two data sources on a scene tile",c->{
                    var value=remember(c);
                    var scene=new BlockSceneElement(); var rows=new ListTag(); rows.add(value.copy()); scene.blocks=new CompoundTag(); scene.blocks.put("blocks",rows);
                    try {
                        scene.ensureScene();
                        c.check("block ID is used as resource name",provider(c).resourceProvider.getResourceName(provider(c).getSelected()).startsWith("minecraft_purple_shulker_box"));
                        c.check("block state component restores east orientation",scene.getDummyWorld().getBlockState(BlockPos.ZERO).getValue(ShulkerBoxBlock.FACING)==Direction.EAST);
                        var tile=(ShulkerBoxBlockEntity)scene.getDummyWorld().getBlockEntity(BlockPos.ZERO);
                        c.check("block entity restores raw tag and implicit contents",tile.saveWithoutMetadata(c.player().level().registryAccess()).getString("Lock").equals("qa-key")
                                && tile.getItem(0).is(Items.DIAMOND) && tile.getItem(0).getCount()==3);
                    } finally { scene.release(); }
                    c.check("inventory stack and components were not consumed or changed",ItemStack.matches(c.player().getInventory().getItem(9),c.get("fixtureItem")));
                });
        menu(s,"vjce.asset.from_inventory");
        pick(s,"vjce_block_inventory_9");
        s.check("duplicate imports use distinct resource names and identities",c->{
                    var value=remember(c);
                    var paths=c.<List<IResourcePath>>get("addedPaths");
                    return !paths.getFirst().equals(paths.getLast())
                            && !provider(c).resourceProvider.getResource(paths.getFirst()).getString("assetId").equals(value.getString("assetId"));
                });
        menu(s,"vjce.asset.from_jei");
        s.waitUntil("JEI block grid mounted",c->root(c).selectId("vjce_ingredient_cell").count()>2)
                .check("block picker omits non-blocks and the ingredient type selector",c->root(c).selectId("vjce_ingredient_type").findAny().isEmpty()
                        && root(c).selectId("vjce_ingredient_cell").flatMap(cell->cell.select("*")).filter(IngredientSlot.class::isInstance)
                        .map(IngredientSlot.class::cast).allMatch(slot->slot.ingredient.getItemStack().filter(SceneBlockImports::isBlock).isPresent()))
                .typeInto("#vjce_ingredient_search_text","minecraft:purpur_block")
                .waitUntil("filtered block cell visible",c->root(c).selectId("vjce_ingredient_cell").count()==1)
                .screenshot("block_jei_picker");
        pick(s,"vjce_ingredient_cell");
        s.check("JEI click creates purpur resource",c->remember(c).getCompound("state").getString("Name").equals("minecraft:purpur_block"));
        menu(s,"vjce.asset.default_block");
        s.check("default resource creation is still available",c->remember(c).getCompound("state").getString("Name").equals("minecraft:stone"))
                .step("check modded block resource conversion if present",c->{
                    var id=ResourceLocation.parse("mekanism:basic_fluid_tank");
                    if (BuiltInRegistries.ITEM.containsKey(id)) {
                        var data=SceneBlockImports.snapshot(new ItemStack(BuiltInRegistries.ITEM.get(id)),c.player().level().registryAccess());
                        c.check("Mekanism tank exports to a block resource",data.getCompound("state").getString("Name").equals(id.toString()));
                    }
                })
                .closeScreen();
        s.teardown("restore inventory and remove only newly added resources",c->{
            if(c.get("oldItem")!=null)c.player().getInventory().setItem(9,c.get("oldItem"));
            if(c.get("oldHotbar")!=null)c.player().getInventory().setItem(0,c.get("oldHotbar"));
            if(provider(c)!=null)for(var path:c.<List<IResourcePath>>get("addedPaths"))provider(c).resourceProvider.removeResource(path);
            SceneAssets.changed();
        });
    }

    private static void pick(ScenarioBuilder s,String id) {
        s.step("aim "+id,c->{var point=ElementBounds.of(root(c).selectId(id).findFirst().orElseThrow()).center();c.put("menuPoint",point);c.input().moveTo(point.x,point.y);})
                .step("pick "+id,c->{var point=c.<Vector2f>get("menuPoint");c.input().mouseDown(point.x,point.y,0);c.input().mouseUp(point.x,point.y,0);});
    }

    private static void menu(ScenarioBuilder s,String entry) {
        s.step("aim at block resource browser",c->{var point=ElementBounds.of(provider(c).scrollerView).center();c.put("menuPoint",point);c.input().moveTo(point.x,point.y);})
                .step("open block resource menu",c->{var point=c.<Vector2f>get("menuPoint");c.input().mouseDown(point.x,point.y,1);c.input().mouseUp(point.x,point.y,1);})
                .step("hover add-resource branch",c->{var point=menuPoint(c,"ldlib.gui.editor.menu.add_resource");c.input().moveTo(point.x,point.y);})
                .step("aim "+entry,c->{var point=menuPoint(c,entry);c.put("menuPoint",point);c.input().moveTo(point.x,point.y);})
                .step("pick "+entry,c->{var point=c.<Vector2f>get("menuPoint");c.input().mouseDown(point.x,point.y,0);c.input().mouseUp(point.x,point.y,0);});
    }

    private static Vector2f menuPoint(TestContext c,String key) {
        var text=Component.translatable(key).getString();
        var label=root(c).select("*").filter(TextElement.class::isInstance).map(TextElement.class::cast)
                .filter(element->element.getText().getString().equals(text)).findFirst().orElseThrow();
        return ElementBounds.of(label).center();
    }

    private static Editor editor(TestContext c) { return c.get("importEditor"); }
    private static UIElement root(TestContext c) { return editor(c).getModularUI().ui.rootElement; }
    private static ResourceProviderContainer<CompoundTag> provider(TestContext c) { return c.get("importProvider"); }

    private static CompoundTag remember(TestContext c) {
        var path=provider(c).getSelected();
        c.<List<IResourcePath>>get("addedPaths").add(path);
        return provider(c).resourceProvider.getResource(path);
    }
}
