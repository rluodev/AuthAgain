package dev.rluo.authagain.gui;

import javax.annotation.Nullable;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AuthAccount;
import dev.rluo.authagain.auth.DeviceCodePrompt;
import dev.rluo.authagain.auth.ReauthService;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.raphimc.minecraftauth.java.JavaAuthManager;

/**
 * <strong>DeviceCodeScreen</strong><br>
 * Shows info to allow user to login, and returns to the account manager on success.
 */
public class DeviceCodeScreen extends Screen {

	private enum State { STARTING, WAITING, ERROR }

	private final Screen parent;
	/** The account being reauthenticated, or {@code null} when adding a new one. */
	@Nullable
	private final AuthAccount existing;

	private State state = State.STARTING;
	private boolean loginStarted;
	@Nullable
	private DeviceCodePrompt prompt;
	@Nullable
	private String error;

	@Nullable
	private Button copyButton;
	/** Clock time when the copy button should go back from "Copied!" to original label, or 0 when idle. */
	private long copyResetAtMs;

	public DeviceCodeScreen(Screen parent, @Nullable AuthAccount existing) {
		super(Component.translatable("gui.authagain.devicecode.title"));
		this.parent = parent;
		this.existing = existing;
	}

	@Override
	protected void init() {
		if (!loginStarted) {
			loginStarted = true;
			startLogin();
		}

		switch (state) {
			case WAITING -> {
				DeviceCodePrompt p = prompt;
				copyButton = Button.builder(Component.translatable("gui.authagain.devicecode.copy"), btn -> {
					minecraft.keyboardHandler.setClipboard(p.userCode());
					btn.setMessage(Component.translatable("gui.authagain.devicecode.copied"));
					copyResetAtMs = System.currentTimeMillis() + 2000;
					// Defer unfocus until after this callback
					minecraft.execute(() -> setFocused(null));
				}).bounds(width / 2 - 152, height - 52, 100, 20).build();
				addRenderableWidget(copyButton);
				addRenderableWidget(Button.builder(Component.translatable("gui.authagain.devicecode.open"),
						btn -> Util.getPlatform().openUri(p.verificationUri()))
						.bounds(width / 2 - 50, height - 52, 100, 20).build());
				addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
						.bounds(width / 2 + 52, height - 52, 100, 20).build());
			}
			case ERROR -> {
				addRenderableWidget(Button.builder(Component.translatable("gui.authagain.devicecode.retry"), btn -> {
					state = State.STARTING;
					startLogin();
					rebuildWidgets();
				}).bounds(width / 2 - 102, height - 52, 100, 20).build());
				addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
						.bounds(width / 2 + 2, height - 52, 100, 20).build());
			}
			case STARTING -> addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
					.bounds(width / 2 - 100, height - 52, 200, 20).build());
		}
	}

	private void startLogin() {
		prompt = null;
		error = null;
		// The prompt callback and the future both resolve on the auth executor, so
		// propagate every UI change back onto the client thread.
		ReauthService.startDeviceCodeLogin(p -> minecraft.execute(() -> onPrompt(p)))
				.whenComplete((manager, throwable) -> minecraft.execute(() -> onComplete(manager, throwable)));
	}

	private void onPrompt(DeviceCodePrompt received) {
		this.prompt = received;
		this.state = State.WAITING;
		rebuildWidgets();
	}

	private void onComplete(@Nullable JavaAuthManager manager, @Nullable Throwable throwable) {
		if (throwable != null) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Device-code login failed.", throwable);
			this.error = throwable.getCause() != null ? throwable.getCause().getMessage() : throwable.getMessage();
			this.state = State.ERROR;
			rebuildWidgets();
			return;
		}

		AuthAccount account = existing == null
				? ReauthService.toAccount(manager)
				: ReauthService.toAccount(existing, manager);
		AuthAgainMod.globalAccountStore.add(account);
		if (parent instanceof AccountManagerScreen accountManager) {
			accountManager.markValid(account.uuid());
		}
		minecraft.setScreen(parent);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		//? if <1.21 {
		this.renderBackground(g);
		//?} else {
		/*super.render(g, mouseX, mouseY, partialTick);*/
		//?}
		g.drawCenteredString(font, title, width / 2, 40, 0xFFFFFF);

		switch (state) {
			case STARTING -> g.drawCenteredString(font, Component.translatable("gui.authagain.devicecode.starting"),
					width / 2, height / 2, 0xA0A0A0);
			case WAITING -> {
				if (copyResetAtMs != 0 && System.currentTimeMillis() >= copyResetAtMs && copyButton != null) {
					copyButton.setMessage(Component.translatable("gui.authagain.devicecode.copy"));
					copyResetAtMs = 0;
				}
				g.drawCenteredString(font, Component.translatable("gui.authagain.devicecode.instructions"),
						width / 2, height / 2 - 30, 0xFFFFFF);
				g.drawCenteredString(font, Component.literal(prompt.userCode()), width / 2, height / 2 - 12, 0x55FF55);
				g.drawCenteredString(font, Component.literal(prompt.verificationUri()), width / 2, height / 2 + 6, 0xA0A0A0);
				long secondsLeft = Math.max(0, (prompt.expiresAtMs() - System.currentTimeMillis()) / 1000);
				g.drawCenteredString(font, Component.translatable("gui.authagain.devicecode.expires", secondsLeft),
						width / 2, height / 2 + 24, 0xA0A0A0);
			}
			case ERROR -> {
				g.drawCenteredString(font, Component.translatable("gui.authagain.devicecode.failed"),
						width / 2, height / 2 - 10, 0xFF5555);
				if (error != null) {
					g.drawCenteredString(font, Component.literal(error), width / 2, height / 2 + 8, 0xA0A0A0);
				}
			}
		}

		//? if <1.21
		super.render(g, mouseX, mouseY, partialTick);
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
