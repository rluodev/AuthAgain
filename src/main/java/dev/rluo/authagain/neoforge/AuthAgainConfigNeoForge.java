//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import dev.rluo.authagain.config.AuthAgainConfig;

public final class AuthAgainConfigNeoForge {

	private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

	private static final ModConfigSpec.BooleanValue PERSIST_ACCOUNTS = BUILDER
			.comment("Whether to save accounts to disk")
			.define("persistAccounts", true);

	public static final ModConfigSpec SPEC = BUILDER.build();

	private AuthAgainConfigNeoForge() {
	}

	static void onLoad(final ModConfigEvent event) {
		AuthAgainConfig.persistAccounts = PERSIST_ACCOUNTS.get();
	}
}
*///?}
