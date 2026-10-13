package org.luckyraven.gangland.copsncrooks.npc.police.radio;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ScannerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * P-SCN-1: a player carrying the Police Scanner anywhere in the inventory (or off hand) overhears a squad with a member
 * within {@code Cops.Scanner.Range}; never an NPC, never with the scanner disabled, never beyond the range.
 */
@DisplayName("ScannerOverhearing")
class ScannerOverhearingTest {

	private final AtomicReference<ScannerSettings> settings = new AtomicReference<>(ScannerSettings.DEFAULT);
	private final ItemStack                        scanner  = mock(ItemStack.class);
	private final ItemStack                        other    = mock(ItemStack.class);

	private World              world;
	private Player            player;
	private PlayerInventory    inventory;
	private NpcSquad           squad;
	private boolean            npc;
	private ScannerOverhearing overhearing;

	@BeforeEach
	void setUp() {
		world     = mock(World.class);
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
		when(inventory.getContents()).thenReturn(new ItemStack[]{other, null, scanner});
		when(inventory.getItemInOffHand()).thenReturn(null);
		squad       = squadAt(world, 120);
		overhearing = new ScannerOverhearing(settings::get, stack -> stack == scanner, p -> npc);
	}

	private static CopNpc copAt(World w, double distance) {
		LivingEntity entity = mock(LivingEntity.class);
		when(entity.getLocation()).thenReturn(new Location(w, distance, 64, 0));
		when(entity.getWorld()).thenReturn(w);
		CopNpc cop = mock(CopNpc.class);
		when(cop.getEntity()).thenReturn(entity);
		return cop;
	}

	private static NpcSquad squadOf(CopNpc... cops) {
		NpcSquad s = mock(NpcSquad.class);
		when(s.members()).thenReturn(List.of(cops));
		return s;
	}

	private static NpcSquad squadAt(World w, double distance) {
		return squadOf(copAt(w, distance));
	}

	@Test
	@DisplayName("a scanner anywhere in the inventory and a member 120 blocks away: overheard")
	void holderNearSquadOverhears() {
		assertTrue(overhearing.test(player, squad));
	}

	@Test
	@DisplayName("a scanner in the off hand only counts too")
	void offHandScannerCounts() {
		when(inventory.getContents()).thenReturn(new ItemStack[]{other});
		when(inventory.getItemInOffHand()).thenReturn(scanner);
		assertTrue(overhearing.test(player, squad));
	}

	@Test
	@DisplayName("member 170 blocks away is out of the 160 range; no scanner at all is deaf")
	void tooFarOrNoItemFalse() {
		assertFalse(overhearing.test(player, squadAt(world, 170)));
		when(inventory.getContents()).thenReturn(new ItemStack[]{other, null});
		assertFalse(overhearing.test(player, squad));
	}

	@Test
	@DisplayName("exactly at the range still hears; the nearest member decides")
	void edgeOfRangeAndNearestMemberWins() {
		assertTrue(overhearing.test(player, squadAt(world, 160)));
		assertTrue(overhearing.test(player, squadOf(copAt(world, 170), copAt(world, 50))));
	}

	@Test
	@DisplayName("Scanner.Enable false: nobody overhears")
	void disabledFalse() {
		settings.set(new ScannerSettings(false, 160.0, "CLOCK", "x", List.of()));
		assertFalse(overhearing.test(player, squad));
	}

	@Test
	@DisplayName("a reload is seen on the next call: a shorter Range drops a 120 block squad")
	void reloadIsSeen() {
		assertTrue(overhearing.test(player, squad));
		settings.set(new ScannerSettings(true, 100.0, "CLOCK", "x", List.of()));
		assertFalse(overhearing.test(player, squad));
	}

	@Test
	@DisplayName("a squad in another world is never overheard")
	void otherWorldFalse() {
		assertFalse(overhearing.test(player, squadAt(mock(World.class), 10)));
	}

	@Test
	@DisplayName("an empty squad and a member without an entity never throw and never match")
	void emptyOrEntitylessSquadFalse() {
		assertFalse(overhearing.test(player, squadOf()));
		assertFalse(overhearing.test(player, squadOf(mock(CopNpc.class))));
	}

	@Test
	@DisplayName("an NPC player never overhears, scanner or not")
	void npcPlayerNever() {
		npc = true;
		assertFalse(overhearing.test(player, squad));
	}
}
