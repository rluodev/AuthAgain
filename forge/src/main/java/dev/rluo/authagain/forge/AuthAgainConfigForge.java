package dev.rluo.authagain.forge;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.config.AuthAgainConfig;

@Mod.EventBusSubscriber(modid = AuthAgainMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AuthAgainConfigForge {

	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	private static final ForgeConfigSpec.BooleanValue PERSIST_ACCOUNTS = BUILDER
			.comment("Whether to save accounts to disk")
			.define("persistAccounts", true);

	public static final ForgeConfigSpec SPEC = BUILDER.build();

	private AuthAgainConfigForge() {
	}

	@SubscribeEvent
	static void onLoad(final ModConfigEvent event) {
		AuthAgainConfig.persistAccounts = PERSIST_ACCOUNTS.get();
	}
}
