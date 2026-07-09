package dev.rluo.authagain.neoforge;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.client.AuthAgainClient;

@Mod(AuthAgainMod.MODID)
public final class AuthAgainNeoForge {

	public AuthAgainNeoForge(FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();
		modBus.addListener(this::onClientSetup);
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, AuthAgainConfigNeoForge.SPEC);
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		AuthAgainClient.init(FMLPaths.CONFIGDIR.get());
		NeoForgeScreenHooks.register();
	}
}
