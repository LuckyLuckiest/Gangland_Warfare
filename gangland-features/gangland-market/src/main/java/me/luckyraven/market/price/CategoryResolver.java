package me.luckyraven.market.price;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves an item id to its (optional) SellCategory id. Injected as a bean so the market module doesn't have to import
 * shop-api {@code SellCategory} internals. Default bean returns null (shocks can still target items directly). The
 * impl-side bridge provides a real resolver backed by the loaded shop definitions.
 */
@FunctionalInterface
public interface CategoryResolver {

	CategoryResolver NONE = itemId -> null;

	@Nullable
	String resolve(String itemId);
}
