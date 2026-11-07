//? if forge {
package dev.rluo.authagain.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.client.AuthAgainClient;
import dev.rluo.authagain.gui.AuthAgainConfigScreen;

@Mod(AuthAgainMod.MODID)
public final class AuthAgainForge {

	public AuthAgainForge(FMLJavaModLoadingContext context) {
		IEventBus modBus = context.getModEventBus();
		modBus.addListener(this::onClientSetup);
		context.registerConfig(ModConfig.Type.CLIENT, AuthAgainConfigForge.SPEC);
		if (FMLEnvironment.dist == Dist.CLIENT) {
			context.getContainer().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
					() -> new ConfigScreenHandler.ConfigScreenFactory(AuthAgainConfigScreen::new));
		}
	}

	private void onClientSetup(final FMLClientSetupEvent event) {
		AuthAgainClient.init(FMLPaths.CONFIGDIR.get());
		ForgeScreenHooks.register();
	}
}
//?}
