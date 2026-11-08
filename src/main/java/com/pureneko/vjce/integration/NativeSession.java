package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIJEIWidget;
import com.mojang.blaze3d.platform.InputConstants;
import com.pureneko.vjce.client.*;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.gui.inputs.*;
import mezz.jei.api.gui.widgets.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.input.IInputTarget;
import mezz.jei.common.input.IInternalKeyMappings;
import mezz.jei.common.input.UserInput;
import mezz.jei.common.input.interaction.ApiInputAdapter;
import mezz.jei.common.input.interaction.IInputInteraction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.*;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** One session per actual recipe+focus layout. Native drawing and input share the same matrix. */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class NativeSession {
    public final NativePage page;
    public final ModularUI ui;
    public final CategoryDocument.Recipe data;
    public List<String> slotKeys = List.of();
    public List<IRecipeSlotDrawable> actualSlots = List.of();
    private final IRecipeSlotsView completeSlots = () -> List.copyOf(actualSlots);
    public NativeSession(IRecipeCategory original, Object recipe, CategoryDocument document, CategoryDocument.Recipe data, IFocusGroup focuses) {
        this.data = data; page = NativePage.create(original, recipe, document, data, focuses, true);
        var template = document.layout.isBlank() ? page.template(document) : RecipeUI.savedTemplate(document);
        var content = template.createUI(); page.bind(content, document); RecipeUI.bind(content, document, data, true);
        ui = new ModularUI(content); ui.setAllowDebugMode(false); ui.setDrawTooltips(false); ui.init(document.width, document.height);
    }
    public UIElement root() { return ui.ui.rootElement; }
    private NativePartElement element(String key) { return NativePage.part(root(), key); }
    private boolean visible(UIElement e) { return NativePage.visible(e) && (!(e instanceof NativePartElement p) || p.useSourceDrawing); }
    private ElementTransform transform(UIElement e, float w, float h) { return new ElementTransform(root(), e, w, h); }
    private ElementTransform pageTransform(UIElement e) { return transform(e, page.original.getWidth(), page.original.getHeight()); }
    private org.joml.Vector2f inputPoint(ElementTransform t, double x, double y) {
        var area = t.area(); return t.local(x + area.left(), y + area.top());
    }
    private org.joml.Vector2f sourceInputPoint(ElementTransform t,double x,double y) {
        var p=inputPoint(t,x,y);var point=t.world(p.x,p.y);return page.drawing.sourcePoint(root(),point.x,point.y,p.x,p.y);
    }
    private void withTransform(GuiGraphics graphics, ElementTransform t, Runnable drawing) {
        graphics.flush(); graphics.pose().pushPose();
        try { t.apply(graphics); drawing.run(); graphics.flush(); } finally { graphics.pose().popPose(); }
    }
    public void drawBase(GuiGraphics graphics, IRecipeSlotsView slots, double x, double y) {
        var e = element("native_layer"); if (!visible(e)) return; var t = pageTransform(e); var p = t.local(x, y);
        withTransform(graphics, t, () -> {
            page.access.vjce$background().draw(graphics, page.layout.getRectWithBorder());
            page.drawBase(graphics, actualSlots.isEmpty() ? slots : completeSlots, p.x, p.y);
        });
    }
    public void extras(IRecipeExtrasBuilder builder) {
        if (builder instanceof com.pureneko.vjce.mixin.RecipeLayoutAccess access) {
            access.vjce$decorators(List.of()); access.vjce$shapeless(null);
            access.vjce$background((graphics, x, y, width, height) -> {});
        }
        // Taking ownership removes category slots. Keep the complete view for source callbacks.
        actualSlots = List.copyOf(builder.getRecipeSlots().getSlots());
        var owned = new ArrayList<Map.Entry<UIElement, IRecipeSlotDrawable>>();
        for (int i = 0; i < actualSlots.size() && i < slotKeys.size(); i++) {
            var e = RecipeUI.slotElement(root(), slotKeys.get(i));
            if (e != null) owned.add(Map.entry(e, actualSlots.get(i)));
        }
        builder.addSlottedWidget(new ISlottedRecipeWidget() {
            @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
            @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x, double y) {
                for (int i = owned.size() - 1; i >= 0; i--) {
                    var pair = owned.get(i); if (!visible(pair.getKey())) continue; var r = pair.getValue().getRect();
                    var t = transform(pair.getKey(), r.getWidth(), r.getHeight()); NativeSlotTransforms.register(pair.getValue(), t);
                    if (t.contains(x, y)) return Optional.of(new RecipeSlotUnderMouse(pair.getValue(), 0, 0));
                }
                return Optional.empty();
            }
            @Override public void drawWidget(GuiGraphics graphics, double x, double y) {
                for (var pair : owned) {
                    if (!visible(pair.getKey())) continue; var slot = pair.getValue(); var r = slot.getRect();
                    var t = transform(pair.getKey(), r.getWidth(), r.getHeight()); NativeSlotTransforms.register(slot, t);
                    withTransform(graphics, t, () -> { graphics.pose().translate(-r.getX(), -r.getY(), 0); slot.draw(graphics, t.contains(x, y)); });
                }
            }
        }, owned.stream().map(Map.Entry::getValue).toList());
        for (var part : page.parts.values()) {
            var e = element(part.key()); if (!visible(e)) continue;
            builder.addWidget(new IRecipeWidget() {
                @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
                @Override public ScreenRectangle getScreenRectangle() { return transform(e, part.width(), part.height()).area(); }
                @Override public void drawWidget(GuiGraphics graphics, double x, double y) {
                    var t = transform(e, part.width(), part.height()); var p = t.local(x, y);
                    withTransform(graphics, t, () -> page.widget(part, e).drawWidget(graphics, p.x, p.y));
                }
                @Override public void getTooltip(ITooltipBuilder tooltip, double x, double y) {
                    var t = transform(e, part.width(), part.height()); if (!t.contains(x, y)) return;
                    var p = t.local(x, y); part.widget().getTooltip(tooltip, p.x, p.y);
                }
            });
        }
        var overlay = element("native_overlay");
        builder.addWidget(new IRecipeWidget() {
            @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
            @Override public ScreenRectangle getScreenRectangle() { return overlay == null ? new ScreenRectangle(0, 0, 0, 0) : pageTransform(overlay).area(); }
            @Override public void tick() { page.layout.tick(); }
            @Override public void drawWidget(GuiGraphics graphics, double x, double y) {
                if (!visible(overlay)) return; var t = pageTransform(overlay); var p = t.local(x, y);
                withTransform(graphics, t, () -> page.drawOverlay(graphics, completeSlots, p.x, p.y));
            }
            @Override public void getTooltip(ITooltipBuilder tooltip, double x, double y) {
                if (!visible(overlay)) return; var t = pageTransform(overlay); if (!t.contains(x, y)) return; var p = t.local(x, y);
                for (var widget : page.unknown) {
                    var area = widget.getScreenRectangle();
                    if (area == null || area.containsPoint((int) p.x, (int) p.y)) widget.getTooltip(tooltip, p.x - widget.getPosition().x(), p.y - widget.getPosition().y());
                }
                for (var decorator : page.access.vjce$decorators()) ((mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator) decorator).decorateTooltips(tooltip, page.recipe, page.original, completeSlots, p.x, p.y);
                var shapeless = page.access.vjce$shapeless();
                if (shapeless != null && shapeless.isMouseOver((int) p.x, (int) p.y) && tooltip instanceof mezz.jei.common.gui.JeiTooltip nativeTooltip) shapeless.addTooltip(nativeTooltip);
            }
        });
        var widget = new ModularUIJEIWidget(ui); builder.addWidget(widget); builder.addGuiEventListener(widget);
        var sourceInput = page.layout.getInputHandler();
        if(sourceInput instanceof com.pureneko.vjce.mixin.GuiInputAccess access) {
            var iterator=access.vjce$listeners().iterator();
            while(iterator.hasNext()) {
                var listener=iterator.next();var source=MekElements.listenerElement(listener);if(source==null)continue;
                String key=page.drawing.componentKey(source);var e=key==null?null:element(key);var piece=key==null?null:page.drawing.pieces.get(key);if(e==null||piece==null)continue;
                iterator.remove();
                builder.addGuiEventListener(new IJeiGuiEventListener(){
                    private ElementTransform t(){return transform(e,piece.width(),piece.height());}
                    private org.joml.Vector2f point(double x,double y){var a=t().area();double rx=x+a.left(),ry=y+a.top();return visible(e)&&t().contains(rx,ry)?t().local(rx,ry):new org.joml.Vector2f(-100000,-100000);}
                    @Override public ScreenRectangle getArea(){return visible(e)?t().area():new ScreenRectangle(0,0,0,0);}
                    @Override public void mouseMoved(double x,double y){var p=point(x,y);listener.mouseMoved(p.x,p.y);}
                    @Override public boolean mouseClicked(double x,double y,int b){var p=point(x,y);return p.x>=0&&listener.mouseClicked(p.x,p.y,b);}
                    @Override public boolean mouseReleased(double x,double y,int b){var p=point(x,y);return p.x>=0&&listener.mouseReleased(p.x,p.y,b);}
                    @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){var p=point(x,y);var d=t().direction(dx,dy);return p.x>=0&&listener.mouseDragged(p.x,p.y,b,d.x,d.y);}
                    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){var p=point(x,y);return p.x>=0&&listener.mouseScrolled(p.x,p.y,dx,dy);}
                    @Override public boolean keyPressed(double x,double y,int key,int scan,int mods){var p=point(x,y);return p.x>=0&&listener.keyPressed(p.x,p.y,key,scan,mods);}
                });
            }
        }
        for(var piece:page.drawing.pieces.values())if(piece.key().startsWith("draw_component_mek_")) {
            var e=element(piece.key());if(e==null)continue;
            builder.addWidget(new IRecipeWidget(){
                @Override public ScreenPosition getPosition(){return new ScreenPosition(0,0);}
                @Override public ScreenRectangle getScreenRectangle(){return transform(e,piece.width(),piece.height()).area();}
                @Override public void getTooltip(ITooltipBuilder tooltip,double x,double y){if(!visible(e)||!transform(e,piece.width(),piece.height()).contains(x,y))return;var text=page.drawing.tooltips.get(piece.key());if(text!=null)tooltip.add(text);}
            });
        }

        class OverlayInput implements IJeiInputHandler, IInputTarget {
            @Override public ScreenRectangle getArea() { return overlay == null ? new ScreenRectangle(0, 0, 0, 0) : pageTransform(overlay).area(); }
            @Override public Optional<IInputInteraction> beginInput(Screen screen, UserInput input, IInternalKeyMappings keys) {
                if (!visible(overlay)) return Optional.empty();
                var point = sourceInputPoint(pageTransform(overlay), input.getMouseX(), input.getMouseY());
                if (!inSource(point)) return Optional.empty();
                // The layout's compatibility handleInput claims every simulated mouse press.
                return ApiInputAdapter.beginInput(screen, input.withMousePosition(point.x, point.y), keys, sourceInput)
                        .map(this::transformInteraction);
            }
            private IInputInteraction transformInteraction(IInputInteraction source) {
                return new IInputInteraction() {
                    @Override public void complete(UserInput input) {
                        var point = sourceInputPoint(pageTransform(overlay), input.getMouseX(), input.getMouseY());
                        source.complete(input.withMousePosition(point.x, point.y));
                    }
                    @Override public void drag(double x, double y, InputConstants.Key button, double dx, double dy) {
                        var transform = pageTransform(overlay);
                        var point = sourceInputPoint(transform, x, y);
                        var delta = transform.direction(dx, dy);
                        source.drag(point.x, point.y, button, delta.x, delta.y);
                    }
                    @Override public boolean capturesMouseMovement() { return source.capturesMouseMovement(); }
                    @Override public void cancel() { source.cancel(); }
                    @Override public boolean keepsCaptureOnRepeatedPress() { return source.keepsCaptureOnRepeatedPress(); }
                };
            }
            @Override public boolean handleInput(double x, double y, IJeiUserInput input) {
                if (!visible(overlay) || input.isSimulate()) return false;
                var p = sourceInputPoint(pageTransform(overlay), x, y);
                return inSource(p) && sourceInput.handleInput(p.x, p.y, input);
            }
            @Override public boolean handleMouseScrolled(double x, double y, double sx, double sy) {
                if (!visible(overlay)) return false; var t = pageTransform(overlay); var p = sourceInputPoint(t, x, y);
                if (p.x < 0 || p.y < 0 || p.x >= page.original.getWidth() || p.y >= page.original.getHeight()) return false;
                return sourceInput.handleMouseScrolled(p.x, p.y, sx, sy);
            }
            @Override public boolean handleMouseDragged(double x, double y, com.mojang.blaze3d.platform.InputConstants.Key key, double dx, double dy) {
                if (!visible(overlay)) return false; var t = pageTransform(overlay); var p = sourceInputPoint(t, x, y); var d = t.direction(dx, dy);
                return sourceInput.handleMouseDragged(p.x, p.y, key, d.x, d.y);
            }
            @Override public void handleMouseMoved(double x, double y) { if (visible(overlay)) { var p = sourceInputPoint(pageTransform(overlay), x, y); sourceInput.handleMouseMoved(p.x, p.y); } }
        }
        builder.addInputHandler(new OverlayInput());
    }
    private boolean inSource(org.joml.Vector2f point) {
        return point.x >= 0 && point.y >= 0 && point.x < page.original.getWidth() && point.y < page.original.getHeight();
    }
    @SuppressWarnings("removal")
    public boolean legacyInput(double x, double y, InputConstants.Key key) {
        var overlay = element("native_overlay");
        if (!visible(overlay)) return false;
        var point = pageTransform(overlay).local(x, y);
        point = page.drawing.sourcePoint(root(), x, y, point.x, point.y);
        return inSource(point) && page.original.handleInput(page.recipe, point.x, point.y, key);
    }
    public void tooltip(ITooltipBuilder tooltip, IRecipeSlotsView slots, double x, double y) {
        var e = element("native_layer"); if (!visible(e)) return; var t = pageTransform(e); if (!t.contains(x, y)) return;
        var p = t.local(x, y); p=page.drawing.sourcePoint(root(),x,y,p.x,p.y); page.original.getTooltip(tooltip, page.recipe, actualSlots.isEmpty() ? slots : completeSlots, p.x, p.y);
    }
}
