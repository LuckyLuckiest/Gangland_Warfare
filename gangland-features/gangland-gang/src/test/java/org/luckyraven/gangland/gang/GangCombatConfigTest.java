package org.luckyraven.gangland.gang;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.combat.region.FriendlyFireRegionSource;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** GP-18: GangCombatConfig must load whole without WorldGuard (Keystone drops a @Configuration whose signatures name an absent type). */
class GangCombatConfigTest {

	@Test
	@DisplayName("no @Bean signature names a WorldGuard type and the bean scan loads")
	void loadsWithoutWorldGuard_noFault_noneSource() {
		Method[] methods = assertDoesNotThrow(GangCombatConfig.class::getDeclaredMethods);
		for (Method method : methods) {
			assertFalse(method.getReturnType().getName().startsWith("com.sk89q"), method.toString());
			for (Class<?> parameter : method.getParameterTypes()) {
				assertFalse(parameter.getName().startsWith("com.sk89q"), method.toString());
			}
		}
	}

	@Test
	@DisplayName("WorldGuard absent: the region source is NONE")
	void absentWorldGuard_noneSource() {
		PluginManager pm = mock(PluginManager.class);
		when(pm.getPlugin("WorldGuard")).thenReturn(null);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getPluginManager).thenReturn(pm);
			assertSame(FriendlyFireRegionSource.NONE, new GangCombatConfig().friendlyFireRegionSource());
		}
	}
}
