package dev.rluo.authagain;

import com.mojang.logging.LogUtils;
import dev.rluo.authagain.auth.AccountStore;

import org.slf4j.Logger;

/**
 * <strong>AuthAgainMod</strong><br>
 * Shared state for the mod<br>
 * The actual mod entrypoints live in their individual loader
 * modules ({@code AuthAgainForge}, {@code AuthAgainNeoForge})
 */
public final class AuthAgainMod {

	public static final String MODID = "authagain";
	public static final Logger LOGGER = LogUtils.getLogger();
	public static AccountStore globalAccountStore = null;

	private AuthAgainMod() {
	}
}
