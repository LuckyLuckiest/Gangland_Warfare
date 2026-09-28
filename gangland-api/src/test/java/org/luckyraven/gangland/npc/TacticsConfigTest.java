package org.luckyraven.gangland.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.npc.NpcEngagement;
import org.luckyraven.keystone.persistence.config.ConfigNodes;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TacticsConfig.read - YAML block to record, defaults key by key, LEGACY switch, clamped and reported")
class TacticsConfigTest {

	private static NodeReader readerOf(Map<String, Object> yaml, ConfigReport report) {
		MappingNode mapping = ConfigNodes.mappingFromJava(yaml, null, "Tactics");
		return NodeReader.of(mapping, report);
	}

	@Test
	@DisplayName("a null node returns defaults wholesale")
	void nullNode_returnsDefaults() {
		assertSame(TacticsConfig.DEFAULT, TacticsConfig.read(null, new ConfigReport(), TacticsConfig.DEFAULT));
	}

	@Test
	@DisplayName("a tier block overrides only the keys it names; the rest keep the passed-in defaults")
	void tierOverridesDefaultsKeyByKey() {
		TacticsConfig defaults = new TacticsConfig(new NpcEngagement(true, 15.0, 3000, 0.10), 270.0);
		ConfigReport  report   = new ConfigReport();
		NodeReader    reader   = readerOf(Map.of("Formation_Arc", 200.0), report);

		TacticsConfig tactics = TacticsConfig.read(reader, report, defaults);

		assertEquals(200.0, tactics.formationArc());
		assertEquals(defaults.engagement().strafeDegrees(), tactics.engagement().strafeDegrees());
		assertEquals(defaults.engagement().repositionMs(), tactics.engagement().repositionMs());
		assertEquals(defaults.engagement().movingAimError(), tactics.engagement().movingAimError());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("Enabled: false returns NpcEngagement.LEGACY, but the arc is still read")
	void enabledFalse_legacy() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Enabled", false, "Formation_Arc", 330.0), report);

		TacticsConfig tactics = TacticsConfig.read(reader, report, TacticsConfig.DEFAULT);

		assertSame(NpcEngagement.LEGACY, tactics.engagement());
		assertEquals(330.0, tactics.formationArc());
	}

	@Test
	@DisplayName("Reposition_Ticks is read as ticks and stored as milliseconds (x50)")
	void repositionTicksToMs() {
		ConfigReport report = new ConfigReport();
		NodeReader   reader = readerOf(Map.of("Reposition_Ticks", 80), report);

		TacticsConfig tactics = TacticsConfig.read(reader, report, TacticsConfig.DEFAULT);

		assertEquals(4000L, tactics.engagement().repositionMs());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("out-of-range values are clamped into their documented range and reported, not replaced by the default")
	void clampsAndReports() {
		ConfigReport report = new ConfigReport();
		NodeReader reader = readerOf(Map.of("Formation_Arc", 400.0, "Strafe_Degrees", 90.0, "Reposition_Ticks", 5,
		                                    "Moving_Aim_Error", 2.0), report);

		TacticsConfig tactics = TacticsConfig.read(reader, report, TacticsConfig.DEFAULT);

		assertEquals(360.0, tactics.formationArc());
		assertEquals(45.0, tactics.engagement().strafeDegrees());
		assertEquals(1000L, tactics.engagement().repositionMs()); // 20 ticks, the documented minimum
		assertEquals(1.0, tactics.engagement().movingAimError());
		assertTrue(report.issues().stream().filter(issue -> issue.code().equals("config.range")).count() >= 4);
	}
}
