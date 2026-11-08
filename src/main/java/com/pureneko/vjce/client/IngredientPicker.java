package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.editor.resource.Resource;
import com.lowdragmc.lowdraglib2.editor.ui.resource.ResourceProviderContainer;
import com.pureneko.vjce.integration.IngredientAccess;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import dev.vfyjxf.taffy.style.FlexDirection;
import mezz.jei.api.ingredients.*;
import mezz.jei.api.constants.VanillaTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** SearchComponent input with an external row-virtualized resource grid; helpers stay on the UI thread. */
final class IngredientPicker {
    private IngredientPicker() {}
    private record Entry(ITypedIngredient<?> ingredient, String name, String id, String search) {}
    static void open(UIElement parent, Consumer<ITypedIngredient<?>> chosen) {
        if (IngredientAccess.manager == null) return;
        open(parent, "vjce.choose_ingredient", new ArrayList<>(IngredientAccess.manager.getRegisteredIngredientTypes()), true, false,
                ingredient -> true, chosen);
    }

    static void openBlocks(UIElement parent, Predicate<ItemStack> eligible, Consumer<ItemStack> chosen) {
        openItems(parent, "vjce.asset.from_jei", eligible, false, chosen);
    }

    static void openCatalysts(UIElement parent, Consumer<ItemStack> chosen) {
        openItems(parent, "vjce.field.catalysts", stack -> true, true, chosen);
    }

    private static void openItems(UIElement parent, String title, Predicate<ItemStack> eligible, boolean details, Consumer<ItemStack> chosen) {
        var runtime = VjceJeiPlugin.runtime;
        if (runtime == null || IngredientAccess.manager == null) {
            Dialog.showNotification(title, "vjce.asset.jei_unavailable", null).show(parent.getModularUI());
            return;
        }
        var visibility = runtime.getJeiHelpers().getIngredientVisibility();
        open(parent, title, List.of(VanillaTypes.ITEM_STACK), false, details,
                ingredient -> ingredient.getItemStack().filter(eligible)
                        .filter(stack -> visibility.isIngredientVisible(VanillaTypes.ITEM_STACK, stack)).isPresent(),
                ingredient -> ingredient.getItemStack().ifPresent(chosen));
    }

    static Component[] itemTooltip(ItemStack stack, String id) {
        var minecraft = Minecraft.getInstance();
        var renderer = IngredientAccess.manager.getIngredientRenderer(VanillaTypes.ITEM_STACK);
        var lines = new ArrayList<>(renderer.getTooltip(stack, Item.TooltipContext.of(minecraft.level), minecraft.player, TooltipFlag.NORMAL));
        lines.add(Component.literal(id));
        return lines.toArray(Component[]::new);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void open(UIElement parent, String title, List<IIngredientType<?>> types, boolean selectType, boolean details,
                             Predicate<ITypedIngredient<?>> eligible, Consumer<ITypedIngredient<?>> chosen) {
        if (parent.getModularUI() == null) return;
        var modal = new Dialog().setTitle(title); modal.setId("vjce_ingredient_dialog");
        modal.overlay.layout(l -> { l.width(480); l.maxWidthPercent(95); });
        var grid = new VirtualScrollerView<List<Entry>>(); grid.setId("vjce_ingredient_grid");
        grid.layout(l -> { l.widthPercent(100); l.height(230); });
        grid.virtualScrollerViewStyle(s -> s.itemHeightMode(VirtualItemHeightMode.FIXED).estimatedItemHeight(52).overscanPixels(104));
        grid.setItemUIProvider(values -> {
            var row = new UIElement().layout(l -> { l.widthPercent(100); l.height(52); l.flexDirection(FlexDirection.ROW); l.gapAll(3); });
            for (var entry : values) {
                var icon = new IngredientSlot(); icon.ingredient = entry.ingredient;
                icon.layout(l -> { l.width(28); l.height(28); });
                var cell = ResourceProviderContainer.createResourceCell(Resource.DisplayMode.GRID, 40, icon, entry.name);
                if (details) entry.ingredient.getItemStack().ifPresent(stack -> cell.style(s -> s.tooltips(itemTooltip(stack, entry.id))));
                else cell.style(s -> s.tooltips(entry.name, entry.id));
                cell.addEventListener(UIEvents.CLICK, e -> { if (e.button == 0) { chosen.accept(entry.ingredient); modal.close(); e.stopPropagation(); } });
                cell.setId("vjce_ingredient_cell"); row.addChild(cell);
            }
            return row;
        });
        var entries = new ArrayList<Entry>(); var count = new Label();
        int[] lastColumns={-1};
        var search = new GridSearch(word -> {
            String query = word.toLowerCase(Locale.ROOT).trim();
            var matches = entries.stream().filter(e -> e.search.contains(query)).toList();
            int columns = Math.max(1, (int) (Math.max(200, grid.getContentWidth()) / 49));
            lastColumns[0]=columns;
            var rows = new ArrayList<List<Entry>>();
            for (int i = 0; i < matches.size(); i += columns) rows.add(matches.subList(i, Math.min(matches.size(), i + columns)));
            grid.setItems(rows); grid.scrollToTop(); count.setText(Integer.toString(matches.size()));
        });
        grid.addEventListener(UIEvents.LAYOUT_CHANGED,e->{int columns=Math.max(1,(int)(Math.max(200,grid.getContentWidth())/49));if(columns!=lastColumns[0])search.search();});
        search.textField.setId("vjce_ingredient_search_text");
        search.setId("vjce_ingredient_search"); search.layout(l -> { l.widthPercent(100); l.height(20); });
        var type = new Selector<IIngredientType<?>>().setCandidates(types);
        type.setId("vjce_ingredient_type"); type.layout(l -> { l.widthPercent(100); l.height(20); });
        type.setCandidateUIProvider(value -> new Label().setText(value == null ? "" : value.getUid()));
        type.setOnValueChanged(selected -> {
            entries.clear();
            if (selected != null) {
                IIngredientType rawType = selected; var helper = IngredientAccess.manager.getIngredientHelper(rawType);
                for (Object value : IngredientAccess.manager.getAllIngredients(rawType)) {
                    var typed = IngredientAccess.manager.createTypedIngredient(rawType, value, false);
                    if (typed.isEmpty() || !eligible.test((ITypedIngredient<?>) typed.get())) continue;
                    String name = helper.getDisplayName(value), id = helper.getResourceLocation(value).toString();
                    entries.add(new Entry((ITypedIngredient<?>) typed.get(), name, id, (name + " " + id).toLowerCase(Locale.ROOT)));
                }
            }
            search.search();
        });
        if (selectType) modal.addContent(type);
        modal.addContent(search).addContent(count).addContent(grid);
        modal.addButton(new Button().setText("vjce.close").setOnClick(e -> modal.close()));
        modal.show(parent.getModularUI()); search.show();
        if (!types.isEmpty()) type.setSelected(types.getFirst(), true);
    }
    private static final class GridSearch extends SearchComponent<ITypedIngredient<?>> {
        private Consumer<String> query;
        GridSearch(Consumer<String> query) { this.query = query; }
        void search() { if (query != null) query.accept(textField.getValue()); }
        @Override protected void onSearchWordChanged(String word) { if (query != null) query.accept(word); }
        @Override public void show() { preview.setDisplay(false); textField.setDisplay(true); search(); }
        @Override public void hide() {} // The containing modal owns the grid lifetime and focus.
    }
}
