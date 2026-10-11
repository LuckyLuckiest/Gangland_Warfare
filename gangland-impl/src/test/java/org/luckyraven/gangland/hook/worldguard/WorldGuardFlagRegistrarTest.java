package org.luckyraven.gangland.hook.worldguard;

import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** GP-18: the glw-gang-pvp region flag registers only when WorldGuard is installed. */
class WorldGuardFlagRegistrarTest {

	@Test
	@DisplayName("absent plugin: present() is false and nothing is registered")
	void absentPlugin_noCall_noClassLoad() {
		PluginManager pm = mock(PluginManager.class);
		when(pm.getPlugin("WorldGuard")).thenReturn(null);
		assertFalse(WorldGuardHooks.present(pm));

		when(pm.getPlugin("WorldGuard")).thenReturn(mock(Plugin.class));
		assertTrue(WorldGuardHooks.present(pm));
	}

	@Test
	@DisplayName("register adds a StateFlag named glw-gang-pvp with no default")
	void registersStateFlag() throws Exception {
		FlagRegistry registry = mock(FlagRegistry.class);
		WorldGuardFlagRegistrar.register(registry);

		ArgumentCaptor<Flag<?>> captor = ArgumentCaptor.forClass(Flag.class);
		verify(registry).register(captor.capture());
		assertTrue(captor.getValue() instanceof StateFlag);
		assertEquals("glw-gang-pvp", captor.getValue().getName());
		assertNull(captor.getValue().getDefault());
	}

	@Test
	@DisplayName("a FlagConflictException is swallowed")
	void conflictIsCaught() throws Exception {
		FlagRegistry registry = mock(FlagRegistry.class);
		doThrow(new FlagConflictException("glw-gang-pvp")).when(registry).register(any());
		assertDoesNotThrow(() -> WorldGuardFlagRegistrar.register(registry));
	}
}
