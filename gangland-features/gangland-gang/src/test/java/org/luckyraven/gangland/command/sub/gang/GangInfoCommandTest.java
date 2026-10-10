package org.luckyraven.gangland.command.sub.gang;

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
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.util.GanglandChatUtil;
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
@DisplayName("GangInfoCommand - /glw gang info [gang]")
class GangInfoCommandTest {

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
	@DisplayName("a gang is found by name, ignoring case, and its card names the owner, members and description")
	void byName_ignoringCase() throws Exception {
		lookup().executeArgument(player, new String[]{"gang", "info", "rATS"});

		List<String> card = lines();
		assertTrue(card.stream().anyMatch(line -> line.contains("Rats")), card.toString());
		assertTrue(card.stream().anyMatch(line -> line.contains("Dockside vermin.")), card.toString());
		assertTrue(card.stream().anyMatch(line -> line.contains("Boss")), "owner line: " + card);
		assertTrue(card.stream().anyMatch(line -> line.contains("0/3")), "members line: " + card);
	}

	@Test
	@DisplayName("a gang is found by its numeric id")
	void byId() throws Exception {
		lookup().executeArgument(player, new String[]{"gang", "info", "5"});

		assertTrue(lines().stream().anyMatch(line -> line.contains("Rats")), lines().toString());
	}

	@Test
	@DisplayName("the card never shows the bank balance")
	void card_neverShowsBalance() throws Exception {
		lookup().executeArgument(player, new String[]{"gang", "info", "Rats"});

		assertTrue(lines().stream().noneMatch(line -> line.contains(BALANCE)), lines().toString());
		verify(rats, never()).getEconomy();
	}

	@Test
	@DisplayName("allies are listed by name on the card")
	void card_listsAllies() throws Exception {
		Gang ferrets = gang(9, "Ferrets", "", "owner", "Other", 1);
		when(rats.getAllies()).thenReturn(Set.of(new GangAlliance(rats, ferrets, 0L)));

		lookup().executeArgument(player, new String[]{"gang", "info", "Rats"});

		assertTrue(lines().stream().anyMatch(line -> line.contains("Ferrets")), lines().toString());
	}

	@Test
	@DisplayName("an unknown gang gets the not-found line")
	void unknown_notFound() throws Exception {
		lookup().executeArgument(player, new String[]{"gang", "info", "Nobody"});

		verify(player).sendMessage(messages.text("Info.Not_Found", "&cNo gang named %gang%.", "gang", "Nobody"));
	}

	@Test
	@DisplayName("with no argument a member sees their own gang's card")
	void noArgument_member_ownGang() throws Exception {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(5);
		when(userManager.getUser(player)).thenReturn(user);

		info().executeArgument(player, new String[]{"gang", "info"});

		assertTrue(lines().stream().anyMatch(line -> line.contains("Rats")), lines().toString());
	}

	@Test
	@DisplayName("with no argument and no gang the usage line is sent")
	void noArgument_gangless_usage() throws Exception {
		User<Player> user = mock(User.class);
		when(user.hasGang()).thenReturn(false);
		when(userManager.getUser(player)).thenReturn(user);

		info().executeArgument(player, new String[]{"gang", "info"});

		verify(player).sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
	}

	/** The optional name/id child of {@code info}, which is where a typed gang name is answered. */
	private Argument lookup() {
		return info().getNode().getChildren().get(0).getData();
	}

	private Argument info() {
		return new GangInfoCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), gangManager,
		                           userManager, messages);
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
