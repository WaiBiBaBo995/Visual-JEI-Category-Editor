package com.pureneko.vjce.data;

import com.google.gson.JsonElement;
import java.util.*;

/** Versioned, client-side presentation data. This is deliberately not a Minecraft recipe. */
public final class CategoryDocument {
    public int schemaVersion = 1;
    public String id = "vjce:example";
    public String title = "Example";
    public String icon = "minecraft:crafting_table";
    /** Empty for a new category; otherwise the original JEI category UID. */
    public String target = "";
    public int width = 176;
    public int height = 90;
    /** UITemplate codec encoded as SNBT, preserving all NBT types and styles. */
    public String layout = "";
    /** Imported element identities distinguish explicit deletions from recipe-dependent new elements. */
    public List<String> nativeOriginElements = new ArrayList<>();
    public List<Field> fields = new ArrayList<>();
    public List<Binding> bindings = new ArrayList<>();
    public List<Recipe> recipes = new ArrayList<>();
    /** Legacy item ID strings and item objects with optional data components. */
    public List<JsonElement> catalysts = new ArrayList<>();

    public static final class Field {
        public String key = "energy";
        public String label = "Energy";
        public String unit = "FE";
        public double initial = 0;
        public double maximum = 10000;
        public int color = 0xFF56B6C2;
        public String icon = "";
    }

    public static final class Binding {
        public String element = "";
        /** text, progress, visible, color; numeric expressions refer only to recipe fields. */
        public String property = "text";
        public String expression = "duration";
        public String format = "%s";
    }

    public static final class Recipe {
        public String id = "vjce:example_recipe";
        /** Overrides match stable original display IDs. Empty means a new display. */
        public String source = "";
        public String title = "";
        public boolean hidden = false;
        /** False permits field-only edits without changing the original ingredient layout. */
        public boolean overrideIngredients = true;
        public double duration = 200;
        public Map<String, Double> values = new LinkedHashMap<>();
        public List<Slot> slots = new ArrayList<>();
        public List<List<String>> focusLinks = new ArrayList<>();

        public String slotIdentifier(int index) {
            var identifier = slots.get(index).identifier;
            return identifier == null ? "slot_" + index : identifier;
        }

        public Slot boundSlot(String identifier) {
            for (int i = 0; i < slots.size(); i++) {
                if (!slots.get(i).invisible && slotIdentifier(i).equals(identifier)) return slots.get(i);
            }
            return null;
        }

        public int sourceSlotIndex(int index) {
            var sourceIndex = slots.get(index).sourceIndex;
            return sourceIndex == null ? index : sourceIndex;
        }

        public int dataSlotIndex(int sourceIndex) {
            for (int i = 0; i < slots.size(); i++) if (sourceSlotIndex(i) == sourceIndex) return i;
            return -1;
        }

        public void removeSlot(int index) {
            // Materialize the old implicit bindings before their list indices change.
            for (int i = index + 1; i < slots.size(); i++) {
                var slot = slots.get(i);
                slot.identifier = slotIdentifier(i);
                if (!source.isBlank()) slot.sourceIndex = sourceSlotIndex(i);
            }
            slots.remove(index);
        }
    }

    public static final class Slot {
        public String name = "";
        public String role = "INPUT";
        public List<Ingredient> ingredients = new ArrayList<>();
        public double chance = 1;
        public boolean invisible = false;
        public int x = 0;
        public int y = 0;
        public String identifier;
        /** Original JEI declaration index, or -1 for a newly added slot. Null uses the legacy list index. */
        public Integer sourceIndex;
    }

    public static final class Ingredient {
        /** Empty alternatives preserve positional focus links in vanilla dynamic recipes. */
        public boolean empty = false;
        public String item = "";
        public String fluid = "";
        public String tag = "";
        public String type = "";
        public JsonElement data;
        public long amount = 1;
    }

