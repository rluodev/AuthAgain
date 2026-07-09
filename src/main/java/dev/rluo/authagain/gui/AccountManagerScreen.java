package dev.rluo.authagain.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.authlib.minecraft.MinecraftSessionService;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AuthAccount;
import dev.rluo.authagain.auth.ReauthService;
import dev.rluo.authagain.mcglue.SessionInjector;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.raphimc.minecraftauth.java.JavaAuthManager;

/**
 * <strong>AccountManagerScreen</strong><br>
 * Lists stored accounts above a footer with buttons.
 * Every account action runs on the auth thread and sets the whole screen {@link #busy}
 * to stop player from doing weird other actions that could break stuff.
 */
public class AccountManagerScreen extends Screen {

	private final Screen parent;
	private AccountList list;
	private Button setActiveButton;
	private Button reauthButton;
	private Button removeButton;
	private volatile boolean busy;
	/** For confirming removing an account. */
	private boolean removeArmed;
	@Nullable
	private AuthAccount lastSelected;
	/** Cached head skins by account UUID */
	private final Map<UUID, ResourceLocation> heads = new HashMap<>();
	/** Token validity by account UUID */
	private final Map<UUID, ReauthService.TokenStatus> statuses = new HashMap<>();
	/** Tooltip to draw on hover. */
	@Nullable
	private Component hoverTooltip;

