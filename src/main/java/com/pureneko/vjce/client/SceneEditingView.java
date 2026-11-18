package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.*;
import net.minecraft.nbt.*;
import java.util.*;
final class SceneEditingView extends WidgetGroup {
    final RecipeLayoutView owner;
    final BlockSceneElement target,preview;
    final Forms.Form form;
    final Forms.Sidebar sidebar;
    final LabelWidget error;
    private String selectedKind="blocks";
    private int selected=-1;
    private boolean closed;
    final SceneViewport viewport;
    SceneEditingView(RecipeLayoutView owner,BlockSceneElement target){super(0,36,owner.getSize().width,owner.getSize().height);this.owner=owner;this.target=target;preview=target==null?new BlockSceneElement():SceneEditor.copy(target);preview.clock.playing=false;int w=getSize().width,h=getSize().height;preview.width=Math.max(40,w*.55f);preview.height=Math.max(40,h-25);viewport=new SceneViewport(this,(int)preview.width,(int)preview.height);addWidget(viewport);form=new Forms.Form(0,0,w-(int)preview.width-3,h-42);sidebar=new Forms.Sidebar((int)preview.width+3,0,w-(int)preview.width-3,h-24,"vjce.scene.configure",form);addWidget(sidebar);sidebar.onResize=this::layoutContent;setDraggingConsumer(o->o instanceof SceneAssets.Drop,o->{},o->{},o->{if(o instanceof SceneAssets.Drop drop)addDrop(drop);});
        addWidget(Forms.button(0,h-22,w/3,"vjce.scene.apply",this::apply));addWidget(Forms.button(w/3,h-22,w/3,"vjce.close",this::close));error=new LabelWidget(3,h-10,"").setTextColor(0xffff6666);addWidget(error);refresh();}
    private void layoutContent(){int w=getSize().width,h=getSize().height,available=Math.max(20,w-sidebar.occupied()-3);sidebar.setSelfPosition(new Position(available+3,0));viewport.setSize(new Size(available,Math.max(20,h-25)));preview.width=available;preview.height=viewport.getSize().height;}
    void resizeView(int w,int h){setSize(new Size(w,h));sidebar.resizePanel(Math.min(330,Math.max(130,(int)(w*.45)-3)),h-24);int i=0;for(var widget:widgets)if(widget instanceof ButtonWidget){widget.setSelfPosition(new Position(i*w/3,h-22));widget.setSize(new Size(Math.max(1,w/3),18));i++;}error.setSelfPosition(new Position(3,h-10));layoutContent();}
    @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){sidebar.advanceAnimation(System.nanoTime());super.drawInBackground(g,mx,my,pt);}
    String selectedKind(){return selectedKind;}
    int selectedIndex(){return selected;}
    CompoundTag selectedRow(){var list=BlockSceneElement.rows(preview.blocks,selectedKind);return selected>=0&&selected<list.size()?list.getCompound(selected):null;}
    void select(String kind,int index){selectedKind=kind;selected=index;refresh();}
    @Override public boolean keyPressed(int key,int scan,int mods){if(super.keyPressed(key,scan,mods))return true;return viewport.key(key);}
    CompoundTag working(){return preview.blocks.copy();}
    void setWorking(CompoundTag data){preview.blocks=data.copy();preview.release();refresh();}
    void refresh(){form.clear();form.label("vjce.asset.global_hint");form.button("vjce.world.open",()->WorldSelectionDialog.open(this,this::working,this::setWorking));form.button("vjce.scene.entity",()->{var list=BlockSceneElement.rows(preview.blocks,"entities");list.add(SceneEntities.defaults("minecraft:pig"));preview.blocks.put("entities",list);selectedKind="entities";selected=list.size()-1;preview.release();refresh();});
        for(String kind:List.of("blocks","entities")){var list=BlockSceneElement.rows(preview.blocks,kind);for(int i=0;i<list.size();i++){int index=i;form.button((kind.equals("blocks")?"B ":"E ")+i+" "+list.getCompound(i).getString("id"),()->{selectedKind=kind;selected=index;refresh();});}}
        var rows=BlockSceneElement.rows(preview.blocks,selectedKind);if(selected>=0&&selected<rows.size()){var row=rows.getCompound(selected);for(String key:List.of("x","y","z","yaw","pitch","roll","scale")){form.number("vjce.field."+key,row.contains(key)?row.getDouble(key):key.equals("scale")?1d:0d,key.equals("scale")?.05:-30000000,key.equals("scale")?16:30000000,key.equals("scale")?.1:1,n->{double value=n.doubleValue();if(selectedKind.equals("blocks")&&Set.of("x","y","z").contains(key))row.putInt(key,(int)value);else row.putDouble(key,value);preview.release();});}if(selectedKind.equals("entities"))form.choice("vjce.field.pose",Arrays.stream(net.minecraft.world.entity.Pose.values()).map(Forms::enumLabel).toList(),Forms.enumLabel(net.minecraft.world.entity.Pose.valueOf(row.getString("pose").isBlank()?"STANDING":row.getString("pose"))),s->{for(var pose:net.minecraft.world.entity.Pose.values())if(Forms.enumLabel(pose).equals(s))row.putString("pose",pose.name());preview.release();});form.tag("vjce.scene.raw",row,tag->{rows.set(selected,tag);preview.release();});form.button("vjce.scene.remove_instance",()->{rows.remove(selected);selected=-1;preview.release();refresh();});}
        form.object(preview,Set.of("blocks","clock","type","id","text","texture","x","y","width","height","rotation","scaleX","scaleY","shearX","opacity","progress","color","background","visible","clip","hitTest","error","missingResources"),this::refresh,preview::release);form.tag("vjce.scene.raw",preview.blocks,data->{preview.blocks=data;preview.release();});form.button("vjce.scene.pause",()->preview.clock.playing=!preview.clock.playing);
    }
    void addDrop(SceneAssets.Drop drop){try{var data=working();String key=drop.type().entity?"entities":"blocks";var rows=BlockSceneElement.rows(data,key);if(rows.size()>=(drop.type().entity?32:4096))throw new IllegalArgumentException("Scene content limit reached");var row=SceneAssets.instance(drop);Set<String> occupied=new HashSet<>();for(var tag:rows){var r=(CompoundTag)tag;occupied.add(r.getInt("x")+","+r.getInt("z"));}var point=viewport.dropPoint(drop.type().entity);boolean found=point!=null;if(found){row.putDouble("x",drop.type().entity?point.x:Math.floor(point.x));row.putDouble("y",drop.type().entity?point.y:Math.floor(point.y));row.putDouble("z",drop.type().entity?point.z:Math.floor(point.z));}for(int z=0;z<=64&&!found;z++)for(int x=0;x<=64;x++)if(!occupied.contains(x+","+z)){row.putDouble("x",drop.type().entity?x+.5:x);row.putDouble("z",drop.type().entity?z+.5:z);found=true;break;}if(!found)throw new IllegalArgumentException("Scene content limit reached");if(!drop.type().entity)for(var tag:rows){var existing=(CompoundTag)tag;if(existing.getInt("x")==row.getInt("x")&&existing.getInt("y")==row.getInt("y")&&existing.getInt("z")==row.getInt("z"))throw new IllegalArgumentException("Position occupied");}rows.add(row);data.put(key,rows);selectedKind=key;selected=rows.size()-1;setWorking(data);}catch(RuntimeException e){error.setText(e.getMessage());}}
    void apply(){viewport.finish();preview.ensureScene();if(!preview.error.isEmpty()){error.setText(preview.error);return;}if(target==null)owner.addScene(SceneEditor.copy(preview));else{SceneEditor.restore(preview,target);owner.markAsDirty();}close();}
    void close(){if(closed)return;closed=true;viewport.cancel();preview.release();SceneEditor.closed(this);var tabs=owner.getEditor().getTabPages();var button=tabs.tabs.inverse().get(this);if(button!=null)tabs.removeTab(button);tabs.switchTag(owner);}
    @Override public void updateScreen(){super.updateScreen();preview.screenTick();}
}
