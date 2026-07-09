package dev.rluo.authagain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import dev.rluo.authagain.gui.AccountManagerScreen;

public class ScreenHooks {
	@SubscribeEvent
	public static void titleScreenEvent(final ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof TitleScreen) {
			LaunchAccountImporter.importIfNeeded();
		}
	}

	@SubscribeEvent
	public static void joinMultiplayerScreenEvent(final ScreenEvent.Init.Post event) {
		if (!(event.getScreen() instanceof JoinMultiplayerScreen screen)) {
			return;
		}
		Button button = Button.builder(Component.translatable("gui.authagain.accounts"), btn -> Minecraft.getInstance().setScreen(new AccountManagerScreen(screen))).bounds(5, 5, 100, 20).build();
		event.addListener(button);
	}
}