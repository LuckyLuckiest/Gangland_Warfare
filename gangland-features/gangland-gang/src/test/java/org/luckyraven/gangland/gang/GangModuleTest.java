package org.luckyraven.gangland.gang;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.sub.gang.GangCommand;
import org.luckyraven.gangland.command.sub.rank.RankCommand;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.listener.gang.GangMembersDamageListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gang module's declaration to the host (WS5 G1): one configuration class, its listener/command/repository
 * packages.
 */
@DisplayName("GangModule")
class GangModuleTest {

	@Test
	@DisplayName("configure registers GangConfig plus the listener, command and repository packages")
	void configure_declaresConfigAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new GangModule().configure(registrations);

		assertEquals(List.of(GangConfig.class, GangMembershipInstaller.class), registrations.configurations());
		assertEquals(List.of(GangModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(GangModule.GANG_COMMAND_PACKAGE, GangModule.RANK_COMMAND_PACKAGE),
		            registrations.commandPackages());
		assertEquals(List.of(GangModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(GangModule.LISTENER_PACKAGE, GangMembersDamageListener.class.getPackageName());
		assertTrue(GangCommand.class.getPackageName().startsWith(GangModule.GANG_COMMAND_PACKAGE));
		assertTrue(RankCommand.class.getPackageName().startsWith(GangModule.RANK_COMMAND_PACKAGE));
		assertEquals(GangModule.REPOSITORY_PACKAGE + ".repositories.gang", GangRepository.class.getPackageName());
	}
}
