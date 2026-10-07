package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.Color;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.RetreatSettings;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.persistence.config.ConfigParser;
import org.luckyraven.keystone.persistence.config.ConfigReport;
import org.luckyraven.keystone.persistence.config.NodeReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("copsncrooks/cop_roles.yml - the role catalogue, its gear and weapons per tier, and the squad compositions")
class CopRolesFileTest {

	private static final String TIERS = """
			Cops:
			   Tiers:
			      1:
			         Display_Name: "&9Officer"
			         Health: 20.0
			         Damage: 2.0
			      3:
			         Display_Name: "&5Lieutenant"
			         Health: 30.0
			         Damage: 4.0
			      4:
			         Display_Name: "&cSWAT"
			         Health: 40.0
			         Damage: 5.0
			      5:
			         Display_Name: "&4Military"
			         Health: 60.0
			         Damage: 7.0
			         Can_Use_Weapons: true
			""";

	/** TIERS plus tier 2: the shipped Squad_Composition names tiers 2-5, so every tier id must be declared. */
	private static final String ALL_TIERS = TIERS.replace("      3:", """
			      2:
			         Display_Name: "&3Sergeant"
			         Health: 25.0
			         Damage: 3.0
			""" + "      3:");

	// ── absent file / shipped file ─────────────────────────────────────────────

	@Test
	@DisplayName("no cop_roles.yml: the built-in catalogue (displays, Marksman far band) and compositions")
	void absentFile_builtInCatalogue() {
		CopConfigProvider provider = provider(TIERS, null, new ConfigReport());

		assertEquals(List.of("Pointman", "Assault"), names(provider.getSquadComposition(1)));
		assertEquals(List.of("Commander", "Pointman", "Defender", "Marksman", "Medic", "Assault"),
		             names(provider.getSquadComposition(5)));

		Map<String, CopRole> roles = byName(provider.getSquadComposition(5));
		assertEquals("&c✚ Medic", roles.get("Medic").display());
		assertEquals("&6★ Commander", roles.get("Commander").display());
		assertEquals("&2⌖ Marksman", roles.get("Marksman").display());
		assertEquals("&9⛨ Defender", roles.get("Defender").display());
		assertEquals("&4⚔ Assault", roles.get("Assault").display());
		assertEquals("&e➤ Pointman", roles.get("Pointman").display());
		assertEquals("Medic", roles.get("Medic").displayName()); // the plain word, for radio lines

		CopRole marksman = roles.get("Marksman");
		assertEquals(22.0, marksman.rangedMin());
		assertEquals(32.0, marksman.rangedMax());
		for (CopRole role : roles.values())
			if (role != marksman) assertTrue(role.rangedMax() <= 14.0, role.name()); // the Marksman stands furthest
	}

	@Test
	@DisplayName("the shipped cop_roles.yml reads with no issues and matches the built-in catalogue exactly")
	void shippedFile_matchesBuiltIns() throws IOException {
		ConfigReport      report  = new ConfigReport();
		CopConfigProvider shipped = provider(ALL_TIERS, shipped("copsncrooks/cop_roles.yml"), report);
		CopConfigProvider builtIn = provider(TIERS, null, new ConfigReport());

		assertTrue(report.issues().isEmpty(), report.issues()::toString);
		for (int level = 1; level <= 5; level++)
			assertEquals(builtIn.getSquadComposition(level), shipped.getSquadComposition(level), "level " + level);
	}

	@Test
	@DisplayName("the shipped cop_roles.yml with Roles_Enabled: false - no composition, and no unknown keys")
	void shippedFile_rolesDisabled_noUnknownKeys() throws IOException {
		String yaml = shipped("copsncrooks/cop_roles.yml");
		assertTrue(yaml.contains("Roles_Enabled: true"));

		ConfigReport      report   = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, yaml.replace("Roles_Enabled: true", "Roles_Enabled: false"),
		                                      report);

