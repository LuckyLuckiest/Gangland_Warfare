package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.command.option.GangOptionContribution;
import org.luckyraven.gangland.gang.command.option.GangStaffVerbs;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.purge.GangPurgeTask;
import org.luckyraven.gangland.gang.purge.PurgeCandidate;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W5-A: the staff verbs under {@code /glw option gang} run from the console, every bank write is logged with actor,
 * gang and amount, and the rank verb never casts the sender to a player (GR-42, the console half).
 */
@DisplayName("/glw option gang - staff verbs (GP-01/03/14/22/24/28/40, GR-42)")
class GangStaffVerbsTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;

	private Gang                  rats;
	private GangMembershipService membership;
	private GangSafehouseService  safehouses;
	private GangStatsService      stats;
	private ChatSpyService        spy;
	private GangPurgeTask         purge;
	private CommandSender         console;
	private List<String>          audit;
	private List<String>          sent;
	private UUID                  boss;
	private Argument              gangArgument;

	@BeforeEach
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
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		boss = UUID.randomUUID();
		OfflinePlayer bossPlayer = mock(OfflinePlayer.class);
		when(bossPlayer.getName()).thenReturn("Boss");
		when(bossPlayer.getUniqueId()).thenReturn(boss);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(() -> Bukkit.getOfflinePlayer(boss)).thenReturn(bossPlayer);
		bukkit.when(() -> Bukkit.getOfflinePlayer("Boss")).thenReturn(bossPlayer);

		rats = new Gang(7);
		rats.setName("Rats");
		rats.addMember(new Member(boss));
		GangManager gangManager = mock(GangManager.class);
		when(gangManager.getAll()).thenReturn(List.of(rats));
		when(gangManager.getGang(7)).thenReturn(rats);

		membership = mock(GangMembershipService.class);
		when(membership.disband(anyInt(), any(), anyBoolean())).thenReturn(GangMembershipService.Result.success());
		when(membership.setLeader(anyInt(), any(), any(), anyBoolean())).thenReturn(
				GangMembershipService.Result.success());
		safehouses = mock(GangSafehouseService.class);
		stats      = mock(GangStatsService.class);
		spy        = new ChatSpyService();
		purge      = mock(GangPurgeTask.class);

		sent    = new ArrayList<>();
		console = mock(CommandSender.class);
		when(console.hasPermission(anyString())).thenReturn(true);
		when(console.getName()).thenReturn("CONSOLE");
		doAnswer(call -> sent.add(call.getArgument(0))).when(console).sendMessage(anyString());

		audit = new ArrayList<>();
		GangMessages messages = GangTestMessages.shipped(tempDir);
		GangStaffVerbs verbs = new GangStaffVerbs(gangManager, mock(MemberManager.class), membership, safehouses,
		                                          stats, spy, purge, messages, audit::add);
		GangOptionContribution contribution = new GangOptionContribution(mock(JavaPlugin.class),
		                                                                 mock(UserManager.class),
		                                                                 mock(MemberManager.class), gangManager,
		                                                                 mock(RankManager.class), verbs);
		gangArgument = contribution.create(new Tree<>(), mock(Argument.class)).get(0);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	/** Runs the node a typed line ends on, as the dispatcher would: {@code verb arg arg ...}. */
	private void run(CommandSender sender, String... words) {
		Argument node = child(gangArgument, words[0]);
		for (int i = 1; i < words.length; i++) {
			node = children(node).get(0);
		}
		String[] args = new String[words.length + 2];
		args[0] = "option";
		args[1] = "gang";
		System.arraycopy(words, 0, args, 2, words.length);
		node.getAction().accept(node, sender, args);
	}

	private static Argument child(Argument parent, String word) {
		return children(parent).stream()
				.filter(c -> Arrays.asList(c.getArguments()).contains(word))
				.findFirst()
				.orElseThrow(() -> new AssertionError("no verb " + word));
	}

	private static List<Argument> children(Argument parent) {
		List<Argument> result = new ArrayList<>();
		for (Tree.Node<Argument> node : parent.getNode().getChildren()) {
			result.add(node.getData());
		}
		return result;
	}

	@Test
	@DisplayName("everyVerb_fromConsole")
	void everyVerb_fromConsole() {
		for (String verb : List.of("disband", "leader", "bank", "safehouse", "stats", "spy", "purge")) {
			sent.clear();
			run(console, verb);
			assertFalse(sent.isEmpty(), verb + " answered nothing from the console");
		}
	}

	@Test
	@DisplayName("bankWrites_loggedWithActor")
	void bankWrites_loggedWithActor() {
		run(console, "bank", "Rats", "give", "50");
		assertEquals(0, BigDecimal.valueOf(50).compareTo(rats.getEconomy().getAmount()));
		assertEquals(1, audit.size(), audit.toString());
		assertTrue(audit.get(0).startsWith("staff bank give by CONSOLE on Rats: 50"), audit.get(0));
		assertTrue(audit.get(0).contains("balance 0") && audit.get(0).contains("-> 50"), audit.get(0));

		audit.clear();
		run(console, "bank", "Rats", "take", "20");
		assertEquals(0, BigDecimal.valueOf(30).compareTo(rats.getEconomy().getAmount()));
		assertTrue(audit.get(0).startsWith("staff bank take by CONSOLE on Rats: 20"), audit.get(0));

		audit.clear();
		run(console, "bank", "Rats", "reset");
		assertEquals(0, rats.getEconomy().getAmount().signum());
		assertEquals(1, audit.size());
		assertTrue(audit.get(0).startsWith("staff bank reset by CONSOLE on Rats:"), audit.get(0));

		audit.clear();
		run(console, "bank", "Rats", "take", "999");
		assertTrue(audit.isEmpty(), "a refused take is not a write: " + audit);

		run(console, "bank", "Rats", "balance");
		assertTrue(audit.isEmpty(), "reading the balance writes nothing");
	}

	@Test
	@DisplayName("disband_firesGangDeleteEvent")
	void disband_firesGangDeleteEvent() {
		run(console, "disband", "Rats");
		verify(membership, never()).disband(anyInt(), any(), anyBoolean());

		run(console, "disband", "Rats", "confirm");
		// the staff path of GangDisbandService fires GangDeleteEvent; the verb must reach it with staff = true
		verify(membership).disband(eq(7), eq(null), eq(true));
	}

	@Test
	@DisplayName("leader_assignsTail")
	void leader_assignsTail() {
		run(console, "leader", "Rats", "Boss");
		verify(membership).setLeader(eq(7), eq(null), eq(boss), eq(true));
	}

	@Test
	@DisplayName("purge_previewThenRun")
	void purge_previewThenRun() {
		when(purge.preview(any(Instant.class))).thenReturn(List.of(new PurgeCandidate(7, "Rats", 0L)));
		when(purge.run(any(Instant.class))).thenReturn(1);

		run(console, "purge", "preview");
		verify(purge, never()).run(any());
		assertTrue(sent.stream().anyMatch(line -> line.contains("Rats")), sent.toString());

		sent.clear();
		run(console, "purge", "run");
		verify(purge).run(any(Instant.class));
		assertTrue(sent.stream().anyMatch(line -> line.contains("1")), sent.toString());
	}

	@Test
	@DisplayName("safehouse_listAndRemoveFromConsole_tpNeedsPlayer")
	void safehouse_listAndRemoveFromConsole_tpNeedsPlayer() {
		Safehouse den = new Safehouse(7, "den", "world", 1, 2, 3, 0, 0);
		when(safehouses.list(7)).thenReturn(List.of(den));
		when(safehouses.find(7, "den")).thenReturn(Optional.of(den));

		run(console, "safehouse", "Rats", "list");
		assertTrue(sent.stream().anyMatch(line -> line.contains("den")), sent.toString());

		run(console, "safehouse", "Rats", "remove", "den");
		verify(safehouses).remove(7, "den");

		sent.clear();
		run(console, "safehouse", "Rats", "tp", "den");
		verify(safehouses, never()).teleport(any(), any());
		assertFalse(sent.isEmpty());

		Player staff = mock(Player.class);
		when(staff.getName()).thenReturn("Mod");
		run(staff, "safehouse", "Rats", "tp", "den");
		verify(safehouses).teleport(staff, den);
	}

	@Test
	@DisplayName("statsReset_resetsThePlayer")
	void statsReset_resetsThePlayer() {
		run(console, "stats", "reset", "Boss");
		verify(stats).resetPlayer(boss);
	}

	@Test
	@DisplayName("spy_togglesForPlayersOnly")
	void spy_togglesForPlayersOnly() {
		run(console, "spy");
		assertTrue(spy.spies().isEmpty());
		assertFalse(sent.isEmpty());

		Player staff = mock(Player.class);
		UUID   id    = UUID.randomUUID();
		when(staff.getUniqueId()).thenReturn(id);
		run(staff, "spy");
		assertTrue(spy.spies().contains(id));
		run(staff, "spy");
		assertTrue(spy.spies().isEmpty());
	}

	@Test
	@DisplayName("rankVerbs_consoleNoCast")
	void rankVerbs_consoleNoCast() {
		Argument rank   = child(gangArgument, "rank");
		Argument target = children(rank).get(0);
		Argument type   = children(target).get(0);

		// the executors of the rank chain used to cast the sender to Player (GR-36/GR-42)
		target.getAction().accept(target, console, new String[]{"option", "gang", "rank"});
		type.getAction().accept(type, console, new String[]{"option", "gang", "rank", "Boss", "Member"});

		ArgumentCaptor<String> lines = ArgumentCaptor.forClass(String.class);
		verify(console, atLeastOnce()).sendMessage(lines.capture());
		assertEquals(Messages.NOT_PLAYER.toString(), lines.getAllValues().get(0));
	}

}
