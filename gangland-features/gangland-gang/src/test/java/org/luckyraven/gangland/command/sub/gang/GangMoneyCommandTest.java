package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@code /glw gang deposit|withdraw <amount>} must refuse a zero or negative amount with the localized error instead
 * of letting it reach {@code EconomyHandler}, whose sign guard throws a raw exception at the player.
 */
@DisplayName("GangDeposit/WithdrawCommand - non-positive amounts are refused")
class GangMoneyCommandTest {

	private static final int GANG_ID = 7;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private Argument               deposit;
	private Argument               withdraw;
	private User<Player>           user;
	private Player                 player;
	private Gang                   gang;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
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
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getGangContributionRate).thenReturn(1.0);
		settings.when(Settings::getGangMaxBalance).thenReturn(new BigDecimal("1000000"));

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

		EconomyHandler economy = new EconomyHandler(null);
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
		when(memberManager.getMember(uuid)).thenReturn(new Member(uuid));
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
	}

	@ParameterizedTest(name = "{0} {1}")
	@CsvSource({"deposit, -100", "deposit, 0", "withdraw, -100", "withdraw, 0"})
	@DisplayName("a non-positive amount is refused and no money moves")
	void nonPositiveAmount_isRefused(String action, String amount) {
		Argument leaf = action.equals("deposit") ? deposit : withdraw;

		assertDoesNotThrow(() -> leaf.executeArgument(player, new String[]{"gang", action, amount}));

		verify(user).sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
		assertEquals(0, new BigDecimal("500").compareTo(user.getEconomy().getAmount()));
		assertEquals(0, new BigDecimal("500").compareTo(gang.getEconomy().getAmount()));
	}

	private static Argument leaf(Argument command) {
		return command.getNode().getChildren().get(0).getData();
	}

}
