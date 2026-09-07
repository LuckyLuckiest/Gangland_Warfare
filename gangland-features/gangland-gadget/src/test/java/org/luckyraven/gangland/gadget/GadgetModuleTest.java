package org.luckyraven.gangland.gadget;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gadget.command.CarCommand;
import org.luckyraven.gangland.gadget.config.GadgetFileConfig;
import org.luckyraven.gangland.gadget.config.GadgetModuleConfig;
import org.luckyraven.gangland.gadget.database.ParkedCarRepository;
import org.luckyraven.gangland.gadget.listener.jetpack.JetpackSessionLifecycleListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gadget module's declaration to the host (flip 2 of the 2026-09-07 module split): the two configuration
 * classes in declaration order, its listener/command/repository packages, and that the declared packages match
 * where the classes actually live. Modelled on {@code MailModuleTest} / {@code CopsNCrooksModuleTest}.
 */
@DisplayName("GadgetModule")
class GadgetModuleTest {

	@Test
	@DisplayName("configure registers GadgetFileConfig and GadgetModuleConfig plus the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new GadgetModule().configure(registrations);

		assertEquals(List.of(GadgetFileConfig.class, GadgetModuleConfig.class), registrations.configurations());
		assertEquals(List.of(GadgetModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(GadgetModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(GadgetModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertTrue(JetpackSessionLifecycleListener.class.getPackageName().startsWith(GadgetModule.LISTENER_PACKAGE));
		assertEquals(GadgetModule.COMMAND_PACKAGE, CarCommand.class.getPackageName());
		assertEquals(GadgetModule.REPOSITORY_PACKAGE, ParkedCarRepository.class.getPackageName());
	}
}
