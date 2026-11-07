package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class AccountCipherTest {

	private final DeviceIdentity identity = new DeviceIdentity("system-uuid", "987d126a-ace7-48be-a91f-83b8cf914c28");
	private final DeviceKey keys = new DeviceKey(List.of(identity));
	private final DeviceIdentity candidate = keys.identities().get(0);
	private final byte[] salt = new byte[16];
	private final byte[] plaintext = "{\"refreshToken\":\"test-only-secret\"}".getBytes(StandardCharsets.UTF_8);

	@Test
	void encryptedDocumentRoundTripsWithoutEmbeddingPlaintext() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);

		assertThat(new String(encrypted, StandardCharsets.ISO_8859_1))
				.doesNotContain("refreshToken", "test-only-secret", identity.value());
		assertThat(AccountCipher.decrypt(encrypted, keys)).isEqualTo(plaintext);
	}

	@Test
	void eachEncryptionGetsAFreshNonce() throws Exception {
		byte[] first = AccountCipher.encrypt(plaintext, candidate, keys, salt);
		byte[] second = AccountCipher.encrypt(plaintext, candidate, keys, salt);

		assertThat(first).isNotEqualTo(second);
		assertThat(AccountCipher.decrypt(first, keys)).isEqualTo(plaintext);
		assertThat(AccountCipher.decrypt(second, keys)).isEqualTo(plaintext);
	}

	@Test
	void ciphertextAndTagTamperingAreRejected() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);
		byte[] changedCiphertext = encrypted.clone();
		changedCiphertext[changedCiphertext.length - 17] ^= 1;
		byte[] changedTag = encrypted.clone();
		changedTag[changedTag.length - 1] ^= 1;

		assertThatThrownBy(() -> AccountCipher.decrypt(changedCiphertext, keys))
				.isInstanceOf(GeneralSecurityException.class);
		assertThatThrownBy(() -> AccountCipher.decrypt(changedTag, keys))
				.isInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void anotherDeviceCannotDecryptTheDocument() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);
		DeviceKey different = new DeviceKey(List.of(
				new DeviceIdentity("system-uuid", "67548fd3-cb04-410b-95a2-720a4c46f987")));

		assertThatThrownBy(() -> AccountCipher.decrypt(encrypted, different))
				.isInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void identicalValueFromAnotherSourceDoesNotMatchTheSavedSource() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);
		DeviceKey different = new DeviceKey(List.of(new DeviceIdentity("disk-serial", identity.value())));

		assertThatThrownBy(() -> AccountCipher.decrypt(encrypted, different))
				.isInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void alteredHeaderIsRejected() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);
		byte[] changedHeader = encrypted.clone();
		changedHeader[0] ^= 1;

		assertThatThrownBy(() -> AccountCipher.decrypt(changedHeader, keys))
				.isInstanceOf(GeneralSecurityException.class);
	}

	@Test
	void truncatedDocumentsAreRejected() throws Exception {
		byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);

		for (int length : new int[] { 0, 7, 8, 19, 20, 35, encrypted.length - 1 }) {
			byte[] truncated = Arrays.copyOf(encrypted, length);
			assertThatThrownBy(() -> AccountCipher.decrypt(truncated, keys))
					.isInstanceOf(GeneralSecurityException.class);
		}
	}
}
