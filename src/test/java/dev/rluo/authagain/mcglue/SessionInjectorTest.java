package dev.rluo.authagain.mcglue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonObject;

import dev.rluo.authagain.auth.AuthAccount;
import dev.rluo.authagain.support.ModBootstrap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

class SessionInjectorTest {

	@BeforeAll
	static void boot() {
		ModBootstrap.init();
	}

	private static AuthAccount account(String token) {
		JsonObject minecraftToken = new JsonObject();
		minecraftToken.addProperty("token", token);
		JsonObject session = new JsonObject();
		session.add("minecraftToken", minecraftToken);
		return new AuthAccount("id-1", "Bob", UUID.randomUUID(), "xuid-9", session, 0L);
	}

	@Test
	void buildUserPullsTokenFromSessionAndSetsMsaType() {
		AuthAccount account = account("access-token-123");

		User user = SessionInjector.buildUser(account);

		assertThat(user.getName()).isEqualTo("Bob");
		//? if <1.21 {
		assertThat(user.getUuid()).isEqualTo(account.uuid().toString());
		//?} else {
		/*assertThat(user.getProfileId()).isEqualTo(account.uuid());*/
		//?}
		assertThat(user.getAccessToken()).isEqualTo("access-token-123");
		assertThat(user.getXuid()).isEqualTo(Optional.of("xuid-9"));
		assertThat(user.getClientId()).isEqualTo(Optional.empty());
		assertThat(user.getType()).isEqualTo(User.Type.MSA);
	}

	@Test
	void applySwapsTheUserOnTheRunningClient() {
		//? if <1.21 {
		User user = new User("Bob", UUID.randomUUID().toString(), "tok", Optional.of("xuid-9"),
				Optional.empty(), User.Type.MSA);
		//?} else {
		/*User user = new User("Bob", UUID.randomUUID(), "tok", Optional.of("xuid-9"),
				Optional.empty(), User.Type.MSA);*/
		//?}
		Minecraft client = mock(Minecraft.class);

		try (MockedStatic<Minecraft> statics = mockStatic(Minecraft.class)) {
			statics.when(Minecraft::getInstance).thenReturn(client);
			SessionInjector.apply(user);
		}

		assertThat(client.user).isSameAs(user);
	}

	@Test
	void injectBuildsAndApplies() {
		AuthAccount account = account("access-token-123");
		Minecraft client = mock(Minecraft.class);

		try (MockedStatic<Minecraft> statics = mockStatic(Minecraft.class)) {
			statics.when(Minecraft::getInstance).thenReturn(client);
			SessionInjector.inject(account);
		}

		assertThat(client.user.getName()).isEqualTo("Bob");
		assertThat(client.user.getAccessToken()).isEqualTo("access-token-123");
	}
}
