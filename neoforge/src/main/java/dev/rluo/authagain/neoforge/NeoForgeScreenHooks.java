package dev.rluo.authagain.neoforge;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import dev.rluo.authagain.client.ScreenHooks;

/**
 * Bridges NeoForge's screen events to {@link ScreenHooks}
 */
public final class NeoForgeScreenHooks {

	private NeoForgeScreenHooks() {
	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(NeoForgeScreenHooks::onInit);
		MinecraftForge.EVENT_BUS.addListener(NeoForgeScreenHooks::onRender);
	}

	private static void onInit(final ScreenEvent.Init.Post event) {
		ScreenHooks.onScreenInit(event.getScreen(), event::addListener);
	}

	private static void onRender(final ScreenEvent.Render.Post event) {
		ScreenHooks.onScreenRender(event.getScreen(), event.getGuiGraphics());
	}
}
