package org.luckyraven.gangland.civilians.witness;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.config.CiviliansConfig;
import org.luckyraven.gangland.civilians.npc.config.YamlCiviliansConfigProvider;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("WitnessRules - the Witness block of npc/civilians.yml")
class WitnessRulesTest {

	private static final WitnessRules SHIPPED = new WitnessRules(true, 24.0, 8, true, 16.0, 12.0, true, 10);

	@Test
	@DisplayName("DEFAULT is the shipped block: on, 24 blocks, 8 s, glow, 16 blocks, 12 degrees, assault on, 10 s")
	void defaultIsShipped() {
		assertEquals(SHIPPED, WitnessRules.DEFAULT);
	}

	@Test
	@DisplayName("no Witness block, or no reader at all, is the default")
	void missingBlockIsDefault() {
		assertEquals(SHIPPED, WitnessRules.parse(null));
		assertEquals(SHIPPED, parseRules("Default_Entities:\n   Civilian:\n      - \"VILLAGER\"\n", new ConfigReport()));
	}

	@Test
	@DisplayName("every key of the block is read")
	void valuesRead() {
		WitnessRules rules = parseRules("""
				Witness:
				   Enable: false
				   Range: 12.5
				   Call_Seconds: 3
				   Caller_Glow: false
				   Scare_Range: 8.0
				   Scare_Angle: 20.0
				   Report_Assault: false
				   Assault_Repeat_Seconds: 30
				""", new ConfigReport());

		assertEquals(new WitnessRules(false, 12.5, 3, false, 8.0, 20.0, false, 30), rules);
	}

	@Test
	@DisplayName("0.21.1: View_Angle, Notice_*, Phone_Item, Intimidate_Range, Scared_Cooldown_Seconds and Loud_* are read")
	void newKeysRead() {
		WitnessRules rules = parseRules("""
				Witness:
				   View_Angle: 360.0
				   Notice_Seconds: 2
				   Notice_Particle: "FLAME"
				   Phone_Item: "PAPER"
				   Intimidate_Range: 6.0
				   Scared_Cooldown_Seconds: 30
				   Loud_Crimes:
				      - Car_Theft
				   Loud_Shot_Seconds: 0
				   Loud_Weapon_Types:
				      - GUN
				""", new ConfigReport());

		assertEquals(360.0, rules.viewAngle());
		assertEquals(2, rules.noticeSeconds());
		assertEquals("FLAME", rules.noticeParticle());
		assertEquals("PAPER", rules.phoneItem());
		assertEquals(6.0, rules.intimidateRange());
		assertEquals(30, rules.scaredCooldownSeconds());
		assertEquals(java.util.List.of("Car_Theft"), rules.loudCrimes());
		assertEquals(0, rules.loudShotSeconds());
		assertEquals(java.util.List.of("GUN"), rules.loudWeaponTypes());
	}

	@Test
	@DisplayName("0.21.1 defaults: 180 degree cone, 1 s notice, ANGRY_VILLAGER, CLOCK, 4 blocks, 60 s, no loud crime, 3 s shot from a gun or a throwable")
	void newKeyDefaults() {
		WitnessRules d = WitnessRules.parse(null);

		assertEquals(180.0, d.viewAngle());
		assertEquals(1, d.noticeSeconds());
		assertEquals("ANGRY_VILLAGER", d.noticeParticle());
		assertEquals("CLOCK", d.phoneItem());
		assertEquals(4.0, d.intimidateRange());
		assertEquals(60, d.scaredCooldownSeconds());
		assertTrue(d.loudCrimes().isEmpty());
		assertEquals(3, d.loudShotSeconds());
		assertEquals(java.util.List.of("GUN", "THROWABLE"), d.loudWeaponTypes());
	}

	@Test
	@DisplayName("Phone_Item and Notice_Particle \"\" mean none; Loud_Crimes [] means no loud crime")
	void emptyValuesMeanNone() {
		WitnessRules rules = parseRules("""
				Witness:
				   Notice_Particle: ""
				   Phone_Item: ""
				   Loud_Crimes: []
				""", new ConfigReport());

		assertEquals("", rules.noticeParticle());
		assertEquals("", rules.phoneItem());
		assertTrue(rules.loudCrimes().isEmpty());
	}

