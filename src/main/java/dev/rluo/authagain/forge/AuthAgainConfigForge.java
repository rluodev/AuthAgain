//? if forge {
package dev.rluo.authagain.forge;

import java.util.Map;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.client.AuthAgainClient;

@Mod.EventBusSubscriber(modid = AuthAgainMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AuthAgainConfigForge {

	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	private static final ForgeConfigSpec.BooleanValue PERSIST_ACCOUNTS = BUILDER
			.comment("Whether to save accounts to disk")
			.define("persistAccounts", true);
	private static final ForgeConfigSpec.BooleanValue ENCRYPT_ACCOUNTS = BUILDER
			.comment("Whether to encrypt saved accounts. Disabling this makes sharing the instance unsafe.")
			.define("encryptAccounts", true);

	public static final ForgeConfigSpec SPEC = BUILDER.build();
	private static ModConfig config;

	private AuthAgainConfigForge() {
	}

	@SubscribeEvent
	static synchronized void onLoad(final ModConfigEvent event) {
		if (event.getConfig().getSpec() == SPEC
				&& (event instanceof ModConfigEvent.Loading || event instanceof ModConfigEvent.Reloading)) {
			config = event.getConfig();
			AuthAgainClient.applyConfig(persistAccounts(), encryptAccounts());
		}
	}

	static boolean persistAccounts() {
		return PERSIST_ACCOUNTS.get();
	}

	static boolean encryptAccounts() {
		return ENCRYPT_ACCOUNTS.get();
	}

	public static synchronized void save(boolean persistAccounts, boolean encryptAccounts) {
		boolean previousPersist = persistAccounts();
		boolean previousEncrypt = encryptAccounts();
		if (previousPersist == persistAccounts && previousEncrypt == encryptAccounts) {
			return;
		}
		Map<String, Object> values = config.getConfigData().valueMap();
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
		}
		AuthAgainClient.applyConfig(persistAccounts, encryptAccounts);
	}
}
//?}
