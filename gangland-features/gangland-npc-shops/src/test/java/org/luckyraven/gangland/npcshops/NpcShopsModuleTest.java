package org.luckyraven.gangland.npcshops;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npcshops.command.banker.BankerCommand;
import org.luckyraven.gangland.npcshops.config.BankerModuleConfig;
import org.luckyraven.gangland.npcshops.config.NpcShopsModuleConfig;
import org.luckyraven.gangland.npcshops.config.NpcShopsYamlConfig;
import org.luckyraven.gangland.npcshops.config.TraderModuleConfig;
import org.luckyraven.gangland.npcshops.database.BankerRepository;
import org.luckyraven.gangland.npcshops.listener.banker.BankerDamageListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The npc-shops module's declaration to the host (group J, T-N2): its four configuration classes in declaration
 * order ({@link NpcShopsYamlConfig}, {@link TraderModuleConfig}, {@link BankerModuleConfig},
 * {@link NpcShopsModuleConfig}), its listener, command and repository packages, and that the declared packages
 * match where the classes actually live. Modelled on {@code MailModuleTest} / {@code CopsNCrooksModuleTest}.
 */
@DisplayName("NpcShopsModule")
class NpcShopsModuleTest {

	@Test
	@DisplayName("configure registers the four configuration classes and the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new NpcShopsModule().configure(registrations);

		assertEquals(List.of(NpcShopsYamlConfig.class, TraderModuleConfig.class, BankerModuleConfig.class,
				NpcShopsModuleConfig.class), registrations.configurations());
		assertEquals(List.of(NpcShopsModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(NpcShopsModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(NpcShopsModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(NpcShopsModule.REPOSITORY_PACKAGE, BankerRepository.class.getPackageName());
		assertTrue(BankerCommand.class.getPackageName().startsWith(NpcShopsModule.COMMAND_PACKAGE));
		assertTrue(BankerDamageListener.class.getPackageName().startsWith(NpcShopsModule.LISTENER_PACKAGE));
	}

	@Test
	@DisplayName("npcshops ships top-level /glw commands")
	void commandPackages_isNotEmpty() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new NpcShopsModule().configure(registrations);

		assertFalse(registrations.commandPackages().isEmpty(), "npcshops ships top-level /glw commands");
	}
}
