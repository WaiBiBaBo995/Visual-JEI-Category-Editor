package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.project.*;
import com.lowdragmc.lowdraglib2.editor.resource.*;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import java.io.*;

public final class VjceProject implements IProject {
    public static final ProjectType TYPE = new ProjectType("vjce.project", ".vjce", VjceProject::new) {
        @Override public File getRootSavePath(IProject project, File root) { return VisualJeiCategoryEditor.STORE.root.resolve("projects").toFile(); }
    };
    private final Resources resources = Resources.of(ColorsResource.INSTANCE, TexturesResource.INSTANCE, UIResource.INSTANCE, SceneAssetResource.BLOCKS, SceneAssetResource.ENTITIES);
    public CategoryDocument document;
    public CategoryDocument.Recipe selected;
    public RecipeLayoutView layoutView;
    public RecipeDataView dataView;
    public String sourceIssue = "";
    public boolean sourceEditable = true;
    public boolean canCreateRecipes() { return document.target.isBlank() || com.pureneko.vjce.integration.VanillaDisplays.supports(net.minecraft.resources.ResourceLocation.parse(document.target)); }
    public boolean canEditSelectedRecipe() {
        return sourceEditable && !selected.source.startsWith("vjce:generated/") && (canCreateRecipes() || !selected.source.isBlank());
    }
    public VjceProject() {
        this(new CategoryDocument());
        document.id = "vjce:category_" + Long.toUnsignedString(System.nanoTime(), 36);
        document.title = "New Category";
        var recipe = new CategoryDocument.Recipe();
        recipe.id = "vjce:recipe_" + Long.toUnsignedString(System.nanoTime(), 36);
        document.recipes.add(recipe); selected = recipe;
    }
    public VjceProject(CategoryDocument document) { this.document = document; selected = document.recipes.isEmpty() ? new CategoryDocument.Recipe() : document.recipes.getFirst(); }
    @Override public Resources getResources() { return resources; }
    @Override public ProjectType getProjectType() { return TYPE; }
    public void captureLayout() {
        if (layoutView != null && layoutView.getCurrentUI() != null) {
            layoutView.transforms.cancel();
            if (!sourceEditable) return;
            var template = layoutView.getCurrentUI().toTemplate();
            if (layoutView.getTemplate() != null) template.copyStylesFrom(layoutView.getTemplate());
            document.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, template).getOrThrow().toString();
        }
    }
    public void publish() throws IOException {
        captureLayout();
        VisualJeiCategoryEditor.STORE.save(document);
    }
    @Override public CompoundTag serializeProject(HolderLookup.Provider provider) {
        captureLayout();
        var tag = new CompoundTag(); tag.putString("document", ProjectStore.JSON.toJson(document));
        tag.putString("categoryFile", VisualJeiCategoryEditor.STORE.sourceFile(document)); return tag;
    }
    @Override public void deserializeProject(HolderLookup.Provider provider, CompoundTag nbt) {
        document = ProjectStore.JSON.fromJson(nbt.getString("document"), CategoryDocument.class);
        document.validate();
        VisualJeiCategoryEditor.STORE.restoreSource(document, nbt.getString("categoryFile"));
        selected = document.recipes.isEmpty() ? new CategoryDocument.Recipe() : document.recipes.getFirst();
    }
    @Override public void onLoad(Editor editor) {
        if (!document.target.isBlank() && com.pureneko.vjce.integration.VjceJeiPlugin.runtime == null) { sourceEditable = false; sourceIssue = "vjce.source.unavailable"; }
        if (!document.target.isBlank() && com.pureneko.vjce.integration.VjceJeiPlugin.runtime != null) {
            var original = com.pureneko.vjce.integration.VjceJeiPlugin.originals.get(document.target);
            if (original == null) { sourceEditable = false; sourceIssue = "vjce.source.unavailable"; }
            else if (document.recipes.isEmpty()) selectSourceSample(original);
            if (original != null && sourceEditable) {
                try { if (com.pureneko.vjce.integration.NativePage.preview(document, selected) == null) { sourceEditable = false; sourceIssue = "vjce.source.empty"; } }
                catch (RuntimeException failure) { sourceEditable = false; sourceIssue = "vjce.source.readonly"; }
            }
        }
        layoutView = new RecipeLayoutView(this);
        dataView = new RecipeDataView(this, editor);
        editor.placeView(dataView, () -> editor.getCenterWindow().getLeftTop());
        editor.placeView(layoutView, () -> editor.getCenterWindow().getLeftTop());
        layoutView.getViewContainer().selectView(layoutView);
    }
    @SuppressWarnings({"rawtypes", "unchecked"}) private void selectSourceSample(mezz.jei.api.recipe.category.IRecipeCategory original) {
        var samples = com.pureneko.vjce.integration.VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().limit(10).toList();
        if (samples.isEmpty()) { sourceEditable = false; sourceIssue = "vjce.source.empty"; return; }
        for (var raw : samples) try {
            selected = com.pureneko.vjce.integration.CapturedCategory.capture(original, raw);
            if (canEditSelectedRecipe()) document.recipes.add(selected);
            else sourceIssue = "vjce.source.unstable";
            return;
        } catch (RuntimeException failure) { sourceIssue = "vjce.source.readonly"; }
        sourceEditable = false;
    }
    @Override public void onClosed(Editor editor) {
        SceneEditor.closeFor(layoutView);
        if (layoutView != null) layoutView.removeSelf();
        if (dataView != null) dataView.removeSelf();
    }
    public void select(CategoryDocument.Recipe recipe) {
        if (layoutView != null) captureLayout();
        selected = recipe;
        sourceEditable = true;
        sourceIssue = canEditSelectedRecipe() ? "" : "vjce.source.unstable";
        layoutView.reload(); dataView.refresh();
    }
}
