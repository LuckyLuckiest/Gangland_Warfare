package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.bukkit.Material;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcFanPlacement;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YamlCopConfigProvider - Melee/Tactics/Radio/Backup (phase H12)")
class YamlCopConfigProviderTest {

	@Test
	@DisplayName("Cops.Melee parses, including Edge_Damage")
	void meleeBlock_parsed_includingEdgeDamage() {
		CopConfigProvider provider = parse("""
				Cops:
				   Melee:
				      Reach: 3.5
				      Approach: 2.5
				      Damage_Spread: 0.2
				      Edge_Damage: 0.6
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(3.5, provider.getMeleeProfile().reach());
		assertEquals(2.5, provider.getMeleeProfile().approach());
		assertEquals(0.2, provider.getMeleeProfile().damageSpread());
		assertEquals(0.6, provider.getMeleeProfile().edgeDamage());
	}

	@Test
	@DisplayName("a tier's own Tactics overrides Cops.Tactics key by key; SWAT arc 270, Military arc 330")
	void tierTactics_parsed_swatArc270_militaryArc330() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tactics:
				      Formation_Arc: 270.0
				      Strafe_Degrees: 15.0
				      Reposition_Ticks: 60
				      Moving_Aim_Error: 0.10
				   Tiers:
				      3:
				         Display_Name: "&1Lieutenant"
				         Health: 30.0
				         Damage: 4.0
				         Tactics:
				            Formation_Arc: 200.0
				            Strafe_Degrees: 10.0
				            Reposition_Ticks: 80
				            Moving_Aim_Error: 0.12
				      4:
				         Display_Name: "&1SWAT"
				         Health: 40.0
				         Damage: 5.0
				         Tactics:
				            Formation_Arc: 270.0
				            Strafe_Degrees: 15.0
				            Reposition_Ticks: 60
				            Moving_Aim_Error: 0.10
				      5:
				         Display_Name: "&4Military"
				         Health: 60.0
				         Damage: 7.0
				         Tactics:
				            Formation_Arc: 330.0
				            Strafe_Degrees: 20.0
				            Reposition_Ticks: 40
				            Moving_Aim_Error: 0.08
				""");

		assertEquals(200.0, provider.getTierConfig(3).tactics().formationArc());
		assertEquals(270.0, provider.getTierConfig(4).tactics().formationArc());
		assertEquals(330.0, provider.getTierConfig(5).tactics().formationArc());
		assertEquals(40L, provider.getTierConfig(5).tactics().engagement().repositionMs() / 50L);
	}

