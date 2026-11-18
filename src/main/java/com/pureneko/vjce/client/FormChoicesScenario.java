package com.pureneko.vjce.client;

import com.google.gson.JsonObject;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.data.ProjectStore;
import com.pureneko.vjce.integration.*;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.fluids.FluidStack;
import mezz.jei.api.neoforge.NeoForgeTypes;
import java.util.*;

/** Exercises the form controls against the running JEI ingredient registry. */
@LDLRegisterClient(name="vjce_choices",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class FormChoicesScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready", c -> VjceJeiPlugin.runtime != null)
                .openModularUI("form choices", c -> {
                    var root = Forms.column(); root.layout(l -> l.width(440));
                    var spec = IngredientAccess.encode(IngredientAccess.manager.createTypedIngredient(VanillaTypes.ITEM_STACK, new ItemStack(Items.DIAMOND, 7)).orElseThrow());
                    c.put("original", spec); c.put("before", ProjectStore.JSON.toJson(spec));
                    Forms.ingredient(root, spec, replacement -> c.put("replacement", replacement), () -> {}, () -> {});
                    var binding = new CategoryDocument.Binding(); c.put("binding", binding);
                    Forms.object(root, binding, () -> {}, () -> {}, Set.of());
                    return new ModularUI(UI.of(root), c.player());
                }).awaitModularUI()
                .step("select a different type without committing data", c -> type(c).setSelected(NeoForgeTypes.FLUID_STACK.getUid(), true))
                .check("type selection opens a filtered picker and preserves the ingredient", c ->
                        root(c).selectId("vjce_ingredient_dialog").findAny().isPresent()
                                && ProjectStore.JSON.toJson(c.<CategoryDocument.Ingredient>get("original")).equals(c.<String>get("before"))
                                && type(c).getValue().equals(VanillaTypes.ITEM_STACK.getUid()))
                .step("press cancel", c -> press(c, "vjce_ingredient_close"))
                .step("release cancel", FormChoicesScenario::release)
                .check("cancelling does not replace or clear the ingredient", c -> c.get("replacement") == null
                        && ProjectStore.JSON.toJson(c.<CategoryDocument.Ingredient>get("original")).equals(c.<String>get("before")))
                .step("select fluid type again", c -> type(c).setSelected(NeoForgeTypes.FLUID_STACK.getUid(), true))
                .typeInto("#vjce_ingredient_search_text", "minecraft:water")
                .waitUntil("water result appears", c -> root(c).selectId("vjce_ingredient_cell").findAny().isPresent())
                .check("the filtered picker displays only fluid ingredients", c -> root(c).selectId("vjce_ingredient_dialog").findFirst().orElseThrow()
                        .select("*").filter(IngredientSlot.class::isInstance).map(IngredientSlot.class::cast)
                        .allMatch(slot -> slot.ingredient.getType() == NeoForgeTypes.FLUID_STACK))
                .screenshot("ingredient-type-fluid-picker")
                .step("press an ingredient result", c -> press(c, "vjce_ingredient_cell"))
                .step("release the ingredient result", FormChoicesScenario::release)
                .check("choosing commits a matching type and payload", c -> {
                    var result = c.<CategoryDocument.Ingredient>get("replacement");
                    return result != null && result.type.equals(NeoForgeTypes.FLUID_STACK.getUid())
                            && IngredientAccess.decode(result).getFirst().getIngredient() instanceof FluidStack;
                })
                .step("select a fixed binding property", c -> property(c).setSelected("color", true))
                .check("binding choices preserve canonical save values and free expressions", c ->
                        property(c).getCandidates().equals(Forms.BINDING_PROPERTIES)
                                && c.<CategoryDocument.Binding>get("binding").property.equals("color")
                                && root(c).selectId("edit_expression").findFirst().orElseThrow() instanceof TextField)
                .step("load an unavailable ingredient type", c -> {
                    var spec = new CategoryDocument.Ingredient(); spec.type = "absent:ingredient";
                    spec.data = new JsonObject(); spec.data.getAsJsonObject().addProperty("keep", 42);
                    c.put("missing", spec); c.put("missingBefore", ProjectStore.JSON.toJson(spec));
                    root(c).clearAllChildren();
                    Forms.ingredient(root(c), spec, replacement -> c.put("missingReplacement", replacement), () -> {}, () -> {});
                })
                .check("an unavailable old type remains selected and labelled", c -> type(c).getValue().equals("absent:ingredient")
                        && type(c).getCandidates().contains("absent:ingredient")
                        && ProjectStore.JSON.toJson(c.<CategoryDocument.Ingredient>get("missing")).equals(c.<String>get("missingBefore")))
                .screenshot("ingredient-type-unavailable")
                .step("choose a loaded type for the missing ingredient", c -> type(c).setSelected(VanillaTypes.ITEM_STACK.getUid(), true))
                .step("press cancel for an unavailable type", c -> press(c, "vjce_ingredient_close"))
                .step("release cancel for an unavailable type", FormChoicesScenario::release)
                .check("cancelling preserves an unavailable type and its payload", c -> c.get("missingReplacement") == null
                        && type(c).getValue().equals("absent:ingredient")
                        && ProjectStore.JSON.toJson(c.<CategoryDocument.Ingredient>get("missing")).equals(c.<String>get("missingBefore")))
                .closeScreen();
    }
    private static UIElement root(TestContext c) { return c.requireUI().ui.rootElement; }
    private static void press(TestContext c, String id) {
        var point = ElementBounds.of(root(c).selectId(id).findFirst().orElseThrow()).center();
        c.put("clickPoint", point); c.input().mouseDown(point.x, point.y, 0);
    }
    private static void release(TestContext c) {
        var point = c.<org.joml.Vector2f>get("clickPoint"); c.input().mouseUp(point.x, point.y, 0);
    }
    @SuppressWarnings("unchecked") private static Selector<String> type(TestContext c) { return (Selector<String>) root(c).selectId("edit_type").findFirst().orElseThrow(); }
    @SuppressWarnings("unchecked") private static Selector<String> property(TestContext c) { return (Selector<String>) root(c).selectId("edit_property").findFirst().orElseThrow(); }
}
