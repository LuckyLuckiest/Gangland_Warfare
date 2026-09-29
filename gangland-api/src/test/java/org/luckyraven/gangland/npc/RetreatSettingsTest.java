package org.luckyraven.gangland.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigNodes;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RetreatSettings.read - Retreat block to record, defaults key by key, clamped and reported")
class RetreatSettingsTest {

	private static NodeReader readerOf(Map<String, Object> yaml, ConfigReport report) {
		MappingNode mapping = ConfigNodes.mappingFromJava(yaml, null, "Retreat");
		return NodeReader.of(mapping, report);
	}

	@Test
	@DisplayName("a null node returns the defaults wholesale")
	void nullNode_returnsDefaults() {
		assertSame(RetreatSettings.DEFAULT, RetreatSettings.read(null, new ConfigReport(), RetreatSettings.DEFAULT));
	}

	@Test
	@DisplayName("the block overrides only the keys it names")
	void overridesDefaultsKeyByKey() {
		ConfigReport report = new ConfigReport();

		RetreatSettings retreat = RetreatSettings.read(readerOf(Map.of("Health_Fraction", 0.5), report), report,
		                                               RetreatSettings.DEFAULT);

		assertEquals(0.5, retreat.healthFraction());
		assertEquals(RetreatSettings.DEFAULT.enabled(), retreat.enabled());
		assertEquals(RetreatSettings.DEFAULT.radius(), retreat.radius());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("Enabled: false is read")
	void enabledFalse() {
		ConfigReport report = new ConfigReport();

		RetreatSettings retreat = RetreatSettings.read(readerOf(Map.of("Enabled", false), report), report,
		                                               RetreatSettings.DEFAULT);

		assertFalse(retreat.enabled());
	}

	@Test
	@DisplayName("out-of-range values are clamped and reported")
	void clampsAndReports() {
		ConfigReport report = new ConfigReport();

		RetreatSettings retreat = RetreatSettings.read(
				readerOf(Map.of("Health_Fraction", 1.5, "Radius", -3.0), report), report, RetreatSettings.DEFAULT);

		assertEquals(1.0, retreat.healthFraction());
		assertEquals(2.0, retreat.radius());
		assertEquals(2, report.issues().stream().filter(i -> "config.range".equals(i.code())).count());
	}

	@Test
	@DisplayName("shouldRetreat: enabled and at or below the health fraction")
	void shouldRetreat() {
		RetreatSettings retreat = new RetreatSettings(true, 0.3, 12.0);

		assertTrue(retreat.shouldRetreat(5.0, 20.0));
		assertFalse(retreat.shouldRetreat(10.0, 20.0));
		assertFalse(new RetreatSettings(false, 0.3, 12.0).shouldRetreat(1.0, 20.0));
		assertFalse(retreat.shouldRetreat(1.0, 0.0));
	}
}
