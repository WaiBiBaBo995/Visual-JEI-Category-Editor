package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.integration.*;
import org.joml.Vector2f;

@LDLRegisterClient(name = "vjce_categories", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class AllCategoriesScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.scenarioTimeoutMs(180000); }
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready", c -> VjceJeiPlugin.runtime != null).step("audit every registered category", c -> {
            try {
                var report = AllCategoriesAudit.run();
                c.check("all vanilla and third-party samples support saved layouts: " + report.get("errors"), Boolean.TRUE.equals(report.get("passed")));
                c.check("arbitrary recipe classes were exercised", (int) report.get("edited") > 12);
                if (net.neoforged.fml.ModList.get().isLoaded("mekanism")) c.check("all 30 Mekanism machine categories inventoried", (int) report.get("mekanismCategories") == 30);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        }).openModularUI("all category library", c -> {
            var window = new EditorWindow(VjceEditor::new); c.put("editor", window.getCurrentEditor());
            return new ModularUI(UI.of(window), c.player());
        }).awaitModularUI().check("library lists every registered category", c -> VjceJeiPlugin.originals.keySet().stream().allMatch(id -> editor(c).selectId("vjce_category_" + id).findAny().isPresent()))
                .check("namespace prevents project ID collisions", c -> !VjceEditor.overrideId(net.minecraft.resources.ResourceLocation.parse("vjce_qa_a:shared")).equals(VjceEditor.overrideId(net.minecraft.resources.ResourceLocation.parse("vjce_qa_b:shared"))))
                .check("third-party smelting cannot use vanilla object factory", c -> !VanillaDisplays.supports(net.minecraft.resources.ResourceLocation.parse("vjce_qa_a:smelting")))
                .check("library search has a visible usable height",c->{var field=editor(c).selectId("vjce_category_search").findFirst().orElseThrow();var b=ElementBounds.of(field);return b.height()>=18&&b.width()>50&&b.y()>=0&&b.y()+b.height()<=c.mc().getWindow().getGuiScaledHeight();})
                .typeInto("#vjce_category_search","  MINECRAFT:SMELTING  ")
                .check("keyboard search filters both lists by source ID ignoring case and whitespace",c->editor(c).selectId("vjce_category_minecraft:smelting").findAny().isPresent()&&editor(c).selectId("vjce_saved_vjce:qa/smelting").findAny().isPresent()&&editor(c).selectId("vjce_saved_vjce:qa/crafting").findAny().isEmpty()&&editor(c).selectId("vjce_category_minecraft:crafting").findAny().isEmpty())
                .typeInto("#vjce_category_search","Minecraft")
                .check("mod names match without inventing mod matches from titles",c->editor(c).selectId("vjce_category_minecraft:smelting").findAny().isPresent()&&editor(c).selectId("vjce_category_vjce_qa_b:shared").findAny().isEmpty());
        click(s,"clear library search",c->editor(c).selectId("vjce_category_clear").findFirst().orElseThrow());
        s.check("clearing search restores every registered category",c->VjceJeiPlugin.originals.keySet().stream().allMatch(id->editor(c).selectId("vjce_category_"+id).findAny().isPresent()))
                .typeInto("#vjce_category_search","no_such_vjce_category_09")
                .check("search with no results hides both lists",c->editor(c).select("*").noneMatch(e->e.getId()!=null&&(e.getId().startsWith("vjce_saved_")||e.getId().startsWith("vjce_category_minecraft:"))))
                .step("choose third-party search query",c->{String id=VjceJeiPlugin.originals.keySet().stream().filter(key->key.startsWith("mekanism:")).findFirst().orElse("vjce_qa_a:shared");c.put("category",id);})
                .focus("#vjce_category_search").key(org.lwjgl.glfw.GLFW.GLFW_KEY_A,Keys.MOD_CONTROL).key(Keys.BACKSPACE)
                .step("type third-party query",c->{for(char ch:c.<String>get("category").toCharArray())c.input().charTyped(ch,0);}).screenshot("all_category_library");
        click(s, "open searched category", c -> editor(c).selectId("vjce_category_" + c.<String>get("category")).findFirst().orElseThrow());
        s.check("third-party category opens from library", c -> ((VjceProject) editor(c).getCurrentProject()).document.target.equals(c.<String>get("category")))
                .check("third-party source is editable", c -> ((VjceProject) editor(c).getCurrentProject()).sourceEditable)
                .check("unadapted third-party creation is disabled", c -> {
                    var p = (VjceProject) editor(c).getCurrentProject(); return !p.canCreateRecipes() && !p.dataView.selectId("vjce_new_recipe").findFirst().orElseThrow().isActive() && !p.dataView.selectId("vjce_copy_recipe").findFirst().orElseThrow().isActive();
                }).screenshot("third_party_category_editor").closeScreen();
        open(s, "empty", "vjce_qa_a:empty");
        s.check("empty category opens with an explicit limitation", c -> !project(c).sourceEditable && project(c).sourceIssue.equals("vjce.source.empty"))
                .check("empty category is not replaced by invented slots", c -> project(c).layoutView.getCurrentUI().rootElement.selectId("slot_0").findAny().isEmpty())
                .screenshot("empty_category").closeScreen();
        open(s, "uncapturable", "vjce_qa_a:readonly");
        s.check("capture restriction opens read-only", c -> !project(c).sourceEditable && project(c).sourceIssue.equals("vjce.source.readonly"))
                .check("read-only preview keeps live original drawing", c -> NativePage.context(project(c).layoutView.getCurrentUI().rootElement) != null)
                .step("read-only view does not persist fake editable layout", c -> { project(c).captureLayout(); c.check("read-only layout remains unset", project(c).document.layout.isBlank()); })
                .screenshot("readonly_original_category").closeScreen();
        open(s, "dynamic", "vjce_qa_a:dynamic");
        s.check("no-ID category allows category-wide layout", c -> project(c).sourceEditable)
                .check("unstable per-recipe edits are disabled", c -> !project(c).canEditSelectedRecipe() && project(c).document.recipes.isEmpty())
                .screenshot("dynamic_category_limitation").closeScreen();
        open(s, "source-managed slots", "minecraft:tag_recipes/item");
        s.check("source-owned scroll slots use an explicit read-only page", c -> !project(c).sourceEditable && NativePage.context(project(c).layoutView.getCurrentUI().rootElement) != null)
                .screenshot("source_widget_readonly").closeScreen();
    }
    private static VjceEditor editor(TestContext c) { return c.get("editor"); }
    private static VjceProject project(TestContext c) { return (VjceProject) editor(c).getCurrentProject(); }
    private static void open(ScenarioBuilder s, String name, String id) {
        s.openModularUI(name + " category", c -> {
            var w = new EditorWindow(VjceEditor::new); c.put("editor", w.getCurrentEditor()); w.getCurrentEditor().loadProject(VjceEditor.projectForCategory(id), null);
            return new ModularUI(UI.of(w), c.player());
        }).awaitModularUI();
    }
    private static void click(ScenarioBuilder s, String name, java.util.function.Function<TestContext, UIElement> target) {
        s.step("aim " + name, c -> { var p = ElementBounds.of(target.apply(c)).center(); c.put("point", p); c.input().moveTo(p.x, p.y); })
                .step("press " + name, c -> { var p = c.<Vector2f>get("point"); c.input().mouseDown(p.x, p.y, 0); })
                .step("release " + name, c -> { var p = c.<Vector2f>get("point"); c.input().mouseUp(p.x, p.y, 0); });
    }
}
