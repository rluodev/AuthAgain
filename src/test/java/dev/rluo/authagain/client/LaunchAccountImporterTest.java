package dev.rluo.authagain.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.auth.AuthAccount;
import dev.rluo.authagain.config.AuthAgainConfig;
import dev.rluo.authagain.mcglue.SessionInjector;
import dev.rluo.authagain.support.ModBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

class LaunchAccountImporterTest {

	@TempDir
	Path dir;
	private AccountStore store;

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() throws Exception {
		resetImportedFlag();
		AuthAgainConfig.persistAccounts = false;
		store = new AccountStore(dir.resolve("accounts.json").toString());
		AuthAgainMod.globalAccountStore = store;
	}

	private static void resetImportedFlag() throws Exception {
		Field imported = LaunchAccountImporter.class.getDeclaredField("imported");
		imported.setAccessible(true);
		imported.setBoolean(null, false);
	}

	private static User msaUser(String name, UUID uuid, String token) {
		User user = mock(User.class);
		when(user.getType()).thenReturn(User.Type.MSA);
		when(user.getName()).thenReturn(name);
		when(user.getProfileId()).thenReturn(uuid);
		when(user.getXuid()).thenReturn(Optional.of("xuid-" + name));
		when(user.getAccessToken()).thenReturn(token);
		return user;
	}

	private void runImportWith(User user) {
		Minecraft client = mock(Minecraft.class);
		when(client.getUser()).thenReturn(user);
		try (MockedStatic<Minecraft> statics = mockStatic(Minecraft.class)) {
			statics.when(Minecraft::getInstance).thenReturn(client);
			LaunchAccountImporter.importIfNeeded();
		}
	}

	@Test
	void importsLaunchAccountAsTokenOnlyStub() {
		UUID uuid = UUID.randomUUID();
		runImportWith(msaUser("Steve", uuid, "launch-token"));

		AuthAccount imported = store.findByUuid(uuid);
		assertThat(imported).isNotNull();
		assertThat(imported.displayName()).isEqualTo("Steve");
		assertThat(imported.xuid()).isEqualTo("xuid-Steve");
		assertThat(SessionInjector.accessToken(imported)).isEqualTo("launch-token");
		assertThat(store.getActiveUuid()).isEqualTo(uuid);
	}

	@Test
	void importRunsAtMostOnce() {
		UUID first = UUID.randomUUID();
		runImportWith(msaUser("Steve", first, "tok"));
		runImportWith(msaUser("Alex", UUID.randomUUID(), "tok2"));

		assertThat(store.list()).hasSize(1);
		assertThat(store.findByUuid(first)).isNotNull();
	}

	@Test
	void doesNothingWhenStoreIsUnset() {
		AuthAgainMod.globalAccountStore = null;
		runImportWith(msaUser("Steve", UUID.randomUUID(), "tok"));

		assertThat(store.list()).isEmpty();
	}

	@Test
	void skipsWhenNoUserIsLoggedIn() {
		runImportWith(null);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void skipsNonMsaAccounts() {
		User user = mock(User.class);
		when(user.getType()).thenReturn(User.Type.LEGACY);
		runImportWith(user);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void skipsWhenProfileIdIsNull() {
		User user = msaUser("Steve", null, "tok");
		runImportWith(user);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void skipsWhenProfileIdCannotBeParsed() {
		User user = mock(User.class);
		when(user.getType()).thenReturn(User.Type.MSA);
		when(user.getProfileId()).thenThrow(new IllegalArgumentException("bad uuid"));
		runImportWith(user);
		assertThat(store.list()).isEmpty();
	}

	@Test
	void keepsExistingFullAccountButMarksItActive() {
		UUID uuid = UUID.randomUUID();
		AuthAccount existing = new AuthAccount("full", "Steve", uuid, "real-xuid", refreshableSession(), 1L);
		store.add(existing);

		runImportWith(msaUser("Steve", uuid, "launch-token"));

		assertThat(store.list()).hasSize(1);
		AuthAccount kept = store.get("full");
		assertThat(kept.xuid()).isEqualTo("real-xuid");
		assertThat(kept.session().has("refreshToken")).isTrue();
		assertThat(store.getActiveUuid()).isEqualTo(uuid);
	}

	private static com.google.gson.JsonObject refreshableSession() {
		com.google.gson.JsonObject session = new com.google.gson.JsonObject();
		session.addProperty("refreshToken", "keep-me");
		return session;
	}
}
