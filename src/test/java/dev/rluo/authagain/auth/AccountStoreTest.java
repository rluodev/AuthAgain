package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dev.rluo.authagain.config.AuthAgainConfig;
import dev.rluo.authagain.support.ModBootstrap;

class AccountStoreTest {

	@TempDir
	Path dir;
	private String file;
	private final List<DeviceIdentity> identities = List.of(
			new DeviceIdentity("system-uuid", "987d126a-ace7-48be-a91f-83b8cf914c28"),
			new DeviceIdentity("baseboard-serial", "AA-TEST-BOARD-126A"),
			new DeviceIdentity("disk-serial", "AA-TEST-DISK-914C28"));

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() throws IOException {
		AuthAgainConfig.persistAccounts = true;
		AuthAgainConfig.encryptAccounts = true;
		file = dir.resolve("pack/config/authagain/accounts.json").toString();
		Files.createDirectories(Path.of(file).getParent());
	}

	@AfterEach
	void tearDown() {
		AuthAgainConfig.encryptAccounts = true;
	}

	private AccountStore store() {
		return new AccountStore(Path.of(file), identities);
	}

	private Path encryptedFile() {
		return Path.of(file).resolveSibling("accounts.enc");
	}

	private static String legacyDocument(AuthAccount... accounts) {
		JsonObject root = new JsonObject();
		root.addProperty("activeUuid", accounts.length == 0 ? null : accounts[0].uuid().toString());
		JsonArray array = new JsonArray();
		for (AuthAccount account : accounts) {
			array.add(new Gson().toJsonTree(account));
		}
		root.add("accounts", array);
		return root.toString();
	}

	private static JsonObject session(String token) {
		JsonObject json = new JsonObject();
		json.addProperty("token", token);
		return json;
	}

	private static AuthAccount account(String name, UUID uuid) {
		return new AuthAccount(name, uuid, "xuid-" + name, session("tok-" + name));
	}

	@Test
	void missingFileStartsEmpty() {
		AccountStore store = store();
		assertThat(store.list()).isEmpty();
		assertThat(store.getActiveUuid()).isNull();
	}

	@Test
	void addPersistsAndSurvivesReload() {
		AuthAccount account = account("Steve", UUID.randomUUID());
		store().add(account);

		AccountStore reloaded = store();
		assertThat(reloaded.list()).hasSize(1);
		AuthAccount loaded = reloaded.get(account.id());
		assertThat(loaded.displayName()).isEqualTo("Steve");
		assertThat(loaded.uuid()).isEqualTo(account.uuid());
		assertThat(loaded.xuid()).isEqualTo("xuid-Steve");
		assertThat(loaded.session().get("token").getAsString()).isEqualTo("tok-Steve");
	}

	@Test
	void addWithDuplicateUuidReplacesRatherThanAppends() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = store();
		store.add(account("Steve", uuid));
		store.add(account("SteveRenamed", uuid));

