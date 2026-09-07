package org.luckyraven.gangland.turf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.turf.command.TurfCommand;
import org.luckyraven.gangland.turf.database.TurfRepository;
import org.luckyraven.gangland.turf.listener.TurfBossBarListener;
import org.luckyraven.gangland.turf.task.GangPresenceListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The turf module's declaration to the host (flip 3 of the 2026-09-07 module split): both configuration classes
 * in declaration order, its two listener packages ({@code turf.listener} and {@code turf.task} —
 * {@code GangPresenceListener} has not yet been relocated under {@code turf.listener}, see turf.md §6 Q3), its
 * command package and its repository package, plus that the declared packages match where the classes actually
 * live. Modelled on {@code MailModuleTest} / {@code GadgetModuleTest} / {@code CopsNCrooksModuleTest}.
 */
@DisplayName("TurfModule")
class TurfModuleTest {

	@Test
	@DisplayName("configure registers TurfModuleFileConfig and TurfModuleConfig plus the listener, command and repository packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new TurfModule().configure(registrations);

		assertEquals(List.of(TurfModuleFileConfig.class, TurfModuleConfig.class), registrations.configurations());
		assertEquals(List.of(TurfModule.LISTENER_PACKAGE, TurfModule.TASK_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(TurfModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(TurfModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(TurfModule.LISTENER_PACKAGE, TurfBossBarListener.class.getPackageName());
		assertEquals(TurfModule.TASK_PACKAGE, GangPresenceListener.class.getPackageName());
		assertEquals(TurfModule.COMMAND_PACKAGE, TurfCommand.class.getPackageName());
		assertEquals(TurfModule.REPOSITORY_PACKAGE, TurfRepository.class.getPackageName());
	}
}
