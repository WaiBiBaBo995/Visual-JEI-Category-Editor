package com.pureneko.vjce.data;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionTest {
    @Test void numericBindingsAndConditions() {
        var fields = Map.of("energy", 1200.0, "duration", 60.0, "temperature", 1500.0);
        assertEquals(20, Expression.evaluate("energy / duration", fields));
        assertEquals(1, Expression.evaluate("temperature >= 1000 && duration > 0", fields));
        assertEquals(100, Expression.evaluate("clamp(energy, 0, 100)", fields));
        assertEquals(30, Expression.evaluate("temperature >= 1000 ? max(20, 30) : 0", fields));
        assertEquals(7, Expression.evaluate("1 + 2 * 3", fields));
        assertEquals(0.001, Expression.evaluate("1e-3", fields));
    }
    @Test void rejectsBadBindingsInsteadOfSilentlyUsingZero() {
        assertThrows(IllegalArgumentException.class, () -> Expression.evaluate("missing / 20", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Expression.evaluate("1 / 0", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Expression.evaluate("java.lang.Runtime()", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Expression.evaluate("min(1)", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> Expression.evaluate("(".repeat(70) + "1" + ")".repeat(70), Map.of()));
    }
}
