package me.luckyraven.inventory.loot.data;

/**
 * Represents a tier/level for loot chests with unlock requirements
 *
 * @param unlockItemId Required item ID for KEY/LOCKPICK types
 */
public record LootTier(String id, String displayName, int level, UnlockRequirement unlockRequirement,
					   String unlockItemId) {

	public LootTier(String id, String displayName, int level, UnlockRequirement unlockRequirement) {
		this(id, displayName, level, unlockRequirement, null);
	}

	public enum UnlockRequirement {
		NONE,
		LOCKPICK,
		KEY,
		PERMISSION
	}
}
