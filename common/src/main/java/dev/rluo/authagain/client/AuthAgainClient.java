package dev.rluo.authagain.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;

/**
 * <strong>AuthAgainClient</strong><br>
 * Client bootstrap: creates the account store under {@code configDir/authagain}.
 * Each loader's client entrypoint calls this and registers the screen hooks.
 */
public final class AuthAgainClient {

	private AuthAgainClient() {
	}

	public static void init(Path configDir) {
		Path dir = configDir.resolve(AuthAgainMod.MODID);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Could not create the config folder.", e);
			throw new IllegalStateException("[AuthAgain] Could not create the config folder.", e);
		}

		if (AuthAgainMod.globalAccountStore != null) {
			throw new IllegalStateException("[AuthAgain] The global account store was already initialized.");
		}
		AuthAgainMod.globalAccountStore = new AccountStore(dir.resolve("accounts.json").toString());
	}
}
