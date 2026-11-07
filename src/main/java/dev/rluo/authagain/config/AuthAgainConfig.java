package dev.rluo.authagain.config;

/**
 * <strong>AuthAgainConfig</strong><br>
 * Loader-agnostic holder for config values. Each loader owns its config spec
 * and writes the resolved values here on load.
 */
public final class AuthAgainConfig {

	/** Whether to save accounts to disk. */
	public static volatile boolean persistAccounts = true;
	/** Whether to encrypt saved accounts using this device's identity. */
	public static volatile boolean encryptAccounts = true;

	private AuthAgainConfig() {
	}
}
