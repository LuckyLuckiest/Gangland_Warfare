package org.luckyraven.gangland.healthbars;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.CitizensBlindScan;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The civilians module's reflection-safety checks, applied to the health-bar module's scanned classes. */
class CitizensAndConfigSafetyTest {

	private static final String BASE_PACKAGE = "org.luckyraven.gangland.healthbars";

	@Test
	@DisplayName("no scanned health-bar class exposes a Citizens type in its own signature")
	void noScannedClassCrashesWithoutCitizens() {
		List<String> unsafe = CitizensBlindScan.findUnsafeClasses(BASE_PACKAGE);
		assertTrue(unsafe.isEmpty(), "Classes unsafe without Citizens: " + unsafe);
	}

	@Test
	@DisplayName("no @Configuration constructor asks for a bean unavailable at configuration-instantiation time")
	void noConfigurationAsksForUnavailableDependency() {
		List<String> violations = ConfigurationConstructorScan.findUnavailableDependencyConfigs(
				BASE_PACKAGE, getClass().getClassLoader());
		assertTrue(violations.isEmpty(), "Configuration constructors with unavailable dependencies: " + violations);
	}
}
