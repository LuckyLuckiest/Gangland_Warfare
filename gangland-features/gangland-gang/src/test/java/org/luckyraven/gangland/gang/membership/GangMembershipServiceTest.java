package org.luckyraven.gangland.gang.membership;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.events.gang.GangJoinEvent;
import org.luckyraven.gangland.events.gangs.GangCreatedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberJoinedEvent;
import org.luckyraven.gangland.events.gangs.GangMemberLeftEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService.JoinSource;
import org.luckyraven.gangland.gang.membership.GangMembershipService.Result;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.gang.support.MembershipKit;
import org.luckyraven.gangland.gang.support.W2P2Messages;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The one membership path: cap, tag refusal, console tokens and exactly one api event per change. */
@DisplayName("GangMembershipService - join, leave and kick")
class GangMembershipServiceTest {

	private static final int GANG_ID = 7;

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private MembershipKit          kit;
	private GangManager            gangManager;
	private MemberManager          memberManager;
	private UserManager<Player>    userManager;
	private Rank                   head;
	private Rank                   tail;
	private Gang                   gang;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());
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
		settings.when(Settings::getLanguagePicked).thenReturn("en");

		pluginManager = mock(PluginManager.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		gangManager   = mock(GangManager.class);
		memberManager = mock(MemberManager.class);
		userManager   = mock(UserManager.class);
		RankManager rankManager = mock(RankManager.class);

		head = new Rank("member", 1);
		tail = new Rank("owner", 2);
		when(rankManager.get("member")).thenReturn(head);
		when(rankManager.get("owner")).thenReturn(tail);
		Tree<Rank> tree = mock(Tree.class);
		when(tree.isDescendant(any(), any())).thenReturn(true);
		when(rankManager.getRankTree()).thenReturn(tree);

		gang = new Gang(GANG_ID);
		gang.setName("Vipers");
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);

		kit = new MembershipKit(gangManager, memberManager, rankManager, userManager, mock(UserManager.class),
		                        mock(RepositoryRegistry.class), W2P2Messages.shipped(tempDir));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("everyJoinAndLeaveSite_firesOneEventWithReason")
	void everyJoinAndLeaveSite_firesOneEventWithReason() {
		Player creator = player("Ann");
		assertTrue(kit.service.join(GANG_ID, creator.getUniqueId(), JoinSource.CREATE).ok());
		assertEquals(1, events(GangCreatedEvent.class).size());
		assertEquals(1, events(GangMemberJoinedEvent.class).size());
		assertEquals(0, events(GangJoinEvent.class).size(), "create is not a gang-join");

		clearInvocations(pluginManager);
		Player invited = player("Bob");
		assertTrue(kit.service.join(GANG_ID, invited.getUniqueId(), JoinSource.INVITE).ok());
		assertEquals(1, events(GangMemberJoinedEvent.class).size());
		assertEquals(0, events(GangJoinEvent.class).size(), "invite accept cancels siblings itself");

		clearInvocations(pluginManager);
		Player joined = player("Cy");
		assertTrue(kit.service.join(GANG_ID, joined.getUniqueId(), JoinSource.JOIN).ok());
		assertEquals(1, events(GangMemberJoinedEvent.class).size());
		assertEquals(1, events(GangJoinEvent.class).size(), "/gang join keeps the module event for mail");

		clearInvocations(pluginManager);
		assertTrue(kit.service.leave(joined.getUniqueId(), GangMemberLeftEvent.Reason.LEFT).ok());
		List<GangMemberLeftEvent> left = events(GangMemberLeftEvent.class);
		assertEquals(1, left.size());
		assertEquals(GangMemberLeftEvent.Reason.LEFT, left.get(0).reason());

		clearInvocations(pluginManager);
		memberOf(creator).setRank(tail);
		memberOf(invited).setRank(head);
		assertTrue(kit.service.kick(GANG_ID, creator.getUniqueId(), invited.getUniqueId()).ok());
		left = events(GangMemberLeftEvent.class);
		assertEquals(1, left.size());
		assertEquals(GangMemberLeftEvent.Reason.KICKED, left.get(0).reason());
		assertEquals(invited.getUniqueId(), left.get(0).player());
	}

	@Test
	@DisplayName("cap_refusesNPlusOne_atAcceptAndJoin")
	void cap_refusesNPlusOne_atAcceptAndJoin() {
		kit.cap = 2;
		join(player("Ann"), JoinSource.INVITE);
		join(player("Bob"), JoinSource.JOIN);

		Player late = player("Cy");
		Result invite = kit.service.join(GANG_ID, late.getUniqueId(), JoinSource.INVITE);
		Result join   = kit.service.join(GANG_ID, late.getUniqueId(), JoinSource.JOIN);

		assertFalse(invite.ok());
		assertFalse(join.ok());
		assertEquals("Membership.Full", join.messageKey());
		assertEquals("Vipers is full (2/2).", ChatColor.stripColor(kit.service.text(join)));
		assertEquals(2, gang.getMembers().size());
	}

	@Test
	@DisplayName("overCapGang_keepsEveryone")
	void overCapGang_keepsEveryone() {
		join(player("Ann"), JoinSource.INVITE);
		join(player("Bob"), JoinSource.INVITE);
		Player cy = player("Cy");
		join(cy, JoinSource.INVITE);

		kit.cap = 1;

		assertEquals(3, gang.getMembers().size(), "lowering the cap removes nobody");
		assertFalse(kit.service.join(GANG_ID, player("Di").getUniqueId(), JoinSource.JOIN).ok());
		assertTrue(kit.service.leave(cy.getUniqueId(), GangMemberLeftEvent.Reason.LEFT).ok(), "members may still leave");
		assertEquals(2, gang.getMembers().size());
	}

	@Test
	@DisplayName("taggedPlayer_cannotLeave")
	void taggedPlayer_cannotLeave() {
		Player ann = player("Ann");
		join(ann, JoinSource.INVITE);
		kit.taggedFor = Duration.ofSeconds(12);

		Result result = kit.service.leave(ann.getUniqueId(), GangMemberLeftEvent.Reason.LEFT);

		assertFalse(result.ok());
		assertEquals("You are in a fight: wait 12 s.", ChatColor.stripColor(kit.service.text(result)));
		assertEquals(1, gang.getMembers().size(), "still a member");
		assertEquals(0, events(GangMemberLeftEvent.class).size());

		kit.taggedFor = Duration.ZERO;
		assertTrue(kit.service.leave(ann.getUniqueId(), GangMemberLeftEvent.Reason.LEFT).ok());
	}

	@Test
	@DisplayName("consoleTokens_asciiNameElseId_neverDisplayName")
	void consoleTokens_asciiNameElseId_neverDisplayName() {
		when(kit.rules.getJoinCommands()).thenReturn(List.of("say %gang% %gang_id% %player%"));
		when(kit.rules.getLeaveCommands()).thenReturn(List.of("say bye %gang% %player%"));

		Player rex = player("Rex");
		join(rex, JoinSource.INVITE);
		assertEquals(List.of("say Vipers 7 Rex"), kit.console);

		kit.console.clear();
		gang.setName("Ñandú");
		Player zed = player("Zed");
		join(zed, JoinSource.INVITE);
		assertEquals(List.of("say 7 7 Zed"), kit.console, "a non-ASCII name falls back to the id");

		kit.console.clear();
		assertTrue(kit.service.leave(zed.getUniqueId(), GangMemberLeftEvent.Reason.LEFT).ok());
		assertEquals(List.of("say bye 7 Zed"), kit.console);
	}

	private void join(Player player, JoinSource source) {
		assertTrue(kit.service.join(GANG_ID, player.getUniqueId(), source).ok());
	}

	private Member memberOf(Player player) {
		return memberManager.getMember(player.getUniqueId());
	}

	/** An online, gangless player with a cached user and member. */
	@SuppressWarnings("unchecked")
	private Player player(String name) {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getName()).thenReturn(name);
		when(player.hasPermission(any(String.class))).thenReturn(true);
		bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn(name);
		bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);

		User<Player> user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenAnswer(i -> memberManager.getMember(uuid).getGangId() != -1);
		when(user.getGangId()).thenAnswer(i -> memberManager.getMember(uuid).getGangId());
		when(userManager.getUser(player)).thenReturn(user);

		Member member = new Member(uuid);
		when(memberManager.getMember(uuid)).thenReturn(member);
		return player;
	}

	private <T extends Event> List<T> events(Class<T> type) {
		ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
		verify(pluginManager, atLeast(0)).callEvent(captor.capture());
		List<T> found = new ArrayList<>();
		for (Event event : captor.getAllValues()) {
			if (type.isInstance(event)) found.add(type.cast(event));
		}
		return found;
	}

}
