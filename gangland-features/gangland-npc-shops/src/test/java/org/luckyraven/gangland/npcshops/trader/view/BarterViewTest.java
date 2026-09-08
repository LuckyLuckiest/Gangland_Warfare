package org.luckyraven.gangland.copsncrooks.npc.trader.view;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins CT-01 (P0, civilians-traders-shops.md observation #1): barter used to consume every non-air stack in the
 * dropzone on confirm, including the ones the valuator had rejected as "not accepted". A player who dropped one wrong
 * stack alongside a valid offer lost it outright.
 *
 * <p>{@code onConfirm} now clears only the slots {@code acceptedSlots} returns, and hands the rest back through the
 * explicit return pass — the same shape {@code SellView.onConfirm} already used. This class pins the selection rule
 * that decides which slots get consumed.
 */
@DisplayName("BarterView.acceptedSlots — rejected stacks are never consumed (CT-01)")
class BarterViewTest {

	private static ItemStack stack(Material material) {
		ItemStack stack = mock(ItemStack.class);
		when(stack.getType()).thenReturn(material);
		return stack;
	}

	@Test
	@DisplayName("only the slots the valuator accepted are selected for consumption")
	void acceptedSlots_skipsRejectedStacks() {
		int[]     dropzone = {10, 11, 12};
		ItemStack valued   = stack(Material.DIAMOND);
		ItemStack rejected = stack(Material.DIRT);

		Inventory inventory = mock(Inventory.class);
		when(inventory.getItem(10)).thenReturn(valued);
		when(inventory.getItem(11)).thenReturn(rejected);
		when(inventory.getItem(12)).thenReturn(valued);

		List<Integer> accepted = BarterView.acceptedSlots(dropzone, inventory, s -> s == valued);

		assertEquals(List.of(10, 12), accepted,
		             "slot 11 held a stack the trader would not value — clearing it would destroy the player's item");
	}

	@Test
	@DisplayName("empty and air slots are skipped")
	void acceptedSlots_skipsEmptyAndAirSlots() {
		int[]     dropzone = {0, 1, 2};
		ItemStack air      = stack(Material.AIR);
		ItemStack valued   = stack(Material.DIAMOND);

		Inventory inventory = mock(Inventory.class);
		when(inventory.getItem(0)).thenReturn(null);
		when(inventory.getItem(1)).thenReturn(air);
		when(inventory.getItem(2)).thenReturn(valued);

		List<Integer> accepted = BarterView.acceptedSlots(dropzone, inventory, s -> true);

		assertEquals(List.of(2), accepted);
	}

	@Test
	@DisplayName("an offer the trader rejects entirely consumes nothing")
	void acceptedSlots_allRejected_isEmpty() {
		int[]     dropzone = {3, 4};
		ItemStack rejected = stack(Material.DIRT);

		Inventory inventory = mock(Inventory.class);
		when(inventory.getItem(3)).thenReturn(rejected);
		when(inventory.getItem(4)).thenReturn(rejected);

		assertTrue(BarterView.acceptedSlots(dropzone, inventory, s -> false).isEmpty());
	}

	@Test
	@DisplayName("the accepted slots are a subset of the dropzone, so the return pass still owns the rest")
	void acceptedSlots_isSubsetOfDropzone() {
		int[]     dropzone = {10, 11, 12};
		ItemStack valued   = stack(Material.DIAMOND);
		ItemStack rejected = stack(Material.DIRT);

		Inventory inventory = mock(Inventory.class);
		when(inventory.getItem(10)).thenReturn(rejected);
		when(inventory.getItem(11)).thenReturn(valued);
		when(inventory.getItem(12)).thenReturn(rejected);

		Set<Integer> accepted = Set.copyOf(BarterView.acceptedSlots(dropzone, inventory, s -> s == valued));

		assertEquals(Set.of(11), accepted);
	}

}
