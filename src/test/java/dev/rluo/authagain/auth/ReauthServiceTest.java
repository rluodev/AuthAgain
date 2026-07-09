package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.google.gson.JsonObject;

import net.raphimc.minecraftauth.java.JavaAuthManager;
import net.raphimc.minecraftauth.java.model.MinecraftProfile;
import net.raphimc.minecraftauth.java.model.MinecraftToken;
import net.raphimc.minecraftauth.util.holder.Holder;

class ReauthServiceTest {

	/** Builds an unsigned JWT whose payload is the given JSON. */
	private static String jwt(String payloadJson) {
		Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
		String header = enc.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
		String payload = enc.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
		return header + "." + payload + "." + enc.encodeToString(new byte[] { 0 });
	}

	@SuppressWarnings("unchecked")
	private static JavaAuthManager managerWith(String tokenJwt, MinecraftProfile profile) {
		JavaAuthManager manager = mock(JavaAuthManager.class);

		MinecraftToken token = mock(MinecraftToken.class);
		when(token.getToken()).thenReturn(tokenJwt);
		Holder<MinecraftToken> tokenHolder = mock(Holder.class);
		when(tokenHolder.getCached()).thenReturn(token);
		when(manager.getMinecraftToken()).thenReturn(tokenHolder);

		if (profile != null) {
			Holder<MinecraftProfile> profileHolder = mock(Holder.class);
			when(profileHolder.getCached()).thenReturn(profile);
			when(manager.getMinecraftProfile()).thenReturn(profileHolder);
		}
		return manager;
	}

	@Test
	void extractXuidReadsTheClaim() {
		JavaAuthManager manager = managerWith(jwt("{\"xuid\":\"2535123\",\"sub\":\"x\"}"), null);
		assertThat(ReauthService.extractXuid(manager)).isEqualTo("2535123");
	}

	@Test
	void extractXuidReturnsEmptyWhenClaimAbsent() {
		JavaAuthManager manager = managerWith(jwt("{\"sub\":\"x\"}"), null);
		assertThat(ReauthService.extractXuid(manager)).isEmpty();
	}

	@Test
	void toAccountBuildsFromProfileAndToken() {
		UUID uuid = UUID.randomUUID();
		MinecraftProfile profile = mock(MinecraftProfile.class);
		when(profile.getName()).thenReturn("Alex");
		when(profile.getId()).thenReturn(uuid);
		JavaAuthManager manager = managerWith(jwt("{\"xuid\":\"99\"}"), profile);

		JsonObject serialized = new JsonObject();
		serialized.addProperty("marker", "session");
		try (MockedStatic<JavaAuthManager> statics = mockStatic(JavaAuthManager.class)) {
			statics.when(() -> JavaAuthManager.toJson(manager)).thenReturn(serialized);

			AuthAccount account = ReauthService.toAccount(manager);

			assertThat(account.displayName()).isEqualTo("Alex");
			assertThat(account.uuid()).isEqualTo(uuid);
			assertThat(account.xuid()).isEqualTo("99");
			assertThat(account.session()).isEqualTo(serialized);
		}
	}

	@Test
	void toAccountFromExistingKeepsIdAndUuid() {
		AuthAccount existing = new AuthAccount("id-keep", "OldName", UUID.randomUUID(), "old-xuid",
				new JsonObject(), 1L);

		MinecraftProfile profile = mock(MinecraftProfile.class);
		when(profile.getName()).thenReturn("NewName");
		when(profile.getId()).thenReturn(UUID.randomUUID());
		JavaAuthManager manager = managerWith(jwt("{\"xuid\":\"new-xuid\"}"), profile);

		JsonObject serialized = new JsonObject();
		serialized.addProperty("marker", "refreshed");
		try (MockedStatic<JavaAuthManager> statics = mockStatic(JavaAuthManager.class)) {
			statics.when(() -> JavaAuthManager.toJson(manager)).thenReturn(serialized);

			AuthAccount refreshed = ReauthService.toAccount(existing, manager);

			assertThat(refreshed.id()).isEqualTo("id-keep");
			assertThat(refreshed.uuid()).isEqualTo(existing.uuid());
			assertThat(refreshed.displayName()).isEqualTo("NewName");
			assertThat(refreshed.xuid()).isEqualTo("new-xuid");
			assertThat(refreshed.session()).isEqualTo(serialized);
			assertThat(refreshed.lastRefreshedAt()).isGreaterThan(1L);
		}
	}
}
