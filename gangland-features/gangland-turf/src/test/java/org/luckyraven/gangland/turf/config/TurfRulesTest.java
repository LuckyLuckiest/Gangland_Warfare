package org.luckyraven.gangland.turf.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.capture.CaptureRules;
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
	@DisplayName("the shipped W2 rules: capture, turf cap, income and garrison values")
	void shippedW2Defaults() throws IOException {
		TurfRules rules = load(YamlFixture.shipped("/turf/turf_rules.yml"));

		assertEquals(new CaptureRules(CaptureRules.Window.OWNERS_ONLINE, 120, 24, 15, 60, 10, 4), rules.capture());
		assertEquals(2, rules.getTurfCapBase());
		assertEquals(1, rules.getTurfCapPerLevel());
		assertEquals(6, rules.getTurfCapCeiling());
		assertEquals(24, rules.getIncomeIdleHours());
		assertEquals(2.0, rules.getMaxIncomeMultiplier());
		assertEquals(1.0, rules.getGangXpPerPayout());
		assertEquals(12, rules.getMaxReserve());
	}

	@ParameterizedTest(name = "level {0} may hold {1} turfs")
	@CsvSource({"0,2", "1,3", "2,4", "3,5", "4,6", "5,6", "20,6", "-3,2"})
	@DisplayName("the turf cap table is Base + Per_Level x level, capped at Ceiling")
	void capTable(int level, int expected) throws IOException {
		TurfRules shipped = load(YamlFixture.shipped("/turf/turf_rules.yml"));

		assertEquals(expected, shipped.cap(level));
	}

	@Test
	@DisplayName("Capture.Window OWNERS_OFFLINE overrides the default window")
	void windowOverride() throws IOException {
		TurfRules rules = load("Capture:\n   Window: OWNERS_OFFLINE\n");

		assertEquals(CaptureRules.Window.OWNERS_OFFLINE, rules.capture().window());
	}

	@Test
	@DisplayName("an unknown Capture.Window falls back to OWNERS_ONLINE")
	void badWindowFallsBack() throws IOException {
		TurfRules rules = load("Capture:\n   Window: SOMETIMES\n");

		assertEquals(CaptureRules.Window.OWNERS_ONLINE, rules.capture().window());
	}

	@Test
	@DisplayName("negative capture values clamp to 0 and a multiplier below 1 clamps to 1")
	void negativeValuesClampToZero() throws IOException {
		TurfRules rules = load("Capture:\n   Presence_Seconds: -5\n   Guard_Weight_Cap: -1\nIncome:\n   Max_Multiplier: 0.5\n");

		assertEquals(0, rules.capture().presenceSeconds());
		assertEquals(0, rules.capture().guardWeightCap());
		assertEquals(1.0, rules.getMaxIncomeMultiplier());
	}

	@Test
	@DisplayName("a minimum below 1 is raised to 1")
	void minBelowOneIsRaised() throws IOException {
		TurfRules rules = load("Create:\n   Min_Side_Blocks: 0\n");

		assertEquals(1, rules.getMinSideBlocks());
	}
}
