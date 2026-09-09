package org.luckyraven.gangland.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.ConfigurationConstructorScan;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B-1 (0.9.0, phase-D review blocker): companion to the four runtime-module checks and
 * {@code GadgetModuleConfigConstructorTest} — covers the core's own {@code @Configuration} classes
 * ({@code org.luckyraven.gangland.config}, the package {@code GanglandContext} scans). Keystone instantiates every
 * {@code @Configuration} class before any bean phase runs ({@code BeanFactory.java:219-233}, then the phase loop at
 * {@code :251}); at that moment the container holds only {@code Gangland}, {@code GanglandContext},
 * {@code DependencyContainer} and {@code ModuleLoader}. A core configuration asking for anything else would sink
 * bootstrap the same way {@code GadgetModuleConfig}'s old {@code FuelService} parameter did.
 */
class CoreConfigurationConstructorTest {

	@Test
	@DisplayName("no core @Configuration constructor asks for a bean unavailable at configuration-instantiation "
	             + "time")
	void noConfigurationAsksForUnavailableDependency() {
		List<String> violations = ConfigurationConstructorScan.findUnavailableDependencyConfigs(
				"org.luckyraven.gangland.config", getClass().getClassLoader());
		assertTrue(violations.isEmpty(), "Configuration constructors with unavailable dependencies: " + violations);
	}
}
