package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;
import java.util.*;

public final class RecipeDataView extends View {
    private static final List<String> SLOT_ROLES = List.of("INPUT", "OUTPUT", "CATALYST", "RENDER_ONLY");
    private final VjceProject project;
    private final Editor editor;
    private final ScrollerView scroll = new ScrollerView();
    private final ScrollerView browser = new ScrollerView();
    private final UIElement browserResults = Forms.column();
    private TreeList<TreeNode<String, UIElement>> formTree;
    private final Map<String,String> nodeTitles = new HashMap<>();
    private final Map<CategoryDocument.Slot, Button> slotButtons = new IdentityHashMap<>();
    private CategoryDocument.Recipe inspectedRecipe;
    private CategoryDocument.Slot selectedSlot;
    private IConfigurable slotInspection;
    private final Label status = new Label();
    final RecipeSplitView split;
    private boolean categoryMode;
    private int browserPage;
    private String browserSearch = "";
    public RecipeDataView(VjceProject project, Editor editor) {
        super("vjce.category_data"); this.project = project; this.editor = editor; status.setText(project.sourceIssue);
        status.textStyle(s -> s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true)).layout(l -> l.widthPercent(100));
        var toolbar = Forms.row();
        toolbar.addChildren(new Button().setText("vjce.category").setOnClick(e -> { categoryMode = true; refresh(); }).setId("vjce_category_settings"),
                new Button().setText("vjce.recipes").setOnClick(e -> { categoryMode = false; refresh(); }).setId("vjce_recipe_settings"),
                new Button().setText("vjce.publish").setOnClick(e -> action(() -> { project.publish(); status.setText("vjce.saved_reenter"); })),
                new Button().setText("vjce.export").setOnClick(e -> action(() -> {
                    project.publish();
                    VisualJeiCategoryEditor.STORE.exportZip(FMLPaths.GAMEDIR.get().resolve("exports/vjce-modpack.zip"), com.lowdragmc.lowdraglib2.LDLib2.getAssetsDir().toPath());
                    status.setText("exports/vjce-modpack.zip");
                })));
        scroll.layout(l -> { l.widthPercent(100); l.flex(1); });
        browser.setId("vjce_recipe_sidebar"); browser.layout(l -> { l.widthPercent(100); l.heightPercent(100); });
        split = new RecipeSplitView(editor.editorSettings instanceof VjceEditorSettings settings ? settings : null);
        scroll.setId("vjce_recipe_details"); scroll.layout(l -> { l.flex(1); l.widthAuto(); l.heightPercent(100); });
        split.left(browser).right(scroll);
        scroll.layout(l -> { l.widthPercent(100); l.heightPercent(100); });
        addChildren(toolbar, status, split); rebuildBrowser(); refresh();
    }
    public void refresh() {
        if (selectedSlot != null && (categoryMode || inspectedRecipe != project.selected || !project.canEditSelectedRecipe()
                || !project.selected.slots.contains(selectedSlot))) clearSlotSelection();
        slotButtons.clear();
        var root = scroll.getContentHost(); root.clearAllChildren();
        split.first.setDisplay(!categoryMode);
        if (categoryMode) {
            Forms.object(root, project.document, this::refresh, () -> {}, Set.of("layout", "recipes", "schemaVersion", "nativeOriginElements", "catalysts"));
            CategoryCatalystEditor.addTo(root, project.document, this::refresh);
            return;
        }
        var actions = Forms.row();
        actions.addChildren(new Button().setText("vjce.new_recipe").setOnClick(e -> {
            var recipe = new CategoryDocument.Recipe(); recipe.id = unique();
            project.document.recipes.add(recipe); project.select(recipe);
        }).setId("vjce_new_recipe").setActive(project.canCreateRecipes()), new Button().setText("vjce.copy").setOnClick(e -> {
            var copy = ProjectStore.JSON.fromJson(ProjectStore.JSON.toJson(project.selected), CategoryDocument.Recipe.class);
            copy.id = unique(); copy.source = ""; copy.overrideIngredients = true;
            project.document.recipes.add(copy); project.select(copy);
        }).setId("vjce_copy_recipe").setActive(project.canCreateRecipes()), new Button().setText("vjce.delete").setOnClick(e -> {
            project.document.recipes.remove(project.selected);
            project.select(project.document.recipes.isEmpty() ? new CategoryDocument.Recipe() : project.document.recipes.getFirst());
        }));
        root.addChild(actions);
        if (!project.canCreateRecipes()) root.addChild(new Label().setText("vjce.source.no_creation"));
        rebuildSavedRecipes();
        if (!project.canEditSelectedRecipe()) { root.addChild(new Label().setText(project.sourceIssue.isEmpty() ? "vjce.source.select_recipe" : project.sourceIssue)); return; }
        root.addChild(new Label().setText("vjce.selected"));
        var slotActions = Forms.row();
        root.addChild(slotActions);
        Set<String> expanded = new HashSet<>(formTree == null ? SLOT_ROLES : formTree.getExpandedNodes().stream().map(TreeNode::getKey).toList());
        if (formTree == null) expanded.addAll(List.of("basic", "parameters"));
        if (selectedSlot != null) expanded.add(selectedSlot.role);
        var treeRoot = new TreeNode<String, UIElement>("recipe"); nodeTitles.clear();
        for (String role : SLOT_ROLES) {
            if (project.selected.slots.stream().noneMatch(slot -> role.equals(slot.role))) continue;
            if (formTree != null && formTree.getRoot().getChildren().stream().noneMatch(node -> role.equals(node.getKey()))) {
                expanded.add(role);
            }
            var roleNode = group(treeRoot, role, "vjce.role." + role);
            for (int i = 0; i < project.selected.slots.size(); i++) {
                var slot = project.selected.slots.get(i);
                if (!slot.role.equals(role)) continue;
                var button = recipeButton(slotTitle(i, slot), slot == selectedSlot, () -> selectSlot(slot));
                button.setId("vjce_recipe_slot_" + i);
                slotButtons.put(slot, button);
                roleNode.addContent(role + "/slot_" + i, button);
            }
        }
        var basic = group(treeRoot, "basic", "vjce.recipe.basic");
        var basicFields = Forms.column();
        var excluded = new HashSet<>(Set.of("slots", "focusLinks", "values", "duration"));
        if (!project.document.target.isBlank() && !project.canCreateRecipes()) excluded.addAll(Set.of("source", "id"));
        Forms.object(basicFields, project.selected, this::refresh, this::refreshPreview, excluded);
        basic.addContent("basic/fields", basicFields);
        var parameters = group(treeRoot, "parameters", "vjce.recipe.parameters");
        var parameterFields = Forms.column();
        if (project.canCreateRecipes()) Forms.object(parameterFields, project.selected, this::refresh, this::refreshPreview, Set.of("id","source","title","hidden","overrideIngredients","slots","focusLinks"));
        for (var field : project.document.fields) Forms.line(parameterFields, field.label + " (" + field.unit + ")", String.valueOf(project.selected.values.getOrDefault(field.key, field.initial)), value -> {
            try { project.selected.values.put(field.key, Double.parseDouble(value)); refreshPreview(); } catch (NumberFormatException ignored) {}
        });
        parameters.addContent("parameters/fields", parameterFields);
        formTree = new TreeList<>(); formTree.setId("vjce_recipe_tree");
        formTree.setFlattenRoot(true).setSelectableNodeFilter(n -> false).setClickToExpand(true).setClickToExpandFilter(n -> n.getContent() == null);
        formTree.setNodeUISupplier(n -> n.getContent() != null ? n.getContent() : new Label().setText(nodeTitles.getOrDefault(n.getKey(), n.getKey())).layout(l -> { l.flex(1); l.height(18); }));
        formTree.setRoot(treeRoot);
        expand(formTree, treeRoot, expanded); root.addChild(formTree);
        for (String role : List.of("INPUT", "OUTPUT", "CATALYST")) slotActions.addChild(new Button()
                .setText(Component.literal("+ ").append(Component.translatable("vjce.role." + role))).setOnClick(e -> {
            var slot = new CategoryDocument.Slot(); slot.role = role;
            slot.identifier = newSlotIdentifier();
            slot.name = project.document.target.isBlank() ? "vjce_" + slot.identifier : slot.identifier;
            if (!project.selected.source.isBlank()) slot.sourceIndex = -1;
            slot.x = role.equals("OUTPUT") ? 120 : 8 + (project.selected.slots.size() % 4) * 20;
            slot.y = 20;
            var ingredient = new CategoryDocument.Ingredient(); ingredient.item = "minecraft:stone";
            slot.ingredients.add(ingredient); project.selected.slots.add(slot);
            project.layoutView.addSlot(project.selected.slots.size() - 1);
            project.selected.overrideIngredients = true;
            project.selected.focusLinks.clear();
            refreshBindings(slot.identifier);
            selectSlot(slot);
            refresh();
        }).setId("vjce_add_slot_" + role));
    }

    @Override public UIElement setDisplay(boolean display) {
        if (!display && selectedSlot != null) clearSlotSelection();
        return super.setDisplay(display);
    }

    @Override protected void onRemoved() {
        clearSlotSelection();
        super.onRemoved();
    }

    private void selectSlot(CategoryDocument.Slot slot) {
        if (slot != selectedSlot) {
            selectedSlot = slot;
            inspectedRecipe = project.selected;
            updateSlotButtons();
            inspectSlot();
        }
        var inspector = editor.inspectorView;
        var container = inspector.getViewContainer();
        if (container != null && container != getViewContainer()) {
            container.expand();
            container.selectView(inspector);
        }
    }

    private void clearSlotSelection() {
        var inspection = slotInspection;
        forgetSlot();
        if (inspection != null && editor.inspectorView.inspector.getInspectedConfigurable() == inspection) {
            editor.inspectorView.clear();
        }
    }

    private void forgetSlot() {
        selectedSlot = null;
        inspectedRecipe = null;
        slotInspection = null;
        updateSlotButtons();
    }

    private void updateSlotButtons() {
        slotButtons.forEach((slot, button) -> {
            if (slot == selectedSlot) button.addClass("__confirm-button__");
            else button.removeClass("__confirm-button__");
        });
    }

    private String slotTitle(int index, CategoryDocument.Slot slot) {
        return project.selected.slotIdentifier(index) + (slot.name.isBlank() ? "" : " · " + slot.name);
    }

    private String newSlotIdentifier() {
        var used = new HashSet<>(project.document.nativeOriginElements);
        project.layoutView.getCurrentUI().rootElement.select("*").forEach(element -> used.add(element.getId()));
        for (var recipe : project.document.recipes) {
            for (int i = 0; i < recipe.slots.size(); i++) used.add(recipe.slotIdentifier(i));
        }
        String prefix = project.document.target.isBlank() ? "slot_" : "vjce_slot_";
        int index = project.selected.slots.size();
        while (used.contains(prefix + index)) index++;
        return prefix + index;
    }

    private UIElement pageSlot(String identifier) {
        return RecipeUI.slotElement(project.layoutView.getCurrentUI().rootElement, identifier);
    }

    private void identifierInput(UIElement properties, CategoryDocument.Recipe recipe, int index) {
        var slot = recipe.slots.get(index);
        String current = recipe.slotIdentifier(index);
        var input = Forms.text(current, value -> {});
        input.setId("edit_identifier");
        var message = new Label().textStyle(s -> s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        message.setId("vjce_slot_identifier_status");
        message.setText("vjce.slot.binding_missing").setDisplay(!slot.invisible && pageSlot(current) == null);
        Runnable apply = () -> {
            String target = input.getValue().strip();
            if (target.isEmpty() || pageSlot(target) == null) {
                message.setText("vjce.slot.binding_invalid").setDisplay(true);
                return;
            }
            if (target.equals(current)) { message.setDisplay(false); return; }
            var occupied = recipe.boundSlot(target);
            Runnable rebind = () -> {
                if (selectedSlot != slot || project.selected != recipe) return;
                if (occupied != null) occupied.identifier = current;
                slot.identifier = target;
                refreshBindings(current, target);
                refresh();
                inspectSlot();
            };
            if (occupied == null) rebind.run();
            else {
                var prompt = Component.translatable("vjce.slot.binding_swap_prompt", target, current);
                if (pageSlot(current) == null) prompt.append("\n").append(Component.translatable("vjce.slot.binding_swap_missing"));
                Dialog.showCheckBox("vjce.slot.binding_swap", prompt, accepted -> {
                    if (accepted) rebind.run();
                    else input.setText(current, false);
                }).show(properties.getModularUI());
            }
        };
        input.addEventListener(UIEvents.KEY_DOWN, event -> {
            if (event.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || event.keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                apply.run();
                event.stopPropagation();
            }
        });
        input.setActive(!slot.invisible);
        properties.addChildren(new Label().setText("vjce.field.identifier"), Forms.row().addChildren(input,
                new Button().setText("vjce.slot.binding_apply").setOnClick(event -> apply.run()).setActive(!slot.invisible)
                        .setId("vjce_apply_slot_identifier")), message);
    }

    private void refreshBindings(String... identifiers) {
        var layout = project.layoutView;
        for (String identifier : identifiers) {
            RecipeUI.updateSlotPreview(layout.getCurrentUI().rootElement, project.selected, identifier);
        }
        refreshPreview();
    }

    private void inspectSlot() {
        var slot = selectedSlot;
        int index = inspectedRecipe.slots.indexOf(slot);
        var inspection = IConfigurable.create(group -> {
            var properties = Forms.column().setId("vjce_slot_inspector");
            var heading = new Label().setText(slotTitle(index, slot));
            var role = new Selector<String>().setCandidates(SLOT_ROLES).setSelected(slot.role, false);
            role.setId("edit_role");
            role.layout(l -> { l.widthPercent(100); l.height(20); });
            role.setCandidateUIProvider(value -> new Label().setText("vjce.role." + value));
            role.setOnValueChanged(value -> { slot.role = value; ingredientsChanged(); refresh(); });
            properties.addChildren(heading, new Label().setText("vjce.field.role"), role);
            identifierInput(properties, inspectedRecipe, index);
            Forms.object(properties, slot, this::inspectSlot, () -> {
                String title = slotTitle(index, slot);
                heading.setText(title);
                var button = slotButtons.get(slot);
                if (button != null) {
                    button.setText(title);
                    button.style(s -> s.tooltips(title));
                }
                ingredientsChanged();
            }, Set.of("role", "ingredients", "identifier", "sourceIndex", "x", "y", "invisible"));
            properties.addChild(new Button().setText("vjce.delete_slot").setOnClick(e -> {
                clearSlotSelection();
                String identifier = project.selected.slotIdentifier(index);
                project.selected.removeSlot(index);
                project.selected.overrideIngredients = true;
                project.selected.focusLinks.clear();
                refreshBindings(identifier);
                refresh();
            }).addClass("__reject-button__").setId("vjce_delete_slot_" + index));
            var fields = new Configurator();
            fields.lineContainer.setDisplay(false);
            fields.addChild(properties);
            group.addConfigurator(fields);

            var alternatives = new ConfiguratorGroup("vjce.field.ingredients", false);
            for (int i = 0; i < slot.ingredients.size(); i++) {
                int alternative = i;
                var candidate = new ConfiguratorGroup("#" + i, i != 0);
                var entry = Forms.column();
                entry.addChild(new Button().setText("vjce.delete").setOnClick(e -> {
                    slot.ingredients.remove(alternative);
                    ingredientsChanged();
                    inspectSlot();
                }).addClass("__reject-button__"));
                Forms.ingredient(entry, slot.ingredients.get(i), replacement -> {
                    if (selectedSlot != slot) return;
                    slot.ingredients.set(alternative, replacement);
                    ingredientsChanged();
                    inspectSlot();
                }, this::inspectSlot, this::ingredientsChanged);
                candidate.configuratorContainer.addChild(entry);
                alternatives.addConfigurator(candidate);
            }
            alternatives.configuratorContainer.addChild(new Button().setText("vjce.add").setOnClick(e -> {
                slot.ingredients.add(new CategoryDocument.Ingredient());
                ingredientsChanged();
                inspectSlot();
            }).setId("vjce_add_slot_ingredient"));
            group.addConfigurator(alternatives);
        });
        slotInspection = inspection;
        editor.inspectorView.inspect(inspection, null, () -> {
            if (slotInspection == inspection) forgetSlot();
        });
    }

    private TreeNode<String, UIElement> group(TreeNode<String, UIElement> parent, String path, String title) {
        nodeTitles.put(path, title); return parent.createChild(path);
    }
    private static void expand(TreeList<TreeNode<String, UIElement>> tree, TreeNode<String, UIElement> node, Set<String> keys) {
        if (keys.contains(node.getKey())) tree.expandNode(node);
        for (var child : node.getChildren()) expand(tree, child, keys);
    }
    private void ingredientsChanged() { project.selected.overrideIngredients = true; project.selected.focusLinks.clear(); refreshPreview(); }
    private void rebuildBrowser() {
        var root = browser.getContentHost(); root.clearAllChildren(); root.addChild(browserResults);
        if (!project.document.target.isBlank()) browseOriginals(root);
    }
    private void rebuildSavedRecipes() {
        browserResults.clearAllChildren(); browserResults.addChild(new Label().setText("vjce.recipe.saved"));
        for (var recipe : project.document.recipes) browserResults.addChild(recipeButton(recipe.id + (recipe.hidden ? " [hidden]" : ""), recipe == project.selected, () -> project.select(recipe)).setId("vjce_recipe_" + recipe.id));
        // Source buttons update their selected material without rebuilding the scrolling browser.
        browser.select("*").filter(Button.class::isInstance).filter(b -> b.getId().startsWith("vjce_source_")).forEach(b -> {
            if (b.getId().equals("vjce_source_" + project.selected.source)) b.addClass("__confirm-button__"); else b.removeClass("__confirm-button__");
        });
    }
    private Button recipeButton(String text, boolean selected, Runnable action) {
        var button = new Button().setText(text).setOnClick(e -> action.run());
        if (selected) button.addClass("__confirm-button__");
        button.layout(l -> { l.widthPercent(100); l.minHeight(20); });
        button.style(st -> st.tooltips(text)); return button;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void browseOriginals(UIElement root) {
        if (VjceJeiPlugin.runtime == null) return;
        var original = VjceJeiPlugin.originals.get(project.document.target);
        if (original == null) return;
        var search = Forms.text(browserSearch, value -> {});
        var results = Forms.column();
        Runnable render = () -> {
            results.clearAllChildren();
            var recipes = VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get()
                    .filter(r -> {
                        var id = ((mezz.jei.api.recipe.category.IRecipeCategory) original).getRegistryName(r);
                        return browserSearch.isBlank() || String.valueOf(id).contains(browserSearch);
                    }).skip(browserPage * 25L).limit(25).toList();
            for (Object raw : recipes) {
                var id = ((mezz.jei.api.recipe.category.IRecipeCategory) original).getRegistryName(raw);
                results.addChild(recipeButton(String.valueOf(id == null ? raw : id), id != null && id.toString().equals(project.selected.source), () -> action(() -> {
                    var record = CapturedCategory.capture((mezz.jei.api.recipe.category.IRecipeCategory) original, raw);
                    var existing = project.document.recipes.stream().filter(r -> r.source.equals(record.source)).findFirst();
                    if (existing.isPresent()) project.select(existing.get());
                    else {
                        if (!record.source.startsWith("vjce:generated/")) project.document.recipes.add(record);
                        project.select(record);
                    }
                })).setId("vjce_source_" + (id == null ? System.identityHashCode(raw) : id)));
            }
        };
        search.setTextResponder(value -> { browserSearch = value; browserPage = 0; render.run(); });
        root.addChildren(new Label().setText("vjce.import_original"), search,
                new Button().setText("vjce.previous").setOnClick(e -> { browserPage = Math.max(0, browserPage - 1); render.run(); }),
                new Button().setText("vjce.next").setOnClick(e -> { browserPage++; render.run(); }), results);
        render.run();
    }
    private static String unique() { return "vjce:recipe_" + UUID.randomUUID().toString().replace("-", ""); }
    private void action(Checked task) {
        try { task.run(); } catch (Exception e) { status.setText(e.getMessage() == null ? e.toString() : e.getMessage()); VisualJeiCategoryEditor.LOG.error("VJCE editor action failed", e); }
    }
    public void showError(String message) { status.setText(message == null ? "Invalid preview data" : message); }
    private void refreshPreview() {
        if (project.layoutView.isSimulationRunning()) {
            project.layoutView.stopSimulation(); project.layoutView.startSimulation();
        } else if (!project.document.target.isBlank()) {
            RecipeUI.bindSource(project.layoutView.getCurrentUI(), project.document, project.selected);
            project.layoutView.hierarchy.loadUI(project.layoutView.getCurrentUI());
        }
    }
    private interface Checked { void run() throws Exception; }
}
