package com.pureneko.vjce;

import com.mojang.logging.LogUtils;
import com.pureneko.vjce.client.ClientBootstrap;
import com.pureneko.vjce.data.ProjectStore;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

public final class VisualJeiCategoryEditor implements ClientModInitializer {
    public static final String ID = "vjce";
    public static final Logger LOG = LogUtils.getLogger();
    public static final ProjectStore STORE = new ProjectStore(FabricLoader.getInstance().getConfigDir().resolve("vjce-1.20.1"));

    @Override
    public void onInitializeClient() {
        ClientBootstrap.init();
    }
}
