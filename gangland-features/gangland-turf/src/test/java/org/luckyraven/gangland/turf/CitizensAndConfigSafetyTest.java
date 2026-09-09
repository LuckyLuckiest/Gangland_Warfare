package org.luckyraven.gangland.turf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.CitizensBlindScan;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2/D-fix-1 (smoke row D2, 2026-09-09, Paper 1.21.11, Citizens NOT installed): sibling regression to the civilians
 * module's {@code NoClassDefFoundError} crash out of Keystone's bean post-construct/listener-registration reflection
 * pass. This module was already fixed ahead of D2 (REVIEW-gangland-RI.md finding I-2 —
 * {@code TurfPowerupInteractListener} carries {@code condition = "isCitizensAvailable"} and
 * {@code TurfPowerupManager} keeps every Citizens type out of its own declared signatures); this test pins that so
 * it cannot regress, and covers the same {@code @Configuration}-constructor-availability rule (B-1) other modules
 * needed fixed.
 */
class CitizensAndConfigSafetyTest {

	private static final String BASE_PACKAGE = "org.luckyraven.gangland.turf";

	@Test
	@DisplayName("no scanned turf class exposes a Citizens type in its own signature")
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
