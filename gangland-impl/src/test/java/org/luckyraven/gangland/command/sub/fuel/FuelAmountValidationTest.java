package org.luckyraven.gangland.command.sub.fuel;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.item.fuel.Fuel;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.MockedStatic;

import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterises the amount handling shared by the fuel add, remove, refuel and defuel commands: the held item must
 * have fuel capacity BEFORE the amount is parsed, a non-integer is MUST_BE_NUMBERS, an amount of zero or less is
 * FUEL_AMOUNT_INVALID, and a valid amount reaches the command's own arithmetic.
 */
@DisplayName("Fuel add/remove/refuel/defuel - amount validation")
class FuelAmountValidationTest {

	@TempDir
	static Path tempDir;

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(java.util.Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);

		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				              .withString("Errors.Prefix", "")
				              .withString("Errors.Must_Be_Numbers", "BAD %command%")
				              .withString("Fuel.No_Capacity", "NO-CAPACITY")
				              .withString("Fuel.Amount_Invalid", "AMOUNT-INVALID")
				              .withString("Commands.Fuel.Capacity_Increased", "ADD %amount% %new_max%")
				              .withString("Commands.Fuel.Capacity_Decreased", "REMOVE %amount% %new_max%")
				              .withString("Commands.Fuel.Refueled_Amount", "REFUEL %added% %current% %max%")
				              .withString("Commands.Fuel.Defueled_Amount", "DEFUEL %removed% %current% %max%"));
	}

	private interface Factory {

		Argument create(JavaPlugin plugin, Tree<Argument> tree, Argument parent, UserManager<Player> userManager);
	}

	private static final Factory[] COMMANDS = {
			FuelAddCommand::new, FuelRemoveCommand::new, FuelRefuelCommand::new, FuelDefuelCommand::new
	};

	private static final String[] NAMES = {"add", "remove", "refuel", "defuel"};

	private static final class Fixture {

		final Argument     amount;
		final Player       player;
		final User<Player> user;
		final ItemStack    held;

		@SuppressWarnings("unchecked")
		Fixture(Factory factory) {
			JavaPlugin          plugin      = mock(JavaPlugin.class);
			Tree<Argument>      tree        = new Tree<>();
			Argument            parent      = new Argument(plugin, "fuel", tree);
			tree.add(parent.getNode());
			UserManager<Player> userManager = mock(UserManager.class);

			Argument command = factory.create(plugin, tree, parent, userManager);
			amount = command.getNode().getChildren().get(0).getData();

			held   = mock(ItemStack.class);
			player = mock(Player.class);
			user   = mock(User.class);
			PlayerInventory inventory = mock(PlayerInventory.class);
			when(player.getInventory()).thenReturn(inventory);
			when(inventory.getItemInMainHand()).thenReturn(held);
			when(userManager.getUser(player)).thenReturn(user);
		}

		void run(String name, String raw) {
			amount.getAction().accept(null, player, new String[]{"fuel", name, raw});
		}
	}

	private static MockedStatic<Fuel> fuel(boolean capacity) {
		MockedStatic<Fuel> fuel = mockStatic(Fuel.class);
		fuel.when(() -> Fuel.hasFuelCapacity(any())).thenReturn(capacity);
		fuel.when(() -> Fuel.readFuelMax(any())).thenReturn(10);
		fuel.when(() -> Fuel.readFuelCurrent(any())).thenReturn(3);
		return fuel;
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3})
	@DisplayName("no fuel capacity: NO_CAPACITY only, the amount is not even looked at")
	void noCapacity_gateComesBeforeTheParse(int index) {
		Fixture f = new Fixture(COMMANDS[index]);

		try (MockedStatic<Fuel> fuel = fuel(false)) {
			f.run(NAMES[index], "not-a-number");
			f.run(NAMES[index], "-4");

			fuel.verify(() -> Fuel.setMaxFuel(any(), anyInt()), never());
			fuel.verify(() -> Fuel.writeFuelCurrent(any(), anyInt()), never());
		}

		verify(f.user, times(2)).sendMessage(contains("NO-CAPACITY"));
		verify(f.user, times(2)).sendMessage(anyString());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3})
	@DisplayName("a non-integer: MUST_BE_NUMBERS names the token and nothing is changed")
	void notAnInteger_isMustBeNumbers(int index) {
		for (String raw : new String[]{"abc", "1.5", " 5", "", "2147483648"}) {
			Fixture f = new Fixture(COMMANDS[index]);

			try (MockedStatic<Fuel> fuel = fuel(true)) {
				f.run(NAMES[index], raw);

				fuel.verify(() -> Fuel.setMaxFuel(any(), anyInt()), never());
				fuel.verify(() -> Fuel.writeFuelCurrent(any(), anyInt()), never());
			}

			verify(f.user).sendMessage("BAD " + raw);
			verify(f.user, times(1)).sendMessage(anyString());
		}
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 2, 3})
	@DisplayName("zero or negative: AMOUNT_INVALID and nothing is changed")
	void zeroOrNegative_isInvalid(int index) {
		for (String raw : new String[]{"0", "-1", "-2147483648", "-0"}) {
			Fixture f = new Fixture(COMMANDS[index]);

			try (MockedStatic<Fuel> fuel = fuel(true)) {
				f.run(NAMES[index], raw);

				fuel.verify(() -> Fuel.setMaxFuel(any(), anyInt()), never());
				fuel.verify(() -> Fuel.writeFuelCurrent(any(), anyInt()), never());
			}

			verify(f.user).sendMessage(contains("AMOUNT-INVALID"));
			verify(f.user, times(1)).sendMessage(anyString());
		}
	}

	@Test
	@DisplayName("add: a valid amount raises the maximum (10 + 5, the sign '+5' parses)")
	void add_validAmount() {
		for (String raw : new String[]{"5", "+5"}) {
			Fixture f = new Fixture(FuelAddCommand::new);

			try (MockedStatic<Fuel> fuel = fuel(true)) {
				f.run("add", raw);

				fuel.verify(() -> Fuel.setMaxFuel(f.held, 15));
			}

			verify(f.user).sendMessage(contains("ADD 5 15"));
			verify(f.user, times(1)).sendMessage(anyString());
		}
	}

	@Test
	@DisplayName("remove: a valid amount lowers the maximum, floored at 0")
	void remove_validAmount() {
		Fixture f = new Fixture(FuelRemoveCommand::new);

		try (MockedStatic<Fuel> fuel = fuel(true)) {
			f.run("remove", "4");
			fuel.verify(() -> Fuel.setMaxFuel(f.held, 6));

			f.run("remove", "25");
			fuel.verify(() -> Fuel.setMaxFuel(f.held, 0));
		}

		verify(f.user).sendMessage(contains("REMOVE 4 6"));
		verify(f.user).sendMessage(contains("REMOVE 25 0"));
	}

	@Test
	@DisplayName("refuel: a valid amount raises the current fuel, capped at the maximum")
	void refuel_validAmount() {
		Fixture f = new Fixture(FuelRefuelCommand::new);

		try (MockedStatic<Fuel> fuel = fuel(true)) {
			f.run("refuel", "4");
			fuel.verify(() -> Fuel.writeFuelCurrent(f.held, 7));

			f.run("refuel", "50");
			fuel.verify(() -> Fuel.writeFuelCurrent(f.held, 10));
		}

		verify(f.user).sendMessage(contains("REFUEL 4 7 10"));
		verify(f.user).sendMessage(contains("REFUEL 7 10 10"));
	}

	@Test
	@DisplayName("add: an amount that overflows int saturates the maximum instead of wrapping to zero")
	void add_hugeAmount_saturates() {
		Fixture f = new Fixture(FuelAddCommand::new);

		try (MockedStatic<Fuel> fuel = fuel(true)) {
			f.run("add", "2147483647");

			fuel.verify(() -> Fuel.setMaxFuel(f.held, Integer.MAX_VALUE));
		}

		verify(f.user).sendMessage(contains("ADD 2147483647 2147483647"));
	}

	@Test
	@DisplayName("refuel: an amount that overflows int fills the tank instead of emptying it")
	void refuel_hugeAmount_fillsTheTank() {
		Fixture f = new Fixture(FuelRefuelCommand::new);

		try (MockedStatic<Fuel> fuel = fuel(true)) {
			f.run("refuel", "2147483647");

			fuel.verify(() -> Fuel.writeFuelCurrent(f.held, 10));
		}

		verify(f.user).sendMessage(contains("REFUEL 7 10 10"));
	}

	@Test
	@DisplayName("defuel: a valid amount lowers the current fuel, floored at 0")
	void defuel_validAmount() {
		Fixture f = new Fixture(FuelDefuelCommand::new);

		try (MockedStatic<Fuel> fuel = fuel(true)) {
			f.run("defuel", "2");
			fuel.verify(() -> Fuel.writeFuelCurrent(f.held, 1));

			f.run("defuel", "50");
			fuel.verify(() -> Fuel.writeFuelCurrent(f.held, 0));
		}

		verify(f.user).sendMessage(contains("DEFUEL 2 1 10"));
		verify(f.user).sendMessage(contains("DEFUEL 3 0 10"));
	}
}
