package org.luckyraven.gangland.copsncrooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.cops.CopCommand;
import org.luckyraven.gangland.copsncrooks.config.BankerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksFileConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.config.TraderModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.TurfNpcsModuleConfig;
import org.luckyraven.gangland.copsncrooks.database.DetainmentRepository;
import org.luckyraven.gangland.copsncrooks.listener.trader.TraderBuyListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cops-n-crooks module's declaration to the host (flip 1 of the 2026-09-07 module split): the six configuration
 * classes in declaration order, its listener/command/repository packages, and that the declared packages match
 * where the classes actually live. Modelled on {@code MailModuleTest}.
 */
@DisplayName("CopsNCrooksModule")
class CopsNCrooksModuleTest {

	@Test
	@DisplayName("configure registers the six configuration classes and the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertEquals(List.of(CopsNCrooksYamlConfig.class, CopsNCrooksFileConfig.class,
				CopsNCrooksModuleConfig.class, BankerModuleConfig.class, TraderModuleConfig.class,
				TurfNpcsModuleConfig.class), registrations.configurations());
		assertEquals(List.of(CopsNCrooksModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(CopsNCrooksModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(CopsNCrooksModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(CopsNCrooksModule.REPOSITORY_PACKAGE, DetainmentRepository.class.getPackageName());
		assertTrue(CopCommand.class.getPackageName().startsWith(CopsNCrooksModule.COMMAND_PACKAGE));
		assertTrue(TraderBuyListener.class.getPackageName().startsWith(CopsNCrooksModule.LISTENER_PACKAGE));
	}

	@Test
	@DisplayName("copsncrooks ships top-level /glw commands")
	void commandPackages_isNotEmpty() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertFalse(registrations.commandPackages().isEmpty(), "copsncrooks ships top-level /glw commands");
	}
}
