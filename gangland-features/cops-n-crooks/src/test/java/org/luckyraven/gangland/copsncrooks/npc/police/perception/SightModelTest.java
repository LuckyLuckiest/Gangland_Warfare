package org.luckyraven.gangland.copsncrooks.npc.police.perception;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.civilians.npc.CivilianNpcRegistry;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopTierConfig;
import org.luckyraven.gangland.copsncrooks.npc.police.config.SightSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SightModel - light, crowd and sneaking scale a cop's alert range (P-SGT-1..4)")
class SightModelTest {

	private static final double EPS = 1e-9;

	private final CivilianNpcRegistry civilians  = mock(CivilianNpcRegistry.class);
	private final CopManager          manager    = mock(CopManager.class);
	private final CopGroup            group      = mock(CopGroup.class);
	private final NpcSquad            squad      = mock(NpcSquad.class);
	private final World               world      = mock(World.class);
	private final World               otherWorld = mock(World.class);
	private final Player              player     = mock(Player.class);
	private final PlayerInventory     inventory  = mock(PlayerInventory.class);
	private final Block               block      = mock(Block.class);
	private final Location            eyes       = mock(Location.class);
	private final UUID                id         = UUID.randomUUID();
	private final AtomicLong          now        = new AtomicLong(1_000_000L);
	private final CopNpc              cop        = mock(CopNpc.class);
	private final CopTierConfig       tier       = mock(CopTierConfig.class);
	private final List<CivilianNpc>   crowd      = new ArrayList<>();
	private final List<String>        debug      = new ArrayList<>();
	private       SightSettings       settings   = SightSettings.DEFAULT;
	private final SightModel          model      = new SightModel(() -> settings, civilians, manager, now::get);

