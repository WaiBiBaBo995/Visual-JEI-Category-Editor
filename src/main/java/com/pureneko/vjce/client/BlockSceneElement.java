package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.editor.annotation.Configurable;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.utils.*;
import com.pureneko.vjce.client.canvas.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import java.util.*;
public class BlockSceneElement extends CanvasElement {
    static final int MAX_BLOCKS = 4096, MAX_ENTITIES = 32;
    public enum Motion { NONE, ROTATE, BOB }
    @Persisted @Configurable(name="vjce.scene.blocks") public CompoundTag blocks = defaults();
    static final float DEFAULT_CAMERA_YAW = -135;
    @Persisted @Configurable(name="vjce.scene.yaw") public float cameraYaw = DEFAULT_CAMERA_YAW;
    @Persisted @Configurable(name="vjce.scene.pitch") public float cameraPitch = 25;
    @Persisted @Configurable(name="vjce.scene.range") public float cameraRange = 1.5f;
    @Persisted @Configurable(name="vjce.scene.auto_frame") public boolean autoFrame = true;
    @Persisted public float cameraOffsetX,cameraOffsetY,cameraOffsetZ;
    @Persisted @Configurable(name="vjce.scene.motion") public Motion motion = Motion.NONE;
    @Persisted @Configurable(name="vjce.scene.speed") public double speed = 1;
    @Persisted @Configurable(name="vjce.scene.period") public double period = 80;
    @Persisted @Configurable(name="vjce.scene.phase") public double phase = 0;
    @Persisted @Configurable(name="vjce.scene.distance") public float distance = .25f;
    @Persisted @Configurable(name="vjce.scene.tick") public boolean animateBlockEntities = true;
    @Persisted @Configurable(name="vjce.scene.tick_entities") public boolean animateEntities = true;
    public final SceneClock clock = new SceneClock();
    public String error = "";
    public String missingResources = "";

