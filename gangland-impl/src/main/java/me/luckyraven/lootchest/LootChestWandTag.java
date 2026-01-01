package me.luckyraven.lootchest;

public enum LootChestWandTag {

	/**
	 * Identifies the item as a loot chest wand.
	 */
	WAND_KEY("loot_chest_wand"),

	/**
	 * The loot table ID assigned to this wand.
	 */
	LOOT_TABLE_ID("loot_chest_table_id"),

	/**
	 * The tier ID for the loot chest.
	 */
	TIER_ID("loot_chest_tier_id"),

	/**
	 * The respawn time in seconds for the loot chest.
	 */
	RESPAWN_TIME("loot_chest_respawn_time"),

	/**
	 * The inventory size for the loot chest.
	 */
	INVENTORY_SIZE("loot_chest_inv_size"),

	/**
	 * The display name for the loot chest.
	 */
	DISPLAY_NAME("loot_chest_display_name"),

	/**
	 * Whether the wand has been configured.
	 */
	CONFIGURED("loot_chest_configured"),

	/**
	 * Whether cracking minigame is enabled for this chest.
	 */
	CRACKING_ENABLED("loot_chest_cracking_enabled"),

	/**
	 * The time limit for cracking minigame in seconds.
	 */
	CRACKING_TIME("loot_chest_cracking_time");

	private final String tagName;

	LootChestWandTag(String tagName) {
		this.tagName = tagName;
	}

	@Override
	public String toString() {
		return tagName;
	}

}
