package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane f2-config: {@link YamlCopConfigProvider} reads the 0.20.0 {@code Cops.*} blocks (Patrol, Warning, Pull_Over,
 * Tracker, Scanner, Siren, Sight, Overwatch, Scanner_Map, Disguise) and the tier key {@code Ignore_Darkness}. A missing
 * block or key is the code default; a bad value keeps the default and reports a warning.
 */
@DisplayName("YamlCopConfigProvider - 0.20.0 blocks")
class YamlCopConfigProvider020Test {

	private static final String TIERS = """
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			      4:
			         Display_Name: "&1SWAT"
			         Health: 40.0
			         Damage: 5.0
			         Ignore_Darkness: true
			""";

	@Test
	@DisplayName("a Cops section without the new blocks gives every new getter its spec default")
	void newBlocksMissing_giveDefaults() {
		CopConfigProvider provider = parse("Cops:\n" + TIERS, new ConfigReport());

		assertEquals(new PatrolSettings(true, 2, 64, 96, 30, 24, 3, 6, 1, true), provider.getPatrolSettings());
		assertEquals(new WarningSettings(true, 12.0, 5, 30), provider.getWarningSettings());
		assertEquals(new PullOverSettings(true, 24.0, 12.0, 6, 1.0, 2, 150.0, 120), provider.getPullOverSettings());
		assertEquals(new TrackerSettings(true, 2, 4, 48.0, 6, 150), provider.getTrackerSettings());
		assertEquals(new ScannerSettings(true, 160.0, "CLOCK", "&9Police Scanner", List.of("&7Hear the police radio")),
		             provider.getScannerSettings());
		assertEquals(new SirenSettings(true, 5, 24.0, "BLOCK_NOTE_BLOCK_BELL", 3.0f, 1.0f), provider.getSirenSettings());
		assertEquals(new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5), provider.getSightSettings());
		assertEquals(new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120),
		             provider.getOverwatchSettings());
		assertEquals(new ScannerMapSettings(true, 1, 10, true, "&9Scanner Map", List.of("&7Cops, search zone, hideouts")),
		             provider.getScannerMapSettings());
		assertEquals(DisguiseSettings.DEFAULT, provider.getDisguiseSettings());
	}

	@Test
	@DisplayName("every block reads key by key, including the nested Item block and the lists")
	void newBlocks_parseKeyByKey() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = parse("""
				Cops:
				   Patrol:
				      Enable: false
				      Cops_Per_Station: 3
				      Activation_Radius: 50
				      Despawn_Radius: 80
				      Despawn_After_Seconds: 10
				      Beat_Radius: 12
				      Pause_Seconds_Min: 1
				      Pause_Seconds_Max: 2
				      Tier: 2
				      Report_Wanted: false
				   Warning:
				      Enable: false
				      Range: 8.0
				      Seconds: 3
				      Repeat_Seconds: 10
				   Pull_Over:
				      Enable: false
				      Range: 30.0
				      Speed_Limit: 20
				      Comply_Seconds: 4
				      Stop_Speed: 0.5
				      Stop_Hold_Seconds: 1
				      Ticket_Amount: 75.5
				      Repeat_Seconds: 60
				   Tracker:
				      Enable: false
				      Min_Level: 3
				      Tag_Seconds: 2
				      Tag_Range: 30.0
				      Ping_Seconds: 3
				      Expire_Seconds: 90
				   Scanner:
				      Enable: false
				      Range: 100.0
				      Item:
				         Material: COMPASS
				         Name: "&cScanner"
				         Lore:
				            - "&7one"
				            - "&7two"
				   Siren:
				      Enable: false
				      Interval_Seconds: 8
				      Min_Distance: 30.0
				      Sound: "BLOCK_NOTE_BLOCK_PLING"
				      Volume: 2.0
				      Pitch: 0.5
				   Sight:
				      Enable: false
				      Dark_Max_Level: 3
				      Dark_Factor: 0.5
				      Dim_Max_Level: 7
				      Dim_Factor: 0.8
				      Crowd_Factor: 0.5
				      Crowd_Min_Civilians: 4
				      Crowd_Radius: 5.0
				      Crowd_Out_Of_Contact_Seconds: 3
				      Sneak_Factor: 0.8
				      Min_Factor: 0.3
				      Shot_Reset_Seconds: 6
				   Overwatch:
				      Enable: false
				      Min_Level: 5
				      Role: "Medic"
				      Ring_Min: 12.0
				      Ring_Max: 25.0
				      Columns: 6
				      Min_Height: 3
				      Sight_Range: 40.0
				      Max_Failures: 2
				      Reach_Seconds: 10
				      Max_Seconds: 60
				   Scanner_Map:
				      Enable: false
				      Blocks_Per_Pixel: 2
				      Refresh_Ticks: 20
				      Show_Hideouts: false
				      Item:
				         Name: "&aMap"
				         Lore:
				            - "&7line"
				   Disguise:
				      Enable: false
				      Mask_Items:
				         - "PLAYER_HEAD"
				      Mask_Wearables:
				         - "bandana"
				      Police_Outfit:
				         - "police_helmet"
				      Outfit_Max_Level: 1
				      Match_Needed: 3
				""" + TIERS, report);

		assertEquals(new PatrolSettings(false, 3, 50, 80, 10, 12, 1, 2, 2, false), provider.getPatrolSettings());
		assertEquals(new WarningSettings(false, 8.0, 3, 10), provider.getWarningSettings());
		assertEquals(new PullOverSettings(false, 30.0, 20.0, 4, 0.5, 1, 75.5, 60), provider.getPullOverSettings());
		assertEquals(new TrackerSettings(false, 3, 2, 30.0, 3, 90), provider.getTrackerSettings());
		assertEquals(new ScannerSettings(false, 100.0, "COMPASS", "&cScanner", List.of("&7one", "&7two")),
		             provider.getScannerSettings());
		assertEquals(new SirenSettings(false, 8, 30.0, "BLOCK_NOTE_BLOCK_PLING", 2.0f, 0.5f), provider.getSirenSettings());
		assertEquals(new SightSettings(false, 3, 0.5, 7, 0.8, 0.5, 4, 5.0, 3, 0.8, 0.3, 6), provider.getSightSettings());
		assertEquals(new OverwatchSettings(false, 5, "Medic", 12.0, 25.0, 6, 3, 40.0, 2, 10, 60),
		             provider.getOverwatchSettings());
		assertEquals(new ScannerMapSettings(false, 2, 20, false, "&aMap", List.of("&7line")),
		             provider.getScannerMapSettings());
		assertEquals(new DisguiseSettings(false, List.of("PLAYER_HEAD"), List.of("bandana"), List.of("police_helmet"), 1, 3),
		             provider.getDisguiseSettings());
		assertTrue(report.issues().isEmpty(), () -> "unexpected issues: " + report.issues());
	}

	@Test
	@DisplayName("a block with one key keeps every other key at its default (Pull_Over Speed_Limit 20)")
	void partialBlock_keepsOtherDefaults() {
		CopConfigProvider provider = parse("Cops:\n   Pull_Over:\n      Speed_Limit: 20\n" + TIERS, new ConfigReport());

		assertEquals(new PullOverSettings(true, 24.0, 20.0, 6, 1.0, 2, 150.0, 120), provider.getPullOverSettings());
	}

	@Test
	@DisplayName("Overwatch Ring_Min above Ring_Max warns and the ring keeps its defaults (10 and 20)")
	void overwatchRing_minOverMax_warnsAndKeepsDefaults() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = parse("Cops:\n   Overwatch:\n      Ring_Min: 30.0\n      Ring_Max: 20.0\n"
		                                   + "      Columns: 6\n" + TIERS, report);

		assertEquals(10.0, provider.getOverwatchSettings().ringMin());
		assertEquals(20.0, provider.getOverwatchSettings().ringMax());
		assertEquals(6, provider.getOverwatchSettings().columns());
		assertIssueAt(report, "Ring_Min");
	}

	@Test
	@DisplayName("Patrol Pause_Seconds_Min above Pause_Seconds_Max warns and the pause keeps its defaults (3 and 6)")
	void patrolPause_minOverMax_warnsAndKeepsDefaults() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = parse("Cops:\n   Patrol:\n      Pause_Seconds_Min: 9\n      Pause_Seconds_Max: 4\n"
		                                   + TIERS, report);

		assertEquals(3, provider.getPatrolSettings().pauseSecondsMin());
		assertEquals(6, provider.getPatrolSettings().pauseSecondsMax());
		assertIssueAt(report, "Pause_Seconds");
	}

	@Test
	@DisplayName("a non-number keeps that key's default, warns, and its sibling keys still read")
	void badNumber_keepsDefaultAndWarns() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = parse("Cops:\n   Tracker:\n      Tag_Seconds: soon\n      Min_Level: 3\n" + TIERS,
		                                   report);

		assertEquals(4, provider.getTrackerSettings().tagSeconds());
		assertEquals(3, provider.getTrackerSettings().minLevel());
		assertIssueAt(report, "Tag_Seconds");
	}

	@Test
	@DisplayName("an out-of-range value keeps the default and reports config.range (Sight Dark_Factor 2.5 -> 0.4)")
	void outOfRange_isClampedAndWarns() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = parse("Cops:\n   Sight:\n      Dark_Factor: 2.5\n" + TIERS, report);

		assertEquals(0.4, provider.getSightSettings().darkFactor());
		assertTrue(report.issues().stream().anyMatch(i -> "config.range".equals(i.code()) && i.path() != null
		                                                  && i.path().contains("Dark_Factor")),
		           () -> "expected a config.range issue at Dark_Factor, got " + report.issues());
	}

	@Test
	@DisplayName("Ignore_Darkness: a tier without the key is false, with true is true, a bad value is false and warns")
	void ignoreDarkness_defaultsFalse_readsTrue() {
		CopConfigProvider provider = parse("Cops:\n" + TIERS, new ConfigReport());

		assertFalse(provider.getTierConfig(1).ignoreDarkness());
		assertTrue(provider.getTierConfig(4).ignoreDarkness());

		ConfigReport badReport = new ConfigReport();
		CopConfigProvider bad = parse("Cops:\n   Tiers:\n      1:\n         Display_Name: \"x\"\n         Health: 20.0\n"
		                              + "         Damage: 2.0\n         Ignore_Darkness: maybe\n", badReport);
		assertFalse(bad.getTierConfig(1).ignoreDarkness());
		assertIssueAt(badReport, "Ignore_Darkness");

		// merge safety with 0.21.0: the pre-0.20 17-argument constructor still exists and means "false"
		CopTierConfig old = new CopTierConfig(1, "x", 1, 1, 1, 1, false, false, List.of(), List.of(), null, null, null,
		                                      null, null, null, 1.0);
		assertFalse(old.ignoreDarkness());
	}

	/** A report issue whose path names the key (manual adds use "&lt;Block&gt;.&lt;Key&gt;", e.g. Overwatch.Ring_Min). */
	private static void assertIssueAt(ConfigReport report, String key) {
		assertTrue(report.issues().stream().anyMatch(i -> i.path() != null && i.path().contains(key)),
		           () -> "expected an issue whose path contains " + key + ", got " + report.issues());
	}

	private static CopConfigProvider parse(String yaml, ConfigReport report) {
		Reader         reader   = new StringReader(yaml);
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), reader, report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
