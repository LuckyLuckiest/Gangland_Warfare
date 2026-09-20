package org.luckyraven.gangland.civilians;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.keystone.module.ModuleRegistrar;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS7 G5b fix round 1 (review C1): {@code CiviliansModule.configure} isolated {@code GanglandCombatEligibility}'s
 * bean into its own {@link CombatEligibilityConfig}, but never actually added that class to the registrar's
 * explicit list - so it was NEVER registered with the host, regardless of Bartizan's presence, meaning downed
 * players silently became hittable again. This pins that the class is registered exactly when Bartizan is
 * available, and never when it is not.
 */
@DisplayName("CiviliansModule.configure — CombatEligibilityConfig registration gate (WS7 G5b fix round 1, C1)")
class CiviliansModuleConfigureBartizanGateTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();
	}

	@Test
	@DisplayName("Bartizan available: CombatEligibilityConfig is registered")
	void bartizanAvailable_registersCombatEligibilityConfig() {
		stubBartizanAvailable(true);
		ModuleRegistrar registrar = mockChainableRegistrar();

		new CiviliansModule().configure(registrar);

		verify(registrar).configuration(CombatEligibilityConfig.class);
	}

	@Test
	@DisplayName("Bartizan unavailable: CombatEligibilityConfig is never registered")
	void bartizanUnavailable_neverRegistersCombatEligibilityConfig() {
		stubBartizanAvailable(false);
		ModuleRegistrar registrar = mockChainableRegistrar();

		new CiviliansModule().configure(registrar);

		verify(registrar, never()).configuration(CombatEligibilityConfig.class);
	}

	private static ModuleRegistrar mockChainableRegistrar() {
		ModuleRegistrar registrar = mock(ModuleRegistrar.class);
		when(registrar.configuration(any())).thenReturn(registrar);
		when(registrar.listenerPackage(anyString())).thenReturn(registrar);
		when(registrar.commandPackage(anyString())).thenReturn(registrar);
		when(registrar.repositoryPackage(anyString())).thenReturn(registrar);
		return registrar;
	}

	private static void stubBartizanAvailable(boolean available) {
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.isPluginEnabled("Bartizan")).thenReturn(available);
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}
}
