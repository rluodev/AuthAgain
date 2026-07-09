package dev.rluo.authagain.config;

/**
 * <strong>AuthAgainConfig</strong><br>
 * Loader-agnostic holder for config values. Each loader owns its config spec
 * and writes the resolved values here on load.
 */
public final class AuthAgainConfig {

	/** Whether to save accounts to disk. */
	public static volatile boolean persistAccounts = true;

	private AuthAgainConfig() {
	}
}
