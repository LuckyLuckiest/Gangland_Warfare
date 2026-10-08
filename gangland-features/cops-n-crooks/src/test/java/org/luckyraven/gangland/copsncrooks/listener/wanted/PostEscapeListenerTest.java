package org.luckyraven.gangland.copsncrooks.listener.wanted;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
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
import org.luckyraven.gangland.file.configuration.Settings;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
	private PostEscapeSearch   search;

	@BeforeEach
	void setUp() {
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		wanted = mock(Wanted.class);
		when(wanted.getMaxLevel()).thenReturn(5);

		bounty = mock(Bounty.class);
		when(bounty.getNotoriety()).thenReturn(BigDecimal.ZERO);
		when(bounty.getAutoBountyIncrease(anyInt(), anyInt())).thenReturn(BigDecimal.valueOf(250));

		Level level = mock(Level.class);
		when(level.getLevelValue()).thenReturn(7);
		User<Player> user = mock(User.class);
		when(user.getBounty()).thenReturn(bounty);
		when(user.getLevel()).thenReturn(level);

		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		search   = new PostEscapeSearch(users);
		listener = new PostEscapeListener(search);
	}

	@Test
	@DisplayName("the search running out gives the cops up once, and the search ends")
	void expire_givesUpOnce_thenEnds() {
		List<UUID> givenUp = new ArrayList<>();
		search.onGiveUp(givenUp::add);
		UUID id = player.getUniqueId();
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		search.expire(id);
		search.expire(id);

		assertEquals(List.of(id), givenUp);
		assertFalse(search.isSearching(id));
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

	@Test
	@DisplayName("an escape is priced on the chase's peak star count, not on the star it dropped from")
	void escapeAfterClimb_isPricedOnPeak() {
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 5, WantedCause.CRIME));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 5, 1, WantedCause.EVASION));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		verify(bounty).getAutoBountyIncrease(7, 5);
	}

	@Test
	@DisplayName("a chase that ends without an escape leaves no peak for the next chase")
	void finishedChase_leavesNoPeak() {
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 5, WantedCause.CRIME));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 5, 0, WantedCause.ARREST));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 1, WantedCause.CRIME));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		verify(bounty).getAutoBountyIncrease(7, 1);
		verify(bounty, never()).getAutoBountyIncrease(7, 5);
	}

	@Test
	@DisplayName("a quitter's chase peak is forgotten: his next escape is priced on that chase alone")
	void quitForgetsPeak() {
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 5, WantedCause.CRIME));
		listener.onQuit(new PlayerQuitEvent(player, ""));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 0, 1, WantedCause.CRIME));
		listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

		verify(bounty).getAutoBountyIncrease(7, 1);
		verify(bounty, never()).getAutoBountyIncrease(7, 5);
	}

	@Test
	@DisplayName("an escape whose increase would take the notoriety past Bounty.Kill.Maximum adds nothing, as a kill does")
	void escapePastKillCap_addsNothing() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("bountyMaxKill");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, BigDecimal.valueOf(200));
		try {
			listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

			verify(bounty, never()).addNotoriety(any());
			assertTrue(search.isSearching(player.getUniqueId()), "the search still starts");
		} finally {
			field.set(null, previous);
		}
	}

	@Test
	@DisplayName("an escape whose increase stays within Bounty.Kill.Maximum adds it")
	void escapeWithinKillCap_addsIncrease() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("bountyMaxKill");
		field.setAccessible(true);
		Object previous = field.get(null);
		field.set(null, BigDecimal.valueOf(300));
		try {
			listener.onLevelChange(new WantedLevelChangeEvent(player, wanted, 1, 0, WantedCause.EVASION));

			verify(bounty).addNotoriety(BigDecimal.valueOf(250));
		} finally {
			field.set(null, previous);
		}
	}
}
