package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.lowdragmc.lowdraglib.gui.editor.ui.ResourcePanel;
import com.lowdragmc.lowdraglib.gui.editor.ui.resource.ResourceContainer;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.mojang.datafixers.util.Either;
import net.minecraft.nbt.*;
import java.io.File;
import java.util.*;
public abstract class SceneAssetResource extends Resource<CompoundTag> {
    static final SceneAssetResource BLOCKS=new Blocks(),ENTITIES=new Entities();
    final boolean entity;
    SceneAssetResource(boolean entity){super(ClientBootstrap.assets().resolve("vjce/"+(entity?"entities":"blocks")).toFile());this.entity=entity;}
    @LDLRegister(name="vjce_block",group="resource") public static final class Blocks extends SceneAssetResource {public Blocks(){super(false);}}
    @LDLRegister(name="vjce_entity",group="resource") public static final class Entities extends SceneAssetResource {public Entities(){super(true);}}
    @Override public String name(){return entity?"vjce_entity":"vjce_block";}
    @Override public Tag serialize(CompoundTag data){return data.copy();}
    @Override public CompoundTag deserialize(Tag data){return data instanceof CompoundTag tag?tag.copy():new CompoundTag();}
    CompoundTag defaults(){var data=entity?SceneEntities.defaults("minecraft:pig"):BlockSceneElement.rows(BlockSceneElement.defaults(),"blocks").getCompound(0).copy();data.putString("assetId",UUID.randomUUID().toString());return data;}
    @Override public ResourceContainer<CompoundTag,WidgetGroup> createContainer(ResourcePanel panel){var type=this;var container=new ResourceContainer<CompoundTag,WidgetGroup>(this,panel);
        container.setWidgetSupplier(key->{var group=new WidgetGroup(0,0,44,44);var value=getResource(key);if(value!=null){var scene=new BlockSceneElement();var list=new ListTag();list.add(value.copy());scene.blocks=new CompoundTag();scene.blocks.put(entity?"entities":"blocks",list);scene.width=40;scene.height=40;scene.clock.playing=false;group.addWidget(new CanvasPreview(0,0,40,40,scene,()->getResource(key),entity));}return group;});
        container.setOnAdd(name->defaults());container.setDragging(key->new SceneAssets.Drop(type,key),drop->new TextTexture(getResourceName(drop.path())));
        container.setOnEdit(key->SceneAssetConfigurator.open(this,key,panel));return container;
    }
    static final class CanvasPreview extends Widget {
        final BlockSceneElement scene;
        final java.util.function.Supplier<CompoundTag> definition;
        final boolean entity;
        CompoundTag shown;
        CanvasPreview(int x,int y,int w,int h,BlockSceneElement scene){this(x,y,w,h,scene,null,false);}
        CanvasPreview(int x,int y,int w,int h,BlockSceneElement scene,java.util.function.Supplier<CompoundTag> definition,boolean entity){super(x,y,w,h);this.scene=scene;this.definition=definition;this.entity=entity;}
        @Override public void updateScreen(){super.updateScreen();if(definition!=null){var next=definition.get();if(next!=null&&!next.equals(shown)){shown=next.copy();var rows=new ListTag();rows.add(shown.copy());scene.blocks=new CompoundTag();scene.blocks.put(entity?"entities":"blocks",rows);scene.release();}}}
        @Override public void setGui(com.lowdragmc.lowdraglib.gui.modular.ModularUI gui){if(gui==null)scene.release();super.setGui(gui);}
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){var p=getPosition();g.pose().pushPose();try{g.pose().translate(p.x,p.y,0);scene.render(g,mx-p.x,my-p.y,pt);}finally{g.pose().popPose();}}
    }
}
