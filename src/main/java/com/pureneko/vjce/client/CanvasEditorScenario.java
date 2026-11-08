package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.settings.AppearanceSettings;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.mojang.serialization.JsonOps;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.joml.Vector2f;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

@LDLRegisterClient(name = "vjce_canvas", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class CanvasEditorScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready", ctx -> VjceJeiPlugin.runtime != null)
                .openModularUI("canvas selection editor", ctx -> {
                    var window = new EditorWindow(VjceEditor::new); var project = new VjceProject(VjceSmokeScenario.example());
                    window.getCurrentEditor().loadProject(project, null); ctx.put("project", project); ctx.put("editor", window.getCurrentEditor());
                    return new ModularUI(UI.of(window), ctx.player());
                }).awaitModularUI()
                .step("install nested and overlapping controls", ctx -> {
                    var root = view(ctx).getCurrentUI().rootElement;
                    var progress = new ProgressBar(); progress.setId("qa_progress"); progress.setProgress(0.5f); progress.label.setText("50%");
                    place(progress, 55, 28, 60, 18);
                    var button = new Button(); button.setId("qa_button"); button.setText("Test"); place(button, 115, 8, 45, 18);
                    button.setOnClick(event -> ctx.put("buttonClicks", ctx.<Integer>get("buttonClicks") + 1));
                    var cover = new Label().setText("Cover"); cover.setId("qa_cover"); place(cover, 115, 8, 45, 18); cover.style(style -> style.zIndex(10));
                    root.addChildren(progress, button, cover); view(ctx).hierarchy.loadUI(view(ctx).getCurrentUI()); ctx.put("buttonClicks", 0);
                    var outer = new IngredientSlot(); outer.setId("qa_outer_slot"); view(ctx).header.addChild(outer); ctx.put("outerSlot", outer);
                    testSettingsMigration(ctx);
                })
                .check("outer editor uses original ORE material", ctx -> ((AppearanceSettings) ctx.<VjceEditor>get("editor").editorSettings.getSettings(AppearanceSettings.ID).orElseThrow()).getStylesheet().equals(StylesheetManager.ORE_MERGED))
                .check("recipe canvas uses MC materials", ctx -> texture(element(ctx, "slot_0").getStyle().backgroundTexture()).contains("ui-mc"))
                .check("new progress fill uses MC material", ctx -> texture(((ProgressBar) element(ctx, "qa_progress")).bar.getStyle().backgroundTexture()).contains("ui-mc"))
                .check("outer ingredient controls retain original material", ctx -> texture(ctx.<IngredientSlot>get("outerSlot").getStyle().backgroundTexture()).contains("ui-gdp"));
        click(s, "nested progress label", ctx -> center(element(ctx, "qa_progress")));
        s.check("plain click selects progress component", ctx -> selected(ctx) == element(ctx, "qa_progress"))
                .check("selection synchronizes inspector", ctx -> view(ctx).inspector.getInspectedConfigurable() == element(ctx, "qa_progress"))
                .check("selection outline visible", ctx -> view(ctx).modularUIPreview.selectionBox.isDisplayed())
                .screenshot("canvas_component_selected");
        click(s, "top overlapping label", ctx -> center(element(ctx, "qa_cover")));
        s.check("top visible component selected", ctx -> selected(ctx) == element(ctx, "qa_cover")).keyDown(Keys.LEFT_ALT);
        click(s, "cycle below overlap", ctx -> center(element(ctx, "qa_cover")));
        s.check("Alt click selects underlying button", ctx -> selected(ctx) == element(ctx, "qa_button")).keyUp(Keys.LEFT_ALT)
                .check("selection never activates a button", ctx -> ctx.<Integer>get("buttonClicks") == 0)
                .step("hide top element", ctx -> element(ctx, "qa_cover").setVisible(false));
        click(s, "hidden cover ignored", ctx -> center(element(ctx, "qa_button")));
        s.check("hidden element does not intercept selection", ctx -> selected(ctx) == element(ctx, "qa_button"));
        click(s, "recipe background", ctx -> view(ctx).getCurrentUI().rootElement.getWorldMouse(view(ctx).getCurrentUI().rootElement.getContentX() + 6, view(ctx).getCurrentUI().rootElement.getContentY() + 5));
        s.check("background click selects background", ctx -> selected(ctx) == view(ctx).getCurrentUI().rootElement);
        click(s, "fully empty canvas", CanvasEditorScenario::blank);
        s.check("fully empty canvas clears selection and inspector", ctx -> selected(ctx) == null && view(ctx).inspector.getInspectedConfigurable() == null)
                .step("aim empty canvas pan", ctx -> {
                    var point = blank(ctx); ctx.put("panPoint", point); ctx.put("offsetX", view(ctx).graphView.getOffsetX()); ctx.input().moveTo(point.x, point.y);
                })
                .step("press empty canvas", ctx -> { var p = ctx.<Vector2f>get("panPoint"); ctx.input().mouseDown(p.x, p.y, Keys.MOUSE_LEFT); })
                .step("drag empty canvas", ctx -> { var p = ctx.<Vector2f>get("panPoint"); ctx.input().dragTo(p.x + 14, p.y + 8, Keys.MOUSE_LEFT); })
                .step("continue empty canvas drag", ctx -> { var p = ctx.<Vector2f>get("panPoint"); ctx.input().dragTo(p.x + 22, p.y + 12, Keys.MOUSE_LEFT); })
                .step("release empty canvas", ctx -> { var p = ctx.<Vector2f>get("panPoint"); ctx.input().mouseUp(p.x + 22, p.y + 12, Keys.MOUSE_LEFT); })
                .check("empty canvas drag pans viewport", ctx -> Math.abs(view(ctx).graphView.getOffsetX() - ctx.<Float>get("offsetX")) > 1)
                .step("zoom panned viewport", ctx -> view(ctx).graphView.setScale(1.25f));
        click(s, "zoomed and panned progress", ctx -> center(element(ctx, "qa_progress")));
        s.check("transformed viewport picks correct component", ctx -> selected(ctx) == element(ctx, "qa_progress"))
                .screenshot("canvas_zoom_selection")
                .step("start interactive preview", ctx -> {
                    view(ctx).startSimulation();
                    var button = view(ctx).canvas.getCanvasModularUI().ui.rootElement.selectId("qa_button", Button.class).findFirst().orElseThrow();
                    button.setOnClick(event -> ctx.put("buttonClicks", ctx.<Integer>get("buttonClicks") + 1));
                });
        click(s, "preview button", ctx -> center(view(ctx).canvas.getCanvasModularUI().ui.rootElement.selectId("qa_button").findFirst().orElseThrow()));
        s.check("preview button remains interactive", ctx -> ctx.<Integer>get("buttonClicks") == 1).closeScreen();

        s.openModularUI("native recipe selection", ctx -> {
            var doc = new CategoryDocument(); doc.id = "vjce:qa/canvas_native"; doc.target = "minecraft:smelting";
            var project = new VjceProject(doc); var window = new EditorWindow(VjceEditor::new); window.getCurrentEditor().loadProject(project, null); ctx.put("project", project);
            return new ModularUI(UI.of(window), ctx.player());
        }).awaitModularUI()
                .check("imported canvas has MC defaults", ctx -> view(ctx).getTemplate().getStylesheets().contains(StylesheetManager.MC))
                .click("#vjce_add_progress")
                .check("toolbar-created control inherits MC material", ctx -> texture(((ProgressBar) element(ctx, "progress")).barContainer.getStyle().backgroundTexture()).contains("ui-mc"))
                .step("move added control away from native input", ctx -> element(ctx, "progress").layout(l -> { l.left(34); l.top(35); l.width(30); }));
        click(s, "native input", ctx -> center(element(ctx, "slot_0")));
        s.check("native overlay does not intercept ordinary selection", ctx -> selected(ctx) == element(ctx, "slot_0"));
        click(s, "native background", ctx -> element(ctx, "native_layer").getWorldMouse(element(ctx, "native_layer").getContentX() + 21, element(ctx, "native_layer").getContentY() + 35));
        s.check("native background selectable by plain click", ctx -> selected(ctx) == element(ctx, "native_layer")).keyDown(Keys.LEFT_ALT);
        for (int i = 0; i < 4; i++) {
            click(s, "cycle native layers " + i, ctx -> center(element(ctx, "slot_0")));
            s.step("remember selected native layer", ctx -> {
                if (selected(ctx) == element(ctx, "native_overlay")) ctx.put("overlaySelected", true);
            });
        }
        s.keyUp(Keys.LEFT_ALT)
                .check("Alt cycling reaches whole native overlay", ctx -> Boolean.TRUE.equals(ctx.get("overlaySelected")))
                .check("temporary hit tests restore native overlay passthrough", ctx -> !element(ctx, "native_overlay").isAllowHitTest())
                .screenshot("native_canvas_selection").closeScreen();
    }

    private static void testSettingsMigration(TestContext ctx) {
        try {
            var file = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("vjce-ui-test-settings.json");
            Files.writeString(file, "{\"ldlib2:appearance\":{\"stylesheet\":\"ldlib2:lss/mc\",\"windowSize\":-1,\"editorWindowX\":77,\"editorWindowY\":88,\"editorWindowWidth\":600,\"editorWindowHeight\":400}}");
            var settings = new VjceEditorSettings(ctx.get("editor"), file.toFile());
            settings.registerSettings(new AppearanceSettings(), AppearanceSettings.CODEC); settings.loadAllSettingsFromFile();
            var appearance = (AppearanceSettings) settings.getSettings(AppearanceSettings.ID).orElseThrow();
            ctx.check("old saved MC editor setting restored", appearance.getStylesheet().equals(StylesheetManager.ORE_MERGED));
            ctx.check("appearance migration retains window bounds", appearance.getEditorWindowBounds().x() == 77 && appearance.getEditorWindowBounds().width() == 600);
            appearance.setStylesheet(StylesheetManager.MC_MERGED); settings.saveAllSettingsToFile(); settings.loadAllSettingsFromFile();
            ctx.check("appearance migration runs once", ((AppearanceSettings) settings.getSettings(AppearanceSettings.ID).orElseThrow()).getStylesheet().equals(StylesheetManager.MC_MERGED));
            Files.delete(file);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String texture(IGuiTexture texture) {
        return IGuiTexture.CODEC.encodeStart(JsonOps.INSTANCE, texture).getOrThrow().toString()
                .replace("mc_styles.png", "ui-mc").replace("gdp_styles.png", "ui-gdp");
    }
    private static RecipeLayoutView view(TestContext ctx) { return ctx.<VjceProject>get("project").layoutView; }
    private static UIElement element(TestContext ctx, String id) { return view(ctx).getCurrentUI().rootElement.selectId(id).findFirst().orElseThrow(); }
    private static UIElement selected(TestContext ctx) { return view(ctx).hierarchy.getSelectedOne().orElse(null); }
    private static Vector2f center(UIElement element) { return ElementBounds.of(element).center(); }
    private static Vector2f blank(TestContext ctx) { var graph = view(ctx).graphView; return graph.getWorldMouse(graph.getContentX() + 6, graph.getContentY() + 6); }
    private static void place(UIElement element, int x, int y, int width, int height) { element.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(x); l.top(y); l.width(width); l.height(height); }); }
    private static void click(ScenarioBuilder steps, String name, Function<TestContext, Vector2f> position) {
        steps.step("aim " + name, ctx -> { var p = position.apply(ctx); ctx.put("clickPoint", p); ctx.input().moveTo(p.x, p.y); })
                .step("press " + name, ctx -> { var p = ctx.<Vector2f>get("clickPoint"); ctx.input().mouseDown(p.x, p.y, Keys.MOUSE_LEFT); })
                .step("release " + name, ctx -> { var p = ctx.<Vector2f>get("clickPoint"); ctx.input().mouseUp(p.x, p.y, Keys.MOUSE_LEFT); });
    }
}
