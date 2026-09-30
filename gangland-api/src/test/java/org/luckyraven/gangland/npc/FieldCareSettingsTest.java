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

@DisplayName("FieldCareSettings.read - Field_Care block to record, defaults key by key, clamped and reported")
class FieldCareSettingsTest {

	private static NodeReader readerOf(Map<String, Object> yaml, ConfigReport report) {
		MappingNode mapping = ConfigNodes.mappingFromJava(yaml, null, "Field_Care");
		return NodeReader.of(mapping, report);
	}

	@Test
	@DisplayName("the defaults: hurt at half health, 0.7 limp, a 3 s channel healing half of max")
	void defaults() {
		FieldCareSettings d = FieldCareSettings.DEFAULT;

		assertTrue(d.enabled());
		assertEquals(0.5, d.healthFraction());
		assertEquals(0.7, d.limpSpeed());
		assertTrue(d.medicEnabled());
		assertEquals(24.0, d.medicRadius());
		assertEquals(2.5, d.healRange());
		assertEquals(60, d.channelTicks());
		assertEquals(0.5, d.healFraction());
	}

	@Test
	@DisplayName("a null node returns the defaults wholesale")
	void nullNode_returnsDefaults() {
		assertSame(FieldCareSettings.DEFAULT,
		           FieldCareSettings.read(null, new ConfigReport(), FieldCareSettings.DEFAULT));
	}

	@Test
	@DisplayName("a full block is read with no issues")
	void fullBlock() {
		ConfigReport report = new ConfigReport();

		FieldCareSettings s = FieldCareSettings.read(readerOf(Map.of(
				"Enabled", false, "Health_Fraction", 0.4, "Limp_Speed", 0.5, "Medic_Enabled", false,
				"Medic_Radius", 16.0, "Heal_Range", 3.0, "Channel_Ticks", 40, "Heal_Fraction", 0.25), report), report,
		                                             FieldCareSettings.DEFAULT);

		assertEquals(new FieldCareSettings(false, 0.4, 0.5, false, 16.0, 3.0, 40, 0.25), s);
		assertTrue(report.issues().isEmpty(), report.issues()::toString);
	}

	@Test
	@DisplayName("the block overrides only the keys it names")
	void overridesKeyByKey() {
		ConfigReport report = new ConfigReport();

		FieldCareSettings s = FieldCareSettings.read(readerOf(Map.of("Limp_Speed", 0.9), report), report,
		                                             FieldCareSettings.DEFAULT);

		assertEquals(0.9, s.limpSpeed());
		assertEquals(FieldCareSettings.DEFAULT.healthFraction(), s.healthFraction());
		assertEquals(FieldCareSettings.DEFAULT.channelTicks(), s.channelTicks());
		assertFalse(report.hasErrors());
	}

	@Test
	@DisplayName("out-of-range values are clamped and reported")
	void clampsAndReports() {
		ConfigReport report = new ConfigReport();

		FieldCareSettings s = FieldCareSettings.read(readerOf(Map.of(
				"Health_Fraction", 1.5, "Limp_Speed", 0.0, "Medic_Radius", 100.0, "Heal_Range", 0.2,
				"Channel_Ticks", 0, "Heal_Fraction", -1.0), report), report, FieldCareSettings.DEFAULT);

		assertEquals(1.0, s.healthFraction());
		assertEquals(0.1, s.limpSpeed());
		assertEquals(64.0, s.medicRadius());
		assertEquals(1.0, s.healRange());
		assertEquals(1, s.channelTicks());
		assertEquals(0.0, s.healFraction());
		assertEquals(6, report.issues().stream().filter(i -> "config.range".equals(i.code())).count());
	}

	@Test
	@DisplayName("isHurt: enabled and at or below the fraction; never with no max health")
	void isHurt() {
		FieldCareSettings s = FieldCareSettings.DEFAULT;

		assertTrue(s.isHurt(10.0, 20.0));
		assertTrue(s.isHurt(3.0, 20.0));
		assertFalse(s.isHurt(10.5, 20.0));
		assertFalse(s.isHurt(1.0, 0.0));
		assertFalse(new FieldCareSettings(false, 0.5, 0.7, true, 24, 2.5, 60, 0.5).isHurt(1.0, 20.0));
	}
}
