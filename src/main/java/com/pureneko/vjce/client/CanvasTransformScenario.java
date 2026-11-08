package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;
import java.util.*;
import java.util.function.Function;

@LDLRegisterClient(name = "vjce_transform", group = "vjce", registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
@SuppressWarnings({"rawtypes", "unchecked"})
public final class CanvasTransformScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s) {
        s.waitUntil("JEI ready", ctx -> VjceJeiPlugin.runtime != null).openModularUI("transform editor", ctx -> {
            var doc = VjceSmokeScenario.example(); doc.width = 192; doc.height = 128; doc.layout = "";
            var p = new VjceProject(doc); var window = new EditorWindow(VjceEditor::new); window.getCurrentEditor().loadProject(p, null); ctx.put("project", p);
            return new ModularUI(UI.of(window), ctx.player());
        }).awaitModularUI().step("install transform fixtures", ctx -> {
            var root = v(ctx).getCurrentUI().rootElement; root.clearAllChildren();
            var a = new UIElement().setId("a"); place(a, 18, 22, 40, 20); a.style(st -> st.backgroundTexture(ColorPattern.CYAN.rectTexture()));
            var b = new UIElement().setId("b"); place(b, 96, 22, 40, 20); b.style(st -> st.backgroundTexture(ColorPattern.ORANGE.rectTexture()));
            root.addChildren(a, b); v(ctx).hierarchy.loadUI(v(ctx).getCurrentUI()); v(ctx).graphView.setScale(1);
            ctx.put("history", v(ctx).historyStack.getUndoStack().size());
        });
        click(s, "select initial transform fixture", ctx -> center(a(ctx)));
        s.step("remember gesture history baseline", ctx -> ctx.put("history", v(ctx).historyStack.getUndoStack().size())).keyDown(Keys.LEFT_SHIFT);
        drag(s, "Shift move", ctx -> center(a(ctx)), ctx -> new Vector2f(77, 0), true);
        s.keyUp(Keys.LEFT_SHIFT).check("move snaps edges and centres", ctx -> near(a(ctx).getLayoutX(), 96) && near(a(ctx).getLayoutY(), 22))
                .check("one move creates one history entry", ctx -> v(ctx).historyStack.getUndoStack().size() == ctx.<Integer>get("history") + 1)
                .step("undo move", ctx -> v(ctx).historyStack.undo())
                .check("undo restores placement", ctx -> near(a(ctx).getLayoutX(), 18))
                .step("redo move", ctx -> v(ctx).historyStack.redo())
                .check("redo restores snapped placement", ctx -> near(a(ctx).getLayoutX(), 96))
                .step("restore move", ctx -> v(ctx).historyStack.undo()).keyDown(Keys.LEFT_CONTROL);
        click(s, "Ctrl enter transform", ctx -> center(a(ctx)));
        s.keyUp(Keys.LEFT_CONTROL).check("green mode persists after releasing Ctrl", ctx -> v(ctx).transforms.isEditing()).screenshot("transform_green_handles");
        drag(s, "right edge width", ctx -> handle(ctx, 1, 0), ctx -> new Vector2f(12, 0), false);
        s.check("right edge changes only width", ctx -> near(a(ctx).getSizeWidth(), 52) && near(a(ctx).getSizeHeight(), 20) && near(a(ctx).getLayoutX(), 18));
        drag(s, "left edge width", ctx -> handle(ctx, -1, 0), ctx -> new Vector2f(-8, 0), false);
        s.check("left edge keeps opposite side fixed", ctx -> near(a(ctx).getLayoutX(), 10) && near(a(ctx).getSizeWidth(), 60));
        drag(s, "bottom height", ctx -> handle(ctx, 0, 1), ctx -> new Vector2f(0, 7), false);
        s.check("bottom edge changes height only", ctx -> near(a(ctx).getSizeWidth(), 60) && near(a(ctx).getSizeHeight(), 27));
        drag(s, "top height", ctx -> handle(ctx, 0, -1), ctx -> new Vector2f(0, -8), false);
        s.check("top edge keeps bottom fixed", ctx -> near(a(ctx).getLayoutY(), 14) && near(a(ctx).getSizeHeight(), 35));
        drag(s, "free corner", ctx -> handle(ctx, 1, 1), ctx -> new Vector2f(9, 5), false);
        s.check("corner changes width and height independently", ctx -> near(a(ctx).getSizeWidth(), 69) && near(a(ctx).getSizeHeight(), 40))
                .step("remember ratio and anchor", ctx -> { ctx.put("ratio", a(ctx).getSizeWidth() / a(ctx).getSizeHeight()); ctx.put("anchor", handle(ctx, -1, -1)); })
                .keyDown(Keys.LEFT_CONTROL);
        drag(s, "proportional corner", ctx -> handle(ctx, 1, 1), ctx -> new Vector2f(12, 0), false);
        s.keyUp(Keys.LEFT_CONTROL).check("Ctrl corner retains aspect ratio", ctx -> near(a(ctx).getSizeWidth() / a(ctx).getSizeHeight(), ctx.<Float>get("ratio")))
                .check("corner keeps opposite corner fixed", ctx -> handle(ctx, -1, -1).distance(ctx.<Vector2f>get("anchor")) < .1f)
                .step("remember cancelled layout", ctx -> { ctx.put("beforeCancel", a(ctx).getLayout().serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry())); ctx.put("history", v(ctx).historyStack.getUndoStack().size()); });
        begin(s, "cancel resize", ctx -> handle(ctx, 1, 0), ctx -> new Vector2f(20, 0));
        s.keyDown(Keys.ESCAPE).keyUp(Keys.ESCAPE);
        release(s);
        s.check("Esc restores complete original inline layout", ctx -> a(ctx).getLayout().serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()).equals(ctx.get("beforeCancel")))
                .check("cancel creates no history entry", ctx -> v(ctx).historyStack.getUndoStack().size() == ctx.<Integer>get("history"))
                .keyDown(Keys.LEFT_ALT).keyDown(Keys.LEFT_SHIFT);
        drag(s, "rotate quarter turn", ctx -> center(a(ctx)).add(15, 0), ctx -> new Vector2f(-15, 15), false);
        s.keyUp(Keys.LEFT_SHIFT).keyUp(Keys.LEFT_ALT).check("Alt drag rotates with 15 degree snapping", ctx -> near(a(ctx).getStyle().transform2D().rotation(), 90))
                .screenshot("transform_rotated")
                .step("remember rotated width and fixed edge", ctx -> { ctx.put("width", a(ctx).getSizeWidth()); ctx.put("anchor", handle(ctx, -1, 0)); });
        drag(s, "rotated local width", ctx -> handle(ctx, 1, 0), ctx -> new Vector2f(0, 10), false);
        s.check("rotated edge follows element local axis", ctx -> near(a(ctx).getSizeWidth(), ctx.<Float>get("width") + 10))
                .check("rotated resize keeps opposite edge fixed", ctx -> handle(ctx, -1, 0).distance(ctx.<Vector2f>get("anchor")) < .1f)
                .step("zoom canvas", ctx -> v(ctx).graphView.setScale(1.25f)).keyDown(Keys.LEFT_SHIFT).keyDown(Keys.LEFT_ALT);
        // Exit green mode first: Alt remains the overlap selector outside transform mode.
        click(s, "exit transform background", ctx -> rootPoint(ctx, 3, 3));
        s.keyUp(Keys.LEFT_ALT).check("click outside exits green mode", ctx -> !v(ctx).transforms.isEditing());
        drag(s, "zoomed move without snap", ctx -> center(a(ctx)), ctx -> new Vector2f(10, 5), false);
        s.keyUp(Keys.LEFT_SHIFT).check("zoomed move preserves rotation", ctx -> near(a(ctx).getStyle().transform2D().rotation(), 90))
                .step("save and restore transformed template", ctx -> {
                    ctx.<VjceProject>get("project").captureLayout(); var p = ctx.<VjceProject>get("project");
                    var ui = RecipeUI.savedTemplate(p.document).createUI(); var e = ui.rootElement.selectId("a").findFirst().orElseThrow();
                    ctx.check("rotation persists in template", near(e.getStyle().getValueImmediate(com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry.TRANSFORM_2D).rotation(), 90));
                }).step("fit page resize handles into viewport", ctx -> { v(ctx).graphView.setOffsetX(0); v(ctx).graphView.setOffsetY(0); v(ctx).graphView.setScale(.75f); }).keyDown(Keys.LEFT_CONTROL);
        click(s, "select root transform", ctx -> rootPoint(ctx, 3, 3));
        s.keyUp(Keys.LEFT_CONTROL);
        drag(s, "resize root page", ctx -> RecipeCanvasTransform.handlePoint(v(ctx).getCurrentUI().rootElement, 1, 1), ctx -> new Vector2f(7.5f, 7.5f), false);
        s.check("root resize synchronizes category dimensions", ctx -> ctx.<VjceProject>get("project").document.width == 202 && ctx.<VjceProject>get("project").document.height == 138)
                .step("undo root resize", ctx -> v(ctx).historyStack.undo())
                .check("root undo restores document dimensions", ctx -> ctx.<VjceProject>get("project").document.width == 192 && ctx.<VjceProject>get("project").document.height == 128)
                .step("prepare edge snapping", ctx -> { v(ctx).transforms.clear(); place(a(ctx), 18, 22, 40, 20); a(ctx).transform(t -> t.setIdentity()); v(ctx).graphView.setScale(1); }).keyDown(Keys.LEFT_CONTROL);
        click(s, "enter edge snapping", ctx -> center(a(ctx)));
        s.keyUp(Keys.LEFT_CONTROL);
        drag(s, "edge snapping", ctx -> handle(ctx, 1, 0), ctx -> new Vector2f(37, 0), true);
        s.check("resize edge snaps and keeps anchor", ctx -> near(a(ctx).getSizeWidth(), 78) && near(a(ctx).getLayoutX(), 18));
        drag(s, "edge snapping", ctx -> handle(ctx, -1, 0), ctx -> new Vector2f(-17, 0), true);
        s.check("fixed aligned edge does not mask moving edge snap", ctx -> near(a(ctx).getLayoutX(), 0) && near(a(ctx).getSizeWidth(), 96))
                .step("remember property undo geometry", ctx -> {
                    ctx.put("propertyWidth", a(ctx).getSizeWidth());
                    a(ctx).layout(l -> l.width(91));
                    a(ctx).createHistoryRecorder().record(v(ctx).historyStack, Component.literal("inspector property"), new Object());
                }).step("undo inspector property after gesture", ctx -> v(ctx).historyStack.undo())
                .check("inspector undo retains gesture geometry", ctx -> near(a(ctx).getSizeWidth(), ctx.<Float>get("propertyWidth")));
        drag(s, "minimum width", ctx -> handle(ctx, 1, 0), ctx -> new Vector2f(-100, 0), false);
        s.check("resize clamps without flipping", ctx -> near(a(ctx).getSizeWidth(), 1) && near(a(ctx).getLayoutX(), 0))
                .step("install percent layout with external child", ctx -> {
                    v(ctx).transforms.clear(); var child = new UIElement().setId("preserved_child"); child.setAllowHitTest(false); a(ctx).addChild(child); ctx.put("child", child);
                    a(ctx).layout(l -> { l.leftPercent(10); l.widthPercent(25); l.height(20); });
                    a(ctx).createHistoryRecorder().record(v(ctx).historyStack, Component.literal("percent layout"), new Object());
                    ctx.put("percentLayout", a(ctx).getLayout().serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()));
                }).keyDown(Keys.LEFT_SHIFT);
        drag(s, "percent move", ctx -> center(a(ctx)), ctx -> new Vector2f(11, 6), false);
        s.keyUp(Keys.LEFT_SHIFT).step("undo percent conversion", ctx -> v(ctx).historyStack.undo())
                .check("undo restores percent and automatic constraints", ctx -> a(ctx).getLayout().serializeNBT(com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry()).equals(ctx.get("percentLayout")))
                .check("undo preserves external child identity", ctx -> a(ctx).getChildren().contains(ctx.<UIElement>get("child"))).closeScreen();
        nativeGeometry(s);
        nativeInput(s);
    }
    private static void nativeInput(ScenarioBuilder s) {
        s.step("rotated native input coordinates", ctx -> {
            mezz.jei.api.recipe.category.IRecipeCategory source = VjceJeiPlugin.originals.get("minecraft:smelting");
            var manager = VjceJeiPlugin.runtime.getRecipeManager(); Object raw = manager.createRecipeLookup(source.getRecipeType()).get().findFirst().orElseThrow();
            double[] received = new double[4];
            mezz.jei.api.recipe.category.IRecipeCategory<Object> probe = new mezz.jei.api.recipe.category.IRecipeCategory<>() {
                @Override public mezz.jei.api.recipe.RecipeType<Object> getRecipeType() { return source.getRecipeType(); }
                @Override public Component getTitle() { return Component.literal("Input probe"); }
                @Override public mezz.jei.api.gui.drawable.IDrawable getIcon() { return source.getIcon(); }
                @Override public int getWidth() { return 100; }
                @Override public int getHeight() { return 70; }
                @Override public net.minecraft.resources.ResourceLocation getRegistryName(Object recipe) { return source.getRegistryName(recipe); }
                @Override public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder b, Object recipe, mezz.jei.api.recipe.IFocusGroup f) { source.setRecipe(b, recipe, f); }
                @Override public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder b, Object recipe, mezz.jei.api.recipe.IFocusGroup f) {
                    b.addInputHandler(new mezz.jei.api.gui.inputs.IJeiInputHandler() {
                        @Override public net.minecraft.client.gui.navigation.ScreenRectangle getArea() { return new net.minecraft.client.gui.navigation.ScreenRectangle(10, 20, 20, 10); }
                        @Override public boolean handleInput(double x, double y, mezz.jei.api.gui.inputs.IJeiUserInput input) { received[0] = x; received[1] = y; return true; }
                        @Override public boolean handleMouseScrolled(double x, double y, double sx, double sy) { received[0] = x; received[1] = y; received[2] = sx; received[3] = sy; return true; }
                        @Override public boolean handleMouseDragged(double x, double y, com.mojang.blaze3d.platform.InputConstants.Key key, double dx, double dy) { received[0] = x; received[1] = y; received[2] = dx; received[3] = dy; return true; }
                    });
                }
            };
            var doc = new CategoryDocument(); doc.id = "vjce:qa/input"; doc.target = "vjce:qa/input"; doc.width = 220; doc.height = 140;
            VjceJeiPlugin.originals.put(doc.target, probe);
            try {
                var data = CapturedCategory.capture(probe, raw); doc.recipes.add(data);
                var template = RecipeUI.template(doc, data); var content = template.createUI(); var overlay = NativePage.part(content.rootElement, "native_overlay");
                overlay.layout(l -> { l.left(60); l.top(35); l.width(120); l.height(84); }); overlay.transform(t -> t.rotation(30));
                var saved = content.toTemplate(); saved.copyStylesFrom(template); doc.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow().toString();
                var testUI = new ModularUI(RecipeUI.savedTemplate(doc).createUI()); testUI.init(doc.width, doc.height);
                var t = new ElementTransform(testUI.ui.rootElement, NativePage.part(testUI.ui.rootElement, "native_overlay"), 100, 70);
                var point = t.world(15, 25); var direction = t.world(19, 25).sub(point);
                var adapter = new CapturedCategory<>(probe, doc);
                var layout = manager.createRecipeLayoutDrawable(adapter, raw, VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of())).orElseThrow();
                ((com.pureneko.vjce.mixin.RecipeLayoutAccess) layout).vjce$createExtras(); layout.setPosition(0, 0);
                var key = com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(0);
                var input = (mezz.jei.api.gui.inputs.IJeiUserInput) java.lang.reflect.Proxy.newProxyInstance(CanvasTransformScenario.class.getClassLoader(), new Class[]{mezz.jei.api.gui.inputs.IJeiUserInput.class}, (p, method, args) -> switch (method.getName()) {
                    case "getKey" -> key; case "getModifiers" -> 0; default -> false;
                });
                ctx.check("rotated native click accepted", layout.getInputHandler().handleInput(point.x, point.y, input));
                ctx.check("native click inverse mapping includes AABB offset", near((float) received[0], 5) && near((float) received[1], 5));
                ctx.check("rotated native scroll accepted", layout.getInputHandler().handleMouseScrolled(point.x, point.y, 0, 1));
                ctx.check("native scroll coordinates restored", near((float) received[0], 5) && near((float) received[1], 5) && near((float) received[3], 1));
                ctx.check("rotated native drag accepted", layout.getInputHandler().handleMouseDragged(point.x, point.y, key, direction.x, direction.y));
                ctx.check("native drag vector inverse mapping", near((float) received[2], 4) && near((float) received[3], 0));
            } finally { VjceJeiPlugin.originals.remove(doc.target); }
        });
    }
    private static void nativeGeometry(ScenarioBuilder s) {
        s.openScreen("native transformed real JEI", ctx -> {
            mezz.jei.api.recipe.category.IRecipeCategory original = VjceJeiPlugin.originals.get("minecraft:smelting"); var manager = VjceJeiPlugin.runtime.getRecipeManager();
            Object raw = manager.createRecipeLookup(original.getRecipeType()).get().findFirst().orElseThrow();
            var doc = new CategoryDocument(); doc.id = "vjce:qa/transformed"; doc.target = "minecraft:smelting"; doc.width = 220; doc.height = 140;
            var data = CapturedCategory.capture(original, raw); doc.recipes.add(data);
            var template = RecipeUI.template(doc, data); var ui = template.createUI(); var slot = NativePage.part(ui.rootElement, "slot_0");
            slot.layout(l -> { l.left(35); l.top(30); l.width(32); l.height(20); }); slot.transform(t -> t.rotation(30));
            NativePage.part(ui.rootElement, "native_layer").layout(l -> { l.width(120); l.height(75); });
            var arrow = NativePage.part(ui.rootElement, "jei_drawable_0"); arrow.transform(t -> t.rotation(-20)); arrow.layout(l -> { l.width(32); l.height(24); });
            var saved = ui.toTemplate(); saved.copyStylesFrom(template); doc.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow().toString();
            var adapter = new CapturedCategory(original, doc); var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
            var layout = (mezz.jei.api.gui.IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(adapter, raw, focus).orElseThrow(); layout.setPosition(0, 0);
            var actualSlot = (mezz.jei.api.gui.ingredient.IRecipeSlotDrawable) layout.getRecipeSlotsView().getSlotViews().getFirst();
            var access = (com.pureneko.vjce.mixin.RecipeLayoutAccess) layout; access.vjce$createExtras();
            ctx.check("native slots remain in complete JEI view", layout.getRecipeSlotsView().getSlotViews().size() == data.slots.stream().filter(p -> !p.invisible).count());
            ctx.check("rotated slot centre returns original identity", layout.getRecipeSlotUnderMouse(51, 40).orElse(null) == actualSlot);
            ctx.check("axis-aligned rectangle outside rotation is rejected", layout.getRecipeSlotUnderMouse(35, 30).isEmpty());
            ctx.check("rotated corner inside transformed content is hit", layout.getRecipeSlotUnderMouse(42, 30).orElse(null) == actualSlot);
            ctx.check("returned slot supports JEI repeated hit check", ((mezz.jei.api.gui.inputs.RecipeSlotUnderMouse) layout.getSlotUnderMouse(42, 30).orElseThrow()).isMouseOver(42, 30));
            var target = (mezz.jei.common.input.IInputTarget) layout.getInputHandler();
            var screen = new Screen(Component.empty()) {};
            for (int button : List.of(0, 1)) {
                var press = mezz.jei.common.input.UserInput.fromVanilla(51, 40, button, mezz.jei.common.input.InputPhase.PRESS).orElseThrow();
                ctx.check("slot mouse button " + button + " reaches native JEI lookup",
                        target.beginInput(screen, press, mezz.jei.common.Internal.getKeyMappings()).isEmpty());
            }
            ctx.check("native tooltip retained", !actualSlot.getTooltip().isEmpty());
            ctx.check("native lookup and transfer identity retained", !adapter.blocksTransfer(raw));
            var rebound = ProjectStore.copy(doc);
            var edited = rebound.recipes.getFirst();
            var output = edited.slots.stream().filter(slotData -> slotData.role.equals("OUTPUT") && !slotData.invisible).findFirst().orElseThrow();
            edited.slots.getFirst().identifier = edited.slotIdentifier(edited.slots.indexOf(output));
            output.identifier = "slot_0";
            var reboundAdapter = new CapturedCategory(original, rebound);
            var reboundLayout = (mezz.jei.api.gui.IRecipeLayoutDrawable) manager.createRecipeLayoutDrawable(reboundAdapter, raw, focus).orElseThrow();
            ((com.pureneko.vjce.mixin.RecipeLayoutAccess) reboundLayout).vjce$createExtras();
            reboundLayout.setPosition(0, 0);
            Object reboundSlot = reboundLayout.getRecipeSlotUnderMouse(51, 40).orElse(null);
            ctx.check("binding-only swap uses target position and source role", reboundSlot instanceof mezz.jei.api.gui.ingredient.IRecipeSlotDrawable view
                    && view.getRole() == mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT);
            ctx.check("binding-only swap keeps native transfer", !reboundAdapter.blocksTransfer(raw));
            ctx.put("transformedLayout", layout);
            return new Screen(Component.literal("transformed native JEI")) {
                @Override public void tick() { layout.tick(); }
                @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
                    graphics.fill(0, 0, width, height, 0xff444444); layout.setPosition(80, 90); layout.drawRecipe(graphics, 131, 130);
                    graphics.drawString(font, "Actual JEI: resized background + rotated/scaled slot and arrow", 40, 40, 0xffffffff, false);
                }
            };
        }).screenshot("native_transformed_real_jei").closeScreen();
    }
    private static boolean near(float a, float b) { if (Math.abs(a - b) >= .15f) com.pureneko.vjce.VisualJeiCategoryEditor.LOG.info("VJCE geometry expected={} actual={}", b, a); return Math.abs(a - b) < .15f; }
    private static RecipeLayoutView v(TestContext c) { return c.<VjceProject>get("project").layoutView; }
    private static UIElement a(TestContext c) { return v(c).getCurrentUI().rootElement.selectId("a").findFirst().orElseThrow(); }
    private static Vector2f center(UIElement e) { return e.getWorldMouse(e.getPositionX() + e.getSizeWidth() / 2, e.getPositionY() + e.getSizeHeight() / 2); }
    private static Vector2f handle(TestContext c, int x, int y) { return RecipeCanvasTransform.handlePoint(a(c), x, y); }
    private static Vector2f rootPoint(TestContext c, int x, int y) { var r = v(c).getCurrentUI().rootElement; return r.getWorldMouse(r.getContentX() + x, r.getContentY() + y); }
    private static void place(UIElement e, int x, int y, int w, int h) { e.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(x); l.top(y); l.width(w); l.height(h); }); }
    private static void click(ScenarioBuilder s, String name, Function<TestContext, Vector2f> pos) {
        s.step("aim " + name, c -> { var p = pos.apply(c); c.put("p", p); c.input().moveTo(p.x, p.y); })
                .step("press " + name, c -> { var p = c.<Vector2f>get("p"); c.input().mouseDown(p.x, p.y, 0); })
                .step("release " + name, c -> { var p = c.<Vector2f>get("p"); c.input().mouseUp(p.x, p.y, 0); });
    }
    private static void begin(ScenarioBuilder s, String name, Function<TestContext, Vector2f> pos, Function<TestContext, Vector2f> delta) {
        s.step("aim " + name, c -> { var p = pos.apply(c); c.put("p", p); c.put("delta", delta.apply(c)); c.put("disableSnap", !name.equals("Shift move") && !name.equals("edge snapping") && !UIElement.isAltDown()); c.input().moveTo(p.x, p.y); })
                .step("press " + name, c -> { var p = c.<Vector2f>get("p"); c.input().mouseDown(p.x, p.y, 0); if (c.<Boolean>get("disableSnap")) c.input().keyDown(Keys.LEFT_ALT, 0); })
                .step("drag " + name, c -> { var p = new Vector2f(c.<Vector2f>get("p")).add(new Vector2f(c.<Vector2f>get("delta")).mul(.5f)); c.input().dragTo(p.x, p.y, 0); })
                .step("continue " + name, c -> { var p = new Vector2f(c.<Vector2f>get("p")).add(c.<Vector2f>get("delta")); c.input().dragTo(p.x, p.y, 0); });
    }
    private static void release(ScenarioBuilder s) { s.step("release gesture", c -> { var p = new Vector2f(c.<Vector2f>get("p")).add(c.<Vector2f>get("delta")); c.input().mouseUp(p.x, p.y, 0); if (c.<Boolean>get("disableSnap")) c.input().keyUp(Keys.LEFT_ALT, 0); }); }
    private static void drag(ScenarioBuilder s, String name, Function<TestContext, Vector2f> pos, Function<TestContext, Vector2f> delta, boolean guides) {
        begin(s, name, pos, delta); if (guides) s.check("alignment guide visible during drag", c -> v(c).transforms.hasGuides());
        if (name.equals("Shift move")) s.screenshot("one_pixel_alignment_guides");
        release(s);
    }
}
