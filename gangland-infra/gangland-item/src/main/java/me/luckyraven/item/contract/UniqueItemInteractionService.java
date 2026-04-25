package me.luckyraven.item.contract;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

/**
 * Contract that gangland-item listeners use to drive the inventory-side reaction to a unique-item right-click without
 * importing the inventory subsystem from gangland-impl.
 *
 * <p>The implementation in gangland-impl wraps the {@code InventoryRuntimeContext}'s unique-item lookup and
 * {@code openInventoryForPlayer} along with the permission check that previously lived inline in
 * {@code UniqueItemInteract}.
 */
public interface UniqueItemInteractionService {

	/**
	 * Tries to handle a right-click interaction on a unique item identified by {@code uniqueItemKey}.
	 *
	 * <p>The implementation should:
	 * <ul>
	 *   <li>Look up the registered {@code UniqueItemHandler} for the key. If none exists, return {@code false}.</li>
	 *   <li>Check that the click {@code action} is one of the handler's allowed actions. If not, return {@code false}.</li>
	 *   <li>Check the configured permission (if any). If the player lacks it, return {@code false}.</li>
	 *   <li>Open the configured inventory and return {@code true}.</li>
	 * </ul>
	 *
	 * <p>The listener will {@code event.setCancelled(true)} on a {@code true} return.
	 *
	 * @param player the player who right-clicked
	 * @param uniqueItemKey the unique item key read from the held item's NBT
	 * @param action the click action from the {@code PlayerInteractEvent}
	 *
	 * @return {@code true} if a handler was found and an inventory was opened
	 */
	boolean tryHandleInteract(Player player, String uniqueItemKey, Action action);

}