    public Map<String, Double> variables(Recipe recipe) {
        var result = new LinkedHashMap<String, Double>();
        fields.forEach(f -> result.put(f.key, f.initial));
        result.putAll(recipe.values);
        result.put("duration", recipe.duration);
        result.put("seconds", recipe.duration / 20.0);
        result.put("inputs", (double) recipe.slots.stream().filter(s -> s.role.equals("INPUT")).count());
        result.put("outputs", (double) recipe.slots.stream().filter(s -> s.role.equals("OUTPUT")).count());
        return result;
    }

    public void validate() {
        if (schemaVersion != 1) throw new IllegalArgumentException("Unsupported schemaVersion: " + schemaVersion);
        checkId(id);
        checkId(icon);
        if (target == null || layout == null || title == null) throw new IllegalArgumentException("Null category property");
        if (!target.isEmpty()) checkId(target);
        if (width < 18 || width > 2048 || height < 18 || height > 2048) throw new IllegalArgumentException("Canvas size must be 18..2048");
        if (recipes == null || fields == null || bindings == null || catalysts == null || nativeOriginElements == null) throw new IllegalArgumentException("Missing category arrays");
        var ids = new HashSet<String>();
        var sources = new HashSet<String>();
        for (var recipe : recipes) {
            checkId(recipe.id);
            if (!ids.add(recipe.id)) throw new IllegalArgumentException("Duplicate recipe: " + recipe.id);
            if (recipe.source == null || recipe.slots == null || recipe.values == null) throw new IllegalArgumentException("Invalid recipe: " + recipe.id);
            if (!recipe.source.isBlank() && !sources.add(recipe.source)) throw new IllegalArgumentException("Duplicate source override: " + recipe.source);
            if (!Double.isFinite(recipe.duration) || recipe.duration <= 0) throw new IllegalArgumentException("Duration must be positive: " + recipe.id);
            if (recipe.focusLinks == null) throw new IllegalArgumentException("Missing focus links: " + recipe.id);
            recipe.values.forEach((key, value) -> { if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("Invalid field " + key + " in " + recipe.id); });
            var slotIds = new HashSet<String>();
            for (int i = 0; i < recipe.slots.size(); i++) {
                var slot = recipe.slots.get(i);
                String identifier = recipe.slotIdentifier(i);
                if (!slot.invisible && (identifier.isBlank() || !slotIds.add(identifier))) {
                    throw new IllegalArgumentException("Invalid or duplicate slot identifier: " + identifier + " in " + recipe.id);
                }
                if (!Set.of("INPUT", "OUTPUT", "CATALYST", "RENDER_ONLY").contains(slot.role)) throw new IllegalArgumentException("Invalid slot role: " + slot.role);
                if (!Double.isFinite(slot.chance) || slot.chance < 0 || slot.chance > 1) throw new IllegalArgumentException("Chance must be 0..1");
                for (var ingredient : slot.ingredients) {
                    if (ingredient.amount < 1) throw new IllegalArgumentException("Ingredient amount must be positive");
                    if (!ingredient.item.isBlank()) checkId(ingredient.item);
                    if (!ingredient.fluid.isBlank()) checkId(ingredient.fluid);
                    if (!ingredient.tag.isBlank()) checkId(ingredient.tag);
                }
            }
        }
        var keys = new HashSet<String>();
        for (var field : fields) {
            if (!field.key.matches("[A-Za-z_][A-Za-z0-9_]*") || !keys.add(field.key)) throw new IllegalArgumentException("Invalid/duplicate field: " + field.key);
            if (Set.of("duration", "seconds", "inputs", "outputs", "true", "false").contains(field.key)) throw new IllegalArgumentException("Reserved field: " + field.key);
            if (!Double.isFinite(field.initial) || !Double.isFinite(field.maximum) || field.maximum <= 0) throw new IllegalArgumentException("Invalid field range: " + field.key);
        }
        for (var binding : bindings) {
            if (!Set.of("text", "progress", "visible", "color").contains(binding.property)) throw new IllegalArgumentException("Unknown binding property: " + binding.property);
            for (var recipe : recipes) {
                double value = Expression.evaluate(binding.expression, variables(recipe));
                if (binding.property.equals("text")) String.format(Locale.ROOT, binding.format, value);
            }
        }
    }

    public static void checkId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid resource ID: " + id);
    }
}
