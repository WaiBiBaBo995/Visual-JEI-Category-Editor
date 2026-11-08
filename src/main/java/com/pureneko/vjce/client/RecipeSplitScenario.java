package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.integration.VjceJeiPlugin;
import org.joml.Vector2f;
import java.nio.file.*;

@LDLRegisterClient(name="vjce_split",group="vjce",registry=UIScenario.REGISTRY,environment=RegistrationEnvironment.DEV_ONLY)
public final class RecipeSplitScenario implements UIScenario {
    @Override public void define(ScenarioBuilder s){
        s.step("back up local editor settings",c->{try{var p=settings();c.put("settingsBackup",Files.exists(p)?Files.readAllBytes(p):null);}catch(Exception e){throw new IllegalStateException(e);}})
        .waitUntil("JEI ready",c->VjceJeiPlugin.runtime!=null).openModularUI("split recipe data",c->open(c)).awaitModularUI()
        .step("remember split width",c->{c.put("before",split(c).first.getSizeWidth());c.put("otherSettings",new VjceEditor().editorSettings);});
        drag(s,"resize recipe browser",120);
        s.check("real divider drag changes left width",c->split(c).first.getSizeWidth()>c.<Float>get("before")+60)
        .check("right form stays beside browser",c->{var v=split(c);return v.second.getPositionX()>=v.first.getPositionX()+v.first.getSizeWidth()-.5f&&v.second.getSizeWidth()>=239;})
        .step("remember saved ratio",c->{c.put("ratio",split(c).getPercentage());try{var json=com.google.gson.JsonParser.parseString(Files.readString(settings())).getAsJsonObject();c.check("drag persisted to editor settings",Math.abs(json.get("vjceRecipeBrowserPercentage").getAsFloat()-split(c).getPercentage())<.1);}catch(Exception e){throw new IllegalStateException(e);}})
        .screenshot("split_dragged")
        .step("another open editor preserves global ratio",c->{c.<com.lowdragmc.lowdraglib2.editor.settings.EditorSettings>get("otherSettings").saveAllSettingsToFile();try{var json=com.google.gson.JsonParser.parseString(Files.readString(settings())).getAsJsonObject();c.check("other editor cannot overwrite ratio with stale value",Math.abs(json.get("vjceRecipeBrowserPercentage").getAsFloat()-c.<Float>get("ratio"))<.1);}catch(Exception e){throw new IllegalStateException(e);}})
        .step("refresh recipe settings",c->project(c).select(project(c).selected))
        .check("recipe refresh retains divider",c->Math.abs(split(c).getPercentage()-c.<Float>get("ratio"))<.1);
        click(s,"category settings","vjce_category_settings");
        s.check("category mode uses the full content width",c->!split(c).first.isDisplayed()&&Math.abs(split(c).second.getSizeWidth()-split(c).getContentWidth())<1);
        click(s,"recipe settings","vjce_recipe_settings");
        s.check("return to recipe mode retains ratio",c->split(c).first.isDisplayed()&&Math.abs(split(c).getPercentage()-c.<Float>get("ratio"))<.1)
        .closeScreen().openModularUI("reopened split data",c->open(c)).awaitModularUI()
        .check("reopening editor loads saved ratio",c->Math.abs(split(c).getPercentage()-c.<Float>get("ratio"))<.1)
        .step("another editor setting save retains ratio",c->{project(c).dataView.getEditor().editorSettings.saveAllSettingsToFile();try{var json=com.google.gson.JsonParser.parseString(Files.readString(settings())).getAsJsonObject();c.check("ordinary settings save retains split",Math.abs(json.get("vjceRecipeBrowserPercentage").getAsFloat()-c.<Float>get("ratio"))<.1);}catch(Exception e){throw new IllegalStateException(e);}});
        drag(s,"clamp to left minimum",-1000);
        s.check("browser minimum width enforced",c->split(c).first.getSizeWidth()>=159);
        drag(s,"clamp to right minimum",1000);
        s.check("details minimum width enforced",c->split(c).second.getSizeWidth()>=239).screenshot("split_minimums").closeScreen()
        .teardown("restore local editor settings",c->{try{var data=c.<byte[]>get("settingsBackup");if(data==null)Files.deleteIfExists(settings());else Files.write(settings(),data);}catch(Exception e){throw new IllegalStateException(e);}});
    }
    private static Path settings(){return VisualJeiCategoryEditor.STORE.root.resolve("editor.json");}
    private static ModularUI open(TestContext c){var p=VjceEditor.projectForCategory("minecraft:smelting");var w=new EditorWindow(VjceEditor::new);w.getCurrentEditor().loadProject(p,null);c.put("splitProject",p);p.dataView.getViewContainer().selectView(p.dataView);return new ModularUI(UI.of(w),c.player());}
    private static VjceProject project(TestContext c){return c.get("splitProject");}
    private static RecipeSplitView split(TestContext c){return project(c).dataView.split;}
    private static void click(ScenarioBuilder s,String name,String id){
        s.step("aim "+name,c->{var p=ElementBounds.of(project(c).dataView.selectId(id).findFirst().orElseThrow()).center();c.put("point",p);c.input().moveTo(p.x,p.y);})
        .step("press "+name,c->{var p=c.<Vector2f>get("point");c.input().mouseDown(p.x,p.y,0);})
        .step("release "+name,c->{var p=c.<Vector2f>get("point");c.input().mouseUp(p.x,p.y,0);});
    }
    private static void drag(ScenarioBuilder s,String name,float dx){
        s.step("aim "+name,c->{var v=split(c);var p=v.getWorldMouse(v.getPositionX()+v.first.getSizeWidth()-2,v.getPositionY()+v.getSizeHeight()*.4f);c.put("start",p);c.put("end",new Vector2f(p).add(dx,0));c.input().moveTo(p.x,p.y);})
        .step("press "+name,c->{var p=c.<Vector2f>get("start");c.input().mouseDown(p.x,p.y,0);})
        .step("drag "+name,c->{var p=c.<Vector2f>get("end");c.input().dragTo(p.x,p.y,0);})
        .step("release "+name,c->{var p=c.<Vector2f>get("end");c.input().mouseUp(p.x,p.y,0);});
    }
}
