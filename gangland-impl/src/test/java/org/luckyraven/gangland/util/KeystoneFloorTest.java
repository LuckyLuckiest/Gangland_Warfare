package org.luckyraven.gangland.util;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KeystoneFloorTest {

	@Test
	void equalPasses() {
		assertTrue(KeystoneFloor.satisfied("1.15.0", "1.15.0"));
	}

	@Test
	void olderPatchFails() {
		assertFalse(KeystoneFloor.satisfied("1.15.0", "1.15.1"));
	}

	@Test
	void olderMinorFails() {
		assertFalse(KeystoneFloor.satisfied("1.14.9", "1.15.0"));
	}

	@Test
	void newerPasses() {
		assertTrue(KeystoneFloor.satisfied("1.16.0", "1.15.0"));
		assertTrue(KeystoneFloor.satisfied("2.0.0", "1.15.0"));
	}

	@Test
	void snapshotOfTheFloorPasses() {
		assertTrue(KeystoneFloor.satisfied("1.15.0-SNAPSHOT", "1.15.0"));
	}

	@Test
	void unparsableInstalledPassesInsteadOfRefusing() {
		assertTrue(KeystoneFloor.satisfied("dev", "1.15.0"));
		assertTrue(KeystoneFloor.satisfied("", "1.15.0"));
		assertTrue(KeystoneFloor.satisfied("  ", "1.15.0"));
		assertTrue(KeystoneFloor.satisfied(null, "1.15.0"));
	}

	@Test
	void floorReadsTheFilteredResource() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getResource("keystone-floor.properties")).thenReturn(
				new ByteArrayInputStream("keystone.floor=1.15.0\n".getBytes(StandardCharsets.UTF_8)));

		assertEquals("1.15.0", KeystoneFloor.floor(plugin));
	}

	@Test
	void missingResourcePasses() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getResource("keystone-floor.properties")).thenReturn(null);

		assertNull(KeystoneFloor.floor(plugin));
	}

	@Test
	void unfilteredPlaceholderIsNoFloor() {
		JavaPlugin plugin = mock(JavaPlugin.class);
		when(plugin.getResource("keystone-floor.properties")).thenReturn(
				new ByteArrayInputStream("keystone.floor=${keystone.version}\n".getBytes(StandardCharsets.UTF_8)));

		assertNull(KeystoneFloor.floor(plugin));
	}
}