    protected SceneRenderer renderer;
    protected TrackedDummyWorld dummyWorld;
    private CompoundTag loaded;
    private Vector3f center=new Vector3f(.5f);
    private double radius=.5,tickBudget;
    private SceneEntities entityContent;
    public BlockSceneElement(){super("scene");width=80;height=80;clip=true;}
    public static CompoundTag defaults(){try{return TagParser.parseTag("{blocks:[{x:0,y:0,z:0,state:{Name:\"minecraft:enchanting_table\"},data:{}}]}");}catch(Exception e){throw new IllegalStateException(e);}}
    public static BlockSceneElement entityScene(){var scene=new BlockSceneElement();scene.blocks=new CompoundTag();scene.blocks.put("blocks",new ListTag());var entities=new ListTag();entities.add(SceneEntities.defaults("minecraft:pig"));scene.blocks.put("entities",entities);return scene;}
    static ListTag rows(CompoundTag data,String key){var value=data.get(key);if(value==null)return new ListTag();if(value instanceof ListTag list&&(list.isEmpty()||list.getElementType()==Tag.TAG_COMPOUND))return list;throw new IllegalArgumentException(key+" must be a list of compounds");}
    public List<Entity> sceneEntities(){return entityContent==null?List.of():entityContent.entities();}
    public void ensureScene(){
        if(blocks==null){error="Missing scene data";return;}
        var effective=SceneAssets.resolve(blocks);if(loaded!=null&&loaded.equals(effective))return;
        release();loaded=effective.copy();error="";missingResources="";
        try{
            var blockRows=rows(effective,"blocks");var entityRows=rows(effective,"entities");
            if(blockRows.size()>MAX_BLOCKS||entityRows.size()>MAX_ENTITIES||blockRows.isEmpty()&&entityRows.isEmpty())throw new IllegalArgumentException("Scene requires up to 4096 blocks and 32 entities");
            var world=new TrackedDummyWorld();dummyWorld=world;var positions=new LinkedHashSet<BlockPos>();AABB bounds=null;
            for(var tag:blockRows){var row=(CompoundTag)tag;var pos=new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z"));
                if(Math.abs(pos.getX())>64||Math.abs(pos.getY())>64||Math.abs(pos.getZ())>64||!positions.add(pos))throw new IllegalArgumentException("Invalid or duplicate scene position: "+pos);
                var state=BlockState.CODEC.parse(NbtOps.INSTANCE,row.getCompound("state")).getOrThrow(false,message->{});if(state.isAir())throw new IllegalArgumentException("Missing scene block: "+row.getCompound("state"));
                world.addBlock(pos,BlockInfo.fromBlockState(state));var box=SceneTransforms.bounds(pos,row);bounds=bounds==null?box:bounds.minmax(box);
            }
            for(var tag:blockRows){var row=(CompoundTag)tag;var tile=world.getBlockEntity(new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")));if(tile!=null&&!row.getCompound("data").isEmpty()){var data=tile.saveWithoutMetadata();data.merge(row.getCompound("data").copy());tile.load(data);}}
            for(var tag:blockRows){var row=(CompoundTag)tag;var tile=world.getBlockEntity(new BlockPos(row.getInt("x"),row.getInt("y"),row.getInt("z")));if(tile!=null&&!row.getCompound("update").isEmpty()){var update=row.getCompound("update").copy();var pos=tile.getBlockPos();for(var key:List.of("x","y","z"))if(update.contains(key))update.putInt(key,key.equals("x")?pos.getX():key.equals("y")?pos.getY():pos.getZ());tile.load(update);}}
            entityContent=new SceneEntities(world,entityRows);if(entityContent.bounds!=null)bounds=bounds==null?entityContent.bounds:bounds.minmax(entityContent.bounds);
            renderer=new SceneRenderer(world);renderer.setBlockTransforms(blockRows);renderer.entities=entityContent;renderer.addRenderedBlocks(positions,null);renderer.useOrtho(true);
            if(bounds==null)bounds=new AABB(0,0,0,1,1,1);var c=bounds.getCenter();center=new Vector3f((float)c.x,(float)c.y,(float)c.z);
            radius=Math.sqrt(bounds.getXsize()*bounds.getXsize()+bounds.getYsize()*bounds.getYsize()+bounds.getZsize()*bounds.getZsize())/2+.25;
            var missing=new LinkedHashSet<String>();for(var list:List.of(blockRows,entityRows))for(var tag:list){var row=(CompoundTag)tag;if(row.contains("missing"))missing.add(row.getString("missing"));}missingResources=String.join(", ",missing);
        }catch(RuntimeException failure){var failed=loaded;release();loaded=failed;error=failure.getMessage()==null?failure.toString():failure.getMessage();}
    }
    @Override public void screenTick(){
        super.screenTick();ensureScene();if(renderer==null||!error.isEmpty()||!clock.playing)return;
        tickBudget+=Double.isFinite(speed)?Math.max(0,Math.min(20,speed)):1;
        try{while(tickBudget>=1){
            if(animateEntities){entityContent.anchorAll();for(var e:sceneEntities()){e.tickCount++;e.setOldPosAndRot();e.tick();}entityContent.anchorAll();entityContent.checkAlive();}
            if(animateBlockEntities)for(var pos:dummyWorld.renderedBlocks.keySet()){var state=dummyWorld.getBlockState(pos);var tile=dummyWorld.getBlockEntity(pos);if(tile!=null&&tile.getType().isValid(state))tickBlock(state,tile,dummyWorld,pos);}
            tickBudget--;
        }}catch(RuntimeException failure){error="Scene animation: "+failure.getMessage();clock.playing=false;}
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static void tickBlock(BlockState state,net.minecraft.world.level.block.entity.BlockEntity tile,TrackedDummyWorld world,BlockPos pos){var ticker=state.getTicker(world,tile.getType());if(ticker!=null)((net.minecraft.world.level.block.entity.BlockEntityTicker)ticker).tick(world,pos,state,tile);}
    static int[] resolution(float w,float h,float sx,float sy,double guiScale){double longest=Math.max(1,Math.max(w,h));double scale=Math.max(32/longest,Math.min(1024/longest,Math.max(.001,Math.max(sx,sy))*guiScale));return new int[]{Math.max(1,(int)Math.ceil(w*scale-.001)),Math.max(1,(int)Math.ceil(h*scale-.001))};}
    @Override public void drawBackgroundAdditional(CanvasContext c){
        ensureScene();SceneResources.touch(this);
        if(!error.isEmpty()){c.graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,error,0,0,0xffff6666,true);return;}
        double t=clock.time(speed)+phase,fraction=t/Math.max(1,Double.isFinite(period)?period:80);float yaw=cameraYaw+(motion==Motion.ROTATE?(float)(fraction*360):0);
        float range=Math.max(.2f,Float.isFinite(cameraRange)?cameraRange:1.5f);if(autoFrame)range=Math.max(range,(float)(radius*Math.max(1,height/width)));
        var focus=new Vector3f(center).add(cameraOffsetX,cameraOffsetY+(motion==Motion.BOB?(float)Math.sin(fraction*Math.PI*2)*distance:0),cameraOffsetZ);
        // LDLib1's third argument is the horizontal angle despite its rotationPitch name.
        renderer.setCameraLookAt(focus,10,Math.toRadians(yaw),Math.toRadians(cameraPitch));
        try{
            var m=c.graphics.pose().last().pose();float sx=new Vector3f(m.m00(),m.m01(),m.m02()).length(),sy=new Vector3f(m.m10(),m.m11(),m.m12()).length();
            var size=resolution(width,height,sx,sy,net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
            if(renderer.getResolutionWidth()!=size[0]||renderer.getResolutionHeight()!=size[1])renderer.setFBOSize(size[0],size[1]);
            renderer.verticalOrtho(range,width,height,100);
            renderer.entityTime(clock.playing&&animateEntities,tickBudget,speed);c.graphics.flush();renderer.render(c.graphics.pose(),0,0,width,height,c.localMouseX,c.localMouseY);c.graphics.flush();
        }catch(RuntimeException failure){com.pureneko.vjce.VisualJeiCategoryEditor.LOG.error("Scene rendering failed",failure);error="Scene renderer: "+failure.getMessage();clock.playing=false;}
        if(!missingResources.isBlank())c.graphics.drawString(net.minecraft.client.Minecraft.getInstance().font,net.minecraft.network.chat.Component.translatable("vjce.asset.missing",missingResources),0,0,0xffff6666,true);
    }
    public void release(){SceneResources.forget(this);try{if(renderer!=null)renderer.releaseResource();}finally{renderer=null;if(dummyWorld!=null)dummyWorld.clear();dummyWorld=null;entityContent=null;loaded=null;tickBudget=0;}}
    @Override protected void onRemoved(){release();}
}
