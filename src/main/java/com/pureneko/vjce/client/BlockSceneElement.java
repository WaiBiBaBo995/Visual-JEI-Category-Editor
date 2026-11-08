package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import java.util.*;

/** The persisted field/registry name stays compatible; its data can contain blocks, entities, or both. */
@LDLRegister(name = "vjce-block-scene", group = "vjce", registry = "ldlib2:ui_element")
public class BlockSceneElement extends Scene {
    static final int MAX_BLOCKS = 4096, MAX_ENTITIES = 32;
    public enum Motion { NONE, ROTATE, BOB }
    @Persisted @Configurable(name="vjce.scene.blocks") public CompoundTag blocks = defaults();
    static final float DEFAULT_CAMERA_YAW = -135;
    @Persisted @Configurable(name="vjce.scene.yaw") public float cameraYaw = DEFAULT_CAMERA_YAW;
    @Persisted @Configurable(name="vjce.scene.pitch") public float cameraPitch = 25;
    @Persisted @Configurable(name="vjce.scene.range") public float cameraRange = 1.5f;
    @Persisted @Configurable(name="vjce.scene.auto_frame") public boolean autoFrame = true;
    @Persisted @Configurable(name="vjce.scene.motion") public Motion motion = Motion.NONE;
    @Persisted @Configurable(name="vjce.scene.speed") public double speed = 1;
    @Persisted @Configurable(name="vjce.scene.period") public double period = 80;
    @Persisted @Configurable(name="vjce.scene.phase") public double phase = 0;
    @Persisted @Configurable(name="vjce.scene.distance") public float distance = .25f;
    @Persisted @Configurable(name="vjce.scene.tick") public boolean animateBlockEntities = true;
    @Persisted @Configurable(name="vjce.scene.tick_entities") public boolean animateEntities = true;
    public final SceneClock clock = new SceneClock();
    public String error = "";
    public String missingResources = "";
    private CompoundTag loaded;
    private Vector3f sceneCenter = new Vector3f(.5f);
    private double fitRadius = .5, tickBudget;
    private SceneEntities entityContent;

