package com.pureneko.vjce.client;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.data.*;
import com.pureneko.vjce.integration.*;
import com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.joml.*;
import java.lang.Math;
import java.nio.file.*;
import java.util.*;
/** Opt-in development driver. All checks use the running client and registered JEI layouts. */
final class PortValidation {
    private static final boolean ENABLED=System.getProperty("vjce.uitest")!=null;
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static int tick,phase,wait;
    private static boolean done;
    private static VjceEditor editor;
    private static VjceProject project;
    private static SceneEditingView sceneView;
    private static SceneAssetConfigurator assetEditor;
    private static BlockPos capturePos;
    private static long started=System.currentTimeMillis();
    private static int pickerScale,referencePixel;
    private static String referenceLayout;
    static void tick(){if(!ENABLED||done)return;var mc=Minecraft.getInstance();tick++;try{
        if(tick%200==0){VisualJeiCategoryEditor.LOG.info("VJCE QA waiting phase {} screen {}",phase,mc.screen==null?"none":mc.screen.getClass().getName());screenshot("current-state");}
        if(phase==0&&mc.screen instanceof AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
        if(tick>18000)throw new IllegalStateException("Client validation timed out at phase "+phase);
        if(phase==0){if(!(mc.screen instanceof TitleScreen)||mc.getOverlay()!=null||tick<80)return;phase=1;String name="vjce-port-qa"+(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create")?"-create":"")+(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mekanism")?"-mekanism":"");if(Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name+"/level.dat")))mc.createWorldOpenFlows().loadLevel(mc.screen,name);else mc.createWorldOpenFlows().createFreshLevel(name,new LevelSettings(name,GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),new WorldOptions(12345,false,false),WorldPresets::createNormalWorldDimensions);return;}
        if(mc.player==null||VjceJeiPlugin.runtime==null)return;
        if (phase == 1) {
            if (++wait < 80) return;
            if (wait == 80) {
                var chat = new ChatScreen("/vjce editor");
                mc.setScreen(chat);
                chat.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0);
                return;
            }
            if (!(mc.screen instanceof ModularUIGuiContainer screen)
                    || screen.modularUI.mainGroup.widgets.isEmpty()
                    || !(screen.modularUI.mainGroup.widgets.get(0) instanceof VjceEditor opened)) {
                if (wait < 120) return;
                check("submitting /vjce editor in chat opens the editor", false);
                return;
            }
            check("submitting /vjce editor in chat opens the editor", true);
            editor = opened;
            project = new VjceProject();
            editor.loadProject(project);
            phase = switch (System.getProperty("vjce.uitest")) {
                case "anvil" -> 23;
                case "picker" -> 28;
                case "panels" -> 42;
                case "choices" -> 55;
                default -> 2;
            };
            wait = 0;
            return;
        }
        if(++wait<20)return;wait=0;
        switch(phase++){
            case 2 -> {editorChecks(); checkForgeProjectFile();}
            case 3 -> {screenshot("editor-layout");sceneChecks();}
            case 4 -> {renderedSceneChecks();screenshot("block-entity-scene");entityChecks();}
            case 5 -> {renderedSceneChecks();screenshot("entity-scene");CompatibilityAudit.run(PortValidation::check);nativeChecks();}
            case 6 -> {screenshot("native-page");var report=AllCategoriesAudit.run();check("all JEI categories audit",Boolean.TRUE.equals(report.get("passed")));}
            case 7 -> {resourceChecks();screenshot("scene-resources");sceneView=new SceneEditingView(project.layoutView,null);editor.getTabPages().addTab("vjce.scene.configure",sceneView);editor.getTabPages().switchTag(sceneView);}
            case 8 -> {sceneInteractionChecks();sceneInputChecks();screenshot("scene-editing");sceneView.close();if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create")){var e=new CreateAnimationElement();project.layoutView.getCurrentUI().rootElement.addChild(e);e.x=40;e.y=10;e.clock.playing=true;check("Create animation API",CreateAnimation.create("PRESS",false)!=null);}}
            case 9 -> {screenshot("integrations");prepareWorldCapture();}
            case 10 -> {worldCaptureChecks();WorldSelectionDialog.open(project.layoutView,()->new CompoundTag(),tag->{});for(var widget:((ModularUIGuiContainer)mc.screen).modularUI.mainGroup.widgets)if(widget instanceof WorldSelectionDialog dialog)dialog.view.lookAt(capturePos,capturePos);}
            case 11 -> {for(var widget:((ModularUIGuiContainer)mc.screen).modularUI.mainGroup.widgets)if(widget instanceof WorldSelectionDialog dialog){check("world selection dialog fills editor",dialog.getSize().width==editor.getSize().width&&dialog.getSize().height==editor.getSize().height);check("world selection preview rendered",dialog.view.renderer.viewProjection!=null);var vp=dialog.view;var projected=vp.renderer.viewProjection.transformProject(capturePos.getCenter().toVector3f());double hx=vp.getPosition().x+(projected.x+1)*vp.getSize().width/2,hy=vp.getPosition().y+(1-projected.y)*vp.getSize().height/2;var hit=vp.hit(hx,hy);check("world preview ray hits rendered block",hit!=null&&vp.rendered.contains(hit.getBlockPos()));var chosen=hit.getBlockPos();vp.mouseClicked(hx,hy,0);vp.release(hx,hy,0,true);check("shift click release selects actual ray hit",dialog.from.equals(chosen));var priorFrom=dialog.from;var priorTo=dialog.to;vp.mouseClicked(hx,hy,0);vp.release(vp.getPosition().x+1,vp.getPosition().y+1,0,true);check("drag across faces does not select corner",dialog.from.equals(priorFrom)&&dialog.to.equals(priorTo));dialog.nextFrom=true;var target=dialog.view.eye;var beforeEye=new Vector3f(target);dialog.view.mouseClicked(dialog.view.getPosition().x+10,dialog.view.getPosition().y+10,1);dialog.view.mouseDragged(0,0,1,15,10);dialog.view.mouseWheelMove(dialog.view.getPosition().x+10,dialog.view.getPosition().y+10,1);check("world RMB camera rotates in place and scroll changes speed",target.equals(beforeEye)&&dialog.view.moveSpeed>.1f);dialog.view.mouseReleased(0,0,1);dialog.pick(capturePos);dialog.pick(capturePos.offset(1,1,1));check("world corner selection alternates",dialog.from.equals(capturePos)&&dialog.to.equals(capturePos.offset(1,1,1)));int oldWidth=dialog.view.getSize().width;dialog.sidebar.setCollapsed(true);dialog.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);check("world settings collapse expands viewport",dialog.view.getSize().width>oldWidth);dialog.sidebar.setCollapsed(false);dialog.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);}screenshot("world-selection");for(var widget:List.copyOf(((ModularUIGuiContainer)mc.screen).modularUI.mainGroup.widgets))if(widget instanceof WorldSelectionDialog dialog)dialog.close();if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mekanism")){var key=VjceJeiPlugin.originals.keySet().stream().filter(k->k.startsWith("mekanism:")).findFirst().orElseThrow();editor.loadProject(VjceEditor.projectForCategory(key));project=(VjceProject)editor.getCurrentProject();}}
            case 12 -> {screenshot("mekanism-page");if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mekanism"))check("Mekanism source components render and mount",project.layoutView.getCurrentUI().rootElement.select("*").anyMatch(e->e.id.startsWith("draw_component_mek_")));var report=RuntimeAudit.run();check("registered ingredients and transfer audit",ProjectStore.JSON.fromJson(Files.readString(report),com.google.gson.JsonObject.class).get("passed").getAsBoolean());}
            case 13 -> {editor.getTabPages().switchTabIndex(0);}
            case 14 -> {screenshot("dark-library");editor.getTabPages().switchTabIndex(1);project.dataView.categoryMode=true;project.dataView.refresh();}
            case 15 -> {screenshot("dark-category-data");int old=project.dataView.form.getSize().width;project.dataView.sidebar.setCollapsed(true);project.dataView.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);check("data sidebar expands available width",project.dataView.form.getSize().width>old);}
            case 16 -> {screenshot("dark-data-collapsed");project.dataView.sidebar.setCollapsed(false);project.dataView.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);editor.getTabPages().switchTabIndex(2);revisionCanvasChecks();}
            case 17 -> {screenshot("canvas-green-handles");assetEditor=new SceneAssetConfigurator(SceneAssetResource.BLOCKS,com.mojang.datafixers.util.Either.left("qa_chest"),editor.getResourcePanel());editor.getConfigPanel().openConfigurator(com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel.Tab.RESOURCE,assetEditor);}
            case 18 -> {screenshot("block-resource-configurator");String id=assetEditor.data.getString("assetId");assetEditor.setState(net.minecraft.world.level.block.Blocks.OAK_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING,net.minecraft.core.Direction.EAST));var tile=new SceneAssetResource.CanvasPreview(0,0,40,40,new BlockSceneElement(),()->SceneAssetResource.BLOCKS.getResource(com.mojang.datafixers.util.Either.left("qa_chest")),false);tile.updateScreen();check("resource tile refreshes after structured edit",BlockSceneElement.rows(tile.scene.blocks,"blocks").getCompound(0).getCompound("state").getString("Name").equals("minecraft:oak_stairs"));tile.scene.release();check("structured block editor preserves asset identity and state",id.equals(SceneAssetResource.BLOCKS.getResource(com.mojang.datafixers.util.Either.left("qa_chest")).getString("assetId"))&&assetEditor.state().getValue(net.minecraft.world.level.block.StairBlock.FACING)==net.minecraft.core.Direction.EAST);var key=com.mojang.datafixers.util.Either.<String,java.io.File>left("qa_entity");SceneAssetResource.ENTITIES.addResource(key,SceneAssetResource.ENTITIES.defaults());assetEditor=new SceneAssetConfigurator(SceneAssetResource.ENTITIES,key,editor.getResourcePanel());editor.getConfigPanel().openConfigurator(com.lowdragmc.lowdraglib.gui.editor.ui.ConfigPanel.Tab.RESOURCE,assetEditor);}
            case 19 -> {screenshot("entity-resource-configurator");check("entity resource preview renders",assetEditor.preview.renderer!=null&&assetEditor.preview.error.isBlank());check("client render remains healthy",mc.level!=null&&mc.screen instanceof ModularUIGuiContainer);eightRevisionChecks();}
            case 20 -> {screenshot("recipe-data-revised");if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ae2")){editor.loadProject(VjceEditor.projectForCategory("ae2:attunement"));project=(VjceProject)editor.getCurrentProject();}}
            case 21 -> {screenshot("ae2-attunement");if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ae2")){check("AE2 attunement loads slots",project.selected.slots.size()>=2);checkEditorTextPixels();var nativePage=NativePage.context(project.layoutView.getCurrentUI().rootElement);var input=project.layoutView.getCurrentUI().rootElement.select("*").filter(NativePartElement.class::isInstance).map(NativePartElement.class::cast).filter(e->e.part.equals("slot_0")).findFirst().orElseThrow();check("AE2 attunement keeps descriptive ingredient tooltip",nativePage.previewSlot(input).getTooltip().size()>1);}IngredientPicker.open(project.layoutView,typed->{});}
            case 22 -> {screenshot("ingredient-picker-icons");var picker=editor.getGui().mainGroup.widgets.stream().filter(com.lowdragmc.lowdraglib.gui.widget.DialogWidget.class::isInstance).findFirst().orElseThrow();check("JEI picker contains rendered ingredient cells",hasIngredientCell(picker));((com.lowdragmc.lowdraglib.gui.widget.DialogWidget)picker).close();}
            case 23 -> {editor.loadProject(VjceEditor.projectForCategory("minecraft:anvil"));project=(VjceProject)editor.getCurrentProject();editor.getTabPages().switchTag(project.layoutView);}
            case 24 -> {screenshot("anvil-page");anvilChecks();}
            case 25 -> {screenshot("anvil-edited-page");if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ae2")){editor.loadProject(VjceEditor.projectForCategory("ae2:condenser"));project=(VjceProject)editor.getCurrentProject();}else finish(null);}
            case 26 -> {var slot=project.layoutView.getCurrentUI().rootElement.select("*").filter(RecipeUI::isSlot).findFirst().orElseThrow();slot.x+=4;project.captureLayout();var saved=project.serializeNBT();var restored=new VjceProject();restored.deserializeNBT(saved);editor.loadProject(restored);project=restored;}
            case 27 -> {screenshot("ae2-condenser-edited");condenserAndTagsChecks();finish(null);}
            case 28 -> {searchChecks();IngredientPicker.open(project.layoutView,typed->{});var dialog=pickerDialog();dialog.search.setFocus(true);for(char c:bannerQuery().toCharArray())dialog.search.charTyped(c,0);check("typing localized query returns banners: "+dialog.values.size(),dialog.values.size()>=16&&dialog.values.size()<100);pickerScale=mc.options.guiScale().get();}
            case 29 -> {screenshot("picker-banner");pickerPixelChecks();var grid=pickerDialog().results;for(int i=0;i<15;i++)mc.screen.mouseScrolled(grid.getPosition().x+20,grid.getPosition().y+20,-1);check("picker result grid scrolls",grid.getScrollYOffset()>0);}
            case 30 -> {screenshot("picker-banner-scrolled");pickerPixelChecks();pickerQuery("");click(pickerDialog().next);check("picker next page changes real ingredients",pickerDialog().page==1);}
            case 31 -> {screenshot("picker-next-page");pickerPixelChecks();click(pickerDialog().previous);check("picker previous page returns to first page",pickerDialog().page==0);pickerQuery(bannerQuery());mc.options.guiScale().set(3);mc.resizeDisplay();}
            case 32 -> {screenshot("picker-gui-scale-3");pickerPixelChecks();check("picker GUI resize retains query and bounds",pickerDialog().search.getCurrentString().equals(bannerQuery())&&pickerDialog().getSize().width==mc.getWindow().getGuiScaledWidth()&&pickerDialog().panel.getPosition().x>=0);mc.options.guiScale().set(pickerScale);mc.resizeDisplay();pickerDialog().close();}
            case 33 -> {IngredientPicker.open(project.layoutView,typed->{});pickerQuery(bannerQuery());}
            case 34 -> {pickerPixelChecks();screenshot("picker-reopened");pickerQuery("vjce_no_such_ingredient_6248");}
            case 35 -> {screenshot("picker-no-results");check("picker no-result feedback",pickerDialog().values.isEmpty()&&pickerDialog().status.getSize().width>40&&!pickerDialog().next.isActive());pickerQuery(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ae2")?"@applied":"@minecraft");}
            case 36 -> {screenshot("picker-mod-search");check("picker mod-name search returns ingredients",!pickerDialog().values.isEmpty());pickerPixelChecks();pickerDialog().close();editor.loadProject(VjceEditor.projectForCategory("minecraft:crafting"));project=(VjceProject)editor.getCurrentProject();}
            case 37 -> {screenshot("jei-reference");referenceChecks();}
            case 38 -> {screenshot("jei-reference-root-transformed");check("reference frame stays at page origin after root transforms",referenceSample()==referencePixel);project.layoutView.panX+=20;project.layoutView.panY+=15;project.layoutView.zoom=1.2f;}
            case 39 -> {screenshot("jei-reference-pan-zoom");check("reference follows canvas pan and zoom",referenceSample()==referencePixel);click(project.layoutView.toolbar.get(project.layoutView.toolbar.size()-1));}
            case 40 -> {screenshot("jei-reference-hidden");check("reference toggle removes frame pixels",referenceSample()!=referencePixel);check("reference toggle is absent from serialized layout",project.layoutView.getCurrentUI().toTemplate().serialize().equals(referenceLayout));project.layoutView.showReference=true;project.document.width+=20;project.document.height+=10;}
            case 41 -> {screenshot("jei-reference-resized-area");var view=project.layoutView;double scale=mc.getWindow().getGuiScale();int x=(int)((view.stage.getPosition().x+view.panX+(project.document.width-1)*view.zoom)*scale),y=(int)((view.stage.getPosition().y+view.panY+project.document.height*.5f*view.zoom)*scale);try(var frame=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){check("reference outline renders at resized recipe boundary",(frame.getPixelRGBA(x,y)&0xffffff)==0xae9e40);}finish(null);}
            case 42,43,44,45,46,47,48,49,50,51,52,53,54 -> {if(PanelValidation.step(phase-1,editor))finish(null);}
            case 55,56,57,58,59 -> {if(FormChoicesValidation.step(phase-1,editor))finish(null);}
        }
    }catch(Throwable failure){VisualJeiCategoryEditor.LOG.error("VJCE port validation failed at phase "+phase,failure);finish(failure);}}
    private static IngredientPicker.PickerDialog pickerDialog(){return (IngredientPicker.PickerDialog)editor.getGui().mainGroup.widgets.stream().filter(IngredientPicker.PickerDialog.class::isInstance).findFirst().orElseThrow();}
    private static void pickerQuery(String text){var picker=pickerDialog();picker.search.setCurrentString(text);picker.search(text);}
    private static String bannerQuery(){String a=net.minecraft.world.item.Items.WHITE_BANNER.getDescription().getString(),b=net.minecraft.world.item.Items.RED_BANNER.getDescription().getString();int n=0;while(n<Math.min(a.length(),b.length())&&a.charAt(a.length()-1-n)==b.charAt(b.length()-1-n))n++;return a.substring(a.length()-n).trim();}
    private static void click(com.lowdragmc.lowdraglib.gui.widget.Widget widget){var screen=Minecraft.getInstance().screen;screen.mouseClicked(widget.getPosition().x+2,widget.getPosition().y+2,0);screen.mouseReleased(widget.getPosition().x+2,widget.getPosition().y+2,0);}
    private static void pickerPixelChecks()throws Exception{
        var picker=pickerDialog();var buttons=List.of(picker.previous,picker.next,picker.close);
        try(var frame=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){
            for(int i=0;i<buttons.size();i++){var b=buttons.get(i);check("picker navigation text remains visible "+i,brightPixels(frame,b.getPosition().x+4,b.getPosition().y+3,b.getSize().width-8,b.getSize().height-6)>8);}
            int visible=0;for(var widget:picker.results.widgets)if(widget instanceof IngredientPicker.IngredientCell cell){var p=cell.getPosition();if(p.y<picker.results.getPosition().y||p.y+36>picker.results.getPosition().y+picker.results.getSize().height)continue;
                check("picker visible icon: "+ingredientKey(cell.ingredient),differentPixels(frame,p.x+(cell.getSize().width-16)/2,p.y+2,16,16,0xff3c3c3c)>6);
                check("picker visible name: "+ingredientKey(cell.ingredient),brightPixels(frame,p.x+3,p.y+20,cell.getSize().width-6,12)>8);visible++;
            }check("picker exercises visible ingredient rows",visible>=3);
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static String ingredientKey(mezz.jei.api.ingredients.ITypedIngredient<?> value){return value.getType().getUid()+":"+((mezz.jei.api.ingredients.IIngredientHelper)IngredientAccess.manager.getIngredientHelper(value.getType())).getUniqueId(value.getIngredient(),mezz.jei.api.ingredients.subtypes.UidContext.Ingredient);}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void searchChecks(){
        var api=VjceJeiPlugin.runtime.getIngredientFilter();String initial=api.getFilterText();
        try{for(String query:List.of("","@minecraft","@applied","@ae2","@minecraft -stone","@minecraft stone|@ae2","-@minecraft","\"iron ingot\"","#energy","$minecraft:logs","%building","^red","&minecraft:stone")){
            api.setFilterText("@minecraft");var actual=JeiIngredientSearch.query(query);check("independent search preserves JEI text: "+query,api.getFilterText().equals("@minecraft"));api.setFilterText(query);
            for(var type:IngredientAccess.manager.getRegisteredIngredientTypes()){
                var expected=((List<Object>)api.getFilteredIngredients((mezz.jei.api.ingredients.IIngredientType)type)).stream().map(v->ingredientKey((mezz.jei.api.ingredients.ITypedIngredient<?>)IngredientAccess.manager.createTypedIngredient((mezz.jei.api.ingredients.IIngredientType)type,v).orElseThrow())).toList();
                var found=actual.stream().filter(v->v.getType()==type).map(PortValidation::ingredientKey).toList();check("picker matches native JEI query "+query+" / "+type.getUid(),found.equals(expected));
            }
        }}finally{api.setFilterText(initial);}
    }
    private static int differentPixels(com.mojang.blaze3d.platform.NativeImage image,int x,int y,int w,int h,int background){
        double scale=Minecraft.getInstance().getWindow().getGuiScale();int count=0;
        for(int py=Math.max(0,(int)(y*scale));py<Math.min(image.getHeight(),(y+h)*scale);py++)for(int px=Math.max(0,(int)(x*scale));px<Math.min(image.getWidth(),(x+w)*scale);px++){int c=image.getPixelRGBA(px,py);if(Math.abs((c&255)-((background>>16)&255))>12||Math.abs(((c>>8)&255)-((background>>8)&255))>12||Math.abs(((c>>16)&255)-(background&255))>12)count++;}return count;
    }
    private static void referenceChecks()throws Exception{
        var view=project.layoutView;var root=view.getCurrentUI().rootElement;check("JEI reference defaults on",view.showReference);long count=root.select("*").count();String before=view.getCurrentUI().toTemplate().serialize();
        view.showReference=false;check("reference visibility does not alter canvas data",before.equals(view.getCurrentUI().toTemplate().serialize())&&root.select("*").count()==count);view.showReference=true;referencePixel=referenceSample();
        root.x+=8;root.y+=5;root.rotation=10;root.scaleX=.8f;root.scaleY=.9f;referenceLayout=view.getCurrentUI().toTemplate().serialize();
        var point=view.stage.point(view.stage.getPosition().x+view.panX,view.stage.getPosition().y+view.panY);check("reference and canvas share exact recipe origin",point.length()<.001f);
    }
    private static int referenceSample()throws Exception{
        var view=project.layoutView;var b=JeiPageReference.bounds(project.document);double scale=Minecraft.getInstance().getWindow().getGuiScale();int x=(int)((view.stage.getPosition().x+view.panX+(b.x()+8)*view.zoom)*scale),y=(int)((view.stage.getPosition().y+view.panY+(b.y()+40)*view.zoom)*scale);
        try(var frame=net.minecraft.client.Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){return frame.getPixelRGBA(x,y);}
    }
    private static int brightPixels(com.mojang.blaze3d.platform.NativeImage image,int x,int y,int w,int h){
        double scale=Minecraft.getInstance().getWindow().getGuiScale();int bright=0;
        for(int py=Math.max(0,(int)(y*scale));py<Math.min(image.getHeight(),(y+h)*scale);py++)for(int px=Math.max(0,(int)(x*scale));px<Math.min(image.getWidth(),(x+w)*scale);px++){int c=image.getPixelRGBA(px,py);if((c&255)>180&&((c>>8)&255)>180&&((c>>16)&255)>180)bright++;}
        return bright;
    }
    private static void anvilChecks(){
        var root=project.layoutView.getCurrentUI().rootElement;
        var page=NativePage.context(root);
        check("anvil library page contains editable text",page!=null&&!page.parts.isEmpty());
        var part=page.parts.values().stream().filter(p->p.key().contains("container_repair_cost")).findFirst().orElseThrow();
        var element=NativePage.part(root,part.key());
        check("anvil experience cost is captured before template creation",element!=null);
        var source=(com.pureneko.vjce.mixin.TextWidgetAccess)part.widget();
        var sourceColor=JeiTextColor.get((mezz.jei.common.gui.elements.TextWidget)part.widget());
        element.replacementText="VJCE anvil";
        var replacement=(mezz.jei.common.gui.elements.TextWidget)page.widget(part,element);
        var text=(com.pureneko.vjce.mixin.TextWidgetAccess)replacement;
        check("anvil replacement text retains source styling",text.vjce$text().get(0).getString().equals(element.replacementText)
                &&text.vjce$font()==source.vjce$font()&&text.vjce$shadow()==source.vjce$shadow()
                &&text.vjce$spacing()==source.vjce$spacing()&&text.vjce$horizontal()==source.vjce$horizontal()
                &&text.vjce$vertical()==source.vjce$vertical()&&Objects.equals(JeiTextColor.get(replacement),sourceColor));
        element.overrideTextColor=true;element.textColor=0xff20ff60;
        check("anvil replacement applies custom color",JeiTextColor.get((mezz.jei.common.gui.elements.TextWidget)page.widget(part,element))==element.textColor);
        var themed=new mezz.jei.common.gui.elements.TextWidget(List.of(net.minecraft.network.chat.Component.literal("theme")),0,0,80,12);
        var themedPart=new NativePage.Part("theme",themed,0,0,80,12,"theme");
        var edit=new NativePartElement();edit.replacementText="themed replacement";
        check("text replacement preserves JEI default color behavior",Objects.equals(JeiTextColor.get(themed),JeiTextColor.get((mezz.jei.common.gui.elements.TextWidget)page.widget(themedPart,edit))));
        var saved=project.layoutView.getCurrentUI().toTemplate().createUI();
        var restored=NativePage.part(saved.rootElement,part.key());
        check("anvil text edit survives layout serialization",restored!=null&&restored.replacementText.equals(element.replacementText)&&restored.overrideTextColor&&restored.textColor==element.textColor);
        saved.rootElement.dispose();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void condenserAndTagsChecks(){
        var manager=VjceJeiPlugin.runtime.getRecipeManager();var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var original=(mezz.jei.api.recipe.category.IRecipeCategory)VjceJeiPlugin.originals.get("ae2:condenser");
        var raw=manager.createRecipeLookup(original.getRecipeType()).get().findFirst().orElseThrow();
        var adapted=(mezz.jei.api.gui.IRecipeLayoutDrawable<?>)manager.createRecipeLayoutDrawable(new CapturedCategory(original,project.document),raw,focus).orElseThrow();
        drawJeiLayout(adapted);
        check("edited AE2 condenser renders in JEI after project reload",adapted.getRecipeSlotsView().getSlotViews().size()>=2);
        for(var category:VjceJeiPlugin.originals.values())if(category.getClass().getName().contains(".tags.")){
            var tagCategory=(mezz.jei.api.recipe.category.IRecipeCategory)category;
            var sample=manager.createRecipeLookup(tagCategory.getRecipeType()).get().findFirst();if(sample.isEmpty())continue;
            var layout=(mezz.jei.api.gui.IRecipeLayoutDrawable<?>)manager.createRecipeLayoutDrawable(tagCategory,sample.get(),focus).orElseThrow();drawJeiLayout(layout);
            check("JEI tag text renders after condenser edit: "+category.getRecipeType().getUid(),((com.pureneko.vjce.mixin.RecipeLayoutAccess)layout).vjce$widgets().stream().anyMatch(mezz.jei.api.gui.widgets.ITextWidget.class::isInstance));
        }
    }
    private static void drawJeiLayout(mezz.jei.api.gui.IRecipeLayoutDrawable<?> layout){
        var mc=Minecraft.getInstance();var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
        graphics.pose().pushPose();try(var state=new SceneRenderState()){graphics.pose().translate(-4096,-4096,0);layout.setPosition(0,0);layout.tick();layout.drawRecipe(graphics,-100,-100);graphics.flush();}finally{graphics.pose().popPose();}
    }
    private static boolean hasIngredientCell(com.lowdragmc.lowdraglib.gui.widget.Widget widget){if(widget instanceof IngredientPicker.IngredientCell cell)return cell.ingredient!=null;if(widget instanceof com.lowdragmc.lowdraglib.gui.widget.WidgetGroup group)for(var child:group.widgets)if(hasIngredientCell(child))return true;return false;}
    private static void eightRevisionChecks() throws Exception {
        var view=project.layoutView;
        int width=view.stage.getSize().width;view.sidebar.setCollapsed(true);view.sidebar.advanceAnimation(System.nanoTime()+200_000_000L);int middle=view.stage.getSize().width;
        check("sidebar slides through intermediate widths",middle>width&&view.sidebar.occupied()>16);view.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);check("sidebar collapse finishes at reachable header",view.sidebar.occupied()==16);view.sidebar.setCollapsed(false);view.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
        var draft=new SceneEditingView(view,null);editor.getTabPages().addTab("vjce.scene.configure",draft);draft.preview.cameraYaw=73;draft.sidebar.setCollapsed(true);draft.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
        int w=editor.getSize().width,h=editor.getSize().height;editor.onScreenSizeUpdate(w-90,h-50);
        check("resize retains canvas and scene draft identity",project.layoutView==view&&draft.preview.cameraYaw==73&&editor.getTabPages().tabs.containsValue(draft));
        check("resize retains collapsed state and panel bounds",draft.sidebar.collapsed&&draft.sidebar.getPosition().x+draft.sidebar.getSize().width<=editor.contentWidth());
        editor.onScreenSizeUpdate(w,h);
        var mc=Minecraft.getInstance();int scale=mc.options.guiScale().get();mc.options.guiScale().set(3);mc.resizeDisplay();
        check("View GUI scale retains session and recalculates sidebar",project.layoutView==view&&draft.preview.cameraYaw==73&&view.getSize().width==editor.contentWidth()&&draft.sidebar.getPosition().x+draft.sidebar.getSize().width<=editor.contentWidth());
        mc.options.guiScale().set(scale);mc.resizeDisplay();draft.close();
        check("preview removed and optional Create toolbar",view.toolbar.size()==(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create")?13:12));
        childrenChecks();
        editor.loadProject(new VjceProject());project=(VjceProject)editor.getCurrentProject();project.dataView.addSlot("INPUT");project.dataView.addSlot("OUTPUT");project.dataView.addSlot("CATALYST");
        project.dataView.categoryMode=false;project.dataView.refresh();editor.getTabPages().switchTag(project.dataView);project.dataView.inspectSlot(0);
    }
    private static void childrenChecks(){
        var view=project.layoutView;var group=new CanvasElement("group");group.id="qa_children";var a=new CanvasElement("text");a.id="child_a";a.text="A";var b=new CanvasElement("text");b.id="child_b";b.text="B";group.addChildren(a,b);view.getCurrentUI().rootElement.addChild(group);view.markAsDirty();
        var config=new com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup("",false);CanvasConfigurator.build(config,group,view);config.init(248);editor.addWidget(config);
        var basic=(com.lowdragmc.lowdraglib.gui.editor.configurator.ConfiguratorGroup)config.getConfigurators().get(0);var children=(com.lowdragmc.lowdraglib.gui.editor.configurator.ArrayConfiguratorGroup<?>)basic.getConfigurators().get(3);children.setCollapse(false);config.computeHeight();
        var first=children.getConfigurators().get(0);var second=children.getConfigurators().get(1);var gui=editor.getGui().getModularUIGui();gui.setDraggingElement(first,com.lowdragmc.lowdraglib.gui.texture.IGuiTexture.EMPTY);second.mouseReleased(second.getPosition().x+1,second.getPosition().y+second.getSize().height-1,0);gui.mouseReleased(-100,-100,0);
        check("children drag reorders canvas drawing order",group.getChildren().get(1)==a);
        var remove=children.getConfigurators().get(0).widgets.stream().filter(com.lowdragmc.lowdraglib.gui.widget.ButtonWidget.class::isInstance).findFirst().orElseThrow();remove.mouseClicked(remove.getPosition().x+2,remove.getPosition().y+2,0);children.updateScreen();
        check("children remove disposes selected child",group.getChildren().size()==1&&group.getChildren().get(0)==a);
        var after=editor.getCurrentHistory();int index=editor.getHistory().indexOf(after);editor.removeWidget(config);editor.jumpToHistory(editor.getHistory().get(index-1));
        check("children delete can be undone",project.layoutView.getCurrentUI().rootElement.selectId("qa_children").findFirst().orElseThrow().getChildren().size()==2);editor.jumpToHistory(after);
        check("children delete can be redone",project.layoutView.getCurrentUI().rootElement.selectId("qa_children").findFirst().orElseThrow().getChildren().size()==1);
    }
    private static void checkEditorTextPixels() throws Exception {
        var mc=Minecraft.getInstance();var button=project.layoutView.toolbar.get(0);var p=button.getPosition();double scale=mc.getWindow().getGuiScale();int bright=0;
        try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            for(int y=(int)((p.y+3)*scale);y<(p.y+button.getSize().height-3)*scale;y++)for(int x=(int)((p.x+5)*scale);x<(p.x+button.getSize().width-5)*scale;x++){int c=image.getPixelRGBA(x,y);if((c&255)>180&&((c>>8)&255)>180&&((c>>16)&255)>180)bright++;}
        }
        check("AE2 page preserves toolbar text pixels",bright>8);
    }
    private static void editorChecks()throws Exception{
        opacityChecks();
        var view=project.layoutView;project.dataView.addSlot("INPUT");project.dataView.addSlot("OUTPUT");check("slot controls create real canvas elements",view.getCurrentUI().rootElement.select("*").filter(IngredientSlot.class::isInstance).count()==2);
        var first=view.getCurrentUI().rootElement.select("*").filter(IngredientSlot.class::isInstance).findFirst().orElseThrow();view.focusElement(first);float x=first.x,y=first.y;
        view.transforms.begin(new Vector2f(x+4,y+4),false,false);view.transforms.update(new Vector2f(x+24,y+17),false,false,true);view.transforms.finish();check("canvas drag commits placement",Math.abs(first.x-x-20)<.01&&Math.abs(first.y-y-13)<.01);
        view.transforms.begin(new Vector2f(first.x,first.y),false,false);view.transforms.update(new Vector2f(first.x+50,first.y),false,true);view.transforms.cancel();check("escape restores transform",Math.abs(first.x-x-20)<.01);
        first.rotation=30;var center=first.worldMatrix().transformPosition(new Vector3f(first.width/2,first.height/2,0));check("rotated element hit test",first.isMouseOverElement(center.x,center.y));
        var second=view.getCurrentUI().rootElement.select("*").filter(IngredientSlot.class::isInstance).filter(e->e!=first).findFirst().orElseThrow();var before=new Matrix4f(first.worldMatrix());view.selection.add(second);CanvasGrouping.group(view);check("group retains first world transform",matrixClose(before,first.worldMatrix()));CanvasGrouping.ungroup(view);check("ungroup retains first world transform",matrixClose(before,first.worldMatrix()));
        var group=new CanvasElement();group.x=20;group.y=30;group.rotation=25;group.scaleX=1.8f;group.scaleY=.7f;
        var child=new CanvasElement("text");child.width=50;child.rotation=35;group.addChild(child);view.getCurrentUI().rootElement.addChild(group);var affine=new Matrix4f(child.worldMatrix());view.focusElement(group);CanvasGrouping.ungroup(view);check("ungroup preserves rotated nonuniform affine transform",matrixClose(affine,child.worldMatrix()));child.detach();
        var texture=new TextureElement();texture.id="qa_texture";texture.x=100;texture.y=45;texture.setImage(com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture.BORDERED_BACKGROUND.copy());view.getCurrentUI().rootElement.addChild(texture);check("LDLib1 texture serialization retains nine slice",CanvasElement.deserialize(texture.serialize()) instanceof TextureElement t&&t.image.getClass()==texture.image.getClass());
        var textureResources=(com.lowdragmc.lowdraglib.gui.editor.data.resource.TexturesResource)project.getResources().resources.get(com.lowdragmc.lowdraglib.gui.editor.data.resource.TexturesResource.RESOURCE_NAME);
        textureResources.addBuiltinResource("qa_nested",new com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup(new com.lowdragmc.lowdraglib.gui.texture.UIResourceTexture(textureResources,com.mojang.datafixers.util.Either.left("slot")),new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(0x66ffffff)));
        var composite=new TextureElement();composite.setImage(new com.lowdragmc.lowdraglib.gui.texture.UIResourceTexture(textureResources,com.mojang.datafixers.util.Either.left("qa_nested")));composite.bind(textureResources);var baked=composite.serialize();check("nested LDLib resource baked for runtime",!baked.getCompound("ldlibTexture").toString().contains("ui_resource")&&CanvasElement.deserialize(baked) instanceof TextureElement t&&t.image instanceof com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup g&&g.textures[0] instanceof com.lowdragmc.lowdraglib.gui.texture.ResourceTexture);
        project.captureLayout();String saved=project.document.layout;var restored=CanvasTemplate.parse(saved).createUI();check("canvas serialization retains transforms",matrixClose(first.worldMatrix(),restored.rootElement.selectId(first.id).findFirst().orElseThrow().worldMatrix()));
        var temp=Files.createTempDirectory("vjce-port-validation");var store=new ProjectStore(temp);store.save(project.document);check("independent project round trip",store.loadAll().size()==1&&saved.equals(store.loadAll().get(0).layout));store.exportZip(temp.resolve("pack.zip"),ClientBootstrap.assets());try(var zip=new java.util.zip.ZipFile(temp.resolve("pack.zip").toFile())){check("modpack uses version-specific config path",zip.stream().anyMatch(e->e.getName().startsWith("config/vjce-1.20.1/categories/")));}
        var snapshot=project.serializeNBT();project.document.title="Changed";project.deserializeNBT(snapshot);editor.loadProject(project);check("LDLib project restores document",!project.document.title.equals("Changed"));
        var modal=new com.lowdragmc.lowdraglib.gui.widget.DialogWidget(editor,true);check("modal blocks canvas gestures",!project.layoutView.canvasInputAllowed());editor.removeWidget(modal);
        IngredientPicker.open(project.layoutView,typed->{});var picker=((ModularUIGuiContainer)Minecraft.getInstance().screen).modularUI.mainGroup.widgets.stream().filter(com.lowdragmc.lowdraglib.gui.widget.DialogWidget.class::isInstance).findFirst().orElseThrow();check("ingredient picker has usable full screen bounds",picker.getSize().width==editor.getSize().width&&picker.getSize().height==editor.getSize().height);((ModularUIGuiContainer)Minecraft.getInstance().screen).modularUI.mainGroup.removeWidget(picker);
        check("history captures edits",editor.getHistory().size()>1);
        var viewBefore=project.layoutView;String oldTitle=project.document.title;project.document.title="History marker";project.changed("vjce.category_data",thisSource());var current=editor.getCurrentHistory();int historyIndex=editor.getHistory().indexOf(current);editor.jumpToHistory(editor.getHistory().get(historyIndex-1));check("undo restores prior document",!project.document.title.equals("History marker"));editor.jumpToHistory(current);check("redo restores changed document",project.document.title.equals("History marker"));project.layoutView.focusElement(project.layoutView.getCurrentUI().rootElement.selectId("qa_texture").findFirst().orElseThrow());
    }
    private static void checkForgeProjectFile() throws Exception {
        var legacy = new VjceProject(ProjectStore.copy(project.document));
        var water = new CategoryDocument.Ingredient();
        water.fluid = "minecraft:water";
        water.type = "fluid_stack";
        water.amount = 750;
        var encodedWater = new com.google.gson.JsonObject();
        encodedWater.addProperty("nbt", "{FluidName:\"minecraft:water\",Amount:750,Tag:{qa:1b}}");
        water.data = encodedWater;
        var slot = new CategoryDocument.Slot();
        slot.name = "water";
        slot.role = "INPUT";
        slot.ingredients.add(water);
        legacy.selected.slots.add(slot);
        var root = legacy.serializeNBT();
        var dir = output();
        Files.createDirectories(dir);
        var fixture = dir.resolve("forge-format.vjce1201").toFile();
        NbtIo.write(root, fixture);
        var imported = (VjceProject) new VjceProject().loadProject(fixture);
        check("Forge .vjce1201 preserves the layout and selected recipe",
                imported.document.layout.equals(legacy.document.layout) && imported.selected.id.equals(legacy.selected.id));
        var decoded = IngredientAccess.decode(imported.selected.slots.get(imported.selected.slots.size() - 1)).get(0);
        var encoded = IngredientAccess.encode(decoded);
        check("Forge .vjce1201 preserves 750 mB of water and fluid NBT",
                encoded.amount == 750 && encoded.fluid.equals("minecraft:water") && encoded.data.getAsJsonObject().get("nbt").getAsString().contains("qa:1b"));
        var saved = dir.resolve("vjce-smoke.vjce1201").toFile();
        imported.saveProject(saved);
        var reopened = (VjceProject) new VjceProject().loadProject(saved);
        check("saved .vjce1201 reopens", reopened.document.id.equals(imported.document.id));
    }

    private static void revisionCanvasChecks() throws Exception {
        var view=project.layoutView;var e=new CanvasElement("text");e.id="qa_gestures";e.text="Gesture QA";e.x=30;e.y=20;e.width=60;e.height=20;view.getCurrentUI().rootElement.addChild(e);view.focusElement(e);
        var p=view.transforms.screen(new Vector2f(e.x+20,e.y+10));view.stage.click(p.x,p.y,0,false,false,false);view.stage.mouseDragged(p.x+20,p.y+10,0,20,10);view.stage.mouseReleased(p.x+20,p.y+10,0);check("plain left drag only selects",e.x==30&&e.y==20&&!view.transforms.isEditing());
        view.stage.click(p.x,p.y,0,false,true,false);view.transforms.update(new Vector2f(e.x+40,e.y+20),false,false,true);view.stage.mouseReleased(p.x+20,p.y+10,0);check("shift left drag moves",Math.abs(e.x-50)<.01&&Math.abs(e.y-30)<.01);
        p=view.transforms.screen(new Vector2f(e.x+20,e.y+10));view.stage.click(p.x,p.y,0,true,false,false);view.stage.mouseReleased(p.x,p.y,0);check("ctrl click enters persistent green mode",view.transforms.isEditing());
        var anchor=e.worldMatrix().transformPosition(new Vector3f(0,0,0));var corner=view.transforms.handlePoint(e,1,1);view.stage.click(corner.x,corner.y,0,false,false,false);var destination=view.stage.point(corner.x+20*view.zoom,corner.y+10*view.zoom);view.transforms.update(destination,false,false,true);view.stage.mouseReleased(corner.x,corner.y,0);check("green handles resize layout and retain scale",e.width==80&&e.height==30&&e.scaleX==1&&e.scaleY==1);check("resize retains opposite anchor",anchor.distance(e.worldMatrix().transformPosition(new Vector3f()))<.01);
        float w=e.width,h=e.height;corner=view.transforms.handlePoint(e,1,1);view.stage.click(corner.x,corner.y,0,false,false,false);view.transforms.update(view.stage.point(corner.x+40*view.zoom,corner.y),false,true,true);check("ctrl corner preserves aspect ratio",Math.abs(e.width/e.height-w/h)<.001);view.escapeCanvasTransform();check("escape restores resize and retains green mode",e.width==w&&e.height==h&&view.transforms.isEditing());view.escapeCanvasTransform();check("second escape exits green mode",!view.transforms.isEditing());
        e.rotation=30;e.scaleX=3;e.scaleY=2;view.focusElement(e);p=view.transforms.screen(new Vector2f(e.x+e.width/2,e.y+e.height/2));view.stage.click(p.x,p.y,0,true,false,false);view.stage.mouseReleased(p.x,p.y,0);check("scaled rotated control points remain selectable",view.transforms.beginHandle(view.stage.point(view.transforms.handlePoint(e,1,0).x,view.transforms.handlePoint(e,1,0).y),false));view.transforms.cancel();
        int old=view.stage.getSize().width;view.sidebar.setCollapsed(true);view.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);check("layout sidebar collapses",view.stage.getSize().width>old);screenshot("canvas-sidebar-collapsed");view.sidebar.setCollapsed(false);view.sidebar.advanceAnimation(System.nanoTime()+600_000_000L);
        var lang=net.minecraft.locale.Language.getInstance();overlayPixelChecks(view);
        for(String field:List.of("part","sourceLabel","replacementText","replacementTexture","useSourceDrawing","overrideTextColor","textColor","sourceX","sourceY","sourceWidth","sourceHeight","sourceIndex"))check("translated dynamic property "+field,lang.has("vjce.field."+field));
    }
    private static void overlayPixelChecks(RecipeLayoutView view) throws Exception {
        var mc=Minecraft.getInstance();var element=new CanvasElement("group");element.width=20;element.height=20;element.x=80;element.y=60;
        float oldZoom=view.zoom;var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());var stagePos=view.stage.getPosition();
        try(var state=new SceneRenderState()){
            mc.getMainRenderTarget().bindWrite(true);com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,-1000,1000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            int[] thickness=new int[2];
            for(int i=0;i<2;i++){
                element.scaleX=element.scaleY=i==0?1:4;view.zoom=i==0?1:2;graphics.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff000000);view.transforms.outline(graphics,element,0xff55ff55);graphics.flush();var a=view.transforms.handlePoint(element,-1,-1);var b=view.transforms.handlePoint(element,1,-1);double scale=mc.getWindow().getGuiScale();
                try(var frame=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int x=(int)((a.x+b.x)/2*scale),y=(int)(a.y*scale);for(int dy=-12;dy<=12;dy++){int pixel=frame.getPixelRGBA(x,y+dy);if((pixel>>8&255)>200&&(pixel&255)<150)thickness[i]++;}}
            }
            check("outline pixel thickness ignores element and canvas scale",thickness[0]>0&&thickness[0]==thickness[1]);
            view.zoom=oldZoom;graphics.fill(0,0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0xff8a2255);graphics.flush();view.stage.drawInBackground(graphics,-100,-100,0);graphics.flush();
            try(var frame=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int color=frame.getPixelRGBA((int)((stagePos.x+view.stage.getSize().width-5)*mc.getWindow().getGuiScale()),(int)((stagePos.y+view.stage.getSize().height-5)*mc.getWindow().getGuiScale()));check("layout viewport leaves underlying background visible",(color&0xffffff)==0x55228a);}
        }finally{view.zoom=oldZoom;}
    }
    private static Object thisSource(){return PortValidation.class;}
    private static void opacityChecks()throws Exception{
        var mc=Minecraft.getInstance();try(var state=new SceneRenderState()){
        mc.getMainRenderTarget().bindWrite(true);com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();com.mojang.blaze3d.systems.RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),0,-1000,1000),com.mojang.blaze3d.vertex.VertexSorting.ORTHOGRAPHIC_Z);com.mojang.blaze3d.systems.RenderSystem.getModelViewStack().setIdentity();com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());graphics.fill(10,10,45,45,0xff000000);
        var element=new CanvasElement();element.x=10;element.y=10;element.width=35;element.height=35;element.background=0xffff0000;element.opacity=.5f;element.render(graphics,-1,-1,0);graphics.flush();
        try(var frame=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){int pixel=frame.getPixelRGBA((int)(20*mc.getWindow().getGuiScale()),(int)(20*mc.getWindow().getGuiScale()));int red=pixel&255,green=pixel>>8&255;frame.writeToFile(output().resolve("opacity-pixels.png"));VisualJeiCategoryEditor.LOG.info("Opacity sample R={} G={} hex={}",red,green,Integer.toHexString(pixel));check("canvas opacity composites actual pixels",red>=115&&red<=140&&green<10);}}
    }
    private static void sceneInteractionChecks(){
        var preview=sceneView.preview;preview.ensureScene();check("scene rendered without errors: "+preview.error,preview.error.isBlank());var projected=preview.renderer.viewProjection.transformProject(new Vector3f(.5f,.5f,.5f));double x=(projected.x+1)*sceneView.viewport.getSize().width/2,y=(1-projected.y)*sceneView.viewport.getSize().height/2;sceneView.viewport.pick(x,y);check("scene ray picks rendered block",sceneView.selectedRow()!=null);var before=sceneView.selectedRow().copy();sceneView.viewport.mouseMoved(sceneView.viewport.getPosition().x+x,sceneView.viewport.getPosition().y+y);sceneView.viewport.key(71);sceneView.viewport.key(88);sceneView.viewport.mouseMoved(sceneView.viewport.getPosition().x+x+80,sceneView.viewport.getPosition().y+y);check("scene move changes working data",!before.equals(sceneView.selectedRow()));sceneView.viewport.cancel();check("scene escape restores working data",before.equals(sceneView.selectedRow()));
    }
    private static void sceneInputChecks(){
        var viewport=sceneView.viewport;var preview=sceneView.preview;float yaw=preview.cameraYaw,pitch=preview.cameraPitch;
        var mc=Minecraft.getInstance();var graphics=new net.minecraft.client.gui.GuiGraphics(mc,mc.renderBuffers().bufferSource());
        for(int[] drag:new int[][]{{10,0},{-10,0},{0,10},{0,-10}}){
            preview.cameraYaw=yaw;preview.cameraPitch=pitch;
            viewport.drawInBackground(graphics,-100,-100,0);graphics.flush();
            var before=new Vector3f(preview.renderer.getEyePos()).sub(preview.renderer.getLookAt());
            viewport.mouseClicked(viewport.getPosition().x+10,viewport.getPosition().y+10,1);
            viewport.mouseDragged(0,0,1,drag[0],drag[1]);viewport.mouseReleased(0,0,1);
            viewport.drawInBackground(graphics,-100,-100,0);graphics.flush();
            var after=new Vector3f(preview.renderer.getEyePos()).sub(preview.renderer.getLookAt());
            double horizontal=Math.atan2(before.x*after.z-before.z*after.x,before.x*after.x+before.z*after.z);
            double vertical=Math.atan2(after.y,Math.hypot(after.x,after.z))-Math.atan2(before.y,Math.hypot(before.x,before.z));
            check("rendered scene camera follows RMB "+(drag[0]>0?"right":drag[0]<0?"left":drag[1]>0?"down":"up")+" without crossing axes",
                    drag[0]!=0?horizontal*drag[0]<0&&Math.abs(vertical)<.0001:vertical*drag[1]<0&&Math.abs(horizontal)<.0001);
        }
        preview.cameraYaw=yaw;preview.cameraPitch=pitch;
        var row=sceneView.form.widgets.stream().filter(com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator.class::isInstance).map(com.lowdragmc.lowdraglib.gui.editor.configurator.NumberConfigurator.class::cast).filter(r->r.getName().equals("vjce.field.cameraRange")).findFirst().orElseThrow();
        var input=row.widgets.stream().filter(com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget.class::isInstance).map(com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget.class::cast).findFirst().orElseThrow();float range=preview.cameraRange;
        row.mouseWheelMove(input.getPosition().x+2,input.getPosition().y+2,1);check("scene numeric parameter accepts hover wheel",preview.cameraRange>range);
    }
    private static void prepareWorldCapture(){
        var mc=Minecraft.getInstance();capturePos=mc.player.blockPosition().offset(2,2,0);var server=mc.getSingleplayerServer();server.execute(()->{var level=server.overworld();level.setBlockAndUpdate(capturePos,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());if(level.getBlockEntity(capturePos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest){var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3);item.setHoverName(net.minecraft.network.chat.Component.literal("Snapshot NBT"));chest.setItem(0,item);chest.setChanged();level.sendBlockUpdated(capturePos,level.getBlockState(capturePos),level.getBlockState(capturePos),3);}});
    }
    private static void worldCaptureChecks(){
        var level=Minecraft.getInstance().level;var selection=WorldSceneCapture.Selection.of(capturePos,capturePos);var snapshot=WorldSceneCapture.capture(level,selection,false,new CompoundTag());check("world capture copies block entity state",BlockSceneElement.rows(snapshot,"blocks").getCompound(0).getCompound("state").getString("Name").equals("minecraft:chest"));var scene=new BlockSceneElement();scene.blocks=snapshot;scene.ensureScene();check("captured scene owns isolated world",scene.error.isBlank()&&(Level)scene.dummyWorld!=level&&scene.dummyWorld.getBlockEntity(BlockPos.ZERO)!=null);scene.release();check("world capture leaves source block intact",level.getBlockState(capturePos).is(net.minecraft.world.level.block.Blocks.CHEST));
    }
    private static void sceneChecks(){var scene=new BlockSceneElement();scene.width=90;scene.height=90;scene.x=40;scene.y=10;project.layoutView.getCurrentUI().rootElement.addChild(scene);var water=new CompoundTag();water.putInt("x",1);var waterState=new CompoundTag();waterState.putString("Name","minecraft:water");water.put("state",waterState);BlockSceneElement.rows(scene.blocks,"blocks").add(water);scene.ensureScene();check("block entity snapshot builds in LDLib1 world",scene.error.isBlank()&&scene.dummyWorld.getBlockEntity(BlockPos.ZERO)!=null);var copy=CanvasElement.deserialize(scene.serialize());check("scene data survives canvas save",copy instanceof BlockSceneElement s&&s.blocks.equals(scene.blocks));scene.clock.playing=true;}
    private static void renderedSceneChecks(){for(var element:project.layoutView.getCurrentUI().rootElement.select("*").toList())if(element instanceof BlockSceneElement scene)check("scene preview remains rendered without error: "+scene.error,scene.error.isBlank()&&scene.renderer!=null&&scene.renderer.viewProjection!=null);}
    private static void entityChecks(){var scene=BlockSceneElement.entityScene();scene.x=40;scene.y=10;scene.width=90;scene.height=90;project.layoutView.getCurrentUI().rootElement.getChildren().stream().filter(BlockSceneElement.class::isInstance).toList().forEach(e->{e.detach();e.dispose();});project.layoutView.getCurrentUI().rootElement.addChild(scene);scene.ensureScene();check("entity scene creates isolated entities",scene.error.isBlank()&&scene.sceneEntities().size()==1&&scene.sceneEntities().get(0).level()!=Minecraft.getInstance().level);scene.clock.playing=true;scene.screenTick();check("entity idle tick remains healthy",scene.error.isBlank());}
    @SuppressWarnings({"rawtypes","unchecked"}) private static void nativeChecks(){var category=(mezz.jei.api.recipe.category.IRecipeCategory)VjceJeiPlugin.originals.get("minecraft:crafting");check("JEI15 vanilla category registered",category!=null);var raw=VjceJeiPlugin.runtime.getRecipeManager().createRecipeLookup(category.getRecipeType()).get().findFirst().orElseThrow();var record=CapturedCategory.capture(category,raw);check("native recipe import retains ingredients",record.slots.size()>=2);var doc=new CategoryDocument();doc.id="vjce:qa_crafting";doc.target="minecraft:crafting";doc.width=category.getWidth();doc.height=category.getHeight();doc.recipes.add(record);editor.loadProject(new VjceProject(doc));project=(VjceProject)editor.getCurrentProject();check("native canvas has source drawing",project.layoutView.getCurrentUI().rootElement.selectId("native_layer").findAny().isPresent());}
    private static void resourceChecks(){var block=SceneBlockImports.snapshot(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST),Minecraft.getInstance().level.registryAccess());check("inventory block import uses NBT format",block.contains("state")&&block.contains("data")&&!block.getString("assetId").isBlank());var key=com.mojang.datafixers.util.Either.<String,java.io.File>left("qa_chest");SceneAssetResource.BLOCKS.addResource(key,block);var row=SceneAssets.instance(new SceneAssets.Drop(SceneAssetResource.BLOCKS,key));var data=new CompoundTag();var list=new ListTag();list.add(row);data.put("blocks",list);check("resource identity resolves snapshot",BlockSceneElement.rows(SceneAssets.resolve(data),"blocks").getCompound(0).getCompound("state").getString("Name").equals("minecraft:chest"));var tag=new CompoundTag();SceneAssetResource.BLOCKS.getBuiltinResources().forEach((k,v)->tag.put(k,v));}
    private static boolean matrixClose(Matrix4f a,Matrix4f b){return a.equals(b,.001f);}
    static void check(String name,boolean passed){results.add(Map.of("name",name,"passed",passed));if(!passed)throw new AssertionError(name);VisualJeiCategoryEditor.LOG.info("VJCE QA PASS {}",name);}
    private static Path output(){return Path.of(System.getProperty("vjce.uitest.out","build/ui-test"));}
    static void screenshot(String name)throws Exception{var mc=Minecraft.getInstance();Files.createDirectories(output());try(var image=net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output().resolve(name+".png"));}}
    private static void finish(Throwable failure){done=true;try{Files.createDirectories(output());var report=new LinkedHashMap<String,Object>();report.put("minecraft","1.20.1");report.put("fabricloader",net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("fabricloader").orElseThrow().getMetadata().getVersion().getFriendlyString());for(String id:List.of("jei","ldlib"))report.put(id,net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id).orElseThrow().getMetadata().getVersion().getFriendlyString());report.put("passed",failure==null);report.put("checks",results);report.put("durationMs",System.currentTimeMillis()-started);report.put("create",net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("create"));report.put("mekanism",net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("mekanism"));report.put("ae2",net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("ae2"));if(failure!=null)report.put("error",failure.toString());Files.writeString(output().resolve("report.json"),ProjectStore.JSON.toJson(report));Files.writeString(output().resolve("exit-code.txt"),failure==null?"0":"1");}catch(Exception e){VisualJeiCategoryEditor.LOG.error("Cannot save validation report",e);}Minecraft.getInstance().stop();}
}
