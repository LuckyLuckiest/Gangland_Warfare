package org.luckyraven.gangland.mail.command;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Rank gate (GR-03 / T-05) on every mail-backed gang leaf, including the no-argument forms, and the self-alliance
 * guard on {@code /glw gang ally request}.
 */
@DisplayName("Gang mail commands - rank gate on every leaf, no self-alliance")
class GangMailCommandGuardTest {

	private static final int  GANG_ID  = 1;
	private static final int  OTHER_ID = 2;
	private static final UUID DAVE     = UUID.randomUUID();

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private MailManager          mailManager;
	private Player               player;
	private User<Player>         user;
	private Argument             invite;
	private Argument             ally;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});

		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());
		bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenReturn(mock(OfflinePlayer.class));

		mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();

		UUID uuid = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(GANG_ID);

		// A rank with a rank above it: not the gang leader, and it grants no node.
		Rank rank = new Rank("recruit", 5);
		rank.getNode().add(new Rank("boss", 6).getNode());
		Member member = new Member(uuid);
		member.setGangId(GANG_ID);
		member.setRank(rank);

		OfflinePlayer daveOffline = mock(OfflinePlayer.class);
		when(daveOffline.getUniqueId()).thenReturn(DAVE);
		when(daveOffline.getName()).thenReturn("Dave");
		User<OfflinePlayer> dave = mock(User.class);
		when(dave.getUser()).thenReturn(daveOffline);

		UserManager<Player>        userManager        = mock(UserManager.class);
		UserManager<OfflinePlayer> offlineUserManager = mock(UserManager.class);
		MemberManager              memberManager      = mock(MemberManager.class);
		GangManager                gangManager        = mock(GangManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		when(offlineUserManager.getUsers()).thenReturn(Map.of(DAVE, dave));
		when(memberManager.getMember(uuid)).thenReturn(member);
		Gang gang = gang(GANG_ID), other = gang(OTHER_ID);
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);
		when(gangManager.getGang(OTHER_ID)).thenReturn(other);

		JavaPlugin     plugin = mock(JavaPlugin.class);
		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		invite = new GangMailContribution(plugin, userManager, offlineUserManager, memberManager, gangManager,
		                                  mock(RankManager.class), mailManager).create(tree, parent).get(0);
		ally   = new Argument(plugin, "ally", tree);
		ally.addAllSubArguments(new GangAllyMailContribution(plugin, userManager, memberManager, gangManager,
		                                                     mailManager).create(tree, ally));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@ParameterizedTest(name = "/glw {0}")
	@CsvSource(delimiter = '|', value = {
			"gang ally accept          | INCOMING_ALLY",
			"gang ally accept 2        | INCOMING_ALLY",
			"gang ally reject          | INCOMING_ALLY",
			"gang ally reject 2        | INCOMING_ALLY",
			"gang ally pending cancel 2 | OUTGOING_ALLY",
			"gang invite cancel        | INVITE",
			"gang invite cancel Dave   | INVITE"})
	@DisplayName("a member without the rank node is refused and the pending mail is untouched")
	void leafWithoutRankNode_isRefused(String command, String mailKind) {
		MailItem mail = pending(mailKind);
		String[] args = command.split(" ");

		leaf(args).executeArgument(player, args);

		verify(user).sendMessage(Messages.COMMAND_NO_PERM.toString());
		assertEquals(MailStatus.PENDING, mailManager.findById(mail.getId()).map(MailItem::getStatus).orElse(null));
	}

	@Test
	@DisplayName("a gang cannot send an alliance request to itself")
	void allyRequestToOwnGang_isRefused() {
		when(player.hasPermission(anyString())).thenReturn(true);
		String[] args = {"gang", "ally", "request", String.valueOf(GANG_ID)};

		leaf(args).executeArgument(player, args);

		verify(user).sendMessage(Messages.GANG_CANNOT_ACT_SELF.toString());
		assertTrue(mailManager.getAll().isEmpty(), "no self-alliance request may be queued");
	}

	private MailItem pending(String kind) {
		long now = System.currentTimeMillis();
		MailItem mail = switch (kind) {
			case "INCOMING_ALLY" -> new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, OTHER_ID,
			                                     null, GANG_ID, null, now, now + 60_000, MailStatus.PENDING, false);
			case "OUTGOING_ALLY" -> new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, GANG_ID,
			                                     null, OTHER_ID, null, now, now + 60_000, MailStatus.PENDING, false);
			default -> new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, GANG_ID, DAVE, 0, null, now,
			                        0, MailStatus.PENDING, false);
		};
		mailManager.send(mail);
		return mail;
	}

	/** Walks {@code args} (after "gang") down the literal leaves; a trailing token lands on the optional argument. */
	private Argument leaf(String[] args) {
		Argument current = args[1].equals("invite") ? invite : ally;
		for (int i = 2; i < args.length; i++) {
			String         token    = args[i];
			List<Argument> children = current.getNode().getChildren().stream().map(Tree.Node::getData).toList();
			current = children.stream()
			                  .filter(a -> Arrays.asList(a.getArguments()).contains(token))
			                  .findFirst()
			                  .orElseGet(() -> children.stream()
			                                           .filter(OptionalArgument.class::isInstance)
			                                           .findFirst()
			                                           .orElseThrow(() -> new AssertionError("no leaf for " + token)));
		}
		return current;
	}

	private static Gang gang(int id) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getDisplayNameString()).thenReturn("gang" + id);
		when(gang.getName()).thenReturn("gang" + id);
		return gang;
	}

}
