package org.luckyraven.gangland.copsncrooks.integration.detainment;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.Wanted;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The detainment flows (bribe, jail intake) clear the wanted level through this contract and name why: the cause has to
 * reach the {@link WantedEndEvent}, and a player who is no longer online is a silent no-op.
 */
@DisplayName("GanglandWantedClearContract - clearing a level with its cause")
class GanglandWantedClearContractTest {

	private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000006");

	private BukkitStatics statics;
	private List<Event>   events;
	private Player        player;
	private Wanted        wanted;
	private GanglandWantedClearContract contract;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		statics = BukkitStatics.install();
		statics.statics().when(Bukkit::isPrimaryThread).thenReturn(true);

		events = new ArrayList<>();
		doAnswer(invocation -> {
			events.add(invocation.getArgument(0));
			return null;
		}).when(statics.pluginManager()).callEvent(any());

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(PLAYER_ID);
		wanted = new Wanted(mock(JavaPlugin.class), 1, 5);
		wanted.setOwner(player);

		User<Player> user = mock(User.class);
		when(user.getWanted()).thenReturn(wanted);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		statics.statics().when(() -> Bukkit.getPlayer(PLAYER_ID)).thenReturn(player);
		contract = new GanglandWantedClearContract(users);
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	@Test
	@DisplayName("clearWanted(id, cause) ends the chase and the end event carries that cause")
	void clearWithCause_endEventCarriesTheCause() {
		wanted.setLevel(3);
		events.clear();

		contract.clearWanted(PLAYER_ID, WantedCause.BRIBE);

		assertEquals(0, wanted.getLevel());
		WantedEndEvent end = events.stream()
		                           .filter(WantedEndEvent.class::isInstance)
		                           .map(WantedEndEvent.class::cast)
		                           .findFirst()
		                           .orElseThrow(() -> new AssertionError("no WantedEndEvent fired"));
		assertEquals(WantedCause.BRIBE, end.getCause());
	}

	@Test
	@DisplayName("a player who is not online is a no-op")
	void offlinePlayer_isANoOp() {
		wanted.setLevel(2);
		events.clear();
		UUID gone = UUID.randomUUID();

		assertDoesNotThrow(() -> contract.clearWanted(gone, WantedCause.ARREST));

		assertEquals(2, wanted.getLevel());
		assertTrue(events.isEmpty());
	}

}
