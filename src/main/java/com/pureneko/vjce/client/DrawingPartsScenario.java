package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.integration.*;
import com.pureneko.vjce.data.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtOps;
import java.util.*;

@LDLRegisterClient(name="vjce_drawing",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class DrawingPartsScenario implements UIScenario {
    @Override @SuppressWarnings({"rawtypes","unchecked"})public void define(ScenarioBuilder s) {
        open(s,"vjce_qa_a:shared");
        s.waitUntil("generic drawing fragments discovered",c->parts(c).stream().filter(e->e.part.startsWith("draw_")).count()>=2)
        .check("fill and text are individual elements",c->parts(c).stream().anyMatch(e->e.part.startsWith("draw_fill_"))&&parts(c).stream().anyMatch(e->e.part.startsWith("draw_text_")))
        .check("custom JEI widget drawing also captured",c->parts(c).stream().anyMatch(e->e.part.startsWith("draw_fill_")&&e.sourceX>=100))
        .step("hover a captured fragment without a styled tooltip", c -> {
            var fragment = parts(c).stream().filter(e -> e.part.startsWith("draw_fill_") && e.sourceX >= 100).findFirst().orElseThrow();
            var point = ElementBounds.of(fragment).center();
            c.input().moveTo(point.x, point.y);
            var tooltip = fragment.collectHoverTooltips();
            c.check("the editor supplies diagnostics without a base tooltip", tooltip != null && tooltip.tooltipTexts().stream()
                    .anyMatch(text -> text.getString().equals(fragment.sourceLabel)));
        })
        .step("runtime discovery stays local to a recipe layout", DrawingPartsScenario::runtimeDiscovery)
        .screenshot("generic_parts_original")
        .step("group source fragments",c->{var view=p(c).layoutView;view.hierarchy.treeList.expandAllNodesIf(view.hierarchy.treeList.getRoot(),n->true);var selected=view.hierarchy.treeList.getNodeUIs().keySet().stream().filter(n->n.getKey() instanceof NativePartElement e&&e.part.startsWith("draw_")).toList();view.hierarchy.treeList.setSelected(selected,true);CanvasGrouping.group(view);})
        .check("grouping creates a real parent",c->parts(c).stream().filter(e->e.part.startsWith("draw_")).allMatch(e->e.getParent().getId().startsWith("group_")))
        .step("move and rotate group",c->{var group=parts(c).stream().filter(e->e.part.startsWith("draw_")).findFirst().orElseThrow().getParent();group.layout(l->{l.left(15);l.top(8);});group.transform(t->t.rotation(10));parts(c).stream().filter(e->e.part.startsWith("draw_text_")).forEach(e->e.replacementText="Edited generic drawing");p(c).captureLayout();})
        .screenshot("generic_parts_grouped")
        .openScreen("saved generic drawing in actual JEI",DrawingPartsScenario::actual).screenshot("generic_parts_actual_jei").closeScreen()
        .step("delete captured fill",c->{parts(c).stream().filter(e->e.part.startsWith("draw_fill_")).forEach(UIElement::removeSelf);p(c).captureLayout();})
        .openScreen("deleted fragment in actual JEI",DrawingPartsScenario::actual).screenshot("generic_deleted_actual_jei").closeScreen();
        open(s,"mekanism:crushing");
        s.step("verify Mek component adapter when installed",c->{if(!c.<Boolean>get("mek"))return;var pieces=parts(c).stream().map(e->e.part).toList();c.check("Mek energy bar is independently editable",pieces.stream().anyMatch(id->id.contains("PowerBar")));c.check("Mek progress is independently editable",pieces.stream().anyMatch(id->id.contains("Progress")));c.check("Mek arrow is independently editable",pieces.stream().anyMatch(id->id.contains("Arrow")));})
        .step("transform Mek progress and energy bar",c->{if(!c.<Boolean>get("mek"))return;for(var e:parts(c))if(e.part.contains("Progress")||e.part.contains("PowerBar")){e.layout(l->{l.left(e.getLayoutX()+20);l.top(e.getLayoutY()+5);l.width(e.getSizeWidth()*1.25f);});e.transform(t->t.rotation(12));}p(c).captureLayout();})
        .screenshot("mekanism_functional_components")
        .step("hover moved Mek energy bar",c->{if(!c.<Boolean>get("mek"))return;var bar=parts(c).stream().filter(e->e.part.contains("PowerBar")).findFirst().orElseThrow();c.put("bar",bar.part);var point=ElementBounds.of(bar).center();c.input().moveTo(point.x,point.y);})
        .check("moved Mek component keeps native tooltip state",c->{if(!c.<Boolean>get("mek"))return true;var drawing=NativePage.context(p(c).layoutView.getCurrentUI().rootElement).drawing;var key=c.<String>get("bar");return java.util.Objects.equals(MekElements.sourceTooltip(drawing.componentObject(key)),drawing.tooltips.get(key));})
        .screenshot("mekanism_component_tooltip").closeScreen();
        open(s,"create:pressing");
        s.waitUntil("Create animation discovered or absent",c->!c.<Boolean>get("create")||parts(c).stream().anyMatch(e->e.part.startsWith("draw_component_")))
        .step("verify Create component adapter",c->{if(!c.<Boolean>get("create"))return;c.check("Create direct textures captured",parts(c).stream().anyMatch(e->e.part.startsWith("draw_texture_")));c.check("Create mechanical animation is an element",parts(c).stream().anyMatch(e->e.sourceLabel.contains("AnimatedPress")));})
        .screenshot("create_imported_components").closeScreen();
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void runtimeDiscovery(TestContext context) {
        var project = p(context);
        var document = ProjectStore.copy(project.document);
        var data = document.recipes.getFirst();
        var original = (mezz.jei.api.recipe.category.IRecipeCategory) VjceJeiPlugin.originals.get(document.target);
        var manager = VjceJeiPlugin.runtime.getRecipeManager();
        Object raw = manager.createRecipeLookup(original.getRecipeType()).get().findFirst().orElseThrow();
        var focus = VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var template = RecipeUI.template(document, data);
        document.layout = UITemplate.CODEC.encodeStart(NbtOps.INSTANCE, template).getOrThrow().toString();
        var first = new NativeSession(original, raw, document, data, focus);
        String key = "draw_texture_qa_discovery";
        String label = "texture · QA source";
        try {
            first.page.drawing.component(new Object(), key, label, 2, 2, 8, 8, null);
            first.ui.tick();
            var fragment = NativePage.part(first.root(), key);
            context.check("first layout mounts its newly discovered fragment", fragment != null);
            context.check("runtime discovery never records a shared deletion marker", !document.nativeOriginElements.contains(key));
            context.check("unstyled runtime fragment has no tooltip", fragment.collectHoverTooltips() == null);
            fragment.style(style -> style.tooltips(label, "vjce.native.fragment.tip"));
            first.ui.init(document.width, document.height);
            context.check("legacy diagnostics alone allow parent tooltip lookup", fragment.collectHoverTooltips() == null);
            fragment.style(style -> style.tooltips(label, "vjce.native.fragment.tip", "QA custom tooltip"));
            first.ui.init(document.width, document.height);
            var tooltip = fragment.collectHoverTooltips().tooltipTexts().stream().map(Component::getString).toList();
            context.check("legacy editor diagnostics are filtered while custom tooltip survives", tooltip.equals(List.of("QA custom tooltip")));
            var second = new NativeSession(original, raw, document, data, focus);
            try {
                second.page.drawing.component(new Object(), key, label, 2, 2, 8, 8, null);
                second.ui.tick();
                context.check("later layout mounts the same fragment independently", NativePage.part(second.root(), key) != null);
            } finally { second.ui.onRemoved(); }
        } finally { first.ui.onRemoved(); }
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static Screen actual(TestContext c){
        var p=p(c);var doc=p.document;mezz.jei.api.recipe.category.IRecipeCategory original=VjceJeiPlugin.originals.get(doc.target);var manager=VjceJeiPlugin.runtime.getRecipeManager();Object raw=manager.createRecipeLookup(original.getRecipeType()).get().findFirst().orElseThrow();var focus=VjceJeiPlugin.helpers.getFocusFactory().createFocusGroup(List.of());
        var actual=(mezz.jei.api.gui.IRecipeLayoutDrawable)manager.createRecipeLayoutDrawable(new CapturedCategory(original,doc),raw,focus).orElseThrow();((com.pureneko.vjce.mixin.RecipeLayoutAccess)actual).vjce$createExtras();
        return new Screen(Component.literal("Generic drawing")){
            @Override public void tick(){actual.tick();}
            @Override public void render(GuiGraphics g,int x,int y,float partial){g.fill(0,0,width,height,0xff444444);actual.setPosition(100,90);actual.drawRecipe(g,0,0);g.drawString(font,"Saved generic fragments: grouped, moved, rotated, edited text",50,40,0xffffffff,false);}
        };
    }
    private static void open(ScenarioBuilder s,String id){s.waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI(id,c->{c.put("mek",net.neoforged.fml.ModList.get().isLoaded("mekanism"));c.put("create",net.neoforged.fml.ModList.get().isLoaded("create"));String target=VjceJeiPlugin.originals.containsKey(id)?id:"vjce_qa_a:shared";var p=VjceEditor.projectForCategory(target);var w=new EditorWindow(VjceEditor::new);w.getCurrentEditor().loadProject(p,null);c.put("p",p);return new ModularUI(UI.of(w),c.player());}).awaitModularUI();}
    private static VjceProject p(TestContext c){return c.get("p");}
    private static List<NativePartElement> parts(TestContext c){return p(c).layoutView.getCurrentUI().rootElement.select("*").filter(NativePartElement.class::isInstance).map(NativePartElement.class::cast).toList();}
}
