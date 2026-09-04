package org.luckyraven.gangland.file.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SettingsLookupImpl#isEnabled(String)} truth table, driven by writing directly into the live
 * {@code Settings.getSettingsMap()} (the getter returns the real mutable map, not a defensive copy — legitimate
 * public API, no reflection needed). Every test removes the probe key it added in {@code @AfterEach} so this class
 * never leaks state into other test classes sharing the JVM.
 *
 * <p>The last test pins Observation #3 (commands-messages-platform.md, High confidence):
 * {@code @CommandHandler(condition = "isGangEnabled")} on {@code GangCommand} names a getter *method*, but
 * {@code SettingsLookupImpl} looks the condition string up directly in {@code settingsMap}, which is keyed by
 * *field* names ({@code gangEnabled}) — so the condition never matches and {@code GangCommand} silently never
 * registers.
 */
@DisplayName("SettingsLookupImpl")
class SettingsLookupImplTest {

	private static final String PROBE_KEY = "settingsLookupImplTestProbeKey";

	private final SettingsLookupImpl lookup = new SettingsLookupImpl();

	@AfterEach
	void removeProbeKey() {
		Settings.getSettingsMap().remove(PROBE_KEY);
		Settings.getSettingsMap().remove("gangEnabled");
	}

	@Test
	@DisplayName("a Boolean.TRUE value is enabled")
	void booleanTrue_isEnabled() {
		Settings.getSettingsMap().put(PROBE_KEY, Boolean.TRUE);

		assertTrue(lookup.isEnabled(PROBE_KEY));
	}

	@Test
	@DisplayName("a Boolean.FALSE value is not enabled")
	void booleanFalse_isNotEnabled() {
		Settings.getSettingsMap().put(PROBE_KEY, Boolean.FALSE);

		assertFalse(lookup.isEnabled(PROBE_KEY));
	}

	@Test
	@DisplayName("the string \"true\" (any case) parses as enabled")
	void stringTrue_caseInsensitive_isEnabled() {
		Settings.getSettingsMap().put(PROBE_KEY, "TRUE");
		assertTrue(lookup.isEnabled(PROBE_KEY));

		Settings.getSettingsMap().put(PROBE_KEY, "true");
		assertTrue(lookup.isEnabled(PROBE_KEY));
	}

	@Test
	@DisplayName("a non-\"true\" string such as \"yes\" is NOT enabled — Boolean.parseBoolean only accepts \"true\"")
	void stringYes_isNotEnabled() {
		Settings.getSettingsMap().put(PROBE_KEY, "yes");

		assertFalse(lookup.isEnabled(PROBE_KEY));
	}

	@Test
	@DisplayName("a key absent from the map fails closed")
	void missingKey_isNotEnabled() {
		assertFalse(lookup.isEnabled("definitelyNotAKeyAnywhere"));
	}

	@Test
	@DisplayName("a non-Boolean, non-String value fails closed")
	void nonBooleanNonStringValue_isNotEnabled() {
		Settings.getSettingsMap().put(PROBE_KEY, 42);

		assertFalse(lookup.isEnabled(PROBE_KEY));
	}

	@Test
	@DisplayName("Observation #3 (commands-messages-platform.md): condition=\"isGangEnabled\" never matches the " +
			"field-name-keyed settingsMap entry \"gangEnabled\", so it always fails closed")
	void observation3_getterNamedConditionNeverMatchesFieldNamedKey() {
		// Settings.addEachFieldReflection() keys settingsMap by Java field name, e.g. "gangEnabled" — never by the
		// "isGangEnabled" getter name @CommandHandler(condition = ...) actually names.
		Settings.getSettingsMap().put("gangEnabled", Boolean.TRUE);

		assertFalse(lookup.isEnabled("isGangEnabled"),
				"current behaviour: settingsMap.get(\"isGangEnabled\") is always null because the map is keyed " +
						"by field name (\"gangEnabled\"), not getter name — so GangCommand's condition can never " +
						"be true and /glw gang never registers, regardless of Gang.Enabled in settings.yml");
	}
}
