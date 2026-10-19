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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** 0.21.0 fnd-w2: {@code Wanted.Most_Wanted}. */
@DisplayName("MostWantedSettings - Wanted.Most_Wanted")
class MostWantedSettingsTest {

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String bundled() throws IOException {
		try (InputStream in = MostWantedSettingsTest.class.getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("the shipped wanted.yml reads as (true, 4, 10, 120) = DEFAULT with an empty ConfigReport")
	void bundledFile_equalsDefault() throws IOException {
		ConfigReport report = new ConfigReport();

		MostWantedSettings parsed = MostWantedSettings.parse(wantedRoot(bundled(), report));

		assertEquals(new MostWantedSettings(true, 4, 10, 120), parsed);
		assertEquals(MostWantedSettings.DEFAULT, parsed);
		assertFalse(report.issues().toString().contains("Most_Wanted"), report.issues().toString());
	}

	@Test
	@DisplayName("a null section and a section without the block are the default")
	void missing_isDefault() {
		assertEquals(MostWantedSettings.DEFAULT, MostWantedSettings.parse(null));
		assertEquals(MostWantedSettings.DEFAULT, MostWantedSettings.parse(
				wantedRoot("Wanted:\n   Heat:\n      Enable: true\n", new ConfigReport())));
	}

	@Test
	@DisplayName("each key overrides its default; a half-written block keeps the rest")
	void overrides_andPartial() {
		String full = "Wanted:\n   Most_Wanted:\n      Enable: false\n      Min_Stars: 6\n      Hold_Seconds: 3\n"
		              + "      Cooldown_Seconds: 9\n";
		assertEquals(new MostWantedSettings(false, 6, 3, 9),
		             MostWantedSettings.parse(wantedRoot(full, new ConfigReport())));

		assertEquals(new MostWantedSettings(true, 5, 10, 120), MostWantedSettings.parse(
				wantedRoot("Wanted:\n   Most_Wanted:\n      Min_Stars: 5\n", new ConfigReport())));
	}

	@Test
	@DisplayName("Min_Stars clamps to at least 1, the seconds to at least 0")
	void clamps() {
		String yaml = "Wanted:\n   Most_Wanted:\n      Min_Stars: 0\n      Hold_Seconds: -1\n      Cooldown_Seconds: -9\n";

		assertEquals(new MostWantedSettings(true, 1, 0, 0),
		             MostWantedSettings.parse(wantedRoot(yaml, new ConfigReport())));
	}
}
