package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.OverwatchSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PatrolSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.PullOverSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerMapSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SirenSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.WarningSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.config.YamlCopConfigProvider;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.TrespassSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.WitnessSettings;
import org.luckyraven.gangland.npc.radio.RadioSettings;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lane f2-config: the BUNDLED {@code cops.yml} and {@code wanted.yml} carry every 0.20.0 block with the spec values,
 * parse with an empty {@link ConfigReport}, use block style only, and the code radio defaults agree with the file.
 */
@DisplayName("Bundled cops.yml / wanted.yml - 0.20.0 blocks")
class Bundled020YamlTest {

	private static final Map<String, Long> NEW_COOLDOWN_TICKS = Map.of("Caller_911", 100L, "Suspect_Description", 1200L,
	                                                                   "Tracker_Tagged", 600L, "Tracker_Ping", 400L,
	                                                                   "Overwatch_Spotted", 200L, "Patrol_Sighting", 400L);

	@Test
	@DisplayName("bundled cops.yml: the new blocks equal the spec defaults, tiers 4/5 ignore darkness, report is empty")
	void bundledCops_newBlocks() throws IOException {
		ConfigReport      report   = new ConfigReport();
		CopConfigProvider provider = copsProvider(report);

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
		assertEquals(new DisguiseSettings(true, List.of("CARVED_PUMPKIN", "PLAYER_HEAD", "ZOMBIE_HEAD", "SKELETON_SKULL",
		                                                "CREEPER_HEAD"), List.of(), List.of("police_helmet", "police_vest"),
		                                  2, 2), provider.getDisguiseSettings());
		assertTrue(report.issues().isEmpty(), () -> "bundled cops.yml has issues: " + report.issues());
	}

	@Test
	@DisplayName("bundled cops.yml: Ignore_Darkness is true on the SWAT and Military tiers and false below")
	void bundledCops_ignoreDarkness() throws IOException {
		CopConfigProvider provider = copsProvider(new ConfigReport());
		String            yaml     = resource("copsncrooks/cops.yml");

		int tiers = provider.getMaxTier();
		assertTrue(tiers >= 5, "expected the five shipped tiers");
		assertTrue(provider.getTierConfig(tiers - 1).ignoreDarkness(), "SWAT");
		assertTrue(provider.getTierConfig(tiers).ignoreDarkness(), "Military");
		assertFalse(provider.getTierConfig(1).ignoreDarkness(), "Officer");
		assertTrue(yaml.contains("Ignore_Darkness"), "documented with a comment in the file");
	}

