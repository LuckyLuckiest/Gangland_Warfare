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
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
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
 * Pins WS7 G4 review I2: {@code giveJetpackItem}'s {@code slots = ceil(amount / maxStackSize)} produced a
 * negative array size for a negative amount ({@code new ItemStack[-1]} → {@code NegativeArraySizeException}) —
 * jetpacks are armour, {@code maxStackSize == 1}, so this crashed on the very first negative input. The command
 * layer now rejects {@code amount <= 0} before ever calling this method; this test pins the method's own
 * defensive floor (a non-positive amount clamps to zero items given, never a negative array size), so the fix
 * holds regardless of caller.
 */
@DisplayName("JetpackGiveCommand.giveJetpackItem — amount guard (WS7 G4 review I2)")
class JetpackGiveCommandAmountGuardTest {

	@BeforeAll
	static void bootstrapBukkitRegistry() {
		BukkitRegistryFixture.install();

		// Constructing a SubArgument registers its derived permission with Bukkit's PluginManager
		// (Argument.setPermission -> addPermission); BukkitRegistryFixture does not stub one.
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	@Test
	@DisplayName("a negative amount gives zero items and does not throw NegativeArraySizeException")
	void negativeAmount_noItemNoException() throws Exception {
		JavaPlugin      plugin       = mock(JavaPlugin.class);
		Tree<Argument>  tree         = new Tree<>();
		Argument        parent       = new Argument(plugin, "jetpack", tree);
		tree.add(parent.getNode());
		JetpackAddon    jetpackAddon = mock(JetpackAddon.class);
		JetpackMessages messages     = mock(JetpackMessages.class);

		Jetpack jetpack = Jetpack.builder().jetpackId("jetpack").material(Material.IRON_CHESTPLATE).build();
		when(jetpackAddon.getJetpack("jetpack")).thenReturn(jetpack);

		JetpackGiveCommand giveCommand = new JetpackGiveCommand(plugin, tree, parent, jetpackAddon, messages);

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Method method = JetpackGiveCommand.class.getDeclaredMethod("giveJetpackItem", Player.class, String.class,
		                                                          int.class);
		method.setAccessible(true);

		assertDoesNotThrow(() -> method.invoke(giveCommand, player, "jetpack", -1));

		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(0, captor.getValue().length, "a negative amount must give zero items");
	}
}
