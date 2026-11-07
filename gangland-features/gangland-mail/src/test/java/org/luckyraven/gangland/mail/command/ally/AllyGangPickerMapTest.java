package org.luckyraven.gangland.mail.command.ally;

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
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises the display-name to gang-id picker map behind the gang argument of {@code /glw gang ally request},
 * {@code accept}, {@code reject} and {@code pending cancel}: a gang name is its own token, a name shared by several
 * gangs becomes {@code name:id}, a gang counted twice (two mails) is disambiguated too. Read through the
 * {@link OptionalArgument} each command wires the map into.
 */
@DisplayName("Gang ally commands - gang picker map")
class AllyGangPickerMapTest {

	private static final int OWN = 1;

	private BukkitStatics       statics;
	private Player              player;
	private User<Player>        user;
	private UserManager<Player> userManager;
	private GangManager         gangManager;
	private MailManager         mailManager;
	private Gang                own;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		statics = BukkitStatics.install();

		player = mock(Player.class);
		user   = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(OWN);
		userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		gangManager = mock(GangManager.class);
		own         = gang(OWN, "Vipers");

		mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	private Supplier<OptionalArgument> request() {
		return () -> child(new GangAllyRequestCommand(mock(JavaPlugin.class), new Tree<>(), parent(), userManager,
		                                              mock(MemberManager.class), gangManager, mailManager,
		                                              mock(GangLimits.class), mock(GangMessages.class)));
	}

	private Supplier<OptionalArgument> accept() {
		return () -> child(new GangAllyAcceptCommand(mock(JavaPlugin.class), new Tree<>(), parent(), userManager,
		                                             mock(MemberManager.class), gangManager, mailManager,
		                                             mock(GangLimits.class), mock(GangMessages.class)));
	}

	private Supplier<OptionalArgument> reject() {
		return () -> child(new GangAllyRejectCommand(mock(JavaPlugin.class), new Tree<>(), parent(), userManager,
		                                             mock(MemberManager.class), gangManager, mailManager));
	}

	private Supplier<OptionalArgument> pendingCancel() {
		return () -> child(new GangAllyPendingCancelCommand(mock(JavaPlugin.class), new Tree<>(), parent(),
		                                                    userManager, mock(MemberManager.class), gangManager,
		                                                    mailManager));
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

	/** A pending ally request: {@code sender} gang asks {@code recipient} gang. */
	private void allyMail(int sender, int recipient) {
		mailManager.send(new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, sender, null,
		                              recipient, null, System.currentTimeMillis(), 0L, MailStatus.PENDING, false));
	}

	private static Set<String> keys(OptionalArgument argument, CommandSender sender) {
		return new HashSet<>(argument.getArgumentString(sender));
	}

	private void assertPickerMap(OptionalArgument argument) {
		assertEquals(Set.of("Cats", "Dogs:3", "Dogs:4", "Owls:5"), keys(argument, player));
		assertEquals("2", argument.getActualValue("Cats", player));
		assertEquals("3", argument.getActualValue("Dogs:3", player));
		assertEquals("4", argument.getActualValue("Dogs:4", player));
		assertEquals("5", argument.getActualValue("Owls:5", player));
		// a disambiguated name is no longer a key of its own, so it falls back to itself
		assertEquals("Dogs", argument.getActualValue("Dogs", player));
		assertEquals("Owls", argument.getActualValue("Owls", player));
	}

	/** Gangs 2..5 (two called Dogs) exist, 99 does not. */
	private void otherGangs() {
		gang(2, "Cats");
		gang(3, "Dogs");
		gang(4, "Dogs");
		gang(5, "Owls");
	}

	@Test
	@DisplayName("request: every other non-allied gang, same-named ones as name:id; self and allies are left out")
	void request_listsRequestableGangs() {
		Gang cats = gang(2, "Cats");
		Gang dogsA = gang(3, "Dogs");
		Gang dogsB = gang(4, "Dogs");
		Gang owls = gang(5, "Owls");
		Gang ally = gang(6, "Friends");
		when(ally.isAlly(own)).thenReturn(true);

		Map<Integer, Gang> all = new LinkedHashMap<>();
		for (Gang gang : List.of(own, cats, dogsA, dogsB, owls, ally)) all.put(gang.getId(), gang);
		when(gangManager.getGangs()).thenReturn(all);

		OptionalArgument argument = request().get();

		assertEquals(Set.of("Cats", "Dogs:3", "Dogs:4", "Owls"), keys(argument, player));
		assertEquals("2", argument.getActualValue("Cats", player));
		assertEquals("3", argument.getActualValue("Dogs:3", player));
		assertEquals("4", argument.getActualValue("Dogs:4", player));
		assertEquals("5", argument.getActualValue("Owls", player));
		assertEquals("Friends", argument.getActualValue("Friends", player));
		assertEquals("Vipers", argument.getActualValue("Vipers", player));
	}

