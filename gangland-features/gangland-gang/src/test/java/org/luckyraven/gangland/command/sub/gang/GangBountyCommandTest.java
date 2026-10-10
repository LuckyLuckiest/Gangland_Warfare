package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
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
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.events.gang.GangBountyEvent;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.message.MessageProvider;
import org.mockito.MockedStatic;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * {@code /glw gang bounty <gang> <amount>} (D8 = B): a player puts money from his own balance on another gang. Refused
 * for his own gang, an allied gang, an unknown gang, a bad or too small amount, or more than his balance. A cancelled
 * post refunds him.
 */
@DisplayName("GangBountyCommand - /glw gang bounty <gang> <amount> posts money on a rival gang")
class GangBountyCommandTest {

	private static final BigDecimal BALANCE = new BigDecimal("1000");

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	private MockedStatic<Bukkit>   bukkit;
	private MockedStatic<Settings> settings;
	private PluginManager          pluginManager;
	private GangMessages           messages;
	private GangManager            gangManager;
	private UserManager<Player>    userManager;
	private Player                 player;
	private User<Player>           user;
	private EconomyHandler         economy;
	private Gang                   rats;
	private Gang                   vipers;
	private Gang                   ferrets;
	private Argument               bounty;

	@BeforeEach
	void setUp() throws Exception {
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

		settings = mockStatic(Settings.class, org.mockito.Mockito.CALLS_REAL_METHODS);
		settings.when(Settings::getMoneySymbol).thenReturn("$");
		settings.when(Settings::getLanguagePicked).thenReturn("en");
		settings.when(Settings::getBountyMinimum).thenReturn(BigDecimal.ZERO);

		pluginManager = mock(PluginManager.class);
		Server server = mock(Server.class);
		when(server.getPluginManager()).thenReturn(pluginManager);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getServer).thenReturn(server);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
		bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(invocation -> List.of());

