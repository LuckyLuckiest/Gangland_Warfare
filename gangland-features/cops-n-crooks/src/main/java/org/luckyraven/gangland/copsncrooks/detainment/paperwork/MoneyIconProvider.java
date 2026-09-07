package org.luckyraven.gangland.copsncrooks.detainment.paperwork;

import org.bukkit.inventory.ItemStack;

/**
 * Abstraction over money.yml-backed visual icons used in detainment GUIs. Provides an {@link ItemStack} whose material
 * matches the configured money variation (without the functional deposit NBT) so bribe / bail buttons visually show the
 * cash denomination.
 */
public interface MoneyIconProvider {

	/**
	 * Returns a clonable visual-only ItemStack representing the money denomination that best matches {@code cost}. The
	 * stack has no deposit NBT markers — it is for display only.
	 */
	ItemStack buildIcon(double cost);
}
