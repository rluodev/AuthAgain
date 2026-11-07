//? if forge {
package dev.rluo.authagain.forge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.client.AuthAgainClient;
import dev.rluo.authagain.support.ModBootstrap;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

class AuthAgainConfigForgeTest {

	@TempDir
	Path dir;
	private CommentedFileConfig data;
	private AccountStore store;

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() throws Exception {
		data = CommentedFileConfig.builder(dir.resolve("authagain-client.toml")).sync().autosave().build();
		data.set("persistAccounts", true);
		data.set("encryptAccounts", true);
		AuthAgainConfigForge.SPEC.setConfig(data);
		ModConfig config = mock(ModConfig.class);
		when(config.<ForgeConfigSpec>getSpec()).thenReturn(AuthAgainConfigForge.SPEC);
		when(config.getConfigData()).thenReturn(data);
		Field configField = AuthAgainConfigForge.class.getDeclaredField("config");
		configField.setAccessible(true);
		configField.set(null, config);
		AuthAgainMod.globalAccountStore = null;
		AuthAgainClient.applyConfig(true, true);
		store = mock(AccountStore.class);
		AuthAgainMod.globalAccountStore = store;
	}

	@AfterEach
	void tearDown() {
		AuthAgainMod.globalAccountStore = null;
		AuthAgainConfigForge.SPEC.setConfig(null);
		data.close();
	}

	@Test
	void savingWritesBothFlagsAndAppliesThemToTheRunningStore() {
		AuthAgainConfigForge.save(false, false);

		try (CommentedFileConfig saved = CommentedFileConfig.of(dir.resolve("authagain-client.toml"))) {
			saved.load();
			assertThat(saved.<Boolean>get("persistAccounts")).isFalse();
			assertThat(saved.<Boolean>get("encryptAccounts")).isFalse();
		}
		assertThat(AuthAgainConfigForge.persistAccounts()).isFalse();
		assertThat(AuthAgainConfigForge.encryptAccounts()).isFalse();
		verify(store).applyConfig(false, false);
	}

	@Test
	void savingUnchangedSettingsDoesNotTouchTheRunningStore() {
		AuthAgainConfigForge.save(true, true);

		verifyNoInteractions(store);
	}
}
//?}
