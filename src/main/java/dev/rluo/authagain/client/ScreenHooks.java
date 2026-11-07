package dev.rluo.authagain.client;

import java.util.List;
import java.util.function.Consumer;

import org.joml.Vector2i;
import org.joml.Vector2ic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.auth.AccountStore.UnencryptedReason;
import dev.rluo.authagain.auth.ReauthService;
import dev.rluo.authagain.gui.AccountManagerScreen;

public class ScreenHooks {

	private static final int WHITE = 0xFFFFFF;
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;
	private static final int GRAY = 0xA0A0A0;

	private static final ClientTooltipPositioner WARNING_POSITIONER = (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
		Vector2ic position = DefaultTooltipPositioner.INSTANCE.positionTooltip(
				screenWidth, screenHeight, mouseX, mouseY, width, height);
		return new Vector2i(Math.max(5, Math.min(position.x(), screenWidth - width - 5)),
				Math.max(5, Math.min(position.y(), screenHeight - height - 5)));
	};

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
	public static void onScreenRender(Screen screen, GuiGraphics g, int mouseX, int mouseY) {
		if (!(screen instanceof JoinMultiplayerScreen)) {
			return;
		}
		User user = Minecraft.getInstance().getUser();
		if (user == null) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		int rightEdge = screen.width - 5;

		AccountStore store = AuthAgainMod.globalAccountStore;
		UnencryptedReason reason = store == null ? null : store.getUnencryptedReason();
		boolean unencrypted = reason != null;
		Component name = unencrypted
				? Component.translatable("gui.authagain.storage.unencrypted", user.getName())
				: Component.literal(user.getName());

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

		int nameLeft = rightEdge - font.width(name);
		g.drawString(font, name, nameLeft, 5, unencrypted ? RED : WHITE);
		g.drawString(font, status, rightEdge - font.width(status), 16, statusColor);
		if (reason != null && mouseX >= nameLeft && mouseX <= rightEdge && mouseY >= 5 && mouseY < 5 + font.lineHeight) {
			Component explanation = Component.translatable(switch (reason) {
				case DISABLED -> "gui.authagain.storage.unencrypted.disabled";
				case NO_DEVICE_ID -> "gui.authagain.storage.unencrypted.no_device_id";
				case ENCRYPTION_FAILED -> "gui.authagain.storage.unencrypted.encryption_failed";
				case WRITE_FAILED -> "gui.authagain.storage.unencrypted.write_failed";
				case LEGACY_FILES -> "gui.authagain.storage.unencrypted.legacy_files";
			});
			Component warning = Component.translatable("gui.authagain.storage.unencrypted.tooltip", explanation);
			List<FormattedCharSequence> lines = font.split(warning, Math.max(1, Math.min(250, screen.width - 10)));
			int tooltipHeight = lines.size() == 1 ? 8 : lines.size() * 10;
			if (tooltipHeight > screen.height - 10) {
				lines = font.split(warning, Math.max(1, screen.width - 10));
			}
			g.renderTooltip(font, lines, WARNING_POSITIONER, mouseX, mouseY);
		}
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
