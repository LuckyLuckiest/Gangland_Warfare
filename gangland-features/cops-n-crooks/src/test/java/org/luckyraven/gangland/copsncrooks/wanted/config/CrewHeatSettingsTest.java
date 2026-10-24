package org.luckyraven.gangland.copsncrooks.wanted.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.MappingNode;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.21.0 fnd-origin: {@code Wanted.Crew_Heat}. The shipped Exclude_Crimes list is the council ruling C-12 (the three
 * 0.20.0 crimes that make no sense to share), lower-cased; ids this build does not know are ignored silently.
 */
@DisplayName("CrewHeatSettings - Wanted.Crew_Heat")
class CrewHeatSettingsTest {

	private static final Set<String> SHIPPED_EXCLUDES = Set.of("trespass_restricted", "evading_police",
	                                                           "brandish_near_cop");

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String bundled() throws IOException {
		try (InputStream in = CrewHeatSettingsTest.class.getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("the shipped wanted.yml reads as 16 blocks, enabled, the three C-12 crimes excluded, and equals DEFAULT")
	void bundledFile_equalsDefault() throws IOException {
		ConfigReport report = new ConfigReport();

		CrewHeatSettings parsed = CrewHeatSettings.parse(wantedRoot(bundled(), report));

		assertEquals(new CrewHeatSettings(true, 16.0, SHIPPED_EXCLUDES), parsed);
		assertEquals(CrewHeatSettings.DEFAULT, parsed);
		assertFalse(report.issues().toString().contains("Crew_Heat"), report.issues().toString());
	}

	@Test
	@DisplayName("a missing Wanted section, and a Wanted section without the block, are the default")
	void missing_isDefault() {
		assertEquals(new CrewHeatSettings(true, 16.0, SHIPPED_EXCLUDES), CrewHeatSettings.parse(null));
		assertEquals(CrewHeatSettings.DEFAULT,
		             CrewHeatSettings.parse(wantedRoot("Wanted:\n   Heat:\n      Enable: true\n", new ConfigReport())));
	}

	@Test
	@DisplayName("each key overrides its default; crime ids are lower-cased")
	void overrides_areRead() {
		String yaml = "Wanted:\n"
		              + "   Crew_Heat:\n"
		              + "      Enable: false\n"
		              + "      Radius: 8.5\n"
		              + "      Exclude_Crimes:\n"
		              + "         - Kill_Cop\n"
		              + "         - TRESPASS_RESTRICTED\n";

		CrewHeatSettings parsed = CrewHeatSettings.parse(wantedRoot(yaml, new ConfigReport()));

		assertEquals(new CrewHeatSettings(false, 8.5, Set.of("kill_cop", "trespass_restricted")), parsed);
	}

	@Test
	@DisplayName("an explicitly empty Exclude_Crimes shares every crime")
	void emptyList_excludesNothing() {
		String yaml = "Wanted:\n   Crew_Heat:\n      Exclude_Crimes: []\n";

		assertTrue(CrewHeatSettings.parse(wantedRoot(yaml, new ConfigReport())).excludeCrimes().isEmpty());
	}

	@Test
	@DisplayName("an Exclude_Crimes key set to nothing reads as an empty set, and blank entries are dropped")
	void nullList_andBlankEntries() {
		String nothing = "Wanted:\n   Crew_Heat:\n      Exclude_Crimes:\n";
		String blanks  = "Wanted:\n   Crew_Heat:\n      Exclude_Crimes:\n         - \"\"\n         - Kill_Cop\n";

		assertTrue(CrewHeatSettings.parse(wantedRoot(nothing, new ConfigReport())).excludeCrimes().isEmpty());
		assertEquals(Set.of("kill_cop"), CrewHeatSettings.parse(wantedRoot(blanks, new ConfigReport())).excludeCrimes());
	}

	@Test
	@DisplayName("ids this build does not know are kept and raise no config issue")
	void unknownIds_raiseNothing() {
		String       yaml   = "Wanted:\n   Crew_Heat:\n      Exclude_Crimes:\n         - Some_Future_Crime\n";
		ConfigReport report = new ConfigReport();

		CrewHeatSettings parsed = CrewHeatSettings.parse(wantedRoot(yaml, report));

		assertEquals(Set.of("some_future_crime"), parsed.excludeCrimes());
		assertTrue(report.isEmpty(), report.issues().toString());
	}

	@Test
	@DisplayName("a negative radius is clamped to 0")
	void negativeRadius_isClamped() {
		String yaml = "Wanted:\n   Crew_Heat:\n      Radius: -5.0\n";

		assertEquals(0.0, CrewHeatSettings.parse(wantedRoot(yaml, new ConfigReport())).radius());
	}
}
