package org.luckyraven.gangland.copsncrooks.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.CrewHeatSettings;
import org.luckyraven.gangland.crime.Crimes;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** cnc-integ pre-gate on the merged 0.20 + 0.21 tree: C-45 (ChaseConfig known keys) and T-393 (Crew_Heat defaults). */
@DisplayName("cnc-integ: merged wanted.yml known keys and Crew_Heat defaults")
class IntegrationGate021ConfigTest {

	private static boolean unknownKey(ConfigReport report, String key) {
		return report.issues().stream().anyMatch(i -> "config.unknown_key".equals(i.code()) && i.path().endsWith(key));
	}

	@Test
	@DisplayName("C-45: ChaseConfig reads Bag + Getaway + Most_Wanted + Crew_Heat + Pursuit_Report: no unknown key in bundled wanted.yml")
	void chaseConfigKnownKeysAreTheUnion() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wantedRoot(resource("copsncrooks/wanted.yml"), report);

		ChaseConfig.parse(wanted, report);

		for (String key : new String[]{"Bag", "Getaway", "Most_Wanted", "Crew_Heat", "Pursuit_Report"}) {
			assertTrue(wanted.has(key), "bundled wanted.yml lacks " + key);
		}
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())), report.issues()::toString);
	}

	@Test
	@DisplayName("C-45 sensitivity: a reader that never touches Bag reports Bag as an unknown key")
	void skippingTheBagReadFlagsBag() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wantedRoot(resource("copsncrooks/wanted.yml"), report);

		for (String key : new String[]{"Kill_Combo", "Post_Escape", "Crew_Heat", "Pursuit_Report", "Getaway", "Most_Wanted",
		                               "Witnesses", "Trespass", "Heat", "Evasion", "Hud", "Charge_Sheet", "Bribe_Stars"}) {
			wanted.get(key);
		}

		assertTrue(unknownKey(report, "Bag"), report.issues()::toString);
	}

	@Test
	@DisplayName("T-393: every default Crew_Heat Exclude_Crimes id is a real crime id of the merged tree")
	void defaultExcludedIdsAreRealCrimeIds() throws IllegalAccessException {
		Set<String> real = new HashSet<>();
		for (Field f : Crimes.class.getFields()) {
			if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
				real.add(((String) f.get(null)).toLowerCase(Locale.ROOT));
			}
		}
		for (String id : CrewHeatSettings.DEFAULT.excludeCrimes()) {
			assertTrue(real.contains(id), "default Exclude_Crimes id is not a crime: " + id);
		}
		assertEquals(Set.of("trespass_restricted", "evading_police", "brandish_near_cop"),
		             CrewHeatSettings.DEFAULT.excludeCrimes());
	}

	@Test
	@DisplayName("T-393: the bundled wanted.yml Exclude_Crimes list equals the code default (both name real ids)")
	void bundledExcludeListEqualsCodeDefault() throws IOException {
		ConfigReport report = new ConfigReport();
		NodeReader   wanted = wantedRoot(resource("copsncrooks/wanted.yml"), report);

		assertEquals(CrewHeatSettings.DEFAULT, CrewHeatSettings.parse(wanted));
	}

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return NodeReader.of(Objects.requireNonNull(section), report);
	}

	private static String resource(String name) throws IOException {
		try (InputStream in = Objects.requireNonNull(
				IntegrationGate021ConfigTest.class.getClassLoader().getResourceAsStream(name), name)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
