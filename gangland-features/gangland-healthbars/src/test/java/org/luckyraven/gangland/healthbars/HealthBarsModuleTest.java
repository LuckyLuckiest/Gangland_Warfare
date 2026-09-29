package org.luckyraven.gangland.healthbars;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.healthbars.config.HealthBarsFileConfig;
import org.luckyraven.gangland.healthbars.listener.HealthBarListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("HealthBarsModule")
class HealthBarsModuleTest {

	@Test
	@DisplayName("configure registers the file config and the listener package, which matches the listener")
	void configure_declaresConfigAndListenerPackage() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new HealthBarsModule().configure(registrations);

		assertEquals(List.of(HealthBarsFileConfig.class), registrations.configurations());
		assertEquals(List.of(HealthBarsModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(HealthBarsModule.LISTENER_PACKAGE, HealthBarListener.class.getPackageName());
	}
}
