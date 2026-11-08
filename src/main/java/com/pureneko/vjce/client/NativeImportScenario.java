package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.settings.AppearanceSettings;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Real side-by-side renders; this test does not compare a reconstruction to itself. */
@LDLRegisterClient(name = "vjce_native", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
@SuppressWarnings({"unchecked", "rawtypes", "removal"})
public final class NativeImportScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.defaultTimeoutMs(30000).scenarioTimeoutMs(180000); }
    @Override public void define(ScenarioBuilder steps) {
        steps.waitUntil("JEI ready", ctx -> VjceJeiPlugin.runtime != null);
        for (String id : RuntimeAudit.VANILLA.stream().sorted().toList()) {
            steps.openScreen("compare " + id, ctx -> {
                IRecipeCategory original = VjceJeiPlugin.originals.get("minecraft:" + id);
                var manager = VjceJeiPlugin.runtime.getRecipeManager();
                Object raw = manager.createRecipeLookup(original.getRecipeType()).get()
                        .filter(recipe -> original.getRegistryName(recipe) == null || !original.getRegistryName(recipe).getNamespace().equals("vjce"))
                        .findFirst().orElseThrow();
                var document = new CategoryDocument(); document.id = "vjce:comparison/" + id; document.target = "minecraft:" + id;
                document.title = original.getTitle().getString(); document.width = original.getWidth(); document.height = original.getHeight();
                var data = CapturedCategory.capture(original, raw); document.recipes.add(data);
                var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
                var adapter = new CapturedCategory(original, document);
                var source = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(original, raw, focus).orElseThrow();
                var imported = (IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(adapter, raw, focus).orElseThrow();
                var before = source.getRecipeSlotsView().getSlotViews(); var after = imported.getRecipeSlotsView().getSlotViews();
                ctx.check(id + ": slot count preserved", before.size() == after.size());
                for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
                    var a = ((IRecipeSlotDrawable) before.get(i)).getAreaIncludingBackground();
                    var b = ((IRecipeSlotDrawable) after.get(i)).getAreaIncludingBackground();
                    ctx.check(id + ": slot background geometry " + i, a.getX() == b.getX() && a.getY() == b.getY() && a.getWidth() == b.getWidth() && a.getHeight() == b.getHeight());
                }
                if (id.equals("smelting")) { ctx.put("document", document); ctx.put("raw", raw); ctx.put("original", original); }
                return new ComparisonScreen(source, imported, id);
            }).screenshot("compare_" + id).closeScreen();
        }
        steps.openModularUI("native editor", ctx -> {
            var window = new EditorWindow(VjceEditor::new);
            var project = new VjceProject(ctx.get("document"));
            window.getCurrentEditor().loadProject(project, null);
            ctx.put("editor", window.getCurrentEditor()); ctx.put("project", project);
            return new ModularUI(UI.of(window), ctx.player());
        }).awaitModularUI()
                .check("original editor theme", ctx -> ((AppearanceSettings) ctx.<VjceEditor>get("editor").editorSettings.getSettings(AppearanceSettings.ID).orElseThrow()).getStylesheet().equals(StylesheetManager.ORE_MERGED))
                .check("VJCE settings isolated", ctx -> ctx.<VjceEditor>get("editor").editorSettings.getSettingsFile().getPath().replace('\\', '/').endsWith("config/vjce/editor.json"))
                .step("rename category", ctx -> ctx.<VjceProject>get("project").document.title = "Renamed category")
                .check("title follows display name and type ID", ctx -> {
                    var title = ctx.<VjceEditor>get("editor").getTitle().getString();
                    return title.contains("Renamed category") && title.contains("minecraft:") && title.endsWith(" *");
                })
                .check("native parts editable", ctx -> ctx.<VjceProject>get("project").layoutView.getCurrentUI().rootElement.select("*").anyMatch(NativePartElement.class::isInstance))
                .check("designer survives dock attachment", ctx -> {
                    var root = ctx.<VjceProject>get("project").layoutView.getCurrentUI().rootElement;
                    return NativePage.context(root) != null;
                })
                .screenshot("native_editor")
                .step("preview native source", ctx -> ctx.<VjceProject>get("project").layoutView.startSimulation())
                .screenshot("native_preview")
                .step("move input and hide native arrow", ctx -> {
                    var project = ctx.<VjceProject>get("project"); project.layoutView.stopSimulation();
                    var root = project.layoutView.getCurrentUI().rootElement;
                    var input = NativePage.part(root, "slot_0");
                    ctx.put("inputX", NativePage.x(root, input) + 12); ctx.put("inputY", NativePage.y(root, input) + 3);
                    input.layout(l -> { l.left(ctx.<Integer>get("inputX")); l.top(ctx.<Integer>get("inputY")); });
                    NativePage.part(root, "jei_drawable_0").useSourceDrawing = false;
                    project.selected.duration = 240; project.selected.values.put("experience", 0.75);
                })
                .step("validate edited real JEI layout", ctx -> {
                    var project = ctx.<VjceProject>get("project"); project.captureLayout();
                    var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
                    var adapter = new CapturedCategory(ctx.get("original"), project.document);
                    var result = (IRecipeLayoutDrawable) VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(adapter, ctx.get("raw"), focus).orElseThrow();
                    var input = (IRecipeSlotDrawable) result.getRecipeSlotsView().getSlotViews().getFirst();
                    ctx.check("moved slot persists into JEI", input.getRect().getX() == ctx.<Integer>get("inputX") && input.getRect().getY() == ctx.<Integer>get("inputY"));
                    ctx.check("moved slot remains queryable and has native tooltip", result.getRecipeSlotUnderMouse(input.getRect().getX() + 2, input.getRect().getY() + 2).isPresent() && !input.getTooltip().isEmpty());
                    var access = (com.pureneko.vjce.mixin.RecipeLayoutAccess) result; access.vjce$createExtras();
                    ctx.check("hidden arrow removed from runtime drawing", access.vjce$widgets().size() == 6);
                    var page = NativePage.preview(project.document, project.selected);
                    var labels = page.parts.values().stream().filter(part -> part.widget() instanceof com.pureneko.vjce.mixin.TextWidgetAccess)
                            .flatMap(part -> ((com.pureneko.vjce.mixin.TextWidgetAccess) part.widget()).vjce$text().stream()).map(text -> text.getString()).toList();
                    ctx.check("duration and experience update native text", labels.stream().anyMatch(text -> text.contains("12")) && labels.stream().anyMatch(text -> text.contains("0.75")));
                    var saved = project.serializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry());
                    var restored = new VjceProject(); restored.deserializeProject(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry(), saved);
                    var root = RecipeUI.savedTemplate(restored.document).createUI().rootElement;
                    ctx.check("native element state survives save", !NativePage.part(root, "jei_drawable_0").useSourceDrawing && !restored.document.nativeOriginElements.isEmpty());
                }).screenshot("native_edited").closeScreen();
    }
    private static final class ComparisonScreen extends Screen {
        final IRecipeLayoutDrawable source, imported; final String name;
        ComparisonScreen(IRecipeLayoutDrawable source, IRecipeLayoutDrawable imported, String name) { super(Component.literal("VJCE source comparison")); this.source = source; this.imported = imported; this.name = name; }
        @Override public void tick() { source.tick(); imported.tick(); }
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xFF444444);
            graphics.drawString(font, "Original JEI - " + name, 40, 40, 0xFFFFFFFF, false);
            graphics.drawString(font, "VJCE imported - " + name, 340, 40, 0xFFFFFFFF, false);
            source.setPosition(40, 80); imported.setPosition(340, 80);
            source.drawRecipe(graphics, -1000, -1000); imported.drawRecipe(graphics, -1000, -1000);
        }
    }
}
