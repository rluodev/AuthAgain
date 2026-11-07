//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.client.AuthAgainClient;
import dev.rluo.authagain.gui.AuthAgainConfigScreen;

@Mod(value = AuthAgainMod.MODID, dist = Dist.CLIENT)
public final class AuthAgainNeoForge {

	public AuthAgainNeoForge(IEventBus modBus, ModContainer container) {
		modBus.addListener(this::onClientSetup);
		modBus.addListener(ModConfigEvent.Loading.class, AuthAgainConfigNeoForge::onLoad);
		modBus.addListener(ModConfigEvent.Reloading.class, AuthAgainConfigNeoForge::onLoad);
		container.registerConfig(ModConfig.Type.CLIENT, AuthAgainConfigNeoForge.SPEC);
		container.registerExtensionPoint(IConfigScreenFactory.class,
				(modContainer, parent) -> new AuthAgainConfigScreen(parent));
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		AuthAgainClient.init(FMLPaths.CONFIGDIR.get());
		NeoForgeScreenHooks.register();
	}
}
*///?}
