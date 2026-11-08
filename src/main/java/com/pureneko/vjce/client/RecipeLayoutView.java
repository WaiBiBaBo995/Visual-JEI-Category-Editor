package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.editor.view.UIEditorView;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import java.util.function.Consumer;
import com.lowdragmc.lowdraglib2.gui.ui.event.*;
import dev.vfyjxf.taffy.style.TaffyPosition;

public final class RecipeLayoutView extends UIEditorView {
    private final VjceProject project;
    final RecipeCanvasTransform transforms;
    private boolean scenePaused;
    private final Button pauseScene = new Button();
    public RecipeLayoutView(VjceProject project) {
        this.project = project; setName("vjce.layout");
        transforms = new RecipeCanvasTransform(this, project.document);
        graphView.addEventListener(UIEvents.MOUSE_DOWN, this::selectOnCanvas, true);
        header.select("*").filter(Toggle.class::isInstance).reduce((first, last) -> last)
                .ifPresent(toggle -> toggle.style(style -> style.tooltips("vjce.canvas.select.tip")));
        header.addChild(new Button().setText("vjce.generate").setOnClick(event -> {
            project.document.layout = "";
            project.document.nativeOriginElements.clear();
            var largest = project.document.recipes.stream().max(java.util.Comparator.comparingInt(recipe -> recipe.slots.size())).orElse(project.selected);
            loadTemplate(RecipeUI.template(project.document, largest), template -> project.document.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, template).getOrThrow().toString());
            RecipeUI.bindSource(getCurrentUI(), project.document, largest);
        }));
        header.addChild(new Button().setText("vjce.add_progress").setOnClick(event -> add(new ProgressBar(), "progress")).setId("vjce_add_progress"));
        header.addChild(new Button().setText("vjce.add_text").setOnClick(event -> add(new Label().setText("Text"), "text_" + getCurrentUI().rootElement.getChildren().size())).setId("vjce_add_text"));
        header.addChild(new Button().setText("vjce.scene.add").setOnClick(event -> SceneEditor.open(this,null)).setId("vjce_add_scene"));
        header.addChild(new Button().setText("vjce.scene.create").setOnClick(event -> add(new CreateAnimationElement(), "create_" + getCurrentUI().rootElement.getChildren().size())).setId("vjce_add_create"));
        graphView.addEventListener(UIEvents.DOUBLE_CLICK,event->{if(!canvasInputAllowed())return;var point=transforms.world(event);var hit=RecipeCanvasSelection.hits(modularUIPreview.getPreviewModularUI(),point.x,point.y,false).stream().filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst();hit.ifPresent(scene->{SceneEditor.open(this,scene);event.stopImmediatePropagation();});},true);
        addEventListener(UIEvents.MOUSE_DOWN,event->{if(event.button!=1||!canvasInputAllowed()||modularUIPreview.getPreviewModularUI()==null)return;var point=transforms.world(event);RecipeCanvasSelection.hits(modularUIPreview.getPreviewModularUI(),point.x,point.y,false).stream().filter(BlockSceneElement.class::isInstance).map(BlockSceneElement.class::cast).findFirst().ifPresent(scene->{getEditor().openMenu(this,event.x,event.y,com.lowdragmc.lowdraglib2.gui.util.TreeBuilder.Menu.start().leaf("vjce.scene.configure",()->SceneEditor.open(this,scene)));event.stopImmediatePropagation();});},true);
        header.addChild(new Button().setText("vjce.native.group").setOnClick(event -> CanvasGrouping.group(this)).setId("vjce_group_elements"));
        pauseScene.setText("vjce.scene.pause").setOnClick(e->{if(!isSimulationRunning()||canvas.getCanvasModularUI()==null)return;scenePaused=!scenePaused;SceneEditor.playing(canvas.getCanvasModularUI().ui,!scenePaused);pauseScene.setText(scenePaused?"vjce.scene.resume":"vjce.scene.pause");});
        header.addChild(pauseScene.setId("vjce_pause_scene"));
        header.addChild(new Button().setText("vjce.scene.reset").setOnClick(event -> (isSimulationRunning() && canvas.getCanvasModularUI()!=null ? canvas.getCanvasModularUI().ui : getCurrentUI()).rootElement.select("*").forEach(SceneEditor::reset)).setId("vjce_reset_scene"));
        var originalContainers=new java.util.ArrayList<>(header.getChildren().subList(0,3));
        var controls=new java.util.ArrayList<UIElement>();for(var container:originalContainers){controls.addAll(container.getChildren());container.removeSelf();}
        for(int i=0;i<controls.size();i++){var control=controls.get(i);int width=i==0?36:18;control.removeSelf();control.layout(l->{l.flexAuto();l.flexGrow(0);l.flexShrink(0);l.flexBasisAuto();l.width(width);l.height(18);});header.addChildAt(control,i);}
        header.select("*").filter(Button.class::isInstance).forEach(b -> b.layout(l -> l.flexShrink(0)));
        header.layout(l -> { l.flexWrap(dev.vfyjxf.taffy.style.FlexWrap.WRAP); l.heightAuto(); });
        if (!project.sourceIssue.isEmpty()) addChildAt(new Label().setText(project.sourceIssue)
                .textStyle(s -> s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true))
                .layout(l -> l.widthPercent(100)).setId("vjce_source_status"), 1);
        if (!project.sourceEditable) header.select("*").filter(Button.class::isInstance).forEach(button -> button.setActive(false));
        reload();
    }
    public void reload() {
        loadTemplate(RecipeUI.template(project.document, project.selected), template -> {
            project.document.layout = UITemplate.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, template).getOrThrow().toString();
        });
    }
    public boolean escapeCanvasTransform() {
        return canvasInputAllowed() && transforms != null && transforms.escape();
    }
    public boolean modalBlocksCanvas() {
        if (!CanvasModalGuard.blocked(getModularUI())) return false;
        if (transforms != null) transforms.clear();
        var drag = getModularUI().getDragHandler();
        if (drag.dragSource == graphView) drag.stopDrag();
        return true;
    }
    boolean canvasInputAllowed() {
        return project.sourceEditable && com.pureneko.vjce.integration.NativePage.visible(this) && !isSimulationRunning() && !modalBlocksCanvas();
    }
    @Override public RecipeLayoutView loadTemplate(UITemplate template, Consumer<UITemplate> saved) {
        if (transforms != null) transforms.clear();
        super.loadTemplate(RecipeUI.canvasTemplate(template.copy()), saved);
        // LDLib rebuilds the preview when this dock view is attached or reattached.
        if (project != null) {
            RecipeUI.bindSource(getCurrentUI(), project.document, project.selected);
            RecipeUI.bind(getCurrentUI(), project.document, project.selected);
            SceneEditor.playing(getCurrentUI(), false);
            hierarchy.loadUI(getCurrentUI());
            // The preview is rendered inside a separate graph UI; its host transform can change
            // without changing the recipe tree's layout or invalidating LDLib's pose cache.
            var preview = modularUIPreview.getPreviewModularUI();
            if (preview != null) {
                int[] size={getCurrentUI().rootElement.select("*").toList().size()};
                preview.addFrameCallback(time -> {
                    preview.ui.rootElement.clearPoseCache();
                    var nativePage=com.pureneko.vjce.integration.NativePage.context(getCurrentUI().rootElement);
                    if(nativePage!=null)nativePage.drawing.mount(getCurrentUI().rootElement,project.document);
                    int current=getCurrentUI().rootElement.select("*").toList().size();
                    if(current!=size[0]){size[0]=current;hierarchy.loadUI(getCurrentUI());}
                });
            }
        }
        return this;
    }
    private void selectOnCanvas(UIEvent event) {
        if (event.button != 0 || !canvasInputAllowed() || !isFocusHierarchyFromPreview() || modularUIPreview.getPreviewModularUI() == null) return;
        var point = transforms.world(event);
        if (transforms.intercept(event, point)) { event.stopImmediatePropagation(); return; }
        var hits = RecipeCanvasSelection.hits(modularUIPreview.getPreviewModularUI(), point.x, point.y, event.isAltDown());
        if (hits.isEmpty()) {
            hierarchy.treeList.setSelected(java.util.List.of(), true);
            if (graphView.getGraphViewStyle().allowPan()) graphView.startDrag(
                    new GraphView.DragOffset(graphView.getOffsetX(), graphView.getOffsetY()), null);
            event.stopImmediatePropagation();
            return;
        }
        int index = 0;
        if (event.isAltDown()) {
            var selected = hierarchy.getSelectedOne().orElse(null);
            int previous = hits.indexOf(selected);
            if (previous >= 0) index = (previous + 1) % hits.size();
        }
        focusElement(hits.get(index));
        transforms.picked(hits.get(index), event, point);
        event.stopImmediatePropagation();
    }
    private void add(UIElement element, String id) {
        if (element instanceof BlockSceneElement scene) scene.clock.playing = false;
        if (element instanceof CreateAnimationElement scene) scene.clock.playing = false;
        element.setId(id);
        element.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(60); l.top(35); l.width(element instanceof Scene || element instanceof CreateAnimationElement ? 90 : 60); l.height(element instanceof Scene || element instanceof CreateAnimationElement ? 90 : 12); });
        var root = getCurrentUI().rootElement;
        historyStack.pushHistory(net.minecraft.network.chat.Component.translatable("vjce.add"),
                com.lowdragmc.lowdraglib2.configurator.EditAction.of(() -> { root.addChild(element); hierarchy.loadUI(getCurrentUI()); },
                        () -> { element.removeSelf(); hierarchy.loadUI(getCurrentUI()); }));
        markAsDirty();
    }
    void addScene(BlockSceneElement scene){add(scene,"scene_"+getCurrentUI().rootElement.getChildren().size());focusElement(scene);}
    void commitScene(Runnable redo,Runnable undo){historyStack.pushHistory(net.minecraft.network.chat.Component.translatable("vjce.scene.configure"),com.lowdragmc.lowdraglib2.configurator.EditAction.of(redo,undo));}
    public void addSlot(int index) {
        var spec = project.selected.slots.get(index);
        var element = new IngredientSlot(); element.setId(project.selected.slotIdentifier(index));
        element.layout(l -> { l.positionType(TaffyPosition.ABSOLUTE); l.left(spec.x); l.top(spec.y); });
        getCurrentUI().rootElement.addChild(element);
        hierarchy.loadUI(getCurrentUI()); markAsDirty();
    }
    @Override public void startSimulation() {
        if (transforms != null) transforms.clear();
        if (getCurrentUI() == null) return;
        try {
            var template = getCurrentUI().toTemplate();
            template.copyStylesFrom(getTemplate());
            var ui = template.createUI();
            RecipeUI.bindSource(ui, project.document, project.selected);
            RecipeUI.bind(ui, project.document, project.selected);
            SceneEditor.playing(ui, true); scenePaused=false; pauseScene.setText("vjce.scene.pause");
            canvas.setDisplay(true); editor.setDisplay(false); canvas.startSimulation(ui);
        } catch (RuntimeException exception) {
            canvas.setDisplay(false); editor.setDisplay(true);
            if (project.dataView != null) project.dataView.showError(exception.getMessage());
        }
    }
    @Override protected void onAdded() {
        super.onAdded();
        if (transforms != null && getModularUI() != null) transforms.attach();
    }
    @Override protected void onRemoved() {
        if (transforms != null) { transforms.clear(); transforms.detach(); }
        super.onRemoved();
    }
}
