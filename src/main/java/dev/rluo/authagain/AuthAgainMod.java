package dev.rluo.authagain;

import dev.rluo.authagain.config.AuthAgainConfig;
import dev.rluo.authagain.client.ClientSetup;
import dev.rluo.authagain.auth.*;
import com.mojang.logging.LogUtils;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.forgespi.language.IModInfo;

import org.slf4j.Logger;

@Mod(AuthAgainMod.MODID)
public class AuthAgainMod {

    public static final String MODID = "authagain";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final IModInfo MOD_INFO = ModLoadingContext.get().getActiveContainer().getModInfo();
    public static AccountStore globalAccountStore = null;

    public AuthAgainMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(ClientSetup::clientSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AuthAgainConfig.SPEC);
    }
}
