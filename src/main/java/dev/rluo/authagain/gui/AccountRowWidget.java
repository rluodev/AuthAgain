package dev.rluo.authagain.gui;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AuthAccount;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;

/**
 * <strong>AccountRowWidget</strong><br>
 * Represents an account: username, a Valid/Expired status, and a green
 * checkmark on the active account.
 */
public class AccountRowWidget extends ObjectSelectionList.Entry<AccountRowWidget> {

	private static final int HEAD_SIZE = 16;
	private static final int GREEN = 0x55FF55;
	private static final int RED = 0xFF5555;
	private static final int GRAY = 0xA0A0A0;
	private static final Component CHECK = Component.literal("✔");

	private final AccountManagerScreen screen;
	private final AuthAccount account;

	public AccountRowWidget(AccountManagerScreen screen, AuthAccount account) {
		this.screen = screen;
		this.account = account;
	}

	public AuthAccount account() {
		return account;
	}

	private boolean isActive() {
		return account.uuid().equals(AuthAgainMod.globalAccountStore.getActiveUuid());
	}

	@Override
	public void render(GuiGraphics g, int index, int top, int left, int rowWidth, int rowHeight,
			int mouseX, int mouseY, boolean hovering, float partialTick) {
		Font font = screen.getFont();
		PlayerFaceRenderer.draw(g, screen.headFor(account.uuid()),
				left + 2, top + (rowHeight - HEAD_SIZE) / 2, HEAD_SIZE);

		int textY = top + (rowHeight - 8) / 2;
		g.drawString(font, account.displayName(), left + HEAD_SIZE + 8, textY, 0xFFFFFF);

		int rightEdge = left + rowWidth - 4;
		if (isActive()) {
			int checkX = rightEdge - font.width(CHECK);
			g.drawString(font, CHECK, checkX, textY, GREEN);
			if (mouseX >= checkX && mouseX <= rightEdge && mouseY >= top && mouseY < top + rowHeight) {
				screen.setHoverTooltip(Component.translatable("gui.authagain.status.active"));
			}
			rightEdge = checkX - 6;
		}

		Component status;
		int statusColor;
		switch (screen.statusFor(account.uuid())) {
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
		g.drawString(font, status, rightEdge - font.width(status), textY, statusColor);
	}

	// Returning true allows the list to mark this row as the selected one.
	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return true;
	}

	@Override
	public Component getNarration() {
		return Component.literal(account.displayName());
	}
}
