package org.luckyraven.gangland.copsncrooks.bag;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
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

	/** The cops-owned PDC key of a hot bill: {@code copsncrooks:hot_cash}. */
	@SuppressWarnings("deprecation")
	public static final NamespacedKey KEY = new NamespacedKey("copsncrooks", "hot_cash");

	private final IntFunction<ItemStack> stackMaker;

	/**
	 * Plugin is kept for constructor compatibility and is unused: the PDC key is a fixed cops-owned namespace.
	 *
	 * @param plugin     unused, kept for constructor compatibility
	 * @param stackMaker builds one money item worth the given whole dollars (production: {@code MoneyItemFactory.build})
	 */
	public BagSpill(JavaPlugin plugin, IntFunction<ItemStack> stackMaker) {
		this.stackMaker = stackMaker;
	}

	/** {@code dollars / stacks} for every stack, the last one also takes the remainder; cents are dropped. */
	public static List<Integer> split(BigDecimal amount, int stacks) {
		List<Integer> parts  = new ArrayList<>();
		int           dollars = amount.setScale(0, RoundingMode.DOWN).intValue();
		if (dollars <= 0 || stacks <= 0) {
			return parts;
		}

		// ponytail: cents remainder dropped (D-W2-16), int dollars; a bag over Integer.MAX_VALUE would wrap
		int each = dollars / stacks;
		for (int i = 0; i < stacks; i++) {
			int share = (i == stacks - 1) ? dollars - each * (stacks - 1) : each;
			if (share > 0) {
				parts.add(share);
			}
		}
		return parts;
	}

	/** The variation called {@code wanted}, else the first one of {@code byId} (iteration order). */
	public static <T> T pickVariation(String wanted, Map<String, T> byId) {
		T hit = byId.get(wanted);
		if (hit != null) {
			return hit;
		}

		Iterator<T> first = byId.values().iterator();
		return first.hasNext() ? first.next() : null;
	}

	/** One tagged item per stack. */
	public List<ItemStack> build(BigDecimal amount, int stacks) {
		List<ItemStack> built = new ArrayList<>();
		for (int dollars : split(amount, stacks)) {
			ItemStack stack = stackMaker.apply(dollars);
			ItemMeta  meta  = stack.getItemMeta();
			meta.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
			// a real getItemMeta is a copy: write the tag back or it is lost
			stack.setItemMeta(meta);
			built.add(stack);
		}
		return built;
	}

	/** True when the item carries the hot tag. */
	public boolean isHot(ItemStack item) {
		if (item == null) {
			return false;
		}

		ItemMeta meta = item.getItemMeta();
		return meta != null && meta.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
	}

	/** Drops the stacks at {@code at}; a null world drops nothing. Returns the stacks dropped. */
	public int drop(Location at, BigDecimal amount, int stacks) {
		World world = at.getWorld();
		if (world == null) {
			return 0;
		}

		List<ItemStack> drops = build(amount, stacks);
		for (ItemStack stack : drops) {
			world.dropItemNaturally(at, stack);
		}
		return drops.size();
	}
}
