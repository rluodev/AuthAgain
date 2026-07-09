package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

class AuthAccountTest {

	private static JsonObject session() {
		JsonObject json = new JsonObject();
		json.addProperty("token", "abc");
		return json;
	}

	@Test
	void convenienceConstructorGeneratesIdAndTimestamp() {
		long before = System.currentTimeMillis();
		UUID uuid = UUID.randomUUID();
		AuthAccount account = new AuthAccount("Steve", uuid, "xuid1", session());

		assertThat(UUID.fromString(account.id())).isNotNull();
		assertThat(account.displayName()).isEqualTo("Steve");
		assertThat(account.uuid()).isEqualTo(uuid);
		assertThat(account.xuid()).isEqualTo("xuid1");
		assertThat(account.lastRefreshedAt()).isBetween(before, System.currentTimeMillis());
	}

	@Test
	void eachConvenienceConstructionGetsAUniqueId() {
		UUID uuid = UUID.randomUUID();
		AuthAccount a = new AuthAccount("Steve", uuid, "x", session());
		AuthAccount b = new AuthAccount("Steve", uuid, "x", session());
		assertThat(a.id()).isNotEqualTo(b.id());
	}

	@Test
	void withSessionKeepsIdentityAndReplacesTheRest() {
		UUID uuid = UUID.randomUUID();
		AuthAccount original = new AuthAccount("id-1", "Steve", uuid, "x1", session(), 100L);
		JsonObject newSession = new JsonObject();
		newSession.addProperty("token", "def");

		AuthAccount updated = original.withSession("Alex", "x2", newSession, 200L);

		assertThat(updated.id()).isEqualTo("id-1");
		assertThat(updated.uuid()).isEqualTo(uuid);
		assertThat(updated.displayName()).isEqualTo("Alex");
		assertThat(updated.xuid()).isEqualTo("x2");
		assertThat(updated.session()).isEqualTo(newSession);
		assertThat(updated.lastRefreshedAt()).isEqualTo(200L);
	}

	@Test
	void withSessionDoesNotMutateOriginal() {
		AuthAccount original = new AuthAccount("id-1", "Steve", UUID.randomUUID(), "x1", session(), 100L);
		original.withSession("Alex", "x2", new JsonObject(), 200L);

		assertThat(original.displayName()).isEqualTo("Steve");
		assertThat(original.xuid()).isEqualTo("x1");
		assertThat(original.lastRefreshedAt()).isEqualTo(100L);
	}

	@Test
	void requiredFieldsRejectNull() {
		UUID uuid = UUID.randomUUID();
		JsonObject session = session();
		assertThatNullPointerException().isThrownBy(() -> new AuthAccount(null, "n", uuid, "x", session, 0L));
		assertThatNullPointerException().isThrownBy(() -> new AuthAccount("id", null, uuid, "x", session, 0L));
		assertThatNullPointerException().isThrownBy(() -> new AuthAccount("id", "n", null, "x", session, 0L));
		assertThatNullPointerException().isThrownBy(() -> new AuthAccount("id", "n", uuid, null, session, 0L));
		assertThatNullPointerException().isThrownBy(() -> new AuthAccount("id", "n", uuid, "x", null, 0L));
	}
}
