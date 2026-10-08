package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.wanted.config.PostEscapeSettings;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.core.wanted.WantedContext;
import org.luckyraven.gangland.core.wanted.WantedStars;
import org.luckyraven.keystone.npc.NpcSquad;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PostEscapeSpotting} (0.16.1 wanted-1): a squad that sights a searched player raises him by the configured stars;
 * nothing else triggers it.
 */
@DisplayName("PostEscapeSpotting - a sighting during the search raises the searched player")
class PostEscapeSpottingTest {

	/** The radio clock the spotting reads; tip-offs are stamped on it. */
	private static final long NOW = 10_000L;

	private World              world;
	private Player             player;
	private User<Player>       user;
	private CopGroup           group;
	private NpcSquad           squad;
	private WantedStars        stars;
	private PostEscapeSearch   search;
	private PostEscapeSettings settings;
	private PostEscapeSpotting spotting;

	@BeforeEach
	void setUp() {
		world  = mock(World.class);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));

		Bounty bounty = mock(Bounty.class);
		when(bounty.getNotoriety()).thenReturn(BigDecimal.ZERO);
		when(bounty.getAutoBountyIncrease(anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
		Level level = mock(Level.class);
		when(level.getLevelValue()).thenReturn(1);
		user = mock(User.class);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getLevel()).thenReturn(level);

		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		search = new PostEscapeSearch(users);
		search.begin(player, 1);

		squad = mock(NpcSquad.class);
		group = mock(CopGroup.class);
		when(group.getSquad()).thenReturn(squad);
		when(group.isEmpty()).thenReturn(false);
		// the squad's sighting is of the searched player, a few blocks from him (wanted-14)
		when(squad.lastKnownLocation()).thenReturn(new Location(world, 2, 64, 1));

		stars    = mock(WantedStars.class);
		settings = new PostEscapeSettings(true, 120, true, "YELLOW", true, 1);
		spotting = new PostEscapeSpotting(search, stars, users, () -> settings, () -> NOW);
	}

	@Test
	@DisplayName("a squad with a fresh sighting raises the searched player by Spotted_Stars, not as a crime")
	void sightedSearchedPlayer_isRaised() {
		when(squad.hasFreshSighting()).thenReturn(true);

		spotting.onAiTick(player, group);

		verify(stars).raise(any(WantedContext.class), eq(1), eq(WantedCause.UNKNOWN));
	}

	@Test
	@DisplayName("a fresh sighting that is a tip-off (stuck recycle, hand-off) is not a sighting: nobody is raised")
	void tipOffSeededSighting_isNotRaised() {
		when(squad.hasFreshSighting()).thenReturn(true);
		when(group.tippedOffWithin(NOW, PostEscapeSpotting.TIP_OFF_WINDOW_MS)).thenReturn(true);

		spotting.onAiTick(player, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}

	@Test
	@DisplayName("a tip-off older than the grace window does not hide a real sighting")
	void oldTipOff_doesNotBlockRealSighting() {
		when(squad.hasFreshSighting()).thenReturn(true);
		when(group.tippedOffWithin(NOW, PostEscapeSpotting.TIP_OFF_WINDOW_MS)).thenReturn(false);

		spotting.onAiTick(player, group);

		verify(stars).raise(any(WantedContext.class), eq(1), eq(WantedCause.UNKNOWN));
	}

	@Test
	@DisplayName("a tip-off stamped 5 ms before its seeded sighting still covers that sighting at the grace edge (wanted-13)")
	void tipOffJustBeforeItsSighting_isNotRaised() {
		// the tip-off is stamped at the tick start; the sighting lands 5 ms later and reads fresh 1500 ms after it
		long tipOffAt = NOW - NpcSquad.SIGHTING_GRACE_MS - 5L;
		when(squad.hasFreshSighting()).thenReturn(true);
		when(group.tippedOffWithin(anyLong(), anyLong()))
				.thenAnswer(inv -> NOW - tipOffAt <= (Long) inv.getArgument(1));

		spotting.onAiTick(player, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}

	@Test
	@DisplayName("a sighting of someone else far from the searched player does not raise him (wanted-14)")
	void sightingFarFromSearchedPlayer_isNotRaised() {
		when(squad.hasFreshSighting()).thenReturn(true);
		when(squad.lastKnownLocation()).thenReturn(new Location(world, 200, 64, 200));

		spotting.onAiTick(player, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}

	@Test
	@DisplayName("no fresh sighting: the searched player is not raised")
	void unsightedSearchedPlayer_isNotRaised() {
		when(squad.hasFreshSighting()).thenReturn(false);

		spotting.onAiTick(player, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}

	@Test
	@DisplayName("a player who is not on the search is not raised by a sighting")
	void notSearched_isNotRaised() {
		Player other = mock(Player.class);
		when(other.getUniqueId()).thenReturn(UUID.randomUUID());
		when(squad.hasFreshSighting()).thenReturn(true);

		spotting.onAiTick(other, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}

	@Test
	@DisplayName("Spotted_Stars 0 keeps the search harmless: a sighting raises nobody")
	void zeroSpottedStars_isNotRaised() {
		settings = new PostEscapeSettings(true, 120, true, "YELLOW", true, 0);
		when(squad.hasFreshSighting()).thenReturn(true);

		spotting.onAiTick(player, group);

		verify(stars, never()).raise(any(WantedContext.class), anyInt(), any());
	}
}
