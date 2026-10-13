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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority("Overwatch_Spotted"));
		assertTrue(CopConfigProvider.COP_RADIO_DEFAULTS.isPriority("Caller_911"));
	}

	@Test
	@DisplayName("bundled wanted.yml: Witnesses, Trespass and Heat.Crimes.Evading_Police 100; report is empty")
	void bundledWanted_newBlocks() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wantedRoot(resource("copsncrooks/wanted.yml"), report);

		assertEquals(new WitnessSettings(true, List.of("Car_Theft", "Assault_Civilian")), WitnessSettings.parse(wanted));
		assertEquals(new TrespassSettings(true, 60), TrespassSettings.parse(wanted));
		assertEquals(100, ChaseConfig.parse(wanted, report).heat().weightOf("Evading_Police"));
		assertEquals(ChaseConfig.DEFAULT, ChaseConfig.parse(wanted, report));
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
