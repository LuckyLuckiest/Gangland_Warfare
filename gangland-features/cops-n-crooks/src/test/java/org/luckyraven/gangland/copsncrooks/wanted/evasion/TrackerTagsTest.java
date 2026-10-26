package org.luckyraven.gangland.copsncrooks.wanted.evasion;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopRole;
import org.luckyraven.gangland.copsncrooks.npc.police.config.TrackerSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.npc.NpcFanPlacement;
import org.luckyraven.keystone.npc.NpcSquad;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TrackerTags}: a ranged cop who sees a rider for {@code Tag_Seconds} in total tags the vehicle (cops PDC key,
 * expiry epoch ms); a rider of a tagged vehicle gives the squad a sighting every {@code Ping_Seconds}; swapping vehicles,
 * leaving the car or the tag expiring stops it. Fake clock, map-backed PDC, mocked cops.
 */
@DisplayName("TrackerTags")
class TrackerTagsTest {

	private static final long          SEC = 1000L;
	private static final long          T0  = 5_000_000L;
	private static final NamespacedKey KEY = new NamespacedKey("copsncrooks", "tracker");

	private static final CopRole MARKSMAN = new CopRole("Marksman", "Marksman", NpcFanPlacement.ANY, 8.0, 30.0, 1.0, null, 0,
	                                                     null, 1.0, 0, null, 0, 60, false, false);
	private static final CopRole ASSAULT  = new CopRole("Assault", "Assault", NpcFanPlacement.FLANK, null, null, 1.0, null, 0,
	                                                     null, 1.0, 0, null, 0, 60, false, false);

	private final long[]                     now  = {T0};
	private final UUID                       id   = UUID.randomUUID();
	private final Map<NamespacedKey, Object> pdcA = new HashMap<>();
	private final Map<NamespacedKey, Object> pdcB = new HashMap<>();
	private final Location                   locA = new Location(mock(World.class), 10, 64, 10);
	private final Location                   locB = new Location(mock(World.class), 90, 64, 90);

	private TrackerSettings      settings = TrackerSettings.DEFAULT;
	private int                  level    = 2;
	private boolean              sees     = true;
	private boolean              ranged   = true;
	private Entity               carA;
	private Entity               carB;
	private Player               player;
	private CopRadio             radio;
	private CopGroup             group;
	private NpcSquad             squad;
	private CopNpc               cop;
	private Entity               riding;
	private TrackerTags.Notifier notifier;
	private TrackerTags          tags;

	@BeforeEach
	void setUp() {
		carA   = vehicle(1, pdcA, locA);
		carB   = vehicle(2, pdcB, locB);
		riding = carA;
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(id);
		when(player.getVehicle()).thenAnswer(inv -> riding);

		cop = mock(CopNpc.class);
		when(cop.getRole()).thenAnswer(inv -> ranged ? MARKSMAN : ASSAULT);
		when(cop.canSee(any(Player.class), anyDouble())).thenAnswer(inv -> sees);

		squad = mock(NpcSquad.class);
		group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		when(group.getCops()).thenReturn(List.of(cop));

		radio    = mock(CopRadio.class);
		notifier = mock(TrackerTags.Notifier.class);
		tags     = new TrackerTags(() -> settings, p -> level, radio, () -> now[0], notifier, KEY);
	}

