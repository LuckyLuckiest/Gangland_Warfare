package org.luckyraven.gangland.command.sub.bounty;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.core.bounty.BountySettings;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedKillTracker;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
import org.luckyraven.gangland.core.wanted.WantedSettings;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.listener.player.EntityDamageListener;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * Drives {@code /glw bounty set <player> <amount>} and the kill that claims it, with real {@link User}s and their
 * real {@link EconomyHandler}s, so every assertion is about money that actually moved.
 */
@DisplayName("/glw bounty set -> kill claim")
class BountySetCommandTest {

	@TempDir
	Path dir;

	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private UserManager<Player> userManager;
	private Player              alice, bob;
	private User<Player>        aliceUser, bobUser;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider()
				              .withString("Commands.Bounty.Bounty_Set", "BOUNTY-SET")
				              .withString("Commands.Bounty.Claimed", "BOUNTY-CLAIMED %amount% %player%")
				              .withString("Commands.Bank.Money.Player_Add", "BANK-DEPOSIT %amount%"));
		EconomyHandler.setVaultEconomy(null);

		// The shipped Bounty.Repeating_Timer.Multiple is 2, the multiplier the test server ran with.
		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);

		plugin      = mock(JavaPlugin.class);
		userManager = mock(UserManager.class);
		alice       = player("Alice");
		bob         = player("Bob");
		aliceUser   = new User<>(plugin, alice, (p, raw) -> raw);
		bobUser     = new User<>(plugin, bob, (p, raw) -> raw);
		when(userManager.getUser(alice)).thenReturn(aliceUser);
		when(userManager.getUser(bob)).thenReturn(bobUser);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("a player-set bounty on a high-level target posts what was paid, and the claim mints nothing")
	void setThenClaim_highLevelTarget_conservesMoney() {
		aliceUser.getEconomy().setAmount(Currency.of(10_000));
		bobUser.getLevel().setLevelValue(100); // 1 + 100 * 2 / 10 = 21x under the old level scaling
		BigDecimal before = cash();

		setBounty(alice, "Bob", "1000");

		assertEquals(0, Currency.of(1000).compareTo(bobUser.getBounty().getAmount()),
		             "the posted bounty must equal the 1000 Alice paid, not a level-scaled figure");
		assertEquals(0, before.compareTo(cash().add(bobUser.getBounty().getAmount())),
		             "cash plus the escrowed bounty must equal the money that existed before the set");

		killBy(alice, bob);

		assertEquals(0, before.compareTo(cash()), "set + claim must neither create nor destroy money");
	}

	@Test
	@DisplayName("a sender with no money is refused and the target is never told a bounty was set")
	void set_senderHasNoMoney_targetNotNotified() {
		setBounty(alice, "Bob", "5");

		verify(bob, never()).sendMessage(contains("BOUNTY-SET"));
		assertEquals(0, bobUser.getBounty().getAmount().signum());
	}

	@Test
	@DisplayName("a sender short of the amount is refused and the target is never told a bounty was set")
	void set_senderCannotAfford_targetNotNotified() {
		aliceUser.getEconomy().setAmount(Currency.of(100));

		setBounty(alice, "Bob", "99999");

		verify(bob, never()).sendMessage(contains("BOUNTY-SET"));
		assertEquals(0, bobUser.getBounty().getAmount().signum());
	}

	@Test
	@DisplayName("the first successful bounty tells the target once")
	void set_success_targetNotifiedOnce() {
		aliceUser.getEconomy().setAmount(Currency.of(100));

		setBounty(alice, "Bob", "50");

		verify(bob, times(1)).sendMessage(contains("BOUNTY-SET"));
	}

	@Test
	@DisplayName("the claim is announced as a bounty claim naming the victim, not as a bank deposit")
	void claim_announcedAsBountyClaim() {
		aliceUser.getEconomy().setAmount(Currency.of(2000));
		setBounty(alice, "Bob", "2000");

		killBy(alice, bob);

		verify(alice).sendMessage(contains("BOUNTY-CLAIMED"));
		verify(alice).sendMessage(contains("Bob"));
		verify(alice, never()).sendMessage(contains("BANK-DEPOSIT"));
	}

	private BigDecimal cash() {
		return aliceUser.getEconomy().getAmount().add(bobUser.getEconomy().getAmount());
	}

	private void setBounty(Player sender, String target, String amount) {
		bukkit.statics().when(() -> Bukkit.getPlayer(target)).thenReturn(target.equals("Bob") ? bob : alice);

		Tree<Argument> tree   = new Tree<>();
		Argument       parent = new Argument(plugin, "bounty", tree);
		tree.add(parent.getNode());

		BountySetCommand command = new BountySetCommand(plugin, tree, parent, userManager);
		Argument amountArgument = command.getNode().getChildren().get(0).getChildren().get(0).getData();

		amountArgument.executeArgument(sender, new String[]{"bounty", "set", target, amount});
	}

	private void killBy(Player killer, Player victim) {
		WantedKillTrackers wantedKills = new WantedKillTrackers();
		wantedKills.install(mock(WantedKillTracker.class));

		EntityDamageListener listener = new EntityDamageListener(mock(Gangland.class), userManager, wantedKills,
		                                                         mock(BountySettings.class),
		                                                         mock(WantedSettings.class));

		when(victim.getHealth()).thenReturn(1.0);
		when(victim.getLocation()).thenReturn(new Location(null, 0, 0, 0));

		EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
		when(event.getDamager()).thenReturn(killer);
		when(event.getEntity()).thenReturn(victim);
		when(event.getFinalDamage()).thenReturn(20.0);

		listener.onPlayerEntityDeath(event);
	}

	private static Player player(String name) {
		Player player = mock(Player.class);
		when(player.getName()).thenReturn(name);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		return player;
	}

}
