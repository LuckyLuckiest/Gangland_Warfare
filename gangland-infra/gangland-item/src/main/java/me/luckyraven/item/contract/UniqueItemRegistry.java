package me.luckyraven.item.contract;

import me.luckyraven.item.unique.UniqueItem;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Narrow read-only view of the unique-item registry, used by gangland-item listeners that need to look up unique item
 * definitions without importing the gangland-impl {@code UniqueItemAddon} class (which depends on
 * {@code PermissionManager}, {@code FileManager}, and the gadget {@code FuelService} — none of which are visible from
 * gangland-item).
 *
 * <p>The gangland-impl {@code UniqueItemAddon} class implements this interface so a single instance can satisfy
 * both impl-side callers and the moved listeners.
 */
public interface UniqueItemRegistry {

	/**
	 * Returns the unique item registered under {@code key}, or {@code null} if no entry exists.
	 */
	@Nullable
	UniqueItem getUniqueItem(String key);

	/**
	 * Returns an unmodifiable view of every registered unique item, keyed by their string id.
	 */
	Map<String, UniqueItem> getUniqueItems();

}
