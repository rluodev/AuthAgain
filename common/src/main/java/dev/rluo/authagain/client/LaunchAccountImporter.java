package dev.rluo.authagain.client;

import java.util.UUID;

import com.google.gson.JsonObject;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.auth.AuthAccount;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * <strong>LaunchAccountImporter</strong><br>
 * Imports the account the game launched with into the store as a token-only
 * entry. The launcher never exposes the Microsoft refresh token, so this entry
 * cannot be silently refreshed; it stays usable until the access token expires.
 */
public final class LaunchAccountImporter {

	private static boolean imported;

	private LaunchAccountImporter() {
	}

	public static synchronized void importIfNeeded() {
		if (imported) {
			return;
		}
		imported = true;

		AccountStore store = AuthAgainMod.globalAccountStore;
		if (store == null) {
			return;
		}

		User user = Minecraft.getInstance().getUser();
		if (user == null || user.getType() != User.Type.MSA) {
			return;
		}

		UUID uuid;
		try {
			uuid = user.getProfileId();
		} catch (Exception e) {
			AuthAgainMod.LOGGER.warn("[AuthAgain] Could not parse the launch account UUID; skipping import.", e);
			return;
		}
		if (uuid == null) {
			return;
		}

		// Don't clobber an existing full account (with a refresh token) with a token-only stub.
		if (store.findByUuid(uuid) == null) {
			store.add(new AuthAccount(user.getName(), uuid, user.getXuid().orElse(""), stubSession(user)));
			AuthAgainMod.LOGGER.info("[AuthAgain] Imported the launch account {}.", user.getName());
		}
		store.setActiveUuid(uuid);
	}

	private static JsonObject stubSession(User user) {
		JsonObject minecraftToken = new JsonObject();
		minecraftToken.addProperty("token", user.getAccessToken());
		JsonObject session = new JsonObject();
		session.add("minecraftToken", minecraftToken);
		return session;
	}
}
