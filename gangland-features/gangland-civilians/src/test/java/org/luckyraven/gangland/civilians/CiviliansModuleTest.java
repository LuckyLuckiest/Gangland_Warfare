package org.luckyraven.gangland.civilians;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.command.CivilianCommand;
import org.luckyraven.gangland.civilians.database.CivilianSpawnerRepository;
import org.luckyraven.gangland.civilians.listener.civilian.CivilianDamageListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The civilians module's declaration to the host (group H, T-N2): its three configuration classes in declaration
 * order ({@link CiviliansYamlConfig}, {@link CiviliansFileConfig}, {@link CiviliansModuleConfig}), its listener,
 * command and repository packages, and that the declared packages match where the classes actually live. Modelled
 * on {@code MailModuleTest} / {@code CopsNCrooksModuleTest}.
 */
@DisplayName("CiviliansModule")
class CiviliansModuleTest {

	@Test
	@DisplayName("configure registers the three configuration classes and the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CiviliansModule().configure(registrations);

		assertEquals(List.of(CiviliansYamlConfig.class, CiviliansFileConfig.class, CiviliansModuleConfig.class),
				registrations.configurations());
		assertEquals(List.of(CiviliansModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(CiviliansModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(CiviliansModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(CiviliansModule.REPOSITORY_PACKAGE, CivilianSpawnerRepository.class.getPackageName());
		assertTrue(CivilianCommand.class.getPackageName().startsWith(CiviliansModule.COMMAND_PACKAGE));
		assertTrue(CivilianDamageListener.class.getPackageName().startsWith(CiviliansModule.LISTENER_PACKAGE));
	}

	@Test
	@DisplayName("civilians ships top-level /glw commands")
	void commandPackages_isNotEmpty() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CiviliansModule().configure(registrations);

		assertFalse(registrations.commandPackages().isEmpty(), "civilians ships top-level /glw commands");
	}
}
