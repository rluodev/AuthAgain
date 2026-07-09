package dev.rluo.authagain.config;

import dev.rluo.authagain.AuthAgainMod;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.loading.FMLPaths;

import java.util.Set;

@Mod.EventBusSubscriber(modid = AuthAgainMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AuthAgainConfig {
	private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

	private static final ForgeConfigSpec.BooleanValue PERSIST_ACCOUNTS = BUILDER
			.comment("Whether to save accounts to disk")
			.define("persistAccounts", true);

	public static final ForgeConfigSpec SPEC = BUILDER.build();

	public static boolean persistAccounts;

	@SubscribeEvent
	static void onLoad(final ModConfigEvent event) {
		persistAccounts = PERSIST_ACCOUNTS.get();
	}
}
