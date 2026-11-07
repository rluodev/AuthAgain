package dev.rluo.authagain.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <strong>EncryptedAccountStorage</strong><br>
 * Stores device-bound accounts beside the legacy account document.
 */
final class EncryptedAccountStorage {

	private static final int MAX_DOCUMENT_BYTES = 16 * 1024 * 1024;
	private static final int MAX_ENCRYPTED_BYTES = MAX_DOCUMENT_BYTES + 4096;
	private static final SecureRandom RANDOM = new SecureRandom();
	private final Path _legacyFile;
	private final Path _legacyTemp;
	private final Path _encryptedFile;
	private final DeviceKey _keys;
	private final Map<DeviceIdentity, byte[]> _salts = new HashMap<>();
	private DeviceIdentity _preferredIdentity;
	private byte[] _salt;
	private boolean _unreadable;

	EncryptedAccountStorage(Path legacyFile, DeviceKey keys) {
		_legacyFile = legacyFile.toAbsolutePath().normalize();
		_legacyTemp = _legacyFile.resolveSibling("accounts.json.tmp");
		_encryptedFile = _legacyFile.resolveSibling("accounts.enc");
		_keys = keys;
	}

	byte[] read() throws IOException, GeneralSecurityException {
		if (Files.notExists(_encryptedFile, LinkOption.NOFOLLOW_LINKS)) {
			return null;
		}
		try {
			byte[] document = readLimited(_encryptedFile, MAX_ENCRYPTED_BYTES);
			String sourceKind = AccountCipher.sourceKind(document);
			for (DeviceIdentity identity : _keys.identities()) {
				if (identity.sourceKind().equals(sourceKind)) {
					try {
						byte[] plaintext = AccountCipher.decrypt(document, identity, _keys);
						_preferredIdentity = identity;
						_salt = AccountCipher.salt(document);
						_unreadable = false;
						return plaintext;
					} catch (GeneralSecurityException ignored) {
					}
				}
			}
			throw new GeneralSecurityException("Account data cannot be decrypted on this device");
		} catch (IOException | GeneralSecurityException e) {
			_unreadable = true;
			throw e;
		}
	}

	void markUnreadable() {
		_unreadable = true;
	}

	boolean hasEncryptionIdentity() {
		return !_keys.identities().isEmpty();
	}

	void write(byte[] plaintext) throws IOException, GeneralSecurityException {
		if (plaintext.length > MAX_DOCUMENT_BYTES) {
			throw new IOException("Account document is too large");
		}
		Files.createDirectories(_encryptedFile.getParent());
		if (!Files.notExists(_encryptedFile, LinkOption.NOFOLLOW_LINKS) && !_unreadable) {
			try {
				byte[] previous = read();
				if (previous != null) {
					Arrays.fill(previous, (byte) 0);
				}
			} catch (IOException | GeneralSecurityException ignored) {
				_unreadable = true;
			}
		}
		List<DeviceIdentity> identities = new ArrayList<>(_keys.identities());
		if (_preferredIdentity != null && identities.remove(_preferredIdentity)) {
			int preferredIndex = !identities.isEmpty() && identities.get(0).sourceKind().equals("windows-dpapi") ? 1 : 0;
			identities.add(preferredIndex, _preferredIdentity);
		}
		Exception failure = null;
		for (DeviceIdentity identity : identities) {
			try {
				byte[] salt = _salt != null && identity.equals(_preferredIdentity) ? _salt
						: _salts.computeIfAbsent(identity, ignored -> {
							byte[] generated = new byte[16];
							RANDOM.nextBytes(generated);
							return generated;
						});
				writeEncrypted(plaintext, identity, salt);
				_preferredIdentity = identity;
				_salt = salt;
				_unreadable = false;
				return;
			} catch (IOException | GeneralSecurityException e) {
				if (failure != null) {
					e.addSuppressed(failure);
				}
				failure = e;
			}
		}
		if (failure instanceof IOException e) {
			throw e;
		}
		if (failure instanceof GeneralSecurityException e) {
			throw e;
		}
		throw new GeneralSecurityException("No usable device identity");
	}

