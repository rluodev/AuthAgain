package dev.rluo.authagain.auth;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import oshi.SystemInfo;
import oshi.hardware.HardwareAbstractionLayer;

/**
 * <strong>DeviceKey</strong><br>
 * Derives and caches account keys from available hardware identifiers.
 */
final class DeviceKey {

	private static final int ITERATIONS = 600_000;
	static final String WINDOWS_DPAPI = "windows-dpapi";
	private static final String DEVICE_COMPOSITE = "device-composite";
	private static final String SYSTEM_COMPOSITE = "system-composite";
	private static final Set<String> SOURCE_KINDS = Set.of(
			"system-uuid", "system-serial", "baseboard-serial", "disk-serial");
	private static final Set<String> PLACEHOLDERS = Set.of(
			"UNKNOWN", "NONE", "NA", "NULL", "DEFAULT", "DEFAULTSTRING", "NOTSPECIFIED",
			"NOTAVAILABLE", "NOTAPPLICABLE", "TOBEFILLEDBYOEM", "SYSTEMSERIALNUMBER",
			"SERIALNUMBER", "BASEBOARDSERIALNUMBER", "BOARDSERIALNUMBER", "OEM",
			"INVALID", "UNDEFINED", "NOTPRESENT", "NOTSET", "NOTPROVIDED", "123456789", "0123456789");
	private final List<DeviceIdentity> _identities;
	private final Map<String, SecretKey> _keys = new HashMap<>();

	DeviceKey() {
		this(discover(), System.getProperty("os.name", "").startsWith("Windows"));
	}

	DeviceKey(List<DeviceIdentity> identities) {
		this(identities, false);
	}

	private DeviceKey(List<DeviceIdentity> identities, boolean windows) {
		List<DeviceIdentity> available = new ArrayList<>();
		for (DeviceIdentity identity : identities) {
			DeviceIdentity normalized = normalize(identity);
			if (normalized != null && !available.contains(normalized)) {
				available.add(normalized);
			}
		}
		available.sort(Comparator.comparing(DeviceIdentity::sourceKind).thenComparing(DeviceIdentity::value));
		List<DeviceIdentity> candidates = new ArrayList<>();
		if (windows) {
			candidates.add(new DeviceIdentity(WINDOWS_DPAPI, ""));
		}
		if (available.stream().map(DeviceIdentity::value).distinct().count() >= 2) {
			candidates.add(composite(DEVICE_COMPOSITE, available));
			List<DeviceIdentity> system = available.stream()
					.filter(identity -> !identity.sourceKind().equals("disk-serial")).toList();
			if (system.size() < available.size() && system.stream().map(DeviceIdentity::value).distinct().count() >= 2) {
				candidates.add(composite(SYSTEM_COMPOSITE, system));
			}
		} else if (!available.isEmpty()) {
			candidates.add(available.get(0));
		}
		_identities = List.copyOf(candidates);
	}

	List<DeviceIdentity> identities() {
		return _identities;
	}

	synchronized SecretKey key(DeviceIdentity identity, byte[] salt) throws GeneralSecurityException {
		if (!_identities.contains(identity) || WINDOWS_DPAPI.equals(identity.sourceKind()) || salt.length != 16) {
			throw new GeneralSecurityException("No usable device identity");
		}
		String cacheKey = identity.sourceKind() + '\0' + identity.value() + '\0'
				+ Base64.getEncoder().encodeToString(salt);
		SecretKey cached = _keys.get(cacheKey);
		if (cached != null) {
			return cached;
		}
		char[] material = identity.value().toCharArray();
		PBEKeySpec spec = new PBEKeySpec(material, salt, ITERATIONS, 256);
		Arrays.fill(material, '\0');
		byte[] bytes = null;
		try {
			bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			SecretKey key = new SecretKeySpec(bytes, "AES");
			_keys.put(cacheKey, key);
			return key;
		} finally {
			spec.clearPassword();
			if (bytes != null) {
				Arrays.fill(bytes, (byte) 0);
			}
		}
	}

	static boolean knownSource(String sourceKind) {
		return SOURCE_KINDS.contains(sourceKind) || DEVICE_COMPOSITE.equals(sourceKind)
				|| SYSTEM_COMPOSITE.equals(sourceKind) || WINDOWS_DPAPI.equals(sourceKind);
	}

	private static DeviceIdentity composite(String sourceKind, List<DeviceIdentity> identities) {
		StringBuilder value = new StringBuilder();
		for (DeviceIdentity identity : identities) {
			value.append(identity.sourceKind()).append(':').append(identity.value().length())
					.append(':').append(identity.value()).append('\n');
		}
		return new DeviceIdentity(sourceKind, value.toString());
	}

	private static DeviceIdentity normalize(DeviceIdentity identity) {
		if (identity == null || identity.sourceKind() == null
				|| !SOURCE_KINDS.contains(identity.sourceKind()) || identity.value() == null) {
			return null;
		}
		String value = identity.value().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
		String compact = value.replaceAll("[^A-Z0-9]", "");
		if (value.length() > 512 || compact.length() < 8 || PLACEHOLDERS.contains(compact)
				|| compact.chars().distinct().count() == 1) {
			return null;
		}
		return new DeviceIdentity(identity.sourceKind(), value);
	}

	private static List<DeviceIdentity> discover() {
		List<DeviceIdentity> identities = new ArrayList<>();
		try {
			HardwareAbstractionLayer hardware = new SystemInfo().getHardware();
			addIdentity(identities, "system-uuid", () -> hardware.getComputerSystem().getHardwareUUID());
			addIdentity(identities, "system-serial", () -> hardware.getComputerSystem().getSerialNumber());
			addIdentity(identities, "baseboard-serial", () -> hardware.getComputerSystem().getBaseboard().getSerialNumber());
			try {
				hardware.getDiskStores().forEach(disk -> addIdentity(identities, "disk-serial", disk::getSerial));
			} catch (RuntimeException | LinkageError ignored) {
			}
		} catch (RuntimeException | LinkageError ignored) {
		}
		return identities;
	}

	private static void addIdentity(List<DeviceIdentity> identities, String sourceKind, Supplier<String> value) {
		try {
			DeviceIdentity identity = normalize(new DeviceIdentity(sourceKind, value.get()));
			if (identity != null) {
				identities.add(identity);
			}
		} catch (RuntimeException | LinkageError ignored) {
		}
	}
}
