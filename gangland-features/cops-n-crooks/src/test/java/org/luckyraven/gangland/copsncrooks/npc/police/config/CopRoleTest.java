package org.luckyraven.gangland.copsncrooks.npc.police.config;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.npc.TacticsConfig;
import org.luckyraven.keystone.npc.NpcDifficulty;
import org.luckyraven.keystone.npc.NpcEngagement;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CopRole - tier overlay, squad composition, firing band and the Defender's front cone")
class CopRoleTest {

	private static final CopRole POINTMAN = role("Pointman");
	private static final CopRole DEFENDER = role("Defender");
	private static final CopRole ASSAULT  = role("Assault");

	@Test
	@DisplayName("overlay: health multiplied, difficulty stepped up, fire rate scaled, Strafe_Degrees replaced; the rest is the tier's")
	void overlay_replacesOnlyDeclaredFields() {
		CopTierConfig tier = tier(new TacticsConfig(new NpcEngagement(true, 10.0, 4000, 0.12), 200.0));
		CopRole role = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, null, null, 1.5, null, 0, 0.0, 0.5, 1,
		                           null, 0, 60, false, false);

		CopTierConfig result = role.overlay(tier);

		assertEquals(45.0, result.health(), 1e-9);
		assertEquals(NpcDifficulty.HARD, result.difficulty());
		assertEquals(0.05, result.fireRateMultiplier(), 1e-9);
		assertEquals(new NpcEngagement(true, 0.0, 4000, 0.12), result.tactics().engagement());
		assertEquals(200.0, result.tactics().formationArc());
		assertEquals("&1Lieutenant", result.displayName()); // a role never renames the tier (radio callsign, nameplate)
		assertEquals(tier.damage(), result.damage());
		assertEquals(tier.weaponNamePool(), result.weaponNamePool());
		assertEquals(tier.canUseWeapons(), result.canUseWeapons());
	}

	@Test
	@DisplayName("overlay keeps a LEGACY tier LEGACY even when the role sets Strafe_Degrees")
	void overlay_keepsLegacyEngagement() {
		CopTierConfig tier = tier(new TacticsConfig(NpcEngagement.LEGACY, 270.0));
		CopRole role = new CopRole("Assault", "Assault", NpcFanPlacement.FLANK, null, null, 1.0, null, 0, 20.0, 1.0,
		                           0, null, 0, 60, false, false);

		assertSame(NpcEngagement.LEGACY, role.overlay(tier).tactics().engagement());
	}

	@Test
	@DisplayName("overlay caps the difficulty step at the top difficulty")
	void overlay_difficultyStepCapped() {
		CopRole role = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, null, null, 1.0, null, 0, null, 1.0, 3,
		                           null, 0, 60, false, false);

		assertEquals(NpcDifficulty.DEADLY, role.overlay(tier(TacticsConfig.DEFAULT)).difficulty());
	}

	@Test
	@DisplayName("nextRole: an empty group gets the first role, a filled prefix the next, overflow repeats the last")
	void nextRole_fillsInOrder_overflowRepeatsLast() {
		List<CopRole> composition = List.of(POINTMAN, DEFENDER, ASSAULT);

		assertSame(POINTMAN, CopRole.nextRole(composition, List.of()));
		assertSame(DEFENDER, CopRole.nextRole(composition, List.of(POINTMAN)));
		assertSame(ASSAULT, CopRole.nextRole(composition, List.of(POINTMAN, DEFENDER)));
		assertSame(ASSAULT, CopRole.nextRole(composition, List.of(POINTMAN, DEFENDER, ASSAULT)));
	}

	@Test
	@DisplayName("nextRole: a live responder already holding Defender is skipped; role-less cops count for nothing")
	void nextRole_skipsFilledRoles_ignoresRoleless() {
		List<CopRole> composition = List.of(POINTMAN, DEFENDER, ASSAULT);

		assertSame(POINTMAN, CopRole.nextRole(composition, Arrays.asList(DEFENDER, null)));
		assertSame(ASSAULT, CopRole.nextRole(composition, Arrays.asList(DEFENDER, POINTMAN)));
	}

	@Test
	@DisplayName("nextRole: no composition means no role")
	void nextRole_nullOrEmptyComposition_null() {
		assertNull(CopRole.nextRole(null, List.of()));
		assertNull(CopRole.nextRole(List.of(), List.of()));
	}

	@Test
	@DisplayName("nextSlot: the first unfilled entry, the last index when all are filled, -1 without a composition")
	void nextSlot_firstUnfilled_lastWhenFull_minusOneForNull() {
		List<CopRole> composition = List.of(POINTMAN, DEFENDER, ASSAULT);

		assertEquals(0, CopRole.nextSlot(composition, List.of()));
		assertEquals(1, CopRole.nextSlot(composition, List.of(POINTMAN)));
		assertEquals(2, CopRole.nextSlot(composition, Arrays.asList(DEFENDER, POINTMAN, null)));
		assertEquals(2, CopRole.nextSlot(composition, List.of(POINTMAN, DEFENDER, ASSAULT)));
		assertEquals(-1, CopRole.nextSlot(null, List.of()));
		assertEquals(-1, CopRole.nextSlot(List.of(), List.of()));
	}

	@Test
	@DisplayName("rangedBand: clamped under the weapon's reach, keeping a valid min < max; no reach leaves it as declared")
	void rangedBand_clampedToReach() {
		CopRole marksman = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, 14.0, 22.0, 1.0, null, 0, null,
		                               1.0, 0, null, 0, 60, false, false);

		assertArrayEquals(new double[]{8.0, 10.0}, marksman.rangedBand(10.0));
		assertArrayEquals(new double[]{14.0, 22.0}, marksman.rangedBand(null));
		assertArrayEquals(new double[]{14.0, 22.0}, marksman.rangedBand(100.0));
		assertArrayEquals(new double[]{0.0, 1.0}, marksman.rangedBand(1.0));
		assertNull(POINTMAN.rangedBand(10.0)); // no band declared: the settings.yml band
	}

	@Test
	@DisplayName("blocks: only hits from inside the front cone, horizontally, and only with a Block_Fraction")
	void blocks_frontConeOnly() {
		CopRole defender = new CopRole("Defender", "Defender", NpcFanPlacement.CENTER, null, null, 1.0, null, 0, null,
		                               1.0, 0, null, 0.5, 60, false, false);
		Location self = new Location(null, 0, 64, 0, 0f, 0f); // yaw 0 faces +Z

		assertTrue(defender.blocks(self, new Location(null, 0, 70, 10)));   // straight ahead, above
		assertTrue(defender.blocks(self, new Location(null, 5, 64, 10)));   // ~26.6 degrees off: inside 30
		assertFalse(defender.blocks(self, new Location(null, 10, 64, 10))); // 45 degrees off
		assertFalse(defender.blocks(self, new Location(null, 0, 64, -10))); // behind
		assertFalse(POINTMAN.blocks(self, new Location(null, 0, 64, 10)));  // no Block_Fraction
	}

	// ── gear and weapons per role and tier (live feedback round) ──────────────

	private static CopRole.Gear gear(Material material) {
		return new CopRole.Gear(material, null, false);
	}

	private static CopRole kitted(CopRole.Kit kit, Map<Integer, CopRole.Kit> tierKits) {
		return new CopRole("Medic", "Medic", NpcFanPlacement.CENTER, null, null, 1.0, 0, null, 1.0, 0, null, 0, 60,
		                   true, false, "&c", "✚", kit, tierKits);
	}

	private static CopTierConfig armoured(int number, boolean canUseWeapons) {
		return new CopTierConfig(number, "&4Military", 60.0, 7.0, 1.4, 5.0, canUseWeapons, true,
		                         List.of("rifle", "CROSSBOW"), List.of(new ItemStack(Material.CROSSBOW)),
		                         new ItemStack(Material.DIAMOND_HELMET), new ItemStack(Material.DIAMOND_CHESTPLATE),
		                         new ItemStack(Material.DIAMOND_LEGGINGS), new ItemStack(Material.DIAMOND_BOOTS),
		                         NpcDifficulty.DEADLY, TacticsConfig.DEFAULT, 0.1);
	}

	@Test
	@DisplayName("overlay: a slot from the tier's own kit beats the role's kit, which beats the tier's armour; same for the weapon pool")
	void overlay_tierKitBeatsRoleKitBeatsTierArmour() {
		CopRole.Kit roleKit = new CopRole.Kit(List.of("pistol"), List.of(), gear(Material.IRON_HELMET),
		                                      gear(Material.IRON_CHESTPLATE), null, CopRole.Gear.NONE,
		                                      gear(Material.SHIELD));
		CopRole.Kit military = new CopRole.Kit(List.of("mp5"), List.of(), null, gear(Material.NETHERITE_CHESTPLATE),
		                                       null, null, null);
		CopRole role = kitted(roleKit, Map.of(5, military));

		CopTierConfig top = role.overlay(armoured(5, true));
		assertEquals(Material.IRON_HELMET, top.helmet().getType());             // role kit
		assertEquals(Material.NETHERITE_CHESTPLATE, top.chestplate().getType()); // tier kit
		assertEquals(Material.DIAMOND_LEGGINGS, top.leggings().getType());      // tier armour
		assertNull(top.boots());                                                  // role kit: explicitly empty
		assertEquals(List.of("mp5"), top.weaponNamePool());
		assertEquals(Material.CROSSBOW, top.weaponPool().get(0).getType()); // no vanilla item of its own: the tier's
		assertEquals(Material.SHIELD, role.offHandFor(5).getType());

		CopTierConfig lower = role.overlay(armoured(4, true)); // no kit of its own for tier 4
		assertEquals(Material.IRON_CHESTPLATE, lower.chestplate().getType());
		assertEquals(List.of("pistol"), lower.weaponNamePool());
	}

	@Test
	@DisplayName("overlay on a melee tier keeps the tier's melee weapon unless that tier's own kit names one")
	void overlay_meleeTier_keepsTierWeapon() {
		CopRole.Kit roleKit = new CopRole.Kit(List.of("pistol"), List.of(), null, null, null, null, null);
		CopRole.Kit officer = new CopRole.Kit(List.of(), List.of(new ItemStack(Material.STONE_SWORD)), null, null,
		                                      null, null, null);
		CopRole role = kitted(roleKit, Map.of(1, officer));

		CopTierConfig sergeant = role.overlay(armoured(2, false));
		assertEquals(List.of("rifle", "CROSSBOW"), sergeant.weaponNamePool());
		assertEquals(Material.CROSSBOW, sergeant.weaponPool().get(0).getType());

		CopTierConfig first = role.overlay(armoured(1, false));
		assertEquals(List.of(), first.weaponNamePool());
		assertEquals(Material.STONE_SWORD, first.weaponPool().get(0).getType());
	}

	@Test
	@DisplayName("a role with no kit (the pre-gear constructor) leaves the tier's weapons and armour alone")
	void overlay_noKit_tierUnchanged() {
		CopTierConfig tier   = armoured(5, true);
		CopTierConfig result = POINTMAN.overlay(tier);

		assertEquals(tier.weaponNamePool(), result.weaponNamePool());
		assertSame(tier.weaponPool(), result.weaponPool());
		assertSame(tier.helmet(), result.helmet());
		assertSame(tier.boots(), result.boots());
		assertNull(POINTMAN.offHandFor(5));
	}

	@Test
	@DisplayName("Gear.toItem: NONE is no item, a plain piece needs no meta, a dyed and glowing one sets its meta")
	void gear_toItem() {
		assertNull(CopRole.Gear.NONE.toItem());
		assertEquals(Material.IRON_HELMET, gear(Material.IRON_HELMET).toItem().getType());

		ItemFactory      factory = mock(ItemFactory.class);
		LeatherArmorMeta meta    = mock(LeatherArmorMeta.class);
		when(factory.getItemMeta(any())).thenReturn(meta);
		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(Bukkit::getItemFactory).thenReturn(factory);

			ItemStack item = new CopRole.Gear(Material.LEATHER_HELMET, Color.RED, true).toItem();

			assertEquals(Material.LEATHER_HELMET, item.getType());
			verify(meta).setColor(Color.RED);
			verify(meta).addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
		}
	}

	private static CopRole role(String name) {
		return new CopRole(name, name, NpcFanPlacement.ANY, null, null, 1.0, null, 0, null, 1.0, 0, null, 0, 60, false,
		                   false);
	}

	private static CopTierConfig tier(TacticsConfig tactics) {
		return new CopTierConfig(3, "&1Lieutenant", 30.0, 4.0, 1.2, 4.0, true, false, List.of("rifle"), List.of(),
		                         null, null, null, null, NpcDifficulty.NORMAL, tactics, 0.1);
	}
}
