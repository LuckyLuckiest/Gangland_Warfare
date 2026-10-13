package org.luckyraven.gangland.civilians.npc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.NpcRole;
import org.luckyraven.keystone.npc.NpcFanPlacement;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("YamlCiviliansConfigProvider - the optional AI.Combat.Roles overlay")
class CivilianRolesOverlayTest {

	@Test
	@DisplayName("a type without AI.Combat.Roles has no roles: default civilians are unchanged")
	void noRoles_isNone() {
		CiviliansConfig config = parse(new StringReader("""
				Types:
				   gang_member:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				"""), new ConfigReport());

		assertTrue(config.types().get("gang_member").ai().roles().isEmpty());
	}

	@Test
	@DisplayName("the shipped turf_defender carries Shield, Marksman and Commander; no other type has roles")
	void rolesParsed() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("npc/civilians.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		ConfigReport    report = new ConfigReport();
		CiviliansConfig config = parse(new StringReader(yaml), report);

		var roles = config.types().get("turf_defender").ai().roles();
		assertEquals(3, roles.size(), roles.keySet().toString());

		NpcRole shield = roles.get("Shield");
		assertNotNull(shield);
		assertEquals(NpcFanPlacement.CENTER, shield.placement(), "FRONT is the front of the fan: CENTER");
		assertEquals(0.5, shield.blockFraction(), 1e-9);
		assertEquals(90.0, shield.blockConeDegrees(), 1e-9);

		NpcRole marksman = roles.get("Marksman");
		assertNotNull(marksman);
		assertEquals(12.0, marksman.rangedMin());
		assertEquals(24.0, marksman.rangedMax());
		assertEquals(NpcFanPlacement.ANY, marksman.placement(), "BACK keeps the fan open: ANY");

		NpcRole commander = roles.get("Commander");
		assertNotNull(commander);
		assertEquals(10, commander.leaderPriority());
		assertTrue(commander.commander());

		config.types().forEach((id, type) -> {
			if (!id.equals("turf_defender")) assertTrue(type.ai().roles().isEmpty(), id);
		});
		assertTrue(report.issues().stream().noneMatch(issue -> issue.path().contains("Roles")),
		           () -> "role keys left unread or invalid: " + report.issues());
	}

	@Test
	@DisplayName("an unknown Placement is reported at the key and the role keeps ANY; FRONT and BACK are accepted")
	void unknownPlacement_warnsAndDefaults() {
		ConfigReport    report = new ConfigReport();
		CiviliansConfig config = parse(new StringReader("""
				Types:
				   guard:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				            Roles:
				               Odd:
				                  Placement: SIDEWAYS
				                  Block_Fraction: 0.25
				               Front:
				                  Placement: front
				"""), report);

		var roles = config.types().get("guard").ai().roles();
		assertEquals(NpcFanPlacement.ANY, roles.get("Odd").placement());
		assertEquals(0.25, roles.get("Odd").blockFraction(), 1e-9, "the other keys of the role still read");
		assertEquals(NpcFanPlacement.CENTER, roles.get("Front").placement());
		assertEquals(1, report.issues().stream()
		                      .filter(issue -> issue.path().endsWith("Odd.Placement")
		                                       && "config.enum".equals(issue.code())).count(),
		             report.issues().toString());
	}

	private static CiviliansConfig parse(Reader yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("civilians.yml"), yaml, report);
		return new YamlCiviliansConfigProvider(NodeReader.of(document.root(), report), report, true, 20, null)
				.getConfig();
	}
}
