package org.luckyraven.gangland.gadget.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.testsupport.BukkitRegistryFixture;
import org.luckyraven.gangland.gadget.car.Car;
import org.luckyraven.gangland.gadget.car.config.CarAddon;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins WS7 G4 review I2 (same root as {@code JetpackGiveCommandAmountGuardTest} — the review's own docket
 * candidate names both commands): {@code giveCarItem}'s {@code slots = ceil(amount / maxStackSize)} could produce
 * a negative array size for a negative amount. The command layer now rejects {@code amount <= 0} before ever
 * calling this method; this test pins the method's own defensive floor.
 */
@DisplayName("CarGiveCommand.giveCarItem — amount guard (WS7 G4 review I2)")
class CarGiveCommandAmountGuardTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();

		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	@Test
	@DisplayName("a negative amount gives zero items and does not throw NegativeArraySizeException")
	void negativeAmount_noItemNoException() throws Exception {
		JavaPlugin            plugin      = mock(JavaPlugin.class);
		Tree<Argument>        tree        = new Tree<>();
		Argument              parent      = new Argument(plugin, "car", tree);
		tree.add(parent.getNode());
		UserManager<Player>   userManager = mock(UserManager.class);
		CarAddon               carAddon   = mock(CarAddon.class);

		Car car = Car.builder().carId("car").itemMaterial(Material.MINECART).build();
		when(carAddon.getCar("car")).thenReturn(car);

		CarGiveCommand giveCommand = new CarGiveCommand(plugin, tree, parent, userManager, carAddon);

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Method method = CarGiveCommand.class.getDeclaredMethod("giveCarItem", Player.class, String.class, int.class);
		method.setAccessible(true);

		assertDoesNotThrow(() -> method.invoke(giveCommand, player, "car", -1));

		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(0, captor.getValue().length, "a negative amount must give zero items");
	}
}