	private void writeEncrypted(byte[] plaintext, DeviceIdentity identity, byte[] salt)
			throws IOException, GeneralSecurityException {
		byte[] encrypted = AccountCipher.encrypt(plaintext, identity, _keys, salt);
		Path temporary = Files.createTempFile(_encryptedFile.getParent(), ".accounts-", ".tmp");
		Path backup = null;
		try {
			writeFile(temporary, encrypted);
			byte[] verified = AccountCipher.decrypt(readLimited(temporary, MAX_ENCRYPTED_BYTES), identity, _keys);
			try {
				if (!MessageDigest.isEqual(plaintext, verified)) {
					throw new GeneralSecurityException("Account storage verification failed");
				}
			} finally {
				Arrays.fill(verified, (byte) 0);
			}
			if (!Files.notExists(_encryptedFile, LinkOption.NOFOLLOW_LINKS)) {
				requireRegularFile(_encryptedFile);
				if (_unreadable) {
					backup = _encryptedFile.resolveSibling("accounts.enc.unreadable");
					if (!Files.notExists(backup, LinkOption.NOFOLLOW_LINKS)) {
						backup = _encryptedFile.resolveSibling("accounts.enc.unreadable." + UUID.randomUUID());
					}
					move(_encryptedFile, backup, false);
				}
			}
			try {
				move(temporary, _encryptedFile, true);
			} catch (IOException e) {
				if (backup != null && Files.notExists(_encryptedFile, LinkOption.NOFOLLOW_LINKS)) {
					try {
						move(backup, _encryptedFile, false);
					} catch (IOException restore) {
						e.addSuppressed(restore);
					}
				}
				throw e;
			}
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	List<byte[]> readLegacy() throws IOException {
		List<byte[]> documents = new ArrayList<>();
		if (!Files.notExists(_legacyFile, LinkOption.NOFOLLOW_LINKS)) {
			documents.add(readLimited(_legacyFile, MAX_DOCUMENT_BYTES));
		}
		if (!Files.notExists(_legacyTemp, LinkOption.NOFOLLOW_LINKS)) {
			documents.add(readLimited(_legacyTemp, MAX_DOCUMENT_BYTES));
		}
		return documents;
	}

	void writeLegacy(byte[] document) throws IOException {
		Files.createDirectories(_legacyFile.getParent());
		if (!Files.notExists(_legacyFile, LinkOption.NOFOLLOW_LINKS)) {
			requireRegularFile(_legacyFile);
		}
		if (!Files.notExists(_legacyTemp, LinkOption.NOFOLLOW_LINKS)) {
			requireRegularFile(_legacyTemp);
		}
		writeFile(_legacyTemp, document);
		move(_legacyTemp, _legacyFile, true);
	}

	void deleteReadableEncrypted() throws IOException, GeneralSecurityException {
		if (_unreadable) {
			return;
		}
		byte[] document = read();
		if (document != null) {
			Arrays.fill(document, (byte) 0);
			Files.deleteIfExists(_encryptedFile);
		}
	}

	boolean hasLegacyFiles() {
		return !Files.notExists(_legacyFile, LinkOption.NOFOLLOW_LINKS)
				|| !Files.notExists(_legacyTemp, LinkOption.NOFOLLOW_LINKS);
	}

	void deleteLegacy() throws IOException {
		IOException failure = null;
		try {
			Files.deleteIfExists(_legacyFile);
		} catch (IOException e) {
			failure = e;
		}
		try {
			Files.deleteIfExists(_legacyTemp);
		} catch (IOException e) {
			if (failure == null) {
				failure = e;
			} else {
				failure.addSuppressed(e);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	void clear() throws IOException {
		IOException failure = null;
		try {
			Files.deleteIfExists(_encryptedFile);
			if (Files.isDirectory(_encryptedFile.getParent(), LinkOption.NOFOLLOW_LINKS)) {
				try (var paths = Files.newDirectoryStream(_encryptedFile.getParent())) {
					for (Path file : paths) {
						String name = file.getFileName().toString();
						if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
								&& name.matches("\\.accounts-[0-9]+\\.tmp|accounts\\.enc\\.unreadable"
										+ "(?:\\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?")) {
							Files.deleteIfExists(file);
						}
					}
				}
			}
		} catch (IOException e) {
			failure = e;
		} finally {
			try {
				deleteLegacy();
			} catch (IOException e) {
				if (failure == null) {
					throw e;
				}
				failure.addSuppressed(e);
			}
		}
		if (failure != null) {
			throw failure;
		}
	}

	private static void move(Path source, Path target, boolean replace) throws IOException {
		try {
			if (replace) {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
			}
		} catch (AtomicMoveNotSupportedException e) {
			if (replace) {
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			} else {
				Files.move(source, target);
			}
		}
	}

	private static void requireRegularFile(Path path) throws IOException {
		if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Account storage is not a regular file");
		}
	}

	private static byte[] readLimited(Path path, int limit) throws IOException {
		requireRegularFile(path);
		try (InputStream input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
			byte[] bytes = input.readNBytes(limit + 1);
			if (bytes.length > limit) {
				throw new IOException("Account storage file is too large");
			}
			return bytes;
		}
	}

	private static void writeFile(Path path, byte[] data) throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE,
				StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)) {
			ByteBuffer buffer = ByteBuffer.wrap(data);
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
			channel.force(true);
		}
	}
}