		assertNull(provider.getSquadComposition(3));
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           report.issues()::toString);
	}

	@Test
	@DisplayName("the role keys moved: cops.yml no longer declares them, and Roles there is an unknown key")
	void cops_yml_noLongerHoldsRoles() throws IOException {
		assertFalse(shipped("copsncrooks/cops.yml").contains("Squad_Composition"));
		assertFalse(shipped("copsncrooks/cops.yml").contains("Roles_Enabled"));

		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS.replace("Cops:\n", """
				Cops:
				   Roles_Enabled: false
				   Roles:
				      Medic:
				         Health_Multiplier: 3.0
				"""), null, report);

		assertEquals(1.0, byName(provider.getSquadComposition(5)).get("Medic").healthMultiplier()); // not read
		assertTrue(report.issues().stream().anyMatch(i -> "config.unknown_key".equals(i.code())
		                                                  && i.path().endsWith("Roles")), report.issues()::toString);
	}

	// ── overrides and bad values ───────────────────────────────────────────────

	@Test
	@DisplayName("a Roles entry is read key by key over the built-in role; a new role over a plain one; bad values reported")
	void rolesBlock_overridesKeyByKey_badValuesReported() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, """
				Roles:
				   Marksman:
				      Display:
				         Name: "Sniper"
				         Symbol: ""
				      Ranged_Max_Distance: 40.0
				      Fire_Rate_Scale: 0.8
				   Breacher:
				      Fan_Placement: SIDEWAYS
				      Health_Multiplier: 20.0
				      Leader_Priority: 3
				      Strafe_Degrees: 30.0
				      Difficulty_Bonus: 9
				      Medic: true
				      Commander: true
				      Retreat:
				         Health_Fraction: 0.2
				Squad_Composition:
				   1:
				      - "Marksman"
				      - "Ghost"
				      - "Breacher"
				   two:
				      - "Assault"
				""", report);

		Map<String, CopRole> roles = byName(provider.getSquadComposition(1));
		assertEquals(List.of("Marksman", "Breacher"), names(provider.getSquadComposition(1)));

		CopRole marksman = roles.get("Marksman");
		assertEquals("&2Sniper", marksman.display()); // colour kept, symbol cleared
		assertEquals(40.0, marksman.rangedMax());
		assertEquals(22.0, marksman.rangedMin());     // built-in
		assertEquals(0.8, marksman.fireRateScale());
		assertEquals(1, marksman.difficultyBonus());  // built-in
		assertEquals(List.of("scout"), marksman.kit().weaponNames()); // built-in gear and weapons kept

		CopRole breacher = roles.get("Breacher");
		assertEquals("Breacher", breacher.display());
		assertEquals(NpcFanPlacement.ANY, breacher.placement()); // SIDEWAYS: reported, plain default kept
		assertEquals(1.0, breacher.healthMultiplier());          // 20 out of range: reported, default kept
		assertEquals(0, breacher.difficultyBonus());             // 9 out of range: reported, default kept
		assertEquals(3, breacher.leaderPriority());
		assertEquals(30.0, breacher.strafeDegrees());
		assertTrue(breacher.medic());
		assertTrue(breacher.commander());
		assertEquals(new RetreatSettings(true, 0.2, RetreatSettings.DEFAULT.radius()), breacher.retreat());

		for (String expected : new String[]{"SIDEWAYS", "Ghost", "two"})
			assertTrue(report.issues().stream().anyMatch(i -> i.message().contains(expected)), expected);
		assertTrue(report.issues().stream().anyMatch(i -> "config.range".equals(i.code())), report.issues()::toString);
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           report.issues()::toString);
	}

	@Test
	@DisplayName("Roles_Enabled: false turns roles off - every cop spawns as its plain tier")
	void rolesDisabled_noComposition() {
		assertNull(provider(TIERS, "Roles_Enabled: false\n", new ConfigReport()).getSquadComposition(3));
	}

	// ── gear and weapons ───────────────────────────────────────────────────────

	@Test
	@DisplayName("Gear slots: a material name, or Material + Leather_Color (hex or R,G,B) + Glow; \"\" empties the slot; bad values reported and skipped")
	void gear_parsed_badValuesReported() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, """
				Roles:
				   Medic:
				      Weapon_Pool:
				         - "weapon:revolver"
				         - "IRON_SWORD"
				      Gear:
				         Helmet:
				            Material: LEATHER_HELMET
				            Leather_Color: "#FF0000"
				            Glow: true
				         Chestplate:
				            Material: LEATHER_CHESTPLATE
				            Leather_Color: "0, 128, 255"
				         Leggings: ""
				         Boots:
				            Material: LEATHER_BOOTS
				            Leather_Color: "not a colour"
				         Off_Hand: "NOT_A_MATERIAL"
				Squad_Composition:
				   1:
				      - "Medic"
				""", report);

		CopRole.Kit kit = byName(provider.getSquadComposition(1)).get("Medic").kit();
		assertEquals(new CopRole.Gear(Material.LEATHER_HELMET, Color.fromRGB(0xFF0000), true), kit.helmet());
		assertEquals(new CopRole.Gear(Material.LEATHER_CHESTPLATE, Color.fromRGB(0, 128, 255), false),
		             kit.chestplate());
		assertEquals(CopRole.Gear.NONE, kit.leggings());
		assertEquals(new CopRole.Gear(Material.LEATHER_BOOTS, null, false), kit.boots()); // colour dropped
		assertEquals(Material.GOLDEN_APPLE, kit.offHand().material());                   // built-in kept
		assertEquals(List.of("revolver"), kit.weaponNames());
		assertEquals(Material.IRON_SWORD, kit.weaponItems().get(0).getType());

		assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("not a colour")),
		           report.issues()::toString);
		assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("NOT_A_MATERIAL")),
		           report.issues()::toString);
		assertTrue(report.issues().stream().noneMatch(i -> "config.unknown_key".equals(i.code())),
		           report.issues()::toString);
	}

	@Test
	@DisplayName("Leather_Color on a piece that is not leather is reported and dropped, so the edit is not silently lost")
	void leatherColor_onNonLeather_reported() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, """
				Roles:
				   Defender:
				      Gear:
				         Helmet:
				            Material: IRON_HELMET
				            Leather_Color: "#FF0000"
				Squad_Composition:
				   1:
				      - "Defender"
				""", report);

		assertEquals(new CopRole.Gear(Material.IRON_HELMET, null, false),
		             byName(provider.getSquadComposition(1)).get("Defender").kit().helmet());
		assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("only applies to LEATHER_")),
		           report.issues()::toString);
	}

	@Test
	@DisplayName("Tiers.<level or tier name>: read over the built-in tier kit; an unknown tier is reported")
	void tierKits_byLevelOrName() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, """
				Roles:
				   Assault:
				      Tiers:
				         1:
				            Weapon_Pool:
				               - "STONE_SWORD"
				         Military:
				            Gear:
				               Boots: NETHERITE_BOOTS
				         Space_Marine:
				            Weapon_Pool:
				               - "weapon:ray_gun"
				""", report);

		CopRole assault = byName(provider.getSquadComposition(5)).get("Assault");
		assertEquals(Material.STONE_SWORD, assault.kitFor(1, false).weaponItems().get(0).getType());
		CopRole.Kit military = assault.kitFor(5, true);
		assertEquals(Material.NETHERITE_BOOTS, military.boots().material());
		assertEquals(Material.NETHERITE_CHESTPLATE, military.chestplate().material()); // built-in tier 5 kept
		assertEquals(List.of("golden_ak47"), military.weaponNames());                 // built-in tier 5 kept
		assertTrue(report.issues().stream().anyMatch(i -> i.message().contains("Space_Marine")),
		           report.issues()::toString);
	}

	@Test
	@DisplayName("a Tiers key naming a tier cops.yml does not declare is reported and skipped")
	void tierKits_undeclaredTierNumber_reported() {
		ConfigReport report = new ConfigReport();
		CopConfigProvider provider = provider(TIERS, """
				Roles:
				   Assault:
				      Tiers:
				         2:
				            Weapon_Pool:
				               - "STONE_SWORD"
				""", report);

		assertTrue(report.issues().stream().anyMatch(i -> "config.unknown_tier".equals(i.code())
		                                                  && i.message().contains("tier 2")),
		           report.issues()::toString);
		assertEquals(List.of("golden_ak47"), byName(provider.getSquadComposition(5)).get("Assault").kitFor(5, true).weaponNames());
	}

	@Test
	@DisplayName("a role setting only one of Ranged_Min_Distance / Ranged_Max_Distance with no base band is reported")
	void halfBand_reported() {
		ConfigReport report = new ConfigReport();
		provider(TIERS, """
				Roles:
				   Pointman:
				      Ranged_Min_Distance: 6.0
				""", report);
		assertEquals(0, report.issues().stream().filter(i -> "config.incomplete_band".equals(i.code())).count(),
		             "the built-in Pointman has a band: no report");

		report = new ConfigReport();
		CopConfigProvider halfSet = provider(TIERS, """
				Roles:
				   Custom_Scout:
				      Ranged_Min_Distance: 6.0
				Squad_Composition:
				   1:
				      - "Custom_Scout"
				""", report);
		ConfigReport finalReport = report;
		assertTrue(finalReport.issues().stream().anyMatch(i -> "config.incomplete_band".equals(i.code())),
		           finalReport.issues()::toString);
		CopRole scout = byName(halfSet.getSquadComposition(1)).get("Custom_Scout");
		assertNull(scout.rangedMin(), "a half-set band is dropped, so the role is classified by the band applied");
		assertNull(scout.rangedMax());
	}

	// ── tier scaling of the built-in catalogue ─────────────────────────────────

	@Test
	@DisplayName("built-in tier scaling: each role keeps its identity piece, the armour and gun grow with the tier")
	void builtIns_scaleWithTier_identityKept() {
		Map<String, CopRole> roles = byName(provider(TIERS, null, new ConfigReport()).getSquadComposition(5));

		CopRole medic = roles.get("Medic");
		CopRole.Kit swatMedic = medic.kitFor(4, true), militaryMedic = medic.kitFor(5, true);
		assertEquals(List.of("pistol"), medic.kitFor(2, true).weaponNames()); // a weak gun at the low tiers
		assertEquals(List.of("mp5"), swatMedic.weaponNames());
		assertEquals(List.of("mp5"), militaryMedic.weaponNames());   // never weak at Military
		assertEquals(Material.NETHERITE_CHESTPLATE, militaryMedic.chestplate().material());
		assertEquals(Material.DIAMOND_LEGGINGS, militaryMedic.leggings().material());
		for (int tier = 1; tier <= 5; tier++) { // the red leather helmet and the golden apple at every tier
			CopRole.Kit kit = medic.kitFor(tier, true);
			assertEquals(new CopRole.Gear(Material.LEATHER_HELMET, Color.fromRGB(0xB02E26), tier >= 4), kit.helmet());
			assertEquals(Material.GOLDEN_APPLE, kit.offHand().material());
		}
		for (CopRole role : roles.values()) // the Military Medic's gun is strictly below every other role's
			if (role != medic)
				for (String gun : role.kitFor(5, true).weaponNames())
					assertFalse(militaryMedic.weaponNames().contains(gun), role.name() + " shares " + gun);
		for (CopRole role : roles.values()) // SWAT and Military never wear plain leather: a role-coloured piece glints
			for (int tier = 4; tier <= 5; tier++) {
				CopRole.Kit kit = role.kitFor(tier, true);
				for (CopRole.Gear piece : new CopRole.Gear[]{kit.helmet(), kit.chestplate(), kit.leggings(),
				                                             kit.boots()})
					if (piece != null && piece.material().name().startsWith("LEATHER"))
						assertTrue(piece.glow(), role.name() + " tier " + tier + " " + piece);
			}

		CopRole marksman = roles.get("Marksman");
		assertEquals(List.of("scout"), marksman.kitFor(3, true).weaponNames());
		assertEquals(List.of("scout"), marksman.kitFor(4, true).weaponNames());
		assertEquals(List.of("awp"), marksman.kitFor(5, true).weaponNames());

		assertEquals(List.of("golden_ak47"), roles.get("Assault").kitFor(5, true).weaponNames());
		assertEquals(List.of("golden_ak47"), roles.get("Commander").kitFor(5, true).weaponNames());
		assertEquals(List.of("steyr_aug"), roles.get("Pointman").kitFor(5, true).weaponNames());
		assertEquals(Material.SHIELD, roles.get("Defender").kitFor(5, true).offHand().material());
		assertEquals(Material.GOLDEN_HELMET, roles.get("Commander").kitFor(5, true).helmet().material());
		for (CopRole role : roles.values()) // Military never wears leather or chain on the body
			assertTrue(role.kitFor(5, true).chestplate().material().name().startsWith("NETHERITE"), role.name());
	}

	@Test
	@DisplayName("an Officer Marksman: never in a level 1 squad; forced in, it keeps the tier's melee weapon and light gear")
	void officerMarksman_neverSpawns_orLevelAppropriate() {
		CopConfigProvider provider = provider(TIERS, null, new ConfigReport());
		assertFalse(names(provider.getSquadComposition(1)).contains("Marksman"));
		assertFalse(names(provider.getSquadComposition(2)).contains("Marksman"));

		CopRole.Kit officer = byName(provider.getSquadComposition(5)).get("Marksman").kitFor(1, false);
		assertNull(officer.weaponNames()); // melee tier: the tier's own pool stays
		for (CopRole.Gear piece : new CopRole.Gear[]{officer.helmet(), officer.chestplate(), officer.leggings(),
		                                             officer.boots()})
			assertTrue(piece.material().name().startsWith("LEATHER"), piece::toString);
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("the shipped cop_roles.yml mixes tiers: 3 stars [3,2,3,3,2], 4 stars [4,3,4,4,3,3], 5 stars [5,4,5,5,4,4], 1-2 stars unchanged")
	void shippedFile_mixedTiers() throws IOException {
		ConfigReport      report  = new ConfigReport();
		CopConfigProvider shipped = provider(ALL_TIERS, shipped("copsncrooks/cop_roles.yml"), report);

		assertEquals(List.of(3, 2, 3, 3, 2), shipped.getSquadTiers(3));
		assertEquals(List.of(4, 3, 4, 4, 3, 3), shipped.getSquadTiers(4));
		assertEquals(List.of(5, 4, 5, 5, 4, 4), shipped.getSquadTiers(5));
		assertEquals(List.of(0, 0), shipped.getSquadTiers(1));
		assertEquals(List.of(0, 0, 0), shipped.getSquadTiers(2));
		assertEquals(List.of("Commander", "Pointman", "Defender", "Marksman", "Assault"),
		             names(shipped.getSquadComposition(3)));
		assertTrue(report.issues().isEmpty(), report.issues()::toString);
	}

	static CopConfigProvider provider(String cops, @Nullable String roles, ConfigReport report) {
		ConfigParser parser      = new ConfigParser();
		NodeReader   copsReader  = NodeReader.of(parser.parse(Path.of("cops.yml"), new StringReader(cops), report)
		                                               .root(), report);
		NodeReader   rolesReader = roles == null ? null
		                           : NodeReader.of(parser.parse(Path.of("cop_roles.yml"), new StringReader(roles),
		                                                        report).root(), report);
		return new YamlCopConfigProvider(copsReader, rolesReader, report, null, null);
	}

	private String shipped(String resource) throws IOException {
		try (InputStream in = Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream(resource))) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<String> names(List<CopRole> roles) {
		return roles.stream().map(CopRole::name).toList();
	}

	private static Map<String, CopRole> byName(List<CopRole> roles) {
		Map<String, CopRole> result = new LinkedHashMap<>();
		for (CopRole role : roles) result.putIfAbsent(role.name(), role);
		return result;
	}
}
