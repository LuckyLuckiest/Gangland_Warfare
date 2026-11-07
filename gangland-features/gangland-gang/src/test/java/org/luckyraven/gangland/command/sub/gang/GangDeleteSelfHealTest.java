package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.support.MembershipKit;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.util.TimeMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pins the self-heal of {@code /glw gang delete} when the player's gang row is gone: the player and member are
 * reset, the contribution zeroed, the rank cleared and the must-create-a-gang line sent, in that order.
 */
@DisplayName("GangDeleteCommand - a player whose gang row is gone")
class GangDeleteSelfHealTest {

	private static final int        GANG_ID = 42;
	private static final BigDecimal FEE     = new BigDecimal("400");  // refund is a quarter: 100
	private static final BigDecimal REFUND  = new BigDecimal("100");

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private final List<Player>            online   = new ArrayList<>();
	private       MockedStatic<Bukkit>    bukkit;
	private       MockedStatic<Settings>  settings;
	private       JavaPlugin              plugin;
	private       BukkitScheduler         scheduler;
	private       PluginManager           pluginManager;
	private       UserManager<Player>        userManager;
	private       UserManager<OfflinePlayer> offlineUserManager;
	private       MemberManager           memberManager;
	private       GangManager             gangManager;
	private       RepositoryRegistry      repositoryRegistry;
	private       IRepository<Object>     userRepository;
	private       Rank                    ownerRank;
	private       Rank                    memberRank;
	private       Gang                    gang;
	private       RankManager             rankManager;
	private       Argument                parent;
	private       GangDeleteCommand       command;
	private       User<Player>            user;
	private       Member                  member;
	private       MembershipKit           kit;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(FEE));
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				if (path.equals(Messages.GANG_REMOVE_CONFIRM.getPath())) return "%timer%";
				if (path.equals(Messages.MINUTE.getPath())) return "m";
				if (path.equals(Messages.SECOND.getPath())) return "s";
				if (path.equals(Messages.KICKED_FROM_GANG.getPath())) {
					return "KICKED";
				}
				if (path.equals(Messages.GANG_REMOVED.getPath())) {
					return "REMOVED %gang%";
				}
				if (path.equals(Messages.DEPOSIT_MONEY_PLAYER.getPath())) {
					return "DEPOSITED %amount%";
				}
				return "";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		TimeMessages.initialize();

		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(
				mock(BukkitTask.class));
		pluginManager = mock(PluginManager.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i -> online);
		bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(i -> {
			OfflinePlayer offline = mock(OfflinePlayer.class);
			when(offline.getUniqueId()).thenReturn(i.getArgument(0));
			return offline;
		});

		plugin             = mock(JavaPlugin.class);
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		memberManager      = mock(MemberManager.class);
		gangManager        = mock(GangManager.class);
		repositoryRegistry = mock(RepositoryRegistry.class);
		userRepository     = mock(IRepository.class);
		rankManager = mock(RankManager.class);

		ownerRank  = new Rank("owner", 1);
		memberRank = new Rank("member", 2);
		when(rankManager.get("owner")).thenReturn(ownerRank);

		when(repositoryRegistry.getRepository(Member.class)).thenReturn(mock(IRepository.class));
		when(repositoryRegistry.getRepository(Gang.class)).thenReturn(mock(IRepository.class));
		when(repositoryRegistry.getRepository(GangAlliance.class)).thenReturn(mock(IRepository.class));
		doReturn(userRepository).when(repositoryRegistry).getGenericRepository(User.class);

		gang = new Gang(GANG_ID);
		gang.setName("Crew");
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);

		parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		command = commandWith(mock(GangMessages.class));
	}

	private GangDeleteCommand commandWith(GangMessages messages) {
		kit = new MembershipKit(gangManager, memberManager, rankManager, userManager, offlineUserManager,
		                                      repositoryRegistry, messages);
		return new GangDeleteCommand(plugin, new Tree<>(), parent, userManager, memberManager, gangManager,
		                             rankManager, kit.service);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("the delete hint resets the player and member in order and sends the must-create line")
	void hint_healsAPlayerWhoseGangIsGone() {
		Player player = ownerPlayer();
		when(gangManager.getGang(GANG_ID)).thenReturn(null);

		command.executeArgument(player, new String[]{"gang", "delete"});

		assertHealed(player);
	}

	@Test
	@DisplayName("the delete confirm resets the player and member in order and sends the must-create line")
	void confirm_healsAPlayerWhoseGangIsGone() {
		Player player = ownerPlayer();
		command.executeArgument(player, new String[]{"gang", "delete"});
		clearInvocations(user, member, memberManager);
		when(gangManager.getGang(GANG_ID)).thenReturn(null);

		ConfirmArgument confirm = (ConfirmArgument) command.getNode().getChildren().get(0).getData();
		confirm.executeArgument(player, new String[]{"gang", "delete", "confirm"});

		assertHealed(player);
	}

	private void assertHealed(Player player) {
		InOrder order = inOrder(user, member, memberManager);
		order.verify(user).resetGang();
		order.verify(member).resetGang();
		order.verify(member).setContribution(0D);
		order.verify(memberManager).assignRank(member, null);
		order.verify(user).sendMessage(Messages.MUST_CREATE_GANG.toString());
		verify(gangManager, never()).remove(any(Gang.class));
		verify(user, never()).sendMessage(Messages.NOT_OWNER.toString().replace("%tail%", GangSettings.getGangRankTail()));
	}

	@SuppressWarnings("unchecked")
	private Player ownerPlayer() {
		// every message distinct, so the line a test expects cannot be confused with another one
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				return path.equals(Messages.GANG_REMOVE_CONFIRM.getPath()) ? "%timer%" : path;
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(GANG_ID);
		when(userManager.getUser(player)).thenReturn(user);
		member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(member.getRank()).thenReturn(ownerRank);
		when(memberManager.getMember(uuid)).thenReturn(member);
		when(rankManager.get(anyString())).thenReturn(ownerRank);
		return player;
	}

}
