package org.luckyraven.gangland.turf.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link TurfMapSettings} reads turf/turf_map.yml: shipped defaults, overrides and every clamp the file documents. */
@DisplayName("TurfMapSettings - turf_map.yml")
class TurfMapSettingsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private TurfMapSettings load(String yaml) throws IOException {
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_map", yaml);
		return new TurfMapSettings(fileManager);
	}

	@Test
	@DisplayName("the shipped file gives a 41 by 7 grid of 16-block cells and the 2 s HUD line")
	void shippedDefaults() throws IOException {
		TurfMapSettings settings = load(YamlFixture.shipped("/turf/turf_map.yml"));

		assertEquals(41, settings.getWidth());
		assertEquals(7, settings.getHeight());
		assertEquals(16, settings.getCellBlocks());
		assertEquals(5, settings.getAutoMinIntervalSeconds());
		assertTrue(settings.isBedrockTextForm());
		assertTrue(settings.isHudEnabled());
		assertEquals(2, settings.getHudIntervalSeconds());
		assertEquals(9, settings.getHudPriority());
	}

	@Test
	@DisplayName("an even width is raised to the next odd value")
	void evenWidthBecomesOdd() throws IOException {
		assertEquals(41, load("Map:\n   Width: 40\n").getWidth());
	}

	@Test
	@DisplayName("a width above 61 is clamped to 61")
	void widthClampedHigh() throws IOException {
		assertEquals(61, load("Map:\n   Width: 99\n").getWidth());
	}

	@Test
	@DisplayName("an even height is raised to the next odd value, and a tiny one clamps to 3")
	void heightRules() throws IOException {
		assertEquals(9, load("Map:\n   Height: 8\n").getHeight());
		assertEquals(3, load("Map:\n   Height: 1\n").getHeight());
	}

	@Test
	@DisplayName("a cell size outside 8, 16, 32, 64 uses 16")
	void cellBlocksFallBack() throws IOException {
		assertEquals(16, load("Map:\n   Cell_Blocks: 20\n").getCellBlocks());
		assertEquals(32, load("Map:\n   Cell_Blocks: 32\n").getCellBlocks());
	}

	@Test
	@DisplayName("a negative HUD priority is clamped to 0 and a huge one to 100")
	void priorityClamped() throws IOException {
		assertEquals(0, load("Hud:\n   Priority: -5\n").getHudPriority());
		assertEquals(100, load("Hud:\n   Priority: 500\n").getHudPriority());
	}

	@Test
	@DisplayName("a disabled HUD line reads as off")
	void hudCanBeOff() throws IOException {
		assertFalse(load("Hud:\n   Enabled: false\n").isHudEnabled());
	}
}
