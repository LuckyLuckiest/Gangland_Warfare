package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code /glw gang deposit|withdraw <amount>} must refuse a zero or negative amount with the localized error instead
 * of letting it reach {@code EconomyHandler}, whose sign guard throws a raw exception at the player.
 */
@DisplayName("GangDeposit/WithdrawCommand - contribution maths and amount suggestions")
class GangContributionTest {

	private static final int GANG_ID = 7;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private Argument               deposit;
	private Argument               withdraw;
	private User<Player>           user;
	private Player                 player;
	private Gang                   gang;
	private Member                 member;
	private EconomyHandler         economy;
	private Locale                 savedLocale;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		savedLocale = Locale.getDefault();
		Locale.setDefault(Locale.ROOT);
		bind(1.0);
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

		PluginManager pluginManager = mock(PluginManager.class);
		Server        server        = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(i -> List.of());

		UUID uuid = UUID.randomUUID();
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(uuid);
		when(player.hasPermission(anyString())).thenReturn(true);

		economy = new EconomyHandler(null);
		economy.setAmount(new BigDecimal("500"));
		user = mock(User.class);
		when(user.getUser()).thenReturn(player);
		when(user.getEconomy()).thenReturn(economy);
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(GANG_ID);

		gang = new Gang(GANG_ID);
		gang.getEconomy().setAmount(new BigDecimal("500"));

		UserManager<Player> userManager   = mock(UserManager.class);
		MemberManager       memberManager = mock(MemberManager.class);
		GangManager         gangManager   = mock(GangManager.class);
		when(userManager.getUser(player)).thenReturn(user);
		member = new Member(uuid);
		when(memberManager.getMember(uuid)).thenReturn(member);
		when(gangManager.getGang(GANG_ID)).thenReturn(gang);

		Argument parent = mock(Argument.class);
		when(parent.getPermission()).thenReturn("glw.gang");
		JavaPlugin plugin = mock(JavaPlugin.class);

		deposit  = leaf(new GangDepositCommand(plugin, new Tree<>(), parent, userManager, memberManager, gangManager));
		withdraw = leaf(new GangWithdrawCommand(plugin, new Tree<>(), parent, userManager, memberManager, gangManager));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Locale.setDefault(savedLocale);
	}

	@ParameterizedTest(name = "rate {0}, amount {1} -> contribution {2}")
	@CsvSource({"1, 500, 500.0", "10, 55, 5.5", "100, 12.34, 0.12", "2.5, 10, 4.0", "0.5, 3, 6.0", "1000, 450, 0.45"})
	@DisplayName("a deposit credits amount / rate rounded to as many decimals as the rate has digits less one")
	void deposit_creditsTheRoundedContribution(double rate, String amount, double expected) {
		bind(rate);

		deposit.executeArgument(player, new String[]{"gang", "deposit", amount});

		assertEquals(expected, member.getContribution());
		verify(user).sendMessage(GanglandChatUtil.color("&a+" + expected));
		assertEquals(0, new BigDecimal("500").add(new BigDecimal(amount)).compareTo(gang.getEconomy().getAmount()));
		assertEquals(0, new BigDecimal("500").subtract(new BigDecimal(amount)).compareTo(economy.getAmount()));
	}

	@ParameterizedTest(name = "rate {0}, amount {1} -> contribution {2}")
	@CsvSource({"1, 500, 500.0", "10, 55, 5.5", "100, 12.34, 0.12", "2.5, 10, 4.0", "0.5, 3, 6.0", "1000, 450, 0.45"})
	@DisplayName("a withdraw debits the same rounded contribution")
	void withdraw_debitsTheRoundedContribution(double rate, String amount, double expected) {
		bind(rate);
		gang.getEconomy().setAmount(new BigDecimal("5000"));

		withdraw.executeArgument(player, new String[]{"gang", "withdraw", amount});

		assertEquals(-expected, member.getContribution());
		verify(user).sendMessage(GanglandChatUtil.color("&c-" + expected));
		assertEquals(0, new BigDecimal("5000").subtract(new BigDecimal(amount)).compareTo(gang.getEconomy().getAmount()));
		assertEquals(0, new BigDecimal("500").add(new BigDecimal(amount)).compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("a refused deposit or withdraw leaves the contribution untouched")
	void refusals_doNotTouchTheContribution() {
		deposit.executeArgument(player, new String[]{"gang", "deposit", "501"});
		verify(user).sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());

		withdraw.executeArgument(player, new String[]{"gang", "withdraw", "501"});
		verify(user, times(2)).sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());

		deposit.executeArgument(player, new String[]{"gang", "deposit", "abc"});
		verify(user).sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", "abc"));

		assertEquals(0D, member.getContribution());
	}

	@Test
	@DisplayName("the deposit amount completer suggests rounded amounts up to the user's balance")
	void deposit_completerSuggestsFromTheUsersBalance() {
		economy.setAmount(new BigDecimal("500"));
		assertEquals(List.of("0.10", "0.25", "0.50", "0.75", "1", "2.50", "5", "7.50", "10", "25", "50", "75", "100", "500"), deposit.getArgumentString(player));

		economy.setAmount(new BigDecimal("12.5"));
		assertEquals(List.of("0.10", "0.25", "0.50", "0.75", "1", "2.50", "5", "7.50", "10", "12.50"), deposit.getArgumentString(player));

		economy.setAmount(BigDecimal.ZERO);
		assertEquals(List.of("<amount>"), deposit.getArgumentString(player));

		when(user.hasGang()).thenReturn(false);
		assertEquals(List.of(""), deposit.getArgumentString(player));
	}

	@Test
	@DisplayName("the withdraw amount completer suggests rounded amounts up to the gang's balance")
	void withdraw_completerSuggestsFromTheGangsBalance() {
		gang.getEconomy().setAmount(new BigDecimal("500"));
		assertEquals(List.of("0.10", "0.25", "0.50", "0.75", "1", "2.50", "5", "7.50", "10", "25", "50", "75", "100", "500"), withdraw.getArgumentString(player));

		gang.getEconomy().setAmount(new BigDecimal("12.5"));
		assertEquals(List.of("0.10", "0.25", "0.50", "0.75", "1", "2.50", "5", "7.50", "10", "12.50"), withdraw.getArgumentString(player));

		gang.getEconomy().setAmount(BigDecimal.ZERO);
		assertEquals(List.of("<amount>"), withdraw.getArgumentString(player));

		when(user.hasGang()).thenReturn(false);
		assertEquals(List.of(""), withdraw.getArgumentString(player));
	}

	private static void bind(double rate) {
		GangSettings.bind(new FakeGangSettingsContract().withContributionRate(rate)
		                                                .withMaxBalance(new BigDecimal("1000000")));
	}

	private static Argument leaf(Argument command) {
		return command.getNode().getChildren().get(0).getData();
	}

}
