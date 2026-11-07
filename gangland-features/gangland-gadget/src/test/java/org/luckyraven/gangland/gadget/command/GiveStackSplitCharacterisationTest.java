package org.luckyraven.gangland.gadget.command;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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
import org.luckyraven.gangland.gadget.jetpack.Jetpack;
import org.luckyraven.gangland.gadget.jetpack.config.JetpackAddon;
import org.luckyraven.gangland.gadget.jetpack.message.JetpackMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Characterises the stack-split / addItem / drop-leftovers body of {@code giveJetpackItem} and
 * {@code giveGrappleItem} before it is extracted into one shared helper: the number and size of the stacks, how many
 * times {@code buildItem} is called, which leftovers are dropped (in {@code addItem}'s map-value order) and the
 * null-world guard.
 */
@DisplayName("Jetpack/Grapple give — stack split, leftover drop and null-world guard")
class GiveStackSplitCharacterisationTest {

	@BeforeAll
	static void bootstrap() {
		BukkitRegistryFixture.install();
		PluginManager pluginManager = mock(PluginManager.class);
		when(pluginManager.getPermissions()).thenReturn(Set.of());
		when(Bukkit.getServer().getPluginManager()).thenReturn(pluginManager);
	}

	private static ItemStack stick() {
		return new ItemStack(Material.STICK);
	}

	private static Method jetpackMethod() throws Exception {
		Method method = JetpackGiveCommand.class.getDeclaredMethod("giveJetpackItem", Player.class, String.class,
		                                                          int.class);
		method.setAccessible(true);
		return method;
	}

	private static Method grappleMethod() throws Exception {
		Method method = GrappleGiveCommand.class.getDeclaredMethod("giveGrappleItem", Player.class, String.class,
		                                                          int.class);
		method.setAccessible(true);
		return method;
	}

	private record Jet(JetpackGiveCommand command, Jetpack jetpack) {}

	private static Jet jetpackCommand() {
		JavaPlugin      plugin = mock(JavaPlugin.class);
		Tree<Argument>  tree   = new Tree<>();
		Argument        parent = new Argument(plugin, "jetpack", tree);
		tree.add(parent.getNode());
		JetpackAddon    addon  = mock(JetpackAddon.class);
		Jetpack         jet    = mock(Jetpack.class);
		when(addon.getJetpack("jp")).thenReturn(jet);
		when(jet.buildItem(any(Player.class))).thenAnswer(invocation -> stick());
		return new Jet(new JetpackGiveCommand(plugin, tree, parent, addon, mock(JetpackMessages.class)), jet);
	}

	private record Grap(GrappleGiveCommand command, Grapple grapple) {}

	private static Grap grappleCommand() {
		JavaPlugin      plugin = mock(JavaPlugin.class);
		Tree<Argument>  tree   = new Tree<>();
		Argument        parent = new Argument(plugin, "grapple", tree);
		tree.add(parent.getNode());
		GrappleAddon    addon  = mock(GrappleAddon.class);
		Grapple         grap   = mock(Grapple.class);
		when(addon.getGrapple("gr")).thenReturn(grap);
		when(grap.buildItem(any(Player.class))).thenAnswer(invocation -> stick());
		return new Grap(new GrappleGiveCommand(plugin, tree, parent, addon, mock(GrappleMessages.class)), grap);
	}

	private static Player playerWith(PlayerInventory inventory, World world, Location location) {
		Player player = mock(Player.class);
		when(player.getInventory()).thenReturn(inventory);
		when(player.getWorld()).thenReturn(world);
		when(player.getLocation()).thenReturn(location);
		return player;
	}

	private static int[] amounts(ArgumentCaptor<ItemStack[]> captor) {
		ItemStack[] stacks = captor.getValue();
		int[]       result = new int[stacks.length];
		for (int i = 0; i < stacks.length; i++) {
			result[i] = stacks[i].getAmount();
		}
		return result;
	}

	@Test
	@DisplayName("jetpack: 100 sticks split 64 + 36, one sample build plus one per stack, nothing dropped")
	void jetpack_splitsIntoStacks() throws Exception {
		Jet             jet       = jetpackCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Player          player    = playerWith(inventory, world, mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object actual = jetpackMethod().invoke(jet.command(), player, "jp", 100);

		assertEquals(100, actual);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertArrayEquals(new int[]{64, 36}, amounts(captor));
		verify(jet.jetpack(), times(3)).buildItem(player);
		verify(world, never()).dropItemNaturally(any(), any());
	}

	@Test
	@DisplayName("jetpack: amount above 36 stacks is capped at 36 * maxStackSize and that is the returned value")
	void jetpack_capsAtThirtySixStacks() throws Exception {
		Jet             jet       = jetpackCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, mock(World.class), mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object actual = jetpackMethod().invoke(jet.command(), player, "jp", 5000);

		assertEquals(36 * 64, actual);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(36, captor.getValue().length);
		verify(jet.jetpack(), times(37)).buildItem(player);
	}

	@Test
	@DisplayName("jetpack: leftovers from addItem are dropped at the player's location in map-value order")
	void jetpack_dropsLeftoversInOrder() throws Exception {
		Jet             jet       = jetpackCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Location        location  = mock(Location.class);
		Player          player    = playerWith(inventory, world, location);
		ItemStack       first     = stick();
		ItemStack       second    = new ItemStack(Material.STICK, 7);
		LinkedHashMap<Integer, ItemStack> left = new LinkedHashMap<>();
		left.put(1, first);
		left.put(0, second);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(left);

		jetpackMethod().invoke(jet.command(), player, "jp", 2);

		InOrder order = inOrder(world);
		order.verify(world).dropItemNaturally(location, first);
		order.verify(world).dropItemNaturally(location, second);
		verify(world, times(2)).dropItemNaturally(any(), any());
	}

	@Test
	@DisplayName("jetpack: leftovers with a null world are skipped without throwing")
	void jetpack_nullWorldSkipsDrop() throws Exception {
		Jet             jet       = jetpackCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, null, mock(Location.class));
		HashMap<Integer, ItemStack> left = new HashMap<>();
		left.put(0, stick());
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(left);

		Object actual = jetpackMethod().invoke(jet.command(), player, "jp", 2);

		assertEquals(2, actual);
	}

	@Test
	@DisplayName("jetpack: amount 0 hands addItem an empty array; unknown id returns -1 and touches nothing")
	void jetpack_zeroAndUnknown() throws Exception {
		Jet             jet       = jetpackCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, mock(World.class), mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object zero = jetpackMethod().invoke(jet.command(), player, "jp", 0);

		assertEquals(0, zero);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(0, captor.getValue().length);
		verify(jet.jetpack(), times(1)).buildItem(player);

		Player untouched = mock(Player.class);
		Object unknown   = jetpackMethod().invoke(jet.command(), untouched, "nope", 5);

		assertEquals(-1, unknown);
		verifyNoInteractions(untouched);
	}

	@Test
	@DisplayName("grapple: 100 sticks split 64 + 36, one sample build plus one per stack, returns true")
	void grapple_splitsIntoStacks() throws Exception {
		Grap            grap      = grappleCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Player          player    = playerWith(inventory, world, mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object actual = grappleMethod().invoke(grap.command(), player, "gr", 100);

		assertEquals(true, actual);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertArrayEquals(new int[]{64, 36}, amounts(captor));
		verify(grap.grapple(), times(3)).buildItem(player);
		verify(world, never()).dropItemNaturally(any(), any());
	}

	@Test
	@DisplayName("grapple: amount above 36 stacks is capped at 36 * maxStackSize")
	void grapple_capsAtThirtySixStacks() throws Exception {
		Grap            grap      = grappleCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, mock(World.class), mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object actual = grappleMethod().invoke(grap.command(), player, "gr", 5000);

		assertEquals(true, actual);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(36, captor.getValue().length);
		verify(grap.grapple(), times(37)).buildItem(player);
	}

	@Test
	@DisplayName("grapple: leftovers from addItem are dropped at the player's location in map-value order")
	void grapple_dropsLeftoversInOrder() throws Exception {
		Grap            grap      = grappleCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		World           world     = mock(World.class);
		Location        location  = mock(Location.class);
		Player          player    = playerWith(inventory, world, location);
		ItemStack       first     = stick();
		ItemStack       second    = new ItemStack(Material.STICK, 7);
		LinkedHashMap<Integer, ItemStack> left = new LinkedHashMap<>();
		left.put(1, first);
		left.put(0, second);
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(left);

		grappleMethod().invoke(grap.command(), player, "gr", 2);

		InOrder order = inOrder(world);
		order.verify(world).dropItemNaturally(location, first);
		order.verify(world).dropItemNaturally(location, second);
		verify(world, times(2)).dropItemNaturally(any(), any());
	}

	@Test
	@DisplayName("grapple: leftovers with a null world are skipped without throwing, still returns true")
	void grapple_nullWorldSkipsDrop() throws Exception {
		Grap            grap      = grappleCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, null, mock(Location.class));
		HashMap<Integer, ItemStack> left = new HashMap<>();
		left.put(0, stick());
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(left);

		Object actual = grappleMethod().invoke(grap.command(), player, "gr", 2);

		assertEquals(true, actual);
	}

	@Test
	@DisplayName("grapple: amount 0 hands addItem an empty array; unknown id returns false and touches nothing")
	void grapple_zeroAndUnknown() throws Exception {
		Grap            grap      = grappleCommand();
		PlayerInventory inventory = mock(PlayerInventory.class);
		Player          player    = playerWith(inventory, mock(World.class), mock(Location.class));
		when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

		Object zero = grappleMethod().invoke(grap.command(), player, "gr", 0);

		assertEquals(true, zero);
		ArgumentCaptor<ItemStack[]> captor = ArgumentCaptor.forClass(ItemStack[].class);
		verify(inventory).addItem(captor.capture());
		assertEquals(0, captor.getValue().length);
		verify(grap.grapple(), times(1)).buildItem(player);

		Player untouched = mock(Player.class);
		Object unknown   = grappleMethod().invoke(grap.command(), untouched, "nope", 5);

		assertEquals(false, unknown);
		verifyNoInteractions(untouched);
	}
}
