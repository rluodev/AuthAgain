package dev.rluo.authagain.auth;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.sun.jna.platform.win32.Crypt32Util;
import com.sun.jna.platform.win32.WinCrypt;

/**
 * <strong>AccountCipher</strong><br>
 * Versioned, authenticated encryption of the entire account document.
 */
final class AccountCipher {

	private static final byte[] MAGIC = "AAGCM002".getBytes(StandardCharsets.US_ASCII);
	private static final int NONCE_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final int KEY_BYTES = 32;
	private static final int MAX_WRAPPED_KEY_BYTES = 3072;
	private static final SecureRandom RANDOM = new SecureRandom();

	private AccountCipher() {
	}

	static byte[] encrypt(byte[] plaintext, DeviceIdentity identity, DeviceKey keys, byte[] salt)
			throws GeneralSecurityException {
		if (!keys.identities().contains(identity) || salt.length != 16) {
			throw new GeneralSecurityException("Unsupported account encryption parameters");
		}
		byte[] source = identity.sourceKind().getBytes(StandardCharsets.US_ASCII);
		ByteBuffer header = ByteBuffer.allocate(MAGIC.length + 1 + source.length + salt.length
				+ Short.BYTES + MAX_WRAPPED_KEY_BYTES);
		header.put(MAGIC).put((byte) source.length).put(source).put(salt);
		SecretKey key;
		if (DeviceKey.WINDOWS_DPAPI.equals(identity.sourceKind())) {
			byte[] bytes = new byte[KEY_BYTES];
			RANDOM.nextBytes(bytes);
			try {
				byte[] wrapped = windowsKey(bytes, salt, true);
				if (wrapped.length == 0 || wrapped.length > MAX_WRAPPED_KEY_BYTES) {
					throw new GeneralSecurityException("Invalid Windows protected key");
				}
				header.putShort((short) wrapped.length).put(wrapped);
				key = new SecretKeySpec(bytes, "AES");
			} finally {
				Arrays.fill(bytes, (byte) 0);
			}
		} else {
			key = keys.key(identity, salt);
		}
		byte[] associatedData = Arrays.copyOf(header.array(), header.position());
		byte[] nonce = new byte[NONCE_BYTES];
		RANDOM.nextBytes(nonce);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
		cipher.updateAAD(associatedData);
		byte[] encrypted = cipher.doFinal(plaintext);
		return ByteBuffer.allocate(associatedData.length + nonce.length + encrypted.length)
				.put(associatedData).put(nonce).put(encrypted).array();
	}

	static byte[] decrypt(byte[] document, DeviceKey keys) throws GeneralSecurityException {
		String sourceKind = sourceKind(document);
		for (DeviceIdentity identity : keys.identities()) {
			if (identity.sourceKind().equals(sourceKind)) {
				try {
					return decrypt(document, identity, keys);
				} catch (GeneralSecurityException ignored) {
				}
			}
		}
		throw new GeneralSecurityException("Account data cannot be decrypted on this device");
	}

	static byte[] decrypt(byte[] document, DeviceIdentity identity, DeviceKey keys)
			throws GeneralSecurityException {
		int headerLength = headerLength(document);
		if (!keys.identities().contains(identity) || !sourceKind(document).equals(identity.sourceKind())) {
			throw new GeneralSecurityException("No matching device identity");
		}
		byte[] salt = salt(document);
		SecretKey key;
		if (DeviceKey.WINDOWS_DPAPI.equals(identity.sourceKind())) {
			byte[] wrapped = Arrays.copyOfRange(document, saltEnd(document) + Short.BYTES, headerLength);
			byte[] bytes = windowsKey(wrapped, salt, false);
			try {
				if (bytes.length != KEY_BYTES) {
					throw new GeneralSecurityException("Invalid Windows account key");
				}
				key = new SecretKeySpec(bytes, "AES");
			} finally {
				Arrays.fill(bytes, (byte) 0);
			}
		} else {
			key = keys.key(identity, salt);
		}
		byte[] nonce = Arrays.copyOfRange(document, headerLength, headerLength + NONCE_BYTES);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
		cipher.updateAAD(document, 0, headerLength);
		return cipher.doFinal(document, headerLength + NONCE_BYTES, document.length - headerLength - NONCE_BYTES);
	}

	static String sourceKind(byte[] document) throws GeneralSecurityException {
		headerLength(document);
		return new String(document, MAGIC.length + 1, Byte.toUnsignedInt(document[MAGIC.length]),
				StandardCharsets.US_ASCII);
	}

	static byte[] salt(byte[] document) throws GeneralSecurityException {
		headerLength(document);
		int saltEnd = saltEnd(document);
		return Arrays.copyOfRange(document, saltEnd - 16, saltEnd);
	}

	private static int saltEnd(byte[] document) {
		return MAGIC.length + 1 + Byte.toUnsignedInt(document[MAGIC.length]) + 16;
	}

	private static int headerLength(byte[] document) throws GeneralSecurityException {
		if (document.length < MAGIC.length + 1 || !Arrays.equals(MAGIC, Arrays.copyOf(document, MAGIC.length))) {
			throw new GeneralSecurityException("Unsupported encrypted account document");
		}
		int sourceLength = Byte.toUnsignedInt(document[MAGIC.length]);
		int headerLength = saltEnd(document);
		if (document.length < headerLength + NONCE_BYTES + TAG_BITS / 8) {
			throw new GeneralSecurityException("Truncated encrypted account document");
		}
		String sourceKind = new String(document, MAGIC.length + 1, sourceLength, StandardCharsets.US_ASCII);
		if (!DeviceKey.knownSource(sourceKind)) {
			throw new GeneralSecurityException("Unsupported device identity source");
		}
		if (DeviceKey.WINDOWS_DPAPI.equals(sourceKind)) {
			int wrappedLength = Short.toUnsignedInt(ByteBuffer.wrap(document, headerLength, Short.BYTES).getShort());
			headerLength += Short.BYTES + wrappedLength;
			if (wrappedLength == 0 || wrappedLength > MAX_WRAPPED_KEY_BYTES
					|| document.length < headerLength + NONCE_BYTES + TAG_BITS / 8) {
				throw new GeneralSecurityException("Invalid Windows protected key");
			}
		}
		return headerLength;
	}

	private static byte[] windowsKey(byte[] data, byte[] salt, boolean protect) throws GeneralSecurityException {
		try {
			int flags = WinCrypt.CRYPTPROTECT_UI_FORBIDDEN;
			return protect ? Crypt32Util.cryptProtectData(data, salt, flags, "", null)
					: Crypt32Util.cryptUnprotectData(data, salt, flags, null);
		} catch (RuntimeException | LinkageError e) {
			throw new GeneralSecurityException("Windows account key protection failed", e);
		}
	}
}
