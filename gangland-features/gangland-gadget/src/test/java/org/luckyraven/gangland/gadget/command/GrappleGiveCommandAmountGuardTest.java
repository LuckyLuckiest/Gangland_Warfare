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
import org.luckyraven.gangland.gadget.grapple.Grapple;
import org.luckyraven.gangland.gadget.grapple.config.GrappleAddon;
import org.luckyraven.gangland.gadget.grapple.message.GrappleMessages;
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
 * Mirrors {@code JetpackGiveCommandAmountGuardTest} (WS7 G4 review I2): {@code giveGrappleItem}'s
 * {@code slots = ceil(amount / maxStackSize)} would produce a negative array size for a negative amount
 * ({@code new ItemStack[-1]} -> {@code NegativeArraySizeException}) if the command layer's {@code amount <= 0}
 * rejection were ever bypassed. This test pins the method's own defensive floor directly, independent of the
 * command layer's guard.
 */
@DisplayName("GrappleGiveCommand.giveGrappleItem — amount guard (mirrors WS7 G4 review I2)")
class GrappleGiveCommandAmountGuardTest {

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
		Argument        parent       = new Argument(plugin, "grapple", tree);
		tree.add(parent.getNode());
		GrappleAddon    grappleAddon = mock(GrappleAddon.class);
		GrappleMessages messages     = mock(GrappleMessages.class);

		Grapple grapple = Grapple.builder().grappleId("grapple").material(Material.FISHING_ROD).build();
		when(grappleAddon.getGrapple("grapple")).thenReturn(grapple);

		GrappleGiveCommand giveCommand = new GrappleGiveCommand(plugin, tree, parent, grappleAddon, messages);

		Player          player    = mock(Player.class);
		PlayerInventory inventory = mock(PlayerInventory.class);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Method method = GrappleGiveCommand.class.getDeclaredMethod("giveGrappleItem", Player.class, String.class,
		                                                          int.class);
		method.setAccessible(true);

		assertDoesNotThrow(() -> method.invoke(giveCommand, player, "grapple", -1));

		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(0, captor.getValue().length, "a negative amount must give zero items");
	}
}
