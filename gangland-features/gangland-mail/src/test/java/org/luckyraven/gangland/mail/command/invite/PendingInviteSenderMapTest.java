package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises the display-name to gang-id picker map that {@code /glw gang accept <gang>} and
 * {@code /glw gang decline <gang>} offer: a gang name is its own token, a name shared by two pending senders becomes
 * {@code name:id}, and a sender counted twice (two invites from one gang) is disambiguated too. Read through the
 * {@link OptionalArgument} the command wires the map into.
 */
@DisplayName("Gang invite accept/decline - pending sender picker map")
class PendingInviteSenderMapTest {

	private BukkitStatics  statics;
	private UUID           uuid;
	private Player         player;
	private MailManager    mailManager;
	private GangManager    gangManager;
	private User<Player>   user;
	private UserManager<Player> userManager;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		statics = BukkitStatics.install();

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		user = mock(User.class);
		userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		gangManager = mock(GangManager.class);

		mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	private Supplier<OptionalArgument> accept() {
		return () -> child(new GangInviteAcceptCommand(mock(JavaPlugin.class), new Tree<>(), parent(), userManager,
		                                               mock(MemberManager.class), gangManager,
		                                               mock(RankManager.class), mailManager, mock(MailMessages.class),
		                                               mock(GangMembershipService.class)));
	}

	private Supplier<OptionalArgument> decline() {
		return () -> child(new GangInviteDeclineCommand(mock(JavaPlugin.class), new Tree<>(), parent(), userManager,
		                                                mock(MemberManager.class), gangManager, mailManager,
		                                                mock(MailMessages.class)));
	}

	private static Argument parent() {
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		return parent;
	}

	private static OptionalArgument child(Argument command) {
		return (OptionalArgument) command.getNode().getChildren().stream().map(Tree.Node::getData)
		                                 .filter(OptionalArgument.class::isInstance).findFirst().orElseThrow();
	}

	private Gang gang(int id, String name) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getName()).thenReturn(name);
		when(gangManager.getGang(id)).thenReturn(gang);
		return gang;
	}

	private void invite(int senderGangId) {
		mailManager.send(new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, senderGangId, uuid,
		                              MailItem.NO_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING,
		                              false));
	}

	private static Set<String> keys(OptionalArgument argument, CommandSender sender) {
		return new HashSet<>(argument.getArgumentString(sender));
	}

	private void check(Supplier<OptionalArgument> command) {
		OptionalArgument argument = command.get();
		gang(2, "Cats");
		gang(3, "Dogs");
		gang(4, "Dogs");
		gang(5, "Owls");
		invite(2);
		invite(3);
		invite(4);
		invite(5);
		invite(5);
		invite(99); // sender gang no longer exists: left out of the map

		assertEquals(Set.of("Cats", "Dogs:3", "Dogs:4", "Owls:5"), keys(argument, player));
		assertEquals("2", argument.getActualValue("Cats", player));
		assertEquals("3", argument.getActualValue("Dogs:3", player));
		assertEquals("4", argument.getActualValue("Dogs:4", player));
		assertEquals("5", argument.getActualValue("Owls:5", player));
		// a name that was disambiguated is no longer a key of its own, so it falls back to itself
		assertEquals("Dogs", argument.getActualValue("Dogs", player));
		assertEquals("Owls", argument.getActualValue("Owls", player));
	}

	@Test
	@DisplayName("accept: same-named senders and a sender invited twice get name:id, a vanished gang is skipped")
	void accept_disambiguatesAndSkipsMissingGangs() {
		check(accept());
	}

	@Test
	@DisplayName("decline: same-named senders and a sender invited twice get name:id, a vanished gang is skipped")
	void decline_disambiguatesAndSkipsMissingGangs() {
		check(decline());
	}

	@Test
	@DisplayName("accept/decline: a non-player sender gets no suggestions and values map to themselves")
	void nonPlayerSender_getsEmptyMap() {
		CommandSender console = mock(CommandSender.class);
		invite(2);
		gang(2, "Cats");

		for (Supplier<OptionalArgument> command : List.of(accept(), decline())) {
			OptionalArgument argument = command.get();
			assertEquals(List.of(""), argument.getArgumentString(console));
			assertEquals("Cats", argument.getActualValue("Cats", console));
		}
	}

	@Test
	@DisplayName("accept: a player with no cached user gets an empty map")
	void accept_unknownUser_getsEmptyMap() {
		invite(2);
		gang(2, "Cats");
		Player stranger = mock(Player.class);
		when(stranger.getUniqueId()).thenReturn(uuid);

		OptionalArgument argument = accept().get();

		assertEquals(List.of(""), argument.getArgumentString(stranger));
	}

}
