package org.luckyraven.gangland.copsncrooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.command.cops.CopCommand;
import org.luckyraven.gangland.copsncrooks.config.BreachModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.BagModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ChaseModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksFileConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CopsNCrooksYamlConfig;
import org.luckyraven.gangland.copsncrooks.config.EvasionModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.HeatModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.GetawayModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.MostWantedModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.PursuitReportModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.RegistryModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.RescueModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.StandoffModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.SurrenderModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ScannerMapModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.LookoutModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.OverwatchModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.SightModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.ScannerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.TrackerModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.PatrolModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.DisguiseModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.WitnessModuleConfig;
import org.luckyraven.gangland.copsncrooks.config.CrimeSourcesModuleConfig;
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
	@DisplayName("configure registers the twenty-seven configuration classes (0.20.0 eleven seams, 0.21.0 seven shells) and the three packages")
	void configure_declaresConfigsAndPackages() {
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CopsNCrooksModule().configure(registrations);

		assertEquals(List.of(CopsNCrooksYamlConfig.class, CopsNCrooksFileConfig.class,
				CopsNCrooksModuleConfig.class, ChaseModuleConfig.class, HeatModuleConfig.class,
				EvasionModuleConfig.class, RegistryModuleConfig.class, SurrenderModuleConfig.class,
				RescueModuleConfig.class, StandoffModuleConfig.class, BreachModuleConfig.class,
				PursuitReportModuleConfig.class, GetawayModuleConfig.class, MostWantedModuleConfig.class,
				CrimeSourcesModuleConfig.class,
				WitnessModuleConfig.class, DisguiseModuleConfig.class, PatrolModuleConfig.class,
				TrackerModuleConfig.class, ScannerModuleConfig.class, SightModuleConfig.class,
				OverwatchModuleConfig.class, ScannerMapModuleConfig.class,
				BagModuleConfig.class, LookoutModuleConfig.class), registrations.configurations());
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
