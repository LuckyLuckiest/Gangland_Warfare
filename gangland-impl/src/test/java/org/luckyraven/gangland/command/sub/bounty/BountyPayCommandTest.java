package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * {@code /glw bounty pay}: the node, the console guard, the prefixed Paid line and the real route through
 * {@link Argument#execute}. The money maths belong to BountyPayoffTest.
 */
@DisplayName("/glw bounty pay")
class BountyPayCommandTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private UserManager<Player> userManager;
	private Player              payer, poster;
	private User<Player>        payerUser, posterUser;
	private Tree<Argument>      tree;
	private Argument            parent;
	private BountyPayCommand    pay;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider()
				              .withString("Commands.Prefix", "CPFX ")
				              .withString("Errors.Prefix", "EPFX ")
				              .withString("Commands.Bounty.Paid", "PAID %amount%")
				              .withString("Errors.Player.Not_Player", "NOT-A-PLAYER"));
		EconomyHandler.setVaultEconomy(null);

		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);

		plugin      = mock(JavaPlugin.class);
		userManager = mock(UserManager.class);
		payer       = player("Payer");
		poster      = player("Poster");
		payerUser   = new User<>(plugin, payer, (p, raw) -> raw);
		posterUser  = new User<>(plugin, poster, (p, raw) -> raw);
		when(userManager.getUser(payer)).thenReturn(payerUser);

		tree   = new Tree<>();
		parent = new Argument(plugin, new String[]{"bounty"}, tree, null, "gangland.command.bounty");
		pay    = new BountyPayCommand(plugin, tree, parent, userManager);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("the node is gangland.command.bounty.pay")
	void node_isGanglandCommandBountyPay() {
		assertEquals("gangland.command.bounty.pay", pay.getPermission());
	}

	@Test
	@DisplayName("the console is told it is not a player")
	void console_getsNotPlayer() {
		ConsoleCommandSender console = mock(ConsoleCommandSender.class);

		pay.executeArgument(console, new String[]{"bounty", "pay"});

		verify(console).sendMessage(contains("NOT-A-PLAYER"));
	}

	@Test
	@DisplayName("a player with a bounty and the money gets the prefixed Paid line")
	void player_paysOff_getsPrefixedPaidLine() {
		giveBounty(500, 1000);

		pay.executeArgument(payer, new String[]{"bounty", "pay"});

		verify(payer).sendMessage(contains("CPFX"));
		verify(payer).sendMessage(contains("PAID"));
		assertFalse(payerUser.getBounty().hasBounty());
	}

	@Test
	@DisplayName("an unknown user is ignored silently")
	void nullUser_silent() {
		Player stranger = player("Stranger");

		pay.executeArgument(stranger, new String[]{"bounty", "pay"});

		verify(stranger, never()).sendMessage(anyString());
	}

	@Test
	@DisplayName("bounty pay routes to the action through Argument.execute, and is denied without the node")
	void routing_bountyPay_reachesAction_andDeniedWithoutNode() {
		tree.add(parent.getNode());
		parent.addSubArgument(pay);
		giveBounty(500, 1000);

		when(payer.hasPermission(anyString())).thenReturn(true);
		parent.execute("glw", payer, new String[]{"bounty", "pay"});

		verify(payer).sendMessage(contains("PAID"));
		assertFalse(payerUser.getBounty().hasBounty());

		// denied: the bounty is back, the wallet is full, and the Paid line is not sent a second time
		giveBounty(500, 1000);
		when(payer.hasPermission("gangland.command.bounty.pay")).thenReturn(false);
		clearInvocations(payer);

		parent.execute("glw", payer, new String[]{"bounty", "pay"});

		verify(payer, never()).sendMessage(contains("PAID"));
		verify(payer, atLeastOnce()).sendMessage(anyString());
		assertTrue(payerUser.getBounty().hasBounty());
		assertEquals(0, Currency.of(1000).compareTo(payerUser.getEconomy().getAmount()));
	}

	private void giveBounty(int amount, int wallet) {
		payerUser.getEconomy().setAmount(Currency.of(wallet));
		posterUser.getEconomy().setAmount(Currency.of(amount));
		payerUser.getBounty().addBounty(poster, Currency.of(amount));
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

}
