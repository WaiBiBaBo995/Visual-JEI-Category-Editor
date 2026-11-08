package com.pureneko.vjce.client;

import com.google.gson.*;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.integration.xei.jei.LDLibJEIPlugin;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.IngredientAccess;
import dev.vfyjxf.taffy.style.FlexDirection;
import mezz.jei.api.ingredients.IIngredientType;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/** Structured forms, including nested lists; raw ingredient codec data is an optional advanced field. */
final class Forms {
    private Forms() {}
    static UIElement column() { return new UIElement().layout(l -> { l.widthPercent(100); l.paddingAll(3); }); }
    static UIElement row() { return new UIElement().layout(l -> { l.widthPercent(100); l.flexDirection(FlexDirection.ROW); l.height(20); }); }
    static TextField text(String value, Consumer<String> changed) {
        var field = new TextField().setText(value == null ? "" : value, false).setTextResponder(changed);
        field.layout(l -> { l.flex(1); l.height(18); });
        return field;
    }
    static void line(UIElement root, String name, String value, Consumer<String> changed) {
        var row = row();
        row.addChild(new Label().setText(name).layout(l -> { l.widthPercent(35); l.maxWidth(85); l.flexShrink(0); })
                .style(s -> s.tooltips(net.minecraft.network.chat.Component.translatable(name))));
        row.addChild(text(value, changed).layout(l -> l.minWidth(0)));
        root.addChild(row);
    }
    static void object(UIElement root, Object object, Runnable refresh, Runnable changed, Set<String> exclude) {
        for (var field : object.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers()) || exclude.contains(field.getName())) continue;
            try {
                Object value = field.get(object);
                String label = "vjce.field." + field.getName();
                if (field.getType() == boolean.class) {
                    root.addChild(new Toggle().setText(label).setOn((boolean) value, false).setOnToggleChanged(v -> set(field, object, v, changed)));
                } else if (field.getType() == String.class || field.getType().isPrimitive()) {
                    line(root, label, String.valueOf(value), str -> {
                        try {
                            Object parsed = field.getType() == int.class ? Integer.decode(str) : field.getType() == long.class ? Long.parseLong(str) : field.getType() == double.class ? Double.parseDouble(str) : str;
                            set(field, object, parsed, changed);
                        } catch (NumberFormatException ignored) { /* Keep the previous valid value while typing. */ }
                    });
                    root.getChildren().getLast().getChildren().stream().filter(TextField.class::isInstance).forEach(input -> input.setId("edit_" + field.getName()));
                } else if (value instanceof List<?> list && field.getGenericType() instanceof ParameterizedType type) {
                    var content = column(); content.setDisplay(false);
                    root.addChild(new Button().setText(net.minecraft.client.resources.language.I18n.get(label) + " [" + list.size() + "]").setOnClick(e -> content.setDisplay(!content.isDisplayed())));
                    root.addChild(content);
                    list(content, (List<Object>) list, type.getActualTypeArguments()[0], refresh, changed);
                } else if (value instanceof Map<?, ?> map) {
                    root.addChild(new Label().setText(label));
                    for (var key : new ArrayList<>(map.keySet())) line(root, String.valueOf(key), String.valueOf(map.get(key)), str -> {
                        try { ((Map<Object, Object>) map).put(key, Double.parseDouble(str)); changed.run(); } catch (NumberFormatException ignored) {}
                    });
                    var key = text("new_field", v -> {}); key.layout(l -> { l.flexAuto(); l.flexGrow(0); l.flexBasisAuto(); l.flexShrink(0); l.height(18); l.widthPercent(100); });
                    root.addChild(key);
                    root.addChild(new Button().setText("vjce.add_value").setOnClick(e -> { ((Map<Object, Object>) map).put(key.getValue(), 0.0); changed.run(); refresh.run(); }));
                } else if (field.getType() == JsonElement.class) {
                    line(root, label, value == null ? "" : value.toString(), str -> {
                        try { set(field, object, str.isBlank() ? null : JsonParser.parseString(str), changed); } catch (JsonParseException ignored) {}
                    });
                }
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        }
    }
    private static void set(Field field, Object target, Object value, Runnable changed) {
        try {
            field.set(target, value);
            if (target instanceof CategoryDocument.Ingredient ingredient && Set.of("item", "fluid", "tag").contains(field.getName())) {
                ingredient.data = null; ingredient.type = "";
                if (field.getName().equals("item")) ingredient.fluid = "";
                if (field.getName().equals("fluid")) ingredient.item = "";
            }
            changed.run();
        } catch (IllegalAccessException e) { throw new IllegalStateException(e); }
    }
    private static void list(UIElement root, List<Object> values, Type elementType, Runnable refresh, Runnable changed) {
        for (int i = 0; i < values.size(); i++) {
            int index = i; Object value = values.get(i);
            var block = column(); root.addChild(block);
            block.addChild(new Button().setText("− " + i).setOnClick(e -> { values.remove(index); changed.run(); refresh.run(); }));
            if (value instanceof String str) block.addChild(text(str, s -> { values.set(index, s); changed.run(); }));
            else if (value instanceof CategoryDocument.Ingredient ingredient) {
                ingredient(block, ingredient, replacement -> { values.set(index, replacement); changed.run(); refresh.run(); }, refresh, changed);
            } else if (value instanceof List<?>) {
                block.addChild(new Label().setText(ProjectStore.JSON.toJson(value)));
            } else object(block, value, refresh, changed, Set.of());
        }
        root.addChild(new Button().setText("vjce.add").setOnClick(e -> {
            try {
                Object value = elementType == String.class ? "" : elementType instanceof Class<?> cls ? cls.getConstructor().newInstance() : new ArrayList<>();
                values.add(value); changed.run(); refresh.run();
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }));
    }
    static void ingredient(UIElement root, CategoryDocument.Ingredient ingredient, Consumer<CategoryDocument.Ingredient> replace, Runnable refresh, Runnable changed) {
        var slot = new IngredientSlot();
        try { var decoded = IngredientAccess.decode(ingredient); if (!decoded.isEmpty()) slot.ingredient = decoded.getFirst(); } catch (Exception ignored) {}
        slot.amount = ingredient.amount;
        registerGhost(slot, typed -> replace.accept(IngredientAccess.encode(typed)));
        root.addChild(slot);
        root.addChild(new Button().setText("vjce.choose_ingredient").setOnClick(e -> IngredientPicker.open(root, typed -> replace.accept(IngredientAccess.encode(typed)))).setId("vjce_choose_ingredient"));
        object(root, ingredient, refresh, changed, Set.of("data"));
        if (ingredient.data != null && !ingredient.data.isJsonNull()) {
            var advanced = new StructuredTagEditor().setAny();
            advanced.layout(l -> { l.widthPercent(100); l.height(150); });
            advanced.setValue(com.mojang.serialization.JsonOps.INSTANCE.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, ingredient.data), false);
            advanced.setTagResponder(tag -> { ingredient.data = net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE, tag); changed.run(); });
            var body = column(); body.setDisplay(false); body.addChild(advanced);
            root.addChildren(new Button().setText("vjce.ingredient.advanced").setOnClick(e -> body.setDisplay(!body.isDisplayed())), body);
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    static void registerGhost(UIElement slot, Consumer<mezz.jei.api.ingredients.ITypedIngredient<?>> changed) {
        if (IngredientAccess.manager == null) return;
        for (IIngredientType type : IngredientAccess.manager.getRegisteredIngredientTypes()) {
            LDLibJEIPlugin.ghostIngredient(slot, type, value -> true, value -> IngredientAccess.manager.createTypedIngredient(type, value).ifPresent(v -> changed.accept((mezz.jei.api.ingredients.ITypedIngredient<?>) v)));
        }
    }
}
