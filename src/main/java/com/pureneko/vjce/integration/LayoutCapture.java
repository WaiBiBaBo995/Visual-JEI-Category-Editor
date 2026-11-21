package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.ingredients.IngredientManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import mezz.jei.api.fabric.constants.FabricTypes;
import mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient;
import mezz.jei.api.fabric.ingredients.fluids.JeiFluidIngredient;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import java.lang.reflect.*;
import java.util.*;

/** Records the original category's own layout, including focus-generated recipes, without enumerating a static mirror. */
public final class LayoutCapture {
    public record Slot(RecipeIngredientRole role, String name, int x, int y, List<ITypedIngredient<?>> ingredients, boolean invisible) {}
    public final List<Slot> slots = new ArrayList<>();
    public final List<int[]> focusLinks = new ArrayList<>();
    private final List<SlotBuilder> builders = new ArrayList<>();
    private final int width, height;
    private final ContextMap context;
    public LayoutCapture() { this(176, 90); }
    public LayoutCapture(int width, int height) {
        this.width = width;
        this.height = height;
        var level = Minecraft.getInstance().level;
        context = level == null ? new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES, com.lowdragmc.lowdraglib2.Platform.getFrozenRegistry())
                .create(SlotDisplayContext.CONTEXT) : SlotDisplayContext.fromLevel(level);
    }
    public final IRecipeLayoutBuilder builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{IRecipeLayoutBuilder.class}, this::layout);

    private Object layout(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
        if (method.getName().equals("getWidth")) return width;
        if (method.getName().equals("getHeight")) return height;
        if (method.getName().equals("addSlot") || method.getName().equals("addSlotToWidget") || method.getName().equals("addInvisibleIngredients")) {
            var slot = new SlotBuilder((RecipeIngredientRole) args[0], method.getName().equals("addInvisibleIngredients"));
            builders.add(slot);
            return slot.proxy;
        }
        if (method.getName().equals("createFocusLink")) {
            Collection<?> members = args[0] instanceof Collection<?> c ? c : Arrays.asList((Object[]) args[0]);
            focusLinks.add(members.stream().mapToInt(member -> {
                for (int i = 0; i < builders.size(); i++) if (builders.get(i).proxy == member) return i;
                return -1;
            }).filter(i -> i >= 0).toArray());
        }
        return defaultValue(proxy, method, args);
    }
    public void finish() {
        slots.clear();
        for (int i = 0; i < builders.size(); i++) {
            var b = builders.get(i);
            slots.add(new Slot(b.role, b.name.isBlank() ? "vjce_slot_" + i : b.name, b.x, b.y, Collections.unmodifiableList(new ArrayList<>(b.values)), b.invisible));
        }
    }
    private final class SlotBuilder implements InvocationHandler {
        final RecipeIngredientRole role;
        final boolean invisible;
        final Object proxy = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{IRecipeSlotBuilder.class}, this);
        String name = "";
        int x, y, width = 16, height = 16;
        final List<ITypedIngredient<?>> values = new ArrayList<>();
        SlotBuilder(RecipeIngredientRole role, boolean invisible) { this.role = role; this.invisible = invisible; }
        @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
            String name = method.getName();
            if (name.equals("getContextMap")) return context;
            if (name.equals("getWidth")) return width;
            if (name.equals("getHeight")) return height;
            if (name.equals("setSlotName")) this.name = (String) args[0];
            else if (name.equals("setCustomRenderer")) { var renderer = (mezz.jei.api.ingredients.IIngredientRenderer<?>) args[1]; width = renderer.getWidth(); height = renderer.getHeight(); }
            else if (name.equals("setPosition")) {
                x = ((Number) args[0]).intValue(); y = ((Number) args[1]).intValue();
                if (args.length == 6) {
                    x += ((mezz.jei.api.gui.placement.HorizontalAlignment) args[4]).getXPos(((Number) args[2]).intValue(), width);
                    y += ((mezz.jei.api.gui.placement.VerticalAlignment) args[5]).getYPos(((Number) args[3]).intValue(), height);
                }
            }
            else if (name.startsWith("add")) {
                if (args != null && args.length >= 2 && args[0] instanceof IIngredientType<?> type) add(type, args[1]);
                else if (args != null && args.length > 0 && args[0] instanceof Fluid fluid) {
                    long amount = args.length < 2 ? FluidConstants.BUCKET : ((Number) args[1]).longValue();
                    var patch = args.length > 2 && args[2] instanceof DataComponentPatch components ? components : DataComponentPatch.EMPTY;
                    addAny(new JeiFluidIngredient(FluidVariant.of(fluid, patch), amount));
                }
                else if (args != null && args.length > 0 && Set.of("add", "addItemStack", "addItemStacks", "addTypedIngredient", "addTypedIngredients", "addOptionalTypedIngredients", "addIngredients", "addIngredientsUnsafe").contains(name)) addAny(args[0]);
            }
            return defaultValue(proxy, method, args);
        }
        void addAny(Object value) {
            if (value instanceof Collection<?> c) { c.forEach(this::addAny); return; }
            if (value instanceof Optional<?> o) { if (o.isPresent()) addAny(o.get()); else values.add(null); return; }
            if (value instanceof Ingredient ingredient) { addAny(ingredient.display()); return; }
            if (value instanceof SlotDisplay display) {
                if (IngredientAccess.manager instanceof IngredientManager manager) {
                    manager.resolveSlotDisplay(context, role, display).forEach(ingredient -> values.add(ingredient.typedIngredient()));
                } else display.resolveForStacks(context).forEach(this::addAny);
                return;
            }
            if (value instanceof ItemStackTemplate template) { addAny(template.create()); return; }
            if (value instanceof ItemLike item) { addAny(new ItemStack(item)); return; }
            if (value instanceof ITypedIngredient<?> typed) { values.add(typed); return; }
            if (value == null) values.add(null);
            else values.add(IngredientAccess.manager.createTypedIngredient(value, false).orElse(null));
        }
        @SuppressWarnings({"unchecked", "rawtypes"}) void add(IIngredientType type, Object value) {
            if (value instanceof Collection<?> c) { c.forEach(v -> add(type, v)); return; }
            if (value instanceof Optional<?> o) { if (o.isPresent()) add(type, o.get()); else values.add(null); return; }
            if (value instanceof Ingredient ingredient) { add(type, ingredient.display()); return; }
            if (value instanceof SlotDisplay display && IngredientAccess.manager instanceof IngredientManager manager) {
                manager.resolveSlotDisplay(type, context, role, display).forEach(ingredient ->
                        values.add(((mezz.jei.library.ingredients.SlotIngredient<?>) ingredient).typedIngredient()));
                return;
            }
            if (value == null) values.add(null);
            else values.add((ITypedIngredient<?>) IngredientAccess.manager.createTypedIngredient(type, value, false).orElse(null));
        }
    }
    private static Object defaultValue(Object proxy, Method method, Object[] args) {
        if (method.getName().equals("equals")) return proxy == args[0];
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("toString")) return "VJCE layout recorder";
        if (method.getReturnType().isInstance(proxy)) return proxy;
        if (method.getReturnType().equals(boolean.class)) return false;
        if (method.getReturnType().equals(int.class)) return 0;
        if (method.getReturnType().equals(Optional.class)) return Optional.empty();
        if (method.getReturnType().equals(java.util.stream.Stream.class)) return java.util.stream.Stream.empty();
        return null;
    }
    public CategoryDocument.Recipe toDocument(String id) {
        var recipe = new CategoryDocument.Recipe();
        recipe.id = id;
        recipe.source = id;
        recipe.overrideIngredients = false;
        for (var original : slots) {
            var slot = new CategoryDocument.Slot();
            slot.name = original.name; slot.role = original.role.name(); slot.x = original.x; slot.y = original.y; slot.invisible = original.invisible;
            original.ingredients.forEach(value -> {
                if (value != null) slot.ingredients.add(IngredientAccess.encode(value));
                else { var empty = new CategoryDocument.Ingredient(); empty.empty = true; slot.ingredients.add(empty); }
            });
            recipe.slots.add(slot);
        }
        for (var link : focusLinks) recipe.focusLinks.add(Arrays.stream(link).mapToObj(i -> slots.get(i).name()).toList());
        return recipe;
    }
}
