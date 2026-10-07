package org.luckyraven.gangland.copsncrooks.listener.setup;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.copsncrooks.setup.SetupMessages;
import org.luckyraven.gangland.copsncrooks.setup.SetupOutline;
import org.luckyraven.gangland.copsncrooks.setup.SetupSelection;
import org.luckyraven.gangland.copsncrooks.setup.StackTags;
import org.luckyraven.gangland.copsncrooks.setup.SetupSelections;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The setup wand's clicks: permission, the wand tag, pos1/pos2, and the clean-up on quit and world change. */
@DisplayName("SetupWandListener")
class SetupWandListenerTest {

	private SetupSelections    selections;
	private SetupOutline       outline;
	private SetupWandListener  listener;
	private Player             admin;
	private UUID               id;
	private World              world;

	@BeforeEach
	void setUp() {
		NbtBridge.install(new StackTags());
		selections = new SetupSelections();
		outline    = mock(SetupOutline.class);
		listener   = new SetupWandListener(selections, outline, mock(SetupMessages.class));
		admin      = mock(Player.class);
		id         = UUID.randomUUID();
		world      = mock(World.class);
		when(world.getName()).thenReturn("world");
		when(admin.getUniqueId()).thenReturn(id);
		when(admin.hasPermission(SetupSelections.PERMISSION)).thenReturn(true);
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	private static ItemStack wand(boolean tagged) {
		ItemStack stack = new ItemStack(Material.BLAZE_ROD);
		if (tagged) new ItemBuilder(stack).addTag(SetupSelections.WAND_NBT_KEY, true);
		return stack;
	}

	private PlayerInteractEvent click(Action action, ItemStack item, int x, int y, int z) {
		PlayerInteractEvent event = mock(PlayerInteractEvent.class);
		Block               block = mock(Block.class);
		when(block.getLocation()).thenReturn(new Location(world, x, y, z));
		when(event.getAction()).thenReturn(action);
		when(event.getClickedBlock()).thenReturn(block);
		when(event.getItem()).thenReturn(item);
		when(event.getPlayer()).thenReturn(admin);
		return event;
	}

	@Test
	@DisplayName("a player without the setup permission is ignored: nothing stored, click not cancelled")
	void wrongPermission_ignored() {
		when(admin.hasPermission(SetupSelections.PERMISSION)).thenReturn(false);
		PlayerInteractEvent event = click(Action.LEFT_CLICK_BLOCK, wand(true), 1, 2, 3);

		listener.onInteract(event);

		assertNull(selections.peek(id));
		verify(event, never()).setCancelled(anyBoolean());
	}

	@Test
	@DisplayName("an item without the wand tag is ignored")
	void nonWand_ignored() {
		PlayerInteractEvent event = click(Action.LEFT_CLICK_BLOCK, wand(false), 1, 2, 3);

		listener.onInteract(event);

		assertNull(selections.peek(id));
		verify(event, never()).setCancelled(anyBoolean());
	}

	@Test
	@DisplayName("left click sets pos1, right click sets pos2, both cancel the click and wake the outline")
	void clicks_setPos1AndPos2() {
		PlayerInteractEvent left  = click(Action.LEFT_CLICK_BLOCK, wand(true), 1, 2, 3);
		PlayerInteractEvent right = click(Action.RIGHT_CLICK_BLOCK, wand(true), 4, 5, 6);

		listener.onInteract(left);
		listener.onInteract(right);

		SetupSelection selection = selections.peek(id);
		assertNotNull(selection);
		assertEquals(1, selection.getPos1().getBlockX());
		assertEquals(6, selection.getPos2().getBlockZ());
		verify(left).setCancelled(true);
		verify(right).setCancelled(true);
		verify(outline, org.mockito.Mockito.times(2)).ensureRunning();
	}

	@Test
	@DisplayName("quitting or changing world forgets the selection")
	void quitAndWorldChange_clear() {
		selections.get(id);
		PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
		when(quit.getPlayer()).thenReturn(admin);
		listener.onQuit(quit);
		assertNull(selections.peek(id));

		selections.get(id);
		PlayerChangedWorldEvent moved = mock(PlayerChangedWorldEvent.class);
		when(moved.getPlayer()).thenReturn(admin);
		listener.onWorldChange(moved);
		assertNull(selections.peek(id));
	}
}
