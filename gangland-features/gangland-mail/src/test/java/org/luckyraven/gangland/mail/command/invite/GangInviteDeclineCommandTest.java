package org.luckyraven.gangland.mail.command.invite;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailMessages;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.mail.support.FakeMailRepositoryContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Pins GR-24: {@code /glw gang decline} rejects the one pending gang invite the player has, and says so when there is
 * none. The decline is a rejection (the invite is removed from storage, status REJECTED).
 */
@DisplayName("GangInviteDeclineCommand - decline a pending gang invite (GR-24)")
class GangInviteDeclineCommandTest {

	private static final int INVITING_GANG = 2;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private BukkitStatics        statics;
	private FakeMailRepositoryContract repo;
	private MailManager          mailManager;
	private UUID                 uuid;
	private Player               player;
	private User<Player>         user;
	private GangInviteDeclineCommand decline;

	@BeforeEach
	void setUp() throws Exception {
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

		statics = BukkitStatics.install();
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		uuid   = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);

		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);

		UserManager<Player> userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		Gang inviting = mock(Gang.class);
		when(inviting.getId()).thenReturn(INVITING_GANG);
		when(inviting.getDisplayNameString()).thenReturn("gang2");
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getGang(INVITING_GANG)).thenReturn(inviting);

		repo        = new FakeMailRepositoryContract();
		mailManager = new MailManager(repo);
		mailManager.initialize();

		JavaPlugin plugin = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, "mail_messages", "mail", ".yml",
		                                    GangInviteDeclineCommandTest.class.getClassLoader()), true);
		MailMessages messages = new MailMessages(fileManager);

		Tree<Argument> tree   = new Tree<>();
		Argument       parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		decline = new GangInviteDeclineCommand(plugin, tree, parent, userManager, mock(MemberManager.class),
		                                       gangManager, mailManager, messages);
	}

	@AfterEach
	void tearDown() {
		statics.close();
	}

	@Test
	@DisplayName("declining with exactly one pending invite rejects it and removes it from storage")
	void decline_withOnePendingInvite_rejectsIt() {
		MailItem invite = new MailItem(mailManager.allocateId(), MailType.GANG_INVITE, null, INVITING_GANG, uuid,
		                               MailItem.NO_GANG, null, System.currentTimeMillis(), 0L, MailStatus.PENDING,
		                               false);
		mailManager.send(invite);

		decline.action().accept(decline, player, new String[]{"gang", "decline"});

		assertEquals(MailStatus.REJECTED, invite.getStatus());
		assertTrue(mailManager.findPendingForRecipient(uuid, MailType.GANG_INVITE).isEmpty());
		assertTrue(repo.deleted().contains(invite));
	}

	@Test
	@DisplayName("declining with no pending invite tells the player there is nothing to decline")
	void decline_withNoPendingInvite_saysNone() {
		decline.action().accept(decline, player, new String[]{"gang", "decline"});

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(user, atLeastOnce()).sendMessage(sent.capture());
		assertTrue(sent.getAllValues().stream().anyMatch(m -> m.contains("no pending gang invitation")),
		           "expected the none-pending line, got " + sent.getAllValues());
	}

}
