package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.IGUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.pureneko.vjce.integration.CreateAnimation;
import mezz.jei.api.gui.drawable.IDrawable;

@LDLRegister(name="vjce-create-animation",group="vjce",registry="ldlib2:ui_element")
public class CreateAnimationElement extends UIElement {
    public enum Preset { PRESS, MIXER, CRUSHING_WHEELS }
    @Persisted @Configurable(name="vjce.scene.preset") public Preset preset=Preset.PRESS;
    @Persisted @Configurable(name="vjce.scene.basin") public boolean basin=false;
    @Persisted @Configurable(name="vjce.scene.speed") public double speed=1;
    @Persisted @Configurable(name="vjce.scene.phase") public double phase=0;
    public final SceneClock clock=new SceneClock();
    private IDrawable animation;
    private String loaded="",error="";
    public CreateAnimationElement() { layout(l->{l.width(100);l.height(100);}); }
    @Override protected void onRemoved(){super.onRemoved();SceneResources.forget(this);clock.reset();animation=null;loaded="";}
    public boolean available() { return com.lowdragmc.lowdraglib2.Platform.isModLoaded("create"); }
    @Override public void drawBackgroundAdditional(IGUIContext renderContext) {
        if (!(renderContext instanceof GUIContext c)) return;
        SceneResources.touch(this);
        String key=preset+":"+basin;
        if(!key.equals(loaded)) { loaded=key; try { animation=CreateAnimation.create(preset.name(),basin);error=""; } catch(RuntimeException failure) { error=failure.getMessage();animation=null; } }
        var g=c.graphics;
        if(animation==null) { g.text(net.minecraft.client.Minecraft.getInstance().font,error.isEmpty()?net.minecraft.client.resources.language.I18n.get("vjce.scene.requires_create"):error,(int)getContentX(),(int)getContentY(),0xffff6666,true);return; }
        g.pose().pushMatrix();
        try {
            g.pose().translate(getContentX()+getContentWidth()*.5f,getContentY()+getContentHeight()*.36f);
            g.pose().scale(getContentWidth()/100f,getContentHeight()/100f);
            CreateAnimation.scoped((float)(clock.time(speed)+phase),()->animation.draw(g,0,0));
        } finally { g.pose().popMatrix(); }
    }
}
