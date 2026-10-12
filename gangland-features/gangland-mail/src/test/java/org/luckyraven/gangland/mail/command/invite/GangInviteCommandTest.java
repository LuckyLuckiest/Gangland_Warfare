package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
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
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailRules;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * T-208: inviting an online player tells the inviter only the invite-sent line; the expiry line goes to the target.
 */
@DisplayName("GangInviteCommand - expiry line goes to the target, not the inviter (T-208)")
class GangInviteCommandTest {

	private static final int    INVITER_GANG = 1;
	private static final String TARGET_NAME  = "Bob";

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;
	private User<Player>           inviter;
	private Player                 inviterPlayer;
	private Player                 targetPlayer;
	private GangInviteCommand      invite;

	@BeforeEach
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
		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		statics = BukkitStatics.install();
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		UUID targetId = UUID.randomUUID();
		inviterPlayer = mock(Player.class);
		when(inviterPlayer.hasPermission(anyString())).thenReturn(true);
		targetPlayer  = mock(Player.class);
		when(targetPlayer.getUniqueId()).thenReturn(targetId);
		when(targetPlayer.getName()).thenReturn(TARGET_NAME);
		statics.statics().when(() -> Bukkit.getPlayer(targetId)).thenReturn(targetPlayer);

		@SuppressWarnings("unchecked")
		User<Player> targetUser = mock(User.class);
		when(targetUser.getUser()).thenReturn(targetPlayer);
		when(targetUser.hasGang()).thenReturn(false);

		@SuppressWarnings("unchecked")
		UserManager<Player> userManager = mock(UserManager.class);
		inviter = mock(User.class);
		when(inviter.getUser()).thenReturn(inviterPlayer);
		when(inviter.hasGang()).thenReturn(true);
		when(inviter.getGangId()).thenReturn(INVITER_GANG);
		when(userManager.getUser(inviterPlayer)).thenReturn(inviter);
		when(userManager.getUsers()).thenReturn(Map.of(targetId, targetUser));

		Gang gang = mock(Gang.class);
		when(gang.getDisplayNameString()).thenReturn("gang1");
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(INVITER_GANG)).thenReturn(gang);

		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		try {
			mailManager.initialize();
		} catch (Exception exception) {
			throw new IllegalStateException(exception);
		}

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		invite = new GangInviteCommand(mock(JavaPlugin.class), new Tree<>(), parent, userManager,
		                               mock(UserManager.class), mock(MemberManager.class), gangManager,
		                               mock(RankManager.class), mailManager, mock(MailRules.class),
		                               mock(MailMessages.class), mock(GangMembershipService.class));
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
	}

	@Test
	@DisplayName("T-208: an online invite sends the expiry line to the target only")
	void onlineInvite_expiryLineOnlyToTarget() {
		optionalChild(invite).executeArgument(inviterPlayer, new String[]{"gang", "invite", TARGET_NAME});

		String expiry = Messages.GANG_INVITE_EXPIRES_ONLINE.toString();
		ArgumentCaptor<String> toInviter = ArgumentCaptor.forClass(String.class);
		verify(inviter, atLeastOnce()).sendMessage(toInviter.capture());
		assertFalse(toInviter.getAllValues().contains(expiry),
		            "the inviter must not get the expiry line: " + toInviter.getAllValues());

		verify(targetPlayer).sendMessage(expiry);
	}

	private static Argument optionalChild(Argument parent) {
		return parent.getNode().getChildren().stream().map(Tree.Node::getData)
		             .filter(OptionalArgument.class::isInstance).findFirst().orElseThrow();
	}

}
