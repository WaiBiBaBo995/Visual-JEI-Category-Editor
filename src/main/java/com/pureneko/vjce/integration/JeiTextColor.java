package com.pureneko.vjce.integration;

import mezz.jei.common.gui.elements.TextWidget;
import java.lang.reflect.Field;

/** JEI 15 uses either a fixed color or a nullable override of its theme color. */
public final class JeiTextColor {
    private static final Field COLOR = findColor();

    private JeiTextColor() {}

    private static Field findColor() {
        for (String name : new String[] {"colorOverride", "color"}) {
            try {
                var field = TextWidget.class.getDeclaredField(name);
                if (field.getType() == Integer.class || field.getType() == int.class) {
                    field.setAccessible(true);
                    return field;
                }
            } catch (NoSuchFieldException ignored) {
                // Older JEI 15 releases store an int named color instead.
            }
        }
        throw new IllegalStateException("Unsupported JEI TextWidget color storage");
    }

    /** Null preserves JEI's theme default instead of forcing a fixed color. */
    public static Integer get(TextWidget widget) {
        try {
            return (Integer) COLOR.get(widget);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot read JEI TextWidget color", failure);
        }
    }
}
