package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ChaseConfig}: the in-code defaults equal the shipped {@code copsncrooks/wanted.yml}, every block overrides key by key,
 * and the small lookup helpers the heat ledger, evasion clock and charge sheet call behave at their edges.
 */
@DisplayName("ChaseConfig")
class ChaseConfigTest {

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	@Test
	@DisplayName("a missing Wanted section is the default")
	void parse_nullRoot_isDefault() {
		assertEquals(ChaseConfig.DEFAULT, ChaseConfig.parse(null, new ConfigReport()));
	}

	@Test
	@DisplayName("the shipped copsncrooks/wanted.yml parses to exactly the in-code default, without issues")
	void parse_bundledFile_equalsDefault() throws IOException {
		String yaml;
		try (InputStream in = getClass().getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		ConfigReport report = new ConfigReport();

		ChaseConfig parsed = ChaseConfig.parse(wantedRoot(yaml, report), report);

		assertEquals(ChaseConfig.DEFAULT, parsed);
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("every block is overridden key by key; unlisted crimes keep their default weight")
	void parse_overridesEveryBlock() {
		String yaml = """
				Wanted:
				   Heat:
				      Enable: false
				      Star_Thresholds: [10, 20]
				      Streak_Bonus: 2.5
				      Seen_By_Cop_Multiplier: 3.0
				      Turf_War_Multiplier: 0.25
				      Assault_Repeat_Seconds: 7
				      Crimes:
				         Car_Theft: 5
				   Evasion:
				      Enable: false
				      Lost_Sight_Seconds: 9
				      Drop_Mode: ALL_STARS
				      Search_Radius: [1, 2]
				      Seconds_To_Drop: [3, 4]
				      Outside_Zone_Speed: 4.5
				   Hud:
				      Boss_Bar:
				         Enable: false
				      Star_Card:
				         Enable: false
				      Title:
				         Enable: false
				      Siren:
				         Enable: false
				         Sound: "ENTITY_BAT_TAKEOFF"
				         Volume: 0.5
				         Pitch: 1.5
				      Zone_Ring:
				         Enable: false
				         Particle: "FLAME"
				         Points: 12
				      Compass:
				         Enable: false
				   Charge_Sheet:
				      Enable: false
				      Base: 1
				      Per_Wanted_Level: 2
				      Maximum: 3
				      Seconds_Per_Unpaid: 0.5
				      Max_Extra_Seconds: 9
				""";
		ConfigReport report = new ConfigReport();

		ChaseConfig c = ChaseConfig.parse(wantedRoot(yaml, report), report);

		HeatSettings heat = c.heat();
		assertFalse(heat.enabled());
		assertEquals(List.of(10, 20), heat.starThresholds());
		assertEquals(2.5, heat.streakBonus());
		assertEquals(3.0, heat.seenByCopMultiplier());
		assertEquals(0.25, heat.turfWarMultiplier());
		assertEquals(7, heat.assaultRepeatSeconds());
		assertEquals(5, heat.weightOf("Car_Theft"));
		assertEquals(450, heat.weightOf("Jailbreak"));
		EvasionSettings ev = c.evasion();
		assertFalse(ev.enabled());
		assertEquals(9, ev.lostSightSeconds());
		assertEquals(DropMode.ALL_STARS, ev.dropMode());
		assertEquals(List.of(1, 2), ev.searchRadius());
		assertEquals(List.of(3, 4), ev.secondsToDrop());
		assertEquals(4.5, ev.outsideZoneSpeed());
		assertEquals(new HudSettings(false, false, false, false, "ENTITY_BAT_TAKEOFF", 0.5f, 1.5f, false, "FLAME", 12,
		                             false), c.hud());
		assertEquals(new ChargeSheetSettings(false, 1, 2, 3, 0.5, 9), c.chargeSheet());
	}

	@Test
	@DisplayName("an unknown Drop_Mode falls back to ONE_STAR and is reported")
	void unknownDropMode_fallsBackToOneStar_andReports() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = ChaseConfig.parse(wantedRoot("Wanted:\n   Evasion:\n      Drop_Mode: SOME_STARS\n", report),
		                                  report);

		assertEquals(DropMode.ONE_STAR, c.evasion().dropMode());
		assertFalse(report.isEmpty());
	}

	@Test
	@DisplayName("heatStarsFor counts the thresholds reached, capped at the max level")
	void heatStarsFor() {
		HeatSettings heat = ChaseConfig.DEFAULT.heat();

		assertEquals(0, heat.starsFor(99, 5));
		assertEquals(1, heat.starsFor(100, 5));
		assertEquals(2, heat.starsFor(449, 5));
		assertEquals(5, heat.starsFor(1000, 5));
		assertEquals(5, heat.starsFor(99999, 5));
	}

	@Test
	@DisplayName("a threshold list shorter than the max level is stretched linearly")
	void heatStarsFor_resizesAShortList() {
		HeatSettings heat = new HeatSettings(true, List.of(100, 500), 1.5, 1.5, 0.5, 10, Map.of());

		assertEquals(1, heat.starsFor(299, 3));
		assertEquals(2, heat.starsFor(300, 3));
		assertEquals(3, heat.starsFor(500, 3));
		assertEquals(500.0, heat.floorOf(3, 3));
	}

	@Test
	@DisplayName("heatFloorOf is 0 at no stars and the star's threshold above")
	void heatFloorOf() {
		HeatSettings heat = ChaseConfig.DEFAULT.heat();

		assertEquals(0.0, heat.floorOf(0, 5));
		assertEquals(250.0, heat.floorOf(2, 5));
		assertEquals(1000.0, heat.floorOf(9, 5));
	}

	@Test
	@DisplayName("evasionRadiusFor clamps the level into the list")
	void evasionRadiusFor_clamps() {
		EvasionSettings ev = ChaseConfig.DEFAULT.evasion();

		assertEquals(40, ev.radiusFor(0));
		assertEquals(40, ev.radiusFor(1));
		assertEquals(180, ev.radiusFor(7));
		assertEquals(10, ev.secondsToDropFor(0));
		assertEquals(60, ev.secondsToDropFor(7));
	}

	@Test
	@DisplayName("chargeSheetFineFor is base plus per-level, capped at the maximum")
	void chargeSheetFineFor() {
		ChargeSheetSettings sheet = ChaseConfig.DEFAULT.chargeSheet();

		assertEquals(700.0, sheet.fineFor(2));
		assertEquals(10000.0, sheet.fineFor(100));
		assertEquals(200.0, sheet.fineFor(-3));
	}

	@Test
	@DisplayName("chargeSheetExtraSecondsFor scales the unpaid fine, capped, never negative")
	void chargeSheetExtraSecondsFor() {
		ChargeSheetSettings sheet = ChaseConfig.DEFAULT.chargeSheet();

		assertEquals(40, sheet.extraSecondsFor(400));
		assertEquals(600, sheet.extraSecondsFor(1e9));
		assertEquals(0, sheet.extraSecondsFor(0));
		assertEquals(0, sheet.extraSecondsFor(-5));
	}
}
