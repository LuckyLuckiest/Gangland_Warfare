package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.Color;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.DisguiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.perception.CopPerception;
import org.luckyraven.gangland.copsncrooks.wanted.disguise.DisguiseFixtures.Wearer;
import org.luckyraven.keystone.npc.NpcSquad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The disguise through the real {@link CopPerception} seam: acquisition (Idle, Pursuing squad-null) and the squad
 * pursuit sight range (Pursuing :142, Combat :75).
 */
@DisplayName("Disguise through CopPerception")
class DisguisePursuitTest {

	private static final double BASE = 40.0;

	private Wearer              wearer;
	private SuspectDescriptions descriptions;
	private PoliceOutfit        outfit;
	private CopPerception       perception;
	private CopNpc              cop;
	private NpcSquad            squad;
	private int                 level;

	@BeforeEach
	void setUp() {
		DisguiseSettings settings = DisguiseSettings.DEFAULT;
		wearer       = new Wearer();
		descriptions = new SuspectDescriptions(() -> settings, wearer.keys(), DisguiseFixtures.words());
		outfit       = new PoliceOutfit(() -> settings, wearer.keys());
		squad        = mock(NpcSquad.class);
		cop          = mock(CopNpc.class);
		level        = 1;
		perception   = new CopPerception();
		perception.addLockOnFilter(new DisguiseFilter(() -> settings, descriptions, outfit, p -> level,
		                                              p -> squad.hasFreshSighting()));
		when(cop.canSee(eq(wearer.player), anyDouble())).thenReturn(true);
	}

	private void crime() {
		descriptions.store(wearer.id, descriptions.describe(wearer.player));
		outfit.onCrime(wearer.player);
	}

	@Test
	@DisplayName("swap two pieces unseen: pursuitRange 0; seen: the normal range")
	void swapUnseenZeroesPursuitRange() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		when(squad.hasFreshSighting()).thenReturn(false);
		assertEquals(0.0, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);

		when(squad.hasFreshSighting()).thenReturn(true);
		assertEquals(BASE, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);
	}

	@Test
	@DisplayName("a new crime after the swap matches the look again: the pursuit range is back")
	void crimeAfterSwapReacquires() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));
		when(squad.hasFreshSighting()).thenReturn(false);
		assertEquals(0.0, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);

		crime();

		assertEquals(BASE, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);
	}

	@Test
	@DisplayName("acquisition: a mismatch while unseen is not acquired; the same look is")
	void acquisitionFollowsTheFilter() {
		wearer.helmet(Material.CARVED_PUMPKIN).leatherChest(Color.RED);
		crime();
		assertTrue(perception.acquires(cop, wearer.player, BASE));

		wearer.helmet(null).leatherChest(Color.fromRGB(20, 20, 250));

		assertFalse(perception.acquires(cop, wearer.player, BASE));
	}

	@Test
	@DisplayName("the police outfit at 2 stars: not acquired, and range 0 once the dispatch sighting is stale")
	void outfitHidesFromAcquireAndStalePursuit() {
		wearer.helmet(Material.IRON_HELMET, "police_helmet").chest(Material.IRON_CHESTPLATE, "police_vest");
		level = 2;

		assertFalse(perception.acquires(cop, wearer.player, BASE));

		when(squad.hasFreshSighting()).thenReturn(true);
		assertEquals(BASE, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9, "dispatch seed still fresh (D-D5)");

		when(squad.hasFreshSighting()).thenReturn(false);
		assertEquals(0.0, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);
	}

	@Test
	@DisplayName("the outfit above Outfit_Max_Level does not hide")
	void outfitThreeStarsDoesNotHide() {
		wearer.helmet(Material.IRON_HELMET, "police_helmet").chest(Material.IRON_CHESTPLATE, "police_vest");
		level = 3;
		when(squad.hasFreshSighting()).thenReturn(false);

		assertTrue(perception.acquires(cop, wearer.player, BASE));
		assertEquals(BASE, perception.pursuitRange(cop, wearer.player, squad, BASE), 1e-9);
	}
}
