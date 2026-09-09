package org.luckyraven.gangland.gadget.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-1 (0.9.0, phase-D review blocker, applied within D-fix-1): {@code GadgetModuleConfig}'s constructor used to ask
 * for {@code FuelService} — Keystone instantiates every {@code @Configuration} class before any bean phase runs
 * ({@code BeanFactory.java:219-233}, then the phase loop at {@code :251}), and only {@code GanglandContext}/
 * {@code DependencyContainer}/{@code Gangland}/{@code ModuleLoader} are in the container at that moment, so with the
 * gadget module deployed bootstrap threw {@code IllegalStateException: Failed to instantiate @Configuration class
 * …GadgetModuleConfig}. Fixed by dropping the {@code FuelService} constructor parameter/field and the
 * {@code @PostConstruct} method, moving {@code fuelService.setFuelSinkPredicate(this::isJetpackFuelSink)} into the
 * {@code jetpackService(FuelService, GadgetPhysicsConfig)} {@code @Bean} method instead (same instance, correct
 * phase).
 */
class GadgetModuleConfigConstructorTest {

	@Test
	@DisplayName("no @Configuration constructor in the gadget module asks for a bean unavailable at "
	             + "configuration-instantiation time")
	void noConfigurationAsksForUnavailableDependency() {
		List<String> violations = ConfigurationConstructorScan.findUnavailableDependencyConfigs(
				"org.luckyraven.gangland.gadget", getClass().getClassLoader());
		assertTrue(violations.isEmpty(), "Configuration constructors with unavailable dependencies: " + violations);
	}
}
