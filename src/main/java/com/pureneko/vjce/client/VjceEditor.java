package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import com.lowdragmc.lowdraglib2.editor.settings.*;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import java.util.*;

public final class VjceEditor extends Editor {
    public static void open() { EditorWindow.show(ResourceLocation.fromNamespaceAndPath("vjce", "editor"), VjceEditor::new); }
    public VjceEditor() {
        leftWindow.setDisplay(false);
        if (leftWindow.getParentWindow() != null) leftWindow.getParentWindow().removeSplitWindow(leftWindow);
        var library = new LibraryView(this);
        placeView(library, () -> getCenterWindow().getLeftTop());
    }
    @Override protected void initMenus() { super.initMenus(); fileMenu.addProjectProvider(VjceProject.TYPE); }
    @Override protected Editor createNewEditorInstance() { return new VjceEditor(); }
    @Override public void screenTick(){super.screenTick();EditorForms.ore(this);}
    @Override protected EditorSettings createSettings() {
        return new VjceEditorSettings(this, VisualJeiCategoryEditor.STORE.root.resolve("editor.json").toFile());
    }
    @Override protected void initEditorSettings() {
        var appearance = new AppearanceSettings();
        appearance.setStylesheet(StylesheetManager.ORE_MERGED);
        editorSettings.registerSettings(appearance, AppearanceSettings.CODEC);
        editorSettings.registerSettings(new BehaviorSettings(), BehaviorSettings.CODEC);
        editorSettings.registerSettings(new KeymapSettings(), KeymapSettings.CODEC);
    }
    @Override public Component getTitle() {
        if (getCurrentProject() instanceof VjceProject project) {
            String name = project.document.title.isBlank() ? project.document.id : project.document.title;
            String id = project.document.target.isBlank() ? project.document.id : project.document.target;
            return Component.literal(name + " [" + id + "]" + (isCurrentProjectDirty() ? " *" : ""));
        }
        return super.getTitle();
    }
    public static ResourceLocation overrideId(ResourceLocation category) {
        return ResourceLocation.fromNamespaceAndPath("vjce", "override/" + category.getNamespace() + "/" + category.getPath());
    }
    public static VjceProject projectForCategory(String id) {
        var original = VjceJeiPlugin.originals.get(id);
        if (original == null) throw new IllegalArgumentException("JEI category unavailable: " + id);
        if (original instanceof DisplayCategory<?> custom && !(original instanceof CapturedCategory<?>)) return new VjceProject(ProjectStore.copy(custom.document));
        var saved = VisualJeiCategoryEditor.STORE.loadAll().stream().filter(doc -> doc.target.equals(id)).findFirst();
        if (saved.isPresent()) return new VjceProject(ProjectStore.copy(saved.get()));
        var document = new CategoryDocument(); document.id = overrideId(ResourceLocation.parse(id)).toString();
        document.target = id; document.title = original.getTitle().getString();
        document.width = Math.clamp(original.getWidth(), 18, 2048); document.height = Math.clamp(original.getHeight(), 18, 2048);
        return new VjceProject(document);
    }

    private static final class LibraryView extends View {
        final VjceEditor editor;
        final ScrollerView list = new ScrollerView();
        String search = "";
        LibraryView(VjceEditor editor) {
            super("vjce.library"); this.editor = editor;
            addChild(new Label().setText("Visual JEI Category Editor · pureneko").layout(l -> {l.height(18);l.minHeight(18);l.flexShrink(0);}));
            addChild(new Button().setText("vjce.new").setOnClick(event -> editor.loadProject(new VjceProject(), null)).layout(l -> {l.height(20);l.flexShrink(0);}));
            addChild(new Button().setText("vjce.refresh").setOnClick(event -> refresh()).layout(l -> {l.height(20);l.flexShrink(0);}));
            var input=Forms.text("", value -> { search = value.strip().toLowerCase(Locale.ROOT); refresh(); });
            input.setId("vjce_category_search");input.textFieldStyle(s->s.placeholder(Component.translatable("vjce.library.search")));
            input.layout(l->{l.minWidth(0);l.height(20);l.minHeight(20);});
            addChild(Forms.row().layout(l->{l.height(22);l.minHeight(22);l.flexShrink(0);}).addChildren(input,
                    new Button().setText("vjce.library.clear").setOnClick(e->input.setText("",true)).setId("vjce_category_clear").layout(l->{l.width(38);l.flexShrink(0);})));
            list.layout(l -> { l.widthPercent(100); l.flex(1); l.minHeight(0); });
            addChild(list);
            refresh();
        }
        @SuppressWarnings({"unchecked", "rawtypes"}) void refresh() {
            var host = list.getContentHost(); host.clearAllChildren();
            int matches=0;
            host.addChild(new Label().setText("vjce.library.saved"));
            for (var document : VisualJeiCategoryEditor.STORE.loadAll()) {
                if(!matches(document.title,document.id,document.target))continue;
                matches++;
                host.addChild(new Button().setText(document.title + " · " + document.id)
                        .setOnClick(event -> editor.loadProject(new VjceProject(ProjectStore.copy(document)), null)).setId("vjce_saved_"+document.id));
            }
            host.addChild(new Label().setText("vjce.registered_categories"));
            for (var entry : VjceJeiPlugin.originals.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                String title = entry.getValue().getTitle().getString();
                if (!matches(title,entry.getKey())) continue;
                matches++;
                host.addChild(new Button().setText(title + " · " + entry.getKey()).setOnClick(event -> {
                    editor.loadProject(projectForCategory(entry.getKey()), null);
                }).setId("vjce_category_" + entry.getKey()));
            }
            if(matches==0)host.addChild(new Label().setText("vjce.library.no_results"));
            if (VjceJeiPlugin.runtime == null) host.addChild(new Label().setText("vjce.enter_world"));
            for (String error : VisualJeiCategoryEditor.STORE.errors) host.addChild(new Label().setText(error));
        }
        private boolean matches(String title,String... ids){
            var text=new StringBuilder(title);
            for(var value:ids){
                text.append(' ').append(value);
                var id=ResourceLocation.tryParse(value);
                if(id!=null){var namespace=id.getNamespace();text.append(' ').append(namespace);
                    net.neoforged.fml.ModList.get().getModContainerById(namespace).ifPresent(mod->text.append(' ').append(mod.getModInfo().getDisplayName()));}
            }
            return text.toString().toLowerCase(Locale.ROOT).contains(search);
        }
    }
}
