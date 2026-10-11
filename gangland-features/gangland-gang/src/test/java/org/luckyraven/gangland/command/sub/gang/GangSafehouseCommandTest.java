package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.events.gang.GangSafehouseSetEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.keystone.command.argument.Argument;

import org.luckyraven.keystone.economy.EconomyHandler;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** {@code /glw gang safehouse}: set, list, remove and teleport (GP-26). */
@DisplayName("GangSafehouseCommand - /glw gang safehouse")
class GangSafehouseCommandTest extends SafehouseTestBase {

	private void set(String name) {
		Argument setVerb = child(safehouseCommand(), "set");
		freeText(setVerb).executeArgument(player, new String[]{"gang", "safehouse", "set", name});
	}

	@Test
	@DisplayName("the leader sets a safehouse; the row is stored lower-case and the player is told the count")
	void leader_setsSafehouse() {
		set("Base");

		verify(repository).save(new Safehouse(1, "base", "world", 10.5, 64, 10.5, 90F, 5F));
		assertTrue(chat.contains(say("Safehouse.Set", "name", "Base", "count", 1, "max", "unlimited")), chat.toString());
	}

	@Test
	@DisplayName("capByLevel: a gang at its level cap cannot add a new safehouse, but may move an existing one")
	void capByLevel() {
		when(limits.maxSafehouses(1)).thenReturn(2);
		service.add(new Safehouse(1, "base", "world", 0, 64, 0, 0F, 0F));
		service.add(new Safehouse(1, "dock", "world", 5, 64, 5, 0F, 0F));
		clearInvocations(repository);

		set("yard");

		verify(repository, never()).save(any());
		assertEquals(2, service.count(1));
		assertTrue(chat.contains(say("Safehouse.Cap", "count", 2, "max", 2)), chat.toString());

		chat.clear();
		set("DOCK");

		assertEquals(2, service.count(1), "re-setting a name replaces it, it does not count twice");
		verify(repository).save(new Safehouse(1, "dock", "world", 10.5, 64, 10.5, 90F, 5F));
	}

	@Test
	@DisplayName("rankGate: a member without the manage node is refused and nothing is saved")
	void rankGate() {
		makeRexAMember();

		set("base");

		verify(repository, never()).save(any());
		assertTrue(chat.contains(Messages.COMMAND_NO_PERM.toString()), chat.toString());
	}

	@Test
	@DisplayName("a member holding the manage node may set")
	void rankGate_nodeGranted() {
		makeRexAMember();
		when(player.hasPermission("gangland.gang.safehouse.manage")).thenReturn(true);

		set("base");

		verify(repository).save(any());
	}

	@Test
	@DisplayName("cancelledSetEvent_refused: a cancelled GangSafehouseSetEvent stops the safehouse")
	void cancelledSetEvent_refused() {
		doAnswer(invocation -> {
			Event event = invocation.getArgument(0);
			if (event instanceof GangSafehouseSetEvent set) set.setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any());

		set("base");

		verify(pluginManager).callEvent(any(GangSafehouseSetEvent.class));
		verify(repository, never()).save(any());
		assertEquals(0, service.count(1));
		assertTrue(chat.contains(say("Safehouse.Set_Refused")), chat.toString());
	}

	@Test
	@DisplayName("disabledWorld_refused: no verb runs in a gang-disabled world")
	void disabledWorld_refused() {
		service.add(new Safehouse(1, "base", "world", 0, 64, 0, 0F, 0F));
		clearInvocations(repository);
		when(policy.enabledIn(any())).thenReturn(false);

		set("yard");
		child(safehouseCommand(), "list").executeArgument(player, new String[]{"gang", "safehouse", "list"});
		freeText(safehouseCommand()).executeArgument(player, new String[]{"gang", "safehouse", "base"});

		verify(repository, never()).save(any());
		assertTrue(teleported.isEmpty(), "no teleport in a disabled world");
		assertEquals(3, chat.stream().filter(say("Safehouse.Disabled_World")::equals).count(), chat.toString());
	}

	@Test
	@DisplayName("Safe_Check on: a spot with lava underfoot is refused")
	void unsafeSpot_refused() {
		when(rules.isSafehouseSafeCheck()).thenReturn(true);
		Block lava = mock(Block.class);
		when(lava.getType()).thenReturn(Material.LAVA);
		when(lava.isPassable()).thenReturn(true);
		when(lava.isLiquid()).thenReturn(true);
		when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(lava);

		set("base");

		verify(repository, never()).save(any());
		assertTrue(chat.contains(say("Safehouse.Unsafe", "name", "base")), chat.toString());
	}

	@Test
	@DisplayName("a name that is not 1 to 16 word characters, or a verb word, is refused")
	void invalidName_refused() {
		set("has space");
		set("list");

		verify(repository, never()).save(any());
		assertEquals(2, chat.stream().filter(say("Safehouse.Name_Invalid")::equals).count());
	}

	@Test
	@DisplayName("shieldZeroOnBuiltWaypoint: the transient GANG waypoint has no shield, the warm-up, cooldown and cost")
	void shieldZeroOnBuiltWaypoint() {
		when(rules.getSafehouseCost()).thenReturn(25D);
		Safehouse base = new Safehouse(1, "base", "world", 1, 70, 2, 30F, 4F);
		service.add(base);
		EconomyHandler economy = new EconomyHandler(null);
		economy.setAmount(BigDecimal.valueOf(100));
		when(user.getEconomy()).thenReturn(economy);

		freeText(safehouseCommand()).executeArgument(player, new String[]{"gang", "safehouse", "base"});

		assertEquals(1, teleported.size());
		Waypoint built = teleported.get(0);
		assertEquals(0, built.getShield(), "a safehouse arrival grants no invulnerability");
		assertEquals(Waypoint.WaypointType.GANG, built.getType());
		assertEquals(1, built.getGangId());
		assertEquals(5, built.getTimer());
		assertEquals(60, built.getCooldown());
		assertEquals(25D, built.getCost());
		assertEquals("world", built.getWorld());
		assertEquals(70D, built.getY());
		assertEquals(0, service.waypoint(base, 0D).getShield());
		assertEquals(0, BigDecimal.valueOf(75).compareTo(economy.getAmount()), "the fee is taken after arrival");
	}

	@Test
	@DisplayName("teleport to an unknown name is told, and nothing is built")
	void teleport_unknownName() {
		freeText(safehouseCommand()).executeArgument(player, new String[]{"gang", "safehouse", "ghost"});

		assertTrue(teleported.isEmpty());
		assertTrue(chat.contains(say("Safehouse.Not_Found", "name", "ghost")), chat.toString());
	}

	@Test
	@DisplayName("remove deletes the row; list shows the header and one row per safehouse")
	void removeAndList() {
		service.add(new Safehouse(1, "base", "world", 3, 64, 4, 0F, 0F));

		child(safehouseCommand(), "list").executeArgument(player, new String[]{"gang", "safehouse", "list"});
		assertTrue(chat.contains(say("Safehouse.List_Row", "name", "base", "world", "world", "x", 3, "y", 64, "z", 4)),
		           chat.toString());

		freeText(child(safehouseCommand(), "remove")).executeArgument(player, new String[]{"gang", "safehouse",
				"remove", "BASE"});

		assertEquals(0, service.count(1));
		verify(repository).delete(new Safehouse(1, "base", "world", 3, 64, 4, 0F, 0F));
		assertTrue(chat.contains(say("Safehouse.Removed", "name", "BASE")), chat.toString());
	}
}
