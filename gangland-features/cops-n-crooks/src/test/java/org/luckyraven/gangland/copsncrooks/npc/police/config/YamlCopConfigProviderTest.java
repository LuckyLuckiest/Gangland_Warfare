package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.RetreatSettings;
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
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
				""");

		assertEquals(200.0, provider.getTierConfig(1).tactics().formationArc());
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
		assertEquals(160.0, provider.getTierConfig(1).tactics().formationArc());
		assertTrue(provider.getTierConfig(1).tactics().engagement().enabled());
		assertTrue(provider.getRadioSettings().enabled());
		assertEquals(2, provider.getRadioSettings().responderMax());
		assertTrue(provider.getBackupSettings().enabled());
		assertEquals(1, provider.getBackupSettings().extraCops());
		assertEquals(RetreatSettings.DEFAULT, provider.getRetreatSettings());
	}

	@Test
	@DisplayName("the shipped cops.yml declares Melee/Tactics/Radio/Backup and the loader reads every key")
	void shippedFile_loadsWithNoUnknownTacticsKeys() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("npc/cops.yml"))) {
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
		assertTrue(provider.getRadioSettings().cooldownFor("Fall_Back") > 0);
		assertTrue(provider.getRadioSettings().cooldownFor("In_Cover") > 0);
	}

	private static CopConfigProvider parse(String yaml) {
		ConfigReport   report   = new ConfigReport();
		Reader         reader   = new StringReader(yaml);
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), reader, report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
