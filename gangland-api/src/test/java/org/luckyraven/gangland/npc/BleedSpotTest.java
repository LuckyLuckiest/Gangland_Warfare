package org.luckyraven.gangland.npc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigNodes;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BleedSpot / BleedSettings - body-spot offsets relative to yaw, Field_Care.Hurt block")
class BleedSpotTest {

	private static final double EPS = 1e-9;

	private static void assertOffset(double x, double y, double z, double[] actual) {
		assertArrayEquals(new double[]{x, y, z}, actual, EPS);
	}

	@Test
	@DisplayName("facing +Z (yaw 0) the right arm is on -X and the chest is in front on +Z")
	void yawZero() {
		assertOffset(-0.4, 1.2, 0, BleedSpot.RIGHT_ARM.offset(0));
		assertOffset(0.4, 1.2, 0, BleedSpot.LEFT_ARM.offset(0));
		assertOffset(0, 1.25, 0.15, BleedSpot.CHEST.offset(0));
	}

	@Test
	@DisplayName("facing -X (yaw 90) the right arm moves to -Z and the chest forward to -X")
	void yawNinety() {
		assertOffset(0, 1.2, -0.4, BleedSpot.RIGHT_ARM.offset(90));
		assertOffset(-0.15, 1.25, 0, BleedSpot.CHEST.offset(90));
	}

	@Test
	@DisplayName("facing -Z (yaw 180) the sides swap and the head stays centred")
	void yawHalfTurn() {
		assertOffset(0.4, 1.2, 0, BleedSpot.RIGHT_ARM.offset(180));
		assertOffset(0, 1.7, 0, BleedSpot.HEAD.offset(180));
	}

	@Test
	@DisplayName("parse is case-insensitive, skips unknown names, and falls back to every spot")
	void parse() {
		assertEquals(List.of(BleedSpot.HEAD, BleedSpot.LEFT_LEG), BleedSpot.parse(List.of("head", "nope", "Left_Leg")));
		assertEquals(List.of(BleedSpot.values()), BleedSpot.parse(List.of("nope")));
	}

	@Test
	@DisplayName("the Hurt block is read key by key, Bleed_Count clamped, and defaults apply without it")
	void settings() {
		assertEquals(BleedSettings.DEFAULT, FieldCareSettings.DEFAULT.bleed());
		assertEquals("BLOCK_CRACK", BleedSettings.DEFAULT.particle());

		ConfigReport report = new ConfigReport();
		NodeReader node = NodeReader.of(ConfigNodes.mappingFromJava(
				Map.of("Hurt", Map.of("Bleed_Particle", "DUST", "Bleed_Count", 99, "Bleed_Spots", List.of("HEAD"))),
				null, "Field_Care"), report);
		BleedSettings bleed = FieldCareSettings.read(node, report, FieldCareSettings.DEFAULT).bleed();

		assertEquals(new BleedSettings("DUST", 50, List.of(BleedSpot.HEAD)), bleed);
		assertTrue(report.issues().stream().anyMatch(i -> i.toString().contains("Bleed_Count")));
	}
}