	public AccountManagerScreen(Screen parent) {
		super(Component.translatable("gui.authagain.accounts.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		list = new AccountList(minecraft);
		for (AuthAccount account : AuthAgainMod.globalAccountStore.list()) {
			list.addEntry(new AccountRowWidget(this, account));
			loadHead(account);
			checkValidity(account);
		}
		addWidget(list);

		int barX = width / 2 - 154;
		int topY = height - 52;
		int bottomY = height - 28;
		setActiveButton = addRenderableWidget(Button.builder(Component.translatable("gui.authagain.row.setactive"),
				btn -> withSelection(this::setActive)).bounds(barX, topY, 100, 20).build());
		reauthButton = addRenderableWidget(Button.builder(Component.translatable("gui.authagain.row.reauth"),
				btn -> withSelection(this::reauth)).bounds(barX + 104, topY, 100, 20).build());
		removeButton = addRenderableWidget(Button.builder(Component.translatable("gui.authagain.row.remove"),
				btn -> withSelection(this::onRemoveClicked)).bounds(barX + 208, topY, 100, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.authagain.add"),
				btn -> minecraft.setScreen(new DeviceCodeScreen(this, null)))
				.bounds(barX, bottomY, 152, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
				.bounds(barX + 156, bottomY, 152, 20).build());
	}

	@Nullable
	private AuthAccount selectedAccount() {
		AccountRowWidget selected = list.getSelected();
		return selected == null ? null : selected.account();
	}

	private void withSelection(java.util.function.Consumer<AuthAccount> action) {
		AuthAccount account = selectedAccount();
		if (account != null) {
			action.accept(account);
		}
	}

	private void onRemoveClicked(AuthAccount account) {
		if (removeArmed) {
			removeArmed = false;
			remove(account);
		} else {
			removeArmed = true;
		}
	}

	/** Refreshes an account's session then injects it and marks it active. */
	void setActive(AuthAccount account) {
		runAction(account, (refreshed, manager) -> {
			AuthAgainMod.globalAccountStore.replace(refreshed);
			AuthAgainMod.globalAccountStore.setActiveUuid(refreshed.uuid());
			SessionInjector.inject(refreshed);
		});
	}

	/** Refreshes an account's session and stores it. */
	void reauth(AuthAccount account) {
		runAction(account, (refreshed, manager) -> AuthAgainMod.globalAccountStore.replace(refreshed));
	}

	void remove(AuthAccount account) {
		AuthAgainMod.globalAccountStore.remove(account.id());
		rebuildWidgets();
	}

	private interface OnRefreshed {
		void accept(AuthAccount refreshed, JavaAuthManager manager);
	}

	/**
	 * Refreshes {@code account}, applies {@code onSuccess}, and
	 * rebuilds the screen. If the refresh token is expired, then it forces a relog.
	 */
	private void runAction(AuthAccount account, OnRefreshed onSuccess) {
		if (busy) {
			return;
		}
		busy = true;
		ReauthService.silentRefresh(account.session()).whenComplete((manager, throwable) -> minecraft.execute(() -> {
			busy = false;
			if (throwable != null) {
				minecraft.setScreen(new DeviceCodeScreen(this, account));
				return;
			}
			AuthAccount refreshed = ReauthService.toAccount(account, manager);
			onSuccess.accept(refreshed, manager);
			statuses.put(refreshed.uuid(), ReauthService.TokenStatus.VALID);
			rebuildWidgets();
		}));
	}

	Font getFont() {
		return font;
	}

	/** Validates account tokens when the screen loads. */
	private void checkValidity(AuthAccount account) {
		UUID uuid = account.uuid();
		if (statuses.containsKey(uuid)) {
			return;
		}
		statuses.put(uuid, ReauthService.TokenStatus.UNKNOWN);
		ReauthService.validate(SessionInjector.accessToken(account))
				.whenComplete((status, throwable) -> minecraft.execute(
						() -> statuses.put(uuid, throwable != null ? ReauthService.TokenStatus.UNKNOWN : status)));
	}

	ReauthService.TokenStatus statusFor(UUID uuid) {
		return statuses.getOrDefault(uuid, ReauthService.TokenStatus.UNKNOWN);
	}

	/** Marks an account valid after a fresh login/refresh, since the new token is known good. */
	void markValid(UUID uuid) {
		statuses.put(uuid, ReauthService.TokenStatus.VALID);
	}

	void setHoverTooltip(Component tooltip) {
		this.hoverTooltip = tooltip;
	}

	/**
	 * Fetches skin for {@code account} unless one is already cached, storing a
	 * placeholder to render until the real one resolves.
	 * <p>
	 * We resolve textures from the account's own profile rather than through
	 * {@link net.minecraft.client.resources.SkinManager#registerSkins}.
	 */
	private void loadHead(AuthAccount account) {
		UUID uuid = account.uuid();
		if (heads.containsKey(uuid)) {
			return;
		}
		heads.put(uuid, DefaultPlayerSkin.getDefaultSkin(uuid));

		if (account.skinUrl() != null) {
			heads.put(uuid, registerSkin(account.skinUrl()));
			return;
		}

		MinecraftSessionService sessionService = minecraft.getMinecraftSessionService();
		GameProfile profile = new GameProfile(uuid, account.displayName());
		CompletableFuture.supplyAsync(() -> {
			try {
				GameProfile filled = sessionService.fillProfileProperties(profile, false);
				return sessionService.getTextures(filled, false).get(MinecraftProfileTexture.Type.SKIN);
			} catch (Exception e) {
				AuthAgainMod.LOGGER.debug("[AuthAgain] Could not resolve skin for {}.", uuid, e);
				return null;
			}
		}, Util.backgroundExecutor()).thenAcceptAsync(skin -> {
			if (skin != null) {
				heads.put(uuid, registerSkin(skin.getUrl()));
				cacheSkinUrl(uuid, skin.getUrl());
			}
		}, minecraft);
	}

	/** Registers a head texture from its Mojang texture URL, must run on the render thread. */
	private ResourceLocation registerSkin(String skinUrl) {
		MinecraftProfileTexture texture = new MinecraftProfileTexture(skinUrl, java.util.Map.of());
		return minecraft.getSkinManager().registerTexture(texture, MinecraftProfileTexture.Type.SKIN);
	}

	/** Stores a resolved skin URL onto the account so later opens skip the fetch. */
	private void cacheSkinUrl(UUID uuid, String skinUrl) {
		AuthAccount current = AuthAgainMod.globalAccountStore.findByUuid(uuid);
		if (current != null && !skinUrl.equals(current.skinUrl())) {
			AuthAgainMod.globalAccountStore.replace(current.withSkinUrl(skinUrl));
		}
	}

	/** The cached head skin for an account, or the default skin while it loads. */
	ResourceLocation headFor(UUID uuid) {
		return heads.getOrDefault(uuid, DefaultPlayerSkin.getDefaultSkin(uuid));
	}

	/**
	 * Refreshes the footer to match the selection. The top buttons
	 * enable only when a row is selected. Remove has a confirmation step and
	 * set active is disabled if the selected row is already active
	 */
	private void updateButtons() {
		AuthAccount selected = selectedAccount();
		if (selected != lastSelected) {
			lastSelected = selected;
			removeArmed = false;
		}
		boolean actionable = selected != null && !busy;
		setActiveButton.active = actionable && !selected.uuid().equals(AuthAgainMod.globalAccountStore.getActiveUuid());
		reauthButton.active = actionable;
		removeButton.active = actionable;
		removeButton.setMessage(Component.translatable(
				removeArmed ? "gui.authagain.row.remove.confirm" : "gui.authagain.row.remove"));
		removeButton.setTooltip(removeArmed
				? Tooltip.create(Component.translatable("gui.authagain.row.remove.confirm.tooltip"))
				: null);
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		updateButtons();
		this.renderBackground(g);
		hoverTooltip = null;
		list.render(g, mouseX, mouseY, partialTick);
		super.render(g, mouseX, mouseY, partialTick);
		g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		if (AuthAgainMod.globalAccountStore.list().isEmpty()) {
			g.drawCenteredString(font, Component.translatable("gui.authagain.accounts.empty"),
					width / 2, height / 2, 0xA0A0A0);
		}
		if (hoverTooltip != null) {
			g.renderTooltip(font, hoverTooltip, mouseX, mouseY);
		}
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	/** Scrolling container of accounts. */
	private class AccountList extends ObjectSelectionList<AccountRowWidget> {
		AccountList(Minecraft mc) {
			super(mc, AccountManagerScreen.this.width, AccountManagerScreen.this.height, 32,
					AccountManagerScreen.this.height - 64, 24);
			setRenderBackground(false);
		}

		@Override
		protected int addEntry(AccountRowWidget entry) {
			return super.addEntry(entry);
		}

		@Override
		public int getRowWidth() {
			return 300;
		}

		@Override
		protected int getScrollbarPosition() {
			return AccountManagerScreen.this.width / 2 + 152;
		}
	}
}
