package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.ui.event.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import java.util.*;

final class SceneEditingView extends View {
    final RecipeLayoutView owner;
    final BlockSceneElement target;
    final Stage preview=new Stage();
    final StructuredTagEditor edit=new StructuredTagEditor().setCompoundTagOnly();
    final UIElement buttonContainer=Forms.row();
    private final Label error=new Label(),selection=new Label();
    private final ScrollerView objects=new ScrollerView();
    private final UIElement fields=Forms.column();
    private String selectedKind="entities";
    private int selected=-1;
    private boolean updating;

    SceneEditingView(RecipeLayoutView owner,BlockSceneElement target){
        super(target==null?"vjce.scene.add":"vjce.scene.configure");this.owner=owner;this.target=target;
        setId("vjce_scene_dialog");setCanRemove(true);setFloatable(false);
        var data=target==null?new CompoundTag():target.blocks.copy();if(target!=null)SceneEditor.copySettings(target,preview);
        preview.blocks=data;preview.clock.playing=false;preview.setId("vjce_scene_preview");
        preview.layout(l->{l.widthPercent(100);l.flex(1);l.minHeight(90);});
        edit.setId("vjce_scene_data");edit.setValue(data,false);edit.layout(l->{l.widthPercent(100);l.height(200);});
        edit.setTagResponder(tag->{preview.blocks=((CompoundTag)tag).copy();if(!updating)refreshObjects();});
        if(!BlockSceneElement.rows(data,"entities").isEmpty())selected=0;else if(!BlockSceneElement.rows(data,"blocks").isEmpty()){selectedKind="blocks";selected=0;}
        var split=new SplitView.Horizontal();split.setPercentage(72);split.setMinPercentage(45);split.setMaxPercentage(85);split.setBorderSize(5);
        split.layout(l->{l.flex(1);l.minHeight(0);});split.first.addChild(preview);
        var parameters=new ScrollerView();parameters.setId("vjce_scene_parameters");parameters.layout(l->{l.widthPercent(100);l.flex(1);l.minHeight(0);});split.second.addChild(parameters);
        var content=parameters.getContentHost();
        content.addChildren(new Label().setText("vjce.asset.drag_help").textStyle(s->s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true)),
                new Button().setText("vjce.world.open").setOnClick(e->WorldSelectionDialog.open(this,this::working,this::setWorking)).setId("vjce_world_open"));
        objects.layout(l->{l.widthPercent(100);l.height(105);l.minHeight(70);l.flexShrink(0);});objects.setId("vjce_scene_objects");
        content.addChildren(objects,selection,fields);
        objects.addEventListener(UIEvents.DRAG_PERFORM,e->{if(e.dragHandler.draggingObject instanceof SceneAssets.Drop drop){addDrop(drop,null);e.stopImmediatePropagation();}});
        addSettings(content);
        var advanced=new UIElement().layout(l->l.widthPercent(100));advanced.setDisplay(false);advanced.addChild(edit);
        content.addChildren(new Button().setText("vjce.scene.raw").setOnClick(e->advanced.setDisplay(!advanced.isDisplayed())),advanced,error);
        error.textStyle(s->s.textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.WRAP).adaptiveHeight(true));
        buttonContainer.layout(l->{l.height(23);l.minHeight(23);l.flexShrink(0);});
        buttonContainer.addChildren(new Button().setText("vjce.scene.apply").setOnClick(e->apply()).setId("vjce_scene_apply"),new Button().setText("vjce.close").setOnClick(e->close()).setId("vjce_scene_cancel"));
        addChildren(new Label().setText("vjce.asset.global_hint").layout(l->{l.widthPercent(100);l.flexShrink(0);}),split,buttonContainer);
        preview.addEventListener(UIEvents.DRAG_PERFORM,e->{if(e.dragHandler.draggingObject instanceof SceneAssets.Drop drop){try{addDrop(drop,preview.point(e.x,e.y,drop.type().entity));}catch(RuntimeException invalid){error.setText(invalid.getMessage());}e.stopImmediatePropagation();}});
        preview.addEventListener(UIEvents.CLICK,e->{if(e.button==0)pick(e.x,e.y);});
        refreshObjects();EditorForms.ore(this);
    }
    CompoundTag working(){return ((CompoundTag)edit.getValue()).copy();}
    void setWorking(CompoundTag data){edit.setValue(data.copy(),true);error.setText("");}
    private void mutate(java.util.function.Consumer<CompoundTag> action){try{var data=working();action.accept(data);setWorking(data);}catch(RuntimeException invalid){error.setText(invalid.getMessage());}}
    private ListTag selectedRows(CompoundTag data){return BlockSceneElement.rows(data,selectedKind);}
    private void refreshObjects(){
        var data=working();var host=objects.getContentHost();host.clearAllChildren();
        for(String kind:List.of("blocks","entities"))for(int i=0;i<BlockSceneElement.rows(data,kind).size();i++){
            final int index=i;var row=BlockSceneElement.rows(data,kind).getCompound(i);
            String name=row.contains("resource")?SceneAssets.displayName(kind.equals("blocks")?SceneAssetResource.BLOCKS:SceneAssetResource.ENTITIES,row):kind.equals("blocks")?row.getCompound("state").getString("Name"):row.getString("id");
            var button=new Button().setText((kind.equals("blocks")?"B ":"E ")+i+" "+name).setOnClick(e->{selectedKind=kind;selected=index;refreshFields();});
            button.setId("vjce_object_"+kind+"_"+i);button.layout(l->{l.widthPercent(100);l.minHeight(18);l.flexShrink(0);});
            button.addEventListener(UIEvents.DRAG_PERFORM,e->{if(e.dragHandler.draggingObject instanceof SceneAssets.Drop drop&&drop.type().entity==kind.equals("entities")){
                mutate(copy->{var old=BlockSceneElement.rows(copy,kind).getCompound(index);var ref=SceneAssets.instance(drop);for(String key:List.of("resource","resourceId"))old.put(key,ref.get(key).copy());});e.stopImmediatePropagation();}});
            host.addChild(button);
        }
        if(selected>=selectedRows(data).size())selected=-1;refreshFields();
    }
    private void refreshFields(){
        fields.clearAllChildren();if(selected<0){selection.setText("vjce.scene.select_instance");return;}
        var row=selectedRows(working()).getCompound(selected);selection.setText((selectedKind.equals("blocks")?"B ":"E ")+selected);
        for(String key:List.of("x","y","z","yaw","pitch","roll","scale")){
            boolean integer=selectedKind.equals("blocks")&&List.of("x","y","z").contains(key);
            double value=row.contains(key)?row.getDouble(key):key.equals("scale")?1:0;
            double min=key.equals("scale")?.05:List.of("x","y","z").contains(key)?-64:-36000;
            double max=key.equals("scale")?16:List.of("x","y","z").contains(key)?64:36000;
            var input=EditorForms.number(value,min,max,integer,v->{if(updating)return;updating=true;try{mutate(data->selectedRows(data).getCompound(selected).putDouble(key,v));}finally{updating=false;}});
            input.setId("vjce_entity_"+key);fields.addChild(Forms.row().addChildren(new Label().setText(key).layout(l->{l.width(40);l.flexShrink(0);}),input));
        }
        fields.addChild(new Button().setText("vjce.scene.remove_instance").setOnClick(e->mutate(data->{selectedRows(data).remove(selected);selected=-1;})));
        if(selectedKind.equals("entities")){
            var pose=new Selector<net.minecraft.world.entity.Pose>();pose.setId("vjce_entity_pose");pose.setCandidates(List.of(net.minecraft.world.entity.Pose.STANDING,net.minecraft.world.entity.Pose.CROUCHING,net.minecraft.world.entity.Pose.SLEEPING,net.minecraft.world.entity.Pose.SWIMMING,net.minecraft.world.entity.Pose.FALL_FLYING));
            pose.setCandidateUIProvider(p->new Label().setText(p==null?"":p.name()));
            var effective=selectedRows(SceneAssets.resolve(working())).getCompound(selected);
            try{pose.setSelected(net.minecraft.world.entity.Pose.valueOf(effective.getString("pose").isBlank()?"STANDING":effective.getString("pose")),false);}catch(IllegalArgumentException ignored){}
            pose.setOnValueChanged(p->mutate(data->selectedRows(data).getCompound(selected).putString("pose",p.name())));fields.addChild(pose);
        }
        EditorForms.ore(fields);
    }
    private void addSettings(UIElement host){
        for(String key:List.of("cameraYaw","cameraPitch","cameraRange","speed","period","phase","distance")){
            try{var field=BlockSceneElement.class.getField(key);double value=((Number)field.get(preview)).doubleValue();
                var input=EditorForms.number(value,key.equals("cameraRange")?.2:key.equals("period")?1:key.equals("speed")?0:-36000,key.equals("speed")?20:36000,false,v->{try{field.set(preview,field.getType()==float.class?(Object)v.floatValue():v);}catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}});
                input.setId("vjce_scene_"+key);host.addChild(Forms.row().addChildren(new Label().setText("vjce.scene."+key).layout(l->{l.width(78);l.flexShrink(0);}),input));
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        host.addChild(new Toggle().setText("vjce.scene.auto_frame").setOn(preview.autoFrame,false).setOnToggleChanged(v->preview.autoFrame=v));
        host.addChild(new Toggle().setText("vjce.scene.tick").setOn(preview.animateBlockEntities,false).setOnToggleChanged(v->preview.animateBlockEntities=v));
        host.addChild(new Toggle().setText("vjce.scene.tick_entities").setOn(preview.animateEntities,false).setOnToggleChanged(v->preview.animateEntities=v));
        var motion=new Selector<BlockSceneElement.Motion>();motion.setCandidates(List.of(BlockSceneElement.Motion.values()));motion.setSelected(preview.motion,false);motion.setOnValueChanged(v->preview.motion=v);host.addChild(motion);
    }
    void addDrop(SceneAssets.Drop drop,Vec3 position){
        try{var data=working();String kind=drop.type().entity?"entities":"blocks";var rows=BlockSceneElement.rows(data,kind);
            if(rows.size()>=(drop.type().entity?32:4096))throw new IllegalArgumentException("Scene content limit reached");
            if(position==null){var occupied=new HashSet<BlockPos>();for(var tag:BlockSceneElement.rows(data,"blocks")){var r=(CompoundTag)tag;occupied.add(new BlockPos(r.getInt("x"),r.getInt("y"),r.getInt("z")));}
                for(var tag:BlockSceneElement.rows(data,"entities")){var r=(CompoundTag)tag;occupied.add(BlockPos.containing(r.getDouble("x"),r.getDouble("y"),r.getDouble("z")));}
                for(int z=0;z<=64&&position==null;z++)for(int x=0;x<=64;x++)if(!occupied.contains(new BlockPos(x,0,z))){position=new Vec3(drop.type().entity?x+.5:x,0,drop.type().entity?z+.5:z);break;}}
            if(position==null||Math.abs(position.x)>64||Math.abs(position.y)>64||Math.abs(position.z)>64)throw new IllegalArgumentException("No valid scene position");
            var row=SceneAssets.instance(drop);if(drop.type().entity){
                row.putDouble("x",position.x);row.putDouble("y",position.y);row.putDouble("z",position.z);
                row.putFloat("yaw", SceneEntities.frontQuarterYaw(preview.rotationYawForPlacement()));
            }else{
                var p=BlockPos.containing(position);for(var tag:rows){var r=(CompoundTag)tag;if(r.getInt("x")==p.getX()&&r.getInt("y")==p.getY()&&r.getInt("z")==p.getZ())throw new IllegalArgumentException("Position occupied");}
                row.putInt("x",p.getX());row.putInt("y",p.getY());row.putInt("z",p.getZ());}
            rows.add(row);data.put(kind,rows);selectedKind=kind;selected=rows.size()-1;setWorking(data);
        }catch(RuntimeException invalid){error.setText(invalid.getMessage());}
    }
    private void pick(float x,float y){
        var ray=preview.ray(x,y);if(ray==null)return;var a=ray[0];var b=ray[1];double best=Double.MAX_VALUE;int index=-1;String kind="entities";
        var entities=preview.sceneEntities();for(int i=0;i<entities.size();i++){var hit=entities.get(i).getBoundingBox().inflate(.15).clip(a,b);if(hit.isPresent()&&hit.get().distanceToSqr(a)<best){best=hit.get().distanceToSqr(a);index=i;}}
        var block=preview.hit(x,y);if(block!=null&&block.distance()<best){var rows=BlockSceneElement.rows(working(),"blocks");for(int i=0;i<rows.size();i++){var r=rows.getCompound(i);if(new BlockPos(r.getInt("x"),r.getInt("y"),r.getInt("z")).equals(block.pos())){index=i;kind="blocks";break;}}}
        if(index>=0){selected=index;selectedKind=kind;refreshFields();}
    }
    private void apply(){
        preview.blocks=working();preview.ensureScene();
        if(BlockSceneElement.rows(working(),"blocks").isEmpty()&&BlockSceneElement.rows(working(),"entities").isEmpty()){error.setText("vjce.scene.empty");return;}
        if(!preview.error.isEmpty()){error.setText(preview.error);return;}
        if(target==null){owner.addScene(SceneEditor.copy(preview));}else{
            var before=SceneEditor.copy(target);var after=SceneEditor.copy(preview);
            owner.commitScene(()->SceneEditor.restore(after,target),()->SceneEditor.restore(before,target));
        }owner.markAsDirty();close();
    }
    void close(){removeSelf();if(owner.getViewContainer()!=null)owner.getViewContainer().selectView(owner);}
    @Override protected void onClose(){close();}
    @Override protected void onRemoved(){preview.release();SceneEditor.closed(this);super.onRemoved();}
    final class Stage extends BlockSceneElement {
        private record Hit(BlockPos pos,net.minecraft.core.Direction face,Vec3 point,AABB bounds,double distance){}
        private Matrix4f inverse;
        Stage(){setDraggable(true);setScalable(true);setIntractable(true);setRenderSelect(false);}
        @Override protected boolean allowEmptyScene(){return true;}
        float rotationYawForPlacement() { return inverse == null ? cameraYaw : rotationYaw; }
        @Override protected void onDragSourceUpdate(UIEvent event){super.onDragSourceUpdate(event);cameraYaw=rotationYaw;cameraPitch=rotationPitch;}
        @Override public void renderBlockOverLay(com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer r){super.renderBlockOverLay(r);inverse=new Matrix4f(com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix()).mul(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix()).invert();}
        Vec3[] ray(float x,float y){if(inverse==null)return null;var mouse=getLocalMouse(x,y);float nx=(mouse.x-getContentX())/getContentWidth()*2-1,ny=1-(mouse.y-getContentY())/getContentHeight()*2;
            var a=inverse.transform(new Vector4f(nx,ny,-1,1));var b=inverse.transform(new Vector4f(nx,ny,1,1));return new Vec3[]{new Vec3(a.x/a.w,a.y/a.w,a.z/a.w),new Vec3(b.x/b.w,b.y/b.w,b.z/b.w)};}
        Vec3 point(float x,float y,boolean entity){
            var hit=hit(x,y);if(hit!=null){if(entity)return new Vec3(hit.point.x,hit.bounds.maxY,hit.point.z);return Vec3.atLowerCornerOf(hit.pos.relative(hit.face));}
            var ray=ray(x,y);if(ray==null||Math.abs(ray[1].y-ray[0].y)<.00001)throw new IllegalArgumentException("No ground intersection");
            double t=-ray[0].y/(ray[1].y-ray[0].y);if(t<0||t>1)throw new IllegalArgumentException("No ground intersection");var p=ray[0].lerp(ray[1],t);return entity?p:Vec3.atLowerCornerOf(BlockPos.containing(p));
        }
        Hit hit(float x,float y){
            var ray=ray(x,y);if(ray==null)return null;Hit nearest=null;
            for(var tag:BlockSceneElement.rows(SceneAssets.resolve(working()),"blocks")){
                var row=(CompoundTag)tag;var pos=new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z"));var box=SceneTransforms.bounds(pos,row);var point=box.clip(ray[0],ray[1]);if(point.isEmpty())continue;
                double distance=point.get().distanceToSqr(ray[0]);if(nearest!=null&&nearest.distance<=distance)continue;var p=point.get();
                double[] distances={Math.abs(p.y-box.minY),Math.abs(p.y-box.maxY),Math.abs(p.z-box.minZ),Math.abs(p.z-box.maxZ),Math.abs(p.x-box.minX),Math.abs(p.x-box.maxX)};
                int face=0;for(int i=1;i<distances.length;i++)if(distances[i]<distances[face])face=i;
                nearest=new Hit(pos,net.minecraft.core.Direction.values()[face],p,box,distance);
            }return nearest;
        }
    }
}
