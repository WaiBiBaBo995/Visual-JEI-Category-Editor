package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.integration.*;
import mezz.jei.api.ingredients.*;
import org.joml.Vector2f;

@LDLRegisterClient(name="vjce_data",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class RecipeDataScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("recipe data",c->{
            var w=new EditorWindow(VjceEditor::new);var p=VjceEditor.projectForCategory("minecraft:smelting");w.getCurrentEditor().loadProject(p,null);c.put("p",p);p.dataView.getViewContainer().selectView(p.dataView);return new ModularUI(UI.of(w),c.player());
        }).awaitModularUI().check("sidebar and details do not overlap",c->{var b=p(c).dataView.selectId("vjce_recipe_sidebar").findFirst().orElseThrow();var d=p(c).dataView.selectId("vjce_recipe_details").findFirst().orElseThrow();return b.getPositionX()+b.getSizeWidth()<=d.getPositionX()+1;})
        .step("JEI registers source, legacy, and component catalyst entries", c -> {
            var type = VjceJeiPlugin.originals.get("minecraft:smelting").getRecipeType();
            var stacks = VjceJeiPlugin.runtime.getRecipeManager().createRecipeCatalystLookup(type).includeHidden().getItemStack().toList();
            c.check("existing category keeps furnace catalyst", stacks.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.FURNACE)));
            c.check("legacy ID catalyst registers", stacks.stream().anyMatch(stack -> stack.is(net.minecraft.world.item.Items.CRAFTING_TABLE)));
            c.check("same-item potions register as distinct catalysts", stacks.stream().filter(stack -> stack.is(net.minecraft.world.item.Items.POTION))
                    .map(stack -> stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS)).distinct().count() == 2);
        })
        .check("current recipe has green theme",c->p(c).dataView.selectId("vjce_recipe_"+p(c).selected.id).findFirst().orElseThrow().getClasses().contains("__confirm-button__"))
        .check("empty alternative has localization",c->!net.minecraft.client.resources.language.I18n.get("vjce.field.empty").equals("vjce.field.empty"))
        .step("collapse native inspector",c->inspector(c).getViewContainer().collapse())
        .click("#vjce_recipe_slot_0")
        .check("slot is green and its form lives in the right inspector",c->green(c,0)
                && p(c).dataView.selectId("edit_chance").findAny().isEmpty()
                && inspector(c).selectId("edit_chance").findAny().isPresent()
                && !inspector(c).getViewContainer().isCollapse()
                && inspector(c).getPositionX()>p(c).dataView.getPositionX())
        .check("slot removal uses red theme",c->inspector(c).selectId("vjce_delete_slot_0").findFirst().orElseThrow().getClasses().contains("__reject-button__"))
        .check("slot shows identifier and omits legacy layout fields", c -> inspector(c).selectId("edit_identifier").findAny().isPresent()
                && inspector(c).selectId("edit_x").findAny().isEmpty() && inspector(c).selectId("edit_y").findAny().isEmpty()
                && inspector(c).selectId("edit_invisible").findAny().isEmpty())
        .step("refresh tree",c->p(c).dataView.refresh())
        .check("selection and ingredient editor survive refresh",c->green(c,0)&&inspector(c).selectId("vjce_choose_ingredient").findAny().isPresent())
        .typeInto("#vjce_slot_inspector #edit_name","qa_slot")
        .step("change slot role",c->{
            c.put("inspectedSlot",p(c).selected.slots.getFirst());
            @SuppressWarnings("unchecked") var role=(Selector<String>)inspector(c).selectId("edit_role").findFirst().orElseThrow();
            role.setSelected("CATALYST",true);
        })
        .check("renamed and regrouped slot stays selected",c->p(c).selected.slots.getFirst()==c.get("inspectedSlot")
                && p(c).selected.slots.getFirst().name.contains("qa_slot") && p(c).selected.slots.getFirst().role.equals("CATALYST") && green(c,0));
        pick(s,"vjce_choose_ingredient");
        s.typeInto("#vjce_ingredient_search_text","minecraft:amethyst_shard");
        pick(s,"vjce_ingredient_cell");
        s.check("choosing a replacement updates the selected recipe slot",c->green(c,0)
                && p(c).selected.slots.getFirst().ingredients.getFirst().item.equals("minecraft:amethyst_shard"))
        .screenshot("recipe_slot_inspector");
        s.step("aim another original recipe",c->{var button=p(c).dataView.select("*").filter(e->e.getId().startsWith("vjce_source_")&&!e.getId().equals("vjce_source_"+p(c).selected.source)).findFirst().orElseThrow();c.put("sourceButton",button.getId());var point=ElementBounds.of(button).center();c.put("sourcePoint",point);c.input().moveTo(point.x,point.y);})
        .step("press original recipe",c->{var v=c.<Vector2f>get("sourcePoint");c.input().mouseDown(v.x,v.y,0);})
        .step("release original recipe",c->{var v=c.<Vector2f>get("sourcePoint");c.input().mouseUp(v.x,v.y,0);})
        .check("source recipe click changes current settings and turns green",c->c.<String>get("sourceButton").equals("vjce_source_"+p(c).selected.source)&&p(c).dataView.selectId(c.<String>get("sourceButton")).findFirst().orElseThrow().getClasses().contains("__confirm-button__"))
        .check("switching recipe clears slot selection",c->!green(c,0)&&inspector(c).selectId("vjce_slot_inspector").findAny().isEmpty())
        .click("#vjce_recipe_slot_0")
        .step("another inspection takes ownership",c->{var inspection=IConfigurable.create(group->group.addChild(new Label().setText("Resource")));c.put("otherInspection",inspection);inspector(c).inspect(inspection);})
        .check("other inspection clears only the slot selection",c->!green(c,0)&&inspector(c).inspector.getInspectedConfigurable()==c.get("otherInspection"))
        .click("#vjce_recipe_slot_0")
        .step("switch to layout tab",c->p(c).layoutView.getViewContainer().selectView(p(c).layoutView))
        .check("leaving recipe data clears its own inspection",c->!green(c,0)&&inspector(c).inspector.getInspectedConfigurable()==null)
        .step("return to recipe data",c->{var data=p(c).dataView;data.getViewContainer().selectView(data);})
        .step("remember slot count and shared layout",c->{c.put("slotCount",p(c).selected.slots.size());c.put("sharedLayout",p(c).layoutView.getCurrentUI().rootElement);});
        pick(s,"vjce_add_slot_INPUT");
        s.check("adding a slot inspects it immediately",c->{int index=c.get("slotCount");return green(c,index)&&inspector(c).selectId("vjce_delete_slot_"+index).findAny().isPresent();})
        .step("aim delete slot",c->{c.put("addedPageSlot", p(c).selected.slotIdentifier(c.<Integer>get("slotCount")));var p=ElementBounds.of(inspector(c).selectId("vjce_delete_slot_"+c.<Integer>get("slotCount")).findFirst().orElseThrow()).center();c.put("deletePoint",p);c.input().moveTo(p.x,p.y);})
        .step("delete slot",c->{var p=c.<Vector2f>get("deletePoint");c.input().mouseDown(p.x,p.y,0);c.input().mouseUp(p.x,p.y,0);})
        .check("deleting a slot clears the inspector",c->p(c).selected.slots.size()==c.<Integer>get("slotCount")&&inspector(c).selectId("vjce_slot_inspector").findAny().isEmpty())
        .check("deleting data leaves the shared page slot and layout editor intact", c -> {
            var root = p(c).layoutView.getCurrentUI().rootElement;
            return root == c.get("sharedLayout") && root.selectId(c.<String>get("addedPageSlot")).findAny().isPresent();
        });
        picker(s,"items","item_stack");picker(s,"fluids","fluid_stack");picker(s,"chemicals","ChemicalStack");
        s.closeScreen();
    }
    private static VjceProject p(TestContext c){return c.get("p");}
    private static void pick(ScenarioBuilder s,String id){
        s.step("aim "+id,c->{var element=p(c).dataView.getModularUI().ui.rootElement.selectId(id).findFirst().orElseThrow();var point=ElementBounds.of(element).center();c.put("pickPoint",point);c.input().moveTo(point.x,point.y);})
        .step("pick "+id,c->{var point=c.<Vector2f>get("pickPoint");c.input().mouseDown(point.x,point.y,0);c.input().mouseUp(point.x,point.y,0);});
    }
    private static com.lowdragmc.lowdraglib2.editor.ui.view.InspectorView inspector(TestContext c){return p(c).dataView.getEditor().inspectorView;}
    private static boolean green(TestContext c,int index){return p(c).dataView.selectId("vjce_recipe_slot_"+index).findFirst().map(e->e.getClasses().contains("__confirm-button__")).orElse(false);}
    @SuppressWarnings({"unchecked","rawtypes"})private static void picker(ScenarioBuilder s,String name,String uid){
        s.step("open "+name+" picker",c->{c.put("chosen",false);IngredientPicker.open(p(c).dataView,typed->{c.put("chosen",true);c.put("ingredient",typed);});
            var desired=IngredientAccess.manager.getRegisteredIngredientTypes().stream().filter(t->t.getUid().contains(uid)).findFirst().orElse(null);c.put("hasType",desired!=null);
            var type=(Selector<IIngredientType<?>>)p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_type").findFirst().orElseThrow();if(desired!=null)type.setSelected(desired,true);
        }).waitUntil(name+" grid mounted",c->p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_cell").count()>1)
        ;
        if(name.equals("items"))s.typeInto("#vjce_ingredient_search_text","minecraft:stone")
            .check("typed SearchComponent query filters the grid",c->{var grid=p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_grid").findFirst().orElseThrow();var cells=grid.select("*").filter(IngredientSlot.class::isInstance).map(IngredientSlot.class::cast).toList();return !cells.isEmpty()&&cells.stream().allMatch(cell->{var typed=cell.ingredient;var helper=IngredientAccess.manager.getIngredientHelper((IIngredientType)typed.getType());return helper.getResourceLocation(typed.getIngredient()).toString().contains("minecraft:stone");});})
            .screenshot("ingredient_search_results");
        s.check(name+" uses SearchComponent",c->p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_search").findFirst().orElseThrow() instanceof SearchComponent)
        .check(name+" cells are tiled",c->{var cells=p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_cell").toList();return Math.abs(cells.get(0).getPositionY()-cells.get(1).getPositionY())<1&&cells.get(1).getPositionX()>cells.get(0).getPositionX();})
        .screenshot(name+"_grid")
        .step("aim "+name+" cell",c->{var cell=p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_cell").findFirst().orElseThrow();var v=ElementBounds.of(cell).center();c.put("click",v);c.input().moveTo(v.x,v.y);})
        .step("press "+name+" cell",c->{var v=c.<Vector2f>get("click");c.input().mouseDown(v.x,v.y,0);})
        .step("release "+name+" cell",c->{var v=c.<Vector2f>get("click");c.input().mouseUp(v.x,v.y,0);})
        .check(name+" click selects and closes modal",c->c.<Boolean>get("chosen")&&p(c).dataView.getModularUI().ui.rootElement.selectId("vjce_ingredient_dialog").findAny().isEmpty());
    }
}
