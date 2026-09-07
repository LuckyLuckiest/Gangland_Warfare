package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link SettingsRedaction}: the MySQL credentials that {@code Settings.addEachFieldReflection()} copies into
 * the settings map must never reach a player.
 *
 * <p>Before the fix {@code /glw debug settings} passed the raw map to {@code JsonFormatter} and
 * {@code GanglandPlaceholder.getSetting} resolved any placeholder whose name matched a map key, so
 * {@code mysql_password} was printable by anyone who could render a placeholder.
 *
 * <p>Observation #13 (commands-messages-platform.md), docket CM-13.
 */
@DisplayName("SettingsRedaction - credential keys never reach a player-visible dump")
class SettingsRedactionTest {

	@Test
	@DisplayName("the three MySQL connection fields are sensitive in both key styles")
	void isSensitive_coversMysqlCredentials() {
		assertTrue(SettingsRedaction.isSensitive("mysqlPassword"));
		assertTrue(SettingsRedaction.isSensitive("mysqlUsername"));
		assertTrue(SettingsRedaction.isSensitive("mysqlHost"));

		// the placeholder map lowercases and underscores every key
		assertTrue(SettingsRedaction.isSensitive("mysql_password"));
		assertTrue(SettingsRedaction.isSensitive("mysql_username"));
		assertTrue(SettingsRedaction.isSensitive("mysql_host"));
	}

	@Test
	@DisplayName("any key carrying a secret word is sensitive, whatever it is called")
	void isSensitive_matchesSecretMarkers() {
		assertTrue(SettingsRedaction.isSensitive("apiToken"));
		assertTrue(SettingsRedaction.isSensitive("webhook_secret"));
		assertTrue(SettingsRedaction.isSensitive("Backup_Passphrase"));
		assertTrue(SettingsRedaction.isSensitive("storeCredentials"));
	}

	@Test
	@DisplayName("gameplay knobs stay visible")
	void isSensitive_leavesOrdinarySettingsAlone() {
		assertFalse(SettingsRedaction.isSensitive("moneySymbol"));
		assertFalse(SettingsRedaction.isSensitive("gangEnabled"));
		assertFalse(SettingsRedaction.isSensitive("mysqlPort"));
		assertFalse(SettingsRedaction.isSensitive("database"));
		assertFalse(SettingsRedaction.isSensitive(null));
		assertFalse(SettingsRedaction.isSensitive(""));
	}

	@Test
	@DisplayName("redact replaces the credential values and keeps everything else, in order")
	void redact_hidesOnlySensitiveValues() {
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put("moneySymbol", "$");
		settings.put("mysqlHost", "db.example.net");
		settings.put("mysqlUsername", "gangland");
		settings.put("mysqlPassword", "hunter2");
		settings.put("mysqlPort", 3306);

		Map<String, Object> safe = SettingsRedaction.redact(settings);

		assertEquals("$", safe.get("moneySymbol"));
		assertEquals(3306, safe.get("mysqlPort"));
		assertEquals(SettingsRedaction.REDACTED, safe.get("mysqlHost"));
		assertEquals(SettingsRedaction.REDACTED, safe.get("mysqlUsername"));
		assertEquals(SettingsRedaction.REDACTED, safe.get("mysqlPassword"));

		assertFalse(safe.toString().contains("hunter2"), "the password must not survive anywhere in the dump");
		assertIterableEquals(settings.keySet(), safe.keySet(), "key order is preserved");
	}

	@Test
	@DisplayName("the source map is never mutated - SettingsLookupImpl still resolves real values from it")
	void redact_doesNotMutateTheLiveMap() {
		Map<String, Object> settings = new LinkedHashMap<>();
		settings.put("mysqlPassword", "hunter2");

		SettingsRedaction.redact(settings);

		assertEquals("hunter2", settings.get("mysqlPassword"));
	}

	@Test
	void redact_nullMapYieldsEmptyMap() {
		assertTrue(SettingsRedaction.redact(null).isEmpty());
	}

}
