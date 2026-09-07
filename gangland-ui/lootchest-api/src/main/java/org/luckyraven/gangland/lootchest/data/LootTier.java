package org.luckyraven.gangland.lootchest.data;

/**
 * Represents a tier/level for loot chests with unlock requirements
 *
 * @param unlockItemId Required item id for KEY/LOCKPICK types (matched against the NBT {@code loot_key} tag on
 * 		inventory items)
 * @param unlockItemDisplay Player-facing display name used in holograms / messages (e.g. {@code "&eLockpick"}); may
 * 		be null to fall back to a humanised {@code unlockItemId}
 * @param floatingItemIcon Item-parser string used to render the floating icon above the chest (e.g.
 *        {@code "unique:lockpick"}); may be null to fall back to {@code "unique:" + unlockItemId}
 */
public record LootTier(String id, String displayName, int level, UnlockRequirement unlockRequirement,
                       String unlockItemId, String unlockItemDisplay, String floatingItemIcon) {

	public LootTier(String id, String displayName, int level, UnlockRequirement unlockRequirement) {
		this(id, displayName, level, unlockRequirement, null, null, null);
	}

	public LootTier(String id, String displayName, int level, UnlockRequirement unlockRequirement,
	                String unlockItemId) {
		this(id, displayName, level, unlockRequirement, unlockItemId, null, null);
	}

	public enum UnlockRequirement {
		NONE,
		LOCKPICK,
		KEY,
		PERMISSION
	}
}
