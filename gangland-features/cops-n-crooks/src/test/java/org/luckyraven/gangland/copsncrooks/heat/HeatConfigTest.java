package org.luckyraven.gangland.copsncrooks.heat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.12 F1: the {@code Heat:} section of {@code cops.yml} parses, falls back to the shipped defaults for a missing
 * section or key, and maps heat to stars ({@link HeatConfig#starsFor}) and stars back to their heat floor
 * ({@link HeatConfig#floorFor}).
 */
@DisplayName("HeatConfig")
class HeatConfigTest {

	@Test
	@DisplayName("defaults match the 0.12 spec")
	void defaults_matchSpec() {
		HeatConfig config = HeatConfig.defaults();

		assertTrue(config.isEnabled());
		assertEquals(List.of(100, 250, 450, 700, 1000), config.getStarThresholds());
		assertEquals(1.5, config.getStreakBonus());
		assertEquals(0.5, config.getTurfWarMultiplier());
		assertEquals(10, config.getAssaultCopCooldownSeconds());
		assertEquals(80, config.weightOf(Crime.KILL_PLAYER));
		assertEquals(100, config.weightOf(Crime.KILL_CIVILIAN));
		assertEquals(150, config.weightOf(Crime.KILL_COP));
		assertEquals(100, config.weightOf(Crime.ASSAULT_COP));
	}

	@Test
	@DisplayName("starsFor counts the thresholds at or below the heat, capped at the max level")
	void starsFor_countsThresholds() {
		HeatConfig config = HeatConfig.defaults();

		assertEquals(0, config.starsFor(0, 5));
		assertEquals(0, config.starsFor(99, 5));
		assertEquals(1, config.starsFor(100, 5));
		assertEquals(2, config.starsFor(449, 5));
		assertEquals(3, config.starsFor(450, 5));
		assertEquals(5, config.starsFor(1000, 5));
		assertEquals(5, config.starsFor(50_000, 5));
		assertEquals(3, config.starsFor(50_000, 3), "capped at the wanted maximum level");
		assertEquals(0, config.starsFor(50_000, 0));
	}

	@Test
	@DisplayName("floorFor is 0 without stars, the star's threshold otherwise, clamped to the last threshold")
	void floorFor_isTheStarThreshold() {
		HeatConfig config = HeatConfig.defaults();

		assertEquals(0, config.floorFor(0));
		assertEquals(0, config.floorFor(-1));
		assertEquals(100, config.floorFor(1));
		assertEquals(450, config.floorFor(3));
		assertEquals(1000, config.floorFor(5));
		assertEquals(1000, config.floorFor(9));
		assertEquals(250, config.floorFor(2, 5));
	}

	@Test
	@DisplayName("fewer thresholds than the max level are stretched so every star stays reachable")
	void starsFor_resizesShortThresholdList() {
		HeatConfig config = new HeatConfig(true, List.of(100, 200), 1.5, 0.5, 10, null);

		assertEquals(5, config.starsFor(Integer.MAX_VALUE, 5));
		assertEquals(2, config.starsFor(Integer.MAX_VALUE, 2));
	}

	@Test
	@DisplayName("an empty threshold list and negative values fall back to safe defaults")
	void constructor_sanitizesValues() {
		HeatConfig config = new HeatConfig(true, List.of(), -1, -1, -5, Map.of(Crime.KILL_COP, -10));

		assertEquals(HeatConfig.DEFAULT_STAR_THRESHOLDS, config.getStarThresholds());
		assertEquals(0.0, config.getStreakBonus());
		assertEquals(0.0, config.getTurfWarMultiplier());
		assertEquals(0, config.getAssaultCopCooldownSeconds());
		assertEquals(0, config.weightOf(Crime.KILL_COP));
		assertEquals(80, config.weightOf(Crime.KILL_PLAYER), "missing weights use the crime default");
	}

	@Test
	@DisplayName("a cops.yml without a Heat section yields the defaults")
	void parse_missingSection_yieldsDefaults() {
		HeatConfig config = parse(new StringReader("""
				Cops:
				   Enabled: true
				"""), new ConfigReport());

		assertTrue(config.isEnabled());
		assertEquals(HeatConfig.DEFAULT_STAR_THRESHOLDS, config.getStarThresholds());
		assertEquals(150, config.weightOf(Crime.KILL_COP));
	}

	@Test
	@DisplayName("every Heat key parses, omitted crimes keep their defaults")
	void parse_readsEveryKey() {
		HeatConfig config = parse(new StringReader("""
				Heat:
				   Enable: false
				   Star_Thresholds: [50, 150, 300]
				   Streak_Bonus: 2.0
				   Turf_War_Multiplier: 0.25
				   Assault_Cop_Cooldown_Seconds: 4
				   Crimes:
				      Kill_Cop: 200
				      Assault_Cop: 40
				"""), new ConfigReport());

		assertFalse(config.isEnabled());
		assertEquals(List.of(50, 150, 300), config.getStarThresholds());
		assertEquals(2.0, config.getStreakBonus());
		assertEquals(0.25, config.getTurfWarMultiplier());
		assertEquals(4, config.getAssaultCopCooldownSeconds());
		assertEquals(200, config.weightOf(Crime.KILL_COP));
		assertEquals(40, config.weightOf(Crime.ASSAULT_COP));
		assertEquals(80, config.weightOf(Crime.KILL_PLAYER));
		assertEquals(100, config.weightOf(Crime.KILL_CIVILIAN));
	}

	@Test
	@DisplayName("the shipped cops.yml declares the Heat section with the spec defaults, every key read")
	void shippedFile_declaresDefaults() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("npc/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		ConfigReport report = new ConfigReport();
		HeatConfig   config = parse(new StringReader(yaml), report);

		assertTrue(config.isEnabled());
		assertEquals(HeatConfig.DEFAULT_STAR_THRESHOLDS, config.getStarThresholds());
		assertEquals(1.5, config.getStreakBonus());
		assertEquals(0.5, config.getTurfWarMultiplier());
		assertEquals(10, config.getAssaultCopCooldownSeconds());
		for (Crime crime : Crime.values()) {
			assertEquals(crime.getDefaultWeight(), config.weightOf(crime), crime.name());
		}

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())
		                                                   && issue.path().contains("Heat")),
		           () -> "Heat keys left unread: " + report.issues());
	}

	private static HeatConfig parse(Reader yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), yaml, report);
		return HeatConfig.parse(NodeReader.of(document.root(), report), report);
	}
}
