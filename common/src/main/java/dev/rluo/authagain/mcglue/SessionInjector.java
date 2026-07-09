package dev.rluo.authagain.mcglue;

import java.util.Optional;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AuthAccount;

/**
 * <strong>SessionInjector</strong><br>
 * Turns a stored {@link AuthAccount} into a Minecraft {@link User} and swaps it
 * into the running client so the player is reauthenticated without a restart.
 */
public class SessionInjector {

	/**
	 * Builds a {@link User} from a stored account.
	 * <p>
	 * The access token is read from the account's serialized session, so the
	 * account must have been freshly refreshed (its {@code xuid} and session are
	 * both populated during login/refresh).
	 */
	public static User buildUser(AuthAccount account) {
		return new User(account.displayName(), account.uuid().toString(), accessToken(account),
				Optional.of(account.xuid()), Optional.empty(), User.Type.MSA);
	}

	/** The Minecraft access token stored in the account's session, or null if absent. */
	public static String accessToken(AuthAccount account) {
		JsonObject session = account.session();
		if (session == null || !session.has("minecraftToken")) {
			return null;
		}
		JsonObject token = session.getAsJsonObject("minecraftToken");
		return token.has("token") ? token.get("token").getAsString() : null;
	}

	/**
	 * Replaces the active session on the running client.
	 * <p>
	 * {@code Minecraft.user} is made public and non-final by our access transformer.
	 * <p>
	 * Note: this swaps the {@link User} the client uses to join servers. Services
	 * that are built at startup (chat reporting, telemetry, etc) keep the old token until Minecraft restarts.
	 */
	public static void apply(User user) {
		Minecraft.getInstance().user = user;
		AuthAgainMod.LOGGER.info("[AuthAgain] Swapped active session to {}.", user.getName());
	}

	/**
	 * Convenience: build a {@link User} from the account and apply it.
	 */
	public static void inject(AuthAccount account) {
		apply(buildUser(account));
	}
}
