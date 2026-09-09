package org.luckyraven.gangland.civilians;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.CitizensBlindScan;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2/D-fix-1 (smoke row D2, 2026-09-09, Paper 1.21.11, Citizens NOT installed): Gangland failed to enable with the
 * civilians module deployed — {@code NoClassDefFoundError: net/citizensnpcs/api/npc/NPC at
 * Class.getDeclaredMethods0 … at BeanFactory.runPostConstruct(BeanFactory.java:543)}, because
 * {@code CivilianNpcFactory} (a {@code @Bean}-produced instance) declared a private {@code applyHealthBonus(NPC, …)}
 * helper. Pins the requirement going forward: no {@code @Configuration}/{@code @ListenerHandler}/
 * {@code @CommandHandler}/{@code @Repository} class in this module may carry a Citizens type in any of its own
 * declared method/constructor signatures, and no {@code @Configuration} class may ask for a dependency that is not
 * yet in the container when Keystone instantiates it.
 */
class CitizensAndConfigSafetyTest {

	private static final String BASE_PACKAGE = "org.luckyraven.gangland.civilians";

	@Test
	@DisplayName("no scanned civilians class exposes a Citizens type in its own signature")
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
