package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.copsncrooks.wanted.evasion.AutoDrop;
import org.luckyraven.keystone.persistence.config.ConfigIssue;
import org.luckyraven.keystone.persistence.config.Severity;
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
		assertTrue(report.issues().toString().contains("AUTO"), report.issues().toString());
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

	private static ChaseConfig parseEvasion(String evasionBody, ConfigReport report) {
		return ChaseConfig.parse(wantedRoot("Wanted:\n   Evasion:\n" + evasionBody, report), report);
	}

	@Test
	@DisplayName("AUTO parses, and a file without the Auto block reads as the default with no issues")
	void auto_parsesAndMissingBlockIsDefault() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion("      Drop_Mode: auto\n", report);

		assertEquals(DropMode.AUTO, c.evasion().dropMode());
		assertEquals(AutoSettings.DEFAULT, c.evasion().auto());
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("every Auto key is overridden key by key and round-trips")
	void auto_overridesEveryKey() {
		String yaml = """
				      Drop_Mode: AUTO
				      Auto:
				         Opening_Seconds: 11
				         Rampage_Crimes: 3
				         Rampage_Peak_Level: 5
				         Lock_Cool_Seconds: 61
				         Respot_Limit: 2
				         Petty:
				            Max_Crimes: 1
				            Max_Peak_Level: 3
				         Cold_Trail:
				            Typical_Seconds:
				               - 10
				               - 20
				            Ratio: 1.5
				            Quiet_Seconds: 45
				         Clean_Break:
				            Outside_Ratio: 0.25
				            Drop_Fraction: 1
				         Momentum:
				            Step_Speed: 0.8
				            Narrow_Step_Speed: 0.6
				            Narrow_Seen_Seconds: 9
				            Floor: 0.3
				         Repeat_Chases: 0
				         Repeat_Window_Minutes: 5
				         Learning:
				            Enable: false
				            Escape_Rate:
				               - 0.5
				               - 0.25
				            Prior_Chases: 7
				            Decay_Per_Chase: 0.8
				            Habitual_Escaper_Delta: 0.3
				            Habit_Time_Strength: 1.0
				            Min_Time_Factor: 0.7
				            Max_Time_Factor: 2.0
				            Min_Chase_Seconds: 5
				            Min_Seconds_Between_Outcomes: 6
				            Forget_After_Days: 7
				""";
		ConfigReport report = new ConfigReport();

		AutoSettings a = parseEvasion(yaml, report).evasion().auto();

		AutoSettings expected = new AutoSettings(11, 3, 5, 61, 2, new AutoSettings.Petty(1, 3),
		                                         new AutoSettings.ColdTrail(List.of(10, 20), 1.5, 45),
		                                         new AutoSettings.CleanBreak(0.25, 1.0),
		                                         new AutoSettings.Momentum(0.8, 0.6, 9, 0.3), 0, 5,
		                                         new AutoSettings.Learning(false, List.of(0.5, 0.25), 7, 0.8, 0.3, 1.0,
		                                                                   0.7, 2.0, 5, 6, 7));
		assertEquals(expected, a);
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("typicalFor and escapeRateFor clamp the level into the list")
	void auto_lookupsClamp() {
		AutoSettings a = AutoSettings.DEFAULT;

		assertEquals(30, a.typicalFor(0));
		assertEquals(90, a.typicalFor(3));
		assertEquals(150, a.typicalFor(9));
		assertEquals(0.90, a.escapeRateFor(1));
		assertEquals(0.20, a.escapeRateFor(9));
	}

	@Test
	@DisplayName("Learned.cold is a stranger with the configured typical time")
	void learnedCold() {
		AutoDrop.Learned l = AutoDrop.Learned.cold(AutoSettings.DEFAULT, 3);

		assertEquals(0.0, l.delta());
		assertEquals(90.0, l.typicalSeconds());
	}

	@ParameterizedTest(name = "{0}: {1}")
	@CsvSource({"Opening_Seconds,-1", "Lock_Cool_Seconds,-1", "Respot_Limit,-1", "Repeat_Chases,-1", "Repeat_Chases,9",
	            "Repeat_Window_Minutes,-1", "Rampage_Crimes,0", "Rampage_Peak_Level,0"})
	@DisplayName("a top-level Auto key out of range is an ERROR config.range and takes its default")
	void auto_topLevelRange(String key, String value) {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion("      Drop_Mode: AUTO\n      Auto:\n         " + key + ": " + value + "\n", report);

		assertEquals(AutoSettings.DEFAULT, c.evasion().auto());
		assertRangeError(report, key);
	}

	@ParameterizedTest(name = "{0}.{1}: {2}")
	@CsvSource({"Petty,Max_Crimes,0", "Petty,Max_Peak_Level,0", "Cold_Trail,Quiet_Seconds,-1", "Cold_Trail,Ratio,0",
	            "Clean_Break,Outside_Ratio,1.5", "Clean_Break,Drop_Fraction,-0.1", "Momentum,Step_Speed,1.5",
	            "Momentum,Narrow_Step_Speed,0", "Momentum,Narrow_Seen_Seconds,-1", "Momentum,Floor,2",
	            "Learning,Prior_Chases,0", "Learning,Decay_Per_Chase,0.2", "Learning,Habitual_Escaper_Delta,0.01",
	            "Learning,Habit_Time_Strength,3", "Learning,Min_Time_Factor,0.05", "Learning,Max_Time_Factor,0.5",
	            "Learning,Min_Chase_Seconds,-1", "Learning,Min_Seconds_Between_Outcomes,-1",
	            "Learning,Forget_After_Days,0"})
	@DisplayName("a nested Auto key out of range is an ERROR config.range and takes its default")
	void auto_nestedRange(String block, String key, String value) {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion(
				"      Drop_Mode: AUTO\n      Auto:\n         " + block + ":\n            " + key + ": " + value + "\n",
				report);

		assertEquals(AutoSettings.DEFAULT, c.evasion().auto());
		assertRangeError(report, key);
	}

	private static void assertRangeError(ConfigReport report, String key) {
		List<ConfigIssue> hits = report.issues().stream().filter(i -> "config.range".equals(i.code())).toList();
		assertEquals(1, hits.size(), report.issues().toString());
		assertEquals(Severity.ERROR, hits.get(0).severity());
		assertTrue(hits.get(0).path().endsWith(key) || hits.get(0).message().contains(key), hits.get(0).render());
	}

	@Test
	@DisplayName("a Typical_Seconds entry below 5 repeats the previous entry (the first takes the default), WARNING config.range")
	void auto_typicalSecondsBelowFive() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion("      Auto:\n         Cold_Trail:\n            Typical_Seconds: [3, 40, 2]\n",
		                             report);

		assertEquals(List.of(30, 40, 40), c.evasion().auto().coldTrail().typicalSeconds());
		assertEquals(2, report.issues().size(), report.issues().toString());
		assertTrue(report.issues().stream()
		                 .allMatch(i -> i.severity() == Severity.WARNING && "config.range".equals(i.code())));
	}

	@Test
	@DisplayName("an empty Typical_Seconds list is the default, silently")
	void auto_typicalSecondsEmpty() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion("      Auto:\n         Cold_Trail:\n            Typical_Seconds: []\n", report);

		assertEquals(AutoSettings.DEFAULT.coldTrail().typicalSeconds(),
		             c.evasion().auto().coldTrail().typicalSeconds());
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("an Escape_Rate entry outside 0..1 is clamped, WARNING config.range")
	void auto_escapeRateClamped() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion("      Auto:\n         Learning:\n            Escape_Rate: [1.5, 0.5, -0.2]\n",
		                             report);

		assertEquals(List.of(1.0, 0.5, 0.0), c.evasion().auto().learning().escapeRate());
		assertEquals(2, report.issues().size(), report.issues().toString());
		assertTrue(report.issues().stream()
		                 .allMatch(i -> i.severity() == Severity.WARNING && "config.range".equals(i.code())));
	}

	@Test
	@DisplayName("Narrow_Step_Speed above Step_Speed is set to Step_Speed, WARNING config.conflict")
	void auto_narrowAboveStep() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion(
				"      Auto:\n         Momentum:\n            Step_Speed: 0.5\n            Narrow_Step_Speed: 0.9\n",
				report);

		assertEquals(0.5, c.evasion().auto().momentum().narrowStepSpeed());
		assertEquals(1, report.issues().size(), report.issues().toString());
		assertEquals(Severity.WARNING, report.issues().get(0).severity());
		assertEquals("config.conflict", report.issues().get(0).code());
	}

	@Test
	@DisplayName("Petty.Max_Peak_Level at or above Rampage_Peak_Level is kept with a WARNING config.conflict")
	void auto_pettyOverlapsRampage() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = parseEvasion(
				"      Auto:\n         Rampage_Peak_Level: 3\n         Petty:\n            Max_Peak_Level: 3\n", report);

		assertEquals(3, c.evasion().auto().petty().maxPeakLevel());
		assertEquals(1, report.issues().size(), report.issues().toString());
		assertEquals(Severity.WARNING, report.issues().get(0).severity());
		assertEquals("config.conflict", report.issues().get(0).code());
		assertTrue(report.issues().get(0).message().contains("rampage lock wins"));
	}

	@Test
	@DisplayName("AUTO with evasion off and the heat ledger off gets an INFO note each; other modes are silent")
	void auto_crossBlockNotes() {
		ConfigReport report = new ConfigReport();
		ChaseConfig.parse(wantedRoot(
				"Wanted:\n   Heat:\n      Enable: false\n   Evasion:\n      Enable: false\n      Drop_Mode: AUTO\n",
				report), report);

		assertEquals(2, report.issues().size(), report.issues().toString());
		assertTrue(report.issues().stream().allMatch(i -> i.severity() == Severity.INFO));
		assertTrue(report.issues().toString().contains("AUTO does nothing while evasion is off"));
		assertTrue(report.issues().toString().contains("no heat ledger"));

		ConfigReport quiet = new ConfigReport();
		ChaseConfig.parse(wantedRoot("Wanted:\n   Heat:\n      Enable: false\n   Evasion:\n      Enable: false\n"
		                             + "      Drop_Mode: ONE_STAR\n      Auto:\n         Respot_Limit: 1\n", quiet),
		                  quiet);
		assertTrue(quiet.isEmpty(), quiet.issues().toString());
	}

	@Test
	@DisplayName("the six-argument EvasionSettings constructor carries the default Auto block")
	void evasionSettings_sixArgConstructor() {
		EvasionSettings e = new EvasionSettings(true, 3, DropMode.AUTO, List.of(1), List.of(1), 2.0);

		assertEquals(AutoSettings.DEFAULT, e.auto());
	}

	@Test
	@DisplayName("a file without the new blocks and keys reads the shipped hideout, quiet speed, cap, rampage weight and bribe stars")
	void newBlocksAbsent_areDefaults() {
		ConfigReport report = new ConfigReport();

		ChaseConfig c = ChaseConfig.parse(wantedRoot("Wanted:\n   Evasion:\n      Enable: true\n", report), report);

		assertEquals(HideoutSettings.DEFAULT, c.evasion().hideout());
		assertEquals(QuietSpeedSettings.DEFAULT, c.evasion().quietSpeed());
		assertEquals(4.0, c.evasion().maxSpeed());
		assertEquals(80, c.evasion().auto().rampageMinWeight());
		assertEquals(BribeStarSettings.DEFAULT, c.bribeStars());
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("the new blocks override key by key")
	void newBlocks_override() {
		ConfigReport report = new ConfigReport();
		String yaml = """
				Wanted:
				   Evasion:
				      Hideout:
				         Enable: false
				         Speed: 3.0
				      Quiet_Speed:
				         Enable: false
				         Per_Minute: 0.5
				         Max: 3.0
				         Backup_Skip_Seconds: 30
				      Max_Speed: 6.0
				      Auto:
				         Rampage_Min_Weight: 50
				   Bribe_Stars:
				      Enable: false
				      Stars: 2
				      Respawn_Seconds: 60
				      Pickup_Radius: 3.0
				      Item: DIAMOND
				""";

		ChaseConfig c = ChaseConfig.parse(wantedRoot(yaml, report), report);

		assertEquals(new HideoutSettings(false, 3.0), c.evasion().hideout());
		assertEquals(new QuietSpeedSettings(false, 0.5, 3.0, 30), c.evasion().quietSpeed());
		assertEquals(6.0, c.evasion().maxSpeed());
		assertEquals(50, c.evasion().auto().rampageMinWeight());
		assertEquals(new BribeStarSettings(false, 2, 60, 3.0, "DIAMOND"), c.bribeStars());
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("bad values log and fall back: speeds <= 0, Max_Speed < 1, Per_Minute < 0, negative Rampage_Min_Weight")
	void newKeys_badValuesFallBack() {
		ConfigReport report = new ConfigReport();
		String yaml = """
				Wanted:
				   Evasion:
				      Hideout:
				         Speed: 0
				      Quiet_Speed:
				         Per_Minute: -1
				         Max: -2
				      Max_Speed: 0.5
				      Auto:
				         Rampage_Min_Weight: -5
				""";

		ChaseConfig c = ChaseConfig.parse(wantedRoot(yaml, report), report);

		assertEquals(2.0, c.evasion().hideout().speed());
		assertEquals(0.25, c.evasion().quietSpeed().perMinute());
		assertEquals(2.0, c.evasion().quietSpeed().max());
		assertEquals(4.0, c.evasion().maxSpeed());
		assertEquals(80, c.evasion().auto().rampageMinWeight());
		assertEquals(5, report.issues().size(), report.issues().toString());
	}

	/** Final fix round 1: a 0.15.2 Outside_Zone_Speed above the new Max_Speed default is capped, so say so. */
	@Test
	@DisplayName("Outside_Zone_Speed above Max_Speed warns that the timer is capped")
	void outsideZoneSpeedAboveMaxSpeed_warns() {
		ConfigReport report = new ConfigReport();
		String yaml = """
				Wanted:
				   Evasion:
				      Outside_Zone_Speed: 6.0
				""";

		ChaseConfig c = ChaseConfig.parse(wantedRoot(yaml, report), report);

		assertEquals(4.0, c.evasion().maxSpeed());
		assertEquals(1, report.issues().size(), report.issues().toString());
		assertTrue(report.issues().toString().contains("config.conflict"), report.issues().toString());
	}

	@Test
	@DisplayName("quiet speed: 0 min = 1.0, 2 min = 1.5, 10 min capped at 2.0, disabled = 1.0")
	void quietSpeed_speedFor() {
		QuietSpeedSettings q = QuietSpeedSettings.DEFAULT;

		assertEquals(1.0, q.speedFor(0));
		assertEquals(1.0, q.speedFor(59_999));
		assertEquals(1.5, q.speedFor(2 * 60_000L));
		assertEquals(2.0, q.speedFor(10 * 60_000L));
		assertEquals(1.0, new QuietSpeedSettings(false, 0.25, 2.0, 60).speedFor(10 * 60_000L));
	}

	@Test
	@DisplayName("the 4-argument constructor defaults the bribe stars, and a null bribeStars maps to the default")
	void fourArgConstructor_defaultsBribeStars() {
		ChaseConfig d = ChaseConfig.DEFAULT;

		assertEquals(BribeStarSettings.DEFAULT,
		             new ChaseConfig(d.heat(), d.evasion(), d.hud(), d.chargeSheet()).bribeStars());
		assertEquals(BribeStarSettings.DEFAULT,
		             new ChaseConfig(d.heat(), d.evasion(), d.hud(), d.chargeSheet(), null).bribeStars());
	}
}
