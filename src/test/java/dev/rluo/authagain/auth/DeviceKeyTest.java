package dev.rluo.authagain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;

import org.junit.jupiter.api.Test;

class DeviceKeyTest {

	@Test
	void unknownAndPlaceholderIdentifiersCannotBecomeEncryptionKeys() {
		DeviceKey keys = new DeviceKey(List.of(
				new DeviceIdentity("system-uuid", "unknown"),
				new DeviceIdentity("system-uuid", "00000000-0000-0000-0000-000000000000"),
				new DeviceIdentity("system-uuid", "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"),
				new DeviceIdentity("system-serial", "To be filled by O.E.M."),
				new DeviceIdentity("baseboard-serial", "Default string"),
				new DeviceIdentity("disk-serial", "")));

		assertThat(keys.identities()).isEmpty();
	}

	@Test
	void unsupportedIdentitySourcesAreRejected() {
		DeviceKey keys = new DeviceKey(List.of(
				new DeviceIdentity("hostname", "test-computer"),
				new DeviceIdentity("username", "test-user")));

		assertThat(keys.identities()).isEmpty();
	}

	@Test
	void derivedKeyUsesAes256AndDependsOnDeviceAndSalt() throws Exception {
		DeviceIdentity identity = new DeviceIdentity("system-uuid", "987d126a-ace7-48be-a91f-83b8cf914c28");
		DeviceIdentity other = new DeviceIdentity("system-uuid", "67548fd3-cb04-410b-95a2-720a4c46f987");
		DeviceKey keys = new DeviceKey(List.of(identity));
		DeviceKey otherKeys = new DeviceKey(List.of(other));
		byte[] salt = new byte[16];
		byte[] anotherSalt = new byte[16];
		anotherSalt[0] = 1;
		DeviceIdentity candidate = keys.identities().get(0);
		byte[] key = keys.key(candidate, salt).getEncoded();

		assertThat(key).hasSize(32);
		assertThat(keys.key(candidate, anotherSalt).getEncoded()).isNotEqualTo(key);
		assertThat(otherKeys.key(otherKeys.identities().get(0), salt).getEncoded()).isNotEqualTo(key);
		assertThat(new DeviceKey(List.of(identity)).key(candidate, salt).getEncoded()).isEqualTo(key);
	}

	@Test
	void noCandidateCanBeDecryptedWithOnlyOneMatchingDisk() throws Exception {
		DeviceIdentity first = new DeviceIdentity("disk-serial", "AA-TEST-DISK-7A6B");
		DeviceIdentity second = new DeviceIdentity("disk-serial", "AA-TEST-DISK-46F9");
		DeviceKey keys = new DeviceKey(List.of(first, second));
		DeviceKey reordered = new DeviceKey(List.of(second, first));
		DeviceKey firstOnly = new DeviceKey(List.of(first));
		DeviceKey secondOnly = new DeviceKey(List.of(second));
		byte[] plaintext = "test-only-account-document".getBytes(StandardCharsets.UTF_8);
		byte[] salt = new byte[16];

		assertThat(keys.identities()).isNotEmpty();
		for (DeviceIdentity candidate : keys.identities()) {
			byte[] encrypted = AccountCipher.encrypt(plaintext, candidate, keys, salt);
			assertThat(AccountCipher.decrypt(encrypted, reordered)).isEqualTo(plaintext);
			assertThatThrownBy(() -> AccountCipher.decrypt(encrypted, firstOnly))
					.isInstanceOf(GeneralSecurityException.class);
			assertThatThrownBy(() -> AccountCipher.decrypt(encrypted, secondOnly))
					.isInstanceOf(GeneralSecurityException.class);
		}
	}

	@Test
	void stableSystemFallbackStillRequiresBothSystemIdentifiers() throws Exception {
		DeviceIdentity uuid = new DeviceIdentity("system-uuid", "987d126a-ace7-48be-a91f-83b8cf914c28");
		DeviceIdentity board = new DeviceIdentity("baseboard-serial", "AA-TEST-BOARD-126A");
		DeviceIdentity disk = new DeviceIdentity("disk-serial", "AA-TEST-DISK-7A6B");
		DeviceKey keys = new DeviceKey(List.of(uuid, board, disk));
		DeviceIdentity changedDisk = new DeviceIdentity("disk-serial", "AA-TEST-DISK-46F9");
		DeviceKey changed = new DeviceKey(List.of(uuid, board, changedDisk));
		DeviceKey partial = new DeviceKey(List.of(uuid, changedDisk));
		byte[] plaintext = "test-only-account-document".getBytes(StandardCharsets.UTF_8);
		byte[] salt = new byte[16];

		assertThat(keys.identities()).extracting(DeviceIdentity::sourceKind)
				.containsExactly("device-composite", "system-composite");
		byte[] full = AccountCipher.encrypt(plaintext, keys.identities().get(0), keys, salt);
		byte[] fallback = AccountCipher.encrypt(plaintext, keys.identities().get(1), keys, salt);
		assertThatThrownBy(() -> AccountCipher.decrypt(full, changed))
				.isInstanceOf(GeneralSecurityException.class);
		assertThat(AccountCipher.decrypt(fallback, changed)).isEqualTo(plaintext);
		assertThatThrownBy(() -> AccountCipher.decrypt(fallback, partial))
				.isInstanceOf(GeneralSecurityException.class);
	}
}
