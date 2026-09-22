package org.luckyraven.gangland.lootchest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.command.LootChestWandCommand;
import org.luckyraven.gangland.lootchest.config.LootChestFileConfig;
import org.luckyraven.gangland.lootchest.config.LootChestModuleConfig;
import org.luckyraven.gangland.lootchest.database.LootChestRepository;
import org.luckyraven.gangland.lootchest.listener.LootChestListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loot chest module's declaration to the host (WS3 G2, 0.10.0 decoupling wave): the two configuration classes
 * in declaration order, its listener/command/repository packages, and that the declared packages match where the
 * classes actually live. Modelled on {@code GadgetModuleTest} / {@code MailModuleTest}.
 */
@DisplayName("LootChestModule")
class LootChestModuleTest {

	@Test
	@DisplayName("configure registers LootChestFileConfig and LootChestModuleConfig plus the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new LootChestModule().configure(registrations);

		assertEquals(List.of(LootChestFileConfig.class, LootChestModuleConfig.class), registrations.configurations());
		assertEquals(List.of(LootChestModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(LootChestModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(LootChestModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertTrue(LootChestListener.class.getPackageName().startsWith(LootChestModule.LISTENER_PACKAGE));
		assertEquals(LootChestModule.COMMAND_PACKAGE, LootChestWandCommand.class.getPackageName());
		assertEquals(LootChestModule.REPOSITORY_PACKAGE, LootChestRepository.class.getPackageName());
	}
}
