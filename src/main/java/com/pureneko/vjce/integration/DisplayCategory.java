package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.integration.xei.jei.*;
import com.lowdragmc.lowdraglib2.integration.xei.jei.handler.JEIRecipeSlotHandler;
import com.pureneko.vjce.client.RecipeUI;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.*;
import mezz.jei.api.recipe.*;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Function;

/** Same LDLib UI is used by the editor preview and by JEI's real ingredient slots. */
public class DisplayCategory<T> extends ModularUIRecipeCategory<T> {
    public final CategoryDocument document;
    private final RecipeType<T> type;
    protected final Function<T, CategoryDocument.Recipe> resolver;
    public DisplayCategory(CategoryDocument document, RecipeType<T> type, Function<T, CategoryDocument.Recipe> resolver) {
        super(recipe -> RecipeUI.create(document, resolver.apply(recipe)));
        this.document = document; this.type = type; this.resolver = resolver;
    }
    @Override public RecipeType<T> getRecipeType() { return type; }
    @Override public Component getTitle() { return Component.literal(document.title); }
    @Override public int getWidth() { return document.width; }
    @Override public int getHeight() { return document.height; }
    @Override public IDrawable getIcon() {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse(document.icon));
        return VjceJeiPlugin.helpers.getGuiHelper().createDrawableItemStack(new net.minecraft.world.item.ItemStack(item));
    }
    protected String slotName(T recipe, JEIRecipeSlotHandler.Binding binding) {
        var slot = resolver.apply(recipe).boundSlot(binding.element().getId());
        return slot == null || slot.name.isBlank() ? binding.name() : slot.name;
    }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        var bindings = JEIRecipeSlotHandler.collectBindings(getUIForRecipe(recipe));
        var slotBuilders = new LinkedHashMap<String, mezz.jei.api.gui.builder.IIngredientAcceptor<?>>();
        var bound = Collections.newSetFromMap(new IdentityHashMap<CategoryDocument.Slot, Boolean>());
        var data = resolver.apply(recipe);
        for (var binding : bindings) {
            if (!NativePage.visible(binding.element())) continue;
            var area = LDLibJEIPlugin.getAreaLocal(binding.element(), true);
            var slot = data.boundSlot(binding.element().getId());
            var role = slot == null ? binding.role() : RecipeIngredientRole.valueOf(slot.role);
            String name = slotName(recipe, binding);
            var slotBuilder = builder.addSlot(role).setSlotName(name).setPosition(area.getX(), area.getY());
            if (slot != null) {
                bound.add(slot);
                slotBuilder.addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));
                slotBuilder.addRichTooltipCallback((view, tooltip) -> {
                    if (slot.chance != 1) tooltip.add(Component.literal("Chance: " + Math.round(slot.chance * 100) + "%"));
                    slot.ingredients.stream().filter(ingredient -> !ingredient.tag.isBlank()).forEach(ingredient -> tooltip.add(Component.literal("#" + ingredient.tag)));
                });
            } else slotBuilder.addTypedIngredients(binding.ingredients());
            slotBuilders.put(name, slotBuilder);
        }
        for (var slot : data.slots) if (!bound.contains(slot)) {
            var acceptor = builder.addInvisibleIngredients(RecipeIngredientRole.valueOf(slot.role));
            acceptor.addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));
            if (!slot.name.isBlank()) slotBuilders.putIfAbsent(slot.name, acceptor);
        }
        for (var link : data.focusLinks) {
            var members = link.stream().map(slotBuilders::get).filter(Objects::nonNull).toList();
            if (members.size() > 1) builder.createFocusLink(members);
        }
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses) {
        var ui = getUIForRecipe(recipe);
        var widget = new ModularUIJEIWidget(ui);
        builder.addWidget(widget);
        builder.addGuiEventListener(widget);
        var bindings = JEIRecipeSlotHandler.collectBindings(ui);
        var pairs = new ArrayList<Map.Entry<JEIRecipeSlotHandler.Binding, IRecipeSlotDrawable>>();
        for (var binding : bindings) builder.getRecipeSlots().findSlotByName(slotName(recipe, binding)).ifPresent(slot -> {
            if (binding.slotUpdater() != null) binding.slotUpdater().bind(slot);
            pairs.add(Map.entry(binding, slot));
        });
        builder.addSlottedWidget(new ISlottedRecipeWidget() {
            @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x, double y) {
                var mouse = widget.getWorldMouse((float) x, (float) y);
                return pairs.stream().filter(p -> p.getKey().element().isMouseOverElement(mouse.x, mouse.y))
                        .map(p -> new RecipeSlotUnderMouse(p.getValue(), 0, 0)).findFirst();
            }
            @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
        }, pairs.stream().map(Map.Entry::getValue).toList());
    }
    @Override public void onDisplayedIngredientsUpdate(T recipe, List<IRecipeSlotDrawable> slots, IFocusGroup focuses) {
        for (var binding : JEIRecipeSlotHandler.collectBindings(getUIForRecipe(recipe))) {
            if (binding.slotUpdater() != null) slots.stream().filter(slot -> slot.getSlotName().orElse("").equals(slotName(recipe, binding)))
                    .findFirst().ifPresent(binding.slotUpdater()::bind);
        }
    }
    @Override public ResourceLocation getRegistryName(T recipe) { return ResourceLocation.parse(resolver.apply(recipe).id); }
    @Override @SuppressWarnings("unchecked")
    public com.mojang.serialization.Codec<T> getCodec(mezz.jei.api.helpers.ICodecHelper helper, IRecipeManager manager) {
        return com.mojang.serialization.Codec.STRING.flatXmap(id -> document.recipes.stream().filter(recipe -> recipe.id.equals(id)).findFirst()
                .<com.mojang.serialization.DataResult<T>>map(recipe -> com.mojang.serialization.DataResult.success((T) recipe))
                .orElseGet(() -> com.mojang.serialization.DataResult.error(() -> "Unknown VJCE recipe: " + id)),
                recipe -> com.mojang.serialization.DataResult.success(resolver.apply(recipe).id));
    }
    @Override public boolean isHandled(T recipe) { return !resolver.apply(recipe).hidden; }
}
