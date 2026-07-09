package dev.rluo.authagain.client;

import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;

import dev.rluo.authagain.auth.ReauthService;
import dev.rluo.authagain.gui.AccountManagerScreen;

public class ScreenHooks {

	private static final int WHITE = 0xFFFFFF;
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;
	private static final int GRAY = 0xA0A0A0;

	private static volatile ReauthService.TokenStatus sessionStatus = ReauthService.TokenStatus.UNKNOWN;
	private static volatile String validatedToken;

	public static void onScreenInit(Screen screen, Consumer<AbstractWidget> addWidget) {
		if (screen instanceof TitleScreen) {
			LaunchAccountImporter.importIfNeeded();
			return;
		}
		if (!(screen instanceof JoinMultiplayerScreen multiplayer)) {
			return;
		}
		addWidget.accept(Button.builder(Component.translatable("gui.authagain.accounts"),
				btn -> Minecraft.getInstance().setScreen(new AccountManagerScreen(multiplayer)))
				.bounds(5, 5, 100, 20).build());
		refreshSessionStatus();
	}

	/**
	 * For convenience, show current session name and state in top right corner.
	 */
	public static void onScreenRender(Screen screen, GuiGraphics g) {
		if (!(screen instanceof JoinMultiplayerScreen)) {
			return;
		}
		User user = Minecraft.getInstance().getUser();
		if (user == null) {
			return;
		}

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
