package com.pureneko.vjce.client;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.IngredientAccess;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.ingredients.IIngredientType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;
import static com.pureneko.vjce.client.PortValidation.check;

/** Runs selection callbacks in a real client with its registered JEI types. */
final class FormChoicesValidation {
    private static DialogWidget dialog;
    private static Forms.Form form;
    private static SelectorWidget type;
    private static CategoryDocument.Ingredient original, replacement;
    private static String before;

    static boolean step(int phase,VjceEditor editor)throws Exception {
        switch(phase){
            case 55 -> {
                dialog=new DialogWidget(editor.getGui().mainGroup,true);
                form=new Forms.Form(8,8,300,editor.getSize().height-16);dialog.addWidget(form);
                original=IngredientAccess.encode(IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK,new ItemStack(Items.DIAMOND,7)).orElseThrow());
                before=ProjectStore.JSON.toJson(original);
                form.ingredient(original,value->replacement=value,()->{},()->{});
                type=selectors().get(0);
                check("legacy item and tag resolve to the registered item type",Forms.ingredientType(legacyItem()).equals(VanillaTypes.ITEM_STACK.getUid()));
                check("type field uses the registered JEI UID",type.getValue().equals(VanillaTypes.ITEM_STACK.getUid()));
                var binding=new CategoryDocument.Binding();form.object(binding,Set.of(),()->{},()->{});
                var property=selectors().get(1);select(property,Forms.BINDING_PROPERTIES.indexOf("color"));
                check("binding dropdown saves a canonical property",binding.property.equals("color"));
                var slot=new CategoryDocument.Slot();form.object(slot,Set.of("ingredients"),()->{},()->{});
                select(selectors().get(2),Forms.ROLES.indexOf("OUTPUT"));
                check("role dropdown saves a canonical role",slot.role.equals("OUTPUT"));
                selectType(FabricTypes.FLUID_STACK);
                check("type selection leaves payload unchanged until a result is chosen",replacement==null&&ProjectStore.JSON.toJson(original).equals(before)&&type.getValue().equals(VanillaTypes.ITEM_STACK.getUid()));
            }
            case 56 -> {
                var picker=picker(editor);
                check("type selector filters the ingredient picker",!picker.values.isEmpty()&&picker.values.stream().allMatch(v->v.getType()==FabricTypes.FLUID_STACK));
                PortValidation.screenshot("ingredient-type-fluid-picker");
                picker.close();
                check("cancelling type selection keeps the original ingredient",replacement==null&&ProjectStore.JSON.toJson(original).equals(before));
                selectType(FabricTypes.FLUID_STACK);
            }
            case 57 -> {
                var picker=picker(editor);
                var cell=(IngredientPicker.IngredientCell)picker.results.widgets.get(0);
                var p=cell.getPosition();cell.mouseClicked(p.x+4,p.y+4,0);
                check("choosing a result replaces both type and payload",replacement!=null&&replacement.type.equals(FabricTypes.FLUID_STACK.getUid())&&IngredientAccess.decode(replacement).get(0).getType()==FabricTypes.FLUID_STACK);
                form.clear();original=new CategoryDocument.Ingredient();original.type="absent:ingredient";original.data=new JsonObject();original.data.getAsJsonObject().addProperty("keep",42);replacement=null;
                before=ProjectStore.JSON.toJson(original);form.ingredient(original,value->replacement=value,()->{},()->{});type=selectors().get(0);
                check("an unavailable old type remains labelled",type.getValue().equals(Component.translatable("vjce.choice.unavailable",original.type).getString()));
            }
            case 58 -> {
                PortValidation.screenshot("ingredient-type-unavailable");
                selectType(VanillaTypes.ITEM_STACK);
            }
            case 59 -> {
                picker(editor).close();
                check("cancelling preserves an unavailable type and its payload",replacement==null&&ProjectStore.JSON.toJson(original).equals(before));
                check("cancelling restores the unavailable type label",type.getValue().equals(Component.translatable("vjce.choice.unavailable",original.type).getString()));
                dialog.close();return true;
            }
        }
        return false;
    }
    private static CategoryDocument.Ingredient legacyItem(){var value=new CategoryDocument.Ingredient();value.tag="minecraft:planks";return value;}
    private static List<SelectorWidget> selectors(){return form.widgets.stream().filter(SelectorWidget.class::isInstance).map(SelectorWidget.class::cast).toList();}
    private static void selectType(IIngredientType<?> chosen){select(type,new ArrayList<>(IngredientAccess.manager.getRegisteredIngredientTypes()).indexOf(chosen));}
    private static void select(SelectorWidget selector,int index){
        selector.setShow(true);
        var popup=(DraggableScrollableWidgetGroup)selector.widgets.stream().filter(DraggableScrollableWidgetGroup.class::isInstance).findFirst().orElseThrow();
        ((SelectableWidgetGroup)popup.widgets.get(index)).onSelected();
    }
    private static IngredientPicker.PickerDialog picker(VjceEditor editor){return (IngredientPicker.PickerDialog)editor.getGui().mainGroup.widgets.stream().filter(IngredientPicker.PickerDialog.class::isInstance).findFirst().orElseThrow();}
}
