package org.luckyraven.gangland.gang.combat;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.data.teleportation.Waypoint;
import org.luckyraven.gangland.data.teleportation.Waypoint.WaypointType;
import org.luckyraven.gangland.events.teleportation.TeleportEvent;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.listener.gang.combat.CombatTeleportGuard;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CombatTag and CombatTeleportGuard")
class CombatTagTest {

	private final AtomicLong         clock = new AtomicLong(1_000L);
	private final Map<UUID, Integer> gangs = new HashMap<>();
	private final UUID               a     = UUID.randomUUID();
	private final UUID               b     = UUID.randomUUID();
	private       CombatTag          tag;

	@BeforeEach
	void setUp() {
		gangs.put(a, 1);
		gangs.put(b, 2);
		tag = new CombatTag(clock::get, () -> 15, uuid -> gangs.getOrDefault(uuid, -1));
	}

	@Test
	@DisplayName("expiresOnFakeClock: both sides are tagged for Tag_Seconds, then free")
	void expiresOnFakeClock() {
		tag.tag(a, b);

		assertTrue(tag.isTagged(a));
		assertTrue(tag.isTagged(b));
		assertEquals(Duration.ofSeconds(15), tag.remaining(a));

		clock.addAndGet(14_999L);
		assertTrue(tag.isTagged(b));
		assertEquals(Duration.ofMillis(1), tag.remaining(b));

		clock.addAndGet(1L);
		assertFalse(tag.isTagged(a));
		assertFalse(tag.isTagged(b));
		assertEquals(Duration.ZERO, tag.remaining(a));
	}

	@Test
	@DisplayName("ganglessAttacker_neverTags: a gangless alt, a gangmate or a zero tag time tags nobody")
	void ganglessAttacker_neverTags() {
		UUID alt = UUID.randomUUID();
		tag.tag(alt, b);
		assertFalse(tag.isTagged(alt));
		assertFalse(tag.isTagged(b));

		tag.tag(b, alt);
		assertFalse(tag.isTagged(b));

		gangs.put(alt, 2);
		tag.tag(alt, b);
		assertFalse(tag.isTagged(b), "same gang");

		gangs.put(alt, 3);
		CombatTag off = new CombatTag(clock::get, () -> 0, uuid -> gangs.getOrDefault(uuid, -1));
		off.tag(alt, b);
		assertFalse(off.isTagged(b), "Tag_Seconds 0");
	}

	private TeleportEvent teleport(UUID who, WaypointType type, Player player) {
		when(player.getUniqueId()).thenReturn(who);
		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		Waypoint waypoint = mock(Waypoint.class);
		when(waypoint.getType()).thenReturn(type);
		return new TeleportEvent(user, null, waypoint);
	}

	@Test
	@DisplayName("blocksGangWaypoint: a tagged player's gang teleport is cancelled and explained")
	void blocksGangWaypoint() {
		GangMessages messages = mock(GangMessages.class);
		when(messages.text(anyString(), anyString(), any(), any())).thenReturn("tagged");
		CombatTeleportGuard guard = new CombatTeleportGuard(tag, messages);
		tag.tag(a, b);

		Player        player = mock(Player.class);
		TeleportEvent event  = teleport(a, WaypointType.GANG, player);
		guard.onTeleport(event);

		assertTrue(event.isCancelled());
		verify(player).sendMessage("tagged");
	}

	@Test
	@DisplayName("leavesSpawnAndGlobal: other waypoint types and untagged players are untouched")
	void leavesSpawnAndGlobal() {
		CombatTeleportGuard guard = new CombatTeleportGuard(tag, mock(GangMessages.class));
		tag.tag(a, b);

		for (WaypointType type : new WaypointType[]{WaypointType.SPAWN, WaypointType.GLOBAL, WaypointType.HOSPITAL}) {
			TeleportEvent event = teleport(a, type, mock(Player.class));
			guard.onTeleport(event);
			assertFalse(event.isCancelled(), type.name());
		}

		Player        free  = mock(Player.class);
		TeleportEvent event = teleport(UUID.randomUUID(), WaypointType.GANG, free);
		guard.onTeleport(event);
		assertFalse(event.isCancelled());
		verify(free, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the fighting flag is still a plain set")
	void fightingFlag() {
		tag.setFighting(a, true);
		assertTrue(tag.isFighting(a));
		tag.setFighting(a, false);
		assertFalse(tag.isFighting(a));
	}
}