	@Test
	@DisplayName("request: a gang named like an allied gang is not disambiguated by it")
	void request_alliedNamesDoNotCountTowardsDuplicates() {
		Gang cats = gang(2, "Cats");
		Gang alliedCats = gang(3, "Cats");
		when(alliedCats.isAlly(own)).thenReturn(true);
		Map<Integer, Gang> all = new LinkedHashMap<>();
		for (Gang gang : List.of(own, cats, alliedCats)) all.put(gang.getId(), gang);
		when(gangManager.getGangs()).thenReturn(all);

		OptionalArgument argument = request().get();

		assertEquals(Set.of("Cats"), keys(argument, player));
		assertEquals("2", argument.getActualValue("Cats", player));
	}

	@Test
	@DisplayName("accept: senders of pending incoming requests, name:id for shared names and for a sender asking twice")
	void accept_incomingRequests() {
		otherGangs();
		for (int sender : new int[]{2, 3, 4, 5, 5, 99}) allyMail(sender, OWN);

		assertPickerMap(accept().get());
	}

	@Test
	@DisplayName("reject: senders of pending incoming requests, name:id for shared names and for a sender asking twice")
	void reject_incomingRequests() {
		otherGangs();
		for (int sender : new int[]{2, 3, 4, 5, 5, 99}) allyMail(sender, OWN);

		assertPickerMap(reject().get());
	}

	@Test
	@DisplayName("pending cancel: targets of our pending outgoing requests, name:id for shared names and for a repeat")
	void pendingCancel_outgoingRequests() {
		otherGangs();
		for (int target : new int[]{2, 3, 4, 5, 5, 99}) allyMail(OWN, target);

		assertPickerMap(pendingCancel().get());
	}

	@Test
	@DisplayName("accept/reject/pending cancel read only their own direction of mail")
	void directionsDoNotMix() {
		otherGangs();
		allyMail(2, OWN);
		allyMail(OWN, 3);

		assertEquals(Set.of("Cats"), keys(accept().get(), player));
		assertEquals(Set.of("Cats"), keys(reject().get(), player));
		assertEquals(Set.of("Dogs"), keys(pendingCancel().get(), player));
	}

	@Test
	@DisplayName("every picker: a non-player sender gets no suggestions and values map to themselves")
	void nonPlayerSender_getsEmptyMap() {
		CommandSender console = mock(CommandSender.class);
		otherGangs();
		allyMail(2, OWN);
		allyMail(OWN, 3);
		Gang cats = gangManager.getGang(2);
		when(gangManager.getGangs()).thenReturn(Map.of(2, cats));

		for (Supplier<OptionalArgument> command : List.of(request(), accept(), reject(), pendingCancel())) {
			OptionalArgument argument = command.get();
			assertEquals(List.of(""), argument.getArgumentString(console));
			assertEquals("Cats", argument.getActualValue("Cats", console));
		}
	}

	@Test
	@DisplayName("every picker: a player without a gang gets an empty map")
	void userWithoutGang_getsEmptyMap() {
		otherGangs();
		allyMail(2, OWN);
		allyMail(OWN, 3);
		Gang cats = gangManager.getGang(2);
		when(gangManager.getGangs()).thenReturn(Map.of(2, cats));
		when(user.hasGang()).thenReturn(false);

		for (Supplier<OptionalArgument> command : List.of(request(), accept(), reject(), pendingCancel())) {
			assertEquals(List.of(""), command.get().getArgumentString(player));
		}
	}

	@Test
	@DisplayName("request/accept/reject: a user whose gang no longer resolves gets an empty map")
	void userGangMissing_getsEmptyMap() {
		otherGangs();
		allyMail(2, OWN);
		Gang cats = gangManager.getGang(2);
		when(gangManager.getGangs()).thenReturn(Map.of(2, cats));
		when(gangManager.getGang(OWN)).thenReturn(null);

		for (Supplier<OptionalArgument> command : List.of(request(), accept(), reject())) {
			assertEquals(List.of(""), command.get().getArgumentString(player));
		}
	}

}
