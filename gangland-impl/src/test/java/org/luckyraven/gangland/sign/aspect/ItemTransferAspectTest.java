package org.luckyraven.gangland.sign.aspect;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.model.ParsedSign;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins LS-03 (P0, lootchests-signs-waypoints.md observation #3): a GIVE sign's {@code canExecute} only asked for one
 * free slot ({@code firstEmpty() != -1}) while {@code execute} handed over {@code sign.getAmount()} items and threw
 * away the leftover map {@code addItem} returns. Buying more than fits silently voided the excess — and because
 * {@code MoneyAspect} (priority 100) runs before {@code ItemTransferAspect} (priority 50), the money had already been
 * withdrawn.
 *
 * <p>{@code canExecute} now measures the real free capacity, so {@code SignInteraction}'s {@code canHandle} gate
 * rejects the purchase before any payment is taken.
 */
@DisplayName("ItemTransferAspect — GIVE checks room for the whole amount (LS-03)")
class ItemTransferAspectTest {

	private static final int MAX_STACK_SIZE = 64;

	private Player          player;
	private PlayerInventory inventory;
	private ItemStack       item;

	@BeforeEach
	void setUp() {
		player    = mock(Player.class);
		inventory = mock(PlayerInventory.class);
		item      = mock(ItemStack.class);

		when(player.getInventory()).thenReturn(inventory);
		when(item.getType()).thenReturn(Material.DIAMOND);
		when(item.getMaxStackSize()).thenReturn(MAX_STACK_SIZE);
	}

	private ItemTransferAspect giveAspect() {
		// Nothing in the storage array is ever "similar" to the sold item unless a test says so.
		return new ItemTransferAspect(sign -> item, ItemTransferAspect.TransferType.GIVE,
		                              (p, a, b) -> a == b);
	}

	private ParsedSign sign(int amount) {
		ParsedSign sign = mock(ParsedSign.class);
		when(sign.getAmount()).thenReturn(amount);
		when(sign.getContent()).thenReturn("diamond");
		return sign;
	}

	/**
	 * A storage array with {@code emptySlots} nulls followed by filled, non-similar stacks.
	 */
	private ItemStack[] storage(int size, int emptySlots) {
		ItemStack[] contents = new ItemStack[size];
		for (int i = emptySlots; i < size; i++) {
			ItemStack filled = mock(ItemStack.class);
			when(filled.getType()).thenReturn(Material.STONE);
			contents[i] = filled;
		}
		return contents;
	}

	@Test
	@DisplayName("a single free slot is not enough for more than one stack")
	void canExecute_give_oneFreeSlot_amountExceedsAStack_isRejected() {
		// storage() stubs mocks of its own, so it must not run inside an open when(...) call.
		ItemStack[] contents = storage(36, 1);
		when(inventory.getStorageContents()).thenReturn(contents);

		assertFalse(giveAspect().canExecute(player, sign(100)),
		            "one empty slot holds 64 diamonds, not 100 — the purchase must be refused before payment");
	}

	@Test
	@DisplayName("a single free slot is enough for a stack or less")
	void canExecute_give_oneFreeSlot_amountFits_isAccepted() {
		// storage() stubs mocks of its own, so it must not run inside an open when(...) call.
		ItemStack[] contents = storage(36, 1);
		when(inventory.getStorageContents()).thenReturn(contents);

		assertTrue(giveAspect().canExecute(player, sign(64)));
	}

	@Test
	@DisplayName("two free slots cover a two-stack purchase")
	void canExecute_give_twoFreeSlots_coversTwoStacks() {
		// storage() stubs mocks of its own, so it must not run inside an open when(...) call.
		ItemStack[] contents = storage(36, 2);
		when(inventory.getStorageContents()).thenReturn(contents);

		assertTrue(giveAspect().canExecute(player, sign(100)));
	}

	@Test
	@DisplayName("a completely full inventory is rejected")
	void canExecute_give_fullInventory_isRejected() {
		// storage() stubs mocks of its own, so it must not run inside an open when(...) call.
		ItemStack[] contents = storage(36, 0);
		when(inventory.getStorageContents()).thenReturn(contents);

		assertFalse(giveAspect().canExecute(player, sign(1)));
	}

	@Test
	@DisplayName("the remaining room in a partially filled matching stack counts towards the space")
	void canExecute_give_partialMatchingStack_countsRemainingRoom() {
		ItemStack[] contents = storage(36, 0);
		// Slot 0 already holds the very item being sold, 60 of a 64 stack — four more fit.
		when(item.getAmount()).thenReturn(60);
		contents[0] = item;

		when(inventory.getStorageContents()).thenReturn(contents);

		assertTrue(giveAspect().canExecute(player, sign(4)));
		assertFalse(giveAspect().canExecute(player, sign(5)));
	}

	@Test
	@DisplayName("execute refuses rather than voiding items when the inventory cannot hold the amount")
	void execute_give_noRoom_failsWithoutHandingAnythingOver() {
		// storage() stubs mocks of its own, so it must not run inside an open when(...) call.
		ItemStack[] contents = storage(36, 1);
		when(inventory.getStorageContents()).thenReturn(contents);

		AspectResult result = giveAspect().execute(player, sign(100));

		assertFalse(result.isSuccess());
		verify(inventory, never()).addItem(any(ItemStack[].class));
	}

}
