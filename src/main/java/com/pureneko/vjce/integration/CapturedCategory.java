package com.pureneko.vjce.integration;

import com.pureneko.vjce.data.*;
import com.mojang.serialization.Codec;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.helpers.ICodecHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import java.util.*;

public final class CapturedCategory<T> extends DisplayCategory<T> {
    public final IRecipeCategory<T> original;
    private final Map<T, CategoryDocument.Recipe> records;
    private final Set<T> additions = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<T> captureFailures = Collections.newSetFromMap(new IdentityHashMap<>());
    private record LayoutKey(Object recipe, IFocusGroup focus) {}
    private final Map<IRecipeSlotDrawable, java.lang.ref.WeakReference<NativeSession>> slotSessions = new WeakHashMap<>();
    private final Map<LayoutKey, NativeSession> sessions = new LinkedHashMap<>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<LayoutKey, NativeSession> eldest) {
            if (size() <= 64) return false;
            eldest.getValue().ui.onRemoved(); return true;
        }
    };
    public CapturedCategory(IRecipeCategory<T> original, CategoryDocument document) { this(original, document, new IdentityHashMap<>()); }
    private CapturedCategory(IRecipeCategory<T> original, CategoryDocument document, Map<T, CategoryDocument.Recipe> records) {
        super(document, original.getRecipeType(), recipe -> records.computeIfAbsent(recipe, r -> resolve(original, document, r)));
        this.original = original; this.records = records;
        if (document.layout.isBlank()) { document.width = original.getWidth(); document.height = original.getHeight(); }
    }
    public static <T> CategoryDocument.Recipe capture(IRecipeCategory<T> original, T recipe) {
        return capture(original, recipe, VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of()));
    }
    public static <T> CategoryDocument.Recipe capture(IRecipeCategory<T> original, T recipe, IFocusGroup focuses) {
        var capture = new LayoutCapture(original.getWidth(), original.getHeight());
        original.setRecipe(capture.builder, recipe, focuses);
        capture.finish();
        var id = original.getRegistryName(recipe);
        String stableId;
        if (id == null) {
            try {
                String payload = ProjectStore.JSON.toJson(capture.toDocument("vjce:temporary"));
                stableId = "vjce:generated/" + HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
        } else stableId = id.toString();
        var result = capture.toDocument(stableId);
        if (recipe instanceof RecipeHolder<?> holder && holder.value() instanceof AbstractCookingRecipe cooking) {
            result.duration = cooking.getCookingTime();
            result.values.put("experience", (double) cooking.getExperience());
        }
        if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe fuel) result.duration = fuel.getBurnTime();
        if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiCompostingRecipe compost) result.values.put("chance", (double) compost.getChance());
        if (recipe instanceof mezz.jei.api.recipe.vanilla.IJeiGrindstoneRecipe grindstone) {
            result.values.put("minXp", (double) grindstone.getMinXpReward()); result.values.put("maxXp", (double) grindstone.getMaxXpReward());
        }
        NativePage.remember(original, recipe, result);
        return result;
    }
    private static <T> CategoryDocument.Recipe resolve(IRecipeCategory<T> original, CategoryDocument document, T recipe) {
        return resolve(original, document, recipe, VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of()));
    }
    private static <T> CategoryDocument.Recipe resolve(IRecipeCategory<T> original, CategoryDocument document, T recipe, IFocusGroup focuses) {
        var raw = capture(original, recipe, focuses);
        var override = document.recipes.stream().filter(r -> r.source.equals(raw.source) || (r.source.isEmpty() && r.id.equals(raw.id))).findFirst();
        if (override.isEmpty()) return raw;
        var replacement = override.get();
        if (replacement.overrideIngredients) return replacement;
        raw.duration = replacement.duration;
        raw.values.putAll(replacement.values);
        raw.hidden = replacement.hidden;
        for (int i = 0; i < raw.slots.size(); i++) {
            int savedIndex = replacement.dataSlotIndex(i);
            if (savedIndex >= 0) raw.slots.get(i).identifier = replacement.slots.get(savedIndex).identifier;
        }
        return raw;
    }
    public CategoryDocument.Recipe presentation(T recipe) { return resolver.apply(recipe); }
    private CategoryDocument.Recipe presentation(T recipe, IFocusGroup focus) { return additions.contains(recipe) ? records.get(recipe) : resolve(original, document, recipe, focus); }
    private NativeSession session(T recipe, IFocusGroup focus) {
        return sessions.computeIfAbsent(new LayoutKey(recipe, focus), key -> new NativeSession(original, recipe, document, presentation(recipe, focus), focus));
    }
    private CategoryDocument.Recipe configured(T recipe) {
        if (additions.contains(recipe)) return records.get(recipe);
        var id = original.getRegistryName(recipe);
        if (id != null) return document.recipes.stream().filter(r -> r.source.equals(id.toString()) || (r.source.isEmpty() && r.id.equals(id.toString()))).findFirst().orElse(null);
        if (document.recipes.stream().noneMatch(r -> !r.source.isEmpty())) return null;
        var source = capture(original, recipe).source;
        return document.recipes.stream().filter(r -> r.source.equals(source)).findFirst().orElse(null);
    }
    private void preserveOriginal(T recipe, RuntimeException error) {
        if (captureFailures.add(recipe)) com.pureneko.vjce.VisualJeiCategoryEditor.LOG.warn(
                "VJCE could not capture {} / {}; preserving the original JEI page", getRecipeType().getUid(), original.getRegistryName(recipe), error);
    }
    @Override public void setRecipe(mezz.jei.api.gui.builder.IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        if (captureFailures.contains(recipe)) { original.setRecipe(builder, recipe, focuses); return; }
        CategoryDocument.Recipe data;
        NativeSession session;
        try {
            if (VjceJeiPlugin.runtime == null) {
                // Index untouched recipes directly; no editor serialization belongs on the login path.
                data = configured(recipe);
                session = null;
            } else {
                data = presentation(recipe, focuses);
                session = session(recipe, focuses);
            }
        } catch (RuntimeException error) {
            preserveOriginal(recipe, error); original.setRecipe(builder, recipe, focuses); return;
        }
        if (data == null || (VjceJeiPlugin.runtime == null && !data.overrideIngredients)) { original.setRecipe(builder, recipe, focuses); return; }
        var replay = new SlotReplay(builder, data, session == null ? null : session.root(), additions.contains(recipe) || data.overrideIngredients);
        original.setRecipe(replay.builder, recipe, focuses); replay.finish();
        if (session != null) session.slotKeys = List.copyOf(replay.slotKeys);
    }
    @Override public void createRecipeExtras(mezz.jei.api.gui.widgets.IRecipeExtrasBuilder builder, T recipe, IFocusGroup focuses) {
        NativeSession active = null;
        if (!captureFailures.contains(recipe)) {
            try { active = session(recipe, focuses); }
            catch (RuntimeException error) { preserveOriginal(recipe, error); }
        }
        if (active == null) original.createRecipeExtras(builder, recipe, focuses);
        else {
            active.extras(builder);
            for (var slot : active.actualSlots) slotSessions.put(slot, new java.lang.ref.WeakReference<>(active));
        }
    }
    @Override public void draw(T recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, net.minecraft.client.gui.GuiGraphics graphics, double x, double y) {
        var active = activeSession(recipe, slots);
        if (active.isPresent()) active.get().drawBase(graphics, slots, x, y);
        else {
            var background = original.getBackground(); if (background != null) background.draw(graphics);
            original.draw(recipe, slots, graphics, x, y);
        }
    }
    private Optional<NativeSession> activeSession(T recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots) {
        if (captureFailures.contains(recipe)) return Optional.empty();
        // JEI invokes draw after setRecipe and before widget extras; each live layout owns its focus-specific session.
        var first = slots.getSlotViews().stream().filter(IRecipeSlotDrawable.class::isInstance).map(IRecipeSlotDrawable.class::cast).findFirst();
        var active = first.map(slotSessions::get).map(java.lang.ref.WeakReference::get);
        if (active.isEmpty()) active = sessions.entrySet().stream().filter(entry -> entry.getKey().recipe() == recipe).reduce((a, b) -> b).map(Map.Entry::getValue);
        return active;
    }
    @Override public void getTooltip(mezz.jei.api.gui.builder.ITooltipBuilder tooltip, T recipe, mezz.jei.api.gui.ingredient.IRecipeSlotsView slots, double x, double y) {
        var active = activeSession(recipe, slots);
        if (active.isPresent()) active.get().tooltip(tooltip, slots, x, y);
        else original.getTooltip(tooltip, recipe, slots, x, y);
    }
    @Override public boolean needsRecipeBorder() { return original.needsRecipeBorder(); }
    @Override public mezz.jei.api.gui.drawable.IDrawable getIcon() { return original.getIcon(); }
    public boolean blocksTransfer(T recipe) {
        if (additions.contains(recipe)) return true;
        try { var data = configured(recipe); return data != null && data.overrideIngredients; }
        catch (RuntimeException error) { preserveOriginal(recipe, error); return false; }
    }
    public void addDisplay(T recipe, CategoryDocument.Recipe data) { records.put(recipe, data); additions.add(recipe); }
    @Override public ResourceLocation getRegistryName(T recipe) { return additions.contains(recipe) ? ResourceLocation.parse(presentation(recipe).id) : original.getRegistryName(recipe); }
    @Override public List<ResourceLocation> getSourceRecipeIds(T recipe) { return additions.contains(recipe) ? List.of() : original.getSourceRecipeIds(recipe); }
    @Override @SuppressWarnings("unchecked")
    public Codec<T> getCodec(ICodecHelper helper, mezz.jei.api.recipe.IRecipeManager manager) {
        var nativeCodec = original.getCodec(helper, manager);
        if (nativeCodec == null) return null;
        if (!VanillaDisplays.supports(getRecipeType().getUid())) return nativeCodec;
        var displayCodec = Codec.STRING.fieldOf("vjce_display").codec();
        return Codec.either(displayCodec, nativeCodec).xmap(either -> either.map(id -> {
            var data = document.recipes.stream().filter(recipe -> recipe.id.equals(id) && recipe.source.isEmpty()).findFirst().orElseThrow();
            T recipe = (T) VanillaDisplays.create(getRecipeType().getUid(), data);
            addDisplay(recipe, data); return recipe;
        }, recipe -> recipe), recipe -> additions.contains(recipe) ? com.mojang.datafixers.util.Either.left(presentation(recipe).id) : com.mojang.datafixers.util.Either.right(recipe));
    }
    @Override public boolean isHandled(T recipe) {
        if (additions.contains(recipe)) return !records.get(recipe).hidden;
        if (!original.isHandled(recipe)) return false;
        if (document.recipes.stream().noneMatch(r -> r.hidden)) return true;
        try { var data = configured(recipe); return data == null || !data.hidden; }
        catch (RuntimeException error) { preserveOriginal(recipe, error); return true; }
    }
    @Override public void onDisplayedIngredientsUpdate(T recipe, List<IRecipeSlotDrawable> slots, IFocusGroup focuses) {
        if (VjceJeiPlugin.runtime != null && !captureFailures.contains(recipe)) {
            try { var active = session(recipe, focuses); if (!active.actualSlots.isEmpty()) slots = active.actualSlots; }
            catch (RuntimeException error) { preserveOriginal(recipe, error); }
        }
        if (VjceJeiPlugin.runtime != null && !slots.isEmpty() && !captureFailures.contains(recipe)) {
            try { slotSessions.put(slots.getFirst(), new java.lang.ref.WeakReference<>(session(recipe, focuses))); }
            catch (RuntimeException error) { preserveOriginal(recipe, error); }
        }
        original.onDisplayedIngredientsUpdate(recipe, slots, focuses);
    }
    @Override @SuppressWarnings("removal") public boolean handleInput(T recipe, double x, double y, com.mojang.blaze3d.platform.InputConstants.Key key) {
        if (captureFailures.contains(recipe)) return original.handleInput(recipe, x, y, key);
        return sessions.entrySet().stream().filter(entry -> entry.getKey().recipe() == recipe).reduce((a, b) -> b)
                .map(entry -> entry.getValue().legacyInput(x, y, key)).orElse(false);
    }
}
