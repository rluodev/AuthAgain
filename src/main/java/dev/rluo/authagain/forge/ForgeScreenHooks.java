//? if forge {
package dev.rluo.authagain.forge;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

import dev.rluo.authagain.client.ScreenHooks;

/**
 * Bridges Forge's screen events to {@link ScreenHooks}
 */
public final class ForgeScreenHooks {

	private ForgeScreenHooks() {
	}

	public static void register() {
		MinecraftForge.EVENT_BUS.addListener(ForgeScreenHooks::onInit);
		MinecraftForge.EVENT_BUS.addListener(ForgeScreenHooks::onRender);
	}

	private static void onInit(final ScreenEvent.Init.Post event) {
		ScreenHooks.onScreenInit(event.getScreen(), event::addListener);
	}

	private static void onRender(final ScreenEvent.Render.Post event) {
		ScreenHooks.onScreenRender(event.getScreen(), event.getGuiGraphics());
	}
}
//?}
