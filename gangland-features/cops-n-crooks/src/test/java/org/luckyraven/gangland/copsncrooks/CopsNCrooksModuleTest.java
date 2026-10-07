package org.luckyraven.gangland.copsncrooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.cops.CopCommand;
import org.luckyraven.gangland.copsncrooks.config.ChaseModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksFileConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.config.EvasionModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.HeatModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.RegistryModuleConfig;
import org.luckyraven.gangland.copsncrooks.database.DetainmentRepository;
import org.luckyraven.gangland.copsncrooks.listener.detainment.CopListener;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cops-n-crooks module's declaration to the host: the seven configuration classes (the last, 0.16.0's
 * {@code RegistryModuleConfig}, holds the station, admin-region and setup-point registries) in declaration order (the
 * three survivors of the six that were left after D5/civilians in group H and D6/npc-shops in group J moved
 * {@code BankerModuleConfig}/{@code TraderModuleConfig} out to {@code gangland-npc-shops} and folded
 * {@code TurfNpcsModuleConfig} into {@code gangland-turf}'s {@code TurfModuleConfig} — review I-1/T-IR1, plus the 0.15.0 chase, heat and
 * evasion configurations), its
 * listener/command/repository packages, and that the declared packages match where the classes actually live.
 * Modelled on {@code MailModuleTest}.
 */
@DisplayName("CopsNCrooksModule")
class CopsNCrooksModuleTest {

	@Test
	@DisplayName("configure registers the seven configuration classes and the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertEquals(List.of(CopsNCrooksYamlConfig.class, CopsNCrooksFileConfig.class,
				CopsNCrooksModuleConfig.class, ChaseModuleConfig.class, HeatModuleConfig.class,
				EvasionModuleConfig.class, RegistryModuleConfig.class), registrations.configurations());
		assertEquals(List.of(CopsNCrooksModule.LISTENER_PACKAGE), registrations.listenerPackages());
		assertEquals(List.of(CopsNCrooksModule.COMMAND_PACKAGE), registrations.commandPackages());
		assertEquals(List.of(CopsNCrooksModule.REPOSITORY_PACKAGE), registrations.repositoryPackages());
	}

	@Test
	@DisplayName("the declared packages match where the classes actually live")
	void declaredPackages_matchClasses() {
		assertEquals(CopsNCrooksModule.REPOSITORY_PACKAGE, DetainmentRepository.class.getPackageName());
		assertTrue(CopCommand.class.getPackageName().startsWith(CopsNCrooksModule.COMMAND_PACKAGE));
		assertTrue(CopListener.class.getPackageName().startsWith(CopsNCrooksModule.LISTENER_PACKAGE));
	}

	@Test
	@DisplayName("copsncrooks ships top-level /glw commands")
	void commandPackages_isNotEmpty() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertFalse(registrations.commandPackages().isEmpty(), "copsncrooks ships top-level /glw commands");
	}
}
