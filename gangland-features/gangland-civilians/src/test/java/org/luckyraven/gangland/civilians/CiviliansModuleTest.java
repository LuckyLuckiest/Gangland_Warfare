package org.luckyraven.gangland.civilians;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.command.CivilianCommand;
import org.luckyraven.gangland.civilians.database.CivilianSpawnerRepository;
import org.luckyraven.gangland.civilians.listener.civilian.CivilianDamageListener;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.module.ModuleRegistrations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The civilians module's declaration to the host (group H, T-N2): its three configuration classes in declaration
 * order ({@link CiviliansYamlConfig}, {@link CiviliansFileConfig}, {@link CiviliansModuleConfig}), its listener,
 * command and repository packages, and that the declared packages match where the classes actually live. Modelled
 * on {@code MailModuleTest} / {@code CopsNCrooksModuleTest}.
 *
 * <p>WS7 G5b fix round 1 (review C1): {@code configure()} now also consults {@code Settings.isBartizanAvailable()}
 * to conditionally register {@link CombatEligibilityConfig} - see {@link CiviliansModuleConfigureBartizanGateTest}
 * for that behaviour. These pre-existing tests bootstrap the Bukkit registry and stub Bartizan unavailable purely
 * so {@code Settings.isBartizanAvailable()} doesn't NPE here; the fixed "3 configurations" list they assert is the
 * Bartizan-unavailable shape.
 */
@DisplayName("CiviliansModule")
class CiviliansModuleTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("configure registers the three configuration classes and the three packages")
	void configure_declaresConfigsAndPackages() {
		stubBartizanUnavailable();
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
		stubBartizanUnavailable();
		ModuleRegistrations registrations = new ModuleRegistrations();

		new CiviliansModule().configure(registrations);

		assertFalse(registrations.commandPackages().isEmpty(), "civilians ships top-level /glw commands");
	}

	private static void stubBartizanUnavailable() {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(false);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}
}
