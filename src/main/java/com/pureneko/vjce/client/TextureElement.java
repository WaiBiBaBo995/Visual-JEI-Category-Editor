package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.editor.data.resource.Resource;
import com.mojang.datafixers.util.Either;
import com.pureneko.vjce.client.canvas.*;
import net.minecraft.nbt.CompoundTag;
import java.io.File;

/** LDLib1 texture resources, with a baked copy for independent JEI runtime layouts. */
public final class TextureElement extends CanvasElement {
    public IGuiTexture image=new ResourceTexture("ldlib:textures/gui/slot.png");
    private CompoundTag reference;
    private Resource<IGuiTexture> resources;
    public TextureElement(){super("texture");width=height=18;}
    public void setImage(IGuiTexture value){image=value;reference=null;}
    public void bind(Resource<IGuiTexture> resources){
        this.resources=resources;image.setUIResource(resources);
        if(reference==null||image instanceof UIResourceTexture)return;
        String key=reference.getString("key");
        var path=reference.getBoolean("file")?Either.<String,File>right(new File(ClientBootstrap.assets().toFile(),key)):Either.<String,File>left(key);
        if(resources.getResource(path)!=null)image=new UIResourceTexture(resources,path);
    }
    @Override public void screenTick(){super.screenTick();image.updateTick();}
    @Override public void drawBackgroundAdditional(CanvasContext c){image.draw(c.graphics,(int)c.localMouseX,(int)c.localMouseY,0,0,Math.round(width),Math.round(height));}
    @Override public CompoundTag serialize(){
        var tag=super.serialize();IGuiTexture resolved=image;
        if(image instanceof UIResourceTexture resource){
            resolved=resource.getTexture();reference=new CompoundTag();
            resource.key.ifLeft(k->reference.putString("key",k));
            resource.key.ifRight(f->{reference.putBoolean("file",true);reference.putString("key",ClientBootstrap.assets().toAbsolutePath().normalize().relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\','/'));});
        }
        var data=IGuiTexture.serializeWrapper(resolved);
        if(data!=null&&resources!=null){
            var previous=UIResourceTexture.getProjectResource();boolean wasProject=UIResourceTexture.isProject();
            UIResourceTexture.setCurrentResource(resources,false);
            try{for(int i=0;i<16&&data.toString().contains("ui_resource");i++)data=IGuiTexture.serializeWrapper(IGuiTexture.deserializeWrapper(data));}finally{if(previous==null)UIResourceTexture.clearCurrentResource();else UIResourceTexture.setCurrentResource(previous,wasProject);}
        }
        if(data==null){data=new CompoundTag();data.putString("type","empty");}tag.put("ldlibTexture",data);
        if(reference!=null)tag.put("textureReference",reference.copy());return tag;
    }
    public void load(CompoundTag tag){
        if(tag.contains("ldlibTexture"))image=IGuiTexture.deserializeWrapper(tag.getCompound("ldlibTexture"));
        else if(!texture.isBlank())image=new ResourceTexture(texture);
        reference=tag.contains("textureReference")?tag.getCompound("textureReference").copy():null;
    }
}
