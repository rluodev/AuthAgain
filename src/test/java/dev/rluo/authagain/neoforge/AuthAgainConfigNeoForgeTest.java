//? if neoforge {
/*package dev.rluo.authagain.neoforge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;

import dev.rluo.authagain.AuthAgainMod;
import dev.rluo.authagain.auth.AccountStore;
import dev.rluo.authagain.client.AuthAgainClient;
import dev.rluo.authagain.support.ModBootstrap;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;

class AuthAgainConfigNeoForgeTest {

	@TempDir
	Path dir;
	private CommentedFileConfig data;
	private ModContainer container;
	private AccountStore store;

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	@BeforeEach
	void setUp() throws Exception {
		FMLPaths.loadAbsolutePaths(dir);
		AuthAgainMod.globalAccountStore = null;
		AuthAgainClient.applyConfig(true, true);
		data = CommentedFileConfig.builder(dir.resolve("authagain-client.toml")).sync().autosave().build();
		data.set("persistAccounts", true);
		data.set("encryptAccounts", true);
		container = mock(ModContainer.class);
		Constructor<ModConfig> configConstructor = ModConfig.class.getDeclaredConstructor(ModConfig.Type.class,
				IConfigSpec.class, ModContainer.class, String.class, ReentrantLock.class);
		configConstructor.setAccessible(true);
		ModConfig config = configConstructor.newInstance(ModConfig.Type.CLIENT, AuthAgainConfigNeoForge.SPEC,
				container, "authagain-client.toml", new ReentrantLock());
		Constructor<?> loadedConstructor = Class.forName("net.neoforged.fml.config.LoadedConfig")
				.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
		loadedConstructor.setAccessible(true);
		IConfigSpec.ILoadedConfig loadedConfig = (IConfigSpec.ILoadedConfig) loadedConstructor
				.newInstance(data, dir.resolve("authagain-client.toml"), config);
		Field loadedField = ModConfig.class.getDeclaredField("loadedConfig");
		loadedField.setAccessible(true);
		loadedField.set(config, loadedConfig);
		doAnswer(invocation -> {
			AuthAgainConfigNeoForge.onLoad(invocation.getArgument(0));
			return null;
		}).when(container).acceptEvent(any(ModConfigEvent.Reloading.class));
		AuthAgainConfigNeoForge.SPEC.acceptConfig(loadedConfig);
		AuthAgainConfigNeoForge.onLoad(new ModConfigEvent.Loading(config));
		clearInvocations(container);
		store = mock(AccountStore.class);
		AuthAgainMod.globalAccountStore = store;
	}

	@AfterEach
	void tearDown() {
		AuthAgainMod.globalAccountStore = null;
		AuthAgainConfigNeoForge.SPEC.acceptConfig(null);
		data.close();
	}

	@Test
	void savingWritesBothFlagsAndAppliesThemToTheRunningStore() {
		AuthAgainConfigNeoForge.save(false, false);

		try (CommentedFileConfig saved = CommentedFileConfig.of(dir.resolve("authagain-client.toml"))) {
			saved.load();
			assertThat(saved.<Boolean>get("persistAccounts")).isFalse();
			assertThat(saved.<Boolean>get("encryptAccounts")).isFalse();
		}
		verify(container).acceptEvent(any(ModConfigEvent.Reloading.class));
		verify(store).applyConfig(false, false);
		assertThat(AuthAgainConfigNeoForge.SPEC.getValues()
				.<ModConfigSpec.ConfigValue<Boolean>>get("persistAccounts").get()).isFalse();
		assertThat(AuthAgainConfigNeoForge.SPEC.getValues()
				.<ModConfigSpec.ConfigValue<Boolean>>get("encryptAccounts").get()).isFalse();

		clearInvocations(store, container);
		AuthAgainConfigNeoForge.save(false, false);

		verifyNoInteractions(store, container);
	}

	@Test
	void savingUnchangedSettingsDoesNotTouchTheRunningStore() {
		AuthAgainConfigNeoForge.save(true, true);

		verifyNoInteractions(store, container);
	}

	@Test
	void failedSaveRestoresBothFlagsWithoutChangingTheRunningStore() {
		RuntimeException failure = new IllegalStateException("Could not save config");
		doThrow(failure).doNothing().when(container).acceptEvent(any(ModConfigEvent.Reloading.class));

		assertThatThrownBy(() -> AuthAgainConfigNeoForge.save(false, false)).isSameAs(failure);

		assertThat(data.<Boolean>get("persistAccounts")).isTrue();
		assertThat(data.<Boolean>get("encryptAccounts")).isTrue();
		try (CommentedFileConfig saved = CommentedFileConfig.of(dir.resolve("authagain-client.toml"))) {
			saved.load();
			assertThat(saved.<Boolean>get("persistAccounts")).isTrue();
			assertThat(saved.<Boolean>get("encryptAccounts")).isTrue();
		}
		verifyNoInteractions(store);
	}
}
*///?}
