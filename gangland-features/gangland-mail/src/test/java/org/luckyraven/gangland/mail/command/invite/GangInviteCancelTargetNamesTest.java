package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Characterises how {@code /glw gang invite cancel <player>} names the recipients of the gang's pending invites in its
 * tab completion: online users first, then offline users, then {@code Bukkit.getOfflinePlayer(uuid)}; an invite with no
 * recipient or an unnamed fallback is left out.
 */
@DisplayName("GangInviteCancelCommand - recipient names offered for cancelling an invite")
class GangInviteCancelTargetNamesTest {

	private static final int GANG = 7;

	private BukkitStatics statics;

	@BeforeEach
	void setUp() {
		statics = BukkitStatics.install();
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	@Test
	@DisplayName("names come from the online users, then the offline users, then the Bukkit fallback; unnamed ones are skipped")
	@SuppressWarnings("unchecked")
	void tabCompletion_resolvesNamesInOrderOfSource() {
		UUID onlineId = UUID.randomUUID();
		UUID offlineId = UUID.randomUUID();
		UUID fallbackId = UUID.randomUUID();
		UUID unnamedId = UUID.randomUUID();
		UUID strangerId = UUID.randomUUID();

		Player online = mock(Player.class);
		when(online.getUniqueId()).thenReturn(onlineId);
		when(online.getName()).thenReturn("Onyx");
		User<Player> onlineUser = mock(User.class);
		when(onlineUser.getUser()).thenReturn(online);

		// the same uuid is also cached offline under another name: the online user wins
		OfflinePlayer shadow = mock(OfflinePlayer.class);
		when(shadow.getUniqueId()).thenReturn(onlineId);
		when(shadow.getName()).thenReturn("Shadow");
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getUniqueId()).thenReturn(offlineId);
		when(offline.getName()).thenReturn("Olive");
		OfflinePlayer unnamedCached = mock(OfflinePlayer.class);
		when(unnamedCached.getUniqueId()).thenReturn(unnamedId);
		when(unnamedCached.getName()).thenReturn(null);
		User<OfflinePlayer> shadowUser = mock(User.class);
		when(shadowUser.getUser()).thenReturn(shadow);
		User<OfflinePlayer> offlineUser = mock(User.class);
		when(offlineUser.getUser()).thenReturn(offline);
		User<OfflinePlayer> unnamedUser = mock(User.class);
		when(unnamedUser.getUser()).thenReturn(unnamedCached);

		UserManager<Player> users = mock(UserManager.class);
		when(users.getUsers()).thenReturn(Map.of(onlineId, onlineUser));
		UserManager<OfflinePlayer> offlineUsers = mock(UserManager.class);
		when(offlineUsers.getUsers()).thenReturn(Map.of(onlineId, shadowUser, offlineId, offlineUser, unnamedId,
		                                                unnamedUser));

		OfflinePlayer fallback = mock(OfflinePlayer.class);
		when(fallback.getName()).thenReturn("Fern");
		OfflinePlayer nameless = mock(OfflinePlayer.class);
		when(nameless.getName()).thenReturn(null);
		statics.statics().when(() -> Bukkit.getOfflinePlayer(fallbackId)).thenReturn(fallback);
		statics.statics().when(() -> Bukkit.getOfflinePlayer(strangerId)).thenReturn(nameless);
		statics.statics().when(() -> Bukkit.getOfflinePlayer(unnamedId)).thenReturn(nameless);

		MailManager mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
		for (UUID recipient : new UUID[]{onlineId, offlineId, fallbackId, unnamedId, strangerId, null}) {
			mailManager.send(new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, GANG, recipient,
			                              MailItem.NO_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING,
			                              false));
		}

		@SuppressWarnings("unchecked")
		User<Player> senderUser = mock(User.class);
		when(senderUser.hasGang()).thenReturn(true);
		when(senderUser.getGangId()).thenReturn(GANG);
		Player sender = mock(Player.class);
		when(users.getUser(sender)).thenReturn(senderUser);

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		GangInviteCancelCommand cancel = new GangInviteCancelCommand(mock(JavaPlugin.class), new Tree<>(), parent,
		                                                             users, offlineUsers, mock(MemberManager.class),
		                                                             mailManager);
		OptionalArgument target = (OptionalArgument) cancel.getNode().getChildren().stream().map(Tree.Node::getData)
		                                                   .filter(OptionalArgument.class::isInstance).findFirst()
		                                                   .orElseThrow();

		// "Shadow" would be offered if the offline cache beat the online user; an unnamed recipient is "?" and dropped
		assertEquals(Set.of("Onyx", "Olive", "Fern"), new HashSet<>(target.getArgumentString(sender)));
		assertEquals(List.of(""), target.getArgumentString(mock(Player.class)));
	}

}
