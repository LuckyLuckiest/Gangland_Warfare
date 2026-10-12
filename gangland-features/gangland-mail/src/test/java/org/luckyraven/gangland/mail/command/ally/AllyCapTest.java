package org.luckyraven.gangland.mail.command.ally;

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
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
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
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** D17 A: an alliance request or accept is refused while either gang holds the allies its level allows (0 = no cap). */
@DisplayName("Ally cap - Levels.Max_Allies on request and accept")
class AllyCapTest {

	private static final int SENDER = 1;
	private static final int TARGET = 2;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Settings> settings;
	private BukkitStatics          statics;
	private GangMessages           messages;
	private GangLimits             limits;
	private Gang                   sending;
	private Gang                   receiving;
	private Player                 player;
	private Player                 receiverMate;
	private MailManager            mailManager;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;

	@BeforeEach
	void setUp() throws IOException {
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
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		statics = BukkitStatics.install();
		messages = shippedMessages();

		limits = mock(GangLimits.class);

		sending = gang(SENDER, "Vipers", 0);
		receiving = gang(TARGET, "Rats", 0);
		gangManager = mock(GangManager.class);
		when(gangManager.getGang(SENDER)).thenReturn(sending);
		when(gangManager.getGang(TARGET)).thenReturn(receiving);

		UUID playerId = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(playerId);
		when(player.hasPermission(anyString())).thenReturn(true);
		receiverMate = mock(Player.class);
		UUID mateId = UUID.randomUUID();
		when(receiverMate.getUniqueId()).thenReturn(mateId);
		statics.statics().when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of(player, receiverMate));

		memberManager = mock(MemberManager.class);
		Member own = new Member(playerId);
		own.setGangId(SENDER);
		Member mate = new Member(mateId);
		mate.setGangId(TARGET);
		when(memberManager.getMember(playerId)).thenReturn(own);
		when(memberManager.getMember(mateId)).thenReturn(mate);

		@SuppressWarnings("unchecked")
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(SENDER);
		@SuppressWarnings("unchecked")
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);
		userManager = users;

		mailManager = new MailManager(new FakeMailRepositoryContract());
		mailManager.initialize();
	}

	@AfterEach
	void tearDown() {
		settings.close();
		statics.close();
	}

	private GangMessages shippedMessages() throws IOException {
		Path file = tempDir.resolve("gang_messages.yml");
		try (InputStream in = AllyCapTest.class.getResourceAsStream("/gang/gang_messages.yml")) {
			if (in == null) throw new IOException("missing classpath resource /gang/gang_messages.yml");
			Files.write(file, in.readAllBytes());
		}
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangMessages(fileManager);
	}

	/** A gang holding {@code allies} alliances, with a given display name. */
	private Gang gang(int id, String name, int allies) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		Set<GangAlliance> held = new HashSet<>();
		for (int i = 0; i < allies; i++) held.add(mock(GangAlliance.class));
		when(gang.getAllies()).thenReturn(held);
		return gang;
	}

	private String capLine(Gang gang, int cap) {
		return messages.text("Levels.Ally_Cap", "x", "gang", gang.getDisplayNameString(), "cap", cap);
	}

	private void request() {
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		GangAllyRequestCommand command = new GangAllyRequestCommand(mock(JavaPlugin.class), new Tree<>(), parent,
		                                                            userManager, memberManager, gangManager,
		                                                            mailManager, limits, messages);
		Argument id = command.getNode().getChildren().get(0).getData();
		id.executeArgument(player, new String[]{"gang", "ally", "request", String.valueOf(TARGET)});
	}

	private void accept() {
		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		GangAllyAcceptCommand command = new GangAllyAcceptCommand(mock(JavaPlugin.class), new Tree<>(), parent,
		                                                          userManager, memberManager, gangManager,
		                                                          mailManager, limits, messages);
		command.action().accept(command, player, new String[]{"gang", "ally", "accept"});
	}

	private MailItem pendingRequestFromTarget() {
		MailItem mail = new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, TARGET, null, SENDER,
		                             null, System.currentTimeMillis(), 0L, MailStatus.PENDING, false);
		mailManager.send(mail);
		return mail;
	}

	@Test
	@DisplayName("request_refusedAtCap: the asking gang is at its cap, or the asked gang is")
	void request_refusedAtCap() {
		sending = gang(SENDER, "Vipers", 1);
		when(gangManager.getGang(SENDER)).thenReturn(sending);
		when(limits.maxAllies(SENDER)).thenReturn(1);

		request();

		verify(player).sendMessage(capLine(sending, 1));
		assertFalse(mailManager.findPendingBetweenGangs(SENDER, TARGET, MailType.GANG_ALLY_REQUEST).isPresent());

		sending = gang(SENDER, "Vipers", 0);
		when(gangManager.getGang(SENDER)).thenReturn(sending);
		receiving = gang(TARGET, "Rats", 1);
		when(gangManager.getGang(TARGET)).thenReturn(receiving);
		when(limits.maxAllies(SENDER)).thenReturn(1);
		when(limits.maxAllies(TARGET)).thenReturn(1);

		request();

		verify(player).sendMessage(capLine(receiving, 1));
		assertFalse(mailManager.findPendingBetweenGangs(SENDER, TARGET, MailType.GANG_ALLY_REQUEST).isPresent());
	}

	@Test
	@DisplayName("accept_refusedAtCap: no alliance is made and the request stays pending")
	void accept_refusedAtCap() {
		MailItem mail = pendingRequestFromTarget();
		receiving = gang(TARGET, "Rats", 2);
		when(gangManager.getGang(TARGET)).thenReturn(receiving);
		when(limits.maxAllies(TARGET)).thenReturn(2);
		when(limits.maxAllies(SENDER)).thenReturn(3);

		accept();

		verify(player).sendMessage(capLine(receiving, 2));
		verify(sending, never()).addAlly(receiving);
		verify(receiving, never()).addAlly(sending);
		assertEquals(MailStatus.PENDING, mail.getStatus());
	}

	@Test
	@DisplayName("unlimited_allowed: a cap of 0 never refuses, and a gang below its cap may ally")
	void unlimited_allowed() {
		sending = gang(SENDER, "Vipers", 1);
		when(gangManager.getGang(SENDER)).thenReturn(sending);
		when(limits.maxAllies(SENDER)).thenReturn(0);
		when(limits.maxAllies(TARGET)).thenReturn(2);

		request();

		Optional<MailItem> pending = mailManager.findPendingBetweenGangs(SENDER, TARGET, MailType.GANG_ALLY_REQUEST);
		assertTrue(pending.isPresent());
		verify(player, never()).sendMessage(capLine(sending, 0));

		accept();   // the player is in SENDER; nothing is addressed to it, so this only proves no cap line appears
		verify(player, never()).sendMessage(capLine(sending, 1));
	}
}
