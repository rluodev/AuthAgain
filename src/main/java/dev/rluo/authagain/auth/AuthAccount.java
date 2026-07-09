package dev.rluo.authagain.auth;

import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonObject;

/**
 * <strong>AuthAccount</strong><br>
 * Immutable record for one stored account.
 * <p>
 * {@code id} is a stable local identifier for this record
 * <p>
 * {@code displayName} is used to display a friendly name to the user
 * <p>
 * {@code uuid} tracks the remote minecraft account by uuid
 * <p>
 * {@code xuid} is the Xbox user ID, needed to build the in-game session
 * <p>
 * {@code session} is the JSON produced by {@link ReauthService#serialize}
 * <p>
 * {@code lastRefreshedAt} when (in millis) the record was last updated
 * <p>
 * {@code skinUrl} is a cached texture URL for the account's head, or {@code null}
 * if it has not been resolved yet, so the account screen can skip re-querying the
 * session server on every open.
 */
public record AuthAccount(String id, String displayName, UUID uuid, String xuid, JsonObject session, long lastRefreshedAt, String skinUrl) {
	public AuthAccount {
		Objects.requireNonNull(id);
		Objects.requireNonNull(displayName);
		Objects.requireNonNull(uuid);
		Objects.requireNonNull(xuid);
		Objects.requireNonNull(session);
	}

	public AuthAccount(String id, String displayName, UUID uuid, String xuid, JsonObject session, long lastRefreshedAt) {
		this(id, displayName, uuid, xuid, session, lastRefreshedAt, null);
	}

	/**
	 * Generates the id and current time then calls the base constructor.
	 */
	public AuthAccount(String displayName, UUID uuid, String xuid, JsonObject session) {
		this(UUID.randomUUID().toString(), displayName, uuid, xuid, session, System.currentTimeMillis(), null);
	}

	/**
	 * Returns a copy with an updated display name, xuid, session, and timestamp.
	 * <p>
	 * The display name is refreshed alongside the session because a Minecraft username can change between logins.
	 * @return a new {@link AuthAccount} so a silent refresh can replace the stored account without mutation.
	 */
	public AuthAccount withSession(String newDisplayName, String newXuid, JsonObject newSession, long newLastRefreshedAt) {
		return new AuthAccount(id, newDisplayName, uuid, newXuid, newSession, newLastRefreshedAt, null);
	}

	/**
	 * Returns a copy carrying the cached skin texture URL.
	 */
	public AuthAccount withSkinUrl(String newSkinUrl) {
		return new AuthAccount(id, displayName, uuid, xuid, session, lastRefreshedAt, newSkinUrl);
	}
}