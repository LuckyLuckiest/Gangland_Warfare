package org.luckyraven.gangland.npc.radio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigNodes;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RadioSettings.read - YAML block to record, defaults key by key, negatives clamped and reported")
class RadioSettingsReadTest {

	private static final RadioSettings DEFAULTS = new RadioSettings(true, 32.0, 64.0, 1500, 1000, 25, 2,
	                                                                 Map.of("Contact", 8000L), Set.of("Contact"),
	                                                                 "BLOCK_NOTE_BLOCK_HAT", 0.4f, 1.8f);

	private static NodeReader readerOf(Map<String, Object> yaml, ConfigReport report) {
		MappingNode mapping = ConfigNodes.mappingFromJava(yaml, null, "Radio");
		return NodeReader.of(mapping, report);
	}

	@Test
	@DisplayName("a null node returns defaults wholesale")
	void nullNode_returnsDefaults() {
		assertSame(DEFAULTS, RadioSettings.read(null, new ConfigReport(), DEFAULTS));
	}

	@Test
	@DisplayName("Squad_Gap_Ticks / Player_Gap_Ticks are read as ticks and stored as milliseconds (x50)")
	void ticksToMs() {
		ConfigReport report = new ConfigReport();
		NodeReader reader = readerOf(Map.of("Squad_Gap_Ticks", 30, "Player_Gap_Ticks", 20, "Ack_Delay_Ticks", 25),
		                             report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(1500L, settings.squadGapMs());
		assertEquals(1000L, settings.playerGapMs());
		assertEquals(25L, settings.ackDelayTicks());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("an absent key falls back to the matching default field")
	void absentKey_fallsBackToDefault() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Range", 40.0), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(40.0, settings.range());
		assertEquals(DEFAULTS.targetRange(), settings.targetRange());
		assertEquals(DEFAULTS.responderMax(), settings.responderMax());
		assertEquals(DEFAULTS.soundName(), settings.soundName());
		assertEquals(DEFAULTS.priority(), settings.priority());
		assertEquals(DEFAULTS.cooldownMs(), settings.cooldownMs());
	}

	@Test
	@DisplayName("a negative value is clamped to 0 and reported, not replaced by the default")
	void negativesClampedAndReported() {
		ConfigReport report = new ConfigReport();
		NodeReader reader = readerOf(Map.of("Range", -5.0, "Responder_Max", -1, "Squad_Gap_Ticks", -3), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(0.0, settings.range());
		assertEquals(0, settings.responderMax());
		assertEquals(0L, settings.squadGapMs());
		assertTrue(report.issues().stream().anyMatch(issue -> issue.code().equals("config.range")));
	}

	@Test
	@DisplayName("Ack_Delay_Ticks below Player_Gap_Ticks + 5 is clamped up and reported")
	void ackDelayBelowPlayerGap_clampedAndReported() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Player_Gap_Ticks", 20, "Ack_Delay_Ticks", 10), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(25L, settings.ackDelayTicks());
		assertTrue(report.issues().stream().anyMatch(issue -> issue.path().contains("Ack_Delay_Ticks")));
	}

	@Test
	@DisplayName("an explicit empty Priority list is honoured, not treated as absent")
	void explicitEmptyPriority_isHonoured() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Priority", List.of()), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertTrue(settings.priority().isEmpty());
	}

	@Test
	@DisplayName("Cooldown_Ticks entries are read as ticks and stored as milliseconds")
	void cooldownTicks_readAsMilliseconds() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Cooldown_Ticks", Map.of("Contact", 160, "Ack", 40)), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(8000L, settings.cooldownFor("Contact"));
		assertEquals(2000L, settings.cooldownFor("Ack"));
		assertEquals(0L, settings.cooldownFor("Never_Configured"));
	}

	@Test
	@DisplayName("the Sound sub-block reads Name/Volume/Pitch, falling back per-field")
	void soundBlock_readPerField() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Sound", Map.of("Volume", 0.9)), report);

		RadioSettings settings = RadioSettings.read(reader, report, DEFAULTS);

		assertEquals(DEFAULTS.soundName(), settings.soundName());
		assertEquals(0.9f, settings.volume());
		assertEquals(DEFAULTS.pitch(), settings.pitch());
	}
}
