package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterisation of {@link YamlCopConfigProvider} for a cops.yml with no {@code Cops} section at all: the reader the
 * section lookups start from is null, so every block (Melee, Radio, Patrol, Disguise, ...) is its code default and the
 * constructor still finishes. Pins the null-reader branch of the section lookups.
 */
@DisplayName("YamlCopConfigProvider - no Cops section")
class YamlCopConfigProviderNoCopsTest {

	private static final String EMPTY_COPS = """
			Cops:
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			""";

	@Test
	@DisplayName("every section getter returns what a Cops section without that block returns")
	void noCopsSection_everySectionIsItsCodeDefault() {
		ConfigReport    report  = new ConfigReport();
		CopConfigProvider none  = parse("Other: 1\n", report);
		CopConfigProvider empty = parse(EMPTY_COPS, new ConfigReport());

		assertEquals(new PatrolSettings(true, 2, 64, 96, 30, 24, 3, 6, 1, true), none.getPatrolSettings());
		assertEquals(new WarningSettings(true, 12.0, 5, 30), none.getWarningSettings());
		assertEquals(new PullOverSettings(true, 24.0, 12.0, 6, 1.0, 2, 150.0, 120), none.getPullOverSettings());
		assertEquals(new TrackerSettings(true, 2, 4, 48.0, 6, 150), none.getTrackerSettings());
		assertEquals(new ScannerSettings(true, 160.0, "CLOCK", "&9Police Scanner", List.of("&7Hear the police radio")),
		             none.getScannerSettings());
		assertEquals(new SirenSettings(true, 5, 24.0, "BLOCK_NOTE_BLOCK_BELL", 3.0f, 1.0f), none.getSirenSettings());
		assertEquals(new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5), none.getSightSettings());
		assertEquals(new OverwatchSettings(true, 4, "Marksman", 10.0, 20.0, 8, 4, 48.0, 3, 20, 120),
		             none.getOverwatchSettings());
		assertEquals(new ScannerMapSettings(true, 1, 10, true, "&9Scanner Map", List.of("&7Cops, search zone, hideouts")),
		             none.getScannerMapSettings());
		assertEquals(DisguiseSettings.DEFAULT, none.getDisguiseSettings());

		assertEquals(empty.getMeleeProfile(), none.getMeleeProfile());
		assertEquals(empty.getRadioSettings(), none.getRadioSettings());
		assertEquals(empty.getBackupSettings(), none.getBackupSettings());
		assertEquals(empty.getCheckInSettings(), none.getCheckInSettings());
		assertEquals(empty.getRegroupSettings(), none.getRegroupSettings());
		assertEquals(empty.getShotNoiseSettings(), none.getShotNoiseSettings());
		assertEquals(empty.getRetreatSettings(), none.getRetreatSettings());
		assertEquals(empty.getNames(), none.getNames());
		assertEquals(empty.getStuckSettings(), none.getStuckSettings());
		assertEquals(empty.getFieldCareSettings(), none.getFieldCareSettings());
		assertEquals(empty.getDispatchSettings(), none.getDispatchSettings());
		assertEquals(empty.getBreatherSettings(), none.getBreatherSettings());
		assertEquals(empty.getHandoffSettings(), none.getHandoffSettings());
		assertEquals(empty.getStandoffSettings(), none.getStandoffSettings());
		assertEquals(empty.getBreachSettings(), none.getBreachSettings());
		assertEquals(empty.getPerimeterSettings(), none.getPerimeterSettings());
		assertEquals(empty.getReturnWatchedCapSeconds(), none.getReturnWatchedCapSeconds());
		assertEquals(empty.isLookoutEnabled(), none.isLookoutEnabled());
		assertTrue(report.hasErrors(), "the missing required Cops section is still reported");
	}

	private static CopConfigProvider parse(String yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), new StringReader(yaml), report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
