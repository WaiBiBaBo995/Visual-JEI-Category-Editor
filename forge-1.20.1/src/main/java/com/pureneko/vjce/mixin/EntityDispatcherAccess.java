package com.pureneko.vjce.mixin;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.level.Level;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityRenderDispatcher.class)
public interface EntityDispatcherAccess {
    @Accessor("level") Level vjce$level();
    @Accessor("level") void vjce$level(Level level);
    @Accessor("cameraOrientation") Quaternionf vjce$orientation();
    @Accessor("cameraOrientation") void vjce$orientation(Quaternionf orientation);
    @Accessor("shouldRenderShadow") boolean vjce$shadow();
    @Accessor("renderHitBoxes") boolean vjce$hitBoxes();
}
