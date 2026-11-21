package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.editor.configurator.WrapperConfigurator;
import com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import net.minecraft.client.Minecraft;
import java.util.*;
import static com.pureneko.vjce.client.PortValidation.*;

/** Opt-in real-client regression for panel bounds, selection pixels and inspector input. */
final class PanelValidation {
    private static VjceProject project;
    private static SceneEditingView scene;
    private static Forms.Form fields;
    private static DraggableScrollableWidgetGroup outer;
    private static TextFieldWidget draft;
    private static String secondId;
    private static int scale;
    @SuppressWarnings({"rawtypes","unchecked"}) static boolean step(int phase,VjceEditor editor)throws Exception{
        var mc=Minecraft.getInstance();var props=(EditorPanels.Properties)editor.getConfigPanel();var resources=(EditorPanels.Resources)editor.getResourcePanel();
        switch(phase){
            case 42 -> {
                project=VjceEditor.projectForCategory("minecraft:crafting");editor.loadProject(project);
                var original=(mezz.jei.api.recipe.category.IRecipeCategory)VjceJeiPlugin.originals.get("minecraft:crafting");
                var raw=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(original.getRecipeType()).get().limit(2).toList();
                var second=CapturedCategory.capture(original,raw.get(1));secondId=second.id;project.document.recipes.add(second);
                project.dataView.refresh();editor.getTabPages().switchTag(project.dataView);
            }
            case 43 -> {
                screenshot("panels-recipe-selected-first");selectionPixels(project.selected.id,secondId);roleRow();
                var second=buttons(project.dataView.browser,secondId).get(1);click(second);
                check("source-list click selects corresponding imported recipe",project.selected.id.equals(secondId));
            }
            case 44 -> {
                screenshot("panels-recipe-selected-second");selectionPixels(secondId,project.document.recipes.get(0).id);
                project.dataView.inspectSlot(0);
            }
            case 45 -> {
                outer=inspectorScroll(props);var wrapper=(WrapperConfigurator)outer.widgets.get(0);fields=(Forms.Form)wrapper.inner;
                check("slot inspector has no inner scrollbar or scroll handler",barWidth(fields)==0&&!fields.isScrollable());
                var all=new ArrayList<>(project.dataView.form.widgets);draft=project.dataView.form.widgets.stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();draft.setCurrentString("qa_unsaved_partial:");draft.setFocus(true);
                int width=project.dataView.form.getSize().width,height=project.dataView.form.getSize().height;
                resources.hide();props.setCollapsed(true);project.dataView.sidebar.setCollapsed(true);
                long now=System.nanoTime();editor.reflowPanels(now+200_000_000L);project.dataView.sidebar.advanceAnimation(now+200_000_000L);
                check("all three panels have intermediate extents",props.occupied()>16&&props.occupied()<252&&resources.occupied()>0&&resources.occupied()<100&&project.dataView.sidebar.occupied()>16);
                check("data reflows during simultaneous animations",project.dataView.form.getSize().width>width&&project.dataView.form.getSize().height>height);
                editor.reflowPanels(now+600_000_000L);project.dataView.sidebar.advanceAnimation(now+600_000_000L);
                check("data collapse fills freed width and height",project.dataView.form.getSize().width==editor.getSize().width-16-16-3&&project.dataView.getPosition().y+project.dataView.getSize().height==editor.getSize().height);
                check("reflow keeps existing widgets and partial input",all.equals(project.dataView.form.widgets)&&draft.getCurrentString().equals("qa_unsaved_partial:"));roleRow();
            }
            case 46 -> {
                screenshot("panels-data-expanded-workspace");check("right expand arrow remains reachable",props.toggle.isVisible()&&props.toggle.getPosition().x+12<=editor.getSize().width);
                click(props.toggle);check("right arrow starts expansion",!props.collapsed);editor.reflowPanels(System.nanoTime()+600_000_000L);
                resources.show();editor.reflowPanels(System.nanoTime()+600_000_000L);project.dataView.sidebar.setCollapsed(false);project.dataView.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
                check("opening panels keeps slot inspector identity",inspectorScroll(props)==outer&&((WrapperConfigurator)outer.widgets.get(0)).inner==fields&&project.dataView.selectedSlot==0);
                for(int i=0;i<100;i++)mc.screen.mouseScrolled(fields.getPosition().x+1,outer.getPosition().y+15,-1);
                check("wheel over form blank space scrolls outer inspector",outer.getScrollYOffset()>0&&fields.getScrollYOffset()==0);
            }
            case 47 -> {
                screenshot("panels-slot-bottom-wheel");var remove=buttons(fields,"vjce.slot.delete").get(0);
                check("wheel reaches final slot field",remove.isVisible()&&remove.getPosition().y>=outer.getPosition().y&&remove.getPosition().y+18<=outer.getPosition().y+outer.getSize().height);
                outer.setScrollYOffset(0);double x=outer.getPosition().x+outer.getSize().width-1,y=outer.getPosition().y+3;
                mc.screen.mouseClicked(x,y,0);mc.screen.mouseDragged(x,y+outer.getSize().height-6,0,0,outer.getSize().height-6);mc.screen.mouseReleased(x,y+outer.getSize().height-6,0);
                check("outer scrollbar drag reaches final field",outer.getScrollYOffset()>0&&remove.getPosition().y+18<=outer.getPosition().y+outer.getSize().height);
                editor.getTabPages().switchTag(project.layoutView);project.layoutView.zoom=1.3f;project.layoutView.panX=75;project.layoutView.panY=43;
                resources.hide();props.setCollapsed(true);project.layoutView.sidebar.setCollapsed(true);editor.reflowPanels(System.nanoTime()+600_000_000L);project.layoutView.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
                check("canvas fills collapsed panel space",project.layoutView.stage.getPosition().x+project.layoutView.stage.getSize().width==editor.contentWidth()&&project.layoutView.stage.getPosition().y+project.layoutView.stage.getSize().height==editor.getSize().height);
                check("canvas keeps zoom and pan",project.layoutView.zoom==1.3f&&project.layoutView.panX==75&&project.layoutView.panY==43);
            }
            case 48 -> {
                screenshot("panels-canvas-expanded-workspace");scene=new SceneEditingView(project.layoutView,null);editor.getTabPages().addTab("vjce.scene.configure",scene);editor.getTabPages().switchTag(scene);
                scene.preview.cameraYaw=73;scene.preview.cameraPitch=23;var child=scene.form.widgets.get(0);int before=scene.viewport.getSize().width;
                click(scene.sidebar.widgets.stream().filter(ButtonWidget.class::isInstance).findFirst().orElseThrow());check("scene sidebar arrow avoids global inspector tabs",scene.sidebar.collapsed);scene.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
                check("scene sidebar expands viewport and keeps fields",scene.viewport.getSize().width>before&&scene.form.widgets.get(0)==child&&scene.preview.cameraYaw==73&&scene.preview.cameraPitch==23);
                int w=editor.getSize().width,h=editor.getSize().height;resources.show();props.setCollapsed(false);scene.sidebar.setCollapsed(false);
                editor.reflowPanels(System.nanoTime()+150_000_000L);scene.sidebar.advanceAnimation(System.nanoTime()+150_000_000L);editor.onScreenSizeUpdate(w-90,h-40);
                editor.reflowPanels(System.nanoTime()+600_000_000L);scene.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
                check("window resize preserves animations and camera",!props.collapsed&&props.occupied()==252&&resources.occupied()==100&&scene.sidebar.occupied()==scene.sidebar.expandedWidth&&scene.preview.cameraYaw==73&&scene.form.widgets.get(0)==child);
                editor.onScreenSizeUpdate(w,h);scale=mc.options.guiScale().get();mc.options.guiScale().set(3);mc.resizeDisplay();
            }
            case 49 -> {
                screenshot("panels-gui-scale-3");check("GUI scaling retains scene and bounds",editor.getTabPages().focus==scene&&scene.preview.cameraYaw==73&&scene.sidebar.getPosition().x+scene.sidebar.occupied()<=editor.contentWidth()&&scene.getSize().height==editor.contentHeight());
                mc.options.guiScale().set(scale);mc.resizeDisplay();scene.close();WorldSelectionDialog.open(project.layoutView,()->new net.minecraft.nbt.CompoundTag(),tag->{});
            }
            case 50 -> {
                var dialog=world(editor);var eye=new org.joml.Vector3f(dialog.view.eye);var center=new org.joml.Vector3f(dialog.view.center);var from=dialog.from;var children=new ArrayList<>(dialog.form.widgets);
                int w=dialog.view.getSize().width;click(dialog.sidebar.widgets.stream().filter(ButtonWidget.class::isInstance).findFirst().orElseThrow());check("world sidebar arrow responds to real input",dialog.sidebar.collapsed);dialog.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
                check("world selection fills collapsed sidebar space",dialog.view.getSize().width>w&&dialog.view.getPosition().x+dialog.view.getSize().width==dialog.sidebar.getPosition().x-3);
                check("world selection retains camera selection and fields",eye.equals(dialog.view.eye)&&center.equals(dialog.view.center)&&from.equals(dialog.from)&&children.equals(dialog.form.widgets));
            }
            case 51 -> {
                screenshot("panels-world-expanded-workspace");world(editor).close();editor.getTabPages().switchTabIndex(0);
                var library=(Forms.Form)editor.getTabPages().focus;draft=library.widgets.stream().filter(TextFieldWidget.class::isInstance).map(TextFieldWidget.class::cast).findFirst().orElseThrow();draft.setCurrentString("anvil");
                resources.hide();props.setCollapsed(true);editor.reflowPanels(System.nanoTime()+600_000_000L);
                check("library fills freed space and retains search widget",library.getSize().width==editor.contentWidth()&&library.getPosition().y+library.getSize().height==editor.getSize().height&&library.widgets.contains(draft)&&draft.getCurrentString().equals("anvil"));
            }
            case 52 -> {
                screenshot("panels-library-expanded-workspace");editor.getTabPages().switchTag(project.dataView);project.dataView.categoryMode=true;project.dataView.refresh();
                var child=project.dataView.form.widgets.get(0);for(int i=0;i<4;i++){
                    resources.show();props.setCollapsed(false);long now=System.nanoTime();editor.reflowPanels(now+170_000_000L);
                    resources.hide();props.setCollapsed(true);editor.reflowPanels(System.nanoTime()+600_000_000L);
                }
                check("reversed panel animations converge without rebuilding category fields",props.occupied()==16&&resources.occupied()==0&&project.dataView.form.widgets.get(0)==child&&project.dataView.form.getSize().width==editor.contentWidth()-project.dataView.sidebar.occupied()-3);
            }
            case 53 -> {screenshot("panels-category-expanded-workspace");resources.show();props.setCollapsed(false);editor.reflowPanels(System.nanoTime()+600_000_000L);}
            case 54 -> {
                click(props.toggle);check("right collapse arrow responds to screen input",props.collapsed);editor.reflowPanels(System.nanoTime()+600_000_000L);
                var toggle=resources.widgets.stream().filter(ButtonWidget.class::isInstance).findFirst().orElseThrow();click(toggle);check("resource collapse arrow responds after reflow",!resources.isShow());editor.reflowPanels(System.nanoTime()+600_000_000L);
                click(toggle);check("resource expand arrow remains clickable",resources.isShow());editor.reflowPanels(System.nanoTime()+600_000_000L);
                editor.loadProject(new VjceProject());project=(VjceProject)editor.getCurrentProject();editor.getTabPages().switchTag(project.dataView);
                for(int i=0;i<3;i++){var row=(WidgetGroup)project.dataView.form.widgets.get(0);click(row.widgets.get(i));}
                check("horizontal buttons add input output and catalyst slots",project.selected.slots.stream().map(s->s.role).toList().equals(List.of("INPUT","OUTPUT","CATALYST")));
                check("editor remains live after repeated panel actions",mc.screen instanceof com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer&&mc.level!=null);return true;
            }
        }
        return false;
    }
    private static int barWidth(DraggableScrollableWidgetGroup group)throws Exception{var field=DraggableScrollableWidgetGroup.class.getDeclaredField("yBarWidth");field.setAccessible(true);return field.getInt(group);}
    private static void roleRow(){var row=(WidgetGroup)project.dataView.form.widgets.get(0);check("three add buttons share one row",row.widgets.size()==3&&row.widgets.stream().allMatch(w->w.getSelfPosition().y==0));int min=Integer.MAX_VALUE,max=0;for(var w:row.widgets){min=Math.min(min,w.getSize().width);max=Math.max(max,w.getSize().width);}check("role buttons have equal responsive widths",max-min<=1&&row.getSize().width==project.dataView.form.width());}
    private static List<ButtonWidget> buttons(WidgetGroup group,String text){return group.widgets.stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).filter(b->b.getBackgroundTexture() instanceof GuiTextureGroup g&&Arrays.stream(g.textures).anyMatch(t->t instanceof TextTexture label&&label.text.equals(com.lowdragmc.lowdraglib.utils.LocalizationUtils.format(text)))).toList();}
    private static void click(Widget w){var s=Minecraft.getInstance().screen;double x=w.getPosition().x+5,y=w.getPosition().y+8;s.mouseClicked(x,y,0);s.mouseReleased(x,y,0);}
    private static DraggableScrollableWidgetGroup inspectorScroll(EditorPanels.Properties props){return (DraggableScrollableWidgetGroup)((WrapperConfigurator)findWrapper(props)).getParent();}
    private static Widget findWrapper(WidgetGroup group){for(var w:group.widgets){if(w instanceof WrapperConfigurator c&&c.inner instanceof Forms.Form)return w;if(w instanceof WidgetGroup nested){var found=findWrapper(nested);if(found!=null)return found;}}return null;}
    private static WorldSelectionDialog world(VjceEditor editor){return (WorldSelectionDialog)editor.getGui().mainGroup.widgets.stream().filter(WorldSelectionDialog.class::isInstance).findFirst().orElseThrow();}
    private static void selectionPixels(String selected,String unselected)throws Exception{
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            var selectedButtons=buttons(project.dataView.browser,selected);check("same recipe appears in imported and source lists",selectedButtons.size()==2);
            for(var b:selectedButtons)check("selected recipe green in both lists "+b.getSelfPosition().y,greenPixels(image,b)>15);
            for(var b:buttons(project.dataView.browser,unselected))check("other recipe returns to gray "+b.getSelfPosition().y,greenPixels(image,b)==0);
        }
    }
    private static int greenPixels(com.mojang.blaze3d.platform.NativeImage image,Widget w){double scale=Minecraft.getInstance().getWindow().getGuiScale();int count=0;for(int y=(int)((w.getPosition().y+2)*scale);y<(w.getPosition().y+16)*scale;y++)for(int x=(int)((w.getPosition().x+8)*scale);x<(w.getPosition().x+w.getSize().width-8)*scale;x++){int c=image.getPixelRGBA(x,y),r=c&255,g=c>>8&255,b=c>>16&255;if(g>r*1.7&&g>b*1.3&&g>80)count++;}return count;}
}
