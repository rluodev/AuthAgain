package dev.rluo.authagain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;

import org.joml.Vector2ic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.auth.AccountStore.UnencryptedReason;
import dev.rluo.authagain.support.ModBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;

class ScreenHooksTest {

	private Minecraft client;
	private Font font;
	private GuiGraphics graphics;
	private JoinMultiplayerScreen screen;
	private AccountStore store;

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() throws Exception {
		client = mock(Minecraft.class);
		font = mock(Font.class);
		graphics = mock(GuiGraphics.class);
		screen = mock(JoinMultiplayerScreen.class);
		screen.width = 320;
		screen.height = 240;
		Field fontField = Minecraft.class.getDeclaredField("font");
		fontField.setAccessible(true);
		fontField.set(client, font);
		when(font.width(any(FormattedText.class))).thenReturn(100);
		User user = mock(User.class);
		when(user.getName()).thenReturn("Anixty");
		when(client.getUser()).thenReturn(user);
		store = mock(AccountStore.class);
		AuthAgainMod.globalAccountStore = store;
	}

	@AfterEach
	void tearDown() {
		AuthAgainMod.globalAccountStore = null;
	}

	private void render(int mouseX, int mouseY) {
		try (MockedStatic<Minecraft> statics = mockStatic(Minecraft.class)) {
			statics.when(Minecraft::getInstance).thenReturn(client);
			ScreenHooks.onScreenRender(screen, graphics, mouseX, mouseY);
		}
	}

	@Test
	void unencryptedAccountDrawsRedNameAndHoverWarning() {
		when(store.getUnencryptedReason()).thenReturn(UnencryptedReason.DISABLED);
		List<FormattedCharSequence> lines = List.of(FormattedCharSequence.EMPTY);
		when(font.split(any(FormattedText.class), eq(250))).thenReturn(lines);

		render(250, 8);

		ArgumentCaptor<Component> name = ArgumentCaptor.forClass(Component.class);
		verify(graphics).drawString(eq(font), name.capture(), eq(215), eq(5), eq(0xFF5555));
		TranslatableContents contents = (TranslatableContents) name.getValue().getContents();
		assertThat(contents.getKey()).isEqualTo("gui.authagain.storage.unencrypted");
		assertThat(contents.getArgs()).containsExactly("Anixty");
		ArgumentCaptor<FormattedText> tooltip = ArgumentCaptor.forClass(FormattedText.class);
		verify(font).split(tooltip.capture(), eq(250));
		verify(graphics).renderTooltip(eq(font), eq(lines), any(ClientTooltipPositioner.class), eq(250), eq(8));
		TranslatableContents warning = (TranslatableContents) ((Component) tooltip.getValue()).getContents();
		assertThat(warning.getKey()).isEqualTo("gui.authagain.storage.unencrypted.tooltip");
		Component reason = (Component) warning.getArgs()[0];
		assertThat(((TranslatableContents) reason.getContents()).getKey())
				.isEqualTo("gui.authagain.storage.unencrypted.disabled");
	}

	@Test
	void warningIsNotShownWhenMouseIsOutsideTheName() {
		when(store.getUnencryptedReason()).thenReturn(UnencryptedReason.NO_DEVICE_ID);

		render(100, 50);

		verify(graphics, never()).renderTooltip(any(Font.class), anyList(),
				any(ClientTooltipPositioner.class), eq(100), eq(50));
	}

	@Test
	void encryptedAccountKeepsTheOriginalNameAndColor() {
		render(250, 8);

		ArgumentCaptor<Component> name = ArgumentCaptor.forClass(Component.class);
		verify(graphics).drawString(eq(font), name.capture(), eq(215), eq(5), eq(0xFFFFFF));
		assertThat(name.getValue().getString()).isEqualTo("Anixty");
		verify(graphics, never()).renderTooltip(any(Font.class), anyList(),
				any(ClientTooltipPositioner.class), eq(250), eq(8));
	}

	@Test
	void warningPositionStaysInsideTheWindowAtAllEdges() {
		when(store.getUnencryptedReason()).thenReturn(UnencryptedReason.DISABLED);
		List<FormattedCharSequence> lines = List.of(FormattedCharSequence.EMPTY);
		when(font.split(any(FormattedText.class), eq(250))).thenReturn(lines);
		render(250, 8);
		ArgumentCaptor<ClientTooltipPositioner> positioner = ArgumentCaptor.forClass(ClientTooltipPositioner.class);
		verify(graphics).renderTooltip(eq(font), eq(lines), positioner.capture(), eq(250), eq(8));

		for (int mouseX : new int[] { 0, 319 }) {
			for (int mouseY : new int[] { 0, 239 }) {
				Vector2ic position = positioner.getValue().positionTooltip(320, 240, mouseX, mouseY, 250, 100);
				assertThat(position.x()).isBetween(5, 65);
				assertThat(position.y()).isBetween(5, 135);
			}
		}
		assertThat(positioner.getValue().positionTooltip(320, 240, 250, 8, 250, 100).y()).isEqualTo(5);
	}

	@Test
	void tallWarningUsesTheAvailableWindowWidth() {
		when(store.getUnencryptedReason()).thenReturn(UnencryptedReason.DISABLED);
		when(font.split(any(FormattedText.class), eq(250)))
				.thenReturn(Collections.nCopies(24, FormattedCharSequence.EMPTY));
		List<FormattedCharSequence> lines = Collections.nCopies(20, FormattedCharSequence.EMPTY);
		when(font.split(any(FormattedText.class), eq(310))).thenReturn(lines);

		render(250, 8);

		verify(font).split(any(FormattedText.class), eq(310));
		verify(graphics).renderTooltip(eq(font), eq(lines), any(ClientTooltipPositioner.class), eq(250), eq(8));
	}

	@Test
	void narrowWindowKeepsTheTooltipWidthPositive() {
		screen.width = 8;
		when(store.getUnencryptedReason()).thenReturn(UnencryptedReason.DISABLED);
		List<FormattedCharSequence> lines = List.of(FormattedCharSequence.EMPTY);
		when(font.split(any(FormattedText.class), eq(1))).thenReturn(lines);

		render(0, 8);

		verify(font).split(any(FormattedText.class), eq(1));
		verify(graphics).renderTooltip(eq(font), eq(lines), any(ClientTooltipPositioner.class), eq(0), eq(8));
	}
}
