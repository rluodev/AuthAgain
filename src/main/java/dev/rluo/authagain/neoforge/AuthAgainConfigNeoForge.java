//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import java.util.Map;

import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import dev.rluo.authagain.client.AuthAgainClient;

public final class AuthAgainConfigNeoForge {

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	private static final ModConfigSpec.BooleanValue PERSIST_ACCOUNTS = BUILDER
			.comment("Whether to save accounts to disk")
			.translation("gui.authagain.config.persist_accounts")
			.define("persistAccounts", true);
	private static final ModConfigSpec.BooleanValue ENCRYPT_ACCOUNTS = BUILDER
			.comment("Whether to encrypt saved accounts. Disabling this makes sharing the instance unsafe.")
			.translation("gui.authagain.config.encrypt_accounts")
			.define("encryptAccounts", true);

	public static final ModConfigSpec SPEC = BUILDER.build();
	private static volatile ModConfig config;
	private static volatile boolean saving;

	private AuthAgainConfigNeoForge() {
	}

	static void onLoad(final ModConfigEvent event) {
		if (event.getConfig().getSpec() == SPEC
				&& (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)) {
			config = event.getConfig();
			if (!saving) {
				AuthAgainClient.applyConfig(PERSIST_ACCOUNTS.get(), ENCRYPT_ACCOUNTS.get());
			}
		}
	}

	public static synchronized void save(boolean persistAccounts, boolean encryptAccounts) {
		boolean previousPersist = PERSIST_ACCOUNTS.get();
		boolean previousEncrypt = ENCRYPT_ACCOUNTS.get();
		if (previousPersist == persistAccounts && previousEncrypt == encryptAccounts) {
			return;
		}
		Map<String, Object> values = config.getLoadedConfig().config().valueMap();
		saving = true;
		try {
			SPEC.afterReload();
			values.putAll(Map.of("persistAccounts", persistAccounts, "encryptAccounts", encryptAccounts));
			SPEC.save();
		} catch (RuntimeException e) {
			try {
				SPEC.afterReload();
				values.putAll(Map.of("persistAccounts", previousPersist, "encryptAccounts", previousEncrypt));
				SPEC.save();
			} catch (RuntimeException rollback) {
				e.addSuppressed(rollback);
			}
			throw e;
		} finally {
			saving = false;
		}
		AuthAgainClient.applyConfig(persistAccounts, encryptAccounts);
	}
}
*///?}
