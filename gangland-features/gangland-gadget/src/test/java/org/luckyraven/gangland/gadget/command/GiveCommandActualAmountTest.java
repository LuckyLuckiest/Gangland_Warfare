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
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins gi=55 (P3): {@code giveCarItem}/{@code giveJetpackItem} clamp the requested amount to
 * {@code 36 * maxStackSize} (WS7 G4's crash-guard clamp) but returned only a {@code boolean}, so the caller built
 * the "gave you N" message from the raw, pre-clamp requested amount — a request exceeding the cap reported giving
 * more than was actually placed/dropped.
 */
@DisplayName("Gadget give commands — gave-message reports the actual capped amount (gi=55)")
class GiveCommandActualAmountTest {

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	private static Player playerWithEmptyInventory() {
		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
		return player;
	}

	@Test
	@DisplayName("CarGiveCommand.giveCarItem returns the actual capped amount, not the requested one")
	void giveCarItem_returnsActualCappedAmount() throws Exception {
		JavaPlugin           plugin      = mock(JavaPlugin.class);
		Tree<Argument>       tree        = new Tree<>();
		Argument             parent      = new Argument(plugin, "car", tree);
		tree.add(parent.getNode());
		UserManager<Player>  userManager = mock(UserManager.class);
		CarAddon             carAddon    = mock(CarAddon.class);

		Car car = Car.builder().carId("car").itemMaterial(Material.MINECART).build();
		when(carAddon.getCar("car")).thenReturn(car);

		CarGiveCommand giveCommand = new CarGiveCommand(plugin, tree, parent, userManager, carAddon);

		Method method = CarGiveCommand.class.getDeclaredMethod("giveCarItem", Player.class, String.class, int.class);
		method.setAccessible(true);

		Object actual = method.invoke(giveCommand, playerWithEmptyInventory(), "car", 100);

		assertEquals(36, actual, "must return the actual (capped) amount given, not the requested 100");
	}

	@Test
	@DisplayName("JetpackGiveCommand.giveJetpackItem returns the actual capped amount, not the requested one")
	void giveJetpackItem_returnsActualCappedAmount() throws Exception {
		JavaPlugin      plugin       = mock(JavaPlugin.class);
		Tree<Argument>  tree         = new Tree<>();
		Argument        parent       = new Argument(plugin, "jetpack", tree);
		tree.add(parent.getNode());
		JetpackAddon    jetpackAddon = mock(JetpackAddon.class);
		JetpackMessages messages     = mock(JetpackMessages.class);

		Jetpack jetpack = Jetpack.builder().jetpackId("jetpack").material(Material.IRON_CHESTPLATE).build();
		when(jetpackAddon.getJetpack("jetpack")).thenReturn(jetpack);

		JetpackGiveCommand giveCommand = new JetpackGiveCommand(plugin, tree, parent, jetpackAddon, messages);

		Method method = JetpackGiveCommand.class.getDeclaredMethod("giveJetpackItem", Player.class, String.class,
		                                                          int.class);
		method.setAccessible(true);

		Object actual = method.invoke(giveCommand, playerWithEmptyInventory(), "jetpack", 100);

		assertEquals(36, actual, "must return the actual (capped) amount given, not the requested 100");
	}
}
