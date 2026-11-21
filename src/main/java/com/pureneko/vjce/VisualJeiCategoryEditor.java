package com.pureneko.vjce;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.mojang.logging.LogUtils;
import com.pureneko.vjce.client.BlockSceneElement;
import com.pureneko.vjce.client.SceneResources;
import com.pureneko.vjce.client.VjceEditor;
import com.pureneko.vjce.data.ProjectStore;
import com.pureneko.vjce.integration.RuntimeAudit;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class VisualJeiCategoryEditor implements ClientModInitializer {
    public static final String ID = "vjce";
    public static final Logger LOG = LogUtils.getLogger();
    public static final ProjectStore STORE = new ProjectStore(FabricLoader.getInstance().getConfigDir().resolve(ID));

    @Override
    public void onInitializeClient() {
        BlockSceneElement.registerRenderer();
        ClientTickEvents.END_CLIENT_TICK.register(client -> SceneResources.sweep());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal(ID)
                .then(literal("editor").executes(context -> {
                    // ChatScreen closes itself after the command returns.
                    context.getSource().getClient().schedule(VjceEditor::open);
                    return 1;
                }))
                .then(literal("verify").executes(context -> {
                    try {
                        context.getSource().sendFeedback(Component.literal(RuntimeAudit.run().toString()));
                        return 1;
                    } catch (Exception failure) {
                        context.getSource().sendError(Component.literal(failure.toString()));
                        return 0;
                    }
                }))
                .then(literal("export").executes(context -> {
                    try {
                        var output = FabricLoader.getInstance().getGameDir().resolve("exports/vjce-modpack.zip");
                        STORE.exportZip(output, LDLib2.getAssetsDir().toPath());
                        context.getSource().sendFeedback(Component.literal(output.toString()));
                        return 1;
                    } catch (Exception failure) {
                        context.getSource().sendError(Component.literal(failure.toString()));
                        return 0;
                    }
                }))));
    }
}
