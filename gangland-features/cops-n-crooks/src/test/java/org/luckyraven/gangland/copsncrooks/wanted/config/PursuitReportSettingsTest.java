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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.21.0 fnd-origin: {@code Wanted.Pursuit_Report}. */
@DisplayName("PursuitReportSettings - Wanted.Pursuit_Report")
class PursuitReportSettingsTest {

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String bundled() throws IOException {
		try (InputStream in = PursuitReportSettingsTest.class.getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final PursuitReportSettings SHIPPED = new PursuitReportSettings(true, 1, 2500.0, 10.0, true, 4, 120,
	                                                                                300);

	@Test
	@DisplayName("the shipped wanted.yml reads as 1 / 2500 / 10, news 4 / 120 / 300 enabled, and equals DEFAULT")
	void bundledFile_equalsDefault() throws IOException {
		ConfigReport report = new ConfigReport();

		PursuitReportSettings parsed = PursuitReportSettings.parse(wantedRoot(bundled(), report));

		assertEquals(SHIPPED, parsed);
		assertEquals(PursuitReportSettings.DEFAULT, parsed);
		assertFalse(report.issues().toString().contains("Pursuit_Report"), report.issues().toString());
	}

	@Test
	@DisplayName("a missing Wanted section, and a Wanted section without the block, are the default")
	void missing_isDefault() {
		assertEquals(SHIPPED, PursuitReportSettings.parse(null));
		assertEquals(PursuitReportSettings.DEFAULT,
		             PursuitReportSettings.parse(wantedRoot("Wanted:\n   Heat:\n      Enable: true\n",
		                                                    new ConfigReport())));
	}

	@Test
	@DisplayName("each key overrides its default")
	void overrides_areRead() {
		String yaml = "Wanted:\n"
		              + "   Pursuit_Report:\n"
		              + "      Enabled: false\n"
		              + "      Min_Peak_Stars: 3\n"
		              + "      City_Cost:\n"
		              + "         Per_Cop_Down: 100.0\n"
		              + "         Per_Heat: 2.5\n"
		              + "      Breaking_News:\n"
		              + "         Enabled: false\n"
		              + "         Min_Stars: 5\n"
		              + "         Min_Seconds: 60\n"
		              + "         Cooldown_Seconds: 30\n";

		assertEquals(new PursuitReportSettings(false, 3, 100.0, 2.5, false, 5, 60, 30),
		             PursuitReportSettings.parse(wantedRoot(yaml, new ConfigReport())));
	}

	@Test
	@DisplayName("a half-written block keeps the default for every key it leaves out")
	void partialBlock_keepsDefaults() {
		String yaml = "Wanted:\n   Pursuit_Report:\n      Breaking_News:\n         Min_Stars: 5\n";

		assertEquals(new PursuitReportSettings(true, 1, 2500.0, 10.0, true, 5, 120, 300),
		             PursuitReportSettings.parse(wantedRoot(yaml, new ConfigReport())));
	}

	@Test
	@DisplayName("negative numbers are clamped to 0")
	void negatives_areClamped() {
		String yaml = "Wanted:\n"
		              + "   Pursuit_Report:\n"
		              + "      Min_Peak_Stars: -1\n"
		              + "      City_Cost:\n"
		              + "         Per_Cop_Down: -5.0\n"
		              + "         Per_Heat: -1.0\n"
		              + "      Breaking_News:\n"
		              + "         Min_Stars: -2\n"
		              + "         Min_Seconds: -3\n"
		              + "         Cooldown_Seconds: -4\n";

		assertEquals(new PursuitReportSettings(true, 0, 0.0, 0.0, true, 0, 0, 0),
		             PursuitReportSettings.parse(wantedRoot(yaml, new ConfigReport())));
	}
}
