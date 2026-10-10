package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * GR-10: a {@code gang transfer} request expires after the 30 s confirm window (the Keystone confirm lock lapses at 30 s), like {@code gang leave}.
 */
@DisplayName("GangTransferCommand - confirm window")
class GangTransferCommandTest {

	private static final long CONFIRM_WINDOW_TICKS = 20L * 30L;

	private BukkitStatics          bukkit;
	private MockedStatic<Settings> settings;
	private UserManager<Player> userManager;
	private MemberManager       memberManager;
	private RankManager         rankManager;
	private Tree<Rank>          rankTree;

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
		bukkit   = BukkitStatics.install();
		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		userManager   = mock(UserManager.class);
		memberManager = mock(MemberManager.class);
		rankManager   = mock(RankManager.class);
		rankTree      = new Tree<>();
		when(rankManager.getRankTree()).thenReturn(rankTree);
	}

	@AfterEach
	void tearDown() {
		settings.close();
		bukkit.close();
	}

	@Test
	@DisplayName("request_thenWindowExpires_unlocksAndForgetsTarget: the lock and the pending target both lapse at 30 s")
	void request_thenWindowExpires_unlocksAndForgetsTarget() {
		Player owner  = mock(Player.class);
		Player target = mock(Player.class);
		UUID   ownerId  = UUID.randomUUID();
		UUID   targetId = UUID.randomUUID();
		when(owner.getUniqueId()).thenReturn(ownerId);

		User<Player> ownerUser = mock(User.class);
		when(userManager.getUser(owner)).thenReturn(ownerUser);
		when(ownerUser.hasGang()).thenReturn(true);
		when(ownerUser.getGangId()).thenReturn(1);

		Rank member = rank("member", 2);
		Rank tail   = rank("owner", 1);
		member.getNode().add(tail.getNode());
		rankTree.add(member.getNode());

		Member ownerMember  = new Member(ownerId);
		Member targetMember = new Member(targetId);
		ownerMember.setRank(tail);
		targetMember.setRank(member);
		when(memberManager.getMember(ownerId)).thenReturn(ownerMember);
		when(memberManager.getMember(targetId)).thenReturn(targetMember);

		OfflinePlayer ownerOffline  = mock(OfflinePlayer.class);
		OfflinePlayer targetOffline = mock(OfflinePlayer.class);
		when(ownerOffline.getName()).thenReturn("Ann");
		when(targetOffline.getName()).thenReturn("Bob");
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(ownerId)).thenReturn(ownerOffline);
		bukkit.statics().when(() -> Bukkit.getOfflinePlayer(targetId)).thenReturn(targetOffline);

		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(1);
		when(gang.getMembers()).thenReturn(List.of(ownerMember, targetMember));
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(1)).thenReturn(gang);

		JavaPlugin plugin = mock(JavaPlugin.class);
		GangTransferCommand command = new GangTransferCommand(plugin, new Tree<>(), mock(Argument.class),
		                                                      userManager, memberManager, gangManager, rankManager);

		OptionalArgument targetArgument = null;
		ConfirmArgument  confirm        = null;
		for (Tree.Node<Argument> child : command.getNode().getChildren()) {
			if (child.getData() instanceof OptionalArgument optional) targetArgument = optional;
			if (child.getData() instanceof ConfirmArgument confirmArgument) confirm = confirmArgument;
		}
		assertTrue(targetArgument != null && confirm != null, "transfer registers a confirm and a target argument");

		targetArgument.executeArgument(owner, new String[]{"gang", "transfer", "Bob"});
		assertTrue(confirm.isLocked(owner), "the request locks the confirm step");

		ArgumentCaptor<Runnable> expiry = ArgumentCaptor.forClass(Runnable.class);
		verify(bukkit.scheduler()).runTaskLater(any(Plugin.class), expiry.capture(), eq(CONFIRM_WINDOW_TICKS));

		expiry.getValue().run();

		assertFalse(confirm.isLocked(owner), "the window expired, so the confirm lock is released");

		// Forgotten target: a confirm arriving after expiry must not transfer anything.
		confirm.lock(owner);
		confirm.executeArgument(owner, new String[]{"gang", "transfer", "confirm"});

		verify(memberManager, never()).assignRank(any(), any());
		assertSame(member, targetMember.getRank(), "the target keeps the rank they had");
	}

	private Rank rank(String name, int id) {
		Rank rank = new Rank(name, id);
		when(rankManager.get(name)).thenReturn(rank);
		return rank;
	}

}
