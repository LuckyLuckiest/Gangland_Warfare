package org.luckyraven.gangland.copsncrooks.detainment.intake;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.copsncrooks.detainment.DetainedPlayer;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentRegistry;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentState;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentCostsContract;
import org.luckyraven.gangland.copsncrooks.detainment.economy.DetainmentEconomyContract;
import org.luckyraven.gangland.copsncrooks.detainment.inventory.SeizedInventoryService;
import org.luckyraven.gangland.copsncrooks.detainment.paperwork.PaperworkItemFactory;
import org.luckyraven.gangland.copsncrooks.detainment.sound.DetainmentSoundContract;
import org.luckyraven.gangland.copsncrooks.detainment.wanted.WantedClearContract;
import org.luckyraven.gangland.copsncrooks.events.police.ArrestedEvent;
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChargeSheetSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.CrimeRecord;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link JailIntakeService#admit}: the charge sheet. The fine comes out of the wallet only, the part the wallet cannot
 * cover is served as extra jail time (capped), the crimes are listed in first-seen order, and a death-commit or a
 * disabled sheet leaves the sentence exactly as before.
 */
@DisplayName("JailIntakeService charge sheet")
class JailIntakeServiceTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000a11c");

	@TempDir
	Path tempDir;

	private final List<String>      chat    = new ArrayList<>();
	private final List<CrimeRecord> crimes  = new ArrayList<>();
	private final FakeEconomy       economy = new FakeEconomy();
	private       DetainmentRegistry registry;
	private       WantedClearContract wanted;
	private       HeatLedger          ledger;
	private       ChaseConfigLoader   chase;
	private       Player              player;
	private       JailIntakeService   intake;
	private       int                 level   = 2;

	/** Colouring a line reads {@code Settings.moneySymbol}, which only a loaded settings.yml sets. */
	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	private BukkitStatics bukkit;

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install(); // the admit fires ArrestedEvent through Bukkit
		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(ID);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		doAnswer(inv -> chat.add(inv.getArgument(0))).when(player).sendMessage(any(String.class));

		JailRegistry jails = new JailRegistry();
		jails.setJailLocation(1, new Location(null, 0, 0, 0), 5);
		registry = new DetainmentRegistry(mock(IRepository.class), jails);

		DetainmentService service = mock(DetainmentService.class);
		doAnswer(inv -> registry.getDetainedPlayers().put(ID, new DetainedPlayer(ID, 1, DetainmentState.JAILED)))
				.when(service).jail(eq(player), eq(1));

		wanted = mock(WantedClearContract.class);
		when(wanted.getWantedLevel(ID)).thenAnswer(inv -> level);

		DetainmentCostsContract costs = mock(DetainmentCostsContract.class);
		// 120 s base + 90 s a star: 300 s at two stars (the Done-when 180 + 120)
		when(costs.computeSentenceSeconds(any(int.class))).thenAnswer(inv -> 120 + 90 * (int) inv.getArgument(0));

		ledger = mock(HeatLedger.class);
		when(ledger.chaseCrimes(ID)).thenAnswer(inv -> List.copyOf(crimes));

		chase = mock(ChaseConfigLoader.class);
		sheet(ChargeSheetSettings.DEFAULT);

		intake = new JailIntakeService(service, registry, mock(JailService.class), jails,
		                               mock(SeizedInventoryService.class), wanted, mock(PaperworkItemFactory.class),
		                               costs, mock(DetainmentSoundContract.class), economy, chase, ledger,
		                               messages());
	}

	private WantedMessages messages() throws IOException {
		Path file = tempDir.resolve("wanted_messages.yml");
		Files.writeString(file, "# fallbacks only\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new WantedMessages(fileManager);
	}

	private void sheet(ChargeSheetSettings settings) {
		when(chase.get()).thenReturn(new ChaseConfig(ChaseConfig.DEFAULT.heat(), ChaseConfig.DEFAULT.evasion(),
		                                             ChaseConfig.DEFAULT.hud(), settings));
	}

	private void crime(String id) {
		crimes.add(new CrimeRecord(id, 10, 0L, mock(Location.class)));
	}

	private String money(double amount) {
		return Settings.getMoneySymbol() + Settings.formatAmount(BigDecimal.valueOf(amount));
	}

	private List<String> plainChat() {
		return chat.stream().map(ChatColor::stripColor).toList();
	}

	private DetainedPlayer row() {
		return registry.getDetainedPlayers().get(ID);
	}

	private long sentenceSeconds() {
		return Math.round((row().getSentenceExpiresAt() - System.currentTimeMillis()) / 1000.0);
	}

	@Test
	@DisplayName("a $300 wallet against a $700 fine pays 300 and serves ceil(400 x 0.1) = 40 extra seconds")
	void busted300Against700_pays300_andServes40ExtraSeconds() {
		economy.balance = 300;
		crime("car_theft");

		assertTrue(intake.admit(player));

		assertEquals(300.0, row().getFinePaid());
		assertEquals(40, row().getFineExtraSeconds());
		assertEquals(0, economy.balance);
		assertEquals(340, sentenceSeconds(), "180 + 120 + 40");

		List<String> lines = plainChat();
		assertTrue(lines.stream().anyMatch(l -> l.contains("Paid from your wallet: " + money(300))), lines.toString());
		assertTrue(lines.stream().anyMatch(l -> l.contains("Unpaid " + money(400)) && l.contains("+40s")),
		           lines.toString());
	}

	@Test
	@DisplayName("a rich player pays the whole fine and gets no extra time")
	void richPlayer_paysTheWholeFine_noExtraTime() {
		economy.balance = 5000;

		intake.admit(player);

		assertEquals(700.0, row().getFinePaid());
		assertEquals(0, row().getFineExtraSeconds());
		assertEquals(4300, economy.balance);
		assertEquals(300, sentenceSeconds());
		assertTrue(plainChat().stream().noneMatch(l -> l.contains("Unpaid")), "no extra-time line");
	}

	@Test
	@DisplayName("a broke player pays nothing and serves the whole fine as time")
	void brokePlayer_paysNothing_servesTheWholeFineAsTime() {
		level           = 5;
		economy.balance = 0;

		intake.admit(player);

		assertEquals(0.0, row().getFinePaid());
		assertEquals(145, row().getFineExtraSeconds(), "1,450 x 0.1");
		assertEquals(570 + 145, sentenceSeconds());
		assertEquals(0, economy.charges, "nothing to take, so the economy is never asked");
	}

	@Test
	@DisplayName("the extra time is capped at Max_Extra_Seconds")
	void extraTime_isCappedAtMaxExtraSeconds() {
		level = 5;
		sheet(new ChargeSheetSettings(true, 200, 250, 10000, 1.0, 600));

		intake.admit(player);

		assertEquals(600, row().getFineExtraSeconds());
	}

	@Test
	@DisplayName("a death-commit already paid the hospital bill, so it gets no sheet")
	void deathCommit_chargesNoSheet() {
		when(player.isDead()).thenReturn(true);
		economy.balance = 5000;

		intake.admit(player);

		assertNull(row().getFinePaid());
		assertEquals(5000, economy.balance);
		assertEquals(300, sentenceSeconds());
		assertTrue(chat.isEmpty());
	}

	@Test
	@DisplayName("the deathCommit flag skips the sheet for a LIVE player (a handcuffed player downed, Ruling R48)")
	void deathCommitFlag_skipsTheSheet_forALivePlayer() {
		economy.balance = 5000;

		intake.admit(player, true);

		assertNull(row().getFinePaid());
		assertEquals(5000, economy.balance);
		assertEquals(300, sentenceSeconds());
		assertTrue(chat.isEmpty());
	}

	@Test
	@DisplayName("the transit timer firing while the cuffed player is downed pays only the ward bill, no sheet (D23)")
	void transitTimerWhileDowned_chargesNoSheet() {
		UUID id = player.getUniqueId();
		org.luckyraven.gangland.core.downed.DownedPlayerRegistry.add(id);
		try {
			economy.balance = 5000;

			intake.admit(player);

			assertNull(row().getFinePaid());
			assertEquals(5000, economy.balance);
			assertEquals(300, sentenceSeconds());
		} finally {
			org.luckyraven.gangland.core.downed.DownedPlayerRegistry.remove(id);
		}
	}

	@Test
	@DisplayName("Charge_Sheet.Enable false charges nothing and leaves the sentence alone")
	void sheetDisabled_chargesNothing_sentenceUnchanged() {
		sheet(new ChargeSheetSettings(false, 200, 250, 10000, 0.1, 600));
		economy.balance = 5000;

		intake.admit(player);

		assertNull(row().getFinePaid());
		assertNull(row().getFineExtraSeconds());
		assertEquals(5000, economy.balance);
		assertEquals(300, sentenceSeconds());
		assertTrue(chat.isEmpty());
	}

	@Test
	@DisplayName("an admin throw of a 0-star player with no crimes charges no sheet and adds no time")
	void zeroStarsNoCrimes_chargesNoSheet() {
		level           = 0;
		economy.balance = 5000;

		intake.admit(player);

		assertNull(row().getFinePaid());
		assertNull(row().getFineExtraSeconds());
		assertEquals(5000, economy.balance);
		assertEquals(120, sentenceSeconds(), "the base sentence only");
		assertTrue(chat.isEmpty());
	}

	@Test
	@DisplayName("crimes are listed once each, with counts, in first-seen order")
	void crimesAreListedGroupedInFirstSeenOrder() {
		for (String id : List.of("car_theft", "kill_cop", "car_theft", "brandish_near_cop", "kill_cop", "car_theft")) {
			crime(id);
		}

		intake.admit(player);

		List<String> lines = plainChat().stream().filter(l -> l.startsWith("- ")).toList();
		assertEquals(List.of("- car theft x3", "- kill cop x2", "- brandish near cop x1"), lines);
	}

	@Test
	@DisplayName("the chase is read before the wanted clear wipes it")
	void chaseIsReadBeforeTheWantedClear() {
		intake.admit(player);

		InOrder order = inOrder(ledger, wanted);
		order.verify(ledger).chaseCrimes(ID);
		order.verify(wanted).clearWanted(eq(ID), any(WantedCause.class));
	}

	@Test
	@DisplayName("the admit fires ArrestedEvent after the wanted clear: a zero-star arrest still ends the chase (0.16.1 T-187)")
	void admit_firesArrestedEvent_afterTheWantedClear() {
		intake.admit(player);

		InOrder order = inOrder(wanted, bukkit.pluginManager());
		order.verify(wanted).clearWanted(eq(ID), any(WantedCause.class));
		order.verify(bukkit.pluginManager()).callEvent(any(ArrestedEvent.class));
	}

	@Test
	@DisplayName("the arrest clears the stars with the ARREST cause")
	void arrestClearsWithTheArrestCause() {
		intake.admit(player);

		ArgumentCaptor<WantedCause> cause = ArgumentCaptor.forClass(WantedCause.class);
		verify(wanted).clearWanted(eq(ID), cause.capture());
		assertEquals(WantedCause.ARREST, cause.getValue());
	}

	/** A wallet that remembers what it was charged. */
	private static final class FakeEconomy implements DetainmentEconomyContract {
		double balance;
		int    charges;

		@Override
		public ChargeResult tryCharge(Player player, double amount) {
			charges++;
			balance -= amount;
			return ChargeResult.SUCCESS;
		}

		@Override
		public double getBalance(Player player) {
			return balance;
		}
	}
}
