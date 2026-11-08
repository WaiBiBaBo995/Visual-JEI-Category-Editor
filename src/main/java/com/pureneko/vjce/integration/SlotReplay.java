package com.pureneko.vjce.integration;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.pureneko.vjce.client.NativePartElement;
import com.pureneko.vjce.client.RecipeUI;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.recipe.RecipeIngredientRole;
import java.lang.reflect.*;
import java.util.*;

/** Replays the actual category against JEI's real builder, retaining renderer/background/callback declarations. */
public final class SlotReplay implements InvocationHandler {
    private static final Set<String> INGREDIENT_CALLS = Set.of("addIngredients", "addIngredient", "addIngredientsUnsafe", "addTypedIngredients", "addOptionalTypedIngredients", "addFluidStack");
    private final IRecipeLayoutBuilder actual;
    private final CategoryDocument.Recipe data;
    private final UIElement root;
    private final boolean edited;
    private final Map<Object, Object> targets = new IdentityHashMap<>();
    private int nextIndex;
    public final List<String> slotKeys = new ArrayList<>();
    public final IRecipeLayoutBuilder builder;
    public SlotReplay(IRecipeLayoutBuilder actual, CategoryDocument.Recipe data, UIElement root, boolean edited) {
        this.actual = actual; this.data = data; this.root = root; this.edited = edited;
        builder = (IRecipeLayoutBuilder) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{IRecipeLayoutBuilder.class}, this);
    }
    @Override public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
        String name = method.getName();
        if (name.equals("addSlot") || name.equals("addSlotToWidget") || name.equals("addInvisibleIngredients")) {
            int sourceIndex = nextIndex++;
            int index = data.dataSlotIndex(sourceIndex);
            var slot = index < 0 ? null : data.slots.get(index);
            String key = slot == null ? "slot_" + sourceIndex : data.slotIdentifier(index);
            var element = root == null ? null : RecipeUI.slotElement(root, key);
            boolean hidden = name.equals("addInvisibleIngredients") || (slot == null ? edited : slot.invisible)
                    || (root != null && !visible(element));
            RecipeIngredientRole role = edited && slot != null ? RecipeIngredientRole.valueOf(slot.role) : (RecipeIngredientRole) args[0];
            Object destination = hidden ? actual.addInvisibleIngredients(role) : name.equals("addSlotToWidget")
                    ? actual.addSlotToWidget(role, (mezz.jei.api.gui.widgets.ISlottedWidgetFactory<?>) args[1]) : actual.addSlot(role);
            if (!hidden) slotKeys.add(key);
            Object[] self = {null}; boolean[] added = {false};
            self[0] = Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{IRecipeSlotBuilder.class}, (p, call, values) -> {
                if (call.isDefault()) return InvocationHandler.invokeDefault(p, call, values);
                if (edited && INGREDIENT_CALLS.contains(call.getName())) {
                    if (!added[0] && slot != null) ((IIngredientAcceptor<?>) destination).addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));
                    added[0] = true;
                    return p;
                }
                if (call.getName().equals("setPosition") && element != null && destination instanceof IRecipeSlotBuilder real) {
                    real.setPosition(NativePage.x(root, element), NativePage.y(root, element)); return p;
                }
                if (!call.getDeclaringClass().isInstance(destination)) return defaultValue(p, call, values);
                Object result = invokeReal(call, destination, values);
                return result == destination ? p : result;
            });
            targets.put(self[0], destination);
            return self[0];
        }
        if (name.equals("createFocusLink")) {
            if (edited) return null;
            Collection<?> members = args[0] instanceof Collection<?> c ? c : Arrays.asList((Object[]) args[0]);
            actual.createFocusLink(members.stream().map(v -> (IIngredientAcceptor<?>) targets.getOrDefault(v, v)).toList());
            return null;
        }
        return invokeReal(method, actual, args);
    }
    public void finish() {
        if (!edited) return;
        for (int index = 0; index < data.slots.size(); index++) {
            int sourceIndex = data.sourceSlotIndex(index);
            if (sourceIndex >= 0 && sourceIndex < nextIndex) continue;
            var slot = data.slots.get(index);
            String key = data.slotIdentifier(index);
            var element = root == null ? null : RecipeUI.slotElement(root, key);
            if (slot.invisible || (root != null && !visible(element))) {
                actual.addInvisibleIngredients(RecipeIngredientRole.valueOf(slot.role)).addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));
            } else {
                slotKeys.add(key);
                actual.addSlot(RecipeIngredientRole.valueOf(slot.role)).setSlotName(slot.name).setPosition(element == null ? slot.x : NativePage.x(root, element), element == null ? slot.y : NativePage.y(root, element))
                        .setStandardSlotBackground().addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));
            }
        }
    }
    private static boolean visible(UIElement element) {
        return NativePage.visible(element) && (!(element instanceof NativePartElement nativePart) || nativePart.useSourceDrawing);
    }
    private static Object invokeReal(Method method, Object destination, Object[] values) throws Throwable {
        try { return method.invoke(destination, values); } catch (InvocationTargetException e) { throw e.getCause(); }
    }
    private static Object defaultValue(Object proxy, Method method, Object[] values) {
        if (method.getName().equals("equals")) return proxy == values[0];
        if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
        if (method.getName().equals("toString")) return "VJCE source slot";
        if (method.getReturnType().isInstance(proxy)) return proxy;
        if (method.getReturnType() == int.class) return 16;
        if (method.getReturnType() == boolean.class) return false;
        return null;
    }
}
