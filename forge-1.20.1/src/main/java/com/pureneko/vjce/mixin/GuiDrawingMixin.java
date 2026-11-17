package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.NativeDrawing;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Terminal drawing overloads only; nested item/font calls are guarded by the capture scope. */
@Mixin(GuiGraphics.class)
public abstract class GuiDrawingMixin {
    private GuiGraphics vjce$g(){return (GuiGraphics)(Object)this;}
    @Inject(method="innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V",at=@At("HEAD"))
    private void vjce$blit(ResourceLocation r,int x1,int x2,int y1,int y2,int z,float u1,float u2,float v1,float v2,CallbackInfo ci){NativeDrawing.begin(vjce$g(),"texture",x1,y1,x2,y2);}
    @Inject(method="innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V",at=@At("HEAD"))
    private void vjce$colorBlit(ResourceLocation r,int x1,int x2,int y1,int y2,int z,float u1,float u2,float v1,float v2,float red,float green,float blue,float alpha,CallbackInfo ci){NativeDrawing.begin(vjce$g(),"texture",x1,y1,x2,y2);}
    @Inject(method={"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V","innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"},at=@At("RETURN"))
    private void vjce$endBlit(CallbackInfo ci){NativeDrawing.end();}
    @ModifyArg(method={"innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFF)V","innerBlit(Lnet/minecraft/resources/ResourceLocation;IIIIIFFFFFFFF)V"},at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"),index=1)
    private ResourceLocation vjce$texture(ResourceLocation texture){return NativeDrawing.texture(texture);}
    @Inject(method="fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V",at=@At("HEAD"))
    private void vjce$fill(RenderType type,int x1,int y1,int x2,int y2,int z,int color,CallbackInfo ci){NativeDrawing.begin(vjce$g(),"fill",x1,y1,x2,y2);}
    @Inject(method="fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V",at=@At("RETURN"))
    private void vjce$endFill(CallbackInfo ci){NativeDrawing.end();}
    @Inject(method="fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V",at=@At("HEAD"))
    private void vjce$gradient(VertexConsumer out,int x1,int y1,int x2,int y2,int z,int top,int bottom,CallbackInfo ci){NativeDrawing.begin(vjce$g(),"gradient",x1,y1,x2,y2);}
    @Inject(method="fillGradient(Lcom/mojang/blaze3d/vertex/VertexConsumer;IIIIIII)V",at=@At("RETURN"))
    private void vjce$endGradient(CallbackInfo ci){NativeDrawing.end();}
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",at=@At("HEAD"),remap=false)
    private void vjce$text(Font font,String text,float x,float y,int color,boolean shadow,CallbackInfoReturnable<Integer> ci){NativeDrawing.begin(vjce$g(),"text",x,y,x+font.width(text),y+font.lineHeight);}
    @Inject(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",at=@At("HEAD"),remap=false)
    private void vjce$formatted(Font font,FormattedCharSequence text,float x,float y,int color,boolean shadow,CallbackInfoReturnable<Integer> ci){NativeDrawing.begin(vjce$g(),"text",x,y,x+font.width(text),y+font.lineHeight);}
    @Inject(method={"drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I","drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I"},at=@At("RETURN"),remap=false)
    private void vjce$endText(CallbackInfoReturnable<Integer> ci){NativeDrawing.end();}
    @ModifyArg(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",remap=true),index=0,remap=false)
    private String vjce$string(String text){return NativeDrawing.text(text);}
    @ModifyArg(method="drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;FFIZ)I",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",remap=true),index=3,remap=false)
    private int vjce$stringColor(int color){return NativeDrawing.color(color);}
    @ModifyArg(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",remap=true),index=0,remap=false)
    private FormattedCharSequence vjce$sequence(FormattedCharSequence text){return NativeDrawing.text(text);}
    @ModifyArg(method="drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;FFIZ)I",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",remap=true),index=3,remap=false)
    private int vjce$sequenceColor(int color){return NativeDrawing.color(color);}
    @Inject(method="renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",at=@At("HEAD"))
    private void vjce$item(LivingEntity entity,Level level,ItemStack stack,int x,int y,int seed,int z,CallbackInfo ci){NativeDrawing.begin(vjce$g(),"item",x,y,x+16,y+16);}
    @Inject(method="renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",at=@At("RETURN"))
    private void vjce$endItem(CallbackInfo ci){NativeDrawing.end();}
}
