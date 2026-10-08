package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.wanted.escape.PostEscapeSearch;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PostEscapeListener} (0.16.1 T-187): only an escape by evasion that takes the last star starts the post-escape
 * search, and it adds the auto-bounty notoriety once.
 */
@DisplayName("PostEscapeListener - the post-escape search starts on an evasion escape")
class PostEscapeListenerTest {

	private Player    player;
	private Wanted    wanted;
	private Bounty    bounty;
	private PostEscapeListener listener;

	@BeforeEach
	void setUp() {
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		wanted = mock(Wanted.class);
		when(wanted.getMaxLevel()).thenReturn(5);

		bounty = mock(Bounty.class);
		when(bounty.getAutoBountyIncrease(anyInt(), anyInt())).thenReturn(BigDecimal.valueOf(250));

		Level level = mock(Level.class);
		when(level.getLevelValue()).thenReturn(7);
		User<Player> user = mock(User.class);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getLevel()).thenReturn(level);

		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		listener = new PostEscapeListener(new PostEscapeSearch(users, () -> 0L));
	}

	@Test
	@DisplayName("evasion takes the last star: the search begins and the auto-bounty notoriety is added once")
	void evasionLastStar_beginsSearch_addsNotorietyOnce() {
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		verify(bounty, times(1)).addNotoriety(BigDecimal.valueOf(250));
		verify(bounty).getAutoBountyIncrease(7, 1);
	}

	@Test
	@DisplayName("evasion that drops a star but leaves one behind starts no search and adds no bounty")
	void evasionNonZeroDrop_beginsNothing() {
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 2, 1, WantedCause.EVASION));

		verify(bounty, never()).addNotoriety(any());
	}
}
