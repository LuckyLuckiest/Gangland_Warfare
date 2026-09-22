package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@code GangAllyAbandonCommand.onlineGangMembers}, the broadcast-target lookup behind
 * {@code /glw gang ally abandon}.
 *
 * <p>Regression net for <b>GR-01</b> (Observation #1, gangs-ranks-mail.md): the command used to stream the online
 * players through {@code memberManager.getMember(uuid).getGangId()}. A player whose {@code Member} is not cached —
 * never persisted, cache cleared by a reload, joined before the plugin was installed — made that filter throw an
 * NPE, aborting the abandon after the first half of the broadcast had already gone out and before the alliance was
 * actually broken. Uncached players are now skipped.
 *
 * <p>Lives in the production package so it can reach the package-private static helper.
 */
@DisplayName("GangAllyAbandonCommand.onlineGangMembers — uncached members are skipped")
class GangAllyAbandonCommandTest {

	private static final int GANG_ID       = 7;
	private static final int OTHER_GANG_ID = 8;

	private MemberManager memberManager;

	@BeforeEach
	void setUp() {
		memberManager = mock(MemberManager.class);
	}

	@Test
	@DisplayName("GR-01: an online player with no cached Member is skipped instead of NPEing the command")
	void onlineGangMembers_uncachedMember_isSkipped() {
		Player inGang   = player(GANG_ID);
		Player uncached = playerWithoutMember();

		List<Player> targets = assertDoesNotThrow(
				() -> GangAllyAbandonCommand.onlineGangMembers(memberManager, List.of(uncached, inGang), GANG_ID));

		assertEquals(List.of(inGang), targets, "only the cached gang member is a broadcast target");
	}

	@Test
	@DisplayName("only players of the requested gang are returned")
	void onlineGangMembers_filtersByGangId() {
		Player mine   = player(GANG_ID);
		Player theirs = player(OTHER_GANG_ID);

		List<Player> targets = GangAllyAbandonCommand.onlineGangMembers(memberManager, List.of(mine, theirs), GANG_ID);

		assertEquals(List.of(mine), targets);
	}

	@Test
	@DisplayName("an empty online list yields no targets")
	void onlineGangMembers_noPlayers_returnsEmpty() {
		assertTrue(GangAllyAbandonCommand.onlineGangMembers(memberManager, List.of(), GANG_ID).isEmpty());
	}

	@Test
	@DisplayName("a lobby where nobody's Member is cached yields no targets and no exception")
	void onlineGangMembers_everyMemberUncached_returnsEmpty() {
		Player first  = playerWithoutMember();
		Player second = playerWithoutMember();

		List<Player> targets = assertDoesNotThrow(
				() -> GangAllyAbandonCommand.onlineGangMembers(memberManager, List.of(first, second), GANG_ID));

		assertTrue(targets.isEmpty());
	}

	private Player player(int gangId) {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		Member member = new Member(uuid);
		member.setGangId(gangId);
		when(memberManager.getMember(uuid)).thenReturn(member);

		return player;
	}

	private Player playerWithoutMember() {
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());

		return player;
	}

}
