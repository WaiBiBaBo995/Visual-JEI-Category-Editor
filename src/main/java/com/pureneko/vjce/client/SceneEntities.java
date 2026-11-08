package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.*;

/** Only descriptions are serialized. Every scene owns fresh entities and UUIDs in its dummy level. */
final class SceneEntities implements ISceneEntityRenderHook {
    private record Entry(Entity entity, Vec3 position, float yaw, float pitch, float roll, float scale, Pose pose) {}
    private final List<Entry> entries = new ArrayList<>();
    private final Map<Entity, Entry> byEntity = new IdentityHashMap<>();
    AABB bounds;

    SceneEntities(TrackedDummyWorld world, ListTag rows) {
        if (rows.size() > 32) throw new IllegalArgumentException("Scene supports at most 32 entities");
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.getCompound(i);
            var id = ResourceLocation.tryParse(row.getString("id"));
            var type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null || type == EntityType.PLAYER) throw new IllegalArgumentException("Unsupported scene entity: " + row.getString("id"));
            Entity entity = type.create(world);
            if (entity == null) throw new IllegalArgumentException("Entity requires gameplay context: " + id);
            var data = row.getCompound("data").copy();
            if (data.contains("Passengers") || data.contains("RootVehicle")) throw new IllegalArgumentException("Scene passengers are not supported");
            for (String key : List.of("id", "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation")) data.remove(key);
            entity.load(data); entity.setUUID(UUID.randomUUID());
            double x = row.getDouble("x"), y = row.getDouble("y"), z = row.getDouble("z");
            if (!coordinate(x) || !coordinate(y) || !coordinate(z)) throw new IllegalArgumentException("Entity position must be finite and within -64..64");
            float yaw = row.getFloat("yaw"), pitch = row.getFloat("pitch"),roll=row.getFloat("roll");
            float scale = row.contains("scale") ? row.getFloat("scale") : 1;
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll) || !Float.isFinite(scale) || scale < .05f || scale > 16) throw new IllegalArgumentException("Invalid entity angle or scale (0.05..16)");
            Pose pose = row.getString("pose").isBlank() ? Pose.STANDING : Pose.valueOf(row.getString("pose").toUpperCase(Locale.ROOT));
            var entry = new Entry(entity, new Vec3(x, y, z), yaw, pitch, roll, scale, pose);
            anchor(entry); world.addEntity(entity);
            if (world.getEntity(entity.getId()) != entity) throw new IllegalArgumentException("Entity creation was cancelled: " + id);
            if (Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity) == null) throw new IllegalArgumentException("Entity has no renderer: " + id);
            entries.add(entry); byEntity.put(entity, entry);
            var box = entity.getBoundingBox();
            var anchor = entry.position;
            var scaled = new AABB(anchor.x + (box.minX - anchor.x) * scale, anchor.y + (box.minY - anchor.y) * scale, anchor.z + (box.minZ - anchor.z) * scale,
                    anchor.x + (box.maxX - anchor.x) * scale, anchor.y + (box.maxY - anchor.y) * scale, anchor.z + (box.maxZ - anchor.z) * scale);
            bounds = bounds == null ? scaled : bounds.minmax(scaled);
        }
    }
    private static boolean coordinate(double value) { return Double.isFinite(value) && value >= -64 && value <= 64; }
    private static void anchor(Entry entry) {
        var entity = entry.entity;
        entity.setNoGravity(true); entity.noPhysics = true; entity.setInvulnerable(true); entity.setSilent(true);
        if (entity instanceof Mob mob) mob.setNoAi(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.moveTo(entry.position.x, entry.position.y, entry.position.z, entry.yaw, entry.pitch);
        entity.setPose(entry.pose); entity.setYHeadRot(entry.yaw); entity.setYBodyRot(entry.yaw);
        entity.setOldPosAndRot();
        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot = entry.yaw;
            living.yHeadRotO = living.yHeadRot = entry.yaw;
        }
    }
    void anchorAll() { for (var entry : entries) anchor(entry); }
    void checkAlive() {
        for (var entry : entries) if (entry.entity.isRemoved()) throw new IllegalStateException("Entity cannot remain in an idle scene: " + BuiltInRegistries.ENTITY_TYPE.getKey(entry.entity.getType()));
    }
    List<Entity> entities() { return entries.stream().map(Entry::entity).toList(); }
    @Override public void applyEntity(Level world, Entity entity, PoseStack pose, float partialTick) {
        var entry = byEntity.get(entity); if (entry == null || entry.scale == 1&&entry.roll==0) return;
        var p = entry.position;
        pose.translate(p.x, p.y, p.z);pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(entry.roll)); pose.scale(entry.scale, entry.scale, entry.scale); pose.translate(-p.x, -p.y, -p.z);
    }
    static float frontQuarterYaw(float cameraYaw) {
        // LDLib's camera azimuth maps to Minecraft's forward yaw with a -90 degree offset.
        return Mth.wrapDegrees(cameraYaw - 90 - 45);
    }

    static CompoundTag defaults(String id) {
        var row = new CompoundTag(); row.putString("id", id);
        row.putDouble("x", 0); row.putDouble("y", 0); row.putDouble("z", 0);
        row.putFloat("yaw", frontQuarterYaw(BlockSceneElement.DEFAULT_CAMERA_YAW));
        row.putFloat("pitch", 0); row.putFloat("scale", 1); row.putString("pose", "STANDING");
        var data = new CompoundTag();
        try {
            data = switch (id) {
                case "minecraft:item" -> TagParser.parseTag("{Item:{id:\"minecraft:stone\",count:1},Age:-32768s,PickupDelay:32767s}");
                case "minecraft:item_display" -> TagParser.parseTag("{item:{id:\"minecraft:stone\",count:1}}");
                case "minecraft:block_display" -> TagParser.parseTag("{block_state:{Name:\"minecraft:stone\"}}");
                case "minecraft:falling_block" -> TagParser.parseTag("{BlockState:{Name:\"minecraft:sand\"},Time:1}");
                case "minecraft:text_display" -> TagParser.parseTag("{text:'{\"text\":\"VJCE\"}'}");
                default -> data;
            };
        } catch (Exception e) { throw new IllegalStateException(e); }
        row.put("data", data); return row;
    }
}