		assertThat(store.list()).hasSize(1);
		assertThat(store.findByUuid(uuid).displayName()).isEqualTo("SteveRenamed");
	}

	@Test
	void replaceUpdatesInPlace() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = store();
		AuthAccount original = account("Steve", uuid);
		store.add(original);

		store.replace(original.withSession("Alex", "xuid-2", session("tok-2"), 500L));

		assertThat(store.list()).hasSize(1);
		AuthAccount updated = store.get(original.id());
		assertThat(updated.displayName()).isEqualTo("Alex");
		assertThat(updated.xuid()).isEqualTo("xuid-2");
		assertThat(updated.lastRefreshedAt()).isEqualTo(500L);
	}

	@Test
	void replaceOfUnknownAccountAddsIt() {
		AccountStore store = store();
		store.replace(account("Steve", UUID.randomUUID()));
		assertThat(store.list()).hasSize(1);
	}

	@Test
	void removeReturnsAccountAndDropsIt() {
		AccountStore store = store();
		AuthAccount account = account("Steve", UUID.randomUUID());
		store.add(account);

		AuthAccount removed = store.remove(account.id());

		assertThat(removed).isEqualTo(account);
		assertThat(store.list()).isEmpty();
		assertThat(store().list()).isEmpty();
	}

	@Test
	void removeOfUnknownIdReturnsNull() {
		AccountStore store = store();
		assertThat(store.remove("nope")).isNull();
	}

	@Test
	void getAndFindReturnNullWhenAbsent() {
		AccountStore store = store();
		assertThat(store.get("nope")).isNull();
		assertThat(store.findById("nope")).isNull();
		assertThat(store.findByUuid(UUID.randomUUID())).isNull();
	}

	@Test
	void listReturnsADefensiveCopy() {
		AccountStore store = store();
		store.add(account("Steve", UUID.randomUUID()));

		List<AuthAccount> snapshot = store.list();
		snapshot.clear();

		assertThat(store.list()).hasSize(1);
	}

	@Test
	void setActiveUuidIgnoresUnknownAccounts() {
		AccountStore store = store();
		store.setActiveUuid(UUID.randomUUID());
		assertThat(store.getActiveUuid()).isNull();
	}

	@Test
	void activeUuidPersistsWhenAccountExists() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = store();
		store.add(account("Steve", uuid));
		store.setActiveUuid(uuid);

		assertThat(store().getActiveUuid()).isEqualTo(uuid);
	}

	@Test
	void savedFileUsesActiveUuidAndAccountsFields() throws Exception {
		UUID uuid = UUID.randomUUID();
		AccountStore store = store();
		store.add(account("Steve", uuid));
		store.setActiveUuid(uuid);

		byte[] plaintext = AccountCipher.decrypt(Files.readAllBytes(encryptedFile()), new DeviceKey(identities));
		JsonObject root = new Gson().fromJson(new String(plaintext, StandardCharsets.UTF_8), JsonObject.class);
		assertThat(root.get("activeUuid").getAsString()).isEqualTo(uuid.toString());
		assertThat(root.get("accounts").isJsonArray()).isTrue();
		assertThat(root.getAsJsonArray("accounts")).hasSize(1);
	}

	@Test
	void atomicWriteLeavesNoTempFileBehind() throws IOException {
		AccountStore store = store();
		store.add(account("Steve", UUID.randomUUID()));
		assertThat(Files.exists(Path.of(file).resolveSibling("accounts.json.tmp"))).isFalse();
		try (var paths = Files.list(Path.of(file).getParent())) {
			assertThat(paths.map(path -> path.getFileName().toString()).toList())
					.noneMatch(name -> name.endsWith(".tmp"));
		}
	}

	@Test
	void persistAccountsFalseNeverWritesToDisk() {
		AuthAgainConfig.persistAccounts = false;
		AccountStore store = store();
		store.add(account("Steve", UUID.randomUUID()));

		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(encryptedFile())).isFalse();
		assertThat(store.list()).hasSize(1);
	}

	@Test
	void loadDeletesExistingFileWhenPersistDisabled() throws IOException {
		store().add(account("Steve", UUID.randomUUID()));
		Path encrypted = encryptedFile();
		assertThat(Files.exists(encrypted)).isTrue();

		AuthAgainConfig.persistAccounts = false;
		AccountStore store = store();

		assertThat(store.list()).isEmpty();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(encrypted)).isFalse();
	}

	@Test
	void switchingOffPersistenceKeepsAccountsAvailableUntilExit() {
		AccountStore store = store();
		AuthAccount first = account("Steve", UUID.randomUUID());
		store.add(first);
		store.setActiveUuid(first.uuid());

		store.applyConfig(false, true);
		assertThat(Files.exists(encryptedFile())).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(store.list()).containsExactly(first);
		assertThat(store.getActiveUuid()).isEqualTo(first.uuid());

		AuthAccount second = account("Alex", UUID.randomUUID());
		store.add(second);
		assertThat(Files.exists(encryptedFile())).isFalse();
		store.applyConfig(true, true);

		AccountStore reloaded = store();
		assertThat(reloaded.list()).containsExactly(first, second);
		assertThat(reloaded.getActiveUuid()).isEqualTo(first.uuid());
	}

	@Test
	void switchingOffPersistencePreservesOtherFilesInTheAccountDirectory() throws IOException {
		AccountStore store = store();
		store.add(account("Steve", UUID.randomUUID()));
		Path notes = encryptedFile().resolveSibling("accounts.enc.unreadable-notes.txt");
		Files.writeString(notes, "user notes");
		Path backup = encryptedFile().resolveSibling("accounts.enc.unreadable");
		Files.write(backup, Files.readAllBytes(encryptedFile()));

		store.applyConfig(false, true);

		assertThat(Files.exists(backup)).isFalse();
		assertThat(Files.readString(notes)).isEqualTo("user notes");
		assertThat(store.list()).hasSize(1);
	}

	@Test
	void corruptFileIsToleratedAsEmpty() throws IOException {
		Files.writeString(Path.of(file), "this is not json {{{");
		AccountStore store = store();
		assertThat(store.list()).isEmpty();
	}

	@Test
	void emptyFileIsToleratedAsEmpty() throws IOException {
		Files.writeString(Path.of(file), "");
		AccountStore store = store();
		assertThat(store.list()).isEmpty();
	}

	@Test
	void skinUrlPersistsAndSurvivesReload() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = store();
		AuthAccount account = account("Steve", uuid);
		store.add(account);

		store.replace(account.withSkinUrl("https://textures.minecraft.net/texture/abc123"));

		AuthAccount reloaded = store().findByUuid(uuid);
		assertThat(reloaded.skinUrl()).isEqualTo("https://textures.minecraft.net/texture/abc123");
	}

	@Test
	void missingSkinUrlLoadsAsNull() {
		AuthAccount account = account("Steve", UUID.randomUUID());
		store().add(account);

		assertThat(store().get(account.id()).skinUrl()).isNull();
	}

	@Test
	void missingXuidLoadsAsEmptyString() throws IOException {
		String json = """
				{
				  "activeUuid": null,
				  "accounts": [
				    {
				      "id": "id-1",
				      "displayName": "Steve",
				      "uuid": "%s",
				      "session": { "token": "t" },
				      "lastRefreshedAt": 42
				    }
				  ]
				}
				""".formatted(UUID.randomUUID());
		Files.write(Path.of(file), json.getBytes(StandardCharsets.UTF_8));

		AccountStore store = store();
		assertThat(store.get("id-1").xuid()).isEmpty();
	}

	@Test
	void legacyMigrationPreservesAccountsAndRemovesBothPlaintextFiles() throws IOException {
		AuthAccount account = account("Steve", UUID.randomUUID());
		Files.writeString(Path.of(file), legacyDocument(account));
		Files.writeString(Path.of(file).resolveSibling("accounts.json.tmp"), legacyDocument(account));

		AccountStore store = store();

		assertThat(store.list()).containsExactly(account);
		assertThat(store.getActiveUuid()).isEqualTo(account.uuid());
		assertThat(store.isUnencrypted()).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(Path.of(file).resolveSibling("accounts.json.tmp"))).isFalse();
		assertThat(store().list()).containsExactly(account);
	}

	@Test
	void legacyMainAndTemporaryFileBothKeepTheirAccounts() throws IOException {
		AuthAccount first = account("Steve", UUID.randomUUID());
		AuthAccount second = account("Alex", UUID.randomUUID());
		Files.writeString(Path.of(file), legacyDocument(first));
		Files.writeString(Path.of(file).resolveSibling("accounts.json.tmp"), legacyDocument(second));

		AccountStore store = store();

		assertThat(store.list()).containsExactlyInAnyOrder(first, second);
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(Path.of(file).resolveSibling("accounts.json.tmp"))).isFalse();
		assertThat(store().list()).containsExactlyInAnyOrder(first, second);
	}

	@Test
	void temporaryLegacyFileAloneCanBeRecoveredAndMigrated() throws IOException {
		AuthAccount account = account("Steve", UUID.randomUUID());
		Path temporary = Path.of(file).resolveSibling("accounts.json.tmp");
		Files.writeString(temporary, legacyDocument(account));

		assertThat(store().list()).containsExactly(account);
		assertThat(store().list()).containsExactly(account);
		assertThat(Files.exists(temporary)).isFalse();
	}

	@Test
	void persistDisabledRemovesCiphertextAndBothPlaintextFiles() throws IOException {
		store().add(account("Steve", UUID.randomUUID()));
		Files.writeString(Path.of(file), legacyDocument(account("Alex", UUID.randomUUID())));
		Path temporary = Path.of(file).resolveSibling("accounts.json.tmp");
		Files.writeString(temporary, legacyDocument(account("Alex", UUID.randomUUID())));
		AuthAgainConfig.persistAccounts = false;

		AccountStore store = store();

		assertThat(store.list()).isEmpty();
		assertThat(Files.exists(encryptedFile())).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(temporary)).isFalse();
	}

	@Test
	void copiedPackCannotExposeSendersAccountsOnAnotherDevice() throws Exception {
		AuthAccount owner = account("owner-unique-account-identity", UUID.randomUUID());
		store().add(owner);
		byte[] original = Files.readAllBytes(encryptedFile());
		Path pack = dir.resolve("pack");
		Path copy = dir.resolve("recipient-pack");
		try (var paths = Files.walk(pack)) {
			for (Path source : paths.toList()) {
				Path target = copy.resolve(pack.relativize(source));
				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else {
					Files.copy(source, target);
				}
			}
		}
		Path recipientFile = copy.resolve("config/authagain/accounts.json");
		List<DeviceIdentity> recipientIdentities = List.of(
				identities.get(0),
				new DeviceIdentity("baseboard-serial", "AA-TEST-BOARD-6754"),
				new DeviceIdentity("disk-serial", "AA-TEST-DISK-46F987"));
		AccountStore recipient = new AccountStore(recipientFile, recipientIdentities);

		assertThat(recipient.list()).isEmpty();
		AuthAccount own = account("recipient-identity", UUID.randomUUID());
		recipient.add(own);

		assertThat(recipient.isUnencrypted()).isFalse();
		assertThat(Files.exists(recipientFile)).isFalse();
		assertThat(new AccountStore(recipientFile, recipientIdentities).list()).containsExactly(own);
		assertThat(store().list()).containsExactly(owner);
		assertThat(Files.readAllBytes(recipientFile.resolveSibling("accounts.enc.unreadable"))).isEqualTo(original);
		byte[] restored = AccountCipher.decrypt(original, new DeviceKey(identities));
		assertThat(new String(restored, StandardCharsets.UTF_8)).contains(owner.displayName());
	}

	@Test
	void savedPackContainsNoPlaintextAccountOrDeviceIdentifiersAndNoKeyFile() throws IOException {
		AuthAccount owner = account("owner-unique-account-identity", UUID.randomUUID());
		store().add(owner);

		try (var paths = Files.walk(dir.resolve("pack"))) {
			List<Path> files = paths.filter(Files::isRegularFile).toList();
			assertThat(files).containsExactly(encryptedFile());
			String encrypted = new String(Files.readAllBytes(encryptedFile()), StandardCharsets.ISO_8859_1);
			assertThat(encrypted).doesNotContain(owner.displayName(), owner.uuid().toString(), owner.xuid(),
					owner.session().get("token").getAsString(), "activeUuid", "session");
			for (DeviceIdentity identity : identities) {
				assertThat(encrypted).doesNotContainIgnoringCase(identity.value());
			}
		}
	}

	@Test
	void tamperedCiphertextIsBackedUpBeforeEncryptingNewAccounts() throws IOException {
		store().add(account("Steve", UUID.randomUUID()));
		byte[] corrupted = Files.readAllBytes(encryptedFile());
		corrupted[corrupted.length - 1] ^= 1;
		Files.write(encryptedFile(), corrupted);

		AccountStore store = store();
		assertThat(store.list()).isEmpty();
		AuthAccount account = account("new-launch-account", UUID.randomUUID());
		store.add(account);

		assertThat(store.isUnencrypted()).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.readAllBytes(encryptedFile().resolveSibling("accounts.enc.unreadable"))).isEqualTo(corrupted);
		assertThat(store().list()).containsExactly(account);
	}

	@Test
	void missingDeviceIdentifiersFallBackToPlaintextWithoutDestroyingCiphertext() throws IOException {
		store().add(account("Steve", UUID.randomUUID()));
		byte[] original = Files.readAllBytes(encryptedFile());
		AccountStore store = new AccountStore(Path.of(file), List.of());
		AuthAccount account = account("Alex", UUID.randomUUID());

		store.add(account);

		assertThat(store.isUnencrypted()).isTrue();
		assertThat(store.getUnencryptedReason()).isEqualTo(AccountStore.UnencryptedReason.NO_DEVICE_ID);
		assertThat(Files.readString(Path.of(file))).contains("Alex", "tok-Alex");
		assertThat(Files.readAllBytes(encryptedFile())).isEqualTo(original);
		assertThat(new AccountStore(Path.of(file), List.of()).list()).containsExactly(account);
	}

	@Test
	void deviceIdentifiersCanBeEnumeratedInADifferentOrder() {
		AuthAccount account = account("Steve", UUID.randomUUID());
		store().add(account);
		List<DeviceIdentity> reordered = List.of(identities.get(2), identities.get(0), identities.get(1));

		AccountStore store = new AccountStore(Path.of(file), reordered);
		assertThat(store.list()).containsExactly(account);
		AuthAccount second = account("Alex", UUID.randomUUID());
		store.add(second);

		assertThat(store.isUnencrypted()).isFalse();
		assertThat(store().list()).containsExactlyInAnyOrder(account, second);
	}

	@Test
	void placeholderIdentifiersAreSkippedBeforeUsingAnotherHardwareSource() {
		List<DeviceIdentity> candidates = List.of(
				new DeviceIdentity("system-uuid", "unknown"),
				new DeviceIdentity("system-serial", "To be filled by O.E.M."),
				new DeviceIdentity("disk-serial", "AA-TEST-SERIAL-7A6B"));
		AccountStore store = new AccountStore(Path.of(file), candidates);
		AuthAccount account = account("Steve", UUID.randomUUID());

		store.add(account);

		assertThat(store.isUnencrypted()).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(new AccountStore(Path.of(file), List.of(candidates.get(2))).list()).containsExactly(account);
	}

	@Test
	void allUnknownDeviceIdentifiersUsePlaintextAndMarkItUnencrypted() throws IOException {
		List<DeviceIdentity> candidates = List.of(
				new DeviceIdentity("system-uuid", "unknown"),
				new DeviceIdentity("system-serial", "To be filled by O.E.M."));
		AccountStore store = new AccountStore(Path.of(file), candidates);
		AuthAccount account = account("Steve", UUID.randomUUID());

		store.add(account);

		assertThat(store.isUnencrypted()).isTrue();
		assertThat(Files.readString(Path.of(file))).contains("Steve", "tok-Steve");
		assertThat(Files.exists(encryptedFile())).isFalse();
	}

	@Test
	void encryptedWriteFailureReportsTheWriteProblem() throws IOException {
		Files.createDirectory(encryptedFile());
		AccountStore store = store();
		store.add(account("Steve", UUID.randomUUID()));

		assertThat(store.getUnencryptedReason()).isEqualTo(AccountStore.UnencryptedReason.WRITE_FAILED);
		assertThat(Files.readString(Path.of(file))).contains("tok-Steve");
	}

	@Test
	void encryptionDisabledSavesReadableAccountsAndMarksThemUnencrypted() throws IOException {
		AuthAgainConfig.encryptAccounts = false;
		AccountStore store = store();
		AuthAccount account = account("Steve", UUID.randomUUID());

		store.add(account);
		store.setActiveUuid(account.uuid());

		assertThat(store.isUnencrypted()).isTrue();
		assertThat(store.getUnencryptedReason()).isEqualTo(AccountStore.UnencryptedReason.DISABLED);
		JsonObject root = new Gson().fromJson(Files.readString(Path.of(file)), JsonObject.class);
		assertThat(root.get("storageVersion").getAsInt()).isEqualTo(2);
		assertThat(root.get("activeUuid").getAsString()).isEqualTo(account.uuid().toString());
		assertThat(root.getAsJsonArray("accounts")).hasSize(1);
		assertThat(Files.readString(Path.of(file))).contains("Steve", "tok-Steve");
		assertThat(Files.exists(encryptedFile())).isFalse();
		assertThat(store().list()).containsExactly(account);
	}

	@Test
	void switchingEncryptionRewritesAccountsInTheSameSession() throws IOException {
		AccountStore store = store();
		AuthAccount account = account("Steve", UUID.randomUUID());
		store.add(account);
		store.setActiveUuid(account.uuid());

		store.applyConfig(true, false);
		assertThat(Files.readString(Path.of(file))).contains("tok-Steve");
		assertThat(Files.exists(encryptedFile())).isFalse();
		assertThat(store.getUnencryptedReason()).isEqualTo(AccountStore.UnencryptedReason.DISABLED);

		store.applyConfig(true, true);
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(store.isUnencrypted()).isFalse();
		assertThat(store.list()).containsExactly(account);
		assertThat(store().getActiveUuid()).isEqualTo(account.uuid());
	}

	@Test
	void failedPlaintextWriteKeepsTheEncryptedFile() throws IOException {
		AccountStore store = store();
		AuthAccount account = account("Steve", UUID.randomUUID());
		store.add(account);
		byte[] encrypted = Files.readAllBytes(encryptedFile());
		Files.createDirectory(Path.of(file));

		store.applyConfig(true, false);

		assertThat(Files.readAllBytes(encryptedFile())).isEqualTo(encrypted);
		assertThat(store.list()).containsExactly(account);
	}

	@Test
	void reenablingEncryptionMigratesPlaintextBackToEncryptedStorage() {
		AuthAgainConfig.encryptAccounts = false;
		AuthAccount account = account("Steve", UUID.randomUUID());
		store().add(account);
		AuthAgainConfig.encryptAccounts = true;

		AccountStore store = store();

		assertThat(store.list()).containsExactly(account);
		assertThat(store.isUnencrypted()).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(Files.exists(encryptedFile())).isTrue();
		assertThat(store().list()).containsExactly(account);
	}

	@Test
	void plaintextRemovalIsNotResurrectedFromAnOlderEncryptedStore() throws IOException {
		AuthAccount account = account("Steve", UUID.randomUUID());
		store().add(account);
		AuthAgainConfig.encryptAccounts = false;
		AccountStore plaintext = store();
		assertThat(plaintext.list()).containsExactly(account);

		plaintext.remove(account.id());

		assertThat(plaintext.isUnencrypted()).isTrue();
		JsonObject root = new Gson().fromJson(Files.readString(Path.of(file)), JsonObject.class);
		assertThat(root.getAsJsonArray("accounts")).isEmpty();
		AuthAgainConfig.encryptAccounts = true;
		assertThat(store().list()).isEmpty();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(store().list()).isEmpty();
	}

	@Test
	void leftoverPlaintextSnapshotDoesNotRestoreAnAccountRemovedFromTheEncryptedStore() throws IOException {
		AuthAgainConfig.encryptAccounts = false;
		AuthAccount removed = account("Steve", UUID.randomUUID());
		AuthAccount retained = account("Alex", UUID.randomUUID());
		AccountStore plaintext = store();
		plaintext.add(removed);
		plaintext.add(retained);
		byte[] stale = Files.readAllBytes(Path.of(file));
		AuthAgainConfig.encryptAccounts = true;
		AccountStore encrypted = store();
		assertThat(encrypted.list()).containsExactly(removed, retained);
		assertThat(encrypted.remove(removed.id())).isEqualTo(removed);
		Files.write(Path.of(file), stale);

		AccountStore reloaded = store();

		assertThat(reloaded.list()).containsExactly(retained);
		assertThat(reloaded.isUnencrypted()).isFalse();
		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(store().list()).containsExactly(retained);
	}

	@Test
	void newerLegacySessionMergesWithEncryptedAccountsWithoutLosingOthers() throws IOException {
		UUID uuid = UUID.randomUUID();
		AuthAccount existing = new AuthAccount("same-id", "Steve", uuid, "xuid-a", session("old"), 10L);
		AuthAccount other = account("Alex", UUID.randomUUID());
		AccountStore store = store();
		store.add(existing);
		store.add(other);
		AuthAccount refreshed = existing.withSession("Steve", "xuid-new", session("newer"), 20L);
		Files.writeString(Path.of(file), legacyDocument(refreshed));

		AccountStore migrated = store();

		assertThat(migrated.list()).hasSize(2);
		assertThat(migrated.findByUuid(uuid).session().get("token").getAsString()).isEqualTo("newer");
		assertThat(migrated.findByUuid(other.uuid())).isEqualTo(other);
		assertThat(Files.exists(Path.of(file))).isFalse();
	}

	@Test
	void staleLegacySessionDoesNotReplaceNewerEncryptedSession() throws IOException {
		UUID uuid = UUID.randomUUID();
		AuthAccount current = new AuthAccount("same-id", "Steve", uuid, "xuid-new", session("newer"), 20L);
		store().add(current);
		Files.writeString(Path.of(file), legacyDocument(current.withSession("Steve", "xuid-old", session("old"), 10L)));

		assertThat(store().findByUuid(uuid).session().get("token").getAsString()).isEqualTo("newer");
		assertThat(Files.exists(Path.of(file))).isFalse();
	}

	@Test
	void newerLegacyLaunchStubDoesNotReplaceAFullRefreshableSession() throws IOException {
		UUID uuid = UUID.randomUUID();
		JsonObject fullSession = new JsonObject();
		JsonObject msaToken = new JsonObject();
		msaToken.addProperty("refreshToken", "keep-full-refresh-token");
		fullSession.add("msaToken", msaToken);
		fullSession.add("minecraftToken", session("full-minecraft-token"));
		AuthAccount full = new AuthAccount("same-id", "Steve", uuid, "xuid-full", fullSession, 10L);
		store().add(full);
		JsonObject stubSession = new JsonObject();
		stubSession.add("minecraftToken", session("new-launch-token"));
		Files.writeString(Path.of(file), legacyDocument(full.withSession("Steve", "xuid-stub", stubSession, 20L)));

		AuthAccount reloaded = store().findByUuid(uuid);

		assertThat(reloaded.session()).isEqualTo(fullSession);
		assertThat(reloaded.xuid()).isEqualTo("xuid-full");
		assertThat(Files.exists(Path.of(file))).isFalse();
	}
}
