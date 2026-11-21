package com.pureneko.vjce.client.canvas;
import net.minecraft.nbt.*;
public final class CanvasTemplate {
    private final CompoundTag data;
    private CanvasTemplate(CompoundTag data){this.data=data;}
    public static CanvasTemplate of(CanvasElement root){return new CanvasTemplate(root.serialize());}
    public static CanvasTemplate parse(String snbt){
        try { var data=TagParser.parseTag(snbt); if(!data.contains("type"))throw new IllegalArgumentException("Not a VJCE 1.20.1 canvas"); CanvasElement.deserialize(data); return new CanvasTemplate(data); }
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalArgumentException("Invalid VJCE canvas",e);}
    }
    public String serialize(){return data.toString();}
    public CanvasUI createUI(){return new CanvasUI(CanvasElement.deserialize(data));}
    @Override public String toString(){return data.toString();}
}
