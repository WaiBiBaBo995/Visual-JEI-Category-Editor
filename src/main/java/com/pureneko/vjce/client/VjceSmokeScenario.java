package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import java.nio.file.Files;

@LDLRegisterClient(name = "vjce_smoke", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class VjceSmokeScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.defaultTimeoutMs(30000).scenarioTimeoutMs(180000); }
    @Override public void define(ScenarioBuilder steps) {
        steps.waitUntil("JEI runtime is ready", ctx -> VjceJeiPlugin.runtime != null)
                .step("audit twelve real vanilla category layouts and ingredient codecs", ctx -> {
                    try {
                        var path = RuntimeAudit.run();
                        var report = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                        ctx.check("all twelve vanilla categories pass layout and codec audit: " + report.get("errors"), report.get("passed").getAsBoolean());
                    } catch (Exception e) { throw new IllegalStateException(e); }
                })
                .openModularUI("vjce_editor", ctx -> {
                    var window = new EditorWindow(VjceEditor::new);
                    var project = new VjceProject(example());
                    window.getCurrentEditor().loadProject(project, null);
                    ctx.put("project", project);
                    return new ModularUI(UI.of(window), ctx.player());
                })
                .awaitModularUI()
                .waitUntil("recipe editor laid out", ctx -> ctx.<VjceProject>get("project").layoutView.getSizeWidth() > 100)
                .check("new canvas uses MC stylesheet", ctx -> ctx.<VjceProject>get("project").layoutView.getTemplate().getStylesheets().contains(com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager.MC))
                .screenshot("01_editor")
                .step("start data-bound layout simulation", ctx -> ctx.<VjceProject>get("project").layoutView.startSimulation())
                .screenshot("02_preview")
                .step("project and publishing round trip", ctx -> {
                    var project = ctx.<VjceProject>get("project");
                    project.layoutView.stopSimulation();
                    var nbt = project.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
                    var restored = new VjceProject();
                    restored.deserializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), nbt);
                    ctx.check("layout retained", !restored.document.layout.isBlank());
                    ctx.check("recipe retained", restored.document.recipes.size() == 1 && restored.document.recipes.getFirst().slots.size() >= 2);
                })
                .step("open structured recipe forms", ctx -> {
                    var project = ctx.<VjceProject>get("project"); project.dataView.getViewContainer().selectView(project.dataView);
                })
                .awaitElement("#edit_duration")
                .step("reveal duration below recipe slots",ctx->{
                    var data=ctx.<VjceProject>get("project").dataView;
                    var details=(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView)data.selectId("vjce_recipe_details").findFirst().orElseThrow();
                    details.scrollToChild(data.selectId("edit_duration").findFirst().orElseThrow());
                })
                .typeInto("#edit_duration", "240")
                .check("numeric recipe form writes a double field", ctx -> ctx.<VjceProject>get("project").selected.duration == 240)
                .screenshot("03_recipe_form")
                .closeScreen();
    }
    public static CategoryDocument example() {
        var document = new CategoryDocument(); document.id = "vjce:smoke"; document.title = "VJCE · Recipe Layout";
        var energy = new CategoryDocument.Field(); energy.initial = 700; energy.maximum = 1000; document.fields.add(energy);
        var binding = new CategoryDocument.Binding(); binding.element = "bar_energy"; binding.property = "progress"; binding.expression = "clamp(energy / 1000, 0, 1)"; document.bindings.add(binding);
        var recipe = new CategoryDocument.Recipe(); recipe.id = "vjce:smoke_recipe";
        for (String role : java.util.List.of("INPUT", "OUTPUT")) {
            var slot = new CategoryDocument.Slot(); slot.name = role.toLowerCase(); slot.role = role;
            slot.x = role.equals("INPUT") ? 20 : 130; slot.y = 30;
            var ingredient = new CategoryDocument.Ingredient(); ingredient.item = role.equals("INPUT") ? "minecraft:iron_ingot" : "minecraft:gold_ingot";
            ingredient.amount = role.equals("INPUT") ? 3 : 2; slot.ingredients.add(ingredient); recipe.slots.add(slot);
        }
        if (IngredientAccess.manager != null) IngredientAccess.manager.getRegisteredIngredientTypes().stream().filter(type -> type.getUid().contains("ChemicalStack")).findFirst().ifPresent(type -> {
            var values = IngredientAccess.manager.getAllTypedIngredients(type);
            if (!values.isEmpty()) {
                var slot = new CategoryDocument.Slot(); slot.name = "chemical"; slot.role = "INPUT"; slot.x = 75; slot.y = 30;
                var chemical = IngredientAccess.encode(values.iterator().next()); chemical.amount = 4321;
                slot.ingredients.add(chemical); recipe.slots.add(slot);
            }
        });
        document.recipes.add(recipe); return document;
    }
}
