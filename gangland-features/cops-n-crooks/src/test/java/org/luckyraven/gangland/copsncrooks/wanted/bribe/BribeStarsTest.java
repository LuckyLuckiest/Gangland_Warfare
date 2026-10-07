package org.luckyraven.gangland.copsncrooks.wanted.bribe;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.place.SetupPoint;
import org.luckyraven.gangland.copsncrooks.place.SetupPointRegistry;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.BribeStarSettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link BribeStars}: pickup points spawn a floating item, a wanted unseen player in range pockets it for one star. */
@DisplayName("BribeStars: the police bribe star pickups")
class BribeStarsTest {

	private final AtomicLong     now    = new AtomicLong(1_000_000L);
	private final List<Item>     items  = new ArrayList<>();
	private final List<Entity>   nearby = new ArrayList<>();

	private World               world;
	private SetupPointRegistry  points;
	private UserManager<Player> users;
	private CopManager          copManager;
	private WantedMessages      messages;
	private BribeStarSettings   settings  = BribeStarSettings.DEFAULT;
	private int                 lostSight = 10;
	private Player              player;
	private Wanted              wanted;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		world = mock(World.class);
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
		when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
				.thenAnswer(invocation -> new ArrayList<>(nearby));

		SetupPoint point = mock(SetupPoint.class);
		when(point.getId()).thenReturn(1);
		when(point.getLocation()).thenReturn(new Location(world, 10, 64, 10));
		points = mock(SetupPointRegistry.class);
		when(points.ofKind(SetupPoint.PICKUP)).thenReturn(List.of(point));

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(new Location(world, 10, 64, 10));
		wanted = mock(Wanted.class);
		when(wanted.isWanted()).thenReturn(true);
		when(wanted.getLevel()).thenReturn(3);
		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		copManager = mock(CopManager.class);
		messages   = mock(WantedMessages.class);
		when(messages.format(any(), any())).thenReturn("msg");
	}

	private BribeStars stars() {
		return new BribeStars(mock(JavaPlugin.class), points, users, () -> settings, () -> lostSight, messages, copManager,
		                      now::get) {
			@Override
			protected Item drop(Location at, String material) {
				Item item = mock(Item.class);
				when(item.isValid()).thenReturn(true);
				when(item.getLocation()).thenReturn(at);
				items.add(item);
				return item;
			}
		};
	}

	private void seenBy(long sightingAgoMs) {
		CopGroup group = mock(CopGroup.class, RETURNS_DEEP_STUBS);
		when(group.getSquad().millisSinceSighting()).thenReturn(sightingAgoMs);
		when(copManager.groupOf(player.getUniqueId())).thenReturn(group);
	}

	@Test
	@DisplayName("spawnsAnItemAtEachLoadedPickupPoint")
	void spawnsAnItemAtEachLoadedPickupPoint() {
		BribeStars stars = stars();
		stars.tick();
		stars.tick();

		assertEquals(1, items.size());
	}

	@Test
	@DisplayName("a point whose chunk is not loaded spawns nothing")
	void unloadedChunk_spawnsNothing() {
		when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
		stars().tick();

		assertEquals(0, items.size());
	}

	@Test
	@DisplayName("wantedPlayerInRange_losesOneStarWithContactCause_andTheItemGoes")
	void wantedPlayerInRange_losesOneStarWithContactCause_andTheItemGoes() {
		BribeStars stars = stars();
		stars.tick();
		nearby.add(player);
		stars.tick();

		verify(wanted).setLevel(2, WantedCause.CONTACT);
		verify(items.get(0)).remove();
		verify(player).sendMessage("msg");
	}

	@Test
	@DisplayName("notWantedPlayer_takesNothing")
	void notWantedPlayer_takesNothing() {
		when(wanted.isWanted()).thenReturn(false);
		BribeStars stars = stars();
		stars.tick();
		nearby.add(player);
		stars.tick();

		verify(wanted, never()).setLevel(anyInt(), any(WantedCause.class));
		verify(items.get(0), never()).remove();
	}

	@Test
	@DisplayName("seenPlayer_takesNothing_andIsToldOncePerFiveSeconds")
	void seenPlayer_takesNothing_andIsToldOncePerFiveSeconds() {
		seenBy(2_000L);
		BribeStars stars = stars();
		stars.tick();
		nearby.add(player);
		stars.tick();
		stars.tick();
		verify(wanted, never()).setLevel(anyInt(), any(WantedCause.class));
		verify(player, times(1)).sendMessage("msg");

		now.addAndGet(5_001L);
		stars.tick();
		verify(player, times(2)).sendMessage("msg");

		seenBy(10_000L);   // exactly Lost_Sight_Seconds ago: unseen
		stars.tick();
		verify(wanted).setLevel(2, WantedCause.CONTACT);
	}

	/** Final fix round 1: a squad that never sighted him (no sighting = Long.MAX_VALUE ms ago) does not see him. */
	@Test
	@DisplayName("a squad that never sighted the player lets him take the star")
	void neverSightedBySquad_takesTheStar() {
		seenBy(Long.MAX_VALUE);
		BribeStars stars = stars();
		stars.tick();
		nearby.add(player);
		stars.tick();

		verify(wanted).setLevel(2, WantedCause.CONTACT);
	}

	@Test
	@DisplayName("takenStar_respawnsAfterTheTimer")
	void takenStar_respawnsAfterTheTimer() {
		BribeStars stars = stars();
		stars.tick();
		nearby.add(player);
		stars.tick();
		nearby.clear();

		now.addAndGet(299_000L);
		stars.tick();
		assertEquals(1, items.size());

		now.addAndGet(1_000L);
		stars.tick();
		assertEquals(2, items.size());
	}

	@Test
	@DisplayName("despawnedItem_respawnsAtOnce")
	void despawnedItem_respawnsAtOnce() {
		BribeStars stars = stars();
		stars.tick();
		when(items.get(0).isValid()).thenReturn(false);
		stars.tick();

		assertEquals(2, items.size());
	}

	@Test
	@DisplayName("disabled_spawnsNothing")
	void disabled_spawnsNothing() {
		settings = new BribeStarSettings(false, 1, 300, 1.5, "NETHER_STAR");
		stars().tick();

		assertEquals(0, items.size());
	}

	@Test
	@DisplayName("shutdown_removesLiveItems")
	void shutdown_removesLiveItems() {
		BribeStars stars = stars();
		stars.tick();
		stars.onClear();

		verify(items.get(0)).remove();
	}
}