	@Test
	@DisplayName("bundled cops.yml: the six new radio kinds have Cooldown_Ticks; Overwatch_Spotted and Caller_911 are priority")
	void bundledCops_radioCooldownsAndPriority() throws IOException {
		RadioSettings radio = copsProvider(new ConfigReport()).getRadioSettings();

		for (Map.Entry<String, Long> entry : NEW_COOLDOWN_TICKS.entrySet()) {
			assertEquals(entry.getValue() * 50L, radio.cooldownFor(entry.getKey()), "bundled " + entry.getKey());
			assertEquals(entry.getValue() * 50L, CopConfigProvider.COP_RADIO_DEFAULTS.cooldownFor(entry.getKey()),
			             "code default " + entry.getKey());
		}
		assertTrue(radio.isPriority("Overwatch_Spotted"));
		assertTrue(radio.isPriority("Caller_911"));
		// 0.21.2 S4: a cop's report is a dispatch line read right after Man_Down: it must not lose to the player gap
		assertTrue(radio.isPriority("Officer_Report"));
		assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority("Officer_Report"));
		assertEquals(100L * 50L, radio.cooldownFor("Officer_Report"));
		assertEquals(100L * 50L, CopConfigProvider.COP_RADIO_DEFAULTS.cooldownFor("Officer_Report"));
		assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority("Overwatch_Spotted"));
		assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority("Caller_911"));
	}

	@Test
	@DisplayName("bundled wanted.yml: Witnesses, Trespass and Heat.Crimes.Evading_Police 100; report is empty")
	void bundledWanted_newBlocks() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wantedRoot(resource("copsncrooks/wanted.yml"), report);

		// 0.21.2 S4 (T-541): Assault_Cop and Kill_Cop are gated too, and Witnesses.Cops / Defer_Minutes ship
		assertEquals(new WitnessSettings(true, List.of("Car_Theft", "Assault_Civilian", "Kill_Civilian", "Assault_Cop",
		                                               "Kill_Cop"), 10,
		                                 new WitnessSettings.Cops(3, 180.0, 6.0, List.of(), true)),
		             WitnessSettings.parse(wanted));
		assertEquals(WitnessSettings.DEFAULT, WitnessSettings.parse(wanted), "the code default equals the bundled file");
		assertEquals(new TrespassSettings(true, 60), TrespassSettings.parse(wanted));
		assertEquals(100, ChaseConfig.parse(wanted, report).heat().weightOf("Evading_Police"));
		assertEquals(Set.of("Enable", "Defer_Minutes", "Crimes", "Cops"), keysOf(wanted, "Witnesses"));
		assertEquals(Set.of("Report_Seconds", "View_Angle", "Witness_Floor", "Loud_Crimes", "Reporter_Glow"),
		             keysOf(wanted, "Witnesses", "Cops"));
		assertEquals(Set.of("Enable", "Repeat_Seconds"), keysOf(wanted, "Trespass"));
		assertTrue(keysOf(wanted, "Heat", "Crimes").contains("Evading_Police"), "Heat.Crimes lists Evading_Police");
		assertEquals(ChaseConfig.DEFAULT, ChaseConfig.parse(wanted, report));
		assertEquals(0.25, ChaseConfig.parse(wanted, report).evasion().noCopInsideSpeed(), "0.21.2 No_Cop_Inside_Speed");
		assertTrue(keysOf(wanted, "Evasion").contains("No_Cop_Inside_Speed"), "Evasion ships No_Cop_Inside_Speed");
		assertTrue(keysOf(wanted, "Heat").containsAll(Set.of("Per_Extra_Witness", "Max_Extra_Witnesses")),
		           "0.21.2 S3: Heat ships Per_Extra_Witness and Max_Extra_Witnesses");
		assertEquals(0.5, ChaseConfig.parse(wanted, report).heat().perExtraWitness());
		assertEquals(3, ChaseConfig.parse(wanted, report).heat().maxExtraWitnesses());
		assertTrue(report.issues().isEmpty(), () -> "bundled wanted.yml has issues: " + report.issues());
	}

	@Test
	@DisplayName("bundled files use block style only, document every new key, and never call setDefaults/copyDefaults")
	void bundledFiles_blockStyleAndDocumented() throws IOException {
		for (String file : List.of("copsncrooks/cops.yml", "copsncrooks/wanted.yml", "copsncrooks/cop_radio_messages.yml",
		                           "copsncrooks/cop_radio_messages_es.yml", "copsncrooks/wanted_messages.yml")) {
			String yaml = resource(file);

			assertTrue(yaml.lines().noneMatch(line -> !line.stripLeading().startsWith("#") && line.contains(": {")
			                                          && !line.contains("{}")), file + " uses a flow-style map");
		}
		String cops = resource("copsncrooks/cops.yml");
		for (String block : List.of("Patrol:", "Warning:", "Pull_Over:", "Tracker:", "Scanner:", "Siren:", "Sight:",
		                            "Overwatch:", "Scanner_Map:", "Disguise:")) {
			assertTrue(cops.contains("   " + block), "cops.yml lacks " + block);
		}
		assertTrue(cops.lines().anyMatch(line -> line.strip().equals("# 0.20.0")),
		           "the appended blocks carry a '# 0.20.0' comment line");
		String wanted = resource("copsncrooks/wanted.yml");
		assertTrue(wanted.contains("   Witnesses:") && wanted.contains("   Trespass:"));
		assertTrue(wanted.contains("# 0.20.0"));
	}

	@Test
	@DisplayName("bundled cops.yml ships exactly the spec keys per new block, each with a comment line above it")
	void bundledCops_everyNewKeyShippedAndCommented() throws IOException {
		Map<String, Set<String>> yamlKeys = new LinkedHashMap<>();
		yamlKeys.put("Patrol", Set.of("Enable", "Cops_Per_Station", "Activation_Radius", "Despawn_Radius",
		                              "Despawn_After_Seconds", "Beat_Radius", "Pause_Seconds_Min", "Pause_Seconds_Max",
		                              "Tier", "Report_Wanted", "Respawn_Seconds"));
		yamlKeys.put("Warning", Set.of("Enable", "Range", "Seconds", "Repeat_Seconds"));
		yamlKeys.put("Pull_Over", Set.of("Enable", "Range", "Speed_Limit", "Comply_Seconds", "Stop_Speed",
		                                 "Stop_Hold_Seconds", "Ticket_Amount", "Repeat_Seconds"));
		yamlKeys.put("Tracker", Set.of("Enable", "Min_Level", "Tag_Seconds", "Tag_Range", "Ping_Seconds",
		                               "Expire_Seconds"));
		yamlKeys.put("Scanner", Set.of("Enable", "Range", "Item"));
		yamlKeys.put("Siren", Set.of("Enable", "Interval_Seconds", "Min_Distance", "Sound", "Volume", "Pitch"));
		yamlKeys.put("Sight", Set.of("Enable", "Dark_Max_Level", "Dark_Factor", "Dim_Max_Level", "Dim_Factor",
		                             "Crowd_Factor", "Crowd_Min_Civilians", "Crowd_Radius",
		                             "Crowd_Out_Of_Contact_Seconds", "Sneak_Factor", "Min_Factor", "Shot_Reset_Seconds"));
		yamlKeys.put("Overwatch", Set.of("Enable", "Min_Level", "Role", "Ring_Min", "Ring_Max", "Columns", "Min_Height",
		                                 "Sight_Range", "Max_Failures", "Reach_Seconds", "Max_Seconds"));
		yamlKeys.put("Scanner_Map", Set.of("Enable", "Blocks_Per_Pixel", "Refresh_Ticks", "Show_Hideouts", "Item"));
		yamlKeys.put("Disguise", Set.of("Enable", "Mask_Items", "Mask_Wearables", "Police_Outfit", "Outfit_Max_Level",
		                                "Match_Needed"));

		String     cops   = resource("copsncrooks/cops.yml");
		ConfigReport report = new ConfigReport();
		NodeReader root   = NodeReader.of(new ConfigParser().parse(Path.of("cops.yml"), new StringReader(cops), report)
		                                                    .root(), report);

		for (Map.Entry<String, Set<String>> block : yamlKeys.entrySet()) {
			assertEquals(block.getValue(), keysOf(root, "Cops", block.getKey()), "Cops." + block.getKey());
		}
		assertEquals(Set.of("Material", "Name", "Lore"), keysOf(root, "Cops", "Scanner", "Item"));
		assertEquals(Set.of("Name", "Lore"), keysOf(root, "Cops", "Scanner_Map", "Item"));

		List<String> lines = cops.lines().toList();
		int          from  = -1;
		for (int i = 0; i < lines.size() && from < 0; i++) {
			if (lines.get(i).startsWith("   Patrol:")) from = i;
		}
		assertTrue(from > 0, "cops.yml has a Patrol block");
		int marker = from - 1;
		while (marker >= 0 && lines.get(marker).strip().startsWith("#") && !lines.get(marker).strip().equals("# 0.20.0")) {
			marker--;
		}
		assertTrue(marker >= 0 && lines.get(marker).strip().equals("# 0.20.0"),
		           "a '# 0.20.0' line sits directly above the appended blocks");
		for (int i = marker + 1; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!line.matches("\\s*[A-Z][A-Za-z0-9_]*:.*")) continue;
			assertTrue(i > 0 && lines.get(i - 1).strip().startsWith("#"), "no comment above: " + line.strip());
		}
	}

	private static Set<String> keysOf(NodeReader reader, String... path) {
		NodeReader current = reader;
		for (int i = 0; i < path.length - 1; i++) {
			MappingNode mapping = current.get(path[i]).asMapping().orNull();
			assertNotNull(mapping, "missing mapping " + path[i]);
			current = NodeReader.of(mapping, new ConfigReport());
		}
		MappingNode last = current.get(path[path.length - 1]).asMapping().orNull();
		assertNotNull(last, "missing mapping " + path[path.length - 1]);
		return last.entries().keySet();
	}

	private static CopConfigProvider copsProvider(ConfigReport report) throws IOException {
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), new StringReader(
				resource("copsncrooks/cops.yml")), report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = Objects.requireNonNull(Bundled020YamlTest.class.getClassLoader().getResourceAsStream(name),
		                                              name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
