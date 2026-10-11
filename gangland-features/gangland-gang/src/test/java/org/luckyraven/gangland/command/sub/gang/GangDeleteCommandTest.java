package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.membership.GangDisbandService;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
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
import net.md_5.bungee.api.ChatColor;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.message.MessageProvider;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.util.TimeMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.util.TimeUtil;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Drives {@code /glw gang delete} then {@code confirm} end to end with mocked managers and real {@link Gang},
 * {@link Member} and {@link EconomyHandler} objects, so the money that leaves the gang vault is the money that lands
 * in the members' accounts.
 */
@DisplayName("GangDeleteCommand - disband payout conserves the vault and persists offline payouts")
class GangDeleteCommandTest {

	private static final int        GANG_ID = 42;
	private static final BigDecimal FEE     = new BigDecimal("400");  // refund is a quarter: 100
	private static final BigDecimal REFUND  = new BigDecimal("100");

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private final List<Player>            online   = new ArrayList<>();
	private       MockedStatic<Bukkit>    bukkit;
	private       MockedStatic<Settings>  settings;
	private       JavaPlugin              plugin;
	private       BukkitScheduler         scheduler;
	private       PluginManager           pluginManager;
	private       UserManager<Player>        userManager;
	private       UserManager<OfflinePlayer> offlineUserManager;
	private       MemberManager           memberManager;
	private       GangManager             gangManager;
	private       RepositoryRegistry      repositoryRegistry;
	private       IRepository<Object>     userRepository;
	private       Rank                    ownerRank;
	private       Rank                    memberRank;
	private       Gang                    gang;
	private       RankManager             rankManager;
	private       Argument                parent;
	private       GangDeleteCommand       command;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		GangSettings.bind(new FakeGangSettingsContract().withCreateFee(FEE));
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		Messages.init(new MessageProvider() {
			@Override
			public String getString(String path) {
				if (path.equals(Messages.GANG_REMOVE_CONFIRM.getPath())) return "%timer%";
				if (path.equals(Messages.MINUTE.getPath())) return "m";
				if (path.equals(Messages.SECOND.getPath())) return "s";
				if (path.equals(Messages.KICKED_FROM_GANG.getPath())) {
					return "KICKED";
				}
				if (path.equals(Messages.GANG_REMOVED.getPath())) {
					return "REMOVED %gang%";
				}
				if (path.equals(Messages.DEPOSIT_MONEY_PLAYER.getPath())) {
					return "DEPOSITED %amount%";
				}
				return "";
			}

			@Override
			public List<String> getStringList(String path) {
				return List.of();
			}
		});
		TimeMessages.initialize();

