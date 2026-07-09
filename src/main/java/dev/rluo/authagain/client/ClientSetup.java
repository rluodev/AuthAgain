package dev.rluo.authagain.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingException;
import net.minecraftforge.fml.ModLoadingStage;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;

public class ClientSetup {
	@SubscribeEvent
	public static void clientSetup(final FMLClientSetupEvent event) {
		Path path = FMLPaths.CONFIGDIR.get().toAbsolutePath().resolve("authagain");
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path);
			}
		} catch (IOException e) {
			AuthAgainMod.LOGGER.error("[AuthAgain] AuthAgain encountered an error while trying to find/create its config folder. Please report this error, along with the following debug information, to the mod maintainer: ", e);
			throw new ModLoadingException(AuthAgainMod.MOD_INFO, ModLoadingStage.SIDED_SETUP, AuthAgainMod.MODID, new Throwable("[AuthAgain] Encountered an error while trying to find/create the config folder. Please report this error to the mod maintainer. More information about this crash can be found in the logs."));
		}

		if (AuthAgainMod.globalAccountStore != null) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Expected the global account store to be null but got {}. Please report this error to the mod maintainer.", AuthAgainMod.globalAccountStore);
			throw new ModLoadingException(AuthAgainMod.MOD_INFO, ModLoadingStage.SIDED_SETUP, AuthAgainMod.MODID, new Throwable("[AuthAgain] Expected the global account store to be null, but it was not. Please report this error to the mod maintainer."));
		}

		AuthAgainMod.globalAccountStore = new AccountStore(path.resolve("accounts.json").toString());

		MinecraftForge.EVENT_BUS.addListener(ScreenHooks::titleScreenEvent);
		MinecraftForge.EVENT_BUS.addListener(ScreenHooks::joinMultiplayerScreenEvent);
	}
}