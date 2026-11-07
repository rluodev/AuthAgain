package dev.rluo.authagain.gui;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.config.AuthAgainConfig;
//? if forge {
import dev.rluo.authagain.forge.AuthAgainConfigForge;
//?} else {
/*import dev.rluo.authagain.neoforge.AuthAgainConfigNeoForge;*/
//?}
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * <strong>AuthAgainConfigScreen</strong><br>
 * Edits the client config.
 */
public final class AuthAgainConfigScreen extends Screen {

	private final Screen parent;
	private boolean persistAccounts;
	private boolean encryptAccounts;
	private boolean saveFailed;

	public AuthAgainConfigScreen(Screen parent) {
		super(Component.translatable("gui.authagain.config.title"));
		this.parent = parent;
		persistAccounts = AuthAgainConfig.persistAccounts;
		encryptAccounts = AuthAgainConfig.encryptAccounts;
	}

	@Override
	protected void init() {
		int left = width / 2 - 100;
		int top = height / 2 - 40;
		addRenderableWidget(CycleButton.onOffBuilder(persistAccounts)
				.withTooltip(value -> Tooltip.create(Component.translatable("gui.authagain.config.persist_accounts.tooltip")))
				.create(left, top, 200, 20, Component.translatable("gui.authagain.config.persist_accounts"),
						(button, value) -> persistAccounts = value));
		addRenderableWidget(CycleButton.onOffBuilder(encryptAccounts)
				.withTooltip(value -> Tooltip.create(Component.translatable("gui.authagain.config.encrypt_accounts.tooltip")))
				.create(left, top + 24, 200, 20, Component.translatable("gui.authagain.config.encrypt_accounts"),
						(button, value) -> encryptAccounts = value));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
				.bounds(left, top + 64, 98, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
				.bounds(left + 102, top + 64, 98, 20).build());
	}

	private void save() {
		try {
			//? if forge {
			AuthAgainConfigForge.save(persistAccounts, encryptAccounts);
			//?} else {
			/*AuthAgainConfigNeoForge.save(persistAccounts, encryptAccounts);*/
			//?}
		} catch (RuntimeException e) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Could not save the client config.", e);
			saveFailed = true;
			return;
		}
		onClose();
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		//? if <1.21
		this.renderBackground(g);
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, height / 2 - 60, 0xFFFFFF);
		if (saveFailed) {
			g.drawCenteredString(font, Component.translatable("gui.authagain.config.save_failed"),
					width / 2, height / 2 + 10, 0xFF5555);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}
}
