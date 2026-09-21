package org.luckyraven.gangland.npcshops.trader.view;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.luckyraven.keystone.inventory.chest.ChestMenu;
import org.luckyraven.keystone.inventory.component.Component;
import org.luckyraven.keystone.inventory.component.ItemHoldingComponent;
import org.luckyraven.keystone.inventory.component.RenderContext;
import org.luckyraven.keystone.inventory.component.SlotView;

import java.util.List;

/**
 * One interactive "drop your own item here" slot for {@link BarterView}/{@link SellView}'s dropzone (WS2 G4
 * §0d re-point). {@code renderInto} only calls {@code view.interactive(true)} — never {@code setItem} — so a
 * render pass never touches whatever the player physically has sitting in the slot: Bukkit's own click/drag
 * handling (uncancelled because the slot is interactive — see {@code MenuListener}) already writes directly into
 * the live {@link Inventory}, and that live inventory IS the state; there is nothing else to stage or restore, and
 * no more "snapshot dropzone, fill around it, restore dropzone" dance the old {@code InventoryUtil.fillInventory}
 * era needed (Keystone's fill never touches an interactive slot to begin with — {@code ChestMenu.applyFillIfEmpty}).
 *
 * <p>Also an {@link ItemHoldingComponent}: on any close path (Escape, disconnect, reload, a differently-shaped
 * panel switch) Keystone's own item-return contract (see {@code keystone-inventory.md} "The item-return contract")
 * hands back whatever is physically in this slot — replacing the old bespoke {@code MultiPanelInventory#onEnd}
 * hook, which had no equivalent on the new immutable-at-build {@code MenuFlow} (its {@code onEnd} is set once via
 * {@code MenuFlowBuilder}, not re-settable per-panel-entry the way {@code MultiPanelInventory#onEnd} was).
 *
 * <p>One instance per slot, created fresh on every {@code Panel.render()} call (cheap, stateless besides the
 * {@link #inventory} reference it captures during its own render so a later {@code heldItems}/{@code clearHeld}
 * call — potentially long after render, on close — can still reach the live slot); {@code returnHeldItems} iterates
 * the menu's component map by slot, so one instance per slot (never shared across slots) is required for a correct
 * per-slot return — a single instance covering all 20 dropzone slots would return the same items once per slot.
 */
final class DropzoneSlotComponent implements Component<DropzoneSlotComponent>, ItemHoldingComponent {

	private final int slot;
	private Inventory inventory;

	DropzoneSlotComponent(int slot) {
		this.slot = slot;
	}

	@Override
	public void renderInto(SlotView view, RenderContext ctx) {
		view.interactive(true);
		if (ctx.menu() instanceof ChestMenu chestMenu) {
			this.inventory = chestMenu.bukkitInventory();
		}
	}

	@Override
	public List<ItemStack> heldItems(Player player) {
		if (inventory == null) return List.of();
		ItemStack stack = inventory.getItem(slot);
		if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) return List.of();
		return List.of(stack);
	}

	@Override
	public void clearHeld(Player player) {
		if (inventory != null) inventory.setItem(slot, null);
	}

}
