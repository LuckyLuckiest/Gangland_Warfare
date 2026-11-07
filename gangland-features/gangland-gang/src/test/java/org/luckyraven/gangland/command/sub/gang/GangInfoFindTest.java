package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.stats.GangStatsService;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
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
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code /glw gang info [gang]}: a public card for a gang by name or id. It never shows the bank balance, and with no
 * argument it shows the sender's own gang (or the usage line when gangless).
 */
@DisplayName("GangInfoCommand - which gang a typed name selects")
class GangInfoFindTest {

	private static final String BALANCE = "987654";

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private Player                 player;
	private Gang                   rats;

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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		gangManager = mock(GangManager.class);
		userManager = mock(UserManager.class);
		player      = mock(Player.class);
		when(player.hasPermission(anyString())).thenReturn(true);
		// the bank is private: the shared fixture is a plain outsider (no balance.others, no gang)
		when(player.hasPermission(GangPermissions.BALANCE_OTHERS)).thenReturn(false);

		rats = gang(5, "Rats", "Dockside vermin.", "owner", "Boss", 3);
		when(gangManager.getAll()).thenReturn(List.of(rats));
		when(gangManager.getGang(5)).thenReturn(rats);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the first gang whose plain name matches ignoring case is shown; a nameless gang is skipped")
	void firstPlainNameMatchWins_namelessSkipped() throws Exception {
		Gang nameless = gang(11, null, "d", "owner", "Nobody", 1);
		Gang kings    = gang(12, "Kings", "d", "owner", "Kong", 1);
		Gang first    = gang(13, "&cVipers", "d", "owner", "FirstBoss", 1);
		Gang second   = gang(14, "vipers", "d", "owner", "SecondBoss", 1);
		when(first.getDisplayNameString()).thenReturn("FIRST-VIPERS");
		when(second.getDisplayNameString()).thenReturn("SECOND-VIPERS");
		when(gangManager.getAll()).thenReturn(List.of(nameless, kings, first, second));

		lookup().executeArgument(player, new String[]{"gang", "info", "vIPERS"});

		List<String> card = lines();
		assertTrue(card.stream().anyMatch(line -> line.contains("FIRST-VIPERS")), card.toString());
		assertTrue(card.stream().noneMatch(line -> line.contains("SECOND-VIPERS")), card.toString());
	}

	@Test
	@DisplayName("colour codes typed in the name are ignored when matching")
	void typedColourCodes_areIgnored() throws Exception {
		lookup().executeArgument(player, new String[]{"gang", "info", "&aRats"});

		assertTrue(lines().stream().anyMatch(line -> line.contains("Dockside vermin.")), lines().toString());
	}

	@Test
	@DisplayName("gangs that do not match, one of them nameless, leave the not-found line with the typed text")
	void nonMatchingGangs_giveNotFound() throws Exception {
		Gang nameless = gang(11, null, "d", "owner", "Nobody", 1);
		when(gangManager.getAll()).thenReturn(List.of(nameless, rats));

		lookup().executeArgument(player, new String[]{"gang", "info", "Zed"});

		assertTrue(lines().stream().anyMatch(line -> line.contains("No gang named Zed")), lines().toString());
	}

	/** The optional name/id child of {@code info}, which is where a typed gang name is answered. */
	private Argument lookup() {
		return info().getNode().getChildren().get(0).getData();
	}

	private Argument info() {
		return new GangInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), gangManager,
		                           userManager, messages, new GangStatsService());
	}

	/** Plain text of every line sent to the player through {@code sendMessage}, in order. */
	private List<String> lines() {
		ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
		verify(player, atLeast(0)).sendMessage(captor.capture());
		return captor.getAllValues().stream().map(ChatColor::stripColor).toList();
	}

	private Gang gang(int id, String name, String description, String rankName, String ownerName, int members) {
		Gang gang = mock(Gang.class);
		when(gang.getId()).thenReturn(id);
		when(gang.getName()).thenReturn(name);
		when(gang.getDisplayNameString()).thenReturn(name);
		when(gang.getDescription()).thenReturn(description);
		when(gang.getDateCreatedString()).thenReturn("01/01/2026");
		when(gang.getAllies()).thenReturn(Set.of());
		when(gang.getOnlineMembers(any())).thenReturn(List.of());

		EconomyHandler economy = new EconomyHandler(null);
		economy.setAmount(new BigDecimal(BALANCE));
		when(gang.getEconomy()).thenReturn(economy);

		UUID        ownerId = UUID.randomUUID();
		OfflinePlayer owner = mock(OfflinePlayer.class);
		when(owner.getName()).thenReturn(ownerName);
		bukkit.when(() -> Bukkit.getOfflinePlayer(ownerId)).thenReturn(owner);

		Rank ownerRank = mock(Rank.class);
		when(ownerRank.getName()).thenReturn(rankName);
		Member ownerMember = mock(Member.class);
		when(ownerMember.getUuid()).thenReturn(ownerId);
		when(ownerMember.getRank()).thenReturn(ownerRank);

		List<Member> roster = new ArrayList<>();
		roster.add(ownerMember);
		for (int i = 1; i < members; i++) {
			roster.add(mock(Member.class));
		}
		when(gang.getMembers()).thenReturn(roster);
		return gang;
	}

}