	@Test
	@DisplayName("a tier with no Tactics block of its own inherits Cops.Tactics wholesale")
	void tierWithoutOwnTactics_inheritsModuleDefault() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tactics:
				      Formation_Arc: 200.0
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				      5:
				         Display_Name: "&4Military"
				         Health: 40.0
				         Damage: 6.0
				""");

		assertEquals(200.0, provider.getTierConfig(1).tactics().formationArc());
		assertEquals(200.0, provider.getTierConfig(5).tactics().formationArc(),
		             "an operator's Cops.Tactics wins over the per-tier code default");
	}

	@Test
	@DisplayName("a 0.11-shaped cops.yml (no Tactics anywhere) still gets the decided cop arcs 270/200/270/330")
	void legacyFile_noTactics_getsCopArcs() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				      3:
				         Display_Name: "&9Lieutenant"
				         Health: 30.0
				         Damage: 4.0
				      4:
				         Display_Name: "&8SWAT"
				         Health: 35.0
				         Damage: 5.0
				      5:
				         Display_Name: "&4Military"
				         Health: 40.0
				         Damage: 6.0
				""");

		assertEquals(270.0, provider.getTierConfig(1).tactics().formationArc());
		assertEquals(200.0, provider.getTierConfig(3).tactics().formationArc());
		assertEquals(270.0, provider.getTierConfig(4).tactics().formationArc());
		assertEquals(330.0, provider.getTierConfig(5).tactics().formationArc());
		assertTrue(provider.getTierConfig(5).tactics().engagement().enabled());
	}

	@Test
	@DisplayName("Cops.Radio parses Priority, Cooldown_Ticks and Responder_Max")
	void radioBlock_parsed_priorityCooldownsResponderMax() {
		CopConfigProvider provider = parse("""
				Cops:
				   Radio:
				      Enabled: true
				      Range: 32.0
				      Responder_Max: 2
				      Priority:
				         - Contact
				         - Man_Down
				      Cooldown_Ticks:
				         Contact: 160
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(2, provider.getRadioSettings().responderMax());
		assertTrue(provider.getRadioSettings().isPriority("Contact"));
		assertTrue(provider.getRadioSettings().isPriority("Man_Down"));
		assertFalse(provider.getRadioSettings().isPriority("Reposition"));
		assertEquals(8000L, provider.getRadioSettings().cooldownFor("Contact"));
	}

	@Test
	@DisplayName("Cops.Backup parses Enabled/Extra_Cops/Duration_Ticks/Cooldown_Ticks (as milliseconds)")
	void backupBlock_parsed() {
		CopConfigProvider provider = parse("""
				Cops:
				   Backup:
				      Enabled: true
				      Extra_Cops: 1
				      Duration_Ticks: 600
				      Cooldown_Ticks: 1200
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		BackupSettings backup = provider.getBackupSettings();
		assertTrue(backup.enabled());
		assertEquals(1, backup.extraCops());
		assertEquals(30_000L, backup.durationMs());
		assertEquals(60_000L, backup.cooldownMs());
	}

	@Test
	@DisplayName("Cops.Tiers.<n>.Fire_Rate_Multiplier parses; a tier without it keeps today's per-AI-tick cadence")
	void tierFireRateMultiplier_parsed() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tiers:
				      3:
				         Display_Name: "&1Lieutenant"
				         Health: 30.0
				         Damage: 4.0
				         Fire_Rate_Multiplier: 0.25
				      4:
				         Display_Name: "&1SWAT"
				         Health: 40.0
				         Damage: 5.0
				""");

		assertEquals(0.25, provider.getTierConfig(3).fireRateMultiplier());
		// no key: 1 / AI_Tick_Rate (10 with no settings), the pre-1.13 cadence of one weapon tick per AI tick
		assertEquals(0.1, provider.getTierConfig(4).fireRateMultiplier(), 1e-9);
	}

	@Test
	@DisplayName("a non-positive Fire_Rate_Multiplier is reported and replaced by the default")
	void tierFireRateMultiplier_nonPositive_reported() {
		ConfigReport   report   = new ConfigReport();
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), new StringReader("""
				Cops:
				   Tiers:
				      4:
				         Display_Name: "&1SWAT"
				         Health: 40.0
				         Damage: 5.0
				         Fire_Rate_Multiplier: 0
				"""), report);
		CopConfigProvider provider =
				new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);

		assertEquals(0.1, provider.getTierConfig(4).fireRateMultiplier(), 1e-9);
		assertTrue(report.issues().stream().anyMatch(i -> "config.range".equals(i.code())),
		           () -> "issues: " + report.issues());
	}

	@Test
	@DisplayName("Cops.Retreat parses Enabled/Health_Fraction/Radius")
	void retreatBlock_parsed() {
		CopConfigProvider provider = parse("""
				Cops:
				   Retreat:
				      Enabled: false
				      Health_Fraction: 0.4
				      Radius: 10.0
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(new RetreatSettings(false, 0.4, 10.0), provider.getRetreatSettings());
	}

	@Test
	@DisplayName("Cops.Stuck parses Enabled/Recycle_Seconds/Avoid_Spawner_Seconds")
	void stuckBlock_parsed() {
		CopConfigProvider provider = parse("""
				Cops:
				   Stuck:
				      Enabled: false
				      Recycle_Seconds: 30
				      Avoid_Spawner_Seconds: 90
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(new StuckSettings(false, 30, 90), provider.getStuckSettings());
	}

	@Test
	@DisplayName("missing Melee/Tactics/Radio/Backup blocks fall back to their documented defaults")
	void missingBlocks_defaults() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(3.0, provider.getMeleeProfile().reach());
		assertEquals(0.7, provider.getMeleeProfile().edgeDamage());
		assertEquals(270.0, provider.getTierConfig(1).tactics().formationArc());
		assertTrue(provider.getTierConfig(1).tactics().engagement().enabled());
		assertTrue(provider.getRadioSettings().enabled());
		assertEquals(2, provider.getRadioSettings().responderMax());
		assertTrue(provider.getBackupSettings().enabled());
		assertEquals(1, provider.getBackupSettings().extraCops());
		assertEquals(RetreatSettings.DEFAULT, provider.getRetreatSettings());
		assertEquals(new StuckSettings(true, 12, 60), provider.getStuckSettings());
		assertEquals(StuckSettings.DEFAULT, provider.getStuckSettings());
	}

	@Test
	@DisplayName("the shipped cops.yml declares Melee/Tactics/Radio/Backup and the loader reads every key")
	void shippedFile_loadsWithNoUnknownTacticsKeys() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("copsncrooks/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		ConfigReport   report   = new ConfigReport();
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), new StringReader(yaml), report);
		CopConfigProvider provider =
				new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())),
		          () -> "unknown keys: " + report.issues());
		assertEquals(200.0, provider.getTierConfig(3).tactics().formationArc());
		assertEquals(270.0, provider.getTierConfig(4).tactics().formationArc());
		assertEquals(330.0, provider.getTierConfig(5).tactics().formationArc());
		assertEquals(0.1, provider.getTierConfig(4).fireRateMultiplier(), 1e-9);
		assertTrue(provider.getRetreatSettings().enabled());
		assertEquals(StuckSettings.DEFAULT, provider.getStuckSettings());
		assertTrue(provider.getRadioSettings().cooldownFor("Fall_Back") > 0);
		assertTrue(provider.getRadioSettings().cooldownFor("In_Cover") > 0);
		assertEquals(CopNames.DEFAULT, provider.getNames());
	}

	@Test
	@DisplayName("Fall_Back and In_Cover are priority lines in the shipped cops.yml and the code defaults, so a same-tick Resisting cannot drop them (T-134)")
	void retreatLines_arePriority() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("copsncrooks/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		CopConfigProvider provider = parse(yaml);

		for (String key : new String[]{"Fall_Back", "In_Cover", "Commander_Down"}) {
			assertTrue(provider.getRadioSettings().isPriority(key), key);
			assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority(key), key);
			assertTrue(provider.getRadioSettings().cooldownFor(key) > 0, key);
			assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.cooldownFor(key) > 0, key);
		}
	}

	@Test
	@DisplayName("Cops.Names parses Format and First_Names; absent gives the defaults, [] an empty pool")
	void namesBlock_parsed() {
		String tiers = """
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""";
		CopConfigProvider provider = parse("""
				Cops:
				   Names:
				      Format: "%rank% %name%"
				      First_Names:
				         - Bob
				         - " "
				""" + tiers);
		assertEquals(new CopNames("%rank% %name%", List.of("Bob")), provider.getNames());

		assertEquals(CopNames.DEFAULT, parse("Cops:\n" + tiers).getNames());

		CopNames empty = parse("""
				Cops:
				   Names:
				      First_Names: []
				""" + tiers).getNames();
		assertEquals(CopNames.DEFAULT.format(), empty.format());
		assertTrue(empty.firstNames().isEmpty());
	}

	// ── Roles and Squad_Composition (phase H13) ───────────────────────────────

	private static final String ONE_TIER = """
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			""";

	@Test
	@DisplayName("no cop_roles.yml (the cops.yml-only constructor): the built-in catalogue and compositions")
	void noRoleBlocks_builtInCatalogue() {
		CopConfigProvider provider = parse("Cops:\n" + ONE_TIER);

		assertEquals(List.of("Pointman", "Assault"), names(provider.getSquadComposition(1)));
		assertEquals(List.of("Pointman", "Assault", "Assault"), names(provider.getSquadComposition(2)));
		assertEquals(List.of("Commander", "Pointman", "Defender", "Marksman", "Assault"),
		             names(provider.getSquadComposition(3)));
		assertEquals(List.of("Commander", "Pointman", "Defender", "Marksman", "Medic", "Assault"),
		             names(provider.getSquadComposition(5))); // a level with no entry uses the highest lower one
		assertNull(provider.getSquadComposition(0));

		Map<String, CopRole> roles = byName(provider.getSquadComposition(5));
		CopRole defender = roles.get("Defender");
		assertEquals(NpcFanPlacement.CENTER, defender.placement());
		assertEquals(Material.SHIELD, defender.kit().offHand().material());
		assertEquals(0.5, defender.blockFraction());
		assertEquals(60.0, defender.blockConeDegrees());
		assertEquals(0.0, defender.strafeDegrees());

		CopRole marksman = roles.get("Marksman");
		assertTrue(marksman.fireRateScale() < 1.0);
		assertEquals(1, marksman.difficultyBonus());
		assertTrue(marksman.rangedMin() > roles.get("Pointman").rangedMax()); // the back of the fan

		CopRole commander = roles.get("Commander");
		assertTrue(commander.commander());
		assertTrue(commander.leaderPriority() > roles.get("Pointman").leaderPriority());
		assertTrue(roles.get("Pointman").leaderPriority() > roles.get("Assault").leaderPriority());
		assertTrue(roles.get("Medic").medic());
		assertEquals(NpcFanPlacement.FLANK, roles.get("Assault").placement());
		for (CopRole role : roles.values()) assertEquals(role.name(), role.displayName());
	}

	// ── Field care (phase H13) ─────────────────────────────────────────────────

	@Test
	@DisplayName("Cops.Field_Care is parsed; a cops.yml without it gets FieldCareSettings.DEFAULT with no unknown keys")
	void fieldCare_parsed_andDefaultWhenAbsent() {
		ConfigReport      report   = new ConfigReport();
		CopConfigProvider provider = parse("Cops:\n" + ONE_TIER, report);
		assertEquals(FieldCareSettings.DEFAULT, provider.getFieldCareSettings());
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           report.issues()::toString);

		ConfigReport parsedReport = new ConfigReport();
		CopConfigProvider parsed = parse("""
				Cops:
				   Field_Care:
				      Enabled: true
				      Health_Fraction: 0.4
				      Limp_Speed: 0.6
				      Medic_Enabled: false
				      Medic_Radius: 20.0
				      Heal_Range: 3.0
				      Channel_Ticks: 80
				      Heal_Fraction: 0.3
				""" + ONE_TIER, parsedReport);
		assertEquals(new FieldCareSettings(true, 0.4, 0.6, false, 20.0, 3.0, 80, 0.3), parsed.getFieldCareSettings());
		assertTrue(parsedReport.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           parsedReport.issues()::toString);
	}

	@Test
	@DisplayName("the field-care radio cooldowns: milliseconds in the code defaults, the shipped cops.yml's ticks x50 agree; Hit and Patched_Up are priority lines")
	void fieldCareRadioCooldowns_millisecondsInCode_ticksInYaml() throws IOException {
		Map<String, Long> expected = Map.of("Hit", 5000L, "Medic_Moving", 5000L, "Covering_Fire", 10000L,
		                                    "Medic_Pinned", 8000L, "Patched_Up", 5000L);
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("copsncrooks/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		CopConfigProvider shipped = parse(yaml);

		expected.forEach((key, ms) -> {
			assertEquals(ms, CopConfigProvider.COP_RADIO_DEFAULTS.cooldownFor(key), key);
			assertEquals(ms, shipped.getRadioSettings().cooldownFor(key), key);
			// said at once through sayAs, the gaps would drop them; the follow-ups go through sayLater instead
			boolean priority = key.equals("Hit") || key.equals("Patched_Up");
			assertEquals(priority, CopConfigProvider.COP_RADIO_DEFAULTS.isPriority(key), key);
			assertEquals(priority, shipped.getRadioSettings().isPriority(key), key);
		});
		assertEquals(FieldCareSettings.DEFAULT, shipped.getFieldCareSettings());
	}

	@Test
	@DisplayName("absent Regroup / Shot_Noise blocks give the documented defaults")
	void absentBlocks_areDefault() {
		CopConfigProvider provider = parse("""
				Cops:
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(RegroupSettings.DEFAULT, provider.getRegroupSettings());
		assertEquals(ShotNoiseSettings.DEFAULT, provider.getShotNoiseSettings());
		assertEquals(48.0, provider.getShotNoiseSettings().radiusFor("GUN"));
	}

	@Test
	@DisplayName("Cops.Regroup is parsed, seconds becoming milliseconds")
	void regroup_isParsed_secondsToMillis() {
		RegroupSettings regroup = parse("""
				Cops:
				   Regroup:
				      Enabled: false
				      Casualties: 3
				      Window_Seconds: 10
				      Fall_Back_Seconds: 5
				      Cooldown_Seconds: 90
				      Arrival_Radius: 12.5
				""").getRegroupSettings();

		assertEquals(new RegroupSettings(false, 3, 10_000L, 5_000L, 90_000L, 12.5), regroup);
	}

	@Test
	@DisplayName("Shot_Noise.Radius lookups ignore case and an unlisted weapon type is 0")
	void shotNoise_radiusFor_isCaseInsensitive_unlistedIsZero() {
		ShotNoiseSettings noise = parse("""
				Cops:
				   Shot_Noise:
				      Enabled: true
				      Radius:
				         GUN: 30
				         throwable: 7.5
				""").getShotNoiseSettings();

		assertEquals(30.0, noise.radiusFor("gun"));
		assertEquals(7.5, noise.radiusFor("THROWABLE"));
		assertEquals(0.0, noise.radiusFor("MELEE"), "unlisted");
		assertEquals(0.0, noise.radiusFor("BIOLOGICAL"), "unlisted");
	}

	@Test
	@DisplayName("Shot_Noise.Enabled false makes every radius 0")
	void shotNoiseDisabled_radiusIsZero() {
		ShotNoiseSettings noise = parse("""
				Cops:
				   Shot_Noise:
				      Enabled: false
				      Radius:
				         GUN: 48
				""").getShotNoiseSettings();

		assertEquals(0.0, noise.radiusFor("GUN"));
	}

	@Test
	@DisplayName("the shipped cops.yml's Regroup / Shot_Noise equal the code defaults, with no unknown keys")
	void bundledCopsYml_equalsDefaults() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("copsncrooks/cops.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertTrue(yaml.contains("   Regroup:") && yaml.contains("   Shot_Noise:"), "blocks must be declared");

		ConfigReport      report   = new ConfigReport();
		CopConfigProvider provider = parse(yaml, report);

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())),
		          () -> "unknown keys: " + report.issues());
		assertEquals(RegroupSettings.DEFAULT, provider.getRegroupSettings());
		assertEquals(ShotNoiseSettings.DEFAULT, provider.getShotNoiseSettings());
		for (String key : List.of("Regroup", "Regroup_Push", "Shots_Fired")) {
			assertEquals(CopConfigProvider.COP_RADIO_DEFAULTS.cooldownFor(key), provider.getRadioSettings().cooldownFor(key), key);
		}
	}

	@Test
	@DisplayName("a pre-upgrade Cops.Radio block (no Regroup/Shots_Fired keys) still gets the new cooldowns as defaults")
	void preUpgradeRadioBlock_getsTheNewCooldownsAsDefaults() {
		CopConfigProvider provider = parse("""
				Cops:
				   Radio:
				      Priority:
				         - "Contact"
				      Cooldown_Ticks:
				         Contact: 160
				""");

		assertEquals(160 * 50L, provider.getRadioSettings().cooldownFor("Contact"));
		assertEquals(60_000L, provider.getRadioSettings().cooldownFor("Regroup"));
		assertEquals(60_000L, provider.getRadioSettings().cooldownFor("Regroup_Push"));
		assertEquals(3_000L, provider.getRadioSettings().cooldownFor("Shots_Fired"));
		assertFalse(provider.getRadioSettings().isPriority("Regroup"), "priority list stays as the file wrote it");
	}

	private static List<String> names(List<CopRole> roles) {
		return roles.stream().map(CopRole::name).toList();
	}

	private static Map<String, CopRole> byName(List<CopRole> roles) {
		Map<String, CopRole> result = new LinkedHashMap<>();
		for (CopRole role : roles) result.putIfAbsent(role.name(), role);
		return result;
	}

	private static CopConfigProvider parse(String yaml) {
		return parse(yaml, new ConfigReport());
	}

	private static CopConfigProvider parse(String yaml, ConfigReport report) {
		Reader         reader   = new StringReader(yaml);
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), reader, report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