		Command.setInformationManager(mock(InformationManager.class));
		messages = GangTestMessages.shipped(tempDir);

		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.hasPermission(anyString())).thenReturn(true);

		economy = new EconomyHandler(null);
		economy.setAmount(BALANCE);
		user = mock(User.class);
		when(user.getEconomy()).thenReturn(economy);
		when(user.hasGang()).thenReturn(false);
		userManager = mock(UserManager.class);
		when(userManager.getUser(player)).thenReturn(user);

		gangManager = mock(GangManager.class);

		rats    = gang(5, "Rats");
		vipers  = gang(7, "Vipers");
		ferrets = gang(9, "Ferrets");
		rats.addAlly(ferrets);
		when(gangManager.getAll()).thenReturn(List.of(rats, vipers, ferrets));

		bounty = new GangBountyCommand(mock(JavaPlugin.class), new Tree<>(), mock(Argument.class), userManager,
		                               mock(MemberManager.class), gangManager, messages);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		settings.close();
		Command.setInformationManager(null);
	}

	@Test
	@DisplayName("the money leaves the poster, the bounty lands on the gang, and the poster hears the total")
	void post_movesMoneyAndAddsBounty() {
		post("Vipers", "300");

		assertEquals(0, new BigDecimal("700").compareTo(economy.getAmount()), "poster " + economy.getAmount());
		assertEquals(0, new BigDecimal("300").compareTo(vipers.getBounty().getAmount()));
		verify(pluginManager).callEvent(argThat((Event event) -> event instanceof GangBountyEvent posted
		                                                         && posted.getGang() == vipers
		                                                         && posted.getAmountApplied()
		                                                                  .compareTo(new BigDecimal("300")) == 0));
		verify(player).sendMessage(messages.text("Bounty.Posted", "",
		                                         "money_symbol", "$",
		                                         "amount", Settings.formatAmount(new BigDecimal("300")),
		                                         "gang", "Vipers",
		                                         "total", Settings.formatAmount(new BigDecimal("300"))));
	}

	@Test
	@DisplayName("a member cannot put a bounty on his own gang")
	void ownGang_refused() {
		asMember(5);

		post("Rats", "300");

		assertRefused("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang.", BALANCE, rats);
	}

	@Test
	@DisplayName("a member is refused for his own gang even when the gang lookup by id misses (T-256)")
	void staleOwnGangLookup_stillRefused() {
		asMember(5);
		when(gangManager.getGang(5)).thenReturn(null);

		post("Rats", "300");

		assertRefused("Bounty.Own_Gang", "&cYou cannot put a bounty on your own gang.", BALANCE, rats);
	}

	@Test
	@DisplayName("a member cannot put a bounty on an allied gang")
	void ally_refused() {
		asMember(5);

		post("Ferrets", "300");

		assertRefused("Bounty.Ally", "&cYou cannot put a bounty on an allied gang.", BALANCE, ferrets);
	}

	@Test
	@DisplayName("a gang name that matches nothing is refused")
	void unknownGang_refused() {
		post("Nobody", "300");

		verify(player).sendMessage(messages.text("Bounty.Unknown", "&cNo gang named %gang%.", "gang", "Nobody"));
		assertEquals(0, BALANCE.compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("a typed colour code in an unknown gang name is not rendered in the refusal (T-257)")
	void unknownGang_typedColourCodesNotRendered() {
		post("&kGhost", "300");

		verify(player).sendMessage(messages.text("Bounty.Unknown", "&cNo gang named %gang%.", "gang", "Ghost"));
		assertEquals(0, BALANCE.compareTo(economy.getAmount()));
	}

	@Test
	@DisplayName("a console sender gets the not-a-player reply (T-258)")
	void console_getsNotPlayer() {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);

		amountArgument().executeArgument(console, new String[]{"gang", "bounty", "Vipers", "300"});

		verify(console).sendMessage(Messages.NOT_PLAYER.toString());
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("an amount below the configured bounty minimum is refused with the minimum")
	void belowMinimum_refused() {
		settings.when(Settings::getBountyMinimum).thenReturn(new BigDecimal("250"));

		post("Vipers", "100");

		verify(player).sendMessage(Messages.BOUNTY_BELOW_MINIMUM.toString()
		                                                        .replace("%money_symbol%", "$")
		                                                        .replace("%minimum%",
		                                                                 Settings.formatAmount(new BigDecimal("250"))));
		assertEquals(0, BALANCE.compareTo(economy.getAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("more than the poster's balance is refused and the balance is untouched")
	void overBalance_refused() {
		post("Vipers", "5000");

		verify(player).sendMessage(Messages.CANNOT_TAKE_MORE_THAN_BALANCE.toString());
		assertEquals(0, BALANCE.compareTo(economy.getAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("a zero or negative amount is refused")
	void zeroOrNegative_refused() {
		post("Vipers", "0");
		post("Vipers", "-5");

		verify(player, times(2)).sendMessage(Messages.CANNOT_TAKE_LESS_THAN_ZERO.toString());
		assertEquals(0, BALANCE.compareTo(economy.getAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("a cancelled post refunds the poster and books nothing")
	void cancelledEvent_refunds() {
		doAnswer(i -> {
			((GangBountyEvent) i.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		post("Vipers", "300");

		assertEquals(0, BALANCE.compareTo(economy.getAmount()), "poster " + economy.getAmount());
		assertEquals(0, BigDecimal.ZERO.compareTo(vipers.getBounty().getAmount()));
	}

	@Test
	@DisplayName("the gang-name completer lists every gang except the sender's own")
	void tabCompletion_listsOtherGangs() {
		asMember(5);

		List<String> names = gangArgument().getArgumentString(player);

		assertEquals(Set.of("Vipers", "Ferrets"), Set.copyOf(names), names.toString());
	}

	private void assertRefused(String key, String fallback, BigDecimal balance, Gang target) {
		verify(player).sendMessage(messages.text(key, fallback));
		assertEquals(0, balance.compareTo(economy.getAmount()));
		assertEquals(0, BigDecimal.ZERO.compareTo(target.getBounty().getAmount()));
	}

	/** Makes the poster a member of the gang with the given id. */
	private void asMember(int gangId) {
		when(user.hasGang()).thenReturn(true);
		when(user.getGangId()).thenReturn(gangId);
	}

	/** Runs the amount step, the way a player's {@code bounty <gang> <amount>} reaches it. */
	private void post(String gang, String amount) {
		amountArgument().executeArgument(player, new String[]{"gang", "bounty", gang, amount});
	}

	private Argument gangArgument() {
		return bounty.getNode().getChildren().get(0).getData();
	}

	private Argument amountArgument() {
		return gangArgument().getNode().getChildren().get(0).getData();
	}

	private Gang gang(int id, String name) {
		Gang gang = new Gang(id);
		gang.setName(name);
		when(gangManager.getGang(id)).thenReturn(gang);
		return gang;
	}

}
