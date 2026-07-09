package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.rluo.authagain.config.AuthAgainConfig;
import dev.rluo.authagain.support.ModBootstrap;

class AccountStoreTest {

	@TempDir
	Path dir;
	private String file;

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() {
		AuthAgainConfig.persistAccounts = true;
		file = dir.resolve("accounts.json").toString();
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
		AccountStore store = new AccountStore(file);
		assertThat(store.list()).isEmpty();
		assertThat(store.getActiveUuid()).isNull();
	}

	@Test
	void addPersistsAndSurvivesReload() {
		AuthAccount account = account("Steve", UUID.randomUUID());
		new AccountStore(file).add(account);

		AccountStore reloaded = new AccountStore(file);
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
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", uuid));
		store.add(account("SteveRenamed", uuid));

		assertThat(store.list()).hasSize(1);
		assertThat(store.findByUuid(uuid).displayName()).isEqualTo("SteveRenamed");
	}

	@Test
	void replaceUpdatesInPlace() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = new AccountStore(file);
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
		AccountStore store = new AccountStore(file);
		store.replace(account("Steve", UUID.randomUUID()));
		assertThat(store.list()).hasSize(1);
	}

	@Test
	void removeReturnsAccountAndDropsIt() {
		AccountStore store = new AccountStore(file);
		AuthAccount account = account("Steve", UUID.randomUUID());
		store.add(account);

		AuthAccount removed = store.remove(account.id());

		assertThat(removed).isEqualTo(account);
		assertThat(store.list()).isEmpty();
		assertThat(new AccountStore(file).list()).isEmpty();
	}

	@Test
	void removeOfUnknownIdReturnsNull() {
		AccountStore store = new AccountStore(file);
		assertThat(store.remove("nope")).isNull();
	}

	@Test
	void getAndFindReturnNullWhenAbsent() {
		AccountStore store = new AccountStore(file);
		assertThat(store.get("nope")).isNull();
		assertThat(store.findById("nope")).isNull();
		assertThat(store.findByUuid(UUID.randomUUID())).isNull();
	}

	@Test
	void listReturnsADefensiveCopy() {
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", UUID.randomUUID()));

		List<AuthAccount> snapshot = store.list();
		snapshot.clear();

		assertThat(store.list()).hasSize(1);
	}

	@Test
	void setActiveUuidIgnoresUnknownAccounts() {
		AccountStore store = new AccountStore(file);
		store.setActiveUuid(UUID.randomUUID());
		assertThat(store.getActiveUuid()).isNull();
	}

	@Test
	void activeUuidPersistsWhenAccountExists() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", uuid));
		store.setActiveUuid(uuid);

		assertThat(new AccountStore(file).getActiveUuid()).isEqualTo(uuid);
	}

	@Test
	void savedFileUsesActiveUuidAndAccountsFields() throws IOException {
		UUID uuid = UUID.randomUUID();
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", uuid));
		store.setActiveUuid(uuid);

		JsonObject root = new Gson().fromJson(Files.readString(Path.of(file)), JsonObject.class);
		assertThat(root.get("activeUuid").getAsString()).isEqualTo(uuid.toString());
		assertThat(root.get("accounts").isJsonArray()).isTrue();
		assertThat(root.getAsJsonArray("accounts")).hasSize(1);
	}

	@Test
	void atomicWriteLeavesNoTempFileBehind() {
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", UUID.randomUUID()));
		assertThat(Files.exists(dir.resolve("accounts.json.tmp"))).isFalse();
	}

	@Test
	void persistAccountsFalseNeverWritesToDisk() {
		AuthAgainConfig.persistAccounts = false;
		AccountStore store = new AccountStore(file);
		store.add(account("Steve", UUID.randomUUID()));

		assertThat(Files.exists(Path.of(file))).isFalse();
		assertThat(store.list()).hasSize(1);
	}

	@Test
	void loadDeletesExistingFileWhenPersistDisabled() throws IOException {
		new AccountStore(file).add(account("Steve", UUID.randomUUID()));
		assertThat(Files.exists(Path.of(file))).isTrue();

		AuthAgainConfig.persistAccounts = false;
		AccountStore store = new AccountStore(file);

		assertThat(store.list()).isEmpty();
		assertThat(Files.exists(Path.of(file))).isFalse();
	}

	@Test
	void corruptFileIsToleratedAsEmpty() throws IOException {
		Files.writeString(Path.of(file), "this is not json {{{");
		AccountStore store = new AccountStore(file);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void emptyFileIsToleratedAsEmpty() throws IOException {
		Files.writeString(Path.of(file), "");
		AccountStore store = new AccountStore(file);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void skinUrlPersistsAndSurvivesReload() {
		UUID uuid = UUID.randomUUID();
		AccountStore store = new AccountStore(file);
		AuthAccount account = account("Steve", uuid);
		store.add(account);

		store.replace(account.withSkinUrl("https://textures.minecraft.net/texture/abc123"));

		AuthAccount reloaded = new AccountStore(file).findByUuid(uuid);
		assertThat(reloaded.skinUrl()).isEqualTo("https://textures.minecraft.net/texture/abc123");
	}

	@Test
	void missingSkinUrlLoadsAsNull() {
		AuthAccount account = account("Steve", UUID.randomUUID());
		new AccountStore(file).add(account);

		assertThat(new AccountStore(file).get(account.id()).skinUrl()).isNull();
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

		AccountStore store = new AccountStore(file);
		assertThat(store.get("id-1").xuid()).isEmpty();
	}
}
