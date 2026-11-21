package com.pureneko.vjce.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.pureneko.vjce.integration.NativeDrawing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Capture GUI elements while their render states still read from the active pose. */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiDrawingMixin {
    @Unique
    private GuiGraphicsExtractor vjce$graphics() {
        return (GuiGraphicsExtractor) (Object) this;
    }

    @WrapMethod(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V")
    private void vjce$texture(RenderPipeline pipeline, Identifier texture, int x1, int x2, int y1, int y2,
                              float u1, float u2, float v1, float v2, int color, Operation<Void> draw) {
        NativeDrawing.begin(vjce$graphics(), "texture", x1, y1, x2, y2);
        try {
            draw.call(pipeline, NativeDrawing.texture(texture), x1, x2, y1, y2, u1, u2, v1, v2, color);
        } finally {
            NativeDrawing.end();
        }
    }

    @WrapMethod(method = "innerBlit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;IIIIFFFFI)V")
    private void vjce$blit(RenderPipeline pipeline, GpuTextureView texture, GpuSampler sampler,
                           int x1, int x2, int y1, int y2, float u1, float u2, float v1, float v2,
                           int color, Operation<Void> draw) {
        NativeDrawing.begin(vjce$graphics(), "texture", x1, y1, x2, y2);
        try {
            draw.call(pipeline, texture, sampler, x1, x2, y1, y2, u1, u2, v1, v2, color);
        } finally {
            NativeDrawing.end();
        }
    }

    @WrapMethod(method = "innerFill")
    private void vjce$fill(RenderPipeline pipeline, TextureSetup texture, int x1, int y1, int x2, int y2,
                           int color, Integer secondColor, Operation<Void> draw) {
        NativeDrawing.begin(vjce$graphics(), secondColor == null ? "fill" : "gradient", x1, y1, x2, y2);
        try {
            draw.call(pipeline, texture, x1, y1, x2, y2, color, secondColor);
        } finally {
            NativeDrawing.end();
        }
    }

    @WrapMethod(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V")
    private void vjce$text(Font font, String text, int x, int y, int color, boolean shadow, Operation<Void> draw) {
        if (!NativeDrawing.active() || text == null) {
            draw.call(font, text, x, y, color, shadow);
            return;
        }
        NativeDrawing.begin(vjce$graphics(), "text", x, y, x + font.width(text), y + font.lineHeight);
        try {
            draw.call(font, NativeDrawing.text(text), x, y, NativeDrawing.color(color), shadow);
        } finally {
            NativeDrawing.end();
        }
    }

    @WrapMethod(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V")
    private void vjce$formatted(Font font, FormattedCharSequence text, int x, int y,
                                int color, boolean shadow, Operation<Void> draw) {
        if (!NativeDrawing.active() || text == null) {
            draw.call(font, text, x, y, color, shadow);
            return;
        }
        NativeDrawing.begin(vjce$graphics(), "text", x, y, x + font.width(text), y + font.lineHeight);
        try {
            draw.call(font, NativeDrawing.text(text), x, y, NativeDrawing.color(color), shadow);
        } finally {
            NativeDrawing.end();
        }
    }

    @WrapMethod(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V")
    private void vjce$item(LivingEntity entity, Level level, ItemStack item, int x, int y, int seed, Operation<Void> draw) {
        NativeDrawing.begin(vjce$graphics(), "item", x, y, x + 16, y + 16);
        try {
            draw.call(entity, level, item, x, y, seed);
        } finally {
            NativeDrawing.end();
        }
    }
}