	/** An entity whose PDC is backed by {@code store} (LONG values only). */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Entity vehicle(int entityId, Map<NamespacedKey, Object> store, Location at) {
		PersistentDataContainer pdc = mock(PersistentDataContainer.class);
		when(pdc.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(
				inv -> store.get(inv.<NamespacedKey>getArgument(0)));
		when(pdc.has(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(
				inv -> store.containsKey(inv.<NamespacedKey>getArgument(0)));
		doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(2))).when(pdc)
		                                                                  .set(any(NamespacedKey.class), any(PersistentDataType.class), any());
		doAnswer(inv -> store.remove(inv.<NamespacedKey>getArgument(0))).when(pdc).remove(any(NamespacedKey.class));
		Entity e = mock(Entity.class);
		when(e.getEntityId()).thenReturn(entityId);
		when(e.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(("v" + entityId).getBytes()));
		when(e.getPersistentDataContainer()).thenReturn(pdc);
		when(e.getLocation()).thenReturn(at);
		return e;
	}

	/** Ticks once now, then once more after each of {@code seconds} 1 s steps. */
	private void tickEverySecond(int seconds) {
		for (int i = 0; i <= seconds; i++) {
			tags.tick(player, group);
			now[0] += SEC;
		}
	}

	private void tag(Map<NamespacedKey, Object> store, long expiry) {
		store.put(KEY, expiry);
	}

	@Test
	@DisplayName("a ranged cop who sees the rider for 4 s at 2 stars tags the car: PDC expiry, radio once, message once")
	void rangedCopFourSecondsTags() {
		tickEverySecond(4);

		assertEquals(T0 + 4 * SEC + 150 * SEC, pdcA.get(KEY));
		verify(radio, times(1)).sayFromLeader(group, "Tracker_Tagged");
		verify(notifier, times(1)).tagged(player);
	}

	@Test
	@DisplayName("3 s of sight is not enough")
	void threeSecondsDoNotTag() {
		tickEverySecond(3);

		assertNull(pdcA.get(KEY));
		verify(notifier, never()).tagged(any());
	}

	@Test
	@DisplayName("only a melee cop sees: no tag")
	void meleeCopNeverTags() {
		ranged = false;

		tickEverySecond(10);

		assertNull(pdcA.get(KEY));
		verify(radio, never()).sayFromLeader(any(), any());
	}

	@Test
	@DisplayName("a cop without a role (roles off) never tags")
	void roleLessCopNeverTags() {
		when(cop.getRole()).thenReturn(null);

		tickEverySecond(10);

		assertNull(pdcA.get(KEY));
	}

	@Test
	@DisplayName("one star never tags; Min_Level from the settings moves the threshold")
	void levelGate() {
		level = 1;
		tickEverySecond(10);
		assertNull(pdcA.get(KEY));

		settings = new TrackerSettings(true, 3, 4, 48.0, 6, 150);
		level    = 2;
		tickEverySecond(10);
		assertNull(pdcA.get(KEY));

		level = 3;
		tickEverySecond(5);
		assertTrue(pdcA.get(KEY) instanceof Long);
	}

	@Test
	@DisplayName("Tracker.Enable false: nothing tags, nothing pings")
	void disabledDoesNothing() {
		settings = new TrackerSettings(false, 2, 4, 48.0, 6, 150);
		tag(pdcA, T0 + 100 * SEC);

		tickEverySecond(10);

		verify(squad, never()).reportTip(any());
		verify(notifier, never()).tagged(any());
		verify(notifier, never()).hud(any(), anyInt());
	}

	@Test
	@DisplayName("no squad (group null): no tag, no ping, no hud")
	void noGroupDoesNothing() {
		tag(pdcA, T0 + 100 * SEC);

		for (int i = 0; i < 10; i++) {
			tags.tick(player, null);
			now[0] += SEC;
		}

		verify(squad, never()).reportTip(any());
		verify(notifier, never()).hud(any(), anyInt());
	}

	@Test
	@DisplayName("the cop is asked with Tag_Range")
	void canSeeUsesTagRange() {
		settings = new TrackerSettings(true, 2, 4, 33.5, 6, 150);

		tickEverySecond(1);

		verify(cop, atLeastOnce()).canSee(player, 33.5);
	}

	@Test
	@DisplayName("a cop who cannot see the rider adds no time")
	void unseenAddsNothing() {
		sees = false;

		tickEverySecond(10);

		assertNull(pdcA.get(KEY));
	}

	@Test
	@DisplayName("sight is totalled: 2 s seen, 2 s unseen, 2 s seen tags the car")
	void seenTimeAccumulatesAcrossGaps() {
		tickEverySecond(2);
		sees = false;
		tickEverySecond(2);
		sees = true;
		tickEverySecond(2);

		assertTrue(pdcA.get(KEY) instanceof Long);
	}

	@Test
	@DisplayName("a tick gap longer than 1 s counts as 1 s (a stalled server tags nobody)")
	void longGapIsCapped() {
		tags.tick(player, group);
		now[0] += 60 * SEC;
		tags.tick(player, group);

		assertNull(pdcA.get(KEY));
	}

	@Test
	@DisplayName("seen time resets when the rider changes vehicle: 3 s in A then 3 s in B tags neither")
	void swapResetsSeenTime() {
		tickEverySecond(3);
		riding = carB;
		tickEverySecond(3);

		assertNull(pdcA.get(KEY));
		assertNull(pdcB.get(KEY));
	}

	@Test
	@DisplayName("a tagged vehicle's rider pings the squad every 6 s: 13 s of ticks give two tips at the car")
	void taggedRiderPingsEverySixSeconds() {
		tag(pdcA, T0 + 150 * SEC);

		tickEverySecond(13);

		verify(squad, times(2)).reportTip(locA);
		// R4: a tracker ping is a tip, never a sighting: the squad searches the car's spot, it does not chase him live
		verify(squad, never()).reportSighting(any());
		verify(radio, times(2)).sayFromLeader(eq(group), eq("Tracker_Ping"), anyMap());
	}

	@Test
	@DisplayName("Ping_Seconds from the settings drives the cadence")
	void pingCadenceFollowsSettings() {
		settings = new TrackerSettings(true, 2, 4, 48.0, 3, 150);
		tag(pdcA, T0 + 150 * SEC);

		tickEverySecond(7);

		verify(squad, times(2)).reportTip(locA);
	}

	@Test
	@DisplayName("the action bar shows the tag's remaining seconds, rounded up")
	void hudShowsRemainingSeconds() {
		tag(pdcA, T0 + 142_500L);

		tags.tick(player, group);

		verify(notifier).hud(player, 143);
	}

	@Test
	@DisplayName("moving to an untagged vehicle stops the pings")
	void swapVehicleStopsPings() {
		tag(pdcA, T0 + 150 * SEC);
		tickEverySecond(7);
		verify(squad, times(1)).reportTip(locA);

		riding = carB;
		sees   = false;
		tickEverySecond(20);

		verify(squad, times(1)).reportTip(any());
	}

	@Test
	@DisplayName("the tag belongs to the vehicle: a rider of an already tagged car is pinged, clock starting at his first tick")
	void tagBelongsToTheVehicle() {
		riding = carB;
		tag(pdcB, T0 + 150 * SEC);

		tickEverySecond(7);

		verify(squad, times(1)).reportTip(locB);
	}

	@Test
	@DisplayName("the tag expires: at 151 s the key is gone, no ping, no hud")
	void tagExpiresAfter150s() {
		tag(pdcA, T0 + 150 * SEC);
		now[0] = T0 + 151 * SEC;

		tags.tick(player, group);

		assertFalse(pdcA.containsKey(KEY));
		verify(squad, never()).reportTip(any());
		verify(notifier, never()).hud(any(), anyInt());
	}

	@Test
	@DisplayName("a tagged vehicle but the player on foot: no ping, no hud")
	void onFootNoPing() {
		tag(pdcA, T0 + 150 * SEC);
		riding = null;

		tickEverySecond(10);

		verify(squad, never()).reportTip(any());
		verify(notifier, never()).hud(any(), anyInt());
	}

	@Test
	@DisplayName("an already tagged vehicle is not re-tagged: expiry unchanged, no radio, no message")
	void alreadyTaggedIsNotRetagged() {
		tag(pdcA, T0 + 100 * SEC);

		tickEverySecond(10);

		assertEquals(T0 + 100 * SEC, pdcA.get(KEY));
		verify(radio, never()).sayFromLeader(group, "Tracker_Tagged");
		verify(notifier, never()).tagged(any());
	}

	@Test
	@DisplayName("Expire_Seconds from the settings sets the stored expiry")
	void expireSecondsFollowsSettings() {
		settings = new TrackerSettings(true, 2, 4, 48.0, 6, 60);

		tickEverySecond(4);

		assertEquals(T0 + 4 * SEC + 60 * SEC, pdcA.get(KEY));
	}

	@Test
	@DisplayName("forget() drops the seen time: 3 s, forget, 2 s does not tag")
	void forgetClearsSeenTime() {
		tickEverySecond(3);
		tags.forget(id);
		tickEverySecond(2);

		assertNull(pdcA.get(KEY));
	}

	@Test
	@DisplayName("forget() drops the ping clock: the next ride starts a fresh period")
	void forgetClearsPingClock() {
		tag(pdcA, T0 + 150 * SEC);
		tickEverySecond(5);
		tags.forget(id);
		tickEverySecond(5);

		verify(squad, never()).reportTip(any());
	}

	@Test
	@DisplayName("the stored tag is a plain LONG epoch written with PersistentDataType.LONG; a copy of the container keeps pinging")
	void tagValueIsPlainLong() {
		tickEverySecond(4);
		assertTrue(pdcA.get(KEY) instanceof Long);
		verify(carA.getPersistentDataContainer()).set(eq(KEY), eq(PersistentDataType.LONG), eq(T0 + 154 * SEC));

		Map<NamespacedKey, Object> reloaded = new HashMap<>(pdcA);
		pdcA.clear();
		pdcA.putAll(reloaded);
		tickEverySecond(7);

		verify(squad, times(1)).reportTip(locA);
	}

	@Test
	@DisplayName("the action bar stops after a swap to an untagged car")
	void hudStopsAfterSwapToUntagged() {
		tag(pdcA, T0 + 150 * SEC);
		tags.tick(player, group);
		verify(notifier, atLeastOnce()).hud(eq(player), anyInt());

		clearInvocations(notifier);
		riding = carB;
		sees   = false;
		tickEverySecond(5);

		verify(notifier, never()).hud(any(), anyInt());
	}

	/** An extra cop with its own role and sight. */
	private static CopNpc cop(CopRole role, boolean canSee) {
		CopNpc c = mock(CopNpc.class);
		when(c.getRole()).thenReturn(role);
		when(c.canSee(any(Player.class), anyDouble())).thenReturn(canSee);
		return c;
	}

	@Test
	@DisplayName("one cop must be ranged AND see: a melee cop who sees plus a marksman who cannot tags nothing")
	void meleeSeerWithBlindMarksmanDoesNotTag() {
		List<CopNpc> cops = List.of(cop(ASSAULT, true), cop(MARKSMAN, false));
		when(group.getCops()).thenReturn(cops);

		tickEverySecond(10);

		assertNull(pdcA.get(KEY));
		verify(notifier, never()).tagged(any());
	}

	@Test
	@DisplayName("any cop of the squad counts, not only the first: a blind melee cop then a marksman who sees tags")
	void secondCopMarksmanTags() {
		List<CopNpc> cops = List.of(cop(ASSAULT, false), cop(MARKSMAN, true));
		when(group.getCops()).thenReturn(cops);

		tickEverySecond(4);

		assertEquals(T0 + 4 * SEC + 150 * SEC, pdcA.get(KEY));
	}

	@Test
	@DisplayName("P-TRK-1: after a tag expires the car needs a fresh 4 s of sight before it is tagged again")
	void expiredTagNeedsFreshFourSeconds() {
		tickEverySecond(4);
		verify(radio, times(1)).sayFromLeader(group, "Tracker_Tagged");

		now[0] = T0 + 155 * SEC;
		tags.tick(player, group);
		assertFalse(pdcA.containsKey(KEY));

		now[0] += SEC;
		tags.tick(player, group);
		assertNull(pdcA.get(KEY));
		verify(radio, times(1)).sayFromLeader(group, "Tracker_Tagged");

		for (int i = 0; i < 3; i++) {
			now[0] += SEC;
			tags.tick(player, group);
		}
		assertTrue(pdcA.get(KEY) instanceof Long);
		verify(radio, times(2)).sayFromLeader(group, "Tracker_Tagged");
		verify(notifier, times(2)).tagged(player);
	}
}
