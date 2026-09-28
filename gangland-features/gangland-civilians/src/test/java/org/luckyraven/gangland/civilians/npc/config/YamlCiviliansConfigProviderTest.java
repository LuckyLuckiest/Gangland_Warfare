package org.luckyraven.gangland.civilians.npc.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.gangland.npc.TacticsConfig;
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

	@Test
	@DisplayName("the shipped civilians.yml declares Shouts and per-type Tactics/Melee, with no unknown-key issues")
	void shippedFile_declaresShoutsAndTacticsAndMelee() throws IOException {
		String yaml;
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("npc/civilians.yml"))) {
			yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}

		ConfigReport    report = new ConfigReport();
		CiviliansConfig config = parse(new StringReader(yaml), report);

		assertTrue(report.issues().stream().noneMatch(issue -> "config.unknown_key".equals(issue.code())),
		          () -> "unknown keys: " + report.issues());
		assertEquals(24.0, config.shouts().range());
		assertEquals(140.0, config.types().get("gang_member").ai().tactics().formationArc());
		assertEquals(0.20, config.types().get("gang_member").ai().melee().damageSpread());
		for (String type : List.of("gang_member", "turf_defender", "quartermaster")) {
			assertEquals(0.05, config.types().get(type).ai().fireRateMultiplier(), 1e-9, type);
			assertTrue(config.types().get(type).ai().retreat().enabled(), type);
		}
		assertTrue(config.shouts().cooldownFor("Fall_Back") > 0);
		assertTrue(config.shouts().cooldownFor("In_Cover") > 0);
	}

	@Test
	@DisplayName("AI.Combat.Tactics and AI.Combat.Melee parse, including Edge_Damage")
	void typeTactics_andMelee_parsed() {
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
				            Tactics:
				               Formation_Arc: 140.0
				               Strafe_Degrees: 12.0
				               Reposition_Ticks: 60
				               Moving_Aim_Error: 0.15
				            Melee:
				               Reach: 3.0
				               Approach: 2.0
				               Damage_Spread: 0.20
				               Edge_Damage: 0.7
				"""), new ConfigReport());

		CivilianAIBehaviorConfig ai = config.types().get("gang_member").ai();
		assertEquals(140.0, ai.tactics().formationArc());
		assertEquals(0.20, ai.melee().damageSpread());
		assertEquals(0.7, ai.melee().edgeDamage());
	}

	@Test
	@DisplayName("the melee cooldown is always Attack_Interval_Ticks, never a separate key")
	void meleeCooldown_isAttackIntervalTicks() {
		CiviliansConfig config = parse(new StringReader("""
				Types:
				   gang_member:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 33
				"""), new ConfigReport());

		assertEquals(33, config.types().get("gang_member").ai().melee().cooldownTicks());
	}

	@Test
	@DisplayName("the top-level Shouts block parses, with Range above Alert_Range")
	void shouts_parsed_rangeAboveAlertRange() {
		CiviliansConfig config = parse(new StringReader("""
				Shouts:
				   Range: 24.0
				   Target_Range: 32.0
				   Responder_Max: 0
				   Priority:
				      - Contact
				      - Rally
				Types:
				   gang_member:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				            Alert_Range: 16.0
				"""), new ConfigReport());

		assertEquals(24.0, config.shouts().range());
		assertTrue(config.shouts().range() > config.types().get("gang_member").ai().alertRange());
		assertEquals(0, config.shouts().responderMax());
		assertTrue(config.shouts().isPriority("Rally"));
	}

	@Test
	@DisplayName("an empty document defaults Shouts to CiviliansConfig.DEFAULT_SHOUTS")
	void missingShouts_defaults() {
		CiviliansConfig config = parse(new StringReader("Types: {}\n"), new ConfigReport());

		assertEquals(CiviliansConfig.DEFAULT_SHOUTS, config.shouts());
	}

	@Test
	@DisplayName("AI.Combat.Fire_Rate_Multiplier and AI.Combat.Retreat parse; omitted they default")
	void fireRateAndRetreat_parsed() {
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
				            Fire_Rate_Multiplier: 0.2
				            Retreat:
				               Enabled: false
				               Health_Fraction: 0.4
				               Radius: 8.0
				   turf_defender:
				      Hostile: true
				      AI:
				         Combat:
				            Enabled: true
				            Attack_Damage: 4.0
				            Attack_Range: 12.0
				            Attack_Interval_Ticks: 20
				"""), new ConfigReport());

		CivilianAIBehaviorConfig gang = config.types().get("gang_member").ai();
		assertEquals(0.2, gang.fireRateMultiplier());
		assertEquals(new RetreatSettings(false, 0.4, 8.0), gang.retreat());

		// no key: 1 / AI_Tick_Rate (20 here), the pre-1.13 cadence of one weapon tick per AI tick
		CivilianAIBehaviorConfig turf = config.types().get("turf_defender").ai();
		assertEquals(0.05, turf.fireRateMultiplier(), 1e-9);
		assertEquals(RetreatSettings.DEFAULT, turf.retreat());
	}

	@Test
	@DisplayName("the legacy 11-argument CivilianAIBehaviorConfig constructor defaults tactics and melee")
	void legacyCtor_defaults() {
		CivilianAIBehaviorConfig ai = new CivilianAIBehaviorConfig(false, 0, false, 0, true, 4.0, 12.0, 20,
		                                                           org.luckyraven.keystone.npc.NpcDifficulty.NORMAL,
		                                                           16.0, 20);

		assertEquals(TacticsConfig.DEFAULT, ai.tactics());
		assertEquals(3.0, ai.melee().reach());
		assertEquals(2.0, ai.melee().approach());
		assertEquals(20, ai.melee().cooldownTicks());
		assertEquals(0.15, ai.melee().damageSpread());
		assertEquals(0.7, ai.melee().edgeDamage());
		assertEquals(1.0, ai.fireRateMultiplier());
		assertEquals(RetreatSettings.DEFAULT, ai.retreat());
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
