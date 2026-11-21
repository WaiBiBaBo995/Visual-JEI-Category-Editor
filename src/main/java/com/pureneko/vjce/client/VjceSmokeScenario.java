package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import java.io.File;
import java.nio.file.Files;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

@LDLRegisterClient(name = "vjce_smoke", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class VjceSmokeScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.defaultTimeoutMs(30000).scenarioTimeoutMs(180000); }
    @Override public void define(ScenarioBuilder steps) {
        steps.waitUntil("JEI runtime is ready", ctx -> VjceJeiPlugin.runtime != null)
                .step("audit vanilla category layouts and ingredient codecs", ctx -> {
                    try {
                        var path = RuntimeAudit.run();
                        var report = com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                        ctx.check("vanilla categories pass layout and codec audit: " + report.get("errors"), report.get("passed").getAsBoolean());
                    } catch (Exception e) { throw new IllegalStateException(e); }
                })
                .openScreen("chat with the editor command", ctx -> new ChatScreen("/vjce editor", false))
                .key(Keys.ENTER)
                .awaitModularUI()
                .awaitElement("#vjce_category_search")
                .closeScreen()
                .openModularUI("vjce_editor", ctx -> {
                    var window = new EditorWindow(VjceEditor::new);
                    var project = new VjceProject(example());
                    project.selected.slots.add(waterSlot());
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
                    try {
                        var projectFile = ctx.outDir().resolve("vjce-smoke.vjce").toFile();
                        VjceProject.TYPE.saveProjectToFile(project, projectFile);
                        var restored = (VjceProject) VjceProject.TYPE.loadProjectFromFile(projectFile);
                        ctx.check("layout retained", !restored.document.layout.isBlank());
                        ctx.check("recipe retained", restored.document.recipes.size() == 1 && restored.document.recipes.getFirst().slots.size() >= 2);
                        checkWater(ctx, restored, "saved project");
                        checkLegacyProject(ctx);
                        String importFile = System.getProperty("vjce.uitest.importProject", "");
                        if (!importFile.isBlank()) {
                            var imported = (VjceProject) VjceProject.TYPE.loadProjectFromFile(new File(importFile));
                            ctx.check("imported project layout retained", !imported.document.layout.isBlank());
                            checkWater(ctx, imported, "imported project");
                            checkUnavailableIngredients(ctx, imported);
                        }
                        var store = new ProjectStore(ctx.outDir().resolve("project-round-trip"));
                        store.save(restored.document);
                        var output = ctx.outDir().resolve("vjce-smoke-modpack.zip");
                        store.exportZip(output, null);
                        try (var zip = new java.util.zip.ZipFile(output.toFile())) {
                            ctx.check("the modpack includes the saved category",
                                    zip.getEntry("config/vjce/categories/" + ProjectStore.filename(restored.document)) != null);
                        }
                    } catch (Exception failure) {
                        throw new IllegalStateException("Project file round trip failed", failure);
                    }
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
    private static CategoryDocument.Slot waterSlot() {
        var water = new CategoryDocument.Ingredient();
        water.fluid = "minecraft:water";
        water.amount = 750;
        var slot = new CategoryDocument.Slot();
        slot.name = "water";
        slot.role = "INPUT";
        slot.x = 75;
        slot.y = 30;
        slot.ingredients.add(IngredientAccess.encode(IngredientAccess.decode(water).getFirst()));
        return slot;
    }

    private static void checkWater(TestContext ctx, VjceProject project, String source) {
        var slot = project.selected.slots.stream().filter(candidate -> candidate.name.equals("water")).findFirst().orElseThrow();
        var encoded = IngredientAccess.encode(IngredientAccess.decode(slot).getFirst());
        ctx.check(source + " preserves 750 mB of water", encoded.fluid.equals("minecraft:water") && encoded.amount == 750);
    }

    private static void checkLegacyProject(TestContext ctx) throws Exception {
        var document = example();
        document.target = "minecraft:fuel";
        var data = new CompoundTag();
        data.putString("document", ProjectStore.JSON.toJson(document));
        data.putString("categoryFile", "");
        // LDLib2 for 1.21.1 stored metadata and VJCE's JSON in an uncompressed compound.
        var root = new CompoundTag();
        root.put("meta", new CompoundTag());
        root.put("data", data);
        var file = ctx.outDir().resolve("vjce-legacy-format.vjce");
        NbtIo.write(root, file);
        var restored = (VjceProject) VjceProject.TYPE.loadProjectFromFile(file.toFile());
        ctx.check("1.21.1 project retains its recipe and migrates the JEI fuel category",
                restored.document.target.equals("minecraft:smelting_fuel") && restored.selected.slots.size() >= 2);
    }

    private static void checkUnavailableIngredients(TestContext ctx, VjceProject project) throws Exception {
        var unavailable = project.selected.slots.stream().flatMap(slot -> slot.ingredients.stream())
                .filter(spec -> !spec.type.isBlank() && IngredientAccess.manager.getIngredientTypeForUid(spec.type).isEmpty())
                .map(ProjectStore.JSON::toJsonTree).toList();
        if (unavailable.isEmpty()) return;
        var file = ctx.outDir().resolve("vjce-import-resaved.vjce").toFile();
        VjceProject.TYPE.saveProjectToFile(project, file);
        var restored = (VjceProject) VjceProject.TYPE.loadProjectFromFile(file);
        var restoredIngredients = restored.selected.slots.stream().flatMap(slot -> slot.ingredients.stream())
                .map(ProjectStore.JSON::toJsonTree).toList();
        ctx.check("resaving retains unregistered ingredient data", restoredIngredients.containsAll(unavailable));
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
    static VjceProject roundTrip(VjceProject project) {
        var registry = com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry();
        var reporter = net.minecraft.util.ProblemReporter.DISCARDING;
        var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, registry);
        project.serialize(output);
        var restored = new VjceProject();
        restored.deserialize(net.minecraft.world.level.storage.TagValueInput.create(reporter, registry, output.buildResult()));
        return restored;
    }
}
