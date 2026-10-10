package org.luckyraven.gangland.turf.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.support.YamlFixture;
import org.luckyraven.keystone.persistence.FileManager;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link TurfRules} reads turf/turf_rules.yml: shipped defaults, overrides and the side clamps. */
@DisplayName("TurfRules - turf_rules.yml")
class TurfRulesTest {

	@TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.NEVER)
	Path tempDir;

	private TurfRules load(String yaml) throws IOException {
		FileManager fileManager = YamlFixture.fileManager(tempDir, "turf_rules", yaml);
		return new TurfRules(fileManager);
	}

	@Test
	@DisplayName("the shipped file gives snapping on, sides 16 to 512")
	void shippedDefaults() throws IOException {
		TurfRules rules = load(YamlFixture.shipped("/turf/turf_rules.yml"));

		assertTrue(rules.isSnapToChunks());
		assertEquals(16, rules.getMinSideBlocks());
		assertEquals(512, rules.getMaxSideBlocks());
	}

	@Test
	@DisplayName("explicit values override the defaults")
	void overrides() throws IOException {
		TurfRules rules = load("Create:\n   Snap_To_Chunks: false\n   Min_Side_Blocks: 8\n   Max_Side_Blocks: 100\n");

		assertFalse(rules.isSnapToChunks());
		assertEquals(8, rules.getMinSideBlocks());
		assertEquals(100, rules.getMaxSideBlocks());
	}

	@Test
	@DisplayName("a maximum below the minimum is raised to the minimum")
	void maxBelowMinIsRaised() throws IOException {
		TurfRules rules = load("Create:\n   Max_Side_Blocks: 4\n");

		assertEquals(16, rules.getMaxSideBlocks());
	}

	@Test
	@DisplayName("a minimum below 1 is raised to 1")
	void minBelowOneIsRaised() throws IOException {
		TurfRules rules = load("Create:\n   Min_Side_Blocks: 0\n");

		assertEquals(1, rules.getMinSideBlocks());
	}
}
