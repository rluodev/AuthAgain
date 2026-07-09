//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

import dev.rluo.authagain.client.ScreenHooks;

/^*
 * Bridges NeoForge's screen events to {@link ScreenHooks}
 ^/
public final class NeoForgeScreenHooks {

	private NeoForgeScreenHooks() {
	}

	public static void register() {
		NeoForge.EVENT_BUS.addListener(NeoForgeScreenHooks::onInit);
		NeoForge.EVENT_BUS.addListener(NeoForgeScreenHooks::onRender);
	}

	private static void onInit(final ScreenEvent.Init.Post event) {
		ScreenHooks.onScreenInit(event.getScreen(), event::addListener);
	}

	private static void onRender(final ScreenEvent.Render.Post event) {
		ScreenHooks.onScreenRender(event.getScreen(), event.getGuiGraphics());
	}
}
*///?}
