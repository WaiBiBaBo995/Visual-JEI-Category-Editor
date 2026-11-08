package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.CreateAnimation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scoped local animation time; outside a VJCE preset the original clock is untouched. */
@Pseudo
@Mixin(targets="net.createmod.catnip.animation.AnimationTickHolder",remap=false)
public abstract class PonderClockMixin {
    @Inject(method={"getRenderTime()F","getRenderTime(Lnet/minecraft/world/level/LevelAccessor;)F"},at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void vjce$time(CallbackInfoReturnable<Float> cir) { var time=CreateAnimation.time();if(time!=null)cir.setReturnValue(time); }
}
