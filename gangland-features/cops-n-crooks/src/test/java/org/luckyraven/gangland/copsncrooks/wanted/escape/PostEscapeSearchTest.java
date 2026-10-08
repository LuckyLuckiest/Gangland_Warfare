package org.luckyraven.gangland.copsncrooks.wanted.escape;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** {@link PostEscapeSearch} (0.16.1 wanted-6): a shutdown ends every search; the chase peak is taken once. */
@DisplayName("PostEscapeSearch - shutdown ends every search, the chase peak is taken once")
class PostEscapeSearchTest {

	private UserManager<Player> users;
	private PostEscapeSearch    search;

	private Player newSearchedPlayer() {
		Player player = mock(Player.class);
		UUID   id     = UUID.randomUUID();
		when(player.getUniqueId()).thenReturn(id);

		Bounty bounty = mock(Bounty.class);
		when(bounty.getNotoriety()).thenReturn(BigDecimal.ZERO);
		when(bounty.getAutoBountyIncrease(anyInt(), anyInt())).thenReturn(BigDecimal.ZERO);
		Level level = mock(Level.class);
		when(level.getLevelValue()).thenReturn(1);
		User<Player> user = mock(User.class);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getLevel()).thenReturn(level);
		when(users.getUser(player)).thenReturn(user);

		search.begin(player, 1);
		return player;
	}

	@Test
	@DisplayName("endAll ends every searched player's search")
	void endAll_endsEverySearch() {
		users  = mock(UserManager.class);
		search = new PostEscapeSearch(users);
		Player first  = newSearchedPlayer();
		Player second = newSearchedPlayer();

		search.endAll();

		assertFalse(search.isSearching(first.getUniqueId()));
		assertFalse(search.isSearching(second.getUniqueId()));
	}

	@Test
	@DisplayName("the peak is the highest level recorded, and taking it forgets it")
	void takePeak_isHighestRecorded_thenForgotten() {
		users  = mock(UserManager.class);
		search = new PostEscapeSearch(users);
		UUID id = UUID.randomUUID();

		search.recordLevel(id, 3);
		search.recordLevel(id, 5);
		search.recordLevel(id, 2);

		assertEquals(5, search.takePeak(id, 1));
		assertEquals(1, search.takePeak(id, 1));
	}

	@Test
	@DisplayName("the peak never falls below the level the chase dropped from")
	void takePeak_neverBelowFallback() {
		users  = mock(UserManager.class);
		search = new PostEscapeSearch(users);

		assertEquals(4, search.takePeak(UUID.randomUUID(), 4));
		assertEquals(0, search.takePeak(UUID.randomUUID(), 0));
	}
}
