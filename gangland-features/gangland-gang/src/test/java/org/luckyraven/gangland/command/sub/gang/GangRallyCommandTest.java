package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.safehouse.Safehouse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** {@code /glw gang rally [safehouse]} and {@code rally join}: online members, 60 s consent, one teleport each. */
@DisplayName("GangRallyCommand - /glw gang rally")
class GangRallyCommandTest extends SafehouseTestBase {

	private static final String[] CALL = {"gang", "rally"};
	private static final String[] JOIN = {"gang", "rally", "join"};

	private Player mate;
	private User<Player> mateUser;

	private void addMate() {
		mate = member("Mia", new Location(world, 200, 70, 200), false);
		mateUser = users.getUser(mate);
		onlineMembers.add(mateUser);
	}

	private void call() {
		rallyCommand().executeArgument(player, CALL);
	}

	private void join(Player who) {
		child(rallyCommand(), "join").executeArgument(who, JOIN);
	}

	@Test
	@DisplayName("onlineMembers only: the call goes to the other online members, never the caller or an offline member")
	void onlineMembersOnly() {
		addMate();
		Player offline = member("Off", new Location(world, 0, 64, 0), false);
		User<Player> offlineUser = users.getUser(offline);

		call();

		String line = say("Safehouse.Rally_Call", "player", "Rex", "where", "Rex", "seconds", 60);
		verify(mateUser).sendMessage(line);
		verify(offlineUser, never()).sendMessage(anyString());
		verify(user, never()).sendMessage(anyString());
		assertTrue(chat.contains(say("Safehouse.Rally_Sent", "count", 1)), chat.toString());

		join(offline);
		assertTrue(teleported.isEmpty(), "a member who was not online at send cannot answer the call");
	}

	@Test
	@DisplayName("nobody else online: told so and no call is opened")
	void noOneOnline() {
		call();

		assertTrue(chat.contains(say("Safehouse.Rally_None")), chat.toString());
		join(player);
		assertTrue(teleported.isEmpty());
	}

	@Test
	@DisplayName("consentExpires: a join inside the window teleports, one after 60 s is refused")
	void consentExpires() {
		addMate();
		call();

		now.addAndGet(59_000L);
		join(mate);
		assertEquals(1, teleported.size(), "inside the window the join is one gang teleport");

		call();
		now.addAndGet(60_000L);
		teleported.clear();
		join(mate);

		assertTrue(teleported.isEmpty(), "the window closed");
		verify(mate).sendMessage(say("Safehouse.Rally_Expired"));
	}

	@Test
	@DisplayName("a join is single use")
	void joinIsSingleUse() {
		addMate();
		call();

		join(mate);
		join(mate);

		assertEquals(1, teleported.size());
	}

	@Test
	@DisplayName("callerSnapshot: the call keeps the position at send, not where the caller stands at the join")
	void callerSnapshot() {
		addMate();
		call();

		standingAt.setX(999);
		standingAt.setZ(999);
		join(mate);

		assertEquals(1, teleported.size());
		assertEquals(10.5, teleported.get(0).getX());
		assertEquals(10.5, teleported.get(0).getZ());
		assertEquals(0, teleported.get(0).getShield());
		assertEquals(0D, teleported.get(0).getCost(), "answering a call is free");
	}

	@Test
	@DisplayName("rally to a safehouse sends the members there")
	void rallyToSafehouse() {
		addMate();
		service.add(new Safehouse(1, "base", "world", 50, 64, 60, 0F, 0F));

		freeText(rallyCommand()).executeArgument(player, new String[]{"gang", "rally", "base"});
		join(mate);

		assertEquals(50D, teleported.get(0).getX());
		assertEquals(60D, teleported.get(0).getZ());
	}

	@Test
	@DisplayName("rally to a name the gang does not have is told")
	void rallyUnknownSafehouse() {
		addMate();

		freeText(rallyCommand()).executeArgument(player, new String[]{"gang", "rally", "ghost"});

		assertTrue(chat.contains(say("Safehouse.Not_Found", "name", "ghost")), chat.toString());
		verify(mateUser, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("the call needs the rally rank node")
	void callNeedsRankNode() {
		addMate();
		makeRexAMember();

		call();

		assertTrue(chat.contains(Messages.COMMAND_NO_PERM.toString()), chat.toString());
		verify(mateUser, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("a gang-disabled world refuses the call")
	void disabledWorld_refused() {
		addMate();
		when(policy.enabledIn(any())).thenReturn(false);

		call();

		assertTrue(chat.contains(say("Safehouse.Disabled_World")), chat.toString());
		verify(mateUser, never()).sendMessage(anyString());
	}
}
