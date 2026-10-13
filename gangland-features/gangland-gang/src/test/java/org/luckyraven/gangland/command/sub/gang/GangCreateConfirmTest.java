package org.luckyraven.gangland.command.sub.gang;

import static org.mockito.ArgumentMatchers.anyInt;
import org.luckyraven.gangland.gang.support.MembershipKit;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Two players stage the same free name inside the confirm window. The first confirm creates the gang; the second
 * confirm must be refused at the confirm step (GR-29: names are unique) with no fee taken and no second gang.
 */
@DisplayName("GangCreateCommand - a name taken between staging and confirm is refused without taking the fee")
class GangCreateConfirmTest {

	private static final BigDecimal FEE = new BigDecimal("2500");

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private final Map<Integer, Gang> gangs = new HashMap<>();

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private JavaPlugin             plugin;
	private UserManager<Player>    userManager;
	private MemberManager          memberManager;
	private GangManager            gangManager;
	private RankManager            rankManager;
	private RepositoryRegistry     repositoryRegistry;
	private GangMessages           messages;
	private GangCreateCommand      command;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() throws IOException {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(FEE));
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
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		BukkitScheduler scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(
				mock(BukkitTask.class));
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		plugin             = mock(JavaPlugin.class);
		userManager        = mock(UserManager.class);
		memberManager      = mock(MemberManager.class);
		rankManager        = mock(RankManager.class);
		repositoryRegistry = mock(RepositoryRegistry.class);
		gangManager        = mock(GangManager.class);

		when(rankManager.get(anyString())).thenReturn(new Rank("member", 2));
		doReturn(mock(IRepository.class)).when(repositoryRegistry).getRepository(Gang.class);
		doReturn(mock(IRepository.class)).when(repositoryRegistry).getRepository(Member.class);
		when(gangManager.getGangs()).thenReturn(gangs);
		when(gangManager.getGang(anyInt())).thenAnswer(i -> gangs.get(i.<Integer>getArgument(0)));
		doAnswer(i -> {
			Gang gang = i.getArgument(0);
			gangs.put(gang.getId(), gang);
			return null;
		}).when(gangManager).add(any(Gang.class));

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		command = new GangCreateCommand(plugin, new Tree<>(), parent, userManager, memberManager, gangManager,
		                                rankManager, repositoryRegistry, () -> Pattern.compile("[A-Za-z0-9_]{3,16}"),
		                                messages,
		                                new MembershipKit(gangManager, memberManager, rankManager, userManager,
		                                                  mock(UserManager.class), repositoryRegistry, messages).service);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the second confirm for a name the first confirm just took is refused: no fee, no second gang")
	@SuppressWarnings("unchecked")
	void secondConfirm_sameNameTakenMeanwhile_refusedWithoutFee() {
		Staged alice = staged("Alice");
		Staged bob   = staged("Bob");

		// Both stage "Rats" while it is still free; the name step only checks at staging time.
		stageName(alice, "Rats");
		stageName(bob, "Rats");

		confirm(alice);
		confirm(bob);

		assertMoney("7500", alice.economy());  // the fee left Alice once
		assertMoney("10000", bob.economy());   // Bob was refused before any withdrawal
		verify(gangManager, times(1)).add(any(Gang.class));
		verify(bob.user()).sendMessage(argThat(
				(String line) -> ChatColor.stripColor(line).contains("A gang named Rats already exists.")));
	}

	@Test
	@DisplayName("T-233: with the owner rank missing, the confirm refuses before any fee is taken and creates nothing")
	@SuppressWarnings("unchecked")
	void missingOwnerRank_refusedBeforeFee() {
		when(rankManager.get(GangSettings.getGangRankTail())).thenReturn(null);
		Staged alice = staged("Alice");

		stageName(alice, "Rats");
		confirm(alice);

		assertMoney("10000", alice.economy());
		verify(gangManager, never()).add(any(Gang.class));
		verify(alice.user()).sendMessage(argThat(
				(String line) -> ChatColor.stripColor(line).contains("the owner rank is missing")));
	}

	/** One player's cached user, member and economy. */
	private Staged staged(String label) {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.getName()).thenReturn(label);

		User<Player>  user    = mock(User.class);
		EconomyHandler economy = new EconomyHandler(null);
		economy.setAmount(new BigDecimal("10000"));
		when(user.getUser()).thenReturn(player);
		when(user.hasGang()).thenReturn(false);
		when(user.getEconomy()).thenReturn(economy);
		when(userManager.getUser(player)).thenReturn(user);
		bukkit.when(() -> Bukkit.getPlayer(uuid)).thenReturn(player);

		Member member = mock(Member.class);
		when(member.getUuid()).thenReturn(uuid);
		when(memberManager.getMember(uuid)).thenReturn(member);

		return new Staged(player, user, economy);
	}

	private void stageName(Staged who, String name) {
		Argument createName = command.getNode().getChildren().get(1).getData();
		createName.executeArgument(who.player(), new String[]{"gang", "create", name});
	}

	private void confirm(Staged who) {
		ConfirmArgument confirm = (ConfirmArgument) command.getNode().getChildren().get(0).getData();
		confirm.executeArgument(who.player(), new String[]{"gang", "create", "confirm"});
	}

	private static void assertMoney(String expected, EconomyHandler economy) {
		assertEquals(0, new BigDecimal(expected).compareTo(economy.getAmount()),
		             "expected " + expected + " but was " + economy.getAmount());
	}

	private record Staged(Player player, User<Player> user, EconomyHandler economy) {
	}

}
