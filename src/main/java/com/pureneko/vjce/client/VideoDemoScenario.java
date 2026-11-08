package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.utils.CursorOverlay;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.data.ProjectStore;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Opt-in development footage: real world capture, then a fresh client with normal JEI. */
@LDLRegisterClient(name = "vjce_video", group = "vjce_video", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class VideoDemoScenario implements UIScenario {
    private static final String ID = "vjce:video/processing_station";
    private static final BlockPos ORIGIN = new BlockPos(8, 80, 8);

    @Override public void configure(ScenarioOptions options) {
        options.defaultTimeoutMs(90_000).scenarioTimeoutMs(240_000);
    }

    @Override public void define(ScenarioBuilder s) {
        boolean full = System.getProperty("vjce.video.phase", "").equals("world");
        if (!System.getProperty("vjce.video.phase", "").equals("capture") && !full) {
            showcase(s);
            return;
        }
        s.waitUntil("JEI and world ready", c -> VjceJeiPlugin.runtime != null)
                .server("prepare dedicated filming set", sc -> {
                    var level = sc.level();
                    sc.player().getAbilities().flying = true;
                    sc.player().onUpdateAbilities();
                    sc.player().connection.teleport(11.5, 84, 3, 0, 30);
                    level.setDayTime(6000);
                    level.setWeatherParameters(60000, 0, false, false);
                    for (int x = 0; x < 7; x++) for (int z = 0; z < 5; z++)
                        level.setBlockAndUpdate(ORIGIN.offset(x, 0, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    for (int y = 1; y <= 3; y++) level.setBlockAndUpdate(ORIGIN.offset(6, y, 4), Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState());
                    for (var entry : Map.of("enrichment_chamber", new BlockPos(2, 1, 2),
                            "energized_smelter", new BlockPos(4, 1, 2)).entrySet()) {
                        var id = ResourceLocation.parse("mekanism:" + entry.getKey());
                        if (!BuiltInRegistries.BLOCK.containsKey(id)) throw new IllegalStateException("Missing filming dependency: " + id);
                        level.setBlockAndUpdate(ORIGIN.offset(entry.getValue()), BuiltInRegistries.BLOCK.get(id).defaultBlockState());
                    }
                    for (int i = 0; i < 2; i++) {
                        var pos = ORIGIN.offset(i == 0 ? 1 : 5, 1, 1);
                        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("mekanism:basic_fluid_tank")).defaultBlockState());
                        var tile = level.getBlockEntity(pos);
                        try {
                            var tank = tile.getClass().getField("fluidTank").get(tile);
                            var method = tank.getClass().getMethod("setStack", FluidStack.class);
                            method.setAccessible(true);
                            method.invoke(tank, new FluidStack(i == 0 ? Fluids.WATER : Fluids.LAVA, 8000));
                        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                    }
                })
                .waitUntil("tank visuals synchronized", c -> {
                    for (int x : List.of(1, 5)) {
                        var tile = c.mc().level.getBlockEntity(ORIGIN.offset(x, 1, 1));
                        if (tile == null || tile.getUpdateTag(c.mc().level.registryAccess()).getFloat("scale") <= 0) return false;
                    }
                    return true;
                }).waitMs(2000)
                .step("recorder handoff", c -> mark(c, "ready"))
                .waitUntil("external recorder started", c -> Files.exists(c.outDir().resolve("recorder-ready")))
                .waitMs(1500).step("world shot", c -> mark(c, "world"))
                .waitMs(full ? 9000 : 5500)
                .openModularUI("structure reference project", c -> {
                    var p = new VjceProject(document());
                    var window = new EditorWindow(VjceEditor::new);
                    window.getCurrentEditor().loadProject(p, null);
                    var root = p.layoutView.getCurrentUI().rootElement;
                    root.selectId("duration").toList().forEach(UIElement::removeSelf);
                    label(root, "vjce.video.title", 12, 10, 0xff303030);
                    label(root, "vjce.video.materials", 238, 33, 0xff477c32);
                    label(root, "vjce.video.caption", 12, 181, 0xff555555);
                    label(root, "vjce.video.reference", 12, 193, 0xff777777);
                    for (int i = 0; i < p.selected.slots.size(); i++)
                        label(root, "vjce.video.material_" + i, 260, 58 + i * 23, 0xff404040);
                    p.layoutView.hierarchy.loadUI(p.layoutView.getCurrentUI());
                    c.put("videoProject", p);
                    return new ModularUI(UI.of(window), c.player());
                }).awaitModularUI().waitMs(2000)
                .step("add scene", c -> SceneEditor.open(project(c).layoutView, null))
                .waitMs(900);
        click(s, "world selection", c -> root(c).selectId("vjce_world_open").findFirst().orElseThrow());
        s.step("frame industrial set", c -> dialog(c).preview.frame(ORIGIN.offset(-1, -1, -1), ORIGIN.offset(7, 4, 5)))
                .waitMs(1800).step("world preview shot", c -> mark(c, "selection"));
        corner(s, "first", ORIGIN);
        corner(s, "second", ORIGIN.offset(6, 3, 4));
        s.waitMs(full ? 4000 : 1400).screenshot("selected_structure")
                .step("hide native test cursor after screenshot", c -> CursorOverlay.setHidden(true));
        click(s, "import selection", c -> root(c).selectId("vjce_world_import").findFirst().orElseThrow());
        s.step("imported scene marker", c -> mark(c,"imported")).waitMs(full ? 5000 : 1400);
        click(s, "apply scene", c -> root(c).selectId("vjce_scene_apply").findFirst().orElseThrow());
        s.step("place imported scene", c -> {
                    var scene = project(c).layoutView.getCurrentUI().rootElement.select("*")
                            .filter(e -> e instanceof BlockSceneElement).map(e -> (BlockSceneElement) e).findFirst().orElseThrow();
                    scene.setId("video_scene");
                    scene.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(8); l.top(30); l.width(218); l.height(145); });
                    scene.cameraYaw = -145;
                    scene.cameraPitch = 25;
                    scene.motion = BlockSceneElement.Motion.ROTATE;
                    scene.period = 800;
                    scene.ensureScene();
                    c.check("captured scene contains entire set", scene.error.isEmpty() && scene.blocks.getList("blocks", 10).size() == 42);
                    c.put("videoScene", scene);
                    project(c).layoutView.hierarchy.loadUI(project(c).layoutView.getCurrentUI());
                    project(c).layoutView.startSimulation();
                    mark(c, "preview");
                }).waitMs(full ? 18000 : 6000)
                .server("snapshot isolation filming control", sc -> {
                    if (full) for (int x=0;x<7;x++) for (int y=0;y<4;y++) for (int z=0;z<5;z++)
                        sc.level().setBlockAndUpdate(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState());
                }).waitMs(full ? 1500 : 1)
                .step("snapshot remains after source change", c -> {
                    if (full) {
                        var scene=project(c).layoutView.canvas.getCanvasModularUI().ui.rootElement.select("*").filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().orElseThrow();
                        scene.ensureScene();
                        c.check("source is air but snapshot contains 42 blocks",c.mc().level.getBlockState(ORIGIN).isAir()&&scene.blocks.getList("blocks",10).size()==42&&!scene.getDummyWorld().getBlockState(BlockPos.ZERO).isAir());
                        mark(c,"snapshot-proof");
                    }
                }).waitMs(full ? 12000 : 1)
                .step("save real category for next client", c -> {
                    try {
                        var p = project(c);
                        p.layoutView.stopSimulation();
                        p.publish();
                        var folder = FMLPaths.GAMEDIR.get().resolve("exports");
                        Files.createDirectories(folder);
                        VjceProject.TYPE.saveProjectToFile(p, folder.resolve("processing-station.vjce").toFile());
                        mark(c, "published");
                    } catch (Exception error) { throw new IllegalStateException(error); }
                }).waitMs(1000).step("capture complete", c -> mark(c, "done")).closeScreen();
    }

    private static void showcase(ScenarioBuilder s) {
        s.waitUntil("JEI restarted with saved configuration", c -> VjceJeiPlugin.runtime != null)
                .step("open registered category in normal JEI", c -> {
                    var type = new mezz.jei.api.recipe.RecipeType<>(ResourceLocation.parse(ID), CategoryDocument.Recipe.class);
                    var category = VjceJeiPlugin.runtime.getRecipeManager().getRecipeCategory(type);
                    c.check("saved category really registered", category != null && category.getRecipeType().getUid().toString().equals(ID));
                    VjceJeiPlugin.runtime.getRecipesGui().showTypes(List.<mezz.jei.api.recipe.RecipeType<?>>of(type));
                })
                .waitUntil("normal JEI recipe browser visible", c -> c.mc().screen != null &&
                        c.mc().screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"))
                .waitMs(1800).step("recorder handoff", c -> mark(c, "ready"))
                .waitUntil("external recorder started", c -> Files.exists(c.outDir().resolve("recorder-ready")))
                .waitMs(1500).step("normal JEI shot", c -> mark(c, "jei"))
                .waitMs(System.getProperty("vjce.video.phase", "").equals("showcase-full") ? 65000 : 22000).screenshot("normal_jei_saved_category")
                .step("showcase complete", c -> mark(c, "done")).closeScreen();
    }

    static CategoryDocument document() {
        var doc = new CategoryDocument();
        doc.id = ID; doc.title = Component.translatable("vjce.video.title").getString();
        doc.icon = "mekanism:enrichment_chamber"; doc.width = 330; doc.height = 208;
        var recipe = new CategoryDocument.Recipe(); recipe.id = "vjce:video/processing_station_reference";
        String[] items = {"minecraft:polished_deepslate", "mekanism:enrichment_chamber", "mekanism:energized_smelter", "mekanism:basic_fluid_tank", "minecraft:polished_blackstone_bricks"};
        int[] counts = {35, 1, 1, 2, 3};
        for (int i = 0; i < items.length; i++) {
            var slot = new CategoryDocument.Slot(); slot.name = "material_" + i; slot.role = "RENDER_ONLY";
            slot.x = 238; slot.y = 55 + i * 23;
            var ingredient = new CategoryDocument.Ingredient(); ingredient.item = items[i]; ingredient.amount = counts[i];
            slot.ingredients.add(ingredient); recipe.slots.add(slot);
        }
        doc.recipes.add(recipe); return doc;
    }

    private static void label(UIElement root, String key, int x, int y, int color) {
        var label = new Label().setText(key);
        label.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(x); l.top(y); });
        label.textStyle(style -> style.textColor(color)); root.addChild(label);
    }
    private static VjceProject project(TestContext c) { return c.get("videoProject"); }
    private static UIElement root(TestContext c) { return project(c).layoutView.getModularUI().ui.rootElement; }
    private static WorldSelectionDialog dialog(TestContext c) { return (WorldSelectionDialog) root(c).selectId("vjce_world_dialog").findFirst().orElseThrow(); }
    static void mark(TestContext c, String name) {
        try {
            var marker = Map.of("name", name, "epochMillis", System.currentTimeMillis(), "phase", System.getProperty("vjce.video.phase"));
            Files.createDirectories(c.outDir());
            Files.writeString(c.outDir().resolve(name + ".json"), ProjectStore.JSON.toJson(marker));
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
    static void click(ScenarioBuilder s, String name, Function<TestContext, UIElement> element) {
        move(s, name, "videoPoint", c -> ElementBounds.of(element.apply(c)).center());
        s
                .waitMs(350)
                .step("press " + name, c -> { var p = c.<Vector2f>get("videoPoint"); c.input().mouseDown(p.x, p.y, 0); trace(c, "down", p); })
                .waitMs(100)
                .step("release " + name, c -> { var p = c.<Vector2f>get("videoPoint"); c.input().mouseUp(p.x, p.y, 0); trace(c, "up", p); }).waitMs(450);
    }
    private static void corner(ScenarioBuilder s, String name, BlockPos pos) {
        move(s, name + " corner", "videoCorner", c -> dialog(c).preview.project(new Vector3f(pos.getX() + .5f, pos.getY() + 1.001f, pos.getZ() + .5f)).orElseThrow());
        s
                .waitUntil(name + " corner visible", c -> dialog(c).preview.scene.getLastHoverPosFace() != null && dialog(c).preview.scene.getLastHoverPosFace().pos().equals(pos))
                .waitMs(600).keyDown(Keys.LEFT_SHIFT)
                .step("press " + name + " corner", c -> { var p = c.<Vector2f>get("videoCorner"); c.input().mouseDown(p.x, p.y, 0); trace(c, "down", p); })
                .waitMs(100)
                .step("release " + name + " corner", c -> { var p = c.<Vector2f>get("videoCorner"); c.input().mouseUp(p.x, p.y, 0); trace(c, "up", p); })
                .keyUp(Keys.LEFT_SHIFT).step(name + " selected marker", c -> mark(c,"corner-"+name))
                .waitMs(System.getProperty("vjce.video.phase", "").equals("world") ? 3000 : 900);
    }

    /** Move the actual synthetic input every frame; the edit uses this same trace for emphasis. */
    static void move(ScenarioBuilder s, String name, String key, Function<TestContext, Vector2f> target) {
        s.step("prepare smooth " + name, c -> {
            c.put(key, new Vector2f(target.apply(c)));
            c.put("videoMoveFrom", new Vector2f(c.get("videoCursor", new Vector2f(
                    c.mc().getWindow().getGuiScaledWidth() / 2f, c.mc().getWindow().getGuiScaledHeight() / 2f))));
            c.put("videoMoveStart", System.nanoTime());
        }).waitUntil("smooth " + name, c -> {
            double fraction = Math.min(1, (System.nanoTime() - c.<Long>get("videoMoveStart")) / 700_000_000.0);
            float ease = (float) (fraction * fraction * (3 - 2 * fraction));
            var p = new Vector2f(c.<Vector2f>get("videoMoveFrom")).lerp(c.get(key), ease);
            c.input().moveTo(p.x, p.y);
            c.put("videoCursor", new Vector2f(p));
            trace(c, "move", p);
            return fraction >= 1;
        });
    }

    static void trace(TestContext c, String event, Vector2f p) {
        CursorOverlay.setHidden(true);
        try {
            var window = c.mc().getWindow();
            var point = Map.of("epochMillis", System.currentTimeMillis(), "event", event,
                    "x", p.x, "y", p.y, "guiWidth", window.getGuiScaledWidth(),
                    "guiHeight", window.getGuiScaledHeight(), "screenWidth", window.getWidth(),
                    "screenHeight", window.getHeight(), "button", event.equals("move") ? -1 : 0);
            Files.writeString(c.outDir().resolve("cursor.jsonl"), ProjectStore.JSON.toJson(point).replace("\n", "") + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) { throw new IllegalStateException(error); }
    }
}
