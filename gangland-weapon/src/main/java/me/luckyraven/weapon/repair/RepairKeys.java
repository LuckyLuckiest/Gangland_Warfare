package me.luckyraven.weapon.repair;

/**
 * Constant keys for repair item NBT tags.
 */
public final class RepairKeys {

	/**
	 * The repair item's unique ID
	 */
	public static final String REPAIR_ITEM_ID             = "repair-item-id";
	/**
	 * The repair item's current durability
	 */
	public static final String REPAIR_ITEM_DURABILITY     = "repair-item-durability";
	/**
	 * The repair item's maximum durability
	 */
	public static final String REPAIR_ITEM_MAX_DURABILITY = "repair-item-max-durability";

	private RepairKeys() {
		throw new UnsupportedOperationException("Utility class");
	}
}