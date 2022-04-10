package org.teacon.xkdeco;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.teacon.xkdeco.client.XKDecoClient;
import org.teacon.xkdeco.data.XKDecoBlockStateProvider;
import org.teacon.xkdeco.init.XKDecoObjects;

import javax.annotation.ParametersAreNonnullByDefault;

@Mod(XKDeco.ID)
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class XKDeco {
    public static final String ID = "xkdeco";

    public XKDeco() {
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        XKDecoObjects.BLOCKS.register(modEventBus);
        XKDecoObjects.ITEMS.register(modEventBus);

        modEventBus.addListener(XKDecoBlockStateProvider::register);

        if (FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(XKDecoClient::setCutoutBlocks);
        }
    }
}
