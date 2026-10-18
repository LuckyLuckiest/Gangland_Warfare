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
import org.luckyraven.gangland.copsncrooks.jail.JailRegistry;
import org.luckyraven.gangland.copsncrooks.jail.JailService;
import org.luckyraven.gangland.copsncrooks.wanted.WantedMessages;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChargeSheetSettings;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfig;
import org.luckyraven.gangland.copsncrooks.wanted.config.ChaseConfigLoader;
import org.luckyraven.gangland.copsncrooks.wanted.heat.HeatLedger;
import org.luckyraven.gangland.core.wanted.WantedCause;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;
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
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lane bag, P-BAG-6: the intake takes the bag inside {@code admit()} just before the wanted clear (cause ARREST), prints
 * {@code Bag.Seized}, and leaves the charge sheet exactly as it was.
 */
@DisplayName("JailIntakeService - bag seizure")
class JailIntakeBagTest {

	private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-00000000b46a");

	@TempDir
	Path tempDir;

	private final List<String> chat   = new ArrayList<>();
	private final List<String> events = new ArrayList<>();

	private DetainmentEconomyContract economy;
	private WantedClearContract       wanted;
	private DetainmentService         service;
	private JailIntakeService         intake;
	private Player                    player;
	private BukkitStatics             bukkit;

	@BeforeAll
	static void primeMoneySymbol() throws ReflectiveOperationException {
		Field field = Settings.class.getDeclaredField("moneySymbol");
		field.setAccessible(true);
		if (field.get(null) == null) {
			field.set(null, "$");
		}
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@BeforeEach
	void setUp() throws IOException {
		bukkit = BukkitStatics.install();
		player = mock(Player.class);
		when(player.isOnline()).thenReturn(true);
		when(player.getUniqueId()).thenReturn(ID);
		when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
		doAnswer(inv -> chat.add(ChatColor.stripColor(inv.getArgument(0)))).when(player).sendMessage(any(String.class));

		JailRegistry jails = new JailRegistry();
		jails.setJailLocation(1, new Location(null, 0, 0, 0), 5);
		DetainmentRegistry registry = new DetainmentRegistry(mock(IRepository.class), jails);

		service = mock(DetainmentService.class);
		doAnswer(inv -> registry.getDetainedPlayers().put(ID, new DetainedPlayer(ID, 1, DetainmentState.JAILED)))
				.when(service).jail(eq(player), eq(1));

		wanted = mock(WantedClearContract.class);
		when(wanted.getWantedLevel(ID)).thenReturn(2);
		doAnswer(inv -> events.add("clear " + inv.getArgument(1))).when(wanted).clearWanted(eq(ID), any(WantedCause.class));

		DetainmentCostsContract costs = mock(DetainmentCostsContract.class);
		when(costs.computeSentenceSeconds(any(int.class))).thenReturn(300);

		HeatLedger ledger = mock(HeatLedger.class);
		when(ledger.chaseCrimes(ID)).thenReturn(List.of());

		ChaseConfigLoader chase = mock(ChaseConfigLoader.class);
		when(chase.get()).thenReturn(new ChaseConfig(ChaseConfig.DEFAULT.heat(), ChaseConfig.DEFAULT.evasion(),
		                                             ChaseConfig.DEFAULT.hud(), ChargeSheetSettings.DEFAULT));

		economy = mock(DetainmentEconomyContract.class);
		when(economy.getBalance(player)).thenReturn(5000.0);
		when(economy.tryCharge(any(), any(double.class))).thenReturn(DetainmentEconomyContract.ChargeResult.SUCCESS);

		intake = new JailIntakeService(service, registry, mock(JailService.class), jails,
		                               mock(SeizedInventoryService.class), wanted, mock(PaperworkItemFactory.class),
		                               costs, mock(DetainmentSoundContract.class), economy, chase, ledger, messages());
	}

	private WantedMessages messages() throws IOException {
		Path file = tempDir.resolve("wanted_messages.yml");
		Files.writeString(file, "# fallbacks only\n", StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new WantedMessages(fileManager);
	}

	private String seizedLine(double amount) throws IOException {
		return ChatColor.stripColor(messages().format(WantedMessages.Key.BAG_SEIZED, java.util.Map.of(
				"money_symbol", Settings.getMoneySymbol(), "amount",
				Settings.formatAmount(BigDecimal.valueOf(amount)))));
	}

	@Test
	@DisplayName("the seizure runs inside admit() before clearWanted(ARREST) and prints Bag.Seized")
	void intakeSeizesBeforeClear() throws IOException {
		ToDoubleFunction<Player> seizure = p -> {
			events.add("seize");
			return 800.0;
		};
		intake.setBagSeizure(seizure);

		assertTrue(intake.admit(player));

		assertEquals(List.of("seize", "clear " + WantedCause.ARREST), events);
		assertTrue(chat.contains(seizedLine(800)), chat.toString());
		InOrder order = inOrder(wanted);
		order.verify(wanted).clearWanted(ID, WantedCause.ARREST);
	}

	@Test
	@DisplayName("the charge sheet is unchanged next to the seizure: header, fine and paid lines still print")
	void chargeSheetUnchanged() throws IOException {
		intake.setBagSeizure(p -> 800.0);

		intake.admit(player);

		String header = ChatColor.stripColor(messages().format(WantedMessages.Key.SHEET_HEADER, java.util.Map.of()));
		assertTrue(chat.contains(header), chat.toString());
		assertTrue(chat.stream().anyMatch(l -> l.startsWith("Fine:")), chat.toString());
		assertTrue(chat.stream().anyMatch(l -> l.startsWith("Paid from your wallet:")), chat.toString());
	}

	@Test
	@DisplayName("a death-commit has no sheet but still prints Bag.Seized")
	void deathCommitStillPrintsSeized() throws IOException {
		intake.setBagSeizure(p -> 450.0);

		intake.admit(player, true);

		assertEquals(List.of(seizedLine(450)), chat);
	}

	@Test
	@DisplayName("an empty bag prints no Bag.Seized line")
	void emptyBagPrintsNothing() throws IOException {
		intake.setBagSeizure(p -> 0.0);

		intake.admit(player, true);

		assertTrue(chat.isEmpty(), chat.toString());
	}

	@Test
	@DisplayName("no jail free: the player is released and the bag is not touched")
	void noJailLeavesTheBag() {
		JailRegistry empty = new JailRegistry();
		JailIntakeService noJail = new JailIntakeService(service, new DetainmentRegistry(mock(IRepository.class), empty),
		                                                 mock(JailService.class), empty,
		                                                 mock(SeizedInventoryService.class), wanted,
		                                                 mock(PaperworkItemFactory.class),
		                                                 mock(DetainmentCostsContract.class),
		                                                 mock(DetainmentSoundContract.class), economy,
		                                                 mock(ChaseConfigLoader.class), mock(HeatLedger.class), null);
		ToDoubleFunction<Player> seizure = mock(ToDoubleFunction.class);
		noJail.setBagSeizure(seizure);

		assertEquals(false, noJail.admit(player));

		verify(seizure, never()).applyAsDouble(any());
	}
}
