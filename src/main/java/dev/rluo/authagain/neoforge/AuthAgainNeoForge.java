//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.client.AuthAgainClient;

@Mod(AuthAgainMod.MODID)
public final class AuthAgainNeoForge {

	public AuthAgainNeoForge(IEventBus modBus, ModContainer container) {
		modBus.addListener(this::onClientSetup);
		modBus.addListener(AuthAgainConfigNeoForge::onLoad);
		container.registerConfig(ModConfig.Type.CLIENT, AuthAgainConfigNeoForge.SPEC);
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		AuthAgainClient.init(FMLPaths.CONFIGDIR.get());
		NeoForgeScreenHooks.register();
	}
}
*///?}
