package org.luckyraven.gangland.copsncrooks.wanted.disguise;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Reads the Bartizan wearable key of an item; a seam so tests and Bartizan-less servers never touch Bartizan. */
@FunctionalInterface
public interface WearableKeys {

	/** The wearable key of {@code item}, or {@code null} when it is not a wearable or Bartizan is absent. */
	@Nullable String keyOf(@Nullable ItemStack item);

	/** The clean display name of a Bartizan weapon, or {@code null} when {@code item} is none or Bartizan is absent. */
	default @Nullable String weaponNameOf(@Nullable ItemStack item) {
		return null;
	}

	/** Always {@code null}. */
	WearableKeys NONE = item -> null;
}
