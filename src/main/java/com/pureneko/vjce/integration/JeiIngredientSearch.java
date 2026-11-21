package com.pureneko.vjce.integration;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.common.util.Translator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;

/** Query JEI's configured index without changing its shared search text or cached result list. */
public final class JeiIngredientSearch {
    private JeiIngredientSearch() {}

    // JEI's public API only exposes queries through the shared search box. Keep the internal
    // compatibility boundary here; both supported JEI 15 generations provide this entry point.
    private record Access(Field filter, Method query) {}
    private static final ClassValue<Access> ACCESS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> apiClass) {
            try {
                Field field = apiClass.getDeclaredField("ingredientFilter");
                field.setAccessible(true);
                Method method = field.getType().getDeclaredMethod("getIngredientListUncached", String.class);
                method.setAccessible(true);
                return new Access(field, method);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unsupported JEI ingredient search API: " + apiClass.getName(), e);
            }
        }
    };

    @SuppressWarnings("unchecked")
    public static List<ITypedIngredient<?>> query(String text) {
        if (VjceJeiPlugin.runtime == null) throw new IllegalStateException("JEI runtime is unavailable");
        var api = VjceJeiPlugin.runtime.getIngredientFilter();
        // Newer JEI versions defer visibility/index updates until a public read.
        api.getFilteredIngredients(VanillaTypes.ITEM_STACK);
        var access = ACCESS.get(api.getClass());
        try (var stream = (Stream<ITypedIngredient<?>>) access.query.invoke(access.filter.get(api), Translator.toLowercaseWithLocale(text))) {
            return stream.toList();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("JEI ingredient search failed", e);
        }
    }
}
