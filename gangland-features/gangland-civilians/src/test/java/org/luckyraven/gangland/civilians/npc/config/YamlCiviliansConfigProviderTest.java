package org.luckyraven.gangland.civilians.npc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase H11 (spec 4.10): the per-type {@code Faction}, {@code AI.Combat.Alert_Range} and
 * {@code AI.Combat.Search_Seconds} keys parse, default to the type id, 16 blocks and 20 seconds, and the shipped
 * {@code civilians.yml} declares them for its three combat types without tripping the unknown-key sweep.
 */
@DisplayName("YamlCiviliansConfigProvider - faction and squad keys")
class YamlCiviliansConfigProviderTest {

	@Test
	@DisplayName("Faction, AI.Combat.Alert_Range and AI.Combat.Search_Seconds parse")
	void squadKeys_parse() {
		CiviliansConfig config = parse(new StringReader("""
				Types:
				   gang_member:
				      Hostile: true
				      Faction: street
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				            Alert_Range: 24.0
				            Search_Seconds: 45
				"""), new ConfigReport());

		CivilianTypeConfig type = config.types().get("gang_member");
		assertEquals("street", type.faction());
		assertEquals(24.0, type.ai().alertRange());
		assertEquals(45, type.ai().searchSeconds());
	}

	@Test
	@DisplayName("omitted keys default to the type id, 16 blocks and 20 seconds")
	void squadKeys_default() {
		CiviliansConfig config = parse(new StringReader("""
				Types:
				   pedestrian:
				      Hostile: false
				   gang_member:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				"""), new ConfigReport());

		for (String typeId : List.of("pedestrian", "gang_member")) {
			CivilianTypeConfig type = config.types().get(typeId);
			assertEquals(typeId, type.faction(), typeId);
			assertEquals(16.0, type.ai().alertRange(), typeId);
			assertEquals(20, type.ai().searchSeconds(), typeId);
		}
	}

	@Test
	@DisplayName("the shipped civilians.yml declares the keys for its three combat types and the loader reads them")
	void shippedFile_declaresAndReadsSquadKeys() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("npc/civilians.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertEquals(3, occurrences(yaml, "\n      Faction: "));
		assertEquals(3, occurrences(yaml, "\n            Alert_Range: "));
		assertEquals(3, occurrences(yaml, "\n            Search_Seconds: "));

		ConfigReport report = new ConfigReport();
		parse(new StringReader(yaml), report);

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())
		                                                   && issue.path().matches(".*\\.(Faction|Alert_Range|Search_Seconds)")),
		           () -> "squad keys left unread: " + report.issues());
	}

	private static CiviliansConfig parse(Reader yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("civilians.yml"), yaml, report);
		return new YamlCiviliansConfigProvider(NodeReader.of(document.root(), report), report, true, 20, null)
				.getConfig();
	}

	private static int occurrences(String text, String needle) {
		return text.split(Pattern.quote(needle), -1).length - 1;
	}
}