		settings = mockStatic(Settings.class, CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");

		scheduler = mock(BukkitScheduler.class);
		when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong())).thenReturn(
				mock(BukkitTask.class));
		pluginManager = mock(PluginManager.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i -> online);
		bukkit.when(() -> Bukkit.getOfflinePlayer(any(UUID.class))).thenAnswer(i -> {
			OfflinePlayer offline = mock(OfflinePlayer.class);
			when(offline.getUniqueId()).thenReturn(i.getArgument(0));
			return offline;
		});

		plugin             = mock(JavaPlugin.class);
		userManager        = mock(UserManager.class);
		offlineUserManager = mock(UserManager.class);
		memberManager      = mock(MemberManager.class);
		gangManager        = mock(GangManager.class);
		repositoryRegistry = mock(RepositoryRegistry.class);
		userRepository     = mock(IRepository.class);
		rankManager = mock(RankManager.class);

		ownerRank  = new Rank("owner", 1);
		memberRank = new Rank("member", 2);
		when(rankManager.get("owner")).thenReturn(ownerRank);

		when(repositoryRegistry.getRepository(Member.class)).thenReturn(mock(IRepository.class));
		when(repositoryRegistry.getRepository(Gang.class)).thenReturn(mock(IRepository.class));
		when(repositoryRegistry.getRepository(GangAlliance.class)).thenReturn(mock(IRepository.class));
		doReturn(userRepository).when(repositoryRegistry).getGenericRepository(User.class);

		gang = new Gang(GANG_ID);
		gang.setName("Crew");
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);

		parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");

		command = commandWith(mock(GangMessages.class));
	}

	private GangDeleteCommand commandWith(GangMessages messages) {
		return new GangDeleteCommand(plugin, new Tree<>(), parent, userManager, offlineUserManager, memberManager,
		                             gangManager, rankManager, repositoryRegistry, messages, new GangDisbandService());
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
	}

	@Test
	@DisplayName("contributions 3:1 on a 4000 vault pay exactly 3000 and 1000, whatever order members are paid in")
	void proRataPayout_isExact() {
		User<Player> carl  = onlineMember(1.0);   // paid first: used to take 1000, then the owner got 3000*3/4
		User<Player> alice = owner(3.0);
		gang.getEconomy().setAmount(new BigDecimal("4000"));

		disband(alice);

		assertMoney("1000", carl.getEconomy());
		assertMoney("3100", alice.getEconomy());  // 3000 + fee refund
		assertMoney("0", gang.getEconomy());
		verify(gangManager).remove(gang);
	}

	@Test
	@DisplayName("a total contribution under 0.5 still pays the vault out instead of zeroing every share")
	void smallTotalContribution_stillPaysOut() {
		User<Player> alice = owner(0.4);
		gang.getEconomy().setAmount(new BigDecimal("400"));

		disband(alice);

		assertMoney("500", alice.getEconomy());   // 400 + fee refund
		assertMoney("0", gang.getEconomy());
	}

	@Test
	@DisplayName("uneven splits leave no rounding dust in the vault: 1:1:1 on 100 pays 100 in total")
	void unevenSplit_conservesEveryCent() {
		User<Player>        carl  = onlineMember(1.0);
		User<OfflinePlayer> dave  = offlineMember(1.0);
		User<Player>        alice = owner(1.0);
		gang.getEconomy().setAmount(new BigDecimal("100"));

		disband(alice);

		BigDecimal paid = carl.getEconomy().getAmount()
		                      .add(dave.getEconomy().getAmount())
		                      .add(alice.getEconomy().getAmount().subtract(REFUND));
		assertEquals(0, new BigDecimal("100").compareTo(paid), "paid out " + paid);
		assertMoney("0", gang.getEconomy());
	}

	@Test
	@DisplayName("a negative contribution gets nothing and the disband still completes")
	void negativeContribution_doesNotAbortDisband() {
		User<Player> carl  = onlineMember(-0.5);
		User<Player> alice = owner(1.0);
		gang.getEconomy().setAmount(new BigDecimal("500"));

		disband(alice);

		assertMoney("0", carl.getEconomy());
		assertMoney("600", alice.getEconomy());   // 500 + fee refund
		assertMoney("0", gang.getEconomy());
		verify(gangManager).remove(gang);
	}

	@Test
	@DisplayName("an offline member's payout is saved right away, not left for an autosave a rejoin can discard")
	void offlinePayout_isPersistedImmediately() {
		User<OfflinePlayer> dave  = offlineMember(1.0);
		User<Player>        alice = owner(1.0);
		gang.getEconomy().setAmount(new BigDecimal("1000"));

		List<BigDecimal> savedBalance = new ArrayList<>();
		doAnswer(i -> savedBalance.add(dave.getEconomy().getAmount())).when(userRepository).save(dave);

		disband(alice);

		assertEquals(1, savedBalance.size(), "the offline User row must be saved once");
		assertEquals(0, new BigDecimal("500").compareTo(savedBalance.get(0)), "saved balance " + savedBalance);
	}

	@Test
	@DisplayName("disband announces the deleted gang so other modules (mail) can drop what points at it")
	void disband_firesGangDeleteEvent() {
		disband(owner(1.0));

		verify(pluginManager).callEvent(argThat(event -> event instanceof GangDeleteEvent deleted
		                                                 && deleted.getGang() == gang));
	}

	@Test
	@DisplayName("the first confirm reminder shows the time left (1 minute), not the timer's 20-tick period")
	void countdownReminder_showsTimeLeft() {
		User<Player> alice = owner(1.0);
		Player       player = alice.getUser();

		command.executeArgument(player, new String[]{"gang", "delete"});

		ArgumentCaptor<Runnable> tick = ArgumentCaptor.forClass(Runnable.class);
		verify(scheduler).runTaskTimer(eq(plugin), tick.capture(), anyLong(), anyLong());
		tick.getValue().run();

		verify(player).sendMessage(Messages.GANG_REMOVE_CONFIRM.toString()
		                                   .replace("%timer%",
		                                            TimeUtil.formatTime(60, true, TimeMessages.getInstance())));
	}

	@Test
	@DisplayName("an all-zero vault splits equally on disband, the owner takes the rounding remainder (D15)")
	void allZeroContributions_splitEqually() {
		UUID owner = UUID.randomUUID();
		UUID bob   = UUID.randomUUID();
		UUID cy    = UUID.randomUUID();
		List<Member> members = List.of(zeroMember(owner), zeroMember(bob), zeroMember(cy));

		Map<UUID, BigDecimal> shares = GangDeleteCommand.payouts(new BigDecimal("100.00"), members, owner);

		assertEquals(0, new BigDecimal("33.33").compareTo(shares.get(bob)), "bob " + shares.get(bob));
		assertEquals(0, new BigDecimal("33.33").compareTo(shares.get(cy)), "cy " + shares.get(cy));
		assertEquals(0, new BigDecimal("33.34").compareTo(shares.get(owner)), "owner " + shares.get(owner));
		BigDecimal total = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
		assertEquals(0, new BigDecimal("100.00").compareTo(total), "total " + total);
	}

	@Test
	@DisplayName("the owner's disband credits him once: his share and the fee refund, one kicked line, one refund line (T-209)")
	void ownerDelete_creditsOwnerOnce() throws Exception {
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		command = commandWith(GangTestMessages.shipped(tempDir));
		User<Player> alice = owner(1.0);
		gang.getEconomy().setAmount(new BigDecimal("1000"));
		disband(alice);

		// every sendMessage the owner received, varargs expanded, in order
		List<String> plain = mockingDetails(alice).getInvocations().stream()
				.filter(invocation -> invocation.getMethod().getName().equals("sendMessage"))
				.flatMap(invocation -> Arrays.stream(invocation.getArguments()))
				.filter(String.class::isInstance)
				.map(line -> ChatColor.stripColor((String) line))
				.toList();
		assertEquals(1, plain.stream().filter(line -> line.equals("KICKED")).count(), plain.toString());
		assertEquals(1, plain.stream().filter(line -> line.startsWith("DEPOSITED")).count(), plain.toString());
		assertEquals(1, plain.stream().filter(line -> line.startsWith("Refunded a quarter")).count(), plain.toString());
		assertMoney("1100", alice.getEconomy());   // 1000 share + 100 fee refund, paid once
	}

	private Member zeroMember(UUID uuid) {
		Member member = new Member(uuid);
		member.setContribution(0D);
		return member;
	}

	private void disband(User<Player> owner) {
		Player player = owner.getUser();
		command.executeArgument(player, new String[]{"gang", "delete"});

		ConfirmArgument confirm = (ConfirmArgument) command.getNode().getChildren().get(0).getData();
		confirm.executeArgument(player, new String[]{"gang", "delete", "confirm"});
	}

	private User<Player> owner(double contribution) {
		return onlineMember(contribution, ownerRank);
	}

	private User<Player> onlineMember(double contribution) {
		return onlineMember(contribution, memberRank);
	}

	@SuppressWarnings("unchecked")
	private User<Player> onlineMember(double contribution, Rank rank) {
		UUID   uuid   = UUID.randomUUID();
		Player player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		online.add(player);

		User<Player> user = user(mock(User.class), player);
		when(userManager.getUser(player)).thenReturn(user);
		member(uuid, contribution, rank);
		return user;
	}

	@SuppressWarnings("unchecked")
	private User<OfflinePlayer> offlineMember(double contribution) {
		UUID uuid = UUID.randomUUID();
		User<OfflinePlayer> user = mock(User.class);
		when(user.getEconomy()).thenReturn(new EconomyHandler(null));
		when(offlineUserManager.getUser(any(OfflinePlayer.class))).thenAnswer(
				i -> uuid.equals(((OfflinePlayer) i.getArgument(0)).getUniqueId()) ? user : null);
		member(uuid, contribution, memberRank);
		return user;
	}

	private User<Player> user(User<Player> user, Player player) {
		EconomyHandler economy = new EconomyHandler(null);
		when(user.getUser()).thenReturn(player);
		when(user.getEconomy()).thenReturn(economy);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(GANG_ID);
		return user;
	}

	private void member(UUID uuid, double contribution, Rank rank) {
		Member member = new Member(uuid);
		gang.addMember(member, rank);
		member.setContribution(contribution);
		when(memberManager.getMember(uuid)).thenReturn(member);
	}

	private static void assertMoney(String expected, EconomyHandler economy) {
		assertEquals(0, new BigDecimal(expected).compareTo(economy.getAmount()),
		             "expected " + expected + " but was " + economy.getAmount());
	}

}