	@Test
	@DisplayName("a partly filled block keeps the default for the missing keys")
	void partialBlockKeepsDefaults() {
		WitnessRules rules = parseRules("""
				Witness:
				   Call_Seconds: 5
				""", new ConfigReport());

		assertEquals(new WitnessRules(true, 24.0, 5, true, 16.0, 12.0, true, 10), rules);
	}

	@Test
	@DisplayName("a bad value keeps the default and is reported")
	void badValueWarns() {
		ConfigReport report = new ConfigReport();

		WitnessRules rules = parseRules("""
				Witness:
				   Range: far
				   Call_Seconds: soon
				""", report);

		assertEquals(SHIPPED, rules);
		assertFalse(report.issues().isEmpty());
	}

	@Test
	@DisplayName("the provider carries the block in CiviliansConfig.witness() and never flags it as an unknown key")
	void providerExposesWitness() {
		ConfigReport report = new ConfigReport();

		CiviliansConfig config = provider("""
				Witness:
				   Call_Seconds: 4
				""", report);

		assertEquals(4, config.witness().callSeconds());
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           () -> report.issues().toString());
	}

	@Test
	@DisplayName("the shipped civilians.yml declares the Witness block with the default values, block style")
	void shippedFileDeclaresWitness() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(
				getClass().getClassLoader().getResourceAsStream("npc/civilians.yml"))) {
			// normalise CRLF: a Windows autocrlf checkout must not break the "\nWitness:\n" anchors
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
		}
		assertTrue(yaml.contains("\nWitness:\n"));
		// the keys must sit INSIDE the Witness block (up to the next top-level line), not merely somewhere in the file
		String after = yaml.substring(yaml.indexOf("\nWitness:\n") + "\nWitness:\n".length());
		java.util.regex.Matcher next = java.util.regex.Pattern.compile("(?m)^\\S").matcher(after);
		String block = next.find() ? after.substring(0, next.start()) : after;
		String[][] expected = {{"Enable", "true"}, {"Range", "24.0"}, {"Call_Seconds", "8"}, {"Caller_Glow", "true"},
		                       {"Scare_Range", "16.0"}, {"Scare_Angle", "12.0"}, {"Report_Assault", "true"},
		                       {"Assault_Repeat_Seconds", "10"}, {"View_Angle", "180.0"}, {"Notice_Seconds", "1"},
		                       {"Notice_Particle", "\"ANGRY_VILLAGER\""}, {"Phone_Item", "\"CLOCK\""},
		                       {"Intimidate_Range", "4.0"}, {"Scared_Cooldown_Seconds", "60"},
		                       {"Loud_Shot_Seconds", "3"}};
		for (String[] pair : expected) {
			assertTrue(java.util.regex.Pattern.compile("(?m)^   " + pair[0] + ": " + java.util.regex.Pattern.quote(pair[1])
			                                            + "\\s*(#.*)?$").matcher(block).find(),
			           () -> pair[0] + " = " + pair[1] + " inside the Witness block:\n" + block);
		}
		assertFalse(yaml.contains("Witness: {"));

		ConfigReport    report = new ConfigReport();
		CiviliansConfig config = provider(yaml, report);

		assertEquals(SHIPPED, config.witness());
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           () -> report.issues().toString());
	}

	// ── helpers ──────────────────────────────────────────────────────────────

	private static WitnessRules parseRules(String yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("civilians.yml"), reader(yaml), report);
		return WitnessRules.parse(NodeReader.of(document.root(), report));
	}

	private static CiviliansConfig provider(String yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("civilians.yml"), reader(yaml), report);
		return new YamlCiviliansConfigProvider(NodeReader.of(document.root(), report), report, true, 20, null)
				.getConfig();
	}

	private static Reader reader(String yaml) {
		return new StringReader(yaml);
	}
}
