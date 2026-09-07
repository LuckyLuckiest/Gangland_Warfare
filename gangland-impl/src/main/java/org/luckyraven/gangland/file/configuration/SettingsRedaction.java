package org.luckyraven.gangland.file.configuration;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hides credential-bearing {@link Settings} values from anything that prints the settings map.
 *
 * <p>{@code Settings.addEachFieldReflection()} copies <em>every</em> static field into
 * {@link Settings#getSettingsMap()}, so {@code mysqlHost}, {@code mysqlUsername} and {@code mysqlPassword} end up
 * beside the harmless gameplay knobs. Two readers expose that map to players: {@code /glw debug settings} dumps it
 * to chat, and {@code GanglandPlaceholder} resolves any placeholder that matches a key of
 * {@link Settings#getSettingsPlaceholder()} — which means a sign, hologram or chat-format placeholder could print
 * the database password to anyone.
 *
 * <p>Both readers filter through here. The maps themselves are left untouched, because
 * {@code SettingsLookupImpl} resolves real values out of them for conditional beans.
 *
 * <p>Observation #13 (commands-messages-platform.md), docket CM-13.
 */
public final class SettingsRedaction {

	/** What a sensitive value is replaced with in any player-visible dump. */
	public static final String REDACTED = "****";

	/** A key whose normalised form contains any of these is a secret regardless of the surrounding words. */
	private static final Set<String> SENSITIVE_MARKERS = Set.of("password", "passphrase", "secret", "token",
	                                                            "credential");

	/** Keys that carry no secret-sounding word but still describe how to reach the database. */
	private static final Set<String> SENSITIVE_KEYS = Set.of("mysqlhost", "mysqluser", "mysqlusername");

	private SettingsRedaction() {
	}

	/**
	 * Decides whether a settings key must never be shown to a player.
	 *
	 * @param key a field-style key ({@code mysqlPassword}) or its placeholder form ({@code mysql_password})
	 *
	 * @return {@code true} when the value behind the key is a credential
	 */
	public static boolean isSensitive(String key) {
		if (key == null || key.isEmpty()) return false;

		String normalised = normalise(key);

		if (SENSITIVE_KEYS.contains(normalised)) return true;

		for (String marker : SENSITIVE_MARKERS) {
			if (normalised.contains(marker)) return true;
		}

		return false;
	}

	/**
	 * Copies {@code settings}, replacing every sensitive value with {@link #REDACTED}. Key order is preserved so a
	 * redacted dump still reads like the original.
	 *
	 * @param settings the live settings or placeholder map; never mutated
	 *
	 * @return a safe-to-print copy
	 */
	public static Map<String, Object> redact(Map<String, Object> settings) {
		Map<String, Object> safe = new LinkedHashMap<>();

		if (settings == null) return safe;

		for (Map.Entry<String, Object> entry : settings.entrySet()) {
			String key = entry.getKey();

			safe.put(key, isSensitive(key) ? REDACTED : entry.getValue());
		}

		return safe;
	}

	private static String normalise(String key) {
		return key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
	}

}
