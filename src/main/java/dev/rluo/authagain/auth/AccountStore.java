package dev.rluo.authagain.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.config.AuthAgainConfig;

/**
 * <strong>AccountStore</strong><br>
 * Manages device-bound accounts in {@code config/authagain}, with legacy JSON
 * support when encryption is disabled or unavailable.
 * <p>
 * The decrypted account document is in the following format:
 * <pre>
 * {
 *   "activeUuid": "&lt;localId or null&gt;",
 *   "accounts": [ { "id", "displayName", "uuid", "session", "lastRefreshedAt" }, ... ]
 * }
 * </pre>
 */
public class AccountStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long MAX_STORAGE_REVISION = Long.MAX_VALUE - 1;

	private final EncryptedAccountStorage _storage;
	private List<AuthAccount> _accounts = new ArrayList<>();
	private volatile UUID _activeUuid;
	private long _storageRevision;
	private UnencryptedReason _unencryptedReason;

	public enum UnencryptedReason {
		DISABLED, NO_DEVICE_ID, ENCRYPTION_FAILED, WRITE_FAILED, LEGACY_FILES
	}

	public AccountStore(String accountStoreLocation) {
		this(Paths.get(accountStoreLocation), new DeviceKey());
	}

	AccountStore(Path accountStoreLocation, List<DeviceIdentity> identities) {
		this(accountStoreLocation, new DeviceKey(identities));
	}

	private AccountStore(Path accountStoreLocation, DeviceKey keys) {
		_storage = new EncryptedAccountStorage(accountStoreLocation, keys);
		load();
	}

	/**
	 * Applies changes to account persistence while keeping the current session in memory.
	 */
	public synchronized void applyConfig(boolean persistAccounts, boolean encryptAccounts) {
		boolean wasPersisting = AuthAgainConfig.persistAccounts;
		if (wasPersisting == persistAccounts && AuthAgainConfig.encryptAccounts == encryptAccounts) {
			return;
		}
		AuthAgainConfig.persistAccounts = persistAccounts;
		AuthAgainConfig.encryptAccounts = encryptAccounts;
		if (persistAccounts) {
			save();
		} else if (wasPersisting) {
			clearSavedAccounts();
		}
	}

	/**
	 * Loads persisted accounts into
	 * {@link #_accounts} and {@link #_activeUuid}.
	 * <p>
	 * Removes legacy JSON only after a verified encrypted write.
	 */
	public synchronized void load() {
		_accounts = new ArrayList<>();
		_activeUuid = null;
		_storageRevision = 0;
		_unencryptedReason = null;
		if (!AuthAgainConfig.persistAccounts) {
			clearSavedAccounts();
			return;
		}

		boolean readEncrypted = false;
		try {
			byte[] document = _storage.read();
			if (document != null) {
				readAccounts(document, false);
				readEncrypted = true;
			}
		} catch (Exception e) {
			_storage.markUnreadable();
			logStorageFailure("read encrypted accounts", e);
		}

		boolean readLegacy = false;
		try {
			for (byte[] document : _storage.readLegacy()) {
				try {
					readAccounts(document, true);
					readLegacy = true;
				} catch (Exception e) {
					logStorageFailure("read legacy accounts", e);
				}
			}
		} catch (Exception e) {
			logStorageFailure("read legacy accounts", e);
		}
		if (AuthAgainConfig.encryptAccounts ? readLegacy : readEncrypted) {
			save();
		}
	}

	private void clearSavedAccounts() {
		_unencryptedReason = null;
		try {
			_storage.clear();
		} catch (IOException | RuntimeException e) {
			logStorageFailure("clear account storage", e);
		}
	}

	/**
	 * Saves accounts using encryption when possible.
	 */
	public synchronized void save() {
		if (!AuthAgainConfig.persistAccounts) {
			return;
		}
		long revision = Math.min(_storageRevision + 1, MAX_STORAGE_REVISION);
		byte[] document = serializeAccounts(revision);
		try {
			_unencryptedReason = null;
			if (AuthAgainConfig.encryptAccounts && saveEncrypted(document)) {
				_storageRevision = revision;
				return;
			}
			try {
				_storage.writeLegacy(document);
				_storageRevision = revision;
			} catch (IOException | RuntimeException e) {
				logStorageFailure("write unencrypted accounts", e);
				return;
			}
			if (!AuthAgainConfig.encryptAccounts) {
				try {
					_storage.deleteReadableEncrypted();
				} catch (IOException | GeneralSecurityException | RuntimeException e) {
					logStorageFailure("remove encrypted accounts", e);
				}
			}
		} finally {
			Arrays.fill(document, (byte) 0);
		}
	}

	/**
	 * Returns whether account persistence can expose plaintext login files.
	 */
	public synchronized boolean isUnencrypted() {
		return getUnencryptedReason() != null;
	}

	@Nullable
	public synchronized UnencryptedReason getUnencryptedReason() {
		if (AuthAgainConfig.persistAccounts) {
			if (!AuthAgainConfig.encryptAccounts) {
				return UnencryptedReason.DISABLED;
			}
			if (_unencryptedReason != null) {
				return _unencryptedReason;
			}
		}
		return _storage.hasLegacyFiles() ? UnencryptedReason.LEGACY_FILES : null;
	}

	private boolean saveEncrypted(byte[] document) {
		try {
			_storage.write(document);
		} catch (IOException | GeneralSecurityException | RuntimeException e) {
			if (e instanceof IOException) {
				_unencryptedReason = UnencryptedReason.WRITE_FAILED;
			} else if (!_storage.hasEncryptionIdentity()) {
				_unencryptedReason = UnencryptedReason.NO_DEVICE_ID;
			} else {
				_unencryptedReason = UnencryptedReason.ENCRYPTION_FAILED;
			}
			logStorageFailure("write encrypted accounts", e);
			return false;
		}
		try {
			_storage.deleteLegacy();
		} catch (IOException | RuntimeException e) {
			_unencryptedReason = UnencryptedReason.LEGACY_FILES;
			logStorageFailure("remove unencrypted accounts", e);
		}
		return true;
	}

	private void readAccounts(byte[] document, boolean merge) throws IOException {
		try {
			JsonObject root = GSON.fromJson(new String(document, StandardCharsets.UTF_8), JsonObject.class);
			if (root == null || !root.has("accounts") || !root.get("accounts").isJsonArray()) {
				throw new IOException("Invalid account document");
			}
			List<AuthAccount> parsed = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("accounts")) {
				parsed.add(fromJson(element.getAsJsonObject()));
			}
			UUID activeUuid = root.has("activeUuid") && !root.get("activeUuid").isJsonNull()
					? UUID.fromString(root.get("activeUuid").getAsString()) : null;
			if (!merge || (root.has("storageVersion") && root.get("storageVersion").getAsInt() == 2)) {
				long revision = readStorageRevision(root);
				if (merge && _storageRevision > 0 && revision <= _storageRevision) {
					return;
				}
				_accounts = parsed;
				_activeUuid = activeUuid;
				_storageRevision = revision;
				return;
			}
			for (AuthAccount account : parsed) {
				AuthAccount existing = findByUuid(account.uuid());
				if (existing == null) {
					_accounts.add(account);
				} else if (account.lastRefreshedAt() > existing.lastRefreshedAt()
						&& (!hasRefreshToken(existing) || hasRefreshToken(account))) {
					_accounts.set(_accounts.indexOf(existing), account);
				}
			}
			if (_activeUuid == null) {
				_activeUuid = activeUuid;
			}
		} finally {
			Arrays.fill(document, (byte) 0);
		}
	}

	private static long readStorageRevision(JsonObject document) throws IOException {
		if (!document.has("storageRevision")) {
			return 0;
		}
		long revision = Long.parseLong(document.get("storageRevision").getAsString());
		if (revision < 1 || revision > MAX_STORAGE_REVISION) {
			throw new IOException("Invalid account storage revision");
		}
		return revision;
	}

	private static boolean hasRefreshToken(AuthAccount account) {
		JsonObject session = account.session();
		if (!session.has("msaToken") || !session.get("msaToken").isJsonObject()) {
			return false;
		}
		JsonObject token = session.getAsJsonObject("msaToken");
		return token.has("refreshToken") && !token.get("refreshToken").isJsonNull();
	}

	private byte[] serializeAccounts(long revision) {
		JsonArray accounts = new JsonArray();
		for (AuthAccount account : _accounts) {
			accounts.add(toJson(account));
		}
		JsonObject root = new JsonObject();
		root.addProperty("storageVersion", 2);
		root.addProperty("storageRevision", revision);
		root.addProperty("activeUuid", _activeUuid == null ? null : _activeUuid.toString());
		root.add("accounts", accounts);
		return GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
	}

	private static void logStorageFailure(String action, Exception error) {
		// JSON parse exceptions can include account secrets in their messages.
		AuthAgainMod.LOGGER.warn("[AuthAgain] Could not {} ({}).", action, error.getClass().getSimpleName());
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
