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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.21.0 fnd-w2: {@code Wanted.Getaway}. */
@DisplayName("GetawaySettings - Wanted.Getaway")
class GetawaySettingsTest {

	private static NodeReader wantedRoot(String yaml, ConfigReport report) {
		NodeReader  file    = NodeReader.of(new ConfigParser().parse(Path.of("wanted.yml"), new StringReader(yaml), report)
		                                                      .root(), report);
		MappingNode section = file.get("Wanted").asMapping().orNull();
		return section == null ? null : NodeReader.of(section, report);
	}

	private static String bundled() throws IOException {
		try (InputStream in = GetawaySettingsTest.class.getClassLoader().getResourceAsStream("copsncrooks/wanted.yml")) {
			assertNotNull(in, "copsncrooks/wanted.yml must ship in the module jar");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	@DisplayName("DEFAULT is true / [0, 25, 75, 175, 350] / 600 / 1000 with the C-12 exclusions")
	void default_isTheAgreedValues() {
		GetawaySettings d = GetawaySettings.DEFAULT;

		assertTrue(d.enabled());
		assertEquals(List.of(0, 25, 75, 175, 350), d.experience());
		assertEquals(600, d.cooldownSeconds());
		assertEquals(1000, d.dailyCap());
		assertEquals(Set.of("trespass_restricted", "evading_police", "brandish_near_cop"), d.excludeCrimes());
	}

	@Test
	@DisplayName("the shipped wanted.yml reads as DEFAULT with an empty ConfigReport")
	void bundledFile_equalsDefault() throws IOException {
		ConfigReport report = new ConfigReport();

		GetawaySettings parsed = GetawaySettings.parse(wantedRoot(bundled(), report));

		assertEquals(GetawaySettings.DEFAULT, parsed);
		assertFalse(report.issues().toString().contains("Getaway"), report.issues().toString());
	}

	@Test
	@DisplayName("a null section, a section without the block, and a partial block keep the defaults")
	void missingAndPartial_keepDefaults() {
		assertEquals(GetawaySettings.DEFAULT, GetawaySettings.parse(null));
		assertEquals(GetawaySettings.DEFAULT,
		             GetawaySettings.parse(wantedRoot("Wanted:\n   Heat:\n      Enable: true\n", new ConfigReport())));

		GetawaySettings partial = GetawaySettings.parse(
				wantedRoot("Wanted:\n   Getaway:\n      Daily_Cap: 50\n", new ConfigReport()));

		assertEquals(new GetawaySettings(true, GetawaySettings.DEFAULT.experience(), 600, 50,
		                                 GetawaySettings.DEFAULT.excludeCrimes()), partial);
	}

	@Test
	@DisplayName("each key overrides its default, including Enable false")
	void overrides_areRead() {
		String yaml = "Wanted:\n"
		              + "   Getaway:\n"
		              + "      Enable: false\n"
		              + "      Experience:\n"
		              + "         - 1\n"
		              + "         - 2\n"
		              + "      Cooldown_Seconds: 30\n"
		              + "      Daily_Cap: 0\n";

		GetawaySettings parsed = GetawaySettings.parse(wantedRoot(yaml, new ConfigReport()));

		assertFalse(parsed.enabled());
		assertEquals(List.of(1, 2), parsed.experience());
		assertEquals(30, parsed.cooldownSeconds());
		assertEquals(0, parsed.dailyCap());
	}

	@Test
	@DisplayName("negatives clamp to 0; an empty experience list is the default list")
	void negativesClamp_badListDefaults() {
		String negatives = "Wanted:\n   Getaway:\n      Cooldown_Seconds: -5\n      Daily_Cap: -1\n"
		                   + "      Experience:\n         - -10\n         - 20\n";
		GetawaySettings clamped = GetawaySettings.parse(wantedRoot(negatives, new ConfigReport()));

		assertEquals(0, clamped.cooldownSeconds());
		assertEquals(0, clamped.dailyCap());
		assertEquals(0, clamped.experienceFor(1));
		assertEquals(20, clamped.experienceFor(2));

		GetawaySettings empty = GetawaySettings.parse(
				wantedRoot("Wanted:\n   Getaway:\n      Experience: []\n", new ConfigReport()));
		assertEquals(GetawaySettings.DEFAULT.experience(), empty.experience());
	}

	@Test
	@DisplayName("experienceFor: below one star 0, entry peak-1, the last entry above the list")
	void experienceFor_indexing() {
		GetawaySettings d = GetawaySettings.DEFAULT;

		assertEquals(0, d.experienceFor(-1));
		assertEquals(0, d.experienceFor(0));
		assertEquals(0, d.experienceFor(1));
		assertEquals(25, d.experienceFor(2));
		assertEquals(75, d.experienceFor(3));
		assertEquals(175, d.experienceFor(4));
		assertEquals(350, d.experienceFor(5));
		assertEquals(350, d.experienceFor(7));
	}

	@Test
	@DisplayName("C-37: a missing Exclude_Crimes gives the three ids, a key with no value is empty, ids match in any case")
	void excludeCrimes_missingEmptyAndListed() {
		GetawaySettings missing = GetawaySettings.parse(
				wantedRoot("Wanted:\n   Getaway:\n      Daily_Cap: 5\n", new ConfigReport()));
		assertEquals(GetawaySettings.DEFAULT.excludeCrimes(), missing.excludeCrimes());

		GetawaySettings nothing = GetawaySettings.parse(
				wantedRoot("Wanted:\n   Getaway:\n      Exclude_Crimes:\n", new ConfigReport()));
		assertTrue(nothing.excludeCrimes().isEmpty());

		GetawaySettings listed = GetawaySettings.parse(wantedRoot(
				"Wanted:\n   Getaway:\n      Exclude_Crimes:\n         - Evading_Police\n         - Some_Future_Crime\n",
				new ConfigReport()));
		assertTrue(listed.excludes("evading_police"));
		assertTrue(listed.excludes("EVADING_POLICE"));
		assertFalse(listed.excludes("Kill_Cop"));
		assertFalse(listed.excludes(null));
	}
}
