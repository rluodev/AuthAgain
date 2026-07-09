package dev.rluo.authagain.auth;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static java.nio.file.StandardCopyOption.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.config.AuthAgainConfig;

/**
 * <strong>AccountStore</strong><br>
 * This class manages {@code config/authagain/accounts.json}.
 * <p>
 * {@code accounts.json} is in the following format so the accounts can survive a restart:
 * <pre>
 * {
 *   "activeUuid": "&lt;localId or null&gt;",
 *   "accounts": [ { "id", "displayName", "uuid", "session", "lastRefreshedAt" }, ... ]
 * }
 * </pre>
 */
public class AccountStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final String _accountStoreLocation;
	private List<AuthAccount> _accounts = new ArrayList<>();
	private volatile UUID _activeUuid;

	public AccountStore(String accountStoreLocation) {
		_accountStoreLocation = accountStoreLocation;
		load();
	}

	/**
	 * Deserializes {@code accounts.json} from disk into the
	 * {@link #_accounts} list and {@link #_activeUuid} pointer.
	 * <p>
	 * @implNote An empty/unparseable file is treated as non-existent and we
	 * just create a new store file so that we don't impede startup.
	 */
	public synchronized void load() {
		Path path = Paths.get(_accountStoreLocation);
		if (!Files.exists(path)) {
			_accounts = new ArrayList<>();
			_activeUuid = null;
			return;
		} else if (!AuthAgainConfig.persistAccounts) {
			_accounts = new ArrayList<>();
			_activeUuid = null;
			try {
				Files.delete(path);
			} catch (IOException e) {
				AuthAgainMod.LOGGER.error("AuthAgain could not delete {} when persistAccounts was set to false.", path, e);
			}
			return;
		}

		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null) {
				throw new IllegalStateException("accounts.json is empty or not a JSON object");
			}

			List<AuthAccount> parsed = new ArrayList<>();
			if (root.has("accounts") && root.get("accounts").isJsonArray()) {
				for (JsonElement element : root.getAsJsonArray("accounts")) {
					parsed.add(fromJson(element.getAsJsonObject()));
				}
			}

			_accounts = parsed;
			_activeUuid = (root.has("activeUuid") && !root.get("activeUuid").isJsonNull())
					? UUID.fromString(root.get("activeUuid").getAsString())
					: null;
			AuthAgainMod.LOGGER.info("[AuthAgain] Read accounts from disk");
		} catch (Exception e) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Failed to read {}, recreating accounts.json", path, e);
			_accounts = new ArrayList<>();
			_activeUuid = null;
		}
	}

	/**
	 * Reserializes the in-memory account list and active account info back to
	 * {@code accounts.json}.
	 * <p>
	 * We don't save if {@link AuthAgainConfig#persistAccounts} is false.
	 */
	public synchronized void save() {
		if (!AuthAgainConfig.persistAccounts) {
			return;
		}

		Path path = Paths.get(_accountStoreLocation);
		try {
			Path parent = path.getParent();
			if (parent == null) {
				AuthAgainMod.LOGGER.error("[AuthAgain] Could not find a parent folder for the account store file. Accounts will not be persisted. Please report this error to the mod maintainer.");
				return;
			}
			Files.createDirectories(parent);
			Path tmpPath = parent.resolve("accounts.json.tmp");

			JsonArray accounts = new JsonArray();
			for (AuthAccount account : _accounts) {
				accounts.add(toJson(account));
			}

			JsonObject root = new JsonObject();
			root.addProperty("activeUuid", _activeUuid == null ? null : _activeUuid.toString());
			root.add("accounts", accounts);

			try (Writer writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
				AuthAgainMod.LOGGER.info("[AuthAgain] Wrote accounts to temporary file accounts.json.tmp.");
			}
			Files.move(tmpPath, path, ATOMIC_MOVE, REPLACE_EXISTING);
			AuthAgainMod.LOGGER.info("[AuthAgain] Moved temporary file into accounts.json.");
		} catch (IOException e) {
			AuthAgainMod.LOGGER.error("[AuthAgain] Failed to write {}.", path, e);
		}
	}

	public synchronized AuthAccount findByUuid(UUID uuid) {
		for (AuthAccount a : _accounts) {
			if (a.uuid().equals(uuid)) {
				return a;
			}
		}
		return null;
	}

	public synchronized AuthAccount findById(String id) {
		for (AuthAccount a : _accounts) {
			if (a.id().equals(id)) {
				return a;
			}
		}
		return null;
	}

	public synchronized void add(AuthAccount account) {
		if (findByUuid(account.uuid()) != null) {
			replace(account);
		} else {
			_accounts.add(account);
			save();
		}
	}

	public synchronized void replace(AuthAccount updated) {
		int index = _accounts.indexOf(findByUuid(updated.uuid()));
		if (index != -1) {
			_accounts.set(index, updated);
			save();
		} else {
			add(updated);
		}
	}

	public synchronized AuthAccount remove(String id) {
		int index = _accounts.indexOf(findById(id));
		if (index != -1) {
			AuthAccount removed = _accounts.remove(index);
			save();
			return removed;
		}
		return null;
	}

	public synchronized AuthAccount get(String id) {
		return findById(id);
	}

	public synchronized List<AuthAccount> list() {
		return new ArrayList<AuthAccount>(_accounts);
	}

	public UUID getActiveUuid() {
		return _activeUuid;
	}

	public synchronized void setActiveUuid(UUID uuid) {
		if (findByUuid(uuid) != null) {
			_activeUuid = uuid;
			save();
		}
	}

	private static JsonObject toJson(AuthAccount account) {
		JsonObject json = new JsonObject();
		json.addProperty("id", account.id());
		json.addProperty("displayName", account.displayName());
		json.addProperty("uuid", account.uuid().toString());
		json.addProperty("xuid", account.xuid());
		json.add("session", account.session());
		json.addProperty("lastRefreshedAt", account.lastRefreshedAt());
		if (account.skinUrl() != null) {
			json.addProperty("skinUrl", account.skinUrl());
		}
		return json;
	}

	private static AuthAccount fromJson(JsonObject json) {
		return new AuthAccount(
			json.get("id").getAsString(),
			json.get("displayName").getAsString(),
			UUID.fromString(json.get("uuid").getAsString()),
			json.has("xuid") && !json.get("xuid").isJsonNull() ? json.get("xuid").getAsString() : "",
			json.getAsJsonObject("session"),
			json.get("lastRefreshedAt").getAsLong(),
			json.has("skinUrl") && !json.get("skinUrl").isJsonNull() ? json.get("skinUrl").getAsString() : null
		);
	}
}
