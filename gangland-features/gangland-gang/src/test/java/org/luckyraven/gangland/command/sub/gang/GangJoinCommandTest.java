package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.*;

/** {@code /glw gang join <gang>}: only an OPEN gang takes a gangless player, at the head rank (W2-P2). */
@DisplayName("GangJoinCommand - /glw gang join <gang> for open gangs")
class GangJoinCommandTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;
	private RankManager            rankManager;
	private Player                 player;
	private UUID                   playerId;
	private User<Player>           user;
	private Member                 member;
	private Rank                   head;
	private final List<Gang>       gangs = new ArrayList<>();

	@BeforeEach
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
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
		settings.when(Settings::getLanguagePicked).thenReturn("en");

		pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = W2P2Messages.shipped(tempDir);

		gangManager   = mock(GangManager.class);
		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		rankManager   = mock(RankManager.class);

		head = new Rank("member", 1);
		when(rankManager.get(anyString())).thenReturn(head);
		when(gangManager.getAll()).thenAnswer(invocation -> gangs);

		playerId = UUID.randomUUID();
		player   = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.getName()).thenReturn("Rex");

		user = user(false);
		when(userManager.getUser(player)).thenReturn(user);
		member = mock(Member.class);
		when(memberManager.getMember(playerId)).thenReturn(member);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("an open gang takes a gangless player at the head rank, stamps the join date and tells its members")
	void openGang_joinsAtHeadRank() throws Exception {
		Gang vipers = gang("Vipers", Gang.State.OPEN);
		User<Player> mate = user(true);
		when(vipers.getOnlineMembers(any())).thenReturn(List.of(mate));
		gangs.add(vipers);
		long before = System.currentTimeMillis();

		nameArgument().executeArgument(player, new String[]{"gang", "join","Vipers"});

		verify(vipers).addMember(user, member, head);
		verify(member).setGangJoinDateLong(longThat(at -> at >= before && at <= System.currentTimeMillis()));
		verify(mate).sendMessage(Messages.GANG_PLAYER_JOINED.toString().replace("%player%", player.getName()));
		verify(player).sendMessage(messages.text("Social.Joined", "&aYou joined %gang%.", "gang", "Vipers"));
	}

	@Test
	@DisplayName("joining an open gang fires GangJoinEvent so the mail module drops the other invites (T-265)")
	void openGang_firesGangJoinEvent() throws Exception {
		Gang vipers = gang("Vipers", Gang.State.OPEN);
		gangs.add(vipers);

		nameArgument().executeArgument(player, new String[]{"gang", "join", "Vipers"});

		verify(pluginManager).callEvent(argThat((Event event) -> event instanceof GangJoinEvent joined
		                                                         && joined.getGang() == vipers
		                                                         && joined.getPlayer() == player));
	}

	@Test
	@DisplayName("a typed colour code in an unknown name is not rendered in the refusal (T-257)")
	void unknownName_typedColourCodesNotRendered() throws Exception {
		nameArgument().executeArgument(player, new String[]{"gang", "join", "&kGhost"});

		verify(player).sendMessage(messages.text("Social.Unknown", "&cNo gang named %gang%.", "gang", "Ghost"));
	}

	@Test
	@DisplayName("a console sender gets the not-a-player reply, not the no-permission one (T-280)")
	void console_getsNotPlayer() throws Exception {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);

		nameArgument().executeArgument(console, new String[]{"gang", "join", "Vipers"});

		verify(console).sendMessage(Messages.NOT_PLAYER.toString());
		verify(console, never()).sendMessage(Messages.COMMAND_NO_PERM.toString());
	}

	@Test
	@DisplayName("an invite-only gang refuses the join and changes nothing")
	void inviteGang_refused() throws Exception {
		Gang kings = gang("Kings", Gang.State.INVITE);
		gangs.add(kings);

		nameArgument().executeArgument(player, new String[]{"gang", "join","Kings"});

		verify(player).sendMessage(messages.text("Social.Invite_Only", "&c%gang% is invite only.", "gang", "Kings"));
		verify(kings, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
	}

	@Test
	@DisplayName("a player already in a gang is refused")
	void alreadyInGang_refused() throws Exception {
		Gang vipers = gang("Vipers", Gang.State.OPEN);
		gangs.add(vipers);
		User<Player> inGang = user(true);
		when(userManager.getUser(player)).thenReturn(inGang);

		nameArgument().executeArgument(player, new String[]{"gang", "join","Vipers"});

		verify(player).sendMessage(messages.text("Social.Already_In_Gang", "&cLeave your gang first."));
		verify(vipers, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
	}

	@Test
	@DisplayName("a name no gang has is refused")
	void unknownName_refused() throws Exception {
		nameArgument().executeArgument(player, new String[]{"gang", "join","Nope"});

		verify(player).sendMessage(messages.text("Social.Unknown", "&cNo gang named %gang%.", "gang", "Nope"));
	}

	@Test
	@DisplayName("an open gang with no head rank is refused with a warning, not joined")
	void noHeadRank_refused() throws Exception {
		Gang vipers = gang("Vipers", Gang.State.OPEN);
		gangs.add(vipers);
		when(rankManager.get(anyString())).thenReturn(null);

		nameArgument().executeArgument(player, new String[]{"gang", "join","Vipers"});

		verify(player).sendMessage(messages.text("Social.No_Rank", "&cThis gang has no starting rank; ask an admin."));
		verify(vipers, never()).addMember(any(User.class), any(Member.class), any(Rank.class));
	}

	@Test
	@DisplayName("the name argument completes to open gangs only")
	void nameArgument_completesOpenGangsOnly() {
		gangs.add(gang("Vipers", Gang.State.OPEN));
		gangs.add(gang("Kings", Gang.State.INVITE));

		assertEquals(List.of("Vipers"), nameArgument().getArgumentString(player));
	}

	private Argument join() {
		return new GangJoinCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                           memberManager, gangManager, rankManager, messages);
	}

	private Argument nameArgument() {
		return join().getNode().getChildren().get(0).getData();
	}

	private Gang gang(String name, Gang.State state) {
		Gang gang = mock(Gang.class);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gang.getState()).thenReturn(state);
		when(gang.getOnlineMembers(any())).thenReturn(List.of());
		return gang;
	}

	@SuppressWarnings("unchecked")
	private static User<Player> user(boolean hasGang) {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(hasGang);
		return user;
	}

}
