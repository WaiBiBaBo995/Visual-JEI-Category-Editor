package com.pureneko.vjce;
import com.pureneko.vjce.data.ProjectStore;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import org.slf4j.Logger;
@Mod(VisualJeiCategoryEditor.ID)
public final class VisualJeiCategoryEditor {
    public static final String ID="vjce";
    public static final Logger LOG=LogUtils.getLogger();
    public static final ProjectStore STORE=new ProjectStore(FMLPaths.CONFIGDIR.get().resolve("vjce-1.20.1"));
    public VisualJeiCategoryEditor(){DistExecutor.safeRunWhenOn(Dist.CLIENT,()->com.pureneko.vjce.client.ClientBootstrap::init);}
}
