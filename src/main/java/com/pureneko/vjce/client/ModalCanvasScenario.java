package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import net.minecraft.client.Minecraft;
import org.joml.Vector2f;
import java.util.function.Function;

@LDLRegisterClient(name = "vjce_modal", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class ModalCanvasScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        open(s, false);
        click(s, "actual top-right close button", c -> editor(c).closeButton);
        s.check("closing dirty layout opens save dialog", c -> dialog(c) != null)
                .check("save dialog buttons win hit testing", c -> {
                    var button = dialog(c).buttonContainer.getChildren().getFirst(); var p = ElementBounds.of(button).center();
                    var hit = editor(c).getModularUI().hitTestAtScreen(p.x, p.y); return hit != null && (button == hit || button.isAncestorOf(hit));
                }).check("modal blocks canvas", c -> !view(c).canvasInputAllowed()).screenshot("save_dialog_above_canvas");
        click(s, "cancel close", c -> dialog(c).buttonContainer.getChildren().get(2));
        s.check("cancel keeps editor and project open", c -> Minecraft.getInstance().screen != null && editor(c).getCurrentProject() != null && dialog(c) == null)
                .step("open nonmodal popup", c -> { var d = new Dialog().allowInteraction(); d.addContent(new Label().setText("Nonmodal probe")); d.show(editor(c).getModularUI()); c.put("popup", d); })
                .check("nonmodal popup leaves canvas available", c -> view(c).canvasInputAllowed())
                .step("close nonmodal popup", c -> c.<Dialog>get("popup").close());
        s.keyDown(Keys.LEFT_SHIFT);
        point(s, "canvas gesture", c -> ElementBounds.of(view(c).getCurrentUI().rootElement.selectId("slot_0").findFirst().orElseThrow()).center());
        s.step("press canvas gesture", c -> { var p = c.<Vector2f>get("point"); c.input().mouseDown(p.x, p.y, 0); })
                .step("move before opening modal", c -> { var p = c.<Vector2f>get("point"); c.input().dragTo(p.x + 12, p.y + 8, 0); })
                .step("open save prompt during gesture", c -> editor(c).askToSaveProject(() -> {}))
                .check("opening modal stops active canvas drag", c -> !editor(c).getModularUI().getDragHandler().isDragging())
                .check("unfinished gesture rolled back", c -> Math.abs(view(c).getCurrentUI().rootElement.selectId("slot_0").findFirst().orElseThrow().getLayoutX() - 20) < .1)
                .step("release stopped gesture", c -> { var p = c.<Vector2f>get("point"); c.input().mouseUp(p.x + 12, p.y + 8, 0); })
                .keyUp(Keys.LEFT_SHIFT).keyDown(Keys.ESCAPE).keyUp(Keys.ESCAPE)
                .check("Esc dismisses modal and keeps editor", c -> Minecraft.getInstance().screen != null && dialog(c) == null);
        click(s, "close again", c -> editor(c).closeButton);
        click(s, "do not save", c -> dialog(c).buttonContainer.getChildren().get(1));
        s.check("discard closes project", c -> editor(c).getCurrentProject() == null || Minecraft.getInstance().screen == null).closeScreen();
        open(s, true);
        click(s, "close project with file", c -> editor(c).closeButton);
        click(s, "save project", c -> dialog(c).buttonContainer.getChildren().getFirst());
        s.check("save success notification appears", c -> dialog(c) != null);
        click(s, "confirm save success", c -> dialog(c).buttonContainer.getChildren().getFirst());
        s.waitUntil("save finishes and closes project", c -> editor(c).getCurrentProject() == null || Minecraft.getInstance().screen == null)
                .step("verify saved project", c -> {
                    try { var p = (VjceProject) VjceProject.TYPE.loadProjectFromFile(c.get("file")); c.check("save button wrote changed project", p.document.title.equals("Saved modal regression")); }
                    catch (Exception failure) { throw new IllegalStateException(failure); }
                }).closeScreen();
    }
    private static VjceEditor editor(TestContext c) { return c.get("editor"); }
    private static RecipeLayoutView view(TestContext c) { return ((VjceProject) editor(c).getCurrentProject()).layoutView; }
    private static Dialog dialog(TestContext c) {
        return editor(c).getModularUI().ui.rootElement.getChildren().stream().filter(Dialog.class::isInstance).map(Dialog.class::cast).findFirst().orElse(null);
    }
    private static void open(ScenarioBuilder s, boolean file) {
        s.openModularUI("modal regression editor", c -> {
            var w = new EditorWindow(VjceEditor::new); var p = new VjceProject(VjceSmokeScenario.example()); c.put("editor", w.getCurrentEditor());
            java.io.File path = null;
            if (file) try {
                path = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("qa-modal-save.vjce").toFile(); VjceProject.TYPE.saveProjectToFile(p, path); c.put("file", path);
            } catch (Exception failure) { throw new IllegalStateException(failure); }
            w.getCurrentEditor().loadProject(p, path); if (file) p.document.title = "Saved modal regression";
            return new ModularUI(UI.of(w), c.player());
        }).awaitModularUI();
    }
    private static void point(ScenarioBuilder s, String name, Function<TestContext, Vector2f> position) {
        s.step("aim " + name, c -> { var p = position.apply(c); c.put("point", p); c.input().moveTo(p.x, p.y); });
    }
    private static void click(ScenarioBuilder s, String name, Function<TestContext, UIElement> target) {
        point(s, name, c -> ElementBounds.of(target.apply(c)).center());
        s.step("press " + name, c -> { var p = c.<Vector2f>get("point"); c.input().mouseDown(p.x, p.y, 0); })
                .step("release " + name, c -> { var p = c.<Vector2f>get("point"); c.input().mouseUp(p.x, p.y, 0); });
    }
}
