package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.pureneko.vjce.client.NativePartElement;
import com.pureneko.vjce.client.IngredientSlot;
import com.pureneko.vjce.data.CategoryDocument;
import com.pureneko.vjce.mixin.*;
import dev.vfyjxf.taffy.style.TaffyPosition;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.*;
import mezz.jei.api.gui.widgets.*;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.gui.elements.TextWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.crafting.*;
import java.util.*;

/** A live, focus-specific original JEI layout. Only element identities/placements go into the template. */
@SuppressWarnings({"rawtypes", "unchecked", "removal"})
public final class NativePage {
    private static final Map<UIElement, NativePage> CONTEXTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<String, Object> PREVIEWS = new LinkedHashMap<>(64, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Object> eldest) { return size() > 512; }
    };
    public final IRecipeCategory original;
    public final Object recipe;
    public final IFocusGroup focuses;
    public final IRecipeLayoutDrawable layout;
    public final RecipeLayoutAccess access;
    public final Map<String, Part> parts = new LinkedHashMap<>();
    public final NativeDrawing drawing = new NativeDrawing();
    private java.lang.ref.WeakReference<UIElement> drawingRoot=new java.lang.ref.WeakReference<>(null);
    public final List<IRecipeWidget> unknown = new ArrayList<>();
    private final List<Integer> slotIndices = new ArrayList<>();
    private boolean runtime;
    private boolean readOnly;
    private CategoryDocument.Recipe displayData;
    private long lastTick = Long.MIN_VALUE;
    public record Part(String key, IRecipeWidget widget, int x, int y, int width, int height, String name) {}
    public static final class UnsupportedLayout extends UnsupportedOperationException {
        UnsupportedLayout() { super("Source widgets own recipe slots; preserve the original read-only page"); }
    }

    private NativePage(IRecipeCategory original, Object recipe, IFocusGroup focuses, boolean runtime) {
        this(original, recipe, focuses, runtime, true);
    }
    private NativePage(IRecipeCategory original, Object recipe, IFocusGroup focuses, boolean runtime, boolean capture) {
        this.original = original; this.recipe = recipe; this.focuses = focuses; this.runtime = runtime;
        layout = (IRecipeLayoutDrawable) VjceJeiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(original, recipe, focuses)
                .orElseThrow();
        layout.setPosition(0, 0);
        access = (RecipeLayoutAccess) layout;
        if (capture) {
            var captured = CapturedCategory.capture(original, recipe, focuses);
            for (int i = 0; i < captured.slots.size(); i++) if (!captured.slots.get(i).invisible) slotIndices.add(i);
        } else for (int i = 0; i < access.vjce$slots().size(); i++) slotIndices.add(i);
        access.vjce$createExtras();
        if (!capture) { readOnly = true; return; }
        if (layout.getRecipeSlotsView().getSlotViews().stream().filter(IRecipeSlotDrawable.class::isInstance)
                .anyMatch(slot -> !access.vjce$slots().contains(slot))) throw new UnsupportedLayout();
        MekElements.discover(original, drawing);
        var counts = new HashMap<String, Integer>();
        for (IRecipeWidget widget : access.vjce$widgets()) {
            if (!(widget instanceof ITextWidget) && !(widget instanceof IDrawableWidget)) { unknown.add(widget); continue; }
            String name = widget instanceof ITextWidget ? "text" : "drawable";
            if (widget instanceof TextWidgetAccess text && !text.vjce$text().isEmpty()) {
                var content = text.vjce$text().getFirst();
                if (content instanceof Component component && component.getContents() instanceof TranslatableContents translated)
                    name = "text_" + translated.getKey().replaceAll("[^A-Za-z0-9_]", "_");
            }
            int number = counts.merge(name, 1, Integer::sum) - 1;
            String key = "jei_" + name + "_" + number;
            int w = widget instanceof ITextWidget text ? text.getWidth() : ((IDrawableWidget) widget).getWidth();
            int h = widget instanceof ITextWidget text ? text.getHeight() : ((IDrawableWidget) widget).getHeight();
            parts.put(key, new Part(key, widget, widget.getPosition().x(), widget.getPosition().y(), Math.max(1, w), Math.max(1, h), name));
        }
    }
    public static void clear() { CONTEXTS.clear(); PREVIEWS.clear(); NativeSlotTransforms.clear(); }
    public static void remember(IRecipeCategory original, Object recipe, CategoryDocument.Recipe data) {
        PREVIEWS.put(original.getRecipeType().getUid() + "|" + data.source, recipe);
    }
    public static NativePage create(IRecipeCategory original, Object raw, CategoryDocument document, CategoryDocument.Recipe data, IFocusGroup focuses, boolean runtime) {
        Object displayed = raw;
        var before = CapturedCategory.capture(original, raw, focuses);
        boolean metadataChanged = data.duration != before.duration || !before.values.entrySet().containsAll(data.values.entrySet());
        if (VanillaDisplays.supports(original.getRecipeType().getUid()) && metadataChanged && ((raw instanceof RecipeHolder<?> holder && holder.value() instanceof AbstractCookingRecipe)
                || raw instanceof mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe
                || raw instanceof mezz.jei.api.recipe.vanilla.IJeiCompostingRecipe
                || raw instanceof mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe)) {
            var changed = com.pureneko.vjce.data.ProjectStore.JSON.fromJson(com.pureneko.vjce.data.ProjectStore.JSON.toJson(before), CategoryDocument.Recipe.class);
            changed.duration = data.duration; changed.values.putAll(data.values);
            displayed = VanillaDisplays.create(original.getRecipeType().getUid(), changed);
        }
        var page = new NativePage(original, displayed, focuses, runtime);
        page.displayData = data;
        page.applyPreviewIngredients();
        return page;
    }
    public static NativePage preview(CategoryDocument document, CategoryDocument.Recipe data) {
        if (VjceJeiPlugin.runtime == null) return null;
        IRecipeCategory original = VjceJeiPlugin.originals.get(document.target);
        if (original == null) return null;
        Object raw = PREVIEWS.get(document.target + "|" + data.source);
        if (raw == null && !data.source.isBlank()) {
            raw = VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).includeHidden().get()
                    .filter(r -> data.source.equals(String.valueOf(original.getRegistryName(r)))).findFirst().orElse(null);
            if (raw == null && net.minecraft.client.Minecraft.getInstance().level != null) {
                var holder = net.minecraft.client.Minecraft.getInstance().level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(data.source));
                if (holder.isPresent() && original.getRecipeType().getRecipeClass().isInstance(holder.get())) raw = holder.get();
            }
        }
        if (raw == null && data.source.isBlank() && !data.slots.isEmpty() && VanillaDisplays.supports(original.getRecipeType().getUid())) raw = VanillaDisplays.create(original.getRecipeType().getUid(), data);
        if (raw == null) raw = VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().findFirst().orElse(null);
        if (raw == null) return null;
        return create(original, raw, document, data, VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of()), false);
    }
    public static NativePage uneditedPreview(CategoryDocument document) {
        if (VjceJeiPlugin.runtime == null) return null;
        IRecipeCategory original = VjceJeiPlugin.originals.get(document.target);
        if (original == null) return null;
        var raw = VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().findFirst();
        if (raw.isEmpty()) return null;
        try { return new NativePage(original, raw.get(), VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of()), false, false); }
        catch (RuntimeException failure) { return null; }
    }
    public boolean runtime() { return runtime; }
    public IRecipeSlotDrawable previewSlot(NativePartElement element) {
        if (runtime || displayData == null || !element.part.startsWith("slot_")) return null;
        var slot = displayData.boundSlot(element.getId());
        if (slot == null) return null;
        int index = slotIndices.indexOf(displayData.sourceSlotIndex(displayData.slots.indexOf(slot)));
        return index >= 0 && index < access.vjce$slots().size() ? access.vjce$slots().get(index) : null;
    }
    public void bind(UI ui, CategoryDocument document) {
        if (!document.nativeOriginElements.isEmpty()) {
            var reference = templateElements(document);
            for (var child : reference.getChildren().stream().toList()) {
                if (!document.nativeOriginElements.contains(child.getId()) && ui.rootElement.selectId(child.getId()).findAny().isEmpty()) {
                    child.removeSelf(); ui.rootElement.addChild(child);
                    if (!runtime) document.nativeOriginElements.add(child.getId());
                }
            }
        }
        // allowHitTest is runtime state and is not carried by UITemplate serialization.
        ui.rootElement.select("*").filter(NativePartElement.class::isInstance).map(NativePartElement.class::cast)
                .filter(part -> part.part.equals("overlay")).forEach(part -> part.setAllowHitTest(false));
        for(String key:List.of("native_layer","native_overlay")){var layer=part(ui.rootElement,key);if(layer!=null){layer.sourceWidth=original.getWidth();layer.sourceHeight=original.getHeight();}}
        CONTEXTS.put(ui.rootElement, this); drawingRoot=new java.lang.ref.WeakReference<>(ui.rootElement); drawing.bind(ui, document, runtime);
    }
    public static NativePage context(UIElement element) { var root = boundRoot(element); return root == null ? null : CONTEXTS.get(root); }
    public static UIElement boundRoot(UIElement element) {
        for (var current = element; current != null; current = current.getParent()) if (CONTEXTS.containsKey(current)) return current;
        return null;
    }
    public UITemplate template(CategoryDocument document) {
        var root = templateElements(document);
        if (!runtime) document.nativeOriginElements = root.select("*").map(UIElement::getId).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return com.pureneko.vjce.client.RecipeUI.canvasTemplate(UITemplate.of(root));
    }
    private UIElement templateElements(CategoryDocument document) {
        var root = new UIElement().layout(l -> { l.width(document.width); l.height(document.height); });
        root.setOverflowVisible(true);
        if (readOnly) return root.addChild(element("native_layer", "page", "Original read-only JEI page", 0, 0, original.getWidth(), original.getHeight()));
        root.addChild(element("native_layer", "native", "JEI source drawing", 0, 0, original.getWidth(), original.getHeight()));
        int index = 0;
        for (IRecipeSlotDrawable slot : access.vjce$slots()) {
            var area = slot.getRect();
            int dataIndex = index < slotIndices.size() ? slotIndices.get(index) : index;
            root.addChild(element("slot_" + dataIndex, "slot_" + dataIndex, slot.getSlotName().orElse("JEI slot " + index), area.getX(), area.getY(), area.getWidth(), area.getHeight()));
            index++;
        }
        for (var part : parts.values()) root.addChild(element(part.key, part.key, part.name, part.x, part.y, part.width, part.height));
        root.addChild(element("native_overlay", "overlay", "JEI source widgets / decorators", 0, 0, original.getWidth(), original.getHeight()));
        drawing.populate(root);
        return root;
    }
    private static NativePartElement element(String id, String part, String label, int x, int y, int width, int height) {
        var element = new NativePartElement(); element.setId(id); element.part = part; element.sourceLabel = label;
        element.setOverflowVisible(true);
        element.sourceX=x; element.sourceY=y; element.sourceWidth=width; element.sourceHeight=height;
        if (part.equals("overlay")) element.setAllowHitTest(false);
        element.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(x); l.top(y); l.width(width); l.height(height); });
        return element;
    }
    public static NativePartElement part(UIElement root, String key) { return root.selectId(key, NativePartElement.class).findFirst().orElse(null); }
    public static boolean visible(UIElement element) {
        for (var current = element; current != null; current = current.getParent())
            if (!current.isDisplayed() || !current.isVisible() || current.getStyle().opacity() <= 0) return false;
        return element != null;
    }
    public static int x(UIElement root, UIElement element) { return Math.round(element.getContentX() - root.getContentX()); }
    public static int y(UIElement root, UIElement element) { return Math.round(element.getContentY() - root.getContentY()); }
    public void tickOnce() {
        long now = net.minecraft.Util.getMillis() / 50;
        if (now != lastTick) { lastTick = now; layout.tick(); applyPreviewIngredients(); }
    }
    private void applyPreviewIngredients() {
        if (displayData == null || !displayData.overrideIngredients) return;
        for (int i = 0; i < access.vjce$slots().size() && i < slotIndices.size(); i++) {
            int index = displayData.dataSlotIndex(slotIndices.get(i));
            if (index >= 0) access.vjce$slots().get(i).createDisplayOverrides().addOptionalTypedIngredients(IngredientAccess.decodeChoices(displayData.slots.get(index)));
        }
    }
    public void drawElement(NativePartElement element, GUIContext context) {
        tickOnce();
        var root = boundRoot(element); if (root == null) return;
        var graphics = context.graphics;
        double mouseX = context.localMouseX - element.getContentX(), mouseY = context.localMouseY - element.getContentY();
        graphics.flush(); graphics.pose().pushPose();
        try {
            graphics.pose().translate(element.getContentX(), element.getContentY(), 0);
            if (element.part.equals("page")) {
                graphics.pose().scale(element.getContentWidth() / Math.max(1, original.getWidth()), element.getContentHeight() / Math.max(1, original.getHeight()), 1);
                layout.drawRecipe(graphics, (int) (mouseX * original.getWidth() / Math.max(1, element.getContentWidth())), (int) (mouseY * original.getHeight() / Math.max(1, element.getContentHeight())));
            } else if (element.part.equals("native")) {
                graphics.pose().scale(element.getContentWidth() / Math.max(1, original.getWidth()), element.getContentHeight() / Math.max(1, original.getHeight()), 1);
                access.vjce$background().draw(graphics, layout.getRectWithBorder());
                drawBase(graphics, layout.getRecipeSlotsView(), mouseX * original.getWidth() / Math.max(1, element.getContentWidth()), mouseY * original.getHeight() / Math.max(1, element.getContentHeight()));
            } else if (element.part.equals("overlay")) {
                graphics.pose().scale(element.getContentWidth() / Math.max(1, original.getWidth()), element.getContentHeight() / Math.max(1, original.getHeight()), 1);
                {
                    drawOverlay(graphics, layout.getRecipeSlotsView(), mouseX * original.getWidth() / Math.max(1, element.getContentWidth()), mouseY * original.getHeight() / Math.max(1, element.getContentHeight()));
                }
            } else if (element.part.startsWith("slot_")) {
                var slot = previewSlot(element);
                if (slot != null) {
                    var area = slot.getRect();
                    graphics.pose().scale(element.getContentWidth() / Math.max(1, area.getWidth()), element.getContentHeight() / Math.max(1, area.getHeight()), 1);
                    graphics.pose().translate(-area.getX(), -area.getY(), 0);
                    slot.draw(graphics, element.isHover());
                } else if (displayData != null) {
                    var data = displayData.boundSlot(element.getId());
                    if (data != null) {
                        var background = VjceJeiPlugin.helpers.getGuiHelper().getSlotDrawable();
                        graphics.pose().scale(element.getContentWidth() / 16, element.getContentHeight() / 16, 1);
                        background.draw(graphics, -1, -1);
                        var ingredients = IngredientAccess.decode(data);
                        if (!ingredients.isEmpty()) IngredientSlot.drawIngredient(graphics, ingredients.getFirst(), 0, 0, 16, 16,
                                data.ingredients.getFirst().amount);
                    }
                }
            } else {
                var part = parts.get(element.part);
                if (part != null) {
                    graphics.pose().scale(element.getContentWidth() / part.width, element.getContentHeight() / part.height, 1);
                    widget(part, element).drawWidget(graphics, mouseX * part.width / Math.max(1, element.getContentWidth()), mouseY * part.height / Math.max(1, element.getContentHeight()));
                }
            }
            graphics.flush();
        } finally { graphics.pose().popPose(); }
    }
    public IRecipeWidget widget(Part part, NativePartElement element) {
        if (part.widget instanceof TextWidgetAccess text && (!element.replacementText.isEmpty() || element.overrideTextColor)) {
            var replacement = new TextWidget(element.replacementText.isEmpty() ? text.vjce$text() : List.of(Component.literal(element.replacementText)), 0, 0, part.width, part.height);
            replacement.setFont(text.vjce$font()); replacement.setShadow(text.vjce$shadow()); replacement.setLineSpacing(text.vjce$spacing());
            replacement.setTextAlignment(text.vjce$horizontal()); replacement.setTextAlignment(text.vjce$vertical());
            if (element.overrideTextColor) replacement.setColor(element.textColor); else if (text.vjce$color() != null) replacement.setColor(text.vjce$color());
            return replacement;
        }
        return part.widget;
    }
    public void drawBase(GuiGraphics graphics, IRecipeSlotsView slots, double mouseX, double mouseY) {
        var background = original.getBackground(); if (background != null) background.draw(graphics);
        var sourceRoot=drawingRoot.get();var layer=sourceRoot==null?null:part(sourceRoot,"native_layer");
        if(layer==null)original.draw(recipe,slots,graphics,mouseX,mouseY);
        else try(var scope=drawing.open(sourceRoot,graphics,layer,mouseX,mouseY)){original.draw(recipe, slots, graphics, mouseX, mouseY);}
    }
    public void drawUnknown(GuiGraphics graphics, double mouseX, double mouseY) {
        for (var widget : unknown) {
            var pos = widget.getPosition(); graphics.pose().pushPose();
            try { graphics.pose().translate(pos.x(), pos.y(), 0); widget.drawWidget(graphics, mouseX - pos.x(), mouseY - pos.y()); }
            finally { graphics.pose().popPose(); }
        }
        for (var drawable : access.vjce$drawables()) drawable.draw(graphics);
    }
    public void drawOverlay(GuiGraphics graphics, IRecipeSlotsView slots, double mouseX, double mouseY) {
        var sourceRoot=drawingRoot.get();var layer=sourceRoot==null?null:part(sourceRoot,"native_overlay");
        if(layer==null)drawOverlaySource(graphics,slots,mouseX,mouseY);
        else try(var scope=drawing.open(sourceRoot,graphics,layer,mouseX,mouseY)){drawOverlaySource(graphics,slots,mouseX,mouseY);}
    }
    private void drawOverlaySource(GuiGraphics graphics,IRecipeSlotsView slots,double mouseX,double mouseY){
        drawUnknown(graphics, mouseX, mouseY);
        for (var decorator : access.vjce$decorators()) ((mezz.jei.api.recipe.category.extensions.IRecipeCategoryDecorator) decorator).draw(recipe, original, slots, graphics, mouseX, mouseY);
        if (access.vjce$shapeless() != null) access.vjce$shapeless().draw(graphics);
    }
}
