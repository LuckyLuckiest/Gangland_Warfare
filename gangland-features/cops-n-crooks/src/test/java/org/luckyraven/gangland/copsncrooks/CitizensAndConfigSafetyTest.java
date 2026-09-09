package org.luckyraven.gangland.copsncrooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.CitizensBlindScan;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2/D-fix-1 (smoke row D2, 2026-09-09, Paper 1.21.11, Citizens NOT installed): Gangland failed to enable with the
 * civilians module deployed, and the same reflection crash shape ({@code NoClassDefFoundError} out of Keystone's
 * bean post-construct pass / listener registration) applied here too — {@code HandcuffBribeListener} declared an
 * {@code @EventHandler} parameter of Citizens' {@code NPCRightClickEvent} with no {@code condition =
 * "isCitizensAvailable"} gate, and {@code NpcDamageUnprotectListener} declared a private
 * {@code isShopNpc(NPC)} helper that could not carry that gate (its other two handlers must stay registered
 * unconditionally). Pins the requirement going forward: no {@code @Configuration}/{@code @ListenerHandler}/
 * {@code @CommandHandler}/{@code @Repository} class in this module may carry a Citizens type in any of its own
 * declared method/constructor signatures, and no {@code @Configuration} class may ask for a dependency that is not
 * yet in the container when Keystone instantiates it.
 */
class CitizensAndConfigSafetyTest {

	private static final String BASE_PACKAGE = "org.luckyraven.gangland.copsncrooks";

	@Test
	@DisplayName("no scanned cops-n-crooks class exposes a Citizens type in its own signature")
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
