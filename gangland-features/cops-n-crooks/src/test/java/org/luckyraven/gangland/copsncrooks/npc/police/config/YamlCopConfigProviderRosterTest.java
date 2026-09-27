package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.keystone.persistence.config.ConfigDocument;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.12 F4: {@code Cops.Rosters} and {@code Cops.Backup_Delay_Seconds} parse the same way the rest of {@code cops.yml}
 * does, mirroring {@code HeatConfigTest}'s use of a real {@link org.luckyraven.keystone.persistence.config.NodeReader}
 * over an in-memory document.
 */
@DisplayName("YamlCopConfigProvider (0.12 F4 rosters)")
class YamlCopConfigProviderRosterTest {

	private static final String MINIMAL_TIER = """
			Cops:
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			""";

	@Test
	@DisplayName("no Rosters/Backup_Delay_Seconds section falls back to empty rosters and the shipped delay list")
	void missingSection_yieldsDefaults() {
		YamlCopConfigProvider provider = parse(MINIMAL_TIER);

		assertTrue(provider.getRoster(1).isEmpty());
		assertEquals(YamlCopConfigProvider.DEFAULT_BACKUP_DELAY_SECONDS.get(0), provider.getBackupDelaySeconds(1));
		assertEquals(YamlCopConfigProvider.DEFAULT_BACKUP_DELAY_SECONDS.get(4), provider.getBackupDelaySeconds(5));
	}

	@Test
	@DisplayName("Rosters parses star -> { tier: count }, a star with no entry stays empty")
	void parse_readsRosters() {
		YamlCopConfigProvider provider = parse("""
				Cops:
				   Rosters:
				      1: { 1: 2 }
				      3: { 2: 2, 3: 2 }
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(Map.of(1, 2), provider.getRoster(1));
		assertEquals(Map.of(2, 2, 3, 2), provider.getRoster(3));
		assertTrue(provider.getRoster(2).isEmpty(), "star 2 has no entry, so it's unconfigured");
	}

	@Test
	@DisplayName("Backup_Delay_Seconds parses and clamps its index to the list, level <= 1 uses index 0")
	void parse_readsBackupDelay() {
		YamlCopConfigProvider provider = parse("""
				Cops:
				   Backup_Delay_Seconds: [9, 6, 3]
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertEquals(9, provider.getBackupDelaySeconds(0));
		assertEquals(9, provider.getBackupDelaySeconds(1));
		assertEquals(6, provider.getBackupDelaySeconds(2));
		assertEquals(3, provider.getBackupDelaySeconds(3));
		assertEquals(3, provider.getBackupDelaySeconds(9), "clamped to the last configured level");
	}

	@Test
	@DisplayName("a non-integer roster star or tier key is skipped, not fatal")
	void parse_skipsNonIntegerKeys() {
		YamlCopConfigProvider provider = parse("""
				Cops:
				   Rosters:
				      one: { 1: 2 }
				      2: { two: 2, 1: 1 }
				   Tiers:
				      1:
				         Display_Name: "&9Officer"
				         Health: 20.0
				         Damage: 2.0
				""");

		assertTrue(provider.getRoster(0).isEmpty());
		assertEquals(Map.of(1, 1), provider.getRoster(2), "the bad tier key is skipped, the good one kept");
	}

	private static YamlCopConfigProvider parse(String yaml) {
		return parse(new StringReader(yaml), new ConfigReport());
	}

	private static YamlCopConfigProvider parse(Reader yaml, ConfigReport report) {
		ConfigDocument document = new ConfigParser().parse(Path.of("cops.yml"), yaml, report);
		return new YamlCopConfigProvider(NodeReader.of(document.root(), report), report, null, null);
	}
}
