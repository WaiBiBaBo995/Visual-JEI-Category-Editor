package com.pureneko.vjce.client;
import com.pureneko.vjce.VisualJeiCategoryEditor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
public final class ClientBootstrap {
    private ClientBootstrap(){}
    private static boolean resizePending;
    static void resizeAfterScreenChange(){resizePending=true;}
    public static void init(){
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::commands);
        MinecraftForge.EVENT_BUS.addListener(ClientBootstrap::tick);
    }
    private static void tick(TickEvent.ClientTickEvent event){if(event.phase==TickEvent.Phase.END){if(resizePending){resizePending=false;net.minecraft.client.Minecraft.getInstance().resizeDisplay();}SceneResources.sweep();PortValidation.tick();}}
    private static void commands(RegisterClientCommandsEvent event){
        event.getDispatcher().register(Commands.literal("vjce")
            .then(Commands.literal("editor").executes(c->{VjceEditor.open();return 1;}))
            .then(Commands.literal("verify").executes(c->{try{var report=com.pureneko.vjce.integration.RuntimeAudit.run().toString();c.getSource().sendSuccess(()->Component.literal(report),false);return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.toString()));return 0;}}))
            .then(Commands.literal("export").executes(c->{try{var output=FMLPaths.GAMEDIR.get().resolve("exports/vjce-1.20.1-modpack.zip");VisualJeiCategoryEditor.STORE.exportZip(output,assets());c.getSource().sendSuccess(()->Component.literal(output.toString()),false);return 1;}catch(Exception e){c.getSource().sendFailure(Component.literal(e.toString()));return 0;}})));
    }
    public static java.nio.file.Path assets(){return new java.io.File(com.lowdragmc.lowdraglib.LDLib.getLDLibDir(),"assets").toPath();}
}
