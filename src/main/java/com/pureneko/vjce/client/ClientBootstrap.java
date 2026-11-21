package com.pureneko.vjce.client;

import com.pureneko.vjce.VisualJeiCategoryEditor;
import com.pureneko.vjce.integration.RuntimeAudit;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ClientBootstrap {
    private ClientBootstrap() {}
    private static boolean resizePending;

    static void resizeAfterScreenChange() { resizePending = true; }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (resizePending) {
                resizePending = false;
                client.resizeDisplay();
            }
            SceneResources.sweep();
            PortValidation.tick();
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(literal("vjce")
                .then(literal("editor").executes(context -> {
                    // ChatScreen closes itself after the command returns.
                    context.getSource().getClient().tell(VjceEditor::open);
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
                        var output = FabricLoader.getInstance().getGameDir().resolve("exports/vjce-1.20.1-modpack.zip");
                        VisualJeiCategoryEditor.STORE.exportZip(output, assets());
                        context.getSource().sendFeedback(Component.literal(output.toString()));
                        return 1;
                    } catch (Exception failure) {
                        context.getSource().sendError(Component.literal(failure.toString()));
                        return 0;
                    }
                }))));
    }

    public static java.nio.file.Path assets() {
        return new java.io.File(com.lowdragmc.lowdraglib.LDLib.getLDLibDir(), "assets").toPath();
    }
}