    private static final class DisplayWorld extends TrackedDummyWorld {
        boolean entitiesEnabled, blocksEnabled;
        @Override public void clear() {
            // LDLib iterates a live entity map while removing entries. Snapshot before removal.
            for (var pos : new ArrayList<>(filledBlocks)) removeBlock(BlockPos.of(pos));
            var entities = new ArrayList<Entity>();
            for (var entity : getEntities().getAll()) if (entity != null) entities.add(entity);
            for (var entity : entities) removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        }
        @Override public void tickEntities() {
            if (entitiesEnabled) super.tickEntities(); else tickBlockEntities();
        }
        @Override public void tickBlockEntities() { if (blocksEnabled) super.tickBlockEntities(); }
    }
    public BlockSceneElement() {
        layout(l -> { l.width(80); l.height(80); });
        autoReleased = false; // This subclass owns renderer/world disposal, including idle JEI caches.
        setDraggable(false); setScalable(false); setIntractable(false); setRenderFacing(false); setRenderSelect(false);
    }
    public static CompoundTag defaults() {
        try { return TagParser.parseTag("{blocks:[{x:0,y:0,z:0,state:{Name:\"minecraft:enchanting_table\"},data:{}}]}"); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static BlockSceneElement entityScene() {
        var scene = new BlockSceneElement(); var data = new CompoundTag();
        data.put("blocks", new ListTag()); var entities = new ListTag(); entities.add(SceneEntities.defaults("minecraft:pig")); data.put("entities", entities);
        scene.blocks = data; return scene;
    }
    static ListTag rows(CompoundTag data, String key) {
        var value = data.get(key); if (value == null) return new ListTag();
        if (value instanceof ListTag list && (list.isEmpty() || list.getElementType() == Tag.TAG_COMPOUND)) return list;
        throw new IllegalArgumentException(key + " must be a list of compounds");
    }
    public List<Entity> sceneEntities() { return entityContent == null ? List.of() : entityContent.entities(); }
    protected boolean allowEmptyScene(){return false;}
    public void ensureScene() {
        if (blocks == null) { error = "Missing scene data"; return; }
        var effective=SceneAssets.resolve(blocks);
        if (loaded != null && loaded.equals(effective)) return;
        release(); loaded = effective.copy(); error = "";missingResources="";
        try {
            var blockRows = rows(effective, "blocks"); var entityRows = rows(effective, "entities");
            var missing=new LinkedHashSet<String>();for(var list:List.of(blockRows,entityRows))for(var tag:list){var row=(CompoundTag)tag;if(row.contains("missing"))missing.add(row.getString("missing"));}missingResources=String.join(", ",missing);
            if (blockRows.size() > MAX_BLOCKS || entityRows.size() > MAX_ENTITIES || blockRows.isEmpty() && entityRows.isEmpty()&&!allowEmptyScene())
                throw new IllegalArgumentException("Scene requires content: up to 4096 blocks and 32 entities");
            var world = new DisplayWorld(); dummyWorld = world;
            var positions = new LinkedHashSet<BlockPos>(); AABB bounds = null;
            for (int i = 0; i < blockRows.size(); i++) {
                var row = blockRows.getCompound(i); var pos = new BlockPos(row.getInt("x"), row.getInt("y"), row.getInt("z"));
                if (pos.getX() < -64 || pos.getX() > 64 || pos.getY() < -64 || pos.getY() > 64 || pos.getZ() < -64 || pos.getZ() > 64 || positions.contains(pos))
                    throw new IllegalArgumentException("Invalid or duplicate scene position: " + pos);
                var state = BlockState.CODEC.parse(NbtOps.INSTANCE, row.getCompound("state")).getOrThrow();
                if (state.isAir()) throw new IllegalArgumentException("Unknown or air block: " + row.getCompound("state"));
                var info = BlockInfo.fromBlockState(state);
                world.addBlock(pos, info); positions.add(pos);
                var box = SceneTransforms.bounds(pos,row); bounds = bounds == null ? box : bounds.minmax(box);
            }
            // Create every neighbour first, then restore persistence and finally native client state.
            // Some renderers (e.g. Mekanism fluid tanks) keep visual state only in update tags.
            for(var tag:blockRows){var row=(CompoundTag)tag;var tile=world.getBlockEntity(new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")));
                if(tile!=null&&row.contains("data",Tag.TAG_COMPOUND)&&!row.getCompound("data").isEmpty()){
                    var data=tile.saveWithoutMetadata(world.registryAccess());data.merge(row.getCompound("data").copy());tile.loadWithComponents(data,world.registryAccess());}}
            for(var tag:blockRows){var row=(CompoundTag)tag;var tile=world.getBlockEntity(new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")));
                if(tile!=null&&row.contains("update",Tag.TAG_COMPOUND)&&!row.getCompound("update").isEmpty()){
                    var update=row.getCompound("update").copy();var pos=tile.getBlockPos();
                    if(update.contains("x",Tag.TAG_ANY_NUMERIC))update.putInt("x",pos.getX());
                    if(update.contains("y",Tag.TAG_ANY_NUMERIC))update.putInt("y",pos.getY());
                    if(update.contains("z",Tag.TAG_ANY_NUMERIC))update.putInt("z",pos.getZ());
                    tile.handleUpdateTag(update,world.registryAccess());
                }}
            entityContent = new SceneEntities(world, entityRows);
            if (entityContent.bounds != null) bounds = bounds == null ? entityContent.bounds : bounds.minmax(entityContent.bounds);
            // Scene initializes its world/filter. Replace the renderer before any drawing/allocation.
            createScene(world); var initial = renderer; renderer = new SceneRenderer(world); initial.releaseResource();
            ((SceneRenderer)renderer).setBlockTransforms(blockRows);
            renderer.setBeforeBatchEnd(this::renderBeforeBatchEnd); renderer.setAfterWorldRender(this::renderBlockOverLay);
            renderer.setSceneEntityRenderHook(entityContent);
            if (world.getParticleManager() != null) renderer.setParticleManager(world.getParticleManager());
            useOrtho(true); setRenderedCore(positions, null, false);
            if(bounds==null)bounds=new AABB(0,0,0,1,1,1);
            var center = bounds.getCenter();
            sceneCenter = new Vector3f((float) center.x, (float) center.y, (float) center.z);
            fitRadius = Math.sqrt(bounds.getXsize() * bounds.getXsize() + bounds.getYsize() * bounds.getYsize() + bounds.getZsize() * bounds.getZsize()) / 2 + .25;
            setCenter(sceneCenter); setZoom(1); setTickWorld(false); tickBudget = 0;
        } catch (RuntimeException failure) {
            var failed = loaded; release(); loaded = failed; error = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        }
    }
    @Override public void screenTick() {
        ensureScene(); if (!error.isEmpty()) return; super.screenTick();
        if (dummyWorld instanceof DisplayWorld world && clock.playing && (animateBlockEntities || animateEntities)) {
            world.blocksEnabled = animateBlockEntities; world.entitiesEnabled = animateEntities;
            tickBudget += Double.isFinite(speed) ? Math.max(0, Math.min(20, speed)) : 1;
            try {
                while (tickBudget >= 1) {
                    entityContent.anchorAll(); world.tickWorld(); entityContent.anchorAll(); entityContent.checkAlive(); tickBudget--;
                }
            } catch (RuntimeException failure) { error = "Scene animation: " + failure.getMessage(); clock.playing = false; }
        }
    }
    static int[] resolution(float width, float height, float axisX, float axisY, double guiScale) {
        width = Math.max(1, width); height = Math.max(1, height);
        double longest = Math.max(width, height);
        double scale = Math.max(.001, Math.max(axisX, axisY)) * guiScale;
        scale = Math.max(32 / longest, Math.min(1024 / longest, scale));
        // One pixel density for both axes keeps the 3D projection independent of 2D stretch/rotation.
        return new int[]{Math.max(1, (int) Math.ceil(width * scale - .001)), Math.max(1, (int) Math.ceil(height * scale - .001))};
    }
    @Override public void drawContents(GUIContext context) {
        boolean hidden = !getStyle().overflowVisible() && getStyle().overflowClip() == IGuiTexture.EMPTY;
        if (hidden) {
            if (isCulled()) return;
            context.graphics.flush();
            // LDLib's default scissor transforms only two opposite corners. At 45/90 degrees
            // it collapses or reverses the clip even though the FBO itself is rendered correctly.
            float x = getContentX(), y = getContentY(), w = getContentWidth(), h = getContentHeight();
            float minX = Float.POSITIVE_INFINITY, minY = minX, maxX = Float.NEGATIVE_INFINITY, maxY = maxX;
            for (var corner : List.of(new Vector3f(x,y,0), new Vector3f(x+w,y,0), new Vector3f(x,y+h,0), new Vector3f(x+w,y+h,0))) {
                context.pose.last().pose().transformPosition(corner);
                minX = Math.min(minX, corner.x); minY = Math.min(minY, corner.y);
                maxX = Math.max(maxX, corner.x); maxY = Math.max(maxY, corner.y);
            }
            context.enableScissor(minX, minY, maxX-minX, maxY-minY, new Matrix4f());
        }
        try {
            if (!isCulled()) drawBackgroundAdditional(context);
            if (!getChildren().isEmpty() && shouldDrawChildren()) {
                int color = context.elementColor;
                if (color != -1) { context.graphics.flush(); context.resetElementColor(); }
                try { drawChildren(context); }
                finally { if (color != -1) { context.graphics.flush(); context.setElementColor(color); } }
            }
        } finally {
            if (hidden) { context.graphics.flush(); context.disableScissor(); }
        }
    }
    @Override public void drawBackgroundAdditional(GUIContext context) {
        ensureScene(); SceneResources.touch(this); double t = clock.time(speed) + phase;
        float fraction = (float) (t / Math.max(1, Double.isFinite(period) ? period : 80));
        setCameraYawAndPitch(cameraYaw + (motion == Motion.ROTATE ? fraction * 360 : 0), cameraPitch);
        float range = Math.max(.2f, Float.isFinite(cameraRange) ? cameraRange : 1.5f);
        if (autoFrame) range = Math.max(range, (float) (fitRadius * Math.max(1, getContentWidth() / Math.max(1, getPaddingHeight()))));
        setOrthoRange(range);
        setCenter(new Vector3f(sceneCenter).add(0, motion == Motion.BOB ? (float) Math.sin(fraction * Math.PI * 2) * distance : 0, 0));
        if (!error.isEmpty()) {
            context.graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "Scene: " + error, (int) getContentX(), (int) getContentY(), 0xffff6666, true);
            return;
        }
        try {
            var matrix = context.graphics.pose().last().pose();
            float sx = new Vector3f(matrix.m00(), matrix.m01(), matrix.m02()).length();
            float sy = new Vector3f(matrix.m10(), matrix.m11(), matrix.m12()).length();
            var fbo = (SceneRenderer) renderer;
            fbo.entityTime(clock.playing && animateEntities, tickBudget, speed);
            var dimensions = resolution(getContentWidth(), getPaddingHeight(), sx, sy, context.surface.guiScale());
            int w = dimensions[0], h = dimensions[1];
            if (w != fbo.getResolutionWidth() || h != fbo.getResolutionHeight()) fbo.setFBOSize(w, h);
            super.drawBackgroundAdditional(context);
            if(!missingResources.isBlank())context.graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("vjce.asset.missing",missingResources),(int)getContentX(),(int)getContentY(),0xffff6666,true);
        } catch (RuntimeException failure) { error = "Scene renderer: " + failure.getMessage(); clock.playing = false; }
    }
    public void release() {
        SceneResources.forget(this);
        try { if (renderer != null) releaseRendererResource(); }
        finally {
            renderer = null;
            try { if (dummyWorld != null) dummyWorld.clear(); }
            finally { dummyWorld = null; level = null; entityContent = null; loaded = null; tickBudget = 0; }
        }
    }
    @Override protected void onRemoved() { super.onRemoved(); release(); }
}
