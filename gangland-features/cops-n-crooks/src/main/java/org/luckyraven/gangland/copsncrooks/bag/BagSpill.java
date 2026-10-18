package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Builds and drops the hot bills of a spilled bag: whole dollars split into stacks, each tagged with the PDC key
 * {@code hot_cash} so a picker who is not wanted earns a star.
 *
 * @since 0.20.0
 */
public final class BagSpill {

	/**
	 * @param plugin     owner of the PDC key
	 * @param stackMaker builds one money item worth the given whole dollars (production: {@code MoneyItemFactory.build})
	 */
	public BagSpill(JavaPlugin plugin, IntFunction<ItemStack> stackMaker) {
	}

	/** {@code dollars / stacks} for every stack, the last one also takes the remainder; cents are dropped. */
	public static List<Integer> split(BigDecimal amount, int stacks) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The variation called {@code wanted}, else the first one of {@code byId} (iteration order). */
	public static <T> T pickVariation(String wanted, Map<String, T> byId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** One tagged item per stack. */
	public List<ItemStack> build(BigDecimal amount, int stacks) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** True when the item carries the hot tag. */
	public boolean isHot(ItemStack item) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops the stacks at {@code at}; a null world drops nothing. Returns the stacks dropped. */
	public int drop(Location at, BigDecimal amount, int stacks) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
