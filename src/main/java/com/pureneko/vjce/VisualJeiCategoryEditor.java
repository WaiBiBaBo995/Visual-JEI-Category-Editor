package com.pureneko.vjce;

import com.pureneko.vjce.client.VjceEditor;
import com.pureneko.vjce.data.ProjectStore;
import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

@Mod(value = VisualJeiCategoryEditor.ID, dist = Dist.CLIENT)
public final class VisualJeiCategoryEditor {
    public static final String ID = "vjce";
    public static final Logger LOG = LogUtils.getLogger();
    public static final ProjectStore STORE = new ProjectStore(FMLPaths.CONFIGDIR.get().resolve(ID));

    public VisualJeiCategoryEditor() {
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientTickEvent.Post e) -> com.pureneko.vjce.client.SceneResources.sweep());
    }

    private void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(ID)
                .then(Commands.literal("editor").executes(context -> { VjceEditor.open(); return 1; }))
                .then(Commands.literal("verify").executes(context -> {
                    try {
                        var report = com.pureneko.vjce.integration.RuntimeAudit.run();
                        context.getSource().sendSuccess(() -> Component.literal(report.toString()), false); return 1;
                    } catch (Exception e) { context.getSource().sendFailure(Component.literal(e.toString())); return 0; }
                }))
                .then(Commands.literal("export").executes(context -> {
                    try {
                        var output = FMLPaths.GAMEDIR.get().resolve("exports/vjce-modpack.zip");
                        STORE.exportZip(output, com.lowdragmc.lowdraglib2.LDLib2.getAssetsDir().toPath());
                        context.getSource().sendSuccess(() -> Component.literal(output.toString()), false);
                        return 1;
                    } catch (Exception e) { context.getSource().sendFailure(Component.literal(e.getMessage())); return 0; }
                })));
    }
}
