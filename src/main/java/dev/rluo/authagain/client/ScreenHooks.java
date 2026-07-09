package dev.rluo.authagain.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import dev.rluo.authagain.auth.ReauthService;
import dev.rluo.authagain.gui.AccountManagerScreen;

public class ScreenHooks {

	private static final int WHITE = 0xFFFFFF;
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;
	private static final int GRAY = 0xA0A0A0;

	private static volatile ReauthService.TokenStatus sessionStatus = ReauthService.TokenStatus.UNKNOWN;
	private static volatile String validatedToken;

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
		refreshSessionStatus();
	}

	/**
	 * For convenience, show current session name and state in top right corner.
	 */
	@SubscribeEvent
	public static void joinMultiplayerRenderEvent(final ScreenEvent.Render.Post event) {
		if (!(event.getScreen() instanceof JoinMultiplayerScreen screen)) {
			return;
		}
		User user = Minecraft.getInstance().getUser();
		if (user == null) {
			return;
		}

		GuiGraphics g = event.getGuiGraphics();
		Font font = Minecraft.getInstance().font;
		int rightEdge = screen.width - 5;

		Component name = Component.literal(user.getName());

		Component status;
		int statusColor;
		switch (sessionStatus) {
			case VALID -> {
				status = Component.translatable("gui.authagain.status.valid");
				statusColor = GREEN;
			}
			case INVALID -> {
				status = Component.translatable("gui.authagain.status.expired");
				statusColor = RED;
			}
			default -> {
				status = Component.translatable("gui.authagain.status.checking");
				statusColor = GRAY;
			}
		}

		g.drawString(font, name, rightEdge - font.width(name), 5, WHITE);
		g.drawString(font, status, rightEdge - font.width(status), 16, statusColor);
	}

	/**
	 * Validates the current session's token on a background thread and
	 * caches the result.
	 */
	private static void refreshSessionStatus() {
		User user = Minecraft.getInstance().getUser();
		if (user == null) {
			return;
		}
		String token = user.getAccessToken();
		if (token != null && token.equals(validatedToken)) {
			return;
		}
		validatedToken = token;
		sessionStatus = ReauthService.TokenStatus.UNKNOWN;
		ReauthService.validate(token).whenComplete((status, throwable) ->
				sessionStatus = throwable != null ? ReauthService.TokenStatus.UNKNOWN : status);
	}
}
