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
 * <p>The last tests cover CM-03 (Observation #3, commands-messages-platform.md):
 * {@code @CommandHandler(condition = "isGangEnabled")} on {@code GangCommand} names a getter *method*, but
 * {@code settingsMap} is keyed by *field* names ({@code gangEnabled}) — so the direct lookup never matched and
 * {@code GangCommand} silently never registered. The lookup now falls back to the JavaBeans property name derived
 * from an {@code is}/{@code get} prefix.
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
	@DisplayName("CM-03: condition=\"isGangEnabled\" resolves the field-name-keyed settingsMap entry \"gangEnabled\"")
	void getterNamedCondition_resolvesFieldNamedKey() {
		// Settings.addEachFieldReflection() keys settingsMap by Java field name, e.g. "gangEnabled" — never by the
		// "isGangEnabled" getter name @CommandHandler(condition = ...) actually names.
		Settings.getSettingsMap().put("gangEnabled", Boolean.TRUE);

		assertTrue(lookup.isEnabled("isGangEnabled"),
				"an is-prefixed getter-style condition must fall back to its JavaBeans property name, otherwise " +
						"GangCommand's condition can never be true and /glw gang never registers, regardless of " +
						"Gang.Enable in settings.yml");
	}

	@Test
	@DisplayName("CM-03: a getter-style condition still fails closed when the underlying field is false")
	void getterNamedCondition_falseField_isNotEnabled() {
		Settings.getSettingsMap().put("gangEnabled", Boolean.FALSE);

		assertFalse(lookup.isEnabled("isGangEnabled"));
	}

	@Test
	@DisplayName("CM-03: a get-prefixed condition resolves the same way")
	void getPrefixedCondition_resolvesFieldNamedKey() {
		Settings.getSettingsMap().put("gangEnabled", Boolean.TRUE);

		assertTrue(lookup.isEnabled("getGangEnabled"));
	}

	@Test
	@DisplayName("CM-03: the getter fallback never invents a key — an unknown property still fails closed")
	void getterNamedCondition_unknownProperty_isNotEnabled() {
		assertFalse(lookup.isEnabled("isDefinitelyNotASetting"));
	}

	@Test
	@DisplayName("CM-03: a direct hit always wins over the getter fallback")
	void directHit_winsOverGetterFallback() {
		Settings.getSettingsMap().put("isGangEnabled", Boolean.FALSE);
		Settings.getSettingsMap().put("gangEnabled", Boolean.TRUE);

		assertFalse(lookup.isEnabled("isGangEnabled"));

		Settings.getSettingsMap().remove("isGangEnabled");
	}

	@Test
	@DisplayName("a key that merely starts with \"is\" but is not getter-shaped is not rewritten")
	void nonGetterShapedKey_isNotRewritten() {
		// "island" -> the remainder "land" is lowercase, so it is not a getter name and must not become "land".
		Settings.getSettingsMap().put("land", Boolean.TRUE);

		assertFalse(lookup.isEnabled("island"));

		Settings.getSettingsMap().remove("land");
	}
}
