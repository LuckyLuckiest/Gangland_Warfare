package org.luckyraven.gangland.lootchest.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.lootchest.LootChestManager;
import org.luckyraven.gangland.lootchest.LootChestWandTag;
import org.luckyraven.gangland.lootchest.config.LootChestSettingsProvider;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.item.nbt.NbtBridge;
import org.luckyraven.keystone.testkit.RecordingNbtAccessor;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins docket LS-31: the loot-chest wand's right-click "allowed block" gate in {@code onPlayerInteract} must match
 * the clicked block's material <em>exactly</em> against the configured allow-list, never as a substring. Before the
 * fix the check used {@code allowedBlocks.stream().anyMatch(allowed -> block.getType().name().toUpperCase()
 * .contains(allowed.toUpperCase()))} (a substring test), so an allow-list of just {@code ["CHEST"]} would wrongly
 * also accept {@code TRAPPED_CHEST}/{@code ENDER_CHEST} because e.g. {@code "TRAPPED_CHEST".contains("CHEST")} is
 * {@code true}.
 *
 * <p>The matching logic sits inline inside the event handler rather than its own method. This test drives the real
 * {@link LootChestWandListener#onPlayerInteract} through a mocked Bukkit event/player/block chain instead of
 * extracting a standalone matcher — the task this test was written under explicitly forbids touching production
 * code — and asserts that a disallowed block never reaches {@link LootChestManager#registerChest}, the observable
 * effect of a loot chest actually getting created at that location.
 */
@DisplayName("LootChestWandListener — LS-31: allowed-block gate is an exact match, not a substring match")
class LootChestWandListenerTest {

	@BeforeEach
	void setUp() {
		NbtBridge.install(new RecordingNbtAccessor());
	}

	@AfterEach
	void tearDown() {
		NbtBridge.reset();
	}

	@Test
	@DisplayName("allow-list [\"CHEST\"] rejects a right-clicked TRAPPED_CHEST instead of substring-matching it")
	void allowListOfChest_rejectsTrappedChest_exactMatchNotSubstring() {
		JavaPlugin               gangland         = mock(JavaPlugin.class);
		LootChestManager         manager          = mock(LootChestManager.class);
		LootChestSettingsProvider settingsProvider = mock(LootChestSettingsProvider.class);
		Player                   player           = mock(Player.class);
		PlayerInventory          inventory        = mock(PlayerInventory.class);
		Block                    block            = mock(Block.class);
		Location                 location         = new Location(null, 10, 64, 10);
		PlayerInteractEvent      event            = mock(PlayerInteractEvent.class);

		ItemStack wand = configuredWandItem();

		when(event.getPlayer()).thenReturn(player);
		when(event.getAction()).thenReturn(Action.RIGHT_CLICK_BLOCK);
		when(event.getClickedBlock()).thenReturn(block);
		when(player.getInventory()).thenReturn(inventory);
		when(inventory.getItemInMainHand()).thenReturn(wand);
		when(block.getType()).thenReturn(Material.TRAPPED_CHEST);
		when(block.getLocation()).thenReturn(location);
		when(manager.getChestAt(location)).thenReturn(Optional.empty());
		when(settingsProvider.getAllowedBlocks()).thenReturn(List.of("CHEST"));

		LootChestWandListener listener = new LootChestWandListener(gangland, manager, settingsProvider);

		listener.onPlayerInteract(event);

		// The observable effect of the bug: a TRAPPED_CHEST wrongly treated as an allowed "CHEST" gets a loot
		// chest registered at its location. The fix must never reach registerChest for this block/allow-list pair.
		verify(manager, never()).registerChest(any());
		verify(player).sendMessage(contains("not allowed"));
	}

	private static ItemStack configuredWandItem() {
		ItemStack stack = new ItemStack(Material.STICK);
		new ItemBuilder(stack).addTag(LootChestWandTag.WAND_KEY.toString(), true)
		                      .addTag(LootChestWandTag.LOOT_TABLE_ID.toString(), "starter")
		                      .addTag(LootChestWandTag.TIER_ID.toString(), "")
		                      .addTag(LootChestWandTag.INVENTORY_SIZE.toString(), 27)
		                      .addTag(LootChestWandTag.DISPLAY_NAME.toString(), "&eLoot Chest");
		return stack;
	}

}
