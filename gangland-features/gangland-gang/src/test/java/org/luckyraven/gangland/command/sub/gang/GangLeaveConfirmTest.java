package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** {@code /glw gang leave}: the first run only asks for a confirm, the second one within the window leaves. */
@DisplayName("GangLeaveCommand - leave asks to confirm before the member leaves")
class GangLeaveConfirmTest {

	private Argument               leave;
	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private Player                 player;
	private User<Player>           user;
	private Member                 member;
	private Gang                   gang;

	@BeforeEach
	void setUp() {
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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getScheduler).thenReturn(mock(BukkitScheduler.class));

		Command.setInformationManager(mock(InformationManager.class));

		UUID uuid = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.hasPermission(anyString())).thenReturn(true);
		when(player.getName()).thenReturn("Carl");

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(5);

		Rank memberRank = mock(Rank.class);
		member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(member.getRank()).thenReturn(memberRank);

		gang = mock(Gang.class);
		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		MemberManager memberManager = mock(MemberManager.class);
		when(memberManager.getMember(uuid)).thenReturn(member);
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(5)).thenReturn(gang);
		RankManager rankManager = mock(RankManager.class);
		when(rankManager.get(anyString())).thenReturn(mock(Rank.class));

		leave = new GangLeaveCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                             memberManager, gangManager, rankManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the first leave asks for a confirm and the member stays in the gang")
	void firstLeave_asksToConfirm_memberStays() {
		leave.executeArgument(player, new String[]{"gang", "leave"});

		verify(player).sendMessage(Messages.ARGUMENT_CONFIRM_HINT.toString());
		verify(gang, never()).removeMember(any(User.class), any(Member.class));
	}

	@Test
	@DisplayName("the confirm (a second leave) completes the leave")
	void confirm_completesLeave() {
		leave.executeArgument(player, new String[]{"gang", "leave"});
		leave.executeArgument(player, new String[]{"gang", "leave"});

		verify(gang).removeMember(user, member);
		verify(user).sendMessage(Messages.GANG_LEAVE.toString());
	}

}