	SightModelTest() {
		when(player.getUniqueId()).thenReturn(id);
		when(player.getName()).thenReturn("Bob");
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(player.getEyeLocation()).thenReturn(eyes);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.AIR));
		when(eyes.getWorld()).thenReturn(world);
		when(eyes.getBlock()).thenReturn(block);
		when(block.getLightLevel()).thenReturn((byte) 15);
		when(manager.groupOf(id)).thenReturn(group);
		when(group.getSquad()).thenReturn(squad);
		when(squad.millisSinceSighting()).thenReturn(10_000L);
		when(civilians.getActiveNpcs()).thenReturn(new ArrayList<>(crowd));
		when(cop.getTierConfig()).thenReturn(tier);
		when(tier.ignoreDarkness()).thenReturn(false);
		model.setDebugSink(debug::add);
	}

	private void light(int level) {
		when(block.getLightLevel()).thenReturn((byte) level);
	}

	/** n live civilians at the given distance from the player, in the player's world. */
	private void civilians(int n, double distance) {
		crowd.clear();
		for (int i = 0; i < n; i++) {
			crowd.add(civilian(world, distance, true));
		}
		when(civilians.getActiveNpcs()).thenReturn(new ArrayList<>(crowd));
	}

	private CivilianNpc civilian(World w, double distance, boolean valid) {
		CivilianNpc  npc    = mock(CivilianNpc.class);
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(w, distance, 64, 0));
		when(npc.getEntity()).thenReturn(entity);
		when(npc.isValid()).thenReturn(valid);
		return npc;
	}

	private double factor() {
		return model.factor(cop, player);
	}

	@Test
	@DisplayName("darkAlleyFactor: light 0-4 -> 0.4, 5-8 -> 0.7, 9+ -> 1.0")
	void darkAlleyFactor() {
		light(2);
		assertEquals(0.4, factor(), EPS);
		light(6);
		assertEquals(0.7, factor(), EPS);
		light(12);
		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("band edges follow Dark_Max_Level and Dim_Max_Level (4/5 and 8/9 by default)")
	void bandEdges() {
		light(4);
		assertEquals(0.4, factor(), EPS);
		light(5);
		assertEquals(0.7, factor(), EPS);
		light(8);
		assertEquals(0.7, factor(), EPS);
		light(9);
		assertEquals(1.0, factor(), EPS);

		settings = new SightSettings(true, 2, 0.5, 3, 0.8, 0.6, 3, 4.0, 2, 0.7, 0.25, 5);
		light(2);
		assertEquals(0.5, factor(), EPS);
		light(3);
		assertEquals(0.8, factor(), EPS);
		light(4);
		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("ignoreDarknessTierSkipsLight: a tier with Ignore_Darkness sees at full range in the dark")
	void ignoreDarknessTierSkipsLight() {
		light(0);
		when(tier.ignoreDarkness()).thenReturn(true);

		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("ignoreDarkness only skips light: sneaking still shrinks the range")
	void ignoreDarknessKeepsSneak() {
		light(0);
		when(tier.ignoreDarkness()).thenReturn(true);
		when(player.isSneaking()).thenReturn(true);

		assertEquals(0.7, factor(), EPS);
	}

	@Test
	@DisplayName("a cop with no tier config is treated as not ignoring darkness")
	void nullTierConfigKeepsDarkness() {
		light(0);
		when(cop.getTierConfig()).thenReturn(null);

		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("crowdNeedsThreeCalmUnarmedOutOfContact: 3 civilians, walking, empty hand, 3 s unseen -> 0.6")
	void crowdNeedsThreeCalmUnarmedOutOfContact() {
		civilians(3, 3.0);
		when(squad.millisSinceSighting()).thenReturn(3_000L);
		assertEquals(0.6, factor(), EPS);

		when(player.isSprinting()).thenReturn(true);
		assertEquals(1.0, factor(), EPS, "sprinting breaks the crowd");
		when(player.isSprinting()).thenReturn(false);

		civilians(2, 3.0);
		assertEquals(1.0, factor(), EPS, "two civilians are not a crowd");

		civilians(3, 3.0);
		when(squad.millisSinceSighting()).thenReturn(1_000L);
		assertEquals(1.0, factor(), EPS, "1 s since the squad saw you is still in contact");

		when(squad.millisSinceSighting()).thenReturn(2_000L);
		assertEquals(0.6, factor(), EPS, "exactly Crowd_Out_Of_Contact_Seconds counts");
	}

	@Test
	@DisplayName("an item in the main hand breaks the crowd")
	void itemInHandBreaksCrowd() {
		civilians(3, 3.0);
		when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.DIAMOND_SWORD));

		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("a null main-hand stack counts as empty")
	void nullHandIsEmpty() {
		civilians(3, 3.0);
		when(inventory.getItemInMainHand()).thenReturn(null);

		assertEquals(0.6, factor(), EPS);
	}

	@Test
	@DisplayName("only civilians within Crowd_Radius, in the player's world and alive, are counted")
	void crowdCountsRadiusWorldAndValidity() {
		crowd.clear();
		crowd.add(civilian(world, 1.0, true));
		crowd.add(civilian(world, 4.0, true));
		crowd.add(civilian(world, 4.5, true));        // beyond 4.0
		crowd.add(civilian(otherWorld, 1.0, true));   // other world
		crowd.add(civilian(world, 1.0, false));       // dead / invalid
		CivilianNpc noEntity = mock(CivilianNpc.class);
		when(noEntity.isValid()).thenReturn(true);
		crowd.add(noEntity);                           // no entity
		when(civilians.getActiveNpcs()).thenReturn(new ArrayList<>(crowd));

		assertEquals(1.0, factor(), EPS, "only 2 count");

		crowd.add(civilian(world, 2.0, true));
		when(civilians.getActiveNpcs()).thenReturn(new ArrayList<>(crowd));
		assertEquals(0.6, factor(), EPS, "the third counting civilian makes a crowd");
	}

	@Test
	@DisplayName("a player the squad has never sighted (or no squad at all) is out of contact")
	void noSquadOrNeverSightedIsOutOfContact() {
		civilians(3, 3.0);
		when(squad.millisSinceSighting()).thenReturn(Long.MAX_VALUE);
		assertEquals(0.6, factor(), EPS);

		when(manager.groupOf(id)).thenReturn(null);
		assertEquals(0.6, factor(), EPS);
	}

	@Test
	@DisplayName("sneaking alone multiplies by Sneak_Factor")
	void sneakAlone() {
		when(player.isSneaking()).thenReturn(true);

		assertEquals(0.7, factor(), EPS);
	}

	@Test
	@DisplayName("factorsMultiplyWithFloor: dark 0.4 x crowd 0.6 x sneak 0.7 = 0.168 -> Min_Factor 0.25")
	void factorsMultiplyWithFloor() {
		light(2);
		civilians(3, 3.0);
		when(player.isSneaking()).thenReturn(true);

		assertEquals(0.25, factor(), EPS);
	}

	@Test
	@DisplayName("factors multiply when above the floor: dim 0.7 x sneak 0.7 = 0.49")
	void factorsMultiplyAboveFloor() {
		light(6);
		when(player.isSneaking()).thenReturn(true);

		assertEquals(0.49, factor(), EPS);
	}

	@Test
	@DisplayName("shotResetsForFiveSeconds: shot at t, factor at t+4 s = 1.0, at t+6 s = the dark value")
	void shotResetsForFiveSeconds() {
		light(2);
		model.shot(player);

		now.addAndGet(4_000L);
		assertEquals(1.0, factor(), EPS);

		now.addAndGet(2_000L);
		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("Shot_Reset_Seconds comes from the settings")
	void shotResetWindowFollowsSettings() {
		settings = new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 10);
		light(2);
		model.shot(player);

		now.addAndGet(9_000L);
		assertEquals(1.0, factor(), EPS);
		now.addAndGet(2_000L);
		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("one player's shot does not reset another player")
	void shotIsPerPlayer() {
		Player other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		light(2);

		model.shot(other);

		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("burningOrGlowingResets: fireTicks > 0 -> 1.0; glowing -> 1.0")
	void burningOrGlowingResets() {
		light(2);
		when(player.getFireTicks()).thenReturn(20);
		assertEquals(1.0, factor(), EPS);

		when(player.getFireTicks()).thenReturn(0);
		when(player.isGlowing()).thenReturn(true);
		assertEquals(1.0, factor(), EPS);

		when(player.isGlowing()).thenReturn(false);
		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("glowingEffectResets: the GLOWING potion effect (spectral arrow, /effect) -> 1.0; without it 0.4")
	void glowingEffectResets() {
		light(2);
		when(player.isGlowing()).thenReturn(false);
		when(player.hasPotionEffect(PotionEffectType.GLOWING)).thenReturn(true);
		assertEquals(1.0, factor(), EPS);

		when(player.hasPotionEffect(PotionEffectType.GLOWING)).thenReturn(false);
		assertEquals(0.4, factor(), EPS);
	}

	@Test
	@DisplayName("disabledIsOne: Sight.Enable false -> 1.0 whatever the light, crowd or sneak")
	void disabledIsOne() {
		settings = new SightSettings(false, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5);
		light(0);
		civilians(5, 1.0);
		when(player.isSneaking()).thenReturn(true);

		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("settings are read live: a reload that changes Dark_Factor applies at once")
	void settingsReadLive() {
		light(2);
		assertEquals(0.4, factor(), EPS);

		settings = new SightSettings(true, 4, 0.5, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.25, 5);
		assertEquals(0.5, factor(), EPS);
	}

	@Test
	@DisplayName("Min_Factor from the settings is the floor")
	void minFactorFromSettings() {
		settings = new SightSettings(true, 4, 0.4, 8, 0.7, 0.6, 3, 4.0, 2, 0.7, 0.1, 5);
		light(2);
		when(player.isSneaking()).thenReturn(true);

		assertEquals(0.28, factor(), EPS);
	}

	@Test
	@DisplayName("an eye location with no world (or no block) skips the light factor and never throws")
	void nullWorldOrBlockIsFullLight() {
		when(eyes.getWorld()).thenReturn(null);
		when(eyes.getBlock()).thenReturn(null);

		assertDoesNotThrow(this::factor);
		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("a player with no world has no crowd and never throws")
	void nullPlayerWorldHasNoCrowd() {
		civilians(3, 3.0);
		when(player.getLocation()).thenReturn(new Location(null, 0, 64, 0));

		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("debug: one SIGHT line per player per 5 s, with factor, light, crowd and sneak")
	void debugLineThrottledPerPlayer() {
		light(2);

		factor();
		factor();
		assertEquals(List.of("SIGHT Bob factor=0.40 light=2 crowd=1.00 sneak=1.00"), debug);

		Player al   = mock(Player.class);
		UUID   alId = UUID.randomUUID();
		when(al.getUniqueId()).thenReturn(alId);
		when(al.getName()).thenReturn("Al");
		when(al.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(al.getEyeLocation()).thenReturn(eyes);
		when(al.getInventory()).thenReturn(inventory);
		when(manager.groupOf(alId)).thenReturn(null);
		model.factor(cop, al);
		assertEquals(2, debug.size());
		assertTrue(debug.get(1).startsWith("SIGHT Al "));

		now.addAndGet(4_999L);
		factor();
		assertEquals(2, debug.size());

		now.addAndGet(2L);
		factor();
		assertEquals(3, debug.size());
	}

	@Test
	@DisplayName("cnc-integ INT-4 (C-11): a crouched empty-handed player the squad has just seen keeps factor 1.0 at night")
	void integ_surrenderPoseWithFreshSightingIsNotShrunk() {
		light(2);
		when(player.isSneaking()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(true);

		assertEquals(1.0, factor(), EPS);
	}

	@Test
	@DisplayName("cnc-integ INT-4: with a stale sighting the same pose still hides (sneak x dark = 0.28)")
	void integ_surrenderPoseWithStaleSightingStillHides() {
		light(2);
		when(player.isSneaking()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(false);

		assertEquals(0.28, factor(), EPS);
	}

	@Test
	@DisplayName("cnc-integ INT-4: crouched but holding an item is not the surrender pose, so it still hides")
	void integ_crouchedWithItemStillHidesEvenWhenSeen() {
		light(2);
		when(player.isSneaking()).thenReturn(true);
		when(squad.hasFreshSighting()).thenReturn(true);
		when(inventory.getItemInMainHand()).thenReturn(new ItemStack(Material.STONE));

		assertEquals(0.28, factor(), EPS);
	}
}
