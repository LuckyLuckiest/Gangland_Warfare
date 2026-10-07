package org.luckyraven.gangland.command.sub.contact;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.command.Command;
import org.luckyraven.gangland.command.data.InformationManager;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.core.user.IdentitySettingsContract;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/** {@code /glw contact [stars]} with a real wallet: every refusal moves nothing, a call charges per star. */
@DisplayName("/glw contact")
class ContactCommandTest {

	@TempDir
	Path dir;

	private BukkitStatics          bukkit;
	private Player                 player;
	private User<Player>           user;
	private ContactDesk            desk;
	private ContactCommand         command;
	private final AtomicLong       now    = new AtomicLong(1_000L);
	private final List<Object>     events = new ArrayList<>();

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider().withString("Wanted_Level.Contact.Used", "USED %stars% %amount%")
		                                       .withString("Wanted_Level.Contact.Seen", "SEEN")
		                                       .withString("Wanted_Level.Contact.Cooldown", "COOLDOWN %time%")
		                                       .withString("Wanted_Level.Contact.Not_Wanted", "NOT-WANTED")
		                                       .withString("Wanted_Level.Contact.No_Money", "NO-MONEY %amount%")
		                                       .withString("Wanted_Level.Contact.Disabled", "DISABLED"));
		EconomyHandler.setVaultEconomy(null);
		IdentitySettingsContract identity = mock(IdentitySettingsContract.class);
		when(identity.getBountyEachKillValue()).thenReturn(BigDecimal.ZERO);
		when(identity.getBountyTimerMultiple()).thenReturn(2.0);
		IdentitySettings.bind(identity);
		Command.setInformationManager(new InformationManager());
		bukkit.statics().when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
		doAnswer(invocation -> {
			events.add(invocation.getArgument(0));
			return null;
		}).when(bukkit.pluginManager()).callEvent(any());
		when(bukkit.scheduler().runTaskTimer(any(org.bukkit.plugin.Plugin.class), any(Runnable.class), anyLong(),
		                                     anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));

		JavaPlugin plugin = mock(JavaPlugin.class);
		player = mock(Player.class);
		when(player.getUniqueId()).thenReturn(UUID.randomUUID());
		when(player.isOnline()).thenReturn(true);
		user = new User<>(plugin, player, (p, raw) -> raw);
		user.getWanted().setOwner(player);
		UserManager<Player> users = mock(UserManager.class);
		when(users.getUser(player)).thenReturn(user);

		desk    = new ContactDesk(now::get);
		command = new ContactCommand(plugin, users, desk);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void wanted(int level, int cash) {
		user.getWanted().setMaxLevel(5);
		user.getWanted().setLevel(level);
		user.getEconomy().setAmount(Currency.of(cash));
		events.clear();
	}

	private int cash() {
		return user.getEconomy().getAmount().intValue();
	}

	@Test
	@DisplayName("a cop in view refuses, and no money moves")
	void seen_refuses() {
		wanted(3, 5000);
		desk.observe(player.getUniqueId(), EvasionState.SEEN);

		command.contact(player, 1);

		verify(player).sendMessage(contains("SEEN"));
		assertEquals(5000, cash());
		assertEquals(3, user.getWanted().getLevel());
	}

	@Test
	@DisplayName("inside the cooldown it refuses with the time left")
	void cooldown_refuses() {
		wanted(3, 5000);
		desk.startCooldown(player.getUniqueId());

		command.contact(player, 1);

		verify(player).sendMessage(contains("COOLDOWN 10m 0s"));
		assertEquals(5000, cash());
	}

	@Test
	@DisplayName("with no stars it refuses")
	void notWanted_refuses() {
		wanted(0, 5000);

		command.contact(player, 1);

		verify(player).sendMessage(contains("NOT-WANTED"));
		assertEquals(5000, cash());
	}

	@Test
	@DisplayName("a short wallet refuses and moves nothing, not even a star")
	void noMoney_refuses_andMovesNothing() {
		wanted(3, 999);

		command.contact(player, 1);

		verify(player).sendMessage(contains("NO-MONEY"));
		assertEquals(999, cash());
		assertEquals(3, user.getWanted().getLevel());
		assertEquals(0, desk.cooldownLeftMs(player.getUniqueId()));
	}

	@Test
	@DisplayName("two stars cost twice the price, lower the level tagged CONTACT and start the cooldown")
	void twoStars_chargesTwiceThePrice_andLowersWithContactCause() {
		wanted(4, 5000);

		command.contact(player, 2);

		assertEquals(3000, cash());
		assertEquals(2, user.getWanted().getLevel());
		assertTrue(desk.cooldownLeftMs(player.getUniqueId()) > 0);
		WantedLevelChangeEvent change = events.stream()
		                                      .filter(WantedLevelChangeEvent.class::isInstance)
		                                      .map(WantedLevelChangeEvent.class::cast)
		                                      .reduce((first, second) -> second)
		                                      .orElseThrow();
		assertEquals(WantedCause.CONTACT, change.getCause());
		verify(player).sendMessage(contains("USED 2"));
	}

	@Test
	@DisplayName("asking for more than the level or Max_Stars is clamped, and only that is charged")
	void clampsToTheCurrentLevel() {
		wanted(1, 5000);
		command.contact(player, 2);
		assertEquals(4000, cash(), "one star owed, one star paid");
		assertEquals(0, user.getWanted().getLevel());

		now.addAndGet(601_000L);
		wanted(5, 9000);
		command.contact(player, 5);
		assertEquals(7000, cash(), "Max_Stars 2 caps the request");
		assertEquals(3, user.getWanted().getLevel());
	}

	@Test
	@DisplayName("with Wanted.Contacts off it refuses and moves nothing")
	void disabled_refuses() throws Exception {
		SettingsFixture.write(dir, "Wanted:\n  Contacts:\n    Enable: false\n");
		SettingsFixture.initialize(dir);
		wanted(3, 5000);

		command.contact(player, 1);

		verify(player).sendMessage(contains("DISABLED"));
		assertEquals(5000, cash());
		assertEquals(3, user.getWanted().getLevel());
	}

}
